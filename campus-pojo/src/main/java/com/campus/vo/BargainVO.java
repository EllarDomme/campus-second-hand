package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 议价展示对象（列表 / 详情共用）
 *
 * 除了议价本身的字段，还带了：
 *   - 商品信息（标题/图片/标价）：前端要显示"这件东西标价 100，他出价 75"
 *   - 双方昵称：议价列表里要显示"和谁在谈"
 *
 * ⚠️ 写 XML 时列别名必须和这里的属性名一致，否则字段是 null 且不报错。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BargainVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== 议价本身 =====
    private Long id;

    private Long sellerId;

    private Long buyerId;

    private Long productId;

    /** 买家出价 */
    private BigDecimal buyerOffer;

    /** 卖家还价（没还价时是 null） */
    private BigDecimal sellerCounterOffer;

    /** 状态：1买家出价 2卖家还价 3已接受 4已拒绝 5已过期 */
    private Integer bargainStatus;

    /** 成交后生成的订单id */
    private Long orderId;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    // ===== 关联商品信息 =====
    private String productTitle;

    private String productImages;

    /** 商品标价（和出价放一起对比着看） */
    private BigDecimal productPrice;

    // ===== 双方昵称 =====
    private String buyerName;

    private String sellerName;

}
