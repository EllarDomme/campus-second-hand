package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.OrderConstant;
import com.sky.constant.ProductConstant;
import com.sky.context.BaseContext;
import com.sky.dto.TradeOrderPageQueryDTO;
import com.sky.dto.TradeOrderCancelDTO;
import com.sky.dto.TradeOrderSubmitDTO;
import com.sky.entity.PaymentRecord;
import com.sky.entity.Product;
import com.sky.entity.TradeOrder;
import com.sky.exception.BaseException;
import com.sky.mapper.PaymentRecordMapper;
import com.sky.mapper.ProductMapper;
import com.sky.mapper.TradeOrderMapper;
import com.sky.result.PageResult;
import com.sky.service.TradeOrderService;
import com.sky.service.DeliveryService;
import com.sky.utils.FlashSaleStockUtil;
import com.sky.vo.TradeOrderPayVO;
import com.sky.vo.TradeOrderSubmitVO;
import com.sky.vo.TradeOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;   // 填空④ 会用到这个异常
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 交易订单服务实现
 *
 * ==========================================================================
 * ★★★ 公共逻辑速查（这些方法被多处复用，改一处会影响多处）★★★
 * --------------------------------------------------------------------------
 * 把本类里的关键字【公共逻辑】拿去搜索，就能定位到全部：
 *
 *   【公共逻辑 · 取消订单】      doCancelOrder(order, reason, cancelStatus)
 *        被 3 处复用：卖家拒单 reject ／ 双方取消 cancel ／ 超时关单 timeoutClose
 *        它内部会自己判断"已支付就退款"，所以三种场景的调用方式完全一样，
 *        差别只有两个参数：原因文案、终态用 3（已取消）还是 4（超时取消）
 *
 *   【公共逻辑 · 查订单或抛异常】  getOrderOrThrow(orderId)
 *        被 2 处复用：拒单 ／ 取消
 *
 *   【公共逻辑 · 原因校验】        checkReason(reason, action)
 *        被 2 处复用：拒单（action="拒单"）／ 取消（action="取消"）
 *
 *   【公共逻辑 · 资金流水号】      generateFlowNo(prefix)
 *        被 3 处复用：支付 "PAY" ／ 退款 "REF" ／ 放款 "SET"
 *
 *   【公共逻辑 · 订单号】          generateOrderNo()
 *        被 2 处复用：普通下单 ／ 议价成交下单
 *
 *   【公共逻辑 · 创建订单】        createOrder(product, buyerId, price, orderSource,
 *                                            dealType, expireMinutes, pickupPlace, remark)
 *        被 2 处复用：普通下单 submit ／ 议价成交 accept（S3-6b）
 *        它做"锁商品 + 建订单 + 算超时"三件事，只写一份；
 *        调用方只传各自不同的部分：成交价来源、订单来源、超时时长、交易地点、备注
 *
 * --------------------------------------------------------------------------
 * 本类之外还有一个更重要的公共逻辑：
 *   【公共逻辑 · 条件更新商品状态】 ProductMapper.updateStatusIfMatch(id, fromStatus, toStatus)
 *        被 4 处复用：
 *          下单 → 在售锁成交易中 ／ 拒单·取消·超时 → 交易中回退在售 ／ 确认收货 → 交易中标记已售罄
 *        ⚠️ 调用它之后【必须】判断影响行数（返回 0 说明状态不对，该抛异常让事务回滚）
 * ==========================================================================
 */
@Service
@Slf4j
public class TradeOrderServiceImpl implements TradeOrderService {

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    /** 捡漏单取消/超时后，要把 Redis 预扣的库存还回去（S3-7c） */
    @Autowired
    private FlashSaleStockUtil flashSaleStockUtil;

    /** 跑腿单：下单时生成、主订单取消时联动取消（S3-9） */
    @Autowired
    private DeliveryService deliveryService;

