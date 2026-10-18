package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据看板-用户概况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户总数 */
    private Integer totalUsers;

    /** 跑腿员数（role=1） */
    private Integer totalRunners;

    /** 已封禁用户数（status=1） */
    private Integer bannedUsers;

    /** 低信用分用户数（低于传入的阈值） */
    private Integer lowCreditUsers;

}
