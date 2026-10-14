package com.sky.task;

import com.sky.entity.TradeOrder;
import com.sky.mapper.TradeOrderMapper;
import com.sky.service.TradeOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private TradeOrderMapper tradeOrderMapper;
    @Autowired
    private TradeOrderService tradeOrderService;

    //每分钟扫一次超时订单
    @Scheduled(cron = "0 * * * * ?")
    public void checkTimeoutOrder() {
        LocalDateTime now = LocalDateTime.now();

        //找出超时的订单并且处理
        List<TradeOrder> expiredList = tradeOrderMapper.listExpired(now);

        if (expiredList == null || expiredList.isEmpty()) {
            //没有超时订单那就静默返回（否则每分钟打一条日志会刷屏）
            return;
        }
        log.info("超时订单数量：{}", expiredList.size());

        //开始逐个处理超时订单
        for(TradeOrder order : expiredList){
            try{
                //调用服务层方法
                tradeOrderService.timeoutClose(order.getId());
                log.info("超时订单{}处理成功", order.getId());
            }catch (Exception e){
                log.error("超时订单{}处理失败", order.getId(), e);
            }
        }
    }

}
