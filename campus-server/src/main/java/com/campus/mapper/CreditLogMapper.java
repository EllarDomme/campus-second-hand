package com.campus.mapper;

import com.campus.entity.CreditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CreditLogMapper {

    @Insert("insert into credit_log (user_id, change_score, score_after, reason_type, ref_id, remark, " +
            "create_time, update_time) " +
            "values (#{userId}, #{changeScore}, #{scoreAfter}, #{reasonType}, #{refId}, #{remark}, " +
            "#{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(CreditLog creditLog);

    /**
     * 【每日加分上限的核心查询】今天这个用户已经加了多少分
     *
     * 两个关键点：
     *   ① change_score > 0 —— 只统计【加分】。扣分不设上限，
     *      否则恶意用户靠着"每天最多扣 10 分"就能一直赖着。
     *   ② create_time >= fromTime —— 只统计今天（从今天 00:00 开始）。
     *      这正是"为什么 credit_log 不是可选项"的原因：
     *      user 表只有一个总分、没有时间戳，光靠它根本算不出"今天加了多少"。
     *
     * ifnull(...,0)：一条都没有时 sum 返回 NULL，不包起来会 NPE。
     */
    @Select("select ifnull(sum(change_score), 0) from credit_log " +
            "where user_id = #{userId} and change_score > 0 and create_time >= #{fromTime}")
    Integer sumAddedSince(@Param("userId") Long userId, @Param("fromTime") LocalDateTime fromTime);

    /** 我的信用分流水（倒序，带原因类型，前端翻译成中文） */
    @Select("select * from credit_log where user_id = #{userId} order by id desc limit 100")
    List<CreditLog> listByUser(Long userId);

}
