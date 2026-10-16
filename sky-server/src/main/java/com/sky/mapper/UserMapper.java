package com.sky.mapper;

import com.sky.dto.UserPageQueryDTO;
import com.sky.entity.User;
import com.sky.vo.UserAdminVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
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

    // ==================== 管理端-用户管理（S4-1）====================

    /** 管理端用户分页（带信用分与好评差评数，SQL 在 UserMapper.xml） */
    List<UserAdminVO> listForAdmin(UserPageQueryDTO query);

    /** 管理端看某个用户的详情（同上，按 id 查一条） */
    UserAdminVO getAdminDetailById(Long id);

    /**
     * 封禁 / 解封
     *
     * 只改 status 一个字段，故意不做成通用的 update ——
     * 管理员能改的只有"是否封禁"，不能顺手改别人的信用分/角色（那是别的流程的事）。
     */
    @Update("update user set status = #{status}, update_time = now() where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}