package com.campus.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 私信消息
 *
 * 设计要点：
 *   ① 消息是【只增不改】的（除了 is_read 会从 0 变 1）——
 *      所以不需要复杂的更新逻辑，历史就是真相。
 *   ② is_read 是"接收方视角"的：同一条消息对发送方永远是已读，对接收方才分已读/未读。
 *   ③ 表上有 idx_to_read(to_user_id, is_read) 索引 —— 专门给"我的未读总数"这个查询用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long conversationId;

    /** 发送人 */
    private Long fromUserId;

    /** 接收人 */
    private Long toUserId;

    /** 消息内容（数据库 varchar(1000)） */
    private String content;

    /** 消息类型：0普通文本 1系统提示（如"订单已确认"） */
    private Integer msgType;

    /** 是否已读（接收方视角）：0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
