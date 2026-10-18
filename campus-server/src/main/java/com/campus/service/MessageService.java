package com.campus.service;

import com.campus.dto.MessageSendDTO;
import com.campus.result.PageResult;
import com.campus.vo.ConversationVO;

import java.util.List;

/**
 * 私信服务（会话 + 消息）
 *
 * 核心设计：
 *   ① 会话由 (商品, 买家, 卖家) 三元组唯一确定 —— 表上有唯一索引兜底
 *   ② 消息【先落库，再推送】：WebSocket 只是实时加速器，不是存储通道
 *   ③ 权限一律用「允许法」：只有会话的买卖双方能看/能说
 */
public interface MessageService {

    /**
     * 打开会话（买家在商品详情页点"联系卖家"）
     *
     * 已有会话就直接返回它的 id，没有才新建 —— 保证同一对用户只有一个对话框。
     *
     * @param productId 商品id
     * @return 会话id
     */
    Long openConversation(Long productId);

    /** 我的会话列表（带对方昵称/头像、最后一条消息、未读数） */
    List<ConversationVO> listConversations();

    /** 聊天记录（分页，倒序） */
    PageResult history(Long conversationId, Integer page, Integer pageSize);

    /** 发送消息（落库 + 尝试实时推送） */
    void send(MessageSendDTO sendDTO);

    /** 把某个会话里"发给我的未读消息"标记为已读 */
    void markRead(Long conversationId);

    /** 我的未读消息总数（消息页小圆点） */
    Integer unreadTotal();

    /**
     * 消息搜索（新功能 A）：在【我参与的所有会话】里按关键词搜消息
     *
     * ⚠️ 权限过滤必须写在 SQL 里（只搜我参与的会话），
     *    不能指望 Java 层再过滤一遍。
     */
    PageResult search(String keyword, Integer page, Integer pageSize);

}
