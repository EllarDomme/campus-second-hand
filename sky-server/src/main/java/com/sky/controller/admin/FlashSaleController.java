package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.FlashSaleService;
import com.sky.utils.FlashSaleStockUtil;
import com.sky.vo.FlashSaleAdminVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端-捡漏活动管理（S4-3）
 *
 * 原来这里只有两个【调试接口】（受 sky.debug.enabled 保护、只够回归测试用）。
 * 现在升级成正式的运营接口：
 *   · 列表：一眼看清每场活动的库存、参与人数、抢到情况
 *   · 重置库存：活动结束/商品重新上架时用
 *   · 结束活动：把捡漏商品变回普通商品
 *
 * 注意：这些接口走管理端令牌（请求头 token），由 JwtTokenAdminInterceptor 保护。
 */
@RestController("adminFlashSaleController")
@RequestMapping("/admin/flashSale")
@Api(tags = "管理端-捡漏活动管理")
@Slf4j
public class FlashSaleController {

    @Autowired
    private FlashSaleService flashSaleService;

    /** 只用于"查单个商品库存"这个轻量读接口，避免为它再套一层 Service */
    @Autowired
    private FlashSaleStockUtil flashSaleStockUtil;

    @GetMapping("/list")
    @ApiOperation("捡漏活动列表（含库存/参与人数/抢到人数）")
    public Result<List<FlashSaleAdminVO>> list() {
        return Result.success(flashSaleService.listForAdmin());
    }

    /**
     * 重置某件捡漏商品的 Redis 库存（清掉 key，下次抢购按初始库存重建）
     * 用法：POST /admin/flashSale/resetStock/3
     */
    @PostMapping("/resetStock/{productId}")
    @ApiOperation("重置捡漏商品的 Redis 库存")
    public Result resetStock(@PathVariable Long productId) {
        log.info("管理端重置捡漏库存：productId={}", productId);
        flashSaleService.resetStock(productId);
        return Result.success();
    }

    /**
     * 提前结束捡漏活动（商品变回普通商品 + 清库存）
     * 用法：PUT /admin/flashSale/end/3
     */
    @PutMapping("/end/{productId}")
    @ApiOperation("提前结束捡漏活动")
    public Result end(@PathVariable Long productId) {
        log.warn("管理端结束捡漏活动：productId={}", productId);
        flashSaleService.endActivity(productId);
        return Result.success();
    }

    /**
     * 查某件捡漏商品当前的 Redis 库存（运维排查用）
     */
    @GetMapping("/stock/{productId}")
    @ApiOperation("查捡漏商品当前 Redis 库存")
    public Result<String> stock(@PathVariable Long productId) {
        return Result.success(flashSaleStockUtil.getStock(productId));
    }

}
