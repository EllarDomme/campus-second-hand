package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户公开主页（S6-3）
 *
 * ⚠️ 这个对象会展示给【任何登录用户】，所以必须遵守数据最小化：
 *    User 实体里有 openid / realName / studentNo / studentCardImg / phone，
 *    这些【一个都不能出现在这里】—— 别人的学号、手机号、学生证照片是隐私。
 *
 *    能公开的只有：昵称、头像、角色、信用分、评价统计、商品统计、加入天数。
 *    （面试常问"你怎么防止数据泄露" —— 这就是一个具体答案：
 *      "不要直接把实体返回给前端，用专门的 VO 只挑能公开的字段"）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String nickname;

    private String avatar;

    /** 0 普通用户 / 1 跑腿员 */
    private Integer role;

    /** 信用分（这是别人最关心的一个数字） */
    private Integer creditScore;

    /** 收到的好评数（≥4 星） */
    private Integer goodReviewCount;

    /** 收到的差评数（≤2 星） */
    private Integer badReviewCount;

    /** 当前在卖的商品数 */
    private Integer onSaleCount;

    /** 已经卖出去的商品数 */
    private Integer soldCount;

    /** 加入平台多少天（老用户 = 更可信） */
    private Integer joinedDays;

}
