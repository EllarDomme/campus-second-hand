package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 提交评价入参
 *
 * 刻意不包含这些字段：
 *   fromUserId  评价人从登录态取
 *   toUserId    由订单推导（我是买家就评卖家，反之亦然）—— 不接受前端传，
 *               否则可以伪造"我给任意用户打差评"
 *   isDefault   默认好评是系统行为，用户提交的一律是真实评价
 */
@Data
public class ReviewSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单id */
    private Long orderId;

    /** 星级 1~5 */
    private Integer star;

    /** 评价文字（可空，最长 500） */
    private String content;

}
