package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 跑腿单（带主订单的上下文信息）
 *
 * 用途：待接单大厅（跑腿员看）、我的跑腿单（跑腿员看）、订单详情里的跑腿信息（买卖双方看）。
 * 跑腿员最关心的是"去哪取、送到哪、多少钱"，其次是"什么商品"，
 * 所以这些字段直接从 trade_order / product 关联出来。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tradeOrderId;

    /** 主订单号（跑腿员线下沟通时报这个号，比 id 好认） */
    private String orderNo;

    private Long runnerUserId;

    private String pickAddress;

    private String receiveAddress;

    private BigDecimal deliveryFee;

    /** 状态：0待接单 1已接单 2取货中 3已送达 4已取消 */
    private Integer status;

    private LocalDateTime acceptTime;

    private LocalDateTime finishTime;

    private LocalDateTime createTime;

    // ---------- 以下来自主订单/商品，方便前端直接展示 ----------

    private String productTitle;

    private String productImages;

    private Long buyerId;

    private String buyerNickname;

    private Long sellerId;

    private String sellerNickname;

}
