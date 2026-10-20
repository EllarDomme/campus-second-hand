package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO implements Serializable {

    private Long id;
    private String openid;
    private String token;

    /**
     * 昵称与角色：前端登录后要立刻显示"欢迎，小明同学"，
     * 并据此决定是否显示"跑腿大厅（抢单）"入口。
     * 放在登录响应里带出来，比前端再单独发一次请求省事。
     */
    private String avatar;
    private String nickname;
    private Integer role;

}
