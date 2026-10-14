package com.sky.controller.user;

import com.sky.entity.FlashSaleRecord;
import com.sky.result.Result;
import com.sky.service.FlashSaleService;
import com.sky.vo.FlashSaleVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端-限时捡漏抢购接口
 */
@RestController("userFlashSaleController")
@RequestMapping("/user/flashSale")
@Api(tags = "C端-限时捡漏接口")
@Slf4j
public class FlashSaleController {

    @Autowired
    private FlashSaleService flashSaleService;

    @GetMapping("/list")
    @ApiOperation("捡漏专区（带抢购状态）")
    public Result<List<FlashSaleVO>> list() {
        return Result.success(flashSaleService.listFlashSale());
    }

    /**
     * 抢购
     * 用法：POST /user/flashSale/3
     */
    @PostMapping("/{productId}")
    @ApiOperation("抢购一件捡漏商品")
    public Result grab(@PathVariable Long productId) {
        log.info("抢购：productId={}", productId);
        flashSaleService.grab(productId);
        return Result.success();
    }

    @GetMapping("/record")
    @ApiOperation("我的抢购记录（抢到过的）")
    public Result<List<FlashSaleRecord>> record() {
        return Result.success(flashSaleService.listMyRecord());
    }

}
