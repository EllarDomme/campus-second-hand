package com.campus.interceptor;

import com.campus.constant.JwtClaimsConstant;
import com.campus.constant.UserConstant;
import com.campus.context.BaseContext;
import com.campus.entity.User;
import com.campus.mapper.UserMapper;
import com.campus.properties.JwtProperties;
import com.campus.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

//Jwt令牌校验的拦截器
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller方法还是其他类型的方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        //从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        //校验令牌
        try{
            //不打印完整令牌：日志会落盘，明文 token 等同于凭据泄露
            log.info("开始校验用户令牌，长度：{}", token == null ? 0 : token.length());
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            log.info("当前用户ID: {}", userId);

            // 【S4-1】封禁校验：被封禁的账号不允许使用任何用户端接口
            if (isBanned(userId)) {
                log.warn("已封禁用户尝试访问接口，已拒绝：userId={}, uri={}", userId, request.getRequestURI());
                response.setStatus(401);
                return false;
            }

            BaseContext.setCurrentId(userId);

            //通过，放行
            return true;
        }catch (Exception e){
            response.setStatus(401);
            return false;
        }

    }

    /**
     * 用户是否被封禁（S4-1）
     *
     * 【为什么必须放在拦截器里，而不是只在登录时校验】
     *   JWT 的有效期比较长，封禁之前签发的 token 依然合法 ——
     *   只在登录时拦，等于"封禁后他还能继续用到 token 过期为止"。
     *
     * 【为什么要加 Redis 缓存】
     *   拦截器是每个请求都会走的，直接查库会让 QPS 一高数据库先跪。
     *   封禁状态极少变化，缓存 5 分钟完全够用；
     *   管理员封禁/解封时会主动删缓存，保证立即生效。
     *
     * 【失败时为什么选择"放行"】
     *   查库/Redis 异常时返回 false（不封禁），也就是 fail-open：
     *   可用性优先 —— 不能因为 Redis 抖一下，就把全站正常用户挡在门外。
     *   （对比：涉及"能不能做某件危险操作"的校验应该 fail-closed；
     *     这里只是"拦坏人"，拦不住最多漏一个，代价远小于全站不可用。）
     */
    private boolean isBanned(Long userId) {
        try {
            String key = UserConstant.STATUS_CACHE_KEY + userId;
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return String.valueOf(UserConstant.STATUS_BANNED).equals(cached);
            }
            User user = userMapper.getById(userId);
            if (user == null) {
                return false;
            }
            Integer status = user.getStatus();
            stringRedisTemplate.opsForValue().set(key, String.valueOf(status),
                    UserConstant.STATUS_CACHE_MINUTES, TimeUnit.MINUTES);
            return UserConstant.STATUS_BANNED.equals(status);
        } catch (Exception e) {
            log.warn("封禁状态校验失败，本次放行：userId={}, err={}", userId, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        //请求结束必须清理 ThreadLocal：Tomcat 线程复用，不清理会造成身份残留甚至跨请求串号
        BaseContext.removeCurrentId();
    }

}
