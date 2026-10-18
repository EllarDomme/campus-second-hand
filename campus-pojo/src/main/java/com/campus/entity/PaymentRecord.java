package com.campus.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水（每一次支付 / 退款都留一条记录）
 *
 * 两个用途：
 *   ① 对账：这一单到底扣了多少、退了多少，看这张表
 *   ② 回调幂等：pay_no 上有唯一索引，重复的支付通知插第二次会撞唯一键，
 *      从而避免同一笔支付被处理两次
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 交易订单id */
    private Long orderId;

    /** 订单编号（冗余一份，对账时不用再关联订单表） */
    private String orderNo;

    /** 支付流水号（唯一） */
    private String payNo;

    /** 金额 */
    private BigDecimal amount;

    /** 类型：1支付 2退款 */
    private Integer payType;

    /** 状态：0处理中 1成功 2失败 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
