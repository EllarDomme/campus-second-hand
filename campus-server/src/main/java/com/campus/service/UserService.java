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
}
