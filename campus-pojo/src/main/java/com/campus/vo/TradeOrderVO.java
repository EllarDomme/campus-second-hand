package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单展示对象（列表 / 详情共用）
 *
 * 前面是订单自身的字段，最后两个 productXxx 是「关联商品表」查出来的快照信息
 * —— 订单列表要显示商品缩略图和标题，所以必须 join product 表。
 *
 * ⚠️ 写 XML 时的关键：select 出来的每一列，**列名/别名必须和这里的属性名一致**，
 *    否则那个字段就是 null，而且**不会报错**。
 *    （你在收藏列表里见过这个坑：categoryName 当时是 null，就是因为 SQL 里没查它）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== 订单自身字段 =====
    private Long id;

    /** 业务订单编号 */
    private String orderNo;

    private Long sellerId;

    private Long buyerId;

    private Long productId;

    /** 实际成交价 */
    private BigDecimal realPrice;

    /** 交易方式：0自提 1跑腿 */
    private Integer dealType;

    /** 订单来源：1普通下单 2议价生成 3捡漏抢购 */
    private Integer orderSource;

    /** 订单状态：0待确认 1待交易 2已完成 3已取消 4超时取消 */
    private Integer status;

    /** 支付状态：0未支付 1已支付 2已退款 */
    private Integer payStatus;

    private LocalDateTime payTime;

    /** 交易地点 */
    private String pickupPlace;

    /** 买家备注 */
    private String remark;

    /** 超时截止时间 */
    private LocalDateTime expireTime;

    /** 交易完成时间 */
    private LocalDateTime finishTime;

    /** 取消原因 */
    private String cancelReason;

    private LocalDateTime createTime;

    // ===== 关联商品的信息（join product 得到）=====
    /** 商品标题（订单列表要显示） */
    private String productTitle;

    /** 商品图片 JSON 数组字符串（订单列表要显示缩略图） */
    private String productImages;

}
