package com.sky.controller.user;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @Value("${sky.debug.enabled:false}")
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
                .build();

        return Result.success(userLoginVO);
    }

    /**
     * 测试登录：用指定openid签发C端JWT，供AI点餐客服等外部系统模拟登录
     * 不同 openid 会查/建不同的用户，从而实现"多用户身份隔离"
     * @param openid 用户唯一标识（不传则默认测试账号 test_openid_0001）
     * @return 用户的id、openid、token
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
                .build();

        return Result.success(userLoginVO);
    }
}
