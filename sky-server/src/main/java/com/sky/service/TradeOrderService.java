package com.sky.service;

import com.sky.dto.TradeOrderPageQueryDTO;
import com.sky.dto.TradeOrderCancelDTO;
import com.sky.dto.TradeOrderSubmitDTO;
import com.sky.entity.Product;
import com.sky.entity.TradeOrder;
import com.sky.result.PageResult;
import com.sky.vo.TradeOrderPayVO;
import com.sky.vo.TradeOrderSubmitVO;
import com.sky.vo.TradeOrderVO;

import java.math.BigDecimal;

/**
 * 交易订单服务（校园二手）
 */
public interface TradeOrderService {

    /**
     * 买家下单（普通商品直接下单；议价/捡漏下单由后续步骤补充）
     *
     * 里面做了两件关键的事：
     *   ① 校验商品可买（存在、已过审、在售、不是自己发的）
     *   ② 把商品从"在售"锁成"交易中"（条件更新，保证并发下只有一个人能成功）
     */
    TradeOrderSubmitVO submit(TradeOrderSubmitDTO submitDTO);

    /**
     * 发起支付（模拟通道）
     *
     * 校验订单可支付（存在、是自己的、未支付、未超时），
     * 生成支付流水号并写回订单，返回给前端去"调支付"。
     */
    TradeOrderPayVO pay(String orderNo);

    /**
     * 处理支付成功通知（模拟回调）
     *
     * ⚠️ 核心是幂等：支付平台的通知可能重复送达，同一笔支付只能被处理一次。
     * 两层防护：① 先查订单状态，已支付就直接返回；② 插流水时靠唯一索引兜底。
     */
    void handlePaySuccess(String payNo);

    //分页查询订单列表
    PageResult pageQuery(TradeOrderPageQueryDTO queryDTO);

    //查看订单详情
    TradeOrderVO getDetail(long orderId);

    //卖家确认订单
    void confirm(Long orderId);

    //卖家拒绝订单（待确认 → 已取消；已支付则退款，商品回退在售）
    //注意：拒单和取消共用同一个 DTO，因为形状一样（哪个订单 + 原因）
    void reject(TradeOrderCancelDTO rejectDTO);

    //取消订单（交易前，买卖任一方都能取消；已支付则退款，商品回退在售）
    void cancel(TradeOrderCancelDTO cancelDTO);

    //买家确认收货
    void complete(Long orderId);

    //超时关单
    void timeoutClose(Long orderId);

    /**
     * 议价成交后生成订单（供 BargainService 调用）
     *
     * 内部会锁定商品（在售 → 交易中），并写一条 order_source=议价生成(2) 的订单。
     * 成交价由调用方传入（取议价金额，不接受前端传）。
     * 交易地点先留空 —— 议价成交时买家还没填地点，之后可以在订单上补充。
     *
     * 为什么不在这里加 @Transactional：调用方 BargainService.accept() 已经开了事务，
     * 本方法会加入同一个事务（Spring 默认传播行为 REQUIRED），
     * 这样"生成订单"和"回填议价状态"才能一起成功、或一起失败。
     */
    TradeOrder createBargainOrder(Product product, Long buyerId, BigDecimal price);

    /**
     * 买家补充/修改交易地点
     *
     * 为什么需要它：议价成交生成的订单【交易地点是空的】——
     * 议价流程里买家没有经过下单调 Deal 页面，没机会填地点（方案 A）。
     * 所以需要一个"后补"的入口，否则双方不知道该去哪里面交。
     *
     * 规则：只有买家本人能改；订单未结束（待确认/待交易）才能改。
     */
    void updatePickupPlace(Long orderId, String pickupPlace);

    /**
     * 捡漏抢购成功后的订单（供 FlashSaleService 调用）
     *
     * 内部会锁定商品（在售 → 交易中，条件更新是"不超卖"的最终保证）。
     * 订单来源 = 捡漏抢购(3)；未支付超时用【10 分钟】，与抢购窗口一致。
     */
    TradeOrder createFlashSaleOrder(Product product, Long buyerId, BigDecimal price);

}
