package com.sky.task;

import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理派送中状态的订单：派送超过60分钟的订单自动置为已完成
     *
     * ⚠️ 改造期间临时停用：本方法依赖老订单表 orders 与 Orders 实体的配送字段，
     * 新库 campus_second_hand 里没有 orders 表，保留 @Scheduled 会每分钟刷一堆
     * "Table 'xxx.orders' doesn't exist" 的异常，干扰其它日志。
     * 阶段 3 重写订单模块（Orders → TradeOrder）时，再按新的跑腿模型决定是删除还是重写本方法。
     */
    //@Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrders() {
        log.info("处理待配送订单:{}", new Date());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);

        if (ordersList != null && ordersList.size() > 0) {
            ordersList.forEach(order -> {
                order.setStatus(Orders.COMPLETED);
                order.setDeliveryTime(LocalDateTime.now());
                orderMapper.update(order);
            });
        }
    }

    /**
     * 关闭超时未支付的订单：每 1 分钟扫描一次，取消 15 分钟前创建且仍未支付的订单
     *
     * 注意：@Scheduled 在多实例部署时每个实例都会触发，虽然 SQL 幂等但仍会重复扫描；
     * 生产环境应配合分布式锁（ShedLock / Redisson）或独立调度中心（XXL-JOB）。
     * 更优方案是延迟队列（RocketMQ 延迟消息 / RabbitMQ 死信队列 / Redis ZSet），避免轮询空扫。
     *
     * ⚠️ 改造期间临时停用（原因同上）：本方法扫的是老订单表 orders。
     * 阶段 3 会按新模型重写：超时判定改为 status=0（待确认）且 expire_time < now，
     * 超时时长由下单时写入的 expire_time 决定（捡漏 10 分钟 / 普通 30 分钟）。
     */
    //@Scheduled(cron = "0 * * * * ?")
    public void handleTimeoutOrders() {
        log.info("定时任务：扫描超时未支付订单，{}", new Date());

        //只取一次当前时间，保证取消时间与超时判定基准一致
        LocalDateTime now = LocalDateTime.now();

        Integer count = orderMapper.cancelExpiredOrders(
                MessageConstant.TIMEOUT_CANCEL_ORDER,
                now,
                now.minusMinutes(15),
                Orders.CANCELLED);

        if (count != null && count > 0) {
            log.info("定时任务：自动取消 {} 笔超时未支付订单", count);
        }
    }
}
