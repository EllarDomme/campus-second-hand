package com.sky.constant;

/**
 * 信用分相关常量
 *
 * 设计说明（这套分值是我们自己定的，需求原文只说了"加分有每日上限，防刷分"）：
 *
 *   加分项：完成交易 +2 ／ 收到好评 +1
 *   扣分项：收到差评 -2 ／ 交易爽约 -5 ／ 投诉核实成立 -10
 *
 * ⚠️ 为什么 credit_log 这张表【不是可选项】：
 *    需求要求"加分有每日上限"，而 user 表只有一个总分、没有时间戳 ——
 *    不记流水就算不出"今天已经加了多少分"。这条规则一旦要落地，
 *    流水表就是必需品（这是数据库评审报告里的"证据 6"）。
 */
public class CreditConstant {

    private CreditConstant() {
    }

    // ---------- 变更原因（与 credit_log.reason_type 对齐）----------
    public static final Integer REASON_TRADE_DONE = 1;      // 完成交易
    public static final Integer REASON_GOOD_REVIEW = 2;     // 收到好评
    public static final Integer REASON_BAD_REVIEW = 3;      // 收到差评
    public static final Integer REASON_TRADE_BREACH = 4;    // 交易爽约
    public static final Integer REASON_FAKE_PRODUCT = 5;    // 发布虚假商品
    public static final Integer REASON_COMPLAINT_UPHELD = 6; // 投诉核实成立
    public static final Integer REASON_REAL_NAME = 7;       // 实名认证
    public static final Integer REASON_PLATFORM_TASK = 8;   // 平台任务

    // ---------- 分值 ----------
    /** 完成一笔交易 */
    public static final int SCORE_TRADE_DONE = 2;
    /** 收到好评（4~5 星） */
    public static final int SCORE_GOOD_REVIEW = 1;
    /** 收到差评（1~2 星） */
    public static final int SCORE_BAD_REVIEW = -2;
    /** 交易爽约（订单超时未支付） */
    public static final int SCORE_TRADE_BREACH = -5;
    /** 投诉核实成立 */
    public static final int SCORE_COMPLAINT_UPHELD = -10;

    // ---------- 边界 ----------
    /**
     * 每日【加分】上限：一天之内所有加分合计不超过这个数（防刷分）
     * ⚠️ 只限加分，扣分不设上限 —— 否则恶意用户靠着"每天最多扣 10 分"就能一直赖着
     */
    public static final int DAILY_ADD_LIMIT = 10;

    /** 信用分下限（扣成负数没有意义，还会让"低于阈值禁止抢购"这类规则失去参照） */
    public static final int MIN_SCORE = 0;

    /** 初始信用分（与 user.credit_score 默认值一致） */
    public static final int INIT_SCORE = 100;

    /** 好评的星级下限：4 星及以上算好评 */
    public static final int GOOD_REVIEW_STAR = 4;
    /** 差评的星级上限：2 星及以下算差评（3 星算中评，不加不减） */
    public static final int BAD_REVIEW_STAR = 2;

    /**
     * 原因类型 → 中文说明（打日志 / 流水备注用）
     */
    public static String reasonName(Integer reasonType) {
        if (REASON_TRADE_DONE.equals(reasonType)) {
            return "完成交易";
        }
        if (REASON_GOOD_REVIEW.equals(reasonType)) {
            return "收到好评";
        }
        if (REASON_BAD_REVIEW.equals(reasonType)) {
            return "收到差评";
        }
        if (REASON_TRADE_BREACH.equals(reasonType)) {
            return "交易爽约";
        }
        if (REASON_FAKE_PRODUCT.equals(reasonType)) {
            return "发布虚假商品";
        }
        if (REASON_COMPLAINT_UPHELD.equals(reasonType)) {
            return "投诉核实成立";
        }
        if (REASON_REAL_NAME.equals(reasonType)) {
            return "实名认证";
        }
        if (REASON_PLATFORM_TASK.equals(reasonType)) {
            return "平台任务";
        }
        return "其它";
    }

}
