package com.campus.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.campus.constant.BargainConstant;
import com.campus.constant.MessageConstant;
import com.campus.constant.ProductConstant;
import com.campus.context.BaseContext;
import com.campus.dto.BargainCounterDTO;
import com.campus.dto.BargainPageQueryDTO;
import com.campus.dto.BargainSubmitDTO;
import com.campus.entity.Bargain;
import com.campus.entity.Product;
import com.campus.entity.TradeOrder;
import com.campus.exception.BaseException;
import com.campus.mapper.BargainMapper;
import com.campus.mapper.ProductMapper;
import com.campus.result.PageResult;
import com.campus.service.BargainService;
import com.campus.service.TradeOrderService;
import com.campus.vo.BargainVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
public class BargainServiceImpl implements BargainService {

    @Autowired
    private BargainMapper bargainMapper;

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private TradeOrderService tradeOrderService;

    /**
     * 买家发起议价
     *
     * 结构提示：前面全是「守卫判断」（不满足就抛异常走开），最后一步才是落库。
     * 守卫条件的思维模式跟 timeoutClose 一样：先写清楚"什么情况下不该做这件事"。
     */
    @Transactional
    @Override
    public void submit(BargainSubmitDTO submitDTO) {
        Long buyerId = BaseContext.getCurrentId();

        // ① 查商品 + 不存在校验（照抄）
        Product product = productMapper.getById(submitDTO.getProductId());
        if (product == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }

        // ② 【填空 1】守卫：不能议价自己发布的商品
        //    提示：拿 product.getSellerId() 和 buyerId 比（注意 null 安全，常量/已知非空放前面）
        //    失败信息："不能议价自己发布的商品"
        //    ← 你写
        if(buyerId.equals(product.getSellerId())){
            throw new BaseException("不能议价自己发布的商品");
               }

        // ③ 【填空 2】守卫：商品必须「已过审」且「在售」
        //    提示：product.getAuditStatus() 用 ProductConstant.AUDIT_PASSED 比
        //         product.getStatus() 用 ProductConstant.ON_SALE 比
        //    失败信息：分别是 "商品还未通过审核，暂时不能议价" / "商品当前不可议价（可能已被拍下或已下架）"
        //    ← 你写
        if(!ProductConstant.AUDIT_PASSED.equals(product.getAuditStatus())){
            throw new BaseException("商品还未通过审核，暂时不能议价");
        }
        if(!ProductConstant.ON_SALE.equals(product.getStatus())){
            throw new BaseException("商品当前不可议价（可能已被拍下或已下架）");
        }

        // ④ 【填空 3】守卫：出价必须 ≥ 商品标价 × 50%
        //    提示：① 先判出价非空、且大于 0
        //         ② 下限 = product.getPrice().multiply(BargainConstant.MIN_OFFER_RATE)
        //         ③ ⚠️ BigDecimal 比较大小必须用 compareTo（用 equals 会因为"25.0 vs 25.00"这种精度差异出错）
        //    失败信息："出价不能为空" / "出价必须大于 0" / "出价不能低于标价的 50%"
        //    ← 你写
        // 先判空（必须在使用之前），再判断大小 —— 顺序不能反
        if (submitDTO.getBuyerOffer() == null) {
            throw new BaseException("出价不能为空");
        }
        if (submitDTO.getBuyerOffer().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException("出价必须大于 0");
        }
        // 下限用常量算，不写死 ÷2：
        //   业务规则（"不低于 50%"）只能有一个出处，改了常量这三处就一起变；
        //   写成 divide(2) 的话，哪天改成 60%，这段代码会静静地继续按 50% 算。
        BigDecimal minOffer = product.getPrice().multiply(BargainConstant.MIN_OFFER_RATE);
        if (submitDTO.getBuyerOffer().compareTo(minOffer) < 0) {
            throw new BaseException("出价不能低于标价的 50%");
        }

        // ⑤ 【填空 4】守卫：同一买家对同一商品不能有「进行中」的议价
        //    提示：用 bargainMapper.countInProgress(buyerId, product.getId())
        //    失败信息："你已经对这件商品出过价了，等对方回应吧"
        //    ← 你写
        int count = bargainMapper.countInProgress(buyerId, product.getId());
        if(count > 0){
            throw new BaseException("你已经对这件商品出过价了，等对方回应吧");
        }

        // ⑥ 【填空 5】守卫：被拒绝后 24 小时内不能再议
        //    提示：用 bargainMapper.getLastRejected(buyerId, product.getId())
        //         拿到的是最近一条"已拒绝"的记录（可能为 null）
        //         如果它的 updateTime 晚于"现在 - 24小时"，就拒绝
        //    失败信息："对方刚拒绝过你的议价，24 小时后可以再试"
        //    ← 你写
        Bargain lastRejected = bargainMapper.getLastRejected(buyerId, product.getId());

        if(lastRejected != null){
            // 注意用的是 REJECT_COOLDOWN_HOURS（拒后冷却期），不是 EXPIRE_HOURS（议价有效期）：
            // 两者碰巧都是 24 小时，但语义不同，将来只改其中一个时不能互相影响
            if(lastRejected.getUpdateTime().isAfter(LocalDateTime.now().minusHours(BargainConstant.REJECT_COOLDOWN_HOURS))){
                throw new BaseException("对方刚拒绝过你的议价，24 小时后可以再试");
            }
        }

        // ⑦ 落库（照抄）
        Bargain bargain = Bargain.builder()
                .sellerId(product.getSellerId())
                .buyerId(buyerId)
                .productId(product.getId())
                .buyerOffer(submitDTO.getBuyerOffer())
                .bargainStatus(BargainConstant.STATUS_BUYER_OFFER)
                .expireTime(LocalDateTime.now().plusHours(BargainConstant.EXPIRE_HOURS))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        bargainMapper.insert(bargain);

        log.info("议价发起成功：bargainId={}, buyerId={}, productId={}, 出价={}",
                bargain.getId(), buyerId, product.getId(), bargain.getBuyerOffer());
    }

