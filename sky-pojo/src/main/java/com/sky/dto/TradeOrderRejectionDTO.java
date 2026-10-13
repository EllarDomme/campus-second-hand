package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 卖家拒单入参
 *
 * 为什么拒单需要一个 DTO，而"确认"不需要：
 *   确认只需要一个订单 id，用 @RequestParam 就够了；
 *   拒单还要填原因（要展示给买家看），两个字段用请求体更整齐。
 */
@Data
public class TradeOrderRejectionDTO implements Serializable {

    /** 订单id */
    private Long id;

    /** 拒单原因（必填，会写进订单的 cancel_reason 给买家看到） */
    private String reason;

}
