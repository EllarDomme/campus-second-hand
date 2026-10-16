package com.sky.service.impl;

import com.sky.constant.CreditConstant;
import com.sky.constant.OrderConstant;
import com.sky.context.BaseContext;
import com.sky.dto.ReviewSubmitDTO;
import com.sky.entity.Review;
import com.sky.entity.TradeOrder;
import com.sky.exception.BaseException;
import com.sky.mapper.ReviewMapper;
import com.sky.mapper.TradeOrderMapper;
import com.sky.service.CreditService;
import com.sky.service.ReviewService;
import com.sky.vo.ReviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易评价服务实现
 *
 * ==========================================================================
 * 【你的任务】把 submit 的【填空 1~6】写完
 *   已替你写好（不用改）：listByProduct / listByToUser / mySentReviews
 * ==========================================================================
 */
@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    /** 评价文字最长 500（数据库 review.content varchar(500)） */
    private static final int MAX_CONTENT_LENGTH = 500;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private CreditService creditService;

    /**
     * 提交评价
     *
     * 顺序：参数校验 → 订单校验 → 确定被评价人 → 查重 → 落库 → 信用分联动
     */
    @Transactional
    @Override
    public void submit(ReviewSubmitDTO submitDTO) {
        Long me = BaseContext.getCurrentId();

        // ================= 你的部分：填空 1 ~ 6 =================
        //
        // 【填空 1】参数校验（三段，照抄发起议价的判空套路）
        //   · orderId == null                        → "订单id不能为空"
        //   · star == null 或 star < 1 或 star > 5    → "星级必须在 1~5 之间"
        //   · content 不为 null 且 trim 后长度 > MAX_CONTENT_LENGTH → "评价太长了（最多 500 字）"
        //   （content 允许为空 —— 只打星不写字是正常的）
        if(submitDTO.getOrderId() == null){
            throw new BaseException("订单id不能为空");
        }
        if(submitDTO.getStar() == null || submitDTO.getStar() < 1 || submitDTO.getStar() > 5){
            throw new BaseException("星级必须在 1~5 之间");
        }
        // ┌── 【错题标记 #20】判空写成了"或"，把合法输入给拒了 ──────────────
        // │ 你当时写的：if (content == null || content.trim().length() > 500) throw "评价太长了（最多5000字）"
        // │
        // │ 错在哪（两个问题）：
        // │   ① 条件方向：评价文字是【允许为空】的（只打星不写字很正常）。
        // │      按你这个写法，content == null 也会进这个分支 ——
        // │      结果是"凡是没写字的评价一律被拒"，而且提示还说"太长了"，让人一头雾水。
        // │      正确条件是"不为 null 【且】超长"，两个条件用 && 连接。
        // │   ② 文案：上限是 500（数据库 varchar(500)），你写成了 5000，
        // │      用户按提示写到 3000 字还是被拒，会觉得系统在骗他。
        // │      建议：直接引用常量拼字符串，改上限时文案自动跟着变。
        // │
        // │ 记法：判空和范围校验连用时，先问一句"空值本身算不算违规"。
        // │      允许为空 → 用 != null 短路放行；不允许为空 → 单独一段判空，
        // │      别把两件事混在一个条件里。
        // └──────────────────────────────────────────────────────────────
        if (submitDTO.getContent() != null
                && submitDTO.getContent().trim().length() > MAX_CONTENT_LENGTH) {
            throw new BaseException("评价太长了（最多 " + MAX_CONTENT_LENGTH + " 字）");
        }


        // 【填空 2】订单校验（四个守卫）
        //   TradeOrder order = tradeOrderMapper.getById(submitDTO.getOrderId());
        //   · order == null                          → "订单不存在"
        //   · 我既不是买家也不是卖家                    → "无权评价该订单"
        //   · !OrderConstant.STATUS_COMPLETED.equals(order.getStatus())
        //                                            → "只有已完成的订单才能评价"
        //   ⚠️ 第四个守卫就是本模块的"业务边界"：没成交就评价 = 刷信誉。
        //      常量是 OrderConstant.STATUS_COMPLETED（已完成=2），记得 import。
        TradeOrder tradeOrder = tradeOrderMapper.getById(submitDTO.getOrderId());
        if(tradeOrder == null){
            throw new BaseException("订单不存在");
        }
        if(!me.equals(tradeOrder.getBuyerId()) && !me.equals(tradeOrder.getSellerId())){
            throw new BaseException("无权评价该订单");
        }
        if(!OrderConstant.STATUS_COMPLETED.equals(tradeOrder.getStatus())){
            throw new BaseException("只有完成了的订单才能够评价");
        }
        // 【填空 3】确定"被评价人"：我是买家就评卖家，我是卖家就评买家
        //   Long toUserId = me.equals(order.getBuyerId()) ? order.getSellerId() : order.getBuyerId();
        //   （绝对不能让前端传 toUserId —— 否则可以伪造"我给任意人打差评"）
        Long toUserId = me.equals(tradeOrder.getBuyerId()) ? tradeOrder.getSellerId() : tradeOrder.getBuyerId();
        // 【填空 4】查重：我有没有评过这笔订单
        //   · reviewMapper.getByOrderAndFrom(orderId, me) != null → "你已经评价过这笔订单了"
        if(reviewMapper.getByOrderAndFrom(tradeOrder.getId(),me) != null){
            throw new BaseException("你已经评价过这笔订单了");
        };
        // 【填空 5】落库
        //   ⚠️ 并发下（同一个人连点两次）会撞唯一索引 uk_order_from，
        //      要 try { insert } catch (DuplicateKeyException e) { throw new BaseException("你已经评价过这笔订单了"); }
        //   Review.builder() 需要的字段：
        //       orderId = submitDTO.getOrderId()
        //       productId = order.getProductId()      ← 从订单拿，不接受前端传
        //       fromUserId = me
        //       toUserId = 填空 3 算出来的
        //       star / content（content 为 null 就存 null，别存空字符串）
        //       isDefault = 0                          ← 用户提交的一律是真实评价
        //       createTime / updateTime = now
        Review review = Review.builder()
                .orderId(tradeOrder.getId())
                .productId(tradeOrder.getProductId())
                .fromUserId(me)
                .toUserId(toUserId)
                .star(submitDTO.getStar())
                .content(submitDTO.getContent())
                .isDefault(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // ┌── 【错题标记 #21】漏了并发兜底（唯一索引冲突没接住）──────────────
        // │ 你当时写的：直接 reviewMapper.insert(review);
        // │ 为什么错：两个请求（比如用户连点两次"提交评价"）会同时通过【填空 4】的查重，
        // │   然后都去 insert → 第二个撞唯一索引 uk_order_from →
        // │   抛 DuplicateKeyException → 用户看到一个数据库异常 500，
        // │   但他其实什么都没做错（只是手快）。
        // │ 正确做法：catch 住它，翻译成业务提示"你已经评价过这笔订单了"。
        // │ 这已经是本项目第 N 次用这个套路了（收藏、支付幂等、建会话、抢购…）：
        // │   查重负责"友好提示"，唯一索引负责"并发兜底"，兜底触发时【优雅降级】。
        // └──────────────────────────────────────────────────────────────
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            throw new BaseException("你已经评价过这笔订单了");
        }

        // 【填空 6】信用分联动（⭐ 这就是"信用分"和"评价"的接缝）
        //   · star >= CreditConstant.GOOD_REVIEW_STAR(4)  → 加 CreditConstant.SCORE_GOOD_REVIEW(+1)
        //   · star <= CreditConstant.BAD_REVIEW_STAR(2)   → 加 CreditConstant.SCORE_BAD_REVIEW(-2)
        //   · 3 星（中评）                                → 不加不减，不调用
        //   调用 creditService.changeCredit(
        //           toUserId, 分值, CreditConstant.REASON_GOOD_REVIEW 或 REASON_BAD_REVIEW,
        //           order.getId(), "订单评价：" + star + "星");
        //   提示：写成一个 if / else if，不要用三元表达式堆在一起
        //
        // =======================================================
        if(review.getStar() >= CreditConstant.GOOD_REVIEW_STAR){
            creditService.changeCredit(toUserId,CreditConstant.SCORE_GOOD_REVIEW,CreditConstant.REASON_GOOD_REVIEW,tradeOrder.getId(),"订单评价" + "评分:" + review.getStar() + "星");
        }
        else if(review.getStar() <= CreditConstant.BAD_REVIEW_STAR){
            creditService.changeCredit(toUserId,CreditConstant.SCORE_BAD_REVIEW,CreditConstant.REASON_BAD_REVIEW,tradeOrder.getId(),"订单评价" + "评分:" + review.getStar() + "星");
        }

    }

    /** 某商品的评价列表（已写好） */
    @Override
    public List<ReviewVO> listByProduct(Long productId) {
        return reviewMapper.listByProduct(productId);
    }

    /** 某人收到的评价列表（已写好） */
    @Override
    public List<ReviewVO> listByToUser(Long toUserId) {
        return reviewMapper.listByToUser(toUserId);
    }

    /** 我发出的评价（已写好） */
    @Override
    public List<Review> mySentReviews() {
        return reviewMapper.listByFromUser(BaseContext.getCurrentId());
    }

}
