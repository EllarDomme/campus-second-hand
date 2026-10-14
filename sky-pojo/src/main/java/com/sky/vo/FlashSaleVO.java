package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 捡漏专区商品（带抢购状态）
 *
 * flashStatus 不是数据库字段，而是服务层算出来的，方便前端直接展示：
 *   0 未开始     now < flashStartTime
 *   1 抢购中     已到抢购时间 且 商品仍在售
 *   2 已抢光     已到抢购时间 但 商品已被抢走（状态变成"交易中"）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品id */
    private Long id;

    private String title;

    private String images;

    private BigDecimal price;

    /** 新旧程度：1全新 2几乎全新 3轻微使用痕迹 4明显使用痕迹 */
    private Integer conditionLevel;

    /** 抢购开始时间 */
    private LocalDateTime flashStartTime;

    /** 卖家昵称 */
    private String sellerName;

    /** 商品状态：0在售 1交易中（服务层读出来判断 flashStatus 用） */
    private Integer status;

    /** 抢购状态（服务层计算，不落库）：0未开始 1抢购中 2已抢光 */
    private Integer flashStatus;

}