    /**
     * 买家下单
     *
     * 整体顺序很重要：先校验 → 再锁商品 → 最后建订单。
     * 如果先建订单再锁商品，一旦锁失败就得回滚订单（多一次无用写入）。
     */
    @Transactional
    @Override
    public TradeOrderSubmitVO submit(TradeOrderSubmitDTO submitDTO) {

        Long buyerId = BaseContext.getCurrentId();

        // ---------- ① 校验商品能不能买 ----------
        Product product = productMapper.getById(submitDTO.getProductId());
        if (product == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }
        if (!ProductConstant.AUDIT_PASSED.equals(product.getAuditStatus())) {
            throw new BaseException("商品还未通过审核，暂时不能下单");
        }
        if (!ProductConstant.ON_SALE.equals(product.getStatus())) {
            throw new BaseException("商品当前不可购买（可能已被别人拍下或已下架）");
        }
        if (product.getSellerId().equals(buyerId)) {
            throw new BaseException("不能购买自己发布的商品");
        }

        // ---------- ①' 交易方式与跑腿必填项（便宜的先校验，别等订单建好了才发现地址没填）----------
        Integer dealType = submitDTO.getDealType() == null
                ? OrderConstant.DEAL_SELF_PICKUP : submitDTO.getDealType();
        if (OrderConstant.DEAL_DELIVERY.equals(dealType)) {
            if (submitDTO.getPickAddress() == null || submitDTO.getPickAddress().trim().isEmpty()) {
                throw new BaseException("跑腿单必须填写取货地址");
            }
            if (submitDTO.getReceiveAddress() == null || submitDTO.getReceiveAddress().trim().isEmpty()) {
                throw new BaseException("跑腿单必须填写收货地址");
            }
        }

        // ---------- ②③ 锁定商品 + 创建订单 ----------
        // 这两件事与"议价成交下单"完全一样，已抽成公共逻辑 createOrder（见下方同名方法）
        TradeOrder order = createOrder(
                product,
                buyerId,
                product.getPrice(),          // 普通下单：成交价取商品标价
                OrderConstant.SOURCE_NORMAL,
                dealType,
                OrderConstant.EXPIRE_MINUTES_NORMAL,   // 普通订单未支付超时 15 分钟
                submitDTO.getPickupPlace(),
                submitDTO.getRemark());

        // ---------- ④ 走跑腿的话，同步生成跑腿单 ----------
        // 跑腿费在 DeliveryService 里按"距离档位"算好并落库（计费快照），
        // 前端只能传档位、传不了金额，防止白嫖跑腿。
        if (OrderConstant.DEAL_DELIVERY.equals(order.getDealType())) {
            deliveryService.createForTradeOrder(order, submitDTO);
        }

        log.info("下单成功：orderNo={}, buyerId={}, productId={}, 金额={}",
                order.getOrderNo(), buyerId, product.getId(), order.getRealPrice());

        return TradeOrderSubmitVO.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .realPrice(order.getRealPrice())
                .expireTime(order.getExpireTime())
                .payStatus(order.getPayStatus())
                .build();
    }