    /**
     * 我的议价分页
     * 跟订单列表同一套路：view 决定查哪一边，用户 id 只从登录态取
     */
    @Override
    public PageResult pageQuery(BargainPageQueryDTO query) {
        // ① 【填空 6】先清空 buyerId / sellerId（挡前端伪造），再按 view 填当前用户 id
        //    提示：view=seller → setSellerId(我)；否则 → setBuyerId(我)
        //         常量放前面防 NPE
        //    ← 你写
        query.setBuyerId(null);
        query.setSellerId(null);

        Long userId = BaseContext.getCurrentId();

        if("seller".equals(query.getView())){
            query.setSellerId(userId);
        }else{
            query.setBuyerId(userId);
        }

        // ② 分页 + 查询 + 包装（照抄）
        PageHelper.startPage(query.getPage(), query.getPageSize());
        Page<BargainVO> page = bargainMapper.pageQuery(query);
        return new PageResult(page.getTotal(), page.getResult());
    }

    // ==================== 以下三个方法属于 S3-6b，下一轮再写 ====================

    @Transactional
    @Override
    public void accept(Long id) {
        Long me = BaseContext.getCurrentId();

        Bargain bargain = bargainMapper.getById(id);
        if(bargain == null){
            throw new BaseException("议价记录不存在");
        }

        // ② 按状态判断"谁有权接受"
        // ┌── 【错题标记 #7】权限判断用了「排除法」而不是「允许法」🔴安全级 ──
        // │ 你当时写的：if (me.equals(bargain.getBuyerId())) throw "买家不能接受自己的出价";
        // │ 为什么错：这只排除了"买家"，但跟这笔议价【毫无关系的第三人】呢？
        // │           陌生人的 me 既不等于 buyerId 也不等于 sellerId，
        // │           两个 if 都不命中 → 直接放行 → 他能替别人成交、锁掉别人的商品！
        // │ 正确写法（白名单）：if (!me.equals(bargain.getSellerId())) throw ...
        // │   你永远列不全"不该做的人"（陌生人、管理员、跑腿员…），
        // │   但你能准确说出"该做的人是谁"。白名单是封闭的，黑名单是开放的。
        // │ 附带：提示语也要跟着改 —— 现在拦的是"非卖家"，
        // │   就该说"只有卖家可以接受这个报价"，而不是"买家不能接受自己的出价"
        // │   （否则陌生人会收到一条跟他无关的提示，莫名其妙）
        // └──────────────────────────────────────────────────────────────
        if(BargainConstant.STATUS_BUYER_OFFER.equals(bargain.getBargainStatus())){
            // 状态1：买家已出价 → 只有卖家能接受
            if(!me.equals(bargain.getSellerId())){
                throw new BaseException("只有卖家可以接受这个报价");
            }
        }else if(BargainConstant.STATUS_SELLER_COUNTER.equals(bargain.getBargainStatus())){
            // 状态2：卖家已还价 → 只有买家能接受
            if(!me.equals(bargain.getBuyerId())){
                throw new BaseException("只有买家可以接受这个报价");
            }
        }else{
            throw new BaseException("议价已结束，不能再操作");
        }

        // ③ 过期校验：到点了就不能再成交
        // ┌── 【错题标记 #6】过期校验连踩两个坑（一个致命、一个写反）────────
        // │ 你当时写的：if (bargain.getOrderId() == null
        // │                 || LocalDateTime.now().isBefore(bargain.getExpireTime())) throw "已过期";
        // │
        // │ 坑① 拿 orderId 判空 —— 【致命】
        // │     orderId 只有在"议价成交"之后才会被回填，正常待处理的议价它本来就是 null。
        // │     拿它当拒绝条件 → 每一次"接受报价"都会失败，功能直接全废。
        // │     要判空的是 expireTime（建表时 DEFAULT NULL），不是 orderId。
        // │
        // │ 坑② 方向写反了
        // │     now.isBefore(expireTime) 的意思是"【还】没到期"，
        // │     却拿它去抛"已过期" → 条件成立与否完全相反。
        // │     正确读法：expireTime.isBefore(now) = 过期时间已经早于现在 = 真的过期了。
        // │
        // │ 这是本项目第 3 次"守卫条件写反"（前两次在超时关单），
        // │ 写完 if 一定要读一遍："这个条件成立时，我是在拦还是在放？"
        // └──────────────────────────────────────────────────────────────
        if (bargain.getExpireTime() == null
                || bargain.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BaseException("议价已过期，请重新发起");
        }

        // ④ 查商品：议价期间可能已经被别人买走了（照抄）
        Product product = productMapper.getById(bargain.getProductId());
        if (product == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }
        if (!ProductConstant.ON_SALE.equals(product.getStatus())) {
            throw new BaseException("商品已被别人买走或已下架，议价无法成交");
        }

        // ⑤ 成交价 = 最后一次报价（有卖家还价就用还价，否则用买家出价）
        //    实体里已经给你写好了 currentOffer() 方法，直接用
        BigDecimal dealPrice = bargain.currentOffer();

        //调用订单模块生成订单
        TradeOrder order = tradeOrderService.createBargainOrder(product,bargain.getBuyerId(),dealPrice);
        
        // ⑦ 回填议价：状态改"已接受" + 写下订单 id（照抄）
        bargainMapper.update(Bargain.builder()
                .id(bargain.getId())
                .bargainStatus(BargainConstant.STATUS_ACCEPTED)
                .orderId(order.getId())
                .updateTime(LocalDateTime.now())
                .build());

        log.info("议价成交：bargainId={}, orderId={}, 成交价={}", bargain.getId(), order.getId(), dealPrice);





    }

