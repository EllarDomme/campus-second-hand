package com.sky.service;

import com.sky.dto.TradeOrderPageQueryDTO;
import com.sky.dto.TradeOrderRejectionDTO;
import com.sky.dto.TradeOrderSubmitDTO;
import com.sky.result.PageResult;
import com.sky.vo.TradeOrderPayVO;
import com.sky.vo.TradeOrderSubmitVO;
import com.sky.vo.TradeOrderVO;

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

    //卖家拒绝订单
    void reject(TradeOrderRejectionDTO rejectionDTO);


}
