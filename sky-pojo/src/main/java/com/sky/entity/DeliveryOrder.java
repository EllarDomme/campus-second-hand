package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 跑腿订单
 *
 * 与主交易订单是一对一关系：trade_order (1) ──── (1) delivery_order
 * 表上有唯一索引 uk_trade_order(trade_order_id) —— 防止同一笔交易重复生成跑腿单
 * （重复生成会扣两次跑腿费，所以这里必须靠数据库兜底）。
 *
 * 表里【不冗余】存买卖双方和商品：通过 trade_order_id 关联拿就行（阶段 1 规格已定）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 关联的主交易订单id（唯一索引） */
    private Long tradeOrderId;

    /** 跑腿员用户id（未接单时为 NULL —— 这也是"抢单"条件更新里要判的空） */
    private Long runnerUserId;

    /** 取货地址（跑腿员去哪拿） */
    private String pickAddress;

    /** 收货地址（送到哪） */
    private String receiveAddress;

    /** 跑腿费（下单时算好的【计费快照】，不要每次查询实时算） */
    private BigDecimal deliveryFee;

    /** 状态：0待接单 1已接单 2取货中 3已送达 4已取消 */
    private Integer status;

    /** 接单时间 */
    private LocalDateTime acceptTime;

    /** 送达时间（结算统计要按时间聚合） */
    private LocalDateTime finishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
