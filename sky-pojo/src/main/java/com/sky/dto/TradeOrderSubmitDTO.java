package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 下单入参
 *
 * 刻意不包含这些字段（防越权 + 防篡改）：
 *   buyerId    买家身份从登录态取，不接受前端传
 *   sellerId   由商品的 sellerId 推导，不接受前端传（否则可以伪造订单的卖家）
 *   realPrice  成交价由后端按商品标价（或议价结果）算，不接受前端传
 *              否则前端把 1000 元的商品改成 1 元就能下单
 *   orderSource / status / payStatus  业务流转字段，由后端控制
 */
@Data
public class TradeOrderSubmitDTO implements Serializable {

    /** 商品id */
    private Long productId;

    /** 交易方式：0自提 1跑腿（不传按自提处理） */
    private Integer dealType;

    /** 交易地点（自提时填：宿舍区/教学楼/校门口） */
    private String pickupPlace;

    /** 买家备注 */
    private String remark;

}
