package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投诉工单（带商品/双方昵称等上下文）
 *
 * 用户端看"我的投诉记录"，管理端看"待处理工单列表"，用同一个 VO：
 * 管理员需要看到"谁投诉谁、关于什么、证据是什么"，用户也需要看到"处理到哪一步了"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Integer complaintType;

    /** 类型中文（服务层填，省得前端再维护一份映射） */
    private String complaintTypeName;

    private Long productId;

    private String productTitle;

    private Long orderId;

    private String orderNo;

    private Long complainantUserId;

    private String complainantNickname;

    private Long respondentUserId;

    private String respondentNickname;

    private Integer complaintReason;

    /** 原因中文 */
    private String complaintReasonName;

    private String content;

    /** 凭证图片 JSON 数组串，前端解析后展示 */
    private String images;

    private Integer handleStatus;

    /** 状态中文 */
    private String handleStatusName;

    /** 处理结果：1成立 2不成立，NULL=未处理 */
    private Integer handleResult;

    private String handleNote;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
