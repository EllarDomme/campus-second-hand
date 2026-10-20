package com.campus.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.campus.constant.MessageConstant;
import com.campus.constant.UserConstant;
import lombok.extern.slf4j.Slf4j;
import com.campus.dto.UserLoginDTO;
import com.campus.entity.User;
import com.campus.exception.BaseException;
import com.campus.exception.LoginFailedException;
import com.campus.mapper.UserMapper;
import com.campus.properties.WeChatProperties;
import com.campus.service.UserService;
import com.campus.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    //微信服务接口地址
    private static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatProperties weChatProperties;

    private User getOrCreateByOpenid(String openid){
        //判断当前用户是否为新用户
        User user = userMapper.selectByOpenid(openid);
        if(user != null){
            // 【S4-1】封禁校验：被封禁的账号不允许重新登录
            //   双保险设计：拦截器管"手里已有的 token"，这里管"重新登录拿新 token"。
            //   只做拦截器 → 他等 token 过期后重新登录又能用；
            //   只做这里   → 他拿着封禁前签发的 token 还能继续用。
            //   两边都做才堵得严。
            if (UserConstant.STATUS_BANNED.equals(user.getStatus())) {
                log.warn("已封禁用户尝试登录，已拒绝：userId={}, openid={}", user.getId(), openid);
                throw new BaseException("账号已被封禁，如有疑问请联系平台管理员");
            }
            return user;
        }

        //如果是新用户，那么就会自动进行注册
        User newUser = User.builder()
                .openid(openid)
                .creditScore(100)
                .role(0)
                .status(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        try{
            userMapper.insert(newUser);
            return newUser;
        }catch(DuplicateKeyException e){
            log.warn("openid={}并发注册冲突，回查已有用户",openid);
            return userMapper.selectByOpenid(openid);
        }
    }


    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    @Override
    public User login(UserLoginDTO userLoginDTO) {
        String openid = getOpenid(userLoginDTO.getCode());
        if(openid == null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        return getOrCreateByOpenid(openid);
    }

    /**
     * 测试登录：用固定openid查询用户，不存在则自动注册（跳过微信code2session）
     * @param openid 固定测试openid
     * @return 用户
     */
    @Override
    public User loginByOpenid(String openid) {
        return getOrCreateByOpenid(openid);
    }

    /**
     * 调用微信接口服务，获取微信用户的openid
     */
    private String getOpenid(String code){
        //调用微信接口服务，获取当前用户的openid
        Map<String,String> map = new HashMap<>();
        map.put("appid",weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("js_code",code);
        map.put("grant_type","authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, map);

        if (json == null || json.isEmpty()) {
            log.error("微信登录接口返回为空，code: {}", code);
            return null;
        }

        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");

        if (openid == null) {
            String errMsg = jsonObject.getString("errmsg");
            log.error("微信登录失败，errcode: {}, errmsg: {}",
                    jsonObject.getString("errcode"), errMsg);
        }

        return openid;
    }

    @Override
    public User getById(Long id) {
        return userMapper.getById(id);
    }

}
