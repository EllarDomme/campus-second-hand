package com.sky.constant;

/**
 * 订单模块常量（校园二手平台）
 *
 * 为什么新建而不是复用老代码里的 Orders 常量：
 *   老的 Orders 实体里那一套状态（待付款/待接单/派送中…）是外卖场景的，
 *   我们的订单没有"配送中"、没有"待付款→待接单"这套流程，语义对不上。
 */
public class OrderConstant {

    // ===== 订单来源 =====
    /** 普通直接下单 */
    public static final Integer SOURCE_NORMAL = 1;
    /** 议价成功后生成 */
    public static final Integer SOURCE_BARGAIN = 2;
    /** 限时捡漏抢购生成 */
    public static final Integer SOURCE_FLASH_SALE = 3;

    // ===== 交易方式 =====
    /** 自提（线下当面交接） */
    public static final Integer DEAL_SELF_PICKUP = 0;
    /** 校园跑腿 */
    public static final Integer DEAL_DELIVERY = 1;

    // ===== 订单状态（订单维度）=====
    /** 待确认：已支付，等卖家确认要卖 */
    public static final Integer STATUS_PENDING_CONFIRM = 0;
    /** 待交易：卖家已确认，约好时间地点当面交接 */
    public static final Integer STATUS_PENDING_TRADE = 1;
    /** 已完成：买家确认收货，平台放款 */
    public static final Integer STATUS_COMPLETED = 2;
    /** 已取消（买卖任一方主动取消） */
    public static final Integer STATUS_CANCELLED = 3;
    /** 超时取消（未支付 / 未确认超时） */
    public static final Integer STATUS_TIMEOUT_CANCELLED = 4;

    // ===== 支付状态（支付维度，独立于订单状态）=====
    /** 未支付 */
    public static final Integer PAY_UNPAID = 0;
    /** 已支付（钱在平台托管） */
    public static final Integer PAY_PAID = 1;
    /** 已退款 */
    public static final Integer PAY_REFUNDED = 2;

    // ===== 支付流水类型 =====
    /** 支付：买家付款，钱进平台托管 */
    public static final Integer PAY_TYPE_PAY = 1;
    /** 退款：卖家拒单 / 取消订单，钱退回买家 */
    public static final Integer PAY_TYPE_REFUND = 2;
    /**
     * 放款（结算给卖家）：买家确认收货后，平台把托管的钱结算给卖家。
     *
     * 加上这一条，一笔订单的资金流才真正闭环：
     *   支付(+66) → 平台托管 → 确认收货则放款(−66 给卖家)，取消/拒单则退款(−66 回买家)
     * 平台在某一单上的托管余额 = 支付 − 退款 − 放款，订单终结时必然是 0。
     */
    public static final Integer PAY_TYPE_SETTLE = 3;

    // ===== 超时时长（分钟）=====
    /** 普通订单：15 分钟未支付自动关单 */
    public static final int EXPIRE_MINUTES_NORMAL = 15;
    /** 捡漏订单：10 分钟（与抢购窗口一致） */
    public static final int EXPIRE_MINUTES_FLASH_SALE = 10;
    /**
     * 支付成功后，卖家确认的时限：30 分钟
     *
     * 同一个 expire_time 字段实现"分段超时"：
     *   下单时     expire_time = 现在 + 15 分钟（未支付超时）
     *   支付成功时 expire_time = 现在 + 30 分钟（卖家确认超时）
     * 定时任务扫到 expire_time 过期时，再看 pay_status 决定"直接关单"还是"退款关单"。
     */
    public static final int EXPIRE_MINUTES_PENDING_CONFIRM = 30;

    private OrderConstant() {
    }
}
