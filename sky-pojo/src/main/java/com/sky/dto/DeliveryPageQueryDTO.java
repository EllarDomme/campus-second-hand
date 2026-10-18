package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端-跑腿单分页查询条件（S4-4）
 */
@Data
public class DeliveryPageQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer page;

    private Integer pageSize;

    /** 状态：0待接单 1已接单 2取货中 3已送达 4已取消（不传=全部） */
    private Integer status;

    /** 只看某个跑腿员的单子（不传=全部） */
    private Long runnerUserId;

    /** 只看某笔交易的单子（不传=全部） */
    private Long tradeOrderId;

}
