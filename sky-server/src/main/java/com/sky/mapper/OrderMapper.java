package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    void insert(Orders order);

    /**
     * 根据订单号和用户id查询订单
     * @param orderNumber
     * @param userId
     */
    @Select("select * from orders where number = #{orderNumber} and user_id= #{userId}")
    Orders getByNumberAndUserId(String orderNumber, Long userId);

    /**
     * 根据订单号查询订单（用于支付回调，无userId上下文）
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息s
     * @param orders
     */
    void update(Orders orders);

    Page<Orders> selectList(OrdersPageQueryDTO ordersPageQueryDTO);

    @Select("select * from orders where id = #{id}")
    Orders selectById(Long id);

    @Select("select count(*) from orders where status = #{status}")
    Long selectCountByStatus(Integer status);

    /**
     * 批量取消超时未支付的订单
     *
     * @param cancelReason    取消原因
     * @param cancelTime      取消时间
     * @param deadline        下单时间早于该时刻的待付款订单视为超时
     * @param cancelledStatus 取消状态值（由调用方传入 Orders.CANCELLED，避免硬编码魔法值）
     * @return 取消的订单数
     */
    @Update("update orders set status = #{cancelledStatus}, cancel_reason = #{cancelReason}, cancel_time = #{cancelTime} " +
            "where status = 1 and pay_status = 0 and order_time < #{deadline} limit 200")
    Integer cancelExpiredOrders(@Param("cancelReason") String cancelReason,
                                @Param("cancelTime") LocalDateTime cancelTime,
                                @Param("deadline") LocalDateTime deadline,
                                @Param("cancelledStatus") Integer cancelledStatus);

    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    Double sumByMap(Map map);

    Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime);

    Integer getValidOrderCount(LocalDateTime beginTime, LocalDateTime endTime, Integer status);

    Integer countByMap(Map map);
}
