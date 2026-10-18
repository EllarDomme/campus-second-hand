package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 数据看板-交易概况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTradeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 今日成交单数（订单状态=已完成） */
    private Integer todayOrders;

    /** 今日成交额 */
    private BigDecimal todayAmount;

    /** 本月成交单数 */
    private Integer monthOrders;

    /** 本月成交额 */
    private BigDecimal monthAmount;

    /** 累计成交额（全部历史） */
    private BigDecimal totalAmount;

}
