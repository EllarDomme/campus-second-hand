package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品展示对象（列表 / 详情共用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVO implements Serializable {

    private Long id;

    /** 卖家用户id */
    private Long sellerId;

    private Long categoryId;

    /** 商品标题 */
    private String title;

    /** 商品标价 */
    private BigDecimal price;

    /** 图片url，JSON数组字符串（前端自行解析成图片数组） */
    private String images;

    /** 商品描述 */
    private String description;

    /** 新旧程度：1全新 2几乎全新 3轻微使用痕迹 4明显使用痕迹 */
    private Integer conditionLevel;

    /** 是否限时捡漏：0普通 1捡漏 */
    private Integer isFlashSale;

    /** 捡漏抢购开始时间 */
    private LocalDateTime flashStartTime;

    /** 审核状态：0待审核 1已通过 2已驳回 */
    private Integer auditStatus;

    /** 审核意见/驳回原因（卖家可以在"我的商品"里看到驳回原因） */
    private String auditNote;

    /** 售卖状态：0在售 1交易中 2已售罄 3已下架 */
    private Integer status;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 分类名称（来自 category 表关联查询） */
    private String categoryName;

}
