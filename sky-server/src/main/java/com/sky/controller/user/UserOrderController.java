package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/order")
@Slf4j
@Api(tags = "C端订单接口")
public class UserOrderController {
    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单，订单信息：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submit(ordersSubmitDTO);
        return Result.success(orderSubmitVO);

    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 查询用户订单历史
     * @param ordersPageQueryDTO
     * @return
     */
    @GetMapping("/historyOrders")
    @ApiOperation("查询用户订单历史")
    public Result<PageResult> historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("查询用户订单历史，查询参数：{}", ordersPageQueryDTO);
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        PageResult pageResult = orderService.historyOrders(ordersPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO>  orderDetail(@PathVariable Long id) {
        log.info("查询订单详情，订单id：{}", id);
        OrderVO orderVo = orderService.orderDetail(id);
        return Result.success(orderVo);
    }

    /**
     * 取消订单
     * @param id
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable Long id){
        log.info("取消订单，订单id：{}", id);
        orderService.cancel(id);
        return Result.success();
    }

    @PostMapping("/repetition/{id}")
    @ApiOperation("再次下单")
    public Result repetition(@PathVariable Long id){
        log.info("再次下单，订单id：{}", id);
        Orders orders = new Orders();
        orders.setId(id);
        orders.setUserId(BaseContext.getCurrentId());
        orderService.repetition(orders);
        return Result.success();
    }

    @GetMapping("/reminder/{id}")
    @ApiOperation("用户催单")
    public Result reminder(@PathVariable Long id) {
        log.info("用户催单：{}", id);
        orderService.reminder(id);
        return Result.success();

    }



}
