package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息（聊天记录里的单条）
 *
 * 带上发送人昵称/头像，前端就不用再额外查一次用户信息了。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long conversationId;

    private Long fromUserId;

    private String fromNickname;

    private String fromAvatar;

    private String content;

    /** 0普通文本 1系统提示 */
    private Integer msgType;

    /** 接收方视角：0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createTime;

}
