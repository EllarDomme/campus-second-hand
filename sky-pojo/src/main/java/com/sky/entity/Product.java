package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品（二手闲置物品）
 *
 * 普通闲置商品与限时捡漏商品共用这张表，靠 isFlashSale 区分：
 *   普通商品：isFlashSale=0，flashStartTime 为 null，支持议价
 *   捡漏商品：isFlashSale=1，有 flashStartTime，一口价、不支持议价
 *
 * 两个状态字段职责不同，不要混用：
 *   auditStatus 审核状态（发布后一律待审核，管理员审核通过才对外展示）
 *   status      售卖状态（在售 / 交易中 / 已售罄 / 已下架）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 卖家用户id（发布时由后端从登录态取，不接受前端传入） */
    private Long sellerId;

    /** 商品分类id */
    private Long categoryId;

    /** 商品标题 */
    private String title;

    /** 商品标价 */
    private BigDecimal price;

    /** 图片url，JSON数组字符串，例如 ["https://.../1.jpg","https://.../2.jpg"] */
    private String images;

    /** 商品描述：成色、瑕疵、配件等细节 */
    private String description;

    /** 新旧程度：1全新 2几乎全新 3轻微使用痕迹 4明显使用痕迹（见 ProductConstant） */
    private Integer conditionLevel;

    /** 是否限时捡漏：0普通 1捡漏 */
    private Integer isFlashSale;

    /** 捡漏抢购开始时间（普通商品为 null） */
    private LocalDateTime flashStartTime;

    /** 审核状态：0待审核 1已通过 2已驳回 */
    private Integer auditStatus;

    /** 审核意见/驳回原因 */
    private String auditNote;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 售卖状态：0在售 1交易中 2已售罄 3已下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
