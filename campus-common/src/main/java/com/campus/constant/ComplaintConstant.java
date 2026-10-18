package com.campus.constant;

/**
 * 投诉工单相关常量
 */
public class ComplaintConstant {

    private ComplaintConstant() {
    }

    // ---------- 工单类型 ----------
    /** 商品举报（针对商品本身，可不关联订单） */
    public static final Integer TYPE_PRODUCT_REPORT = 0;
    /** 交易纠纷（针对某笔交易，必须关联订单） */
    public static final Integer TYPE_TRADE_DISPUTE = 1;

    // ---------- 投诉原因（细分，需求里明确列了这几种）----------
    /** 货不对板 */
    public static final Integer REASON_NOT_AS_DESCRIBED = 1;
    /** 虚假描述 */
    public static final Integer REASON_FAKE_DESCRIPTION = 2;
    /** 对方失联 */
    public static final Integer REASON_LOST_CONTACT = 3;
    /** 跑腿纠纷 */
    public static final Integer REASON_DELIVERY_DISPUTE = 4;
    /** 其他 */
    public static final Integer REASON_OTHER = 9;

    // ---------- 处理状态 ----------
    public static final Integer STATUS_PENDING = 0;   // 待处理
    public static final Integer STATUS_HANDLING = 1;  // 处理中
    public static final Integer STATUS_DONE = 2;      // 已完结

    // ---------- 处理结果（S3-10b 补的字段）----------
    /** 投诉成立 → 被投诉人扣信用分 */
    public static final Integer RESULT_UPHELD = 1;
    /** 投诉不成立 → 不动信用分 */
    public static final Integer RESULT_REJECTED = 2;

    // ---------- 内容限制 ----------
    /** 投诉内容：数据库是 text 没硬限制，但业务层要拦住"粘贴几万字" */
    public static final int MAX_CONTENT_LENGTH = 2000;
    /** 凭证图片 JSON 串最长 1000（数据库 varchar(1000)） */
    public static final int MAX_IMAGES_LENGTH = 1000;
    /** 最多上传几张凭证 */
    public static final int MAX_IMAGE_COUNT = 6;

    /** 工单类型 → 中文 */
    public static String typeName(Integer type) {
        if (TYPE_PRODUCT_REPORT.equals(type)) {
            return "商品举报";
        }
        if (TYPE_TRADE_DISPUTE.equals(type)) {
            return "交易纠纷";
        }
        return "未知";
    }

    /** 投诉原因 → 中文 */
    public static String reasonName(Integer reason) {
        if (REASON_NOT_AS_DESCRIBED.equals(reason)) {
            return "货不对板";
        }
        if (REASON_FAKE_DESCRIPTION.equals(reason)) {
            return "虚假描述";
        }
        if (REASON_LOST_CONTACT.equals(reason)) {
            return "对方失联";
        }
        if (REASON_DELIVERY_DISPUTE.equals(reason)) {
            return "跑腿纠纷";
        }
        if (REASON_OTHER.equals(reason)) {
            return "其他";
        }
        return "未知";
    }

    /** 处理状态 → 中文 */
    public static String statusName(Integer status) {
        if (STATUS_PENDING.equals(status)) {
            return "待处理";
        }
        if (STATUS_HANDLING.equals(status)) {
            return "处理中";
        }
        if (STATUS_DONE.equals(status)) {
            return "已完结";
        }
        return "未知";
    }

}
