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
import com.campus.mapper.ReviewMapper;
import com.campus.mapper.ProductMapper;
import com.campus.vo.UserProfileVO;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    /** 【S6-3】主页要用到评价统计 */
    @Autowired
    private ReviewMapper reviewMapper;

    /** 【S6-3】主页要用到商品统计 */
    @Autowired
    private ProductMapper productMapper;

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

    /**
     * 【S6-3 你的练习】用户公开主页
     */
    @Override
    public UserProfileVO getProfile(Long userId) {
        // ================= 你的部分：填空 1 ~ 5 =================
        //
        // 【1】参数与存在性
        //   · userId 为 null → throw new BaseException("用户不存在")
        //   · userMapper.getById(userId) 为 null → 同上
        //
        // 【2】评价统计（ReviewMapper 已经有现成方法，直接用）
        //   · 好评数：reviewMapper.countByStarAtLeast(userId, 4)   // ≥4 星算好评
        //   · 差评数：reviewMapper.countByStarAtMost(userId, 2)    // ≤2 星算差评
        //   （3 星算中评，既不是好评也不是差评 —— 所以上一步算好评时会自动排除）
        //
        // 【3】商品统计（ProductMapper.countByMap 接收一个 Map，里面放筛选条件）
        //   · 在售：  new HashMap<>() 里 put("status", 0) → productMapper.countByMap(map)
        //   · 已售出：put("status", 2)
        //   · ⚠️ countByMap 返回的是 Integer，可能是 null，要兜底成 0
        //
        // 【4】加入天数
        //   · user.getCreateTime() 是 LocalDateTime
        //   · int days = (int) ChronoUnit.DAYS.between(user.getCreateTime().toLocalDate(), LocalDate.now())
        //   · 兜底：createTime 为 null → 0；算出来是负数（时钟问题）→ 也当 0
        //
        // 【5】组装 UserProfileVO（用 builder）
        //   ⚠️⚠️ 只放这几个字段：userId / nickname / avatar / role /
        //        creditScore / goodReviewCount / badReviewCount /
        //        onSaleCount / soldCount / joinedDays
        //   ⚠️⚠️ 绝对不要放 openid / realName / studentNo / studentCardImg / phone
        //        —— 这个对象是给"任何登录用户"看的，泄露别人的学号和手机号是事故。
        //
        // 【自测】
        //   用户 1000（林小满）：应该有在售商品、有 1 条好评、信用分 128
        //   用户 999999（不存在）：应该抛"用户不存在"
        // =======================================================
        throw new UnsupportedOperationException("S6-3 待实现：getProfile 的填空 1~5");
    }
}