    // ==========================================================================
    // 下面两个方法的"重复骨架"已搭好（查记录、写状态、打日志）
    // 你只需写标注了【你的部分】的地方 —— 那才是各自不同的业务逻辑
    // ==========================================================================

    @Transactional
    @Override
    public void reject(Long id) {
        Long me = BaseContext.getCurrentId();

        // ① 查议价记录 + 不存在校验（和 accept 完全一样）
        Bargain bargain = bargainMapper.getById(id);
        if (bargain == null) {
            throw new BaseException("议价记录不存在");
        }

        // ② 按状态判断"谁有权拒绝"（一律用「允许法」：只有该做的人能过）
        // ┌── 【错题标记 #8】整段权限校验被漏写了（当时这里是空的）──────────
        // │ 当时的情况：骨架留了"【你的部分 1】"，但没有填 → 这一段不存在。
        // │ 后果：接口不会报错、日志一切正常，但【任何人都能拒绝别人的议价】。
        // │       这就是"骨架能跑 ≠ 功能正确"最危险的地方 ——
        // │       漏掉权限校验不会有任何报错，只有攻击者会笑。
        // │ 规矩：写任何"改变状态"的接口时，第一件事就是问
        // │       "谁有权做这件事？"，并且用白名单写出来。
        // │ 状态 1（买家出价）→ 只有【卖家】能拒绝
        // │ 状态 2（卖家还价）→ 只有【买家】能拒绝
        // │ 其它状态（3已接受/4已拒绝/5已过期）→ "议价已结束，不能再操作"
        // └──────────────────────────────────────────────────────────────
        if (BargainConstant.STATUS_BUYER_OFFER.equals(bargain.getBargainStatus())) {
            if (!me.equals(bargain.getSellerId())) {
                throw new BaseException("只有卖家可以拒绝这个报价");
            }
        } else if (BargainConstant.STATUS_SELLER_COUNTER.equals(bargain.getBargainStatus())) {
            if (!me.equals(bargain.getBuyerId())) {
                throw new BaseException("只有买家可以拒绝这个报价");
            }
        } else {
            throw new BaseException("议价已结束，不能再操作");
        }

        // ③ 状态改成"已拒绝"（照抄：动态更新只会改传了的字段）
        bargainMapper.update(Bargain.builder()
                .id(bargain.getId())
                .bargainStatus(BargainConstant.STATUS_REJECTED)
                .updateTime(LocalDateTime.now())
                .build());

        log.info("议价被拒绝：bargainId={}, 操作人={}", bargain.getId(), me);
    }

