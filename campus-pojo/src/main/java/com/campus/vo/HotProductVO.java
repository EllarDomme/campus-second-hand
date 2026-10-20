package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 热门品类榜 · 单品条目（S6-2）
 *
 * 注意：这里【没有 orderCount 字段】。
 * 因为二手商品是唯一的，一件商品最多成交一次，成交数恒为 0/1 —— 没有区分度。
 * 所以用 sold（0/1）+ elapsedDays（多快卖掉）来表达"热度"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotProductVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;

    /** 所属分类id（Service 里要按它分组，所以查出来时必须带上） */
    private Long categoryId;

    private String title;

    private BigDecimal price;

    /** 商品状态：0在售 1交易中 2已售罄 */
    private Integer status;

    /** 是否已成交：1 已成交 / 0 未成交（由 status = 2 得出） */
    private Integer sold;

    /**
     * 耗时天数（由 SQL 算好）：
     *   已成交 → 成交时刻 − 起算时刻
     *   未成交 → 当前时刻 − 起算时刻（也就是"挂了多久"）
     * 起算时刻：普通商品用 online_time；捡漏商品用 flash_start_time
     */
    private Integer elapsedDays;

    private Integer viewCount;

    private Integer favoriteCount;

    /** 热度分（0~100，由 calcHotScore 算出） */
    private Double hotScore;

}
