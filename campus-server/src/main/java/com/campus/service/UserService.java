package com.campus.service;

import com.campus.dto.UserLoginDTO;
import com.campus.entity.User;

public interface UserService {
    User login(UserLoginDTO userLoginDTO);

    /**
     * 测试登录：用固定openid查询或自动注册用户，跳过微信code2session
     * @param openid 固定测试openid
     * @return 用户
     */
    User loginByOpenid(String openid);

    /** 按 id 查用户（前端要取自己的头像/昵称用） */
    User getById(Long id);
}
