package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 议价记录
 *
 * 一条记录承载一整轮"一来一回"：
 *   buyer_offer          买家出价（发起时写入，之后不变）
 *   seller_counter_offer 卖家还价（卖家选择"还价"时写入）
 *   两个字段加上 bargainStatus，就够表达单轮制状态机的全部流转。
 *
 * 议价成交（status=3）时会生成订单，订单 id 回填到 order_id，便于互相追溯。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bargain implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long sellerId;

    private Long buyerId;

    private Long productId;

    /** 买家出价 */
    private BigDecimal buyerOffer;

    /** 卖家还价（卖家没还价时为 null） */
    private BigDecimal sellerCounterOffer;

    /** 状态：0待处理 1买家出价 2卖家还价 3已接受 4已拒绝 5已过期 */
    private Integer bargainStatus;

    /** 议价成交后生成的订单id（未成交为 null） */
    private Long orderId;

    /** 议价有效期截止时间 */
    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 当前这条议价"谈的是多少钱"：卖家还过价就是还价，否则就是买家出价。
     * 不是数据库字段（@Data 会为它生成 getter），只是给业务代码用的小工具，
     * 免得每处都写一遍"有还价用还价，没还价用出价"。
     */
    public BigDecimal currentOffer() {
        return sellerCounterOffer != null ? sellerCounterOffer : buyerOffer;
    }

}
