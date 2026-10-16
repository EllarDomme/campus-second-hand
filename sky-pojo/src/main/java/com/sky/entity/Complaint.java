package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投诉举报工单
 *
 * 两种类型，字段要求不同：
 *   · 商品举报（type=0）：product_id 必填、order_id 可为空 —— 举报"这个商品本身有问题"
 *   · 交易纠纷（type=1）：order_id 必填 —— 纠纷一定发生在某笔交易上
 *
 * 被投诉人（respondent_user_id）由服务端推导，不接受前端传 ——
 * 否则可以伪造"我投诉任何人"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long productId;

    /** 关联订单（商品举报可为空） */
    private Long orderId;

    /** 投诉人 */
    private Long complainantUserId;

    /** 被投诉人 */
    private Long respondentUserId;

    /** 工单类型：0商品举报 1交易纠纷 */
    private Integer complaintType;

    /** 投诉原因：1货不对板 2虚假描述 3对方失联 4跑腿纠纷 9其他 */
    private Integer complaintReason;

    /** 投诉内容 */
    private String content;

    /** 凭证图片 url 的 JSON 数组串 */
    private String images;

    /** 处理状态：0待处理 1处理中 2已完结 */
    private Integer handleStatus;

    /** 处理结果：1投诉成立 2投诉不成立，NULL=未处理（S3-10b 补的字段） */
    private Integer handleResult;

    /** 管理员处理备注 */
    private String handleNote;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
