package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.utils.FlashSaleStockUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-捡漏活动管理（库存维护）
 *
 * 为什么需要"重置库存"这个能力：
 *   Redis 里的库存 key 是独立于数据库的。抢购结束后它是 0；
 *   如果商品被取消交易、重新上架，或者要重新开一场捡漏活动，
 *   就需要把库存清掉，让下次抢购按新的初始库存重建。
 *
 * ⚠️ 这两个接口受 sky.debug.enabled 开关保护（和 testLogin 一样）：
 *   它们能直接改 Redis 状态，生产环境必须关闭。
 *   正规做法是做成"管理端活动管理"页面的一部分，这里先用调试接口满足回归测试和运维。
 */
@RestController("adminFlashSaleController")
@RequestMapping("/admin/flashSale")
@Api(tags = "管理端-捡漏活动管理")
@Slf4j
public class FlashSaleController {

    @Autowired
    private FlashSaleStockUtil flashSaleStockUtil;

    /** 调试开关：默认关闭，仅 application-dev.yml 显式开启时可用 */
    @Value("${sky.debug.enabled:false}")
    private boolean debugEnabled;

    /**
     * 重置某件捡漏商品的 Redis 库存（清掉 key，下次抢购按初始库存重建）
     * 用途：活动结束 / 商品重新上架 / 回归测试准备数据
     */
    @PostMapping("/resetStock/{productId}")
    @ApiOperation("【调试】重置捡漏商品的 Redis 库存")
    public Result resetStock(@PathVariable Long productId) {
        if (!debugEnabled) {
            return Result.error("调试接口未开启");
        }
        flashSaleStockUtil.clear(productId);
        log.info("已重置捡漏库存：productId={}", productId);
        return Result.success();
    }

    /**
     * 查某件捡漏商品当前的 Redis 库存（排查问题 / 运维查看）
     */
    @GetMapping("/stock/{productId}")
    @ApiOperation("查捡漏商品当前 Redis 库存")
    public Result<String> stock(@PathVariable Long productId) {
        if (!debugEnabled) {
            return Result.error("调试接口未开启");
        }
        return Result.success(flashSaleStockUtil.getStock(productId));
    }

}
