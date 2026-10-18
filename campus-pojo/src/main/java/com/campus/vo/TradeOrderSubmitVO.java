package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 下单成功后返回给前端的信息
 *
 * 前端拿到后通常会立刻跳到"支付页"，所以这里带上
 * 订单号、金额、支付截止时间（倒计时用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderSubmitVO implements Serializable {

    private Long orderId;

    private String orderNo;

    /** 应付金额 */
    private BigDecimal realPrice;

    /** 支付截止时间（前端用它做倒计时） */
    private LocalDateTime expireTime;

    /** 支付状态：0未支付 1已支付 2已退款 */
    private Integer payStatus;

}
