package com.campus.constant;

/**
 * 商品模块常量（校园二手平台）
 *
 * 为什么要单独一套常量，而不是复用 StatusConstant：
 * 老项目的 StatusConstant 是为"菜品"设计的——0 停售 / 1 起售，
 * 而本平台 product.status 的语义完全不同（0 在售 / 1 交易中 / 2 已售罄 / 3 已下架）。
 * 如果复用 StatusConstant.ENABLE（=1）去表示"在售"，实际含义会变成"交易中"，逻辑就错了。
 * 状态类字段的取值必须集中定义，避免散落的魔法数字导致语义漂移。
 */
public class ProductConstant {

    /** 售卖状态：在售 */
    public static final Integer ON_SALE = 0;
    /** 售卖状态：交易中（已生成订单，其他人不能再下单） */
    public static final Integer IN_TRADE = 1;
    /** 售卖状态：已售罄 */
    public static final Integer SOLD_OUT = 2;
    /** 售卖状态：已下架（卖家主动下架或违规下架） */
    public static final Integer OFF_SHELF = 3;

    /** 审核状态：待审核（商品发布后一律先待审核） */
    public static final Integer AUDIT_PENDING = 0;
    /** 审核状态：审核通过（只有通过的才对外展示） */
    public static final Integer AUDIT_PASSED = 1;
    /** 审核状态：审核驳回 */
    public static final Integer AUDIT_REJECTED = 2;

    /** 是否限时捡漏：普通闲置商品 */
    public static final Integer NOT_FLASH_SALE = 0;
    /** 是否限时捡漏：捡漏商品 */
    public static final Integer FLASH_SALE = 1;

    /** 新旧程度：全新 */
    public static final Integer CONDITION_NEW = 1;
    /** 新旧程度：几乎全新 */
    public static final Integer CONDITION_ALMOST_NEW = 2;
    /** 新旧程度：轻微使用痕迹 */
    public static final Integer CONDITION_SLIGHTLY_USED = 3;
    /** 新旧程度：明显使用痕迹 */
    public static final Integer CONDITION_USED = 4;

    private ProductConstant() {
    }
}
