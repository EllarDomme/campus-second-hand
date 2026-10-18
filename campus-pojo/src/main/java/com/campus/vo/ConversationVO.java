package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话列表项（"我的消息"页面用）
 *
 * 注意 otherUserId / otherNickname 这两列：
 * 一个会话里有两个用户，"对方"是谁取决于【当前登录人】是买家还是卖家。
 * 所以在 SQL 里用 if(c.buyer_id = 我, c.seller_id, c.buyer_id) 算出来 ——
 * 不要在 Java 里再判断一遍，容易两边逻辑不一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话id */
    private Long id;

    private Long productId;

    private String productTitle;

    /** 商品图片 JSON 数组字符串（前端取第一张展示） */
    private String productImages;

    private Long otherUserId;

    private String otherNickname;

    private String otherAvatar;

    /** 最后一条消息内容（会话列表的预览） */
    private String lastMessage;

    private LocalDateTime lastMessageTime;

    /** 我对这个会话的未读数 */
    private Integer unreadCount;

    /** 会话状态：0开放 1已关闭 */
    private Integer status;

}
