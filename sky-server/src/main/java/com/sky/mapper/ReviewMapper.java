package com.sky.mapper;

import com.sky.entity.Review;
import com.sky.vo.ReviewVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewMapper {

    /**
     * 写一条评价
     *
     * ⚠️ 表上有唯一索引 uk_order_from(order_id, from_user_id)：
     * 同一笔订单同一个人只能评一次，并发下重复插入会抛 DuplicateKeyException（兜底防线）。
     */
    @Insert("insert into review (order_id, product_id, from_user_id, to_user_id, star, content, " +
            "is_default, create_time, update_time) " +
            "values (#{orderId}, #{productId}, #{fromUserId}, #{toUserId}, #{star}, #{content}, " +
            "#{isDefault}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Review review);

    /** 查我有没有评过这笔订单（友好提示；唯一索引是并发兜底） */
    @Select("select * from review where order_id = #{orderId} and from_user_id = #{fromUserId}")
    Review getByOrderAndFrom(@Param("orderId") Long orderId, @Param("fromUserId") Long fromUserId);

    /** 我【发出】的评价（买卖双方都能看自己评过谁） */
    @Select("select * from review where from_user_id = #{fromUserId} order by id desc limit 100")
    List<Review> listByFromUser(Long fromUserId);

    /** 某商品的评价列表（SQL 在 ReviewMapper.xml，带双方昵称） */
    List<ReviewVO> listByProduct(Long productId);

    /** 某人【收到】的评价列表（SQL 在 ReviewMapper.xml） */
    List<ReviewVO> listByToUser(Long toUserId);

    /** 某人收到的"≥N 星"评价数（用来统计好评数） */
    @Select("select count(*) from review where to_user_id = #{userId} and star >= #{star}")
    int countByStarAtLeast(@Param("userId") Long userId, @Param("star") int star);

    /** 某人收到的"≤N 星"评价数（用来统计差评数） */
    @Select("select count(*) from review where to_user_id = #{userId} and star <= #{star}")
    int countByStarAtMost(@Param("userId") Long userId, @Param("star") int star);

}
