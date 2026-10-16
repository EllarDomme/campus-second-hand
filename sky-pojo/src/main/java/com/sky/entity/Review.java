package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易评价
 *
 * 一对一约束：表上有唯一索引 uk_order_from(order_id, from_user_id)
 * —— 同一笔订单、同一个人只能评一次。
 * 买家评卖家一次、卖家评买家一次，所以一笔订单最多两条评价（互评）。
 *
 * is_default 的含义：如果一方一直不评价，系统会给一个"默认好评"
 * （is_default=1），避免"不评价的人反而拉低了对方的好评率"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 所属订单 */
    private Long orderId;

    /** 商品id（查"这个商品的评价"用） */
    private Long productId;

    /** 评价人 */
    private Long fromUserId;

    /** 被评价人 */
    private Long toUserId;

    /** 星级 1~5 */
    private Integer star;

    /** 评价文字（最长 500，可空） */
    private String content;

    /** 是否系统默认好评：0真实评价 1逾期默认 */
    private Integer isDefault;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
