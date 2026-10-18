package com.campus.mapper;

import com.campus.entity.Message;
import com.campus.vo.MessageSearchVO;
import com.campus.vo.MessageVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageMapper {

    @Insert("insert into message (conversation_id, from_user_id, to_user_id, content, msg_type, is_read, " +
            "create_time, update_time) " +
            "values (#{conversationId}, #{fromUserId}, #{toUserId}, #{content}, #{msgType}, #{isRead}, " +
            "#{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Message message);

    /**
     * 分页查聊天记录（倒序取最近的，由 Service 决定怎么给前端）
     * SQL 在 MessageMapper.xml —— 要关联 user 拿发送人昵称/头像
     */
    List<MessageVO> pageHistory(@Param("conversationId") Long conversationId,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    /** 某个会话的消息总数（分页用） */
    @Select("select count(*) from message where conversation_id = #{conversationId}")
    int countByConversation(Long conversationId);

    /**
     * 未读数
     *   conversationId 为 null → 统计我的全部未读（消息列表页的小红点）
     *   传了 conversationId   → 只统计这个会话的未读
     */
    @Select("<script>select count(*) from message where to_user_id = #{toUserId} and is_read = 0" +
            "<if test='conversationId != null'> and conversation_id = #{conversationId}</if></script>")
    int countUnread(@Param("toUserId") Long toUserId, @Param("conversationId") Long conversationId);

    /**
     * 把某个会话里"发给我的未读消息"全部标成已读
     *
     * ⚠️ 条件里必须有 to_user_id = 我：
     *    否则等于允许任何人把别人的消息标成已读（越权）。
     *    这是"允许法"思路 —— 只动"收件人是我"的那些行。
     */
    @Update("update message set is_read = 1, update_time = #{updateTime} " +
            "where conversation_id = #{conversationId} and to_user_id = #{toUserId} and is_read = 0")
    int markRead(@Param("conversationId") Long conversationId,
                 @Param("toUserId") Long toUserId,
                 @Param("updateTime") LocalDateTime updateTime);

    // ==========================================================================
    // 【新功能 A：消息搜索】方法声明我写好了，**SQL 由你写**（写在 MessageMapper.xml 里）
    //
    // 需求：在【我参与的所有会话】里，按关键词搜消息内容，倒序分页返回。
    //   ① 权限必须写进 SQL 的 where 里：只搜 buyer_id = 我 或 seller_id = 我 的会话
    //      —— 绝不能只靠 Java 层过滤，那样会把别人的消息也查出来再丢掉（既低效又不安全）
    //   ② 关键词用 like concat('%', #{keyword}, '%')
    //   ③ 对方昵称要用 if(c.buyer_id = #{userId}, c.seller_id, c.buyer_id) 算（同会话列表）
    //   ④ 排序 + 分页：order by m.id desc limit #{offset}, #{limit}
    //   ⑤ 两个方法都要写：列表 + 总数（分页要给前端 total）
    // ==========================================================================

    /** 搜索我参与会话里的消息（SQL 由你写） */
    List<MessageSearchVO> searchMyMessages(@Param("userId") Long userId,
                                           @Param("keyword") String keyword,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 搜索结果总数（SQL 由你写） */
    int countMyMessages(@Param("userId") Long userId, @Param("keyword") String keyword);

}