    /**
     * 发起支付（模拟通道）
     *
     * 这个接口只负责"生成支付流水号并告诉前端去付款"，不负责改订单状态。
     * 订单真正变成"已支付"是在支付回调里完成的 —— 因为只有回调才能证明"钱真的到了"。
     */
    @Override
    public TradeOrderPayVO pay(String orderNo) {
        Long buyerId = BaseContext.getCurrentId();

        TradeOrder order = tradeOrderMapper.getByOrderNo(orderNo);
        if (order == null) {
            throw new BaseException("订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BaseException("只能支付自己的订单");
        }
        if (OrderConstant.PAY_PAID.equals(order.getPayStatus())) {
            throw new BaseException("订单已支付，请勿重复支付");
        }

        // ===== 填空 ① =====
        // TODO ①：校验订单是否已经超时 —— 超时了就拒绝支付
        //   提示：比较"当前时间" LocalDateTime.now() 和 order.getExpireTime()
        //        注意 expireTime 可能是 null（先判空，null 就当不超时）
        boolean expired = true;
        if(order.getExpireTime() == null){
            expired = false;
        } else if (order.getExpireTime().isBefore(LocalDateTime.now())) {
            expired = true;
        } else if (order.getExpireTime().isAfter(LocalDateTime.now())) {
            expired = false;
        }
        if (expired) {
            throw new BaseException("订单已超时，无法支付");
        }

        // 生成支付流水号，并写回订单（回调只带回来 payNo，靠它找到订单）
        String payNo = generateFlowNo("PAY");
        tradeOrderMapper.update(TradeOrder.builder()
                .id(order.getId())
                .payNo(payNo)
                .updateTime(LocalDateTime.now())
                .build());

        log.info("发起支付：orderNo={}, payNo={}, 金额={}", orderNo, payNo, order.getRealPrice());

        return TradeOrderPayVO.builder()
                .orderNo(order.getOrderNo())
                .payNo(payNo)
                .amount(order.getRealPrice())
                .expireTime(order.getExpireTime())
                .build();
    }

    /**
     * 处理支付成功通知（模拟回调）
     *
     * 这是整个支付里最需要小心的地方：支付平台的通知可能重复送达
     *（网络重试、平台重发都会导致同一个通知来两次），
     * 所以必须保证「同一笔支付只被处理一次」——这就是幂等。
     * 下面用两层防护，两层都要有，原因见各自的注释。
     */
    @Transactional
    @Override
    public void handlePaySuccess(String payNo) {

        // ===== 填空 ② =====
        // TODO ②：按 payNo 查出订单
        //   提示：用 tradeOrderMapper.getByPayNo(payNo)（本次刚加的方法）
        TradeOrder order = tradeOrderMapper.getByPayNo(payNo);   // ← 换成正确的查询
        if (order == null) {
            throw new BaseException("支付记录对应的订单不存在");
        }

        // ===== 填空 ③（幂等第一层：快速路径）=====
        // TODO ③：如果订单已经是"已支付"，说明这是重复通知 → 直接 return，不要重复处理
        //   提示：判断 order.getPayStatus() 是否等于 OrderConstant.PAY_PAID
        //   为什么还要第二层：两个重复通知「同时」到达时，可能都还没查到"已支付"，
        //                    这一层就挡不住了 → 只能靠下面的唯一索引
        boolean alreadyPaid = order.getPayStatus() == OrderConstant.PAY_PAID;   // ← 把 false 换成正确的判断
        if (alreadyPaid) {
            log.info("重复的支付通知（订单已支付），直接返回：payNo={}", payNo);
            return;
        }

        PaymentRecord record = PaymentRecord.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .payNo(payNo)
                .amount(order.getRealPrice())
                .payType(OrderConstant.PAY_TYPE_PAY)
                .status(1)
                .remark("模拟支付成功")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // ===== 填空 ④（幂等第二层：唯一索引兜底）=====
        // TODO ④：把下面这行 insert 包进 try/catch
        //   - catch 的异常类型：参考你自己在 UserServiceImpl.getOrCreateByOpenid 里写过的那个
        //     （就是处理"并发注册冲突"那一处；这个类上面已经帮你 import 好了）
        //   - catch 到之后：打一条 log.warn，然后 return（说明这笔流水已经处理过了）
        //   - 不包 try/catch 的后果：重复通知会抛异常 → 接口返回"未知错误"，
        //     验收时你会看到"重复回调应该成功却失败了"
        try{
            paymentRecordMapper.insert(record);
        }catch(DuplicateKeyException e){
            log.warn("支付流水号已经存在，{payno}的,直接返回",payNo);
            return;
        }


        // ===== 填空 ⑤ =====
        // TODO ⑤：更新订单（三个字段）
        //   payStatus  → 改成"已支付"（OrderConstant.PAY_PAID）
        //   payTime    → 改成当前时间 LocalDateTime.now()
        //   expireTime → 顺延到"现在 + 30 分钟"（用 OrderConstant.EXPIRE_MINUTES_PENDING_CONFIRM）
        //                这一步是给"卖家确认"留出时限：同一个 expire_time 字段实现分段超时
        TradeOrder update = TradeOrder.builder()
                .id(order.getId())
                .payStatus(OrderConstant.PAY_PAID)   // ← 填空⑤-a：改成"已支付"
                .payTime(LocalDateTime.now())                     // ← 填空⑤-b：改成当前时间
                .expireTime(LocalDateTime.now().plusMinutes(OrderConstant.EXPIRE_MINUTES_PENDING_CONFIRM))                  // ← 填空⑤-c：改成 现在 + 30 分钟
                .updateTime(LocalDateTime.now())
                .build();
        tradeOrderMapper.update(update);

        log.info("支付成功已处理：payNo={}, orderNo={}", payNo, order.getOrderNo());
    }

    // ==========================================================================
    // 【公共逻辑 · 资金流水号】被 3 处复用：支付 "PAY" ／ 退款 "REF" ／ 放款 "SET"
    // ==========================================================================
    /**
     * 生成资金流水号：前缀 + 年月日时分秒 + 4 位随机数
     *
     * 三种流水共用这一个方法，只靠前缀区分（原来是三个几乎一样的方法，合并成参数化）：
     *   PAY = 支付     REF = 退款     SET = 放款（结算给卖家）
     *
     * 为什么每种流水都必须有自己的号（不能复用）：
     *   ① payment_record.pay_no 上有唯一索引，复用会撞唯一键报错
     *   ② 前缀区分开后，看流水表时一眼能分清这笔钱是收、是退、还是放给卖家
     */
    private String generateFlowNo(String prefix) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + time + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    // ==========================================================================
    // 【公共逻辑 · 订单号】被 2 处复用：普通下单 ／ 议价成交下单
    // ==========================================================================
    /**
     * 生成业务订单号：年月日时分秒 + 4 位随机数
     *
     * 为什么不用自增 id 当订单号：自增 id 会暴露业务量（用户看到订单号 10086 就知道平台有多少单），
     * 而且 id 是数据库内部主键，不该对外暴露。数据库上还有 uk_order_no 唯一索引兜底。
     */
    private String generateOrderNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return time + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    // ==========================================================================
    // 【公共逻辑 · 创建订单】被 2 处复用：普通下单 submit ／ 议价成交 accept
    // --------------------------------------------------------------------------
    //   做三件事：① 锁定商品（在售 → 交易中）② 补齐订单公共字段 ③ 落库
    //   ⚠️ 顺序不能反：先锁商品、再落库 —— 锁失败就不该产生订单
    //   ⚠️ 订单号/状态/支付状态/时间这些"每个订单都一样"的字段由本方法统一填，
    //      不让两个调用方各写一遍（写得不一致就会出现"有的订单没有 expireTime"这类问题）
    //   调用方只负责各自不同的部分：成交价来源、orderSource、超时时长、交易地点、备注
    // ==========================================================================
    /**
     * 创建订单（锁定商品 + 落库）
     *
     * @param product       已校验过"可买"的商品（存在、已过审、在售、且不是买家自己的）
     * @param buyerId       买家 id（调用方从登录态取，不接受前端传）
     * @param price         成交价（由调用方决定来源：普通=标价 / 议价=议价金额；不接受前端传）
     * @param orderSource   订单来源：1普通下单 2议价生成 3捡漏抢购
     * @param dealType      交易方式：0自提 1跑腿
     * @param expireMinutes 未支付超时分钟数（普通 15 / 捡漏 10）
     * @param pickupPlace   交易地点（议价成交时为空，买家随后可以补充）
     * @param remark        买家备注（可为 null）
     * @return 落库后的订单对象（自增 id 已回填）
     */
    private TradeOrder createOrder(Product product, Long buyerId, BigDecimal price,
                                   Integer orderSource, Integer dealType, int expireMinutes,
                                   String pickupPlace, String remark) {
        // ① 锁定商品：只有当前是"在售"才能锁成"交易中"
        //    条件更新把"判断 + 修改"合成一条 SQL，靠数据库行锁保证并发下只有一个人成功
        //    （另一个人影响 0 行 → 抛异常 → 事务回滚，不会出现"一件商品卖出两份"）
        int locked = productMapper.updateStatusIfMatch(
                product.getId(), ProductConstant.ON_SALE, ProductConstant.IN_TRADE);
        if (locked == 0) {
            throw new BaseException("手慢了，商品已被别人拍下");
        }

        // ② 补齐公共字段并落库
        LocalDateTime now = LocalDateTime.now();
        TradeOrder order = TradeOrder.builder()
                .orderNo(generateOrderNo())
                .sellerId(product.getSellerId())
                .buyerId(buyerId)
                .productId(product.getId())
                .realPrice(price)
                .dealType(dealType)
                .orderSource(orderSource)
                // "待确认" + "未支付" = 前端看到的"待支付"
                .status(OrderConstant.STATUS_PENDING_CONFIRM)
                .payStatus(OrderConstant.PAY_UNPAID)
                .pickupPlace(pickupPlace)
                .remark(remark)
                .expireTime(now.plusMinutes(expireMinutes))
                .createTime(now)
                .updateTime(now)
                .build();
        tradeOrderMapper.insert(order);
        return order;
    }

    /**
     * 议价成交后生成订单（对外暴露给 BargainService 用的语义化入口）
     *
     * 它只是把"议价场景的固定参数"替调用方填好，再转交给上面的私有 createOrder：
     *   订单来源 = 议价生成(2)
     *   交易方式 = 自提（议价成交默认当面交易）
     *   超时     = 普通订单 15 分钟
     *   交易地点 = 空（议价时买家没填，之后可在订单上补充）
     *
     * 这样设计的好处：8 个参数的"通用建单"留在私有层，对外只暴露 3 个参数的业务入口，
     * 调用方不需要知道 orderSource 该填几、超时该填多少。
     */
    @Override
    public TradeOrder createBargainOrder(Product product, Long buyerId, BigDecimal price) {
        return createOrder(
                product,
                buyerId,
                price,
                OrderConstant.SOURCE_BARGAIN,
                OrderConstant.DEAL_SELF_PICKUP,
                OrderConstant.EXPIRE_MINUTES_NORMAL,
                null,
                "议价成交");
    }

    /**
     * 捡漏抢购成功后的订单（对外暴露给 FlashSaleService 用的语义化入口）
     *
     * 和"议价成交"一样，只是把捡漏场景的固定参数替调用方填好：
     *   订单来源 = 捡漏抢购(3)
     *   未支付超时 = 10 分钟（EXPIRE_MINUTES_FLASH_SALE，与抢购窗口一致）
     *   交易地点 = 空（抢购时买家没填，之后可用 /user/order/pickup/{id} 补充）
     */
    @Override
    public TradeOrder createFlashSaleOrder(Product product, Long buyerId, BigDecimal price) {
        return createOrder(
                product,
                buyerId,
                price,
                OrderConstant.SOURCE_FLASH_SALE,
                OrderConstant.DEAL_SELF_PICKUP,
                OrderConstant.EXPIRE_MINUTES_FLASH_SALE,
                null,
                "捡漏抢购成功");
    }

    /**
     * 买家补充/修改交易地点
     *
     * 交易地点是双方碰面的依据 —— 如果卖家或陌生人都能改，买家可能白跑一趟，
     * 所以权限卡得很紧：只有买家本人、且订单还没结束。
     */
    @Override
    public void updatePickupPlace(Long orderId, String pickupPlace) {
        Long me = BaseContext.getCurrentId();

        // ① 查订单 + 不存在校验（复用公共逻辑）
        TradeOrder order = getOrderOrThrow(orderId);

        // ② 权限：只有买家本人（允许法：除了买家谁都不行）
        if (!me.equals(order.getBuyerId())) {
            throw new BaseException("只有买家可以填写交易地点");
        }

        // ③ 订单已结束就不能再改（待确认0 / 待交易1 之外都是终态）
        boolean editable = OrderConstant.STATUS_PENDING_CONFIRM.equals(order.getStatus())
                || OrderConstant.STATUS_PENDING_TRADE.equals(order.getStatus());
        if (!editable) {
            throw new BaseException("订单已结束，不能再修改交易地点");
        }

        // ④ 内容校验：非空 + 不超过数据库列长度
        //    数据库里 pickup_place 是 varchar(255)，超长会直接抛 SQL 异常（提示很不友好），
        //    所以在业务层先拦一道，给出可读的提示
        if (pickupPlace == null || pickupPlace.trim().isEmpty()) {
            throw new BaseException("交易地点不能为空");
        }
        String place = pickupPlace.trim();
        if (place.length() > 255) {
            throw new BaseException("交易地点太长了（最多 255 个字符）");
        }

        // ⑤ 落库（动态更新只会改传了的字段，不会动订单其它列）
        tradeOrderMapper.update(TradeOrder.builder()
                .id(orderId)
                .pickupPlace(place)
                .updateTime(LocalDateTime.now())
                .build());

        log.info("买家填写交易地点：orderId={}, 买家={}, 地点={}", orderId, me, place);
    }

    /**
     * 分页查询订单列表
     * @param queryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(TradeOrderPageQueryDTO queryDTO) {
        // 清空 buyerId 和 sellerId，避免前端伪造
        queryDTO.setBuyerId(null);
        queryDTO.setSellerId(null);

        //获取我的id
        Long userId = BaseContext.getCurrentId();

        //做判断(顺序是重中之重)
        if("seller".equals(queryDTO.getView())){
            queryDTO.setSellerId(userId);
        }else{
            queryDTO.setBuyerId(userId);
        }

        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        Page<TradeOrderVO> page = tradeOrderMapper.pageQuery(queryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 查看订单详情
     * @param orderId
     * @return
     */
    @Override
    public TradeOrderVO getDetail(long orderId) {
        TradeOrderVO vo =  tradeOrderMapper.getVOById(orderId);

        if(vo == null){
            throw new BaseException("订单不存在");
        }

        //检验是买家还是卖家
        Long userId = BaseContext.getCurrentId();

        if(userId.equals(vo.getBuyerId()) || userId.equals(vo.getSellerId())){
            return vo;
        }else{
            throw new BaseException("您没有权限查看该订单");
        }
    }

    @Override
    public void confirm(Long orderId) {
        //获取的使用者id
        Long userId = BaseContext.getCurrentId();
        //看订单是否存在
        TradeOrder order = tradeOrderMapper.getById(orderId);
        if(order == null){
            throw new BaseException("订单不存在");
        }

        //检测自己是不是卖家本人
        if(!userId.equals(order.getSellerId())){
            throw new BaseException("您没有权限确认该订单");
        }

        //订单状态必须是”待确认“
        if(!order.getStatus().equals(OrderConstant.STATUS_PENDING_CONFIRM)){
            throw new BaseException("订单状态不是待确认，不能确认");
        }

        //订单支付状态必须是”已支付“
        if(!order.getPayStatus().equals(OrderConstant.PAY_PAID)){
            throw new BaseException("订单支付状态不是已支付，不能确认");
        }

        //更新状态改为“已确认”
        tradeOrderMapper.update(TradeOrder.builder()
                .id(order.getId())
                .status(OrderConstant.STATUS_PENDING_TRADE)
                .updateTime(LocalDateTime.now())
                .build());

        log.info("订单确认成功：订单id={}，卖家id={}",orderId,userId);
    }


    // ==================== 订单取消：公共逻辑 + 两个入口 ====================

    // ==========================================================================
    // 【公共逻辑 · 查订单或抛异常】被 2 处复用：卖家拒单 reject ／ 双方取消 cancel
    // ==========================================================================
    /**
     * 查订单，查不到直接抛异常
     */
    private TradeOrder getOrderOrThrow(Long orderId) {
        TradeOrder order = tradeOrderMapper.getById(orderId);
        if (order == null) {
            throw new BaseException("订单不存在");
        }
        return order;
    }

    // ==========================================================================
    // 【公共逻辑 · 原因校验】被 2 处复用：拒单（action="拒单"）／ 取消（action="取消"）
    // ==========================================================================
    /**
     * 原因校验
     *
     * @param action 动作名，用来拼提示语，这样两个入口都能给出准确的提示
     *               （"拒单原因不能为空" / "取消原因不能为空"）
     */
    private void checkReason(String reason, String action) {
        // trim 后判断，"   " 这种全空格也算空
        if (reason == null || reason.trim().isEmpty()) {
            throw new BaseException(action + "原因不能为空");
        }
    }

    // ==========================================================================
    // 【公共逻辑 · 取消订单】被 3 处复用：
    //     卖家拒单 reject ／ 双方取消 cancel ／ 超时关单 timeoutClose
    //   三种场景的调用方式完全一样，差别只有：原因文案 + 终态(3已取消/4超时取消)
    // ==========================================================================
    /**
     * 【取消订单的公共逻辑】卖家拒单 与 买卖任一方取消 都走这里。
     *
     * 调用方负责各自不同的部分（这些不能放进来）：
     *   ① 权限校验：拒单只有卖家；取消买卖双方都行
     *   ② 状态校验：拒单必须"待确认"；取消在"交易前"都能做
     *
     * 这里只做"取消本身"必须做的三件事，
     * 依赖调用方方法上的 @Transactional 保证它们一起成功、或一起失败：
     *   ① 已支付 → 写一条退款流水
     *   ② 订单   → 已取消 + 原因（只有真付过钱才标"已退款"）
     *   ③ 商品   → 从"交易中"回退"在售"（条件更新 + 判影响行数）
     */
    private void doCancelOrder(TradeOrder order, String reason, Integer cancelStatus) {
        boolean paid = OrderConstant.PAY_PAID.equals(order.getPayStatus());

        // ① 已支付 → 写退款流水
        // 退款流水号必须【新生成一个】（前缀 REF）：
        //   payment_record.pay_no 上有唯一索引，复用原支付流水号会直接撞唯一键报错
        if (paid) {
            paymentRecordMapper.insert(PaymentRecord.builder()
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .payNo(generateFlowNo("REF"))
                    .payType(OrderConstant.PAY_TYPE_REFUND)
                    .amount(order.getRealPrice())
                    .remark("订单取消退款，原支付流水号=" + order.getPayNo())
                    .status(1)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        }

        // ② 订单 → 已取消
        // ⚠️ 只有【真的付过钱】才把 payStatus 标成"已退款"。
        //    否则那些从未支付的订单会被标成"已退款"——钱一分没收到，哪来的退款？
        TradeOrder.TradeOrderBuilder updateBuilder = TradeOrder.builder()
                .id(order.getId())
                .status(cancelStatus)
                .cancelReason(reason)
                .updateTime(LocalDateTime.now());
        if (paid) {
            updateBuilder.payStatus(OrderConstant.PAY_REFUNDED);
        }
        tradeOrderMapper.update(updateBuilder.build());

        // ③ 商品 → 交易中回退成在售
        // 用【条件更新】而不是无条件 updateById：
        //   只有商品当前确实是"交易中"才回退，避免把已下架/已售罄的商品错误复活成"在售"。
        // 影响 0 行 → 抛异常 → 整个事务回滚（退款流水也一起撤销），
        // 保证"订单、资金、商品"三者不会各说各话。
        int back = productMapper.updateStatusIfMatch(order.getProductId(),
                ProductConstant.IN_TRADE,
                ProductConstant.ON_SALE);
        if (back == 0) {
            throw new BaseException("商品状态异常，无法回退到在售");
        }

        // ④ 捡漏单：把 Redis 里预扣的库存还回去（S3-7c）
        //
        // ⚠️ 为什么必须做：Redis 不参与数据库事务，取消订单【不会自动】恢复它。
        //    不做的后果：商品明明已回到"在售"，Redis 库存却还是 0
        //    → 捡漏专区显示"抢购中"，一点却报"已经被抢光了" → 这件商品谁也拿不到。
        //
        // 为什么放在 ③ 之后：先确认商品真的回退成功了，再还名额；
        // 顺序反过来的话，③ 失败抛异常时库存已经加回去，这个名额就凭空多出来了。
        if (OrderConstant.SOURCE_FLASH_SALE.equals(order.getOrderSource())) {
            flashSaleStockUtil.restore(order.getProductId());
        }

        // ⑤ 跑腿单联动取消（这笔交易没走跑腿的话，内部会自己 return）
        //    为什么必须联动：主订单都取消了，跑腿员还跑去取货就是白跑一趟。
        //    已经送达的跑腿单不会被改（内部判断），保留跑腿员的劳动记录。
        deliveryService.cancelByTradeOrder(order.getId(), reason);
    }

    /**
     * 卖家拒单：待确认(0) → 已取消(3)
     */
    @Transactional
    @Override
    public void reject(TradeOrderCancelDTO cancelDTO) {
        TradeOrder order = getOrderOrThrow(cancelDTO.getId());

        // 只有卖家本人能拒单
        if (!BaseContext.getCurrentId().equals(order.getSellerId())) {
            throw new BaseException("您没有权限拒绝该订单");
        }
        // 只有"待确认"能拒单（已经确认过的订单不该再拒）
        if (!OrderConstant.STATUS_PENDING_CONFIRM.equals(order.getStatus())) {
            throw new BaseException("订单状态不是待确认，不能拒绝");
        }
        checkReason(cancelDTO.getReason(), "拒单");

        // 拒单/取消 → 终态是"已取消"(3)
        doCancelOrder(order, cancelDTO.getReason(), OrderConstant.STATUS_CANCELLED);
        log.info("卖家拒单成功：订单id={}，卖家id={}，是否退款={}",
                order.getId(), order.getSellerId(), OrderConstant.PAY_PAID.equals(order.getPayStatus()));
    }

    /**
     * 取消订单（交易前，买卖任一方都可以）
     */
    @Transactional
    @Override
    public void cancel(TradeOrderCancelDTO cancelDTO) {
        TradeOrder order = getOrderOrThrow(cancelDTO.getId());

        Long me = BaseContext.getCurrentId();
        // 买卖双方都能取消；跟这单无关的人不行
        if (!me.equals(order.getBuyerId()) && !me.equals(order.getSellerId())) {
            throw new BaseException("您没有权限取消该订单");
        }
        // "交易前"= 待确认(0) 或 待交易(1)；已完成/已取消/超时取消都是终态，不能再取消
        boolean beforeTrade = OrderConstant.STATUS_PENDING_CONFIRM.equals(order.getStatus())
                || OrderConstant.STATUS_PENDING_TRADE.equals(order.getStatus());
        if (!beforeTrade) {
            throw new BaseException("订单已进入终态，不能取消");
        }
        checkReason(cancelDTO.getReason(), "取消");

        // 拒单/取消 → 终态是"已取消"(3)
        doCancelOrder(order, cancelDTO.getReason(), OrderConstant.STATUS_CANCELLED);
        log.info("订单已取消：订单id={}，操作人={}，是否退款={}",
                order.getId(), me, OrderConstant.PAY_PAID.equals(order.getPayStatus()));
    }

    @Transactional
    @Override
    public void complete(Long orderId) {
        //先查询本人的id
        Long userId = BaseContext.getCurrentId();

        //查订单，看订单是否存在
        TradeOrder order = tradeOrderMapper.getById(orderId);
        if(order == null){
            throw new BaseException("订单不存在");
        }

        //只有买家本人才能够确认收货
        if(!userId.equals(order.getBuyerId())){
            throw new BaseException("您没有权限确认收货");
        }

        //订单状态必须是待交易
        if(!OrderConstant.STATUS_PENDING_TRADE.equals(order.getStatus())){
            throw new BaseException("订单状态不是待交易，不能确认收货");
        }

        //更新订单状态：修改为已完成+完成时间
        tradeOrderMapper.update(TradeOrder.builder()
                .id(order.getId())
                .status(OrderConstant.STATUS_COMPLETED)
                .finishTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());

        //将商品状态从交易中变成已售罄
        int back = productMapper.updateStatusIfMatch(order.getProductId(),
                ProductConstant.IN_TRADE,
                ProductConstant.SOLD_OUT);

        if(back == 0){
            throw new BaseException("商品状态异常，无法确认收货");
        }

        //写一条结算流水
        paymentRecordMapper.insert(PaymentRecord.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .payNo(generateFlowNo("SET"))
                .payType(OrderConstant.PAY_TYPE_SETTLE)
                .amount(order.getRealPrice())
                .remark("买家确认收货，原支付流水号=" + order.getPayNo())
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());

        log.info("买家确认收货：订单id={},买家={}",order.getId(),userId);
    }

    /**
     * 超时关单（供定时任务调用）
     *
     * 参数为什么是 orderId 而不是整个 TradeOrder 对象：
     *   定时任务扫完列表、到真正处理之间可能过了几十毫秒，
     *   这期间买家可能刚好付款成功。如果直接信任列表里的旧对象，
     *   就会按"未支付"处理 → 取消但不退款 → 买家的钱丢了。
     *   所以只传 id，这里重新查最新状态。
     *
     * 三个 guard（守卫）判断的写法要领：
     *   这里的 return 条件是「这单不该我处理」，而不是「这单正是我要处理的」。
     *   写成后者最容易把条件写反（比如"status=0 就 return"——那正是要处理的单子）。
     */
    @Transactional
    @Override
    public void timeoutClose(Long orderId) {
        // ① 重查一次，拿最新状态
        TradeOrder order = tradeOrderMapper.getById(orderId);
        if (order == null) {
            return;
        }
        // ② 状态已经变了就放手走开（status≠0 说明已被卖家确认 / 被取消 / 已完成）
        if (!OrderConstant.STATUS_PENDING_CONFIRM.equals(order.getStatus())) {
            return;
        }
        // ③ 还没到点也放手走开（expireTime 为 null 视为不超时）
        if (order.getExpireTime() == null || order.getExpireTime().isAfter(LocalDateTime.now())) {
            return;
        }

        // ④ 交给公共逻辑：
        //    它内部会自动判断"已支付就写退款流水、未支付就不写"，并回退商品状态；
        //    终态传"超时取消(4)"而不是"已取消(3)"，因为这是超时关单，语义不同。
        log.info("超时关单：orderId={}，是否已支付={}",
                order.getId(), OrderConstant.PAY_PAID.equals(order.getPayStatus()));
        doCancelOrder(order, "订单超时未处理，系统自动关闭", OrderConstant.STATUS_TIMEOUT_CANCELLED);
    }
}
