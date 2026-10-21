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

    /**
     * 【S6-3 你的练习】用户公开主页
     *
     * @param userId 要看谁的主页
     * @return 只含能公开的字段（昵称/头像/信用分/评价统计/商品统计/加入天数）
     */
    com.campus.vo.UserProfileVO getProfile(Long userId);
}
