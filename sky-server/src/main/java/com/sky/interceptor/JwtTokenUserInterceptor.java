package com.sky.interceptor;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//Jwt令牌校验的拦截器
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;

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
            BaseContext.setCurrentId(userId);

            //通过，放行
            return true;
        }catch (Exception e){
            response.setStatus(401);
            return false;
        }

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        //请求结束必须清理 ThreadLocal：Tomcat 线程复用，不清理会造成身份残留甚至跨请求串号
        BaseContext.removeCurrentId();
    }

}