    @Transactional
    @Override
    public void counter(BargainCounterDTO counterDTO) {
        Long me = BaseContext.getCurrentId();

        // ① 查议价记录 + 不存在校验（同上）
        Bargain bargain = bargainMapper.getById(counterDTO.getId());
        if (bargain == null) {
            throw new BaseException("议价记录不存在");
        }

        // ② 只有【卖家】能还价（允许法：除了卖家谁都不行）
        if(!me.equals(bargain.getSellerId())){
            throw new BaseException("只有卖家可以还价");
        }

        // ③ 过期校验：议价过期后是「死状态」，不该再产生新动作
        //    和 accept 保持一致。不加这一条的话会出现很别扭的状态：
        //    卖家给一条已过期的议价还了价 → 议价"活"了过来（状态变成2），
        //    但买家去接受时又会被过期校验拦住 —— 双方都不知道到底能不能成交。
        if (bargain.getExpireTime() == null
                || bargain.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BaseException("议价已过期，请重新发起");
        }

        // ④ 只有状态 1（买家出价）能还价 —— 单轮制！
        //    先单独判断"是不是已经还过价了"，这样文案更准确
        if(BargainConstant.STATUS_SELLER_COUNTER.equals(bargain.getBargainStatus())){
            throw new BaseException("卖家已还价，只能接受或拒绝");
        }else if(!BargainConstant.STATUS_BUYER_OFFER.equals(bargain.getBargainStatus())){
            throw new BaseException("议价已结束，不能再操作");
        }

        // ⑤ 还价金额校验（三段，和"发起议价"的判空套路一模一样）
        //    非空              → "还价金额不能为空"
        //    > 0               → "还价金额必须大于 0"
        //    > 买家出价         → "还价必须高于买家的出价（否则你可以直接接受）"
        //    提示：第三段用 counterDTO.getCounterOffer().compareTo(bargain.getBuyerOffer()) <= 0
        //         （BigDecimal 比大小只能用 compareTo）
        //    ← 你写
        if(counterDTO.getCounterOffer() == null){
            throw new BaseException("还价金额不能为空");
        }
        if(counterDTO.getCounterOffer().compareTo(BigDecimal.ZERO) <= 0){
            throw new BaseException("还价金额必须大于 0");
        }
        if(counterDTO.getCounterOffer().compareTo(bargain.getBuyerOffer()) <= 0){
            throw new BaseException("还价必须高于买家的出价（否则你可以直接接受）");
        }

        // ⑤ 写入还价 + 状态改成"卖家还价"（照抄）
        bargainMapper.update(Bargain.builder()
                .id(bargain.getId())
                .sellerCounterOffer(counterDTO.getCounterOffer())
                .bargainStatus(BargainConstant.STATUS_SELLER_COUNTER)
                .updateTime(LocalDateTime.now())
                .build());

        log.info("卖家还价：bargainId={}, 买家出价={}, 还价={}",
                bargain.getId(), bargain.getBuyerOffer(), counterDTO.getCounterOffer());
    }

}
