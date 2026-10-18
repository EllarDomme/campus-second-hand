package com.campus.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 私信会话
 *
 * 一个"会话"= 围绕某件商品，买家与卖家之间的一个对话。
 * 所以三元组 (product_id, buyer_id, seller_id) 能唯一确定一个会话 ——
 * 表上也正是用这个三元组做了唯一索引 uk_product_buyer_seller。
 *
 * 为什么要用唯一索引：两个人在商品页同时点"联系卖家"，
 * 并发下可能各建一个会话，用户就会看到两个一模一样的对话框。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 会话围绕的商品id（也是权限校验的业务上下文：只有这笔交易的双方能聊） */
    private Long productId;

    private Long buyerId;

    private Long sellerId;

    /** 关联订单id（由订单产生的会话，可以为空） */
    private Long orderId;

    /** 会话状态：0开放 1已关闭 */
    private Integer status;

    /**
     * 可发送截止时间（NULL = 不限期）
     *
     * 用途：捡漏抢购这类场景只给 10 分钟付款窗口，
     * 窗口过了就不该再让双方私聊（防止抢购结束后继续骚扰）。
     */
    private LocalDateTime canSendUntil;

    /** 最后一条消息时间（会话列表按它倒序） */
    private LocalDateTime lastMessageTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
