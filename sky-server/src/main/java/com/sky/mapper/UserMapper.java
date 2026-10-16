package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface UserMapper {
    @Select("select * from user where openid = #{openid}")
    User selectByOpenid(String openid);

    void insert(User user);

    @Select("select * from user where id = #{id}")
    User getById(Long id);

    Long getUserCount(LocalDateTime beginTime, LocalDateTime endTime);

    Integer countByMap(Map map);

    /**
     * 【原子加减信用分】S3-10
     *
     * ⚠️ 为什么让数据库做算术（credit_score = credit_score + ?），
     *    而不是"先查出来 +5 再 update 成新值"：
     *    后者在并发下会丢更新 —— 两个请求同时读到 100，
     *    各自 +2 后都写回 102，实际上应该变成 104。
     *    把加法交给数据库，一条 SQL 就原子了（和商品状态的条件更新同一个思路）。
     */
    @Update("update user set credit_score = credit_score + #{delta}, update_time = now() " +
            "where id = #{id}")
    int addCreditScore(@Param("id") Long id, @Param("delta") int delta);
}