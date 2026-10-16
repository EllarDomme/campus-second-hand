package com.sky.controller.user;

import com.sky.result.Result;
import com.sky.service.DeliveryService;
import com.sky.vo.DeliveryOrderVO;
import com.sky.vo.RunnerIncomeVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端-校园跑腿接口
 *
 * 接口分两类：
 *   跑腿员用的：大厅、抢单、取货、送达、我的跑腿单、收入统计
 *   买卖双方用的：跑腿单详情、取消
 * 权限校验都在 Service 里做（谁有权做这件事，不靠接口路径区分）
 */
@RestController("userDeliveryController")
@RequestMapping("/user/delivery")
@Api(tags = "C端-校园跑腿接口")
@Slf4j
public class DeliveryController {

    @Autowired
    private DeliveryService deliveryService;

    @GetMapping("/available")
    @ApiOperation("待接单大厅（跑腿员视角）")
    public Result<List<DeliveryOrderVO>> available() {
        return Result.success(deliveryService.listAvailable());
    }

    @PutMapping("/accept/{id}")
    @ApiOperation("抢单")
    public Result accept(@PathVariable Long id) {
        log.info("跑腿员抢单：deliveryOrderId={}", id);
        deliveryService.accept(id);
        return Result.success();
    }

    @PutMapping("/pickup/{id}")
    @ApiOperation("确认取货（已接单 → 取货中）")
    public Result pickup(@PathVariable Long id) {
        deliveryService.pickup(id);
        return Result.success();
    }

    @PutMapping("/finish/{id}")
    @ApiOperation("确认送达（取货中 → 已送达）")
    public Result finish(@PathVariable Long id) {
        deliveryService.finish(id);
        return Result.success();
    }

    @PutMapping("/cancel/{id}")
    @ApiOperation("取消跑腿单（买家/卖家）")
    public Result cancel(@PathVariable Long id) {
        deliveryService.cancel(id);
        return Result.success();
    }

    @GetMapping("/my")
    @ApiOperation("我的跑腿单（跑腿员视角）")
    public Result<List<DeliveryOrderVO>> my() {
        return Result.success(deliveryService.listMyOrders());
    }

    @GetMapping("/income")
    @ApiOperation("我的跑腿收入统计（跑腿员视角）")
    public Result<RunnerIncomeVO> income() {
        return Result.success(deliveryService.myIncome());
    }

    /**
     * 按主交易订单查跑腿单（买家/卖家视角）
     * 用法：GET /user/delivery/byTradeOrder/20
     */
    @GetMapping("/byTradeOrder/{tradeOrderId}")
    @ApiOperation("按主交易订单查跑腿单")
    public Result<DeliveryOrderVO> byTradeOrder(@PathVariable Long tradeOrderId) {
        return Result.success(deliveryService.getByTradeOrderId(tradeOrderId));
    }

    /**
     * 跑腿单详情
     * 用法：GET /user/delivery/1
     * 注意：固定路径（available/my/income）会优先匹配，不会被这个吃掉
     */
    @GetMapping("/{id}")
    @ApiOperation("跑腿单详情")
    public Result<DeliveryOrderVO> detail(@PathVariable Long id) {
        return Result.success(deliveryService.getDetail(id));
    }

}
