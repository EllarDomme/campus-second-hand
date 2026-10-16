package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端-用户分页查询条件
 */
@Data
public class UserPageQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer page;

    private Integer pageSize;

    /** 状态：0正常 1封禁（不传=全部） */
    private Integer status;

    /** 角色：0普通用户 1跑腿员（不传=全部） */
    private Integer role;

    /** 关键词：昵称 / 手机号 / 学号 模糊匹配（不传=全部） */
    private String keyword;

}
