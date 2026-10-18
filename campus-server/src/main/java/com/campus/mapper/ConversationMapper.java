package com.campus.mapper;

import com.campus.entity.Conversation;
import com.campus.vo.ConversationVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationMapper {

    /**
     * 新增会话
     *
     * ⚠️ 表上有唯一索引 uk_product_buyer_seller，
     * 并发下重复插入会抛 DuplicateKeyException —— 这是防"同一对用户出现两个对话框"的兜底。
     */
    @Insert("insert into conversation (product_id, buyer_id, seller_id, order_id, status, can_send_until, " +
            "last_message_time, create_time, update_time) " +
            "values (#{productId}, #{buyerId}, #{sellerId}, #{orderId}, #{status}, #{canSendUntil}, " +
            "#{lastMessageTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Conversation conversation);

    @Select("select * from conversation where id = #{id}")
    Conversation getById(Long id);

    /**
     * 按"商品 + 买家 + 卖家"查会话（这就是唯一索引对应的三个字段）
     *
     * 用途：用户点"联系卖家"时先查一下，已有会话就直接返回它的 id，
     * 不再新建 —— 这是给用户的友好体验，唯一索引才是并发兜底。
     */
    @Select("select * from conversation where product_id = #{productId} " +
            "and buyer_id = #{buyerId} and seller_id = #{sellerId}")
    Conversation getByProductAndUsers(@Param("productId") Long productId,
                                      @Param("buyerId") Long buyerId,
                                      @Param("sellerId") Long sellerId);

    /**
     * 我的会话列表（带对方昵称/头像、最后一条消息、我的未读数）
     * SQL 在 ConversationMapper.xml —— 需要关联商品和用户，还带两个子查询
     */
    List<ConversationVO> listMine(Long userId);

    /** 动态更新：只更新传了值的字段（发消息后改 last_message_time、关闭会话时改 status） */
    void update(Conversation conversation);

}
