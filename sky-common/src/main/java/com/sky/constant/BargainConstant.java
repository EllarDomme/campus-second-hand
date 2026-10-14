package com.sky.constant;

import java.math.BigDecimal;

/**
 * 议价模块常量（校园二手）
 *
 * 状态沿用阶段 1 建表时定的语义（见 docs/01-阶段1-建表规格.md）：
 *   0 待处理   1 买家出价   2 卖家还价   3 已接受   4 已拒绝   5 已过期
 *
 * 【单轮制】说明（决策点 3，已定）：
 *   状态 2（卖家还价）之后，买家【只能接受或拒绝】，不能再还价。
 *   原因是 bargain 表只有一组 buyer_offer / seller_counter_offer 字段，
 *   存不下第二轮。想支持多轮就得把它改成流水表（每轮插一条 + round 字段），
 *   代价是状态机复杂化——对一个实习项目不划算。
 */
public class BargainConstant {

    /** 待处理（建表时预留，当前流程不产生这个状态） */
    public static final Integer STATUS_PENDING = 0;
    /** 买家已出价，等卖家响应 */
    public static final Integer STATUS_BUYER_OFFER = 1;
    /** 卖家已还价，等买家响应（买家只能接受或拒绝） */
    public static final Integer STATUS_SELLER_COUNTER = 2;
    /** 已接受（议价成交，同时会生成订单） */
    public static final Integer STATUS_ACCEPTED = 3;
    /** 已拒绝 */
    public static final Integer STATUS_REJECTED = 4;
    /** 已过期 */
    public static final Integer STATUS_EXPIRED = 5;

    /**
     * 出价下限系数：出价不得低于商品标价的这个比例
     * 防止恶意低价（比如 1000 块的东西出 1 块）
     */
    public static final BigDecimal MIN_OFFER_RATE = new BigDecimal("0.5");

    /**
     * 议价有效期（小时）
     * 卖家一直不理，24 小时后这条议价自动视为过期（买家可以重新发起）
     */
    public static final int EXPIRE_HOURS = 24;

    /**
     * 被拒绝后的冷却时间（小时）
     * 防止反复骚扰：同一买家对同一商品被拒后，24 小时内不能再议价
     */
    public static final int REJECT_COOLDOWN_HOURS = 24;

    private BargainConstant() {
    }
}
