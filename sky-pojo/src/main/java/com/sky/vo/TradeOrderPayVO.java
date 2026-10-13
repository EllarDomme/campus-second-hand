package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发起支付后返回给前端的信息
 *
 * 真实场景这里返回的是微信支付参数（prepay_id、nonceStr、paySign、timeStamp…），
 * 前端拿去调 wx.requestPayment。我们用的是模拟通道，
 * 所以只返回订单号 + 支付流水号，前端拿流水号去调"模拟支付成功"的回调接口。
 *
 * 面试可以这么讲：接真实通道时只需要把这个 VO 的字段换成微信支付参数，
 * 其余流程（回调、幂等、状态流转）完全不用改。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderPayVO implements Serializable {

    private String orderNo;

    /** 支付流水号（模拟通道自己生成；回调时要带回来） */
    private String payNo;

    /** 应付金额 */
    private BigDecimal amount;

    /** 支付截止时间（前端倒计时用） */
    private LocalDateTime expireTime;

}
