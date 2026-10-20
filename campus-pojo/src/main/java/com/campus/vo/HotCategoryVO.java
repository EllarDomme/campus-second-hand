package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 热门品类榜 · 分类条目（S6-2）
 *
 * 两级结构：分类 → 该分类下的 Top N 商品。
 * 为什么要两级：单件商品成交数恒为 0/1，没法按"销量"排；
 * 但"某个品类整体卖得好不好"是可以聚合的 —— 那才是运营/卖家真正想知道的。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long categoryId;

    private String categoryName;

    /** 该分类下已售出的商品数 */
    private Integer soldCount;

    /** 该分类下当前在售的商品数 */
    private Integer onSaleCount;

    /** 分类热度分 = soldCount × 10 + onSaleCount（在 Java 里算，不在 SQL 里算） */
    private Integer categoryScore;

    /** 该分类下的 Top N 商品（已按 hotScore 降序） */
    private List<HotProductVO> products;

}
