package com.sky.mapper;

import com.sky.dto.DeliveryPageQueryDTO;
import com.sky.entity.DeliveryOrder;
import com.sky.vo.DeliveryOrderVO;
import com.sky.vo.RunnerIncomeVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeliveryOrderMapper {

    /**
     * 创建跑腿单
     *
     * ⚠️ 表上有唯一索引 uk_trade_order(trade_order_id)，
     * 同一笔交易重复插入会抛 DuplicateKeyException —— 这是防"一笔交易扣两次跑腿费"的兜底。
     */
    @Insert("insert into delivery_order (trade_order_id, runner_user_id, pick_address, receive_address, " +
            "delivery_fee, status, accept_time, finish_time, create_time, update_time) " +
            "values (#{tradeOrderId}, #{runnerUserId}, #{pickAddress}, #{receiveAddress}, " +
            "#{deliveryFee}, #{status}, #{acceptTime}, #{finishTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DeliveryOrder deliveryOrder);

    @Select("select * from delivery_order where id = #{id}")
    DeliveryOrder getById(Long id);

    @Select("select * from delivery_order where trade_order_id = #{tradeOrderId}")
    DeliveryOrder getByTradeOrderId(Long tradeOrderId);

    /**
     * 【抢单】条件更新 —— 整个跑腿模块最有技术含量的一句 SQL
     *
     * 多个跑腿员同时点"接单"，靠数据库行锁保证只有一个能成功：
     *   where id = ? and status = 0 and runner_user_id is null
     * 第一个请求执行后 status 变成 1、runner_user_id 被填上，
     * 后面的请求虽然也会执行（排队等锁），但 where 条件已不成立 → 影响 0 行。
     *
     * 为什么不加分布式锁：这是典型的"单行条件更新"场景，
     * 数据库的行级锁已经保证原子性。分布式锁适合"跨多个资源/长事务/非数据库资源"。
     */
    @Update("update delivery_order set runner_user_id = #{runnerUserId}, status = 1, " +
            "accept_time = #{acceptTime}, update_time = #{updateTime} " +
            "where id = #{id} and status = 0 and runner_user_id is null")
    int acceptIfWaiting(@Param("id") Long id,
                        @Param("runnerUserId") Long runnerUserId,
                        @Param("acceptTime") LocalDateTime acceptTime,
                        @Param("updateTime") LocalDateTime updateTime);

    /**
     * 【状态流转】通用条件更新：只有当前状态 = fromStatus 才推进到 toStatus
     *
     * 用来做"确认取货（1→2）""确认送达（2→3）""取消（任意→4）"。
     * 判断影响行数：1 = 推进成功；0 = 状态已经被别人改过（或订单不存在）。
     * finishTime 只在送达时传，其它传 null（XML 里用 <if> 动态拼）。
     */
    @Update("<script>update delivery_order set status = #{toStatus}, update_time = #{updateTime}" +
            "<if test='finishTime != null'>, finish_time = #{finishTime}</if>" +
            " where id = #{id} and status = #{fromStatus}</script>")
    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("fromStatus") Integer fromStatus,
                            @Param("toStatus") Integer toStatus,
                            @Param("finishTime") LocalDateTime finishTime,
                            @Param("updateTime") LocalDateTime updateTime);

    /** 待接单大厅：所有"待接单 + 主订单还活着"的跑腿单（SQL 在 XML） */
    List<DeliveryOrderVO> listAvailable();

    /** 我的跑腿单（跑腿员视角，SQL 在 XML） */
    List<DeliveryOrderVO> listMyOrders(Long runnerUserId);

    /** 跑腿单详情（带主订单/商品/双方昵称，SQL 在 XML） */
    DeliveryOrderVO getDetailById(Long id);

    /** 跑腿员收入统计（聚合查询，SQL 在 XML） */
    RunnerIncomeVO getIncome(Long runnerUserId);

    /**
     * 某个跑腿员手上【还没结束】的跑腿单数（S4-1 用）
     *
     * 用途：管理员要把跑腿员降级成普通用户之前，得先确认他手上没有进行中的单子 ——
     * 否则单子没人送了，买家干等。这是"角色变更"必须有的业务守卫。
     * 进行中 = 已接单(1) 或 取货中(2)。
     */
    @Select("select count(*) from delivery_order where runner_user_id = #{runnerUserId} " +
            "and status in (1, 2)")
    int countOngoingByRunner(Long runnerUserId);

    /**
     * 【管理端】跑腿单分页列表（S4-4）
     *
     * 和 listMyOrders 的区别：可以按状态/跑腿员/交易订单任意筛选。
     * 管理员要的是"全局视角" —— 谁手上压着单子、哪些单子卡住了。
     * SQL 在 DeliveryOrderMapper.xml，分页交给 PageHelper。
     */
    List<DeliveryOrderVO> listForAdmin(DeliveryPageQueryDTO query);

}
