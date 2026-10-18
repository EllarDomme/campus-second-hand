package com.campus.constant;

/**
 * 用户（账号）相关常量
 *
 * 说明：原来这些值散落在 DeliveryConstant 里（当时只有跑腿用得到），
 * 现在"封禁"成了跨模块的概念（拦截器、登录、跑腿抢单都要判），
 * 所以集中到 UserConstant，DeliveryConstant 改为引用这里，避免两处定义各改一半。
 */
public class UserConstant {

    private UserConstant() {
    }

    // ---------- 角色 ----------
    /** 普通用户 */
    public static final Integer ROLE_NORMAL = 0;
    /** 跑腿员 */
    public static final Integer ROLE_RUNNER = 1;

    // ---------- 账号状态 ----------
    /** 正常 */
    public static final Integer STATUS_NORMAL = 0;
    /** 已封禁 */
    public static final Integer STATUS_BANNED = 1;

    // ---------- 缓存 ----------
    /**
     * 封禁状态的缓存 key 前缀：user_status:{userId}
     *
     * 拦截器每个请求都要判断"这人被封了没"，直接查库扛不住；
     * 封禁状态变化极少，缓存 5 分钟足够。
     * ⚠️ 管理员封禁/解封时必须【主动删掉这个 key】，
     *    否则最长 5 分钟内拦截器还拿着旧状态，出现"封了但还能用"的假象。
     */
    public static final String STATUS_CACHE_KEY = "user_status:";

    /** 封禁状态缓存时长（分钟） */
    public static final long STATUS_CACHE_MINUTES = 5;

    /** 状态 → 中文 */
    public static String statusName(Integer status) {
        if (STATUS_BANNED.equals(status)) {
            return "已封禁";
        }
        return "正常";
    }

    /** 角色 → 中文 */
    public static String roleName(Integer role) {
        if (ROLE_RUNNER.equals(role)) {
            return "跑腿员";
        }
        return "普通用户";
    }

}
