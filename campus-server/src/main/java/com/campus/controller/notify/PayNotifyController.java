package com.campus.controller.notify;

import com.campus.result.Result;
import com.campus.service.TradeOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付结果通知（模拟通道）
 *
 * 真实场景：微信支付平台主动 POST 到商户配置的 notifyUrl，请求体是加密的，
 *          需要「验签 + 解密 + 应答」，而且**同一条通知可能重复送达**。
 * 模拟场景：由前端（或测试脚本）直接调用本接口，带上 payNo 即可。
 *
 * 为什么保留这个类：幂等、状态流转、对账这些核心逻辑与真实通道完全一致。
 * 将来接真实通道，只需在最外层补上验签与解密，核心逻辑一行都不用改。
 */
@RestController
@RequestMapping("/notify")
@Api(tags = "支付回调（模拟通道）")
@Slf4j
public class PayNotifyController {

    @Autowired
    private TradeOrderService tradeOrderService;

    /**
     * 调试接口开关：支付回调能直接改订单状态，生产环境必须关闭。
     * 由 application-dev.yml 的 campus.debug.enabled 控制。
     */
    @Value("${campus.debug.enabled:false}")
    private boolean debugEnabled;

    /**
     * 模拟「支付成功」通知
     *
     * ⚠️ 幂等要求：同一个 payNo 调用 N 次，只能产生一次实际效果，并且**每次都返回成功**。
     * 为什么必须返回成功：真实支付平台收到非成功应答会不断重试通知，
     * 如果重复通知我们回"失败"，平台就会一直重发。
     */
    @PostMapping("/paySuccess")
    @ApiOperation("模拟支付成功通知（幂等：重复调用不会重复处理）")
    public Result paySuccess(@RequestParam String payNo) {
        if (!debugEnabled) {
            return Result.error("调试接口未开启");
        }
        log.info("收到模拟支付成功通知：payNo={}", payNo);
        tradeOrderService.handlePaySuccess(payNo);
        return Result.success();
    }
}
