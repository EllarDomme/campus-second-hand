package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 取消订单入参（拒单 / 取消 共用）
 *
 * 两个场景共用这一个 DTO，因为它俩的形状完全一样 —— 都是「哪个订单 + 为什么」：
 *   ① 卖家拒单     PUT /user/order/reject
 *   ② 任何一方取消  PUT /user/order/cancel
 *
 * 区别只在【谁有权限发起】和【什么状态下能发起】，那是 Service 该管的事。
 *
 * 说明：这个类原来叫 TradeOrderRejectionDTO（只为拒单服务），
 *      把两个几乎一模一样的 DTO 合并成一个，是典型的"消除重复"重构。
 */
@Data
public class TradeOrderCancelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单id */
    private Long id;

    /** 原因（必填，会写进订单的 cancel_reason 给对方看） */
    private String reason;

}
