package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易订单
 *
 * 两种状态维度，不要混用：
 *   status     订单状态（待确认 / 待交易 / 已完成 / 已取消 / 超时取消）
 *   payStatus  支付状态（未支付 / 已支付 / 已退款）
 *
 * 之所以拆成两个字段：订单状态和支付状态的变化节奏不一样
 *   —— 支付成功只是"钱到位了"，订单还要等卖家确认、等面交、等买家确认收货。
 *   合在一起会变成 status 里塞十几个枚举值，谁都记不住。
 *
 * 下单后商品会被锁定（product.status → 交易中），所以一件商品同时只能有一个进行中的订单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 业务订单编号（对外展示，订单号唯一索引） */
    private String orderNo;

    /** 卖家用户id */
    private Long sellerId;

    /** 买家用户id */
    private Long buyerId;

    /** 商品id */
    private Long productId;

    /** 实际成交价（与商品标价解耦；捡漏/议价改的是这个价） */
    private BigDecimal realPrice;

    /** 交易方式：0自提 1跑腿 */
    private Integer dealType;

    /** 订单来源：1普通下单 2议价生成 3捡漏抢购 */
    private Integer orderSource;

    /** 订单状态：0待确认 1待交易 2已完成 3已取消 4超时取消 */
    private Integer status;

    /** 支付状态：0未支付 1已支付 2已退款 */
    private Integer payStatus;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 支付流水号（模拟通道生成；唯一，重复回调靠它挡住） */
    private String payNo;

    /** 交易地点（自提时填：宿舍区/教学楼/校门口） */
    private String pickupPlace;

    /** 买家备注 */
    private String remark;

    /** 订单超时截止时间（捡漏10分钟 / 普通15分钟） */
    private LocalDateTime expireTime;

    /** 交易完成时间 */
    private LocalDateTime finishTime;

    /** 取消原因 */
    private String cancelReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
