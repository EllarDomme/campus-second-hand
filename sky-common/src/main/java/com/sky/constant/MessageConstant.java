package com.sky.constant;

/**
 * 信息提示常量类
 *
 * 说明：这里只保留「当前代码真正在用」的提示语。
 * 改造过程中删掉了 15 个已经没有任何引用的旧常量（购物车/地址簿/套餐/菜品/旧订单相关的提示语），
 * 留着它们只会让读代码的人误以为"某个功能还在"。
 *
 * 判断一个常量还有没有人用，最可靠的办法就是全局搜一下常量名（编译器不管未使用的常量，
 * 所以死常量会一直静静躺着）。
 */
public class MessageConstant {

    // ===== 账号与登录 =====
    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String ALREADY_EXIST = "已存在";

    // ===== 通用 =====
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String UPLOAD_FAILED = "文件上传失败";

    // ===== 商品（校园二手）=====
    public static final String PRODUCT_NOT_FOUND = "商品不存在";
    public static final String PRODUCT_IN_TRADE = "商品正在交易中，无法删除";
    public static final String PRODUCT_NOT_BELONG_TO_YOU = "只能操作自己发布的商品";
    public static final String PRODUCT_AUDIT_STATUS_ERROR = "审核状态不合法";

}
