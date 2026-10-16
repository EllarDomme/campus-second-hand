package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 信用分变更流水（只增不改）
 *
 * score_after 这个字段很关键：它记下"这次变更之后总分是多少"。
 * 好处是**便于对账** —— 拿最后一条流水的 score_after 和 user.credit_score 对一下，
 * 就知道有没有哪里扣分/加分漏记了（生产环境排查数据问题的常用手段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    /** 本次变更分值（加分为正，扣分为负） */
    private Integer changeScore;

    /** 变更后的总分（便于对账） */
    private Integer scoreAfter;

    /** 变更原因：见 CreditConstant.REASON_* */
    private Integer reasonType;

    /** 关联业务id（订单id / 投诉id 等），用于溯源 */
    private Long refId;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
