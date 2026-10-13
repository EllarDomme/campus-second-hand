package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.OrderConstant;
import com.sky.constant.ProductConstant;
import com.sky.context.BaseContext;
import com.sky.dto.TradeOrderPageQueryDTO;
import com.sky.dto.TradeOrderRejectionDTO;
import com.sky.dto.TradeOrderSubmitDTO;
import com.sky.entity.PaymentRecord;
import com.sky.entity.Product;
import com.sky.entity.TradeOrder;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.mapper.PaymentRecordMapper;
import com.sky.mapper.ProductMapper;
import com.sky.mapper.TradeOrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;
import com.sky.service.TradeOrderService;
import com.sky.vo.TradeOrderPayVO;
import com.sky.vo.TradeOrderSubmitVO;
import com.sky.vo.TradeOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;   // 填空④ 会用到这个异常
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class TradeOrderServiceImpl implements TradeOrderService {

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;
    @Autowired
    private UserMapper userMapper;

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

        // ---------- ② 锁定商品（这是"一件商品只能卖给一个人"的关键）----------
        // 用「条件更新」而不是"先查再改"：
        //   先查再改在并发下会失效（两个人同时查到"在售"，然后都去改成"交易中"）。
        //   条件更新把判断和修改合成一条 SQL，靠数据库行锁保证原子性：
        //   只有第一条 update 能影响 1 行，第二条影响 0 行（因为 status 已经不是 0 了）。
        int locked = productMapper.updateStatusIfMatch(
                product.getId(), ProductConstant.ON_SALE, ProductConstant.IN_TRADE);
        if (locked == 0) {
            throw new BaseException("手慢了，商品已被别人拍下");
        }

        // ---------- ③ 创建订单 ----------
        TradeOrder order = TradeOrder.builder()
                .orderNo(generateOrderNo())
                .sellerId(product.getSellerId())
                .buyerId(buyerId)
                .productId(product.getId())
                // 成交价取商品标价：普通下单没有议价环节，价格由后端定，不接受前端传
                .realPrice(product.getPrice())
                .dealType(submitDTO.getDealType() == null
                        ? OrderConstant.DEAL_SELF_PICKUP : submitDTO.getDealType())
                .orderSource(OrderConstant.SOURCE_NORMAL)
                // 订单状态"待确认" + 支付状态"未支付" = 前端看到的"待支付"
                .status(OrderConstant.STATUS_PENDING_CONFIRM)
                .payStatus(OrderConstant.PAY_UNPAID)
                .pickupPlace(submitDTO.getPickupPlace())
                .remark(submitDTO.getRemark())
                // 未支付超时：普通订单 15 分钟
                .expireTime(LocalDateTime.now().plusMinutes(OrderConstant.EXPIRE_MINUTES_NORMAL))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        tradeOrderMapper.insert(order);

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
        String payNo = generatePayNo();
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

    /**
     * 生成支付流水号：PAY + 年月日时分秒 + 4 位随机数
     */
    private String generatePayNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "PAY" + time + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /**
     * 生成退款流水号：REF + 年月日时分秒 + 4 位随机数
     *
     * 必须和支付流水号用不同前缀，两个原因：
     *   ① payment_record.pay_no 有唯一索引，退款不能复用原支付流水号，否则撞唯一键
     *   ② 前缀区分开后，看流水表时一眼能分清"收款"和"退款"
     */
    private String generateRefundNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "REF" + time + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

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


    @Transactional
    @Override
    public void reject(TradeOrderRejectionDTO rejectionDTO) {
        //查订单，不存在的话就报错
        TradeOrder order = tradeOrderMapper.getById(rejectionDTO.getId());
        if(order == null){
            throw new BaseException("订单不存在");
        }

        Long userId = BaseContext.getCurrentId();

        //只有卖家本人才能够拒绝
        if(!userId.equals(order.getSellerId())){
            throw new BaseException("您没有权限拒绝该订单");
        }

        //状态：必须待确认
        if(!order.getStatus().equals(OrderConstant.STATUS_PENDING_CONFIRM)) {
            throw new BaseException("订单状态不是待确认，不能拒绝");
        }

        //拒单原因不能为空（trim 后判断，"   " 这种全空格也算空）
        if(rejectionDTO.getReason() == null || rejectionDTO.getReason().trim().isEmpty()){
            throw new BaseException("拒单原因不能为空");
        }

        //如果已支付，就写一条退款流水记录
        //退款流水号必须【新生成一个】（前缀用 REF）：
        //   payment_record.pay_no 上有唯一索引，若复用原支付流水号会直接撞唯一键报错；
        //   而且退款和收款前缀区分开，对账时一眼能看出哪条是退钱。
        if(OrderConstant.PAY_PAID.equals(order.getPayStatus())){
            paymentRecordMapper.insert(PaymentRecord.builder()
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .payNo(generateRefundNo())
                    .payType(OrderConstant.PAY_TYPE_REFUND)
                    .amount(order.getRealPrice())
                    .remark("卖家拒单退款，原支付流水号=" + order.getPayNo())
                    .status(1)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        }

        //更新订单：状态改"已取消"
        //⚠️ 只有【真的付过钱】才把 payStatus 标成"已退款"。
        //   如果无条件写 2，那些从未支付的订单会被标成"已退款"——钱一分没收到，哪来的退款？
        TradeOrder.TradeOrderBuilder updateBuilder = TradeOrder.builder()
                .id(order.getId())
                .status(OrderConstant.STATUS_CANCELLED)
                .cancelReason(rejectionDTO.getReason())
                .updateTime(LocalDateTime.now());
        if (OrderConstant.PAY_PAID.equals(order.getPayStatus())) {
            updateBuilder.payStatus(OrderConstant.PAY_REFUNDED);
        }
        tradeOrderMapper.update(updateBuilder.build());

        //商品回退：交易中 → 在售
        //用【条件更新】而不是无条件 updateById：
        //   只有商品当前确实是"交易中"才回退，避免把已下架/已售罄的商品错误复活成"在售"。
        //返回 0 说明商品状态不是"交易中"，此时抛异常 → 整个事务回滚（退款流水也一起撤销），
        //保证"订单、资金、商品"三者不会各说各话。
        int back = productMapper.updateStatusIfMatch(order.getProductId(),
                ProductConstant.IN_TRADE,
                ProductConstant.ON_SALE);
        if (back == 0) {
            throw new BaseException("商品状态异常，无法回退到在售");
        }

        log.info("卖家拒单成功：订单id={}，卖家id={}，是否退款={}",
                order.getId(), userId, OrderConstant.PAY_PAID.equals(order.getPayStatus()));
    }
}
