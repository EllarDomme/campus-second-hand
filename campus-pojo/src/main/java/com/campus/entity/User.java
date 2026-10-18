package com.campus.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //微信用户唯一标识
    private String openid;

    //昵称
    private String nickname;

    //真是姓名
    private String realName;

    //学号
    private String studentNo;

    //学生卡图像
    private String studentCardImg;

    //手机号
    private String phone;

    //信用分
    private Integer creditScore;

    //角色
    private Integer role;

    //状态
    private Integer status;

    //头像
    private String avatar;

    //注册时间
    private LocalDateTime createTime;

    //更新时间
    private LocalDateTime updateTime;
}
