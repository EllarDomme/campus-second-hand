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

    /**
     * ⚠️ 这个 UID 必须显式声明，别让编译器算！
     *
     * 背景：这个对象会被 JDK 序列化后放进 Redis（商品列表缓存，TTL 30 分钟）。
     * 如果不写 serialVersionUID，编译器会按类的"结构"算一个指纹 ——
     * 于是只要给类加一个字段，指纹就变，Redis 里的老缓存就反序列化失败，
     * 接口直接 500（我就这么踩过一次：给 ProductVO 加 viewCount 后列表全挂）。
     *
     * 写死成 1L 之后，加字段/删字段都兼容：老缓存照样能读出来，
     * 新增的字段是默认值（null/0），不会炸。
     */
    private static final long serialVersionUID = 1L;

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

    /**
     * 商品热度：让买家能判断"这件东西有多少人关注"
     *   viewCount     —— 浏览次数（进详情页 +1，存在 product.view_count）
     *   favoriteCount —— 收藏人数（实时从 favorite 表数出来，不冗余存字段：
     *                    收藏/取消很频繁，冗余计数很容易和明细对不上）
     */
    private Integer viewCount;
    private Integer favoriteCount;

    /** 卖家昵称（详情页显示"谁在卖"，列表页不需要） */
    private String sellerNickname;

}
