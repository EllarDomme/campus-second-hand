package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端-手动调整信用分入参
 *
 * 为什么需要这个功能：系统自动加减分（完成交易/好评差评/超时/投诉）覆盖不了所有情况，
 * 现实里管理员需要人工干预，比如"经核实是误会，补回 5 分"。
 */
@Data
public class CreditAdjustDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标用户id */
    private Long userId;

    /** 调整分值：加分为正、扣分为负 */
    private Integer changeScore;

    /** 调整理由（必填 —— 这是审计要求，也是约束管理员的一部分） */
    private String remark;

}
