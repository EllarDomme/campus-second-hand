package com.campus.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 买家发起议价
 *
 * 只有两个字段，都是必须的：
 *   productId  —— 对哪件商品出价
 *   buyerOffer —— 出多少
 *
 * 刻意不包含（防越权/防篡改）：
 *   buyerId / sellerId  身份由后端从登录态和商品推导
 *   realPrice           成交价由后端校验后的出价决定
 */
@Data
public class BargainSubmitDTO implements Serializable {

    private Long productId;

    /** 买家出价（不得低于商品标价的 50%） */
    private BigDecimal buyerOffer;

}
