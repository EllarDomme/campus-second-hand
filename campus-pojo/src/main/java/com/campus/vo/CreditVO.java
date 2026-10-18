package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 我的信用分概览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前信用分 */
    private Integer creditScore;

    /** 今日已加的分（用来展示"今日还能加多少"，也便于排查为什么没加分） */
    private Integer todayAdded;

    /** 今日加分上限 */
    private Integer dailyAddLimit;

    /** 收到的好评数 */
    private Integer goodReviewCount;

    /** 收到的差评数 */
    private Integer badReviewCount;

}
