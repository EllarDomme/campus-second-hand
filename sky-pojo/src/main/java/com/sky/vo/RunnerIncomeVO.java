package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 跑腿员收入统计
 *
 * 重要定位说明：平台【不做资金流转】（阶段 2 施工图已定：删掉微信支付），
 * 所以这里只是"记录与统计展示"，不是"账户余额"。
 * 跑腿费在交易订单里已经随商品价格一起支付了，平台只是把"跑腿员应得的份额"统计出来。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunnerIncomeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已完成（已送达）的跑腿单数 */
    private Integer finishedCount;

    /** 累计跑腿费（只统计已送达的） */
    private BigDecimal totalFee;

    /** 进行中的跑腿单数（已接单 / 取货中）—— 方便跑腿员看手上还有几单 */
    private Integer ongoingCount;

}
