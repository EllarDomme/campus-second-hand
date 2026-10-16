package com.sky.constant;

import java.math.BigDecimal;

/**
 * 私信（会话 + 消息）相关常量
 */
public class ChatConstant {

    private ChatConstant() {
    }

    // ---------- 会话状态 ----------
    /** 会话开放（可以发消息） */
    public static final Integer CONVERSATION_OPEN = 0;
    /** 会话已关闭 */
    public static final Integer CONVERSATION_CLOSED = 1;

    // ---------- 消息类型 ----------
    /** 普通文本（用户发的） */
    public static final Integer MSG_TYPE_TEXT = 0;
    /** 系统提示（系统发的，比如"订单已确认"） */
    public static final Integer MSG_TYPE_SYSTEM = 1;
    /** 系统消息的发送人id：用它表示"系统"，而不是某个真实用户 */
    public static final Long SYSTEM_USER_ID = 0L;

    // ---------- 已读状态 ----------
    /** 未读 */
    public static final Integer UNREAD = 0;
    /** 已读 */
    public static final Integer READ = 1;

    // ---------- 内容限制 ----------
    /** 消息内容最大长度（与数据库 message.content varchar(1000) 对齐） */
    public static final int MAX_CONTENT_LENGTH = 1000;

    // ---------- WebSocket 推送协议（约定用 type 字段区分种类）----------
    //
    // 为什么要有"协议"：WebSocket 只传字符串，服务端会推好几种东西
    // （新消息 / 已读回执 / 未读数变化），前端必须能区分开。
    // 所以在 JSON 里固定放一个 type 字段，前端 switch(type) 分发处理。
    //
    // 三种推送长这样：
    //   {"type":"message","conversationId":1,"fromUserId":2,"content":"在吗","createTime":"..."}
    //   {"type":"read","conversationId":1,"readerId":1}
    //   {"type":"unread","total":3}

    /** 推送类型：新消息 */
    public static final String PUSH_TYPE_MESSAGE = "message";
    /** 推送类型：已读回执（对方读了我发的消息） */
    public static final String PUSH_TYPE_READ = "read";
    /** 推送类型：未读总数变化（前端小红点） */
    public static final String PUSH_TYPE_UNREAD = "unread";

    /** 搜索分页的每页上限（防止前端传 pageSize=10000 把库拖垮） */
    public static final int MAX_PAGE_SIZE = 50;

}
