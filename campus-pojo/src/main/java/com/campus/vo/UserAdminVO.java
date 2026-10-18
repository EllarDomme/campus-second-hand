package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端-用户列表项
 *
 * 管理员最关心三件事：这人是谁（昵称/学号）、能不能信（信用分 + 好评差评）、要不要处理（状态/角色）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String nickname;

    private String avatar;

    private String phone;

    private String studentNo;

    /** 角色：0普通用户 1跑腿员 */
    private Integer role;

    /** 角色中文（服务层填，省得前端维护映射表） */
    private String roleName;

    /** 状态：0正常 1封禁 */
    private Integer status;

    private String statusName;

    private Integer creditScore;

    /** 收到的好评数（≥4 星） */
    private Integer goodReviewCount;

    /** 收到的差评数（≤2 星） */
    private Integer badReviewCount;

    private LocalDateTime createTime;

}
