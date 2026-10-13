package com.sky.controller.user;

import com.sky.dto.TradeOrderPageQueryDTO;
import com.sky.dto.TradeOrderRejectionDTO;
import com.sky.dto.TradeOrderSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.TradeOrderService;
import com.sky.vo.TradeOrderPayVO;
import com.sky.vo.TradeOrderSubmitVO;
import com.sky.vo.TradeOrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * C端-交易订单接口
 *
 * 路径沿用老的 /user/order（前端不用改），老的 UserOrderController 已删除。
 * 目前只实现"下单"，后续步骤会陆续补上：支付、我的订单、详情、取消、确认收货。
 */
@RestController("userTradeOrderController")
@RequestMapping("/user/order")
@Api(tags = "C端-交易订单接口")
@Slf4j
public class TradeOrderController {

    @Autowired
    private TradeOrderService tradeOrderService;

    /**
     * 买家下单
     *
     * 请求体示例：
     * {
     *   "productId": 1,
     *   "dealType": 0,
     *   "pickupPlace": "3号宿舍楼下",
     *   "remark": "晚上7点后方便"
     * }
     */
    @PostMapping("/submit")
    @ApiOperation("买家下单")
    public Result<TradeOrderSubmitVO> submit(@RequestBody TradeOrderSubmitDTO submitDTO) {
        log.info("买家下单：{}", submitDTO);
        return Result.success(tradeOrderService.submit(submitDTO));
    }

    /**
     * 发起支付（模拟通道）
     *
     * 调用示例：POST /user/order/pay?orderNo=202610110915435146
     * 返回 payNo 后，前端带着它去调 POST /notify/paySuccess?payNo=xxx 表示"支付成功"
     *（真实场景这里是拿去调微信支付的 SDK）
     */
    @PostMapping("/pay")
    @ApiOperation("发起支付（模拟通道）")
    public Result<TradeOrderPayVO> pay(@RequestParam String orderNo) {
        log.info("发起支付：{}", orderNo);
        return Result.success(tradeOrderService.pay(orderNo));
    }

    @GetMapping("/page")
    @ApiOperation("分页查询订单列表")
    public Result<PageResult> pageQuery(TradeOrderPageQueryDTO queryDTO) {
        log.info("分页查询订单列表：{}", queryDTO);
        return Result.success(tradeOrderService.pageQuery(queryDTO));
    }

    @GetMapping("/{id}")
    @ApiOperation("查看订单详情")
    public Result<TradeOrderVO> getDetail(@PathVariable Long id) {
        log.info("查看订单详情：{}", id);
        return Result.success(tradeOrderService.getDetail(id));
    }

    @PutMapping("/confirm/{id}")
    @ApiOperation("卖家确认订单")
    public Result confirm(@PathVariable Long id) {
        log.info("卖家确认订单：{}", id);
        tradeOrderService.confirm(id);
        return Result.success();
    }

    @PutMapping("/reject")
    @ApiOperation("卖家拒绝订单")
    public Result reject(@RequestBody TradeOrderRejectionDTO rejectionDTO) {
        log.info("卖家拒绝订单：{}", rejectionDTO);
        tradeOrderService.reject(rejectionDTO);
        return Result.success();
    }



}
