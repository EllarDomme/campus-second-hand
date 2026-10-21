package com.campus.controller.user;

import com.campus.constant.JwtClaimsConstant;
import com.campus.dto.UserLoginDTO;
import com.campus.entity.User;
import com.campus.properties.JwtProperties;
import com.campus.context.BaseContext;
import com.campus.result.Result;
import com.campus.service.UserService;
import com.campus.utils.JwtUtil;
import com.campus.vo.UserLoginVO;
import com.campus.vo.UserProfileVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")
@Api(tags = "C端用户相关接口")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 调试接口开关：默认关闭，仅在 application-dev.yml 中显式开启。
     * testLogin 可以按任意 openid 签发 C 端 JWT，属于调试后门，绝不能在生产环境开放。
     */
    @Value("${campus.debug.enabled:false}")
    private boolean debugEnabled;

    @PostMapping("/login")
    @ApiOperation("微信登录")
    public Result login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("用户登录授权码：{}",userLoginDTO);

        //通过Service方法获取用户id，openid，以及token
        User user = userService.login(userLoginDTO);

        //为微信用户生成JWT令牌
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID,user.getId());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(),jwtProperties.getUserTtl(),claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .avatar(user.getAvatar())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();

        return Result.success(userLoginVO);
    }

    /**
     * 取当前登录用户的资料（昵称、头像、角色）
     *
     * 为什么需要这个接口：登录响应里虽然有头像，但它被存在前端 localStorage 里，
     * 一旦换过头像/改过昵称，前端还是旧的；而且老用户登录过之后也没有 avatar 字段。
     * 页面上需要"打开就取一次最新资料"，所以单独给一个轻量接口。
     */
    @GetMapping("/info")
    @ApiOperation("取当前登录用户资料")
    public Result<UserLoginVO> info() {
        User user = userService.getById(BaseContext.getCurrentId());
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build());
    }
    /**
     * 测试登录：用指定openid签发C端JWT（不需要微信，供本地开发和前端联调）
     * 不同 openid 会查/建不同的用户，从而实现"多用户身份隔离"
     * @param openid 用户唯一标识（不传则默认测试账号 test_openid_0001）
     * @return 用户的id、openid、token、昵称、角色
     */
    @GetMapping("/testLogin")
    @ApiOperation("测试登录：按openid签发C端JWT")
    public Result testLogin(@RequestParam(value = "openid", required = false, defaultValue = "test_openid_0001") String openid) {
        log.info("测试登录，签发C端JWT，openid={}", openid);

        //调试开关：关闭时直接拒绝，避免测试后门在生产环境生效
        if (!debugEnabled) {
            return Result.error("调试接口未开启");
        }

        //通过openid查询或自动注册用户
        User user = userService.loginByOpenid(openid);

        //为该测试用户生成JWT令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .avatar(user.getAvatar())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();

        return Result.success(userLoginVO);
    }

    /**
     * 【S6-3】用户公开主页（看别人的信用分、评价、在卖什么）
     * 用法：GET /user/user/profile/1000
     */
    @GetMapping("/profile/{userId}")
    @ApiOperation("用户公开主页")
    public Result<UserProfileVO> profile(@PathVariable Long userId) {
        return Result.success(userService.getProfile(userId));
    }
}
