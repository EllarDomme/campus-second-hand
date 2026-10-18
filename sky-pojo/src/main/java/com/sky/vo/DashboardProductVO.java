package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据看板-商品概况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardProductVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 在售（status=0） */
    private Integer onSaleProducts;

    /** 交易中（status=1） */
    private Integer inTradeProducts;

    /** 已售罄（status=2） */
    private Integer soldOutProducts;

    /** 待审核（audit_status=0） */
    private Integer pendingAuditProducts;

}
