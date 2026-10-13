package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import lombok.extern.slf4j.Slf4j;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
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
}
