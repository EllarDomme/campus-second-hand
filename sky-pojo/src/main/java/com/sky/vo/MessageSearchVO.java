package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息搜索结果项（"在聊天记录里搜关键词"）
 *
 * 和 MessageVO 的区别：搜索结果要给用户"上下文"——
 * 这条消息是【在哪个会话里】、【跟谁】、【关于哪件商品】说的，
 * 否则用户看到一句"好的"根本不知道是在哪聊的。
 *
 * 所以这个查询需要三表关联：
 *   message → conversation（权限过滤 + 找对方）→ product / user（补展示信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSearchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long conversationId;

    /** 发送人id（前端靠它判断"这条是我发的还是对方发的"，光有昵称不够） */
    private Long fromUserId;

    private String content;

    private LocalDateTime createTime;

    /** 消息属于哪件商品（会话上下文） */
    private String productTitle;

    /** 对方昵称（我是买家就是卖家，反之亦然） */
    private String otherNickname;

    /** 发送人昵称（用来区分"我发的"和"对方发的"） */
    private String fromNickname;

}
