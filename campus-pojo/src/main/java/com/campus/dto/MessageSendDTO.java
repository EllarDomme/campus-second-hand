package com.campus.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送私信请求体
 */
@Data
public class MessageSendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话id */
    private Long conversationId;

    /** 消息内容 */
    private String content;

}
