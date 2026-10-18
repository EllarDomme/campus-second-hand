package com.campus.websocket;

import lombok.extern.slf4j.Slf4j;
import com.campus.constant.JwtClaimsConstant;
import com.campus.properties.JwtProperties;
import com.campus.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务（私信实时推送）
 *
 * 连接地址：/ws/{sid}，私信场景里 sid 就传【当前登录用户的 id】
 *
 * ┌── 一个必须搞清楚的认知 ────────────────────────────────────────
 * │ WebSocket 只是「实时性加速器」，【不是】消息的存储通道。
 * │ 正确姿势是：消息先落库 → 再看对方在不在线 → 在线就推一把。
 * │ 不在线就不管了：他下次登录拉历史记录，照样能看到。
 * │
 * │ 反过来（只在 WebSocket 里转发、不落库）会有两个致命问题：
 * │   ① 对方不在线 → 消息永久丢失
 * │   ② 服务器重启 → sessionMap 清空，所有在线连接失效
 * └──────────────────────────────────────────────────────────────
 *
 * 实现细节：@ServerEndpoint 的类【每个连接都会 new 一个实例】，
 * 所以在线用户表必须是 static 的，否则实例之间互相看不见。
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    /** 在线会话表：sid(=用户id) → Session */
    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * ⚠️ 为什么用 static 字段来存 JwtProperties
     *
     * @ServerEndpoint 的类【每建立一条连接，容器就 new 一个实例】，
     * 而这些实例由 WebSocket 容器创建，**不走 Spring 的依赖注入** ——
     * 在里面直接写 @Autowired 字段，运行时永远是 null（经典坑）。
     *
     * 标准解法：让 Spring 创建的那个 Bean 实例去注入，再存进 static 字段共享给所有连接实例。
     */
    private static JwtProperties jwtProperties;

    @Autowired
    public void setJwtProperties(JwtProperties properties) {
        WebSocketServer.jwtProperties = properties;
    }

    /**
     * 连接建立成功调用的方法
     *
     * ☠️ 这里必须自己做鉴权，原因是：
     *    WebSocket 握手（HTTP Upgrade）【不会经过 Spring MVC 的拦截器】，
     *    所以 JwtTokenUserInterceptor 根本保护不到这个端点。
     *    而我们的 sid 就是用户 id，不鉴权的后果是：
     *    **任何人连上 /ws/1 就能收走 1 号用户的全部私信**。
     *
     * 鉴权方式：连接时带上 token（ws://localhost:8080/ws/1?token=xxx），
     *          解析出 userId 并要求它与 sid 一致。
     *
     * 取舍说明：token 放在 URL 查询串里有缺点 —— 会被网关/服务器访问日志记录。
     * 更严格的做法是"一次性票据"：先用 HTTP 换一个短期票据，再用票据连 WebSocket。
     * 学习项目用查询串够用，但面试被问到要能说出这个差别。
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        Long tokenUserId = parseUserId(session.getQueryString());
        if (tokenUserId == null || !String.valueOf(tokenUserId).equals(sid)) {
            log.warn("WebSocket 鉴权失败，拒绝连接：sid={}", sid);
            try {
                session.close();
            } catch (IOException e) {
                log.error("关闭非法连接失败", e);
            }
            return;
        }
        log.info("WebSocket 连接建立：sid={}，当前在线 {} 人", sid, sessionMap.size() + 1);
        sessionMap.put(sid, session);
    }

    /**
     * 收到客户端消息
     *
     * 私信是走 HTTP 接口落库的（POST /user/message/send），
     * 所以这里只记日志：客户端不需要通过 WebSocket 发消息，只用它收消息。
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到客户端消息：sid={}, message={}", sid, message);
    }

    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("WebSocket 连接断开：sid={}", sid);
        sessionMap.remove(sid);
    }

    /**
     * 给【指定用户】推送消息（用户不在线则静默跳过）
     *
     * 这是本项目在原生"群发"基础上新增的能力：
     * 私信必须是点对点的，群发会把 A 的私信推给所有人。
     *
     * @param userId  接收人id
     * @param message 消息内容（JSON 字符串，前端按 type 字段区分处理）
     */
    public void sendToUser(Long userId, String message) {
        Session session = sessionMap.get(String.valueOf(userId));
        if (session == null) {
            log.info("用户 {} 不在线，跳过实时推送（消息已落库，登录后可拉历史）", userId);
            return;
        }
        try {
            session.getBasicRemote().sendText(message);
            log.info("已实时推送给用户 {}：{}", userId, message);
        } catch (Exception e) {
            // 推送失败不影响业务：消息已经存库了，用户拉历史照样能看到
            log.error("WebSocket 推送失败：userId={}", userId, e);
        }
    }

    /** 用户是否在线（排查问题 / 后续做"在线状态"展示用） */
    public boolean isOnline(Long userId) {
        return sessionMap.containsKey(String.valueOf(userId));
    }

    /**
     * 从查询串里取出 token 并解析出 userId（解析失败返回 null）
     */
    private Long parseUserId(String queryString) {
        if (queryString == null || jwtProperties == null) {
            return null;
        }
        String token = null;
        for (String pair : queryString.split("&")) {
            if (pair.startsWith("token=")) {
                token = pair.substring("token=".length());
                break;
            }
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            // token 是 base64url 编码的，本身就是 URL 安全的，不用再解码
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            return Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
        } catch (Exception e) {
            // 不打印完整 token：日志会落盘，明文 token 等同于凭据泄露
            log.warn("WebSocket token 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /** 群发（保留原生能力） */
    public void sendToAllClient(String message) {
        Collection<Session> sessions = sessionMap.values();
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("WebSocket 群发失败", e);
            }
        }
    }

}
