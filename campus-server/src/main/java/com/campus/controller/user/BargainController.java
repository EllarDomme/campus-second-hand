package com.campus.controller.user;

import com.campus.dto.BargainCounterDTO;
import com.campus.dto.BargainPageQueryDTO;
import com.campus.dto.BargainSubmitDTO;
import com.campus.result.PageResult;
import com.campus.result.Result;
import com.campus.service.BargainService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端-议价接口
 *
 * 买卖双方共用这几个接口 —— 因为"接受/拒绝"这个动作本身是一样的，
 * 区别只在【谁有权做】和【当前处于哪个状态】，这个判断放在 Service 里。
 *   状态 1（买家出价）→ 卖家来接受 / 拒绝 / 还价
 *   状态 2（卖家还价）→ 买家来接受 / 拒绝
 */
@RestController("userBargainController")
@RequestMapping("/user/bargain")
@Api(tags = "C端-议价接口")
@Slf4j
public class BargainController {

    @Autowired
    private BargainService bargainService;

    /**
     * 买家发起议价
     * body: {"productId": 1, "buyerOffer": 75.00}
     */
    @PostMapping
    @ApiOperation("买家发起议价")
    public Result submit(@RequestBody BargainSubmitDTO submitDTO) {
        log.info("买家发起议价：{}", submitDTO);
        bargainService.submit(submitDTO);
        return Result.success();
    }

    /**
     * 我的议价列表
     * GET /user/bargain/list?page=1&pageSize=10&view=seller
     */
    @GetMapping("/list")
    @ApiOperation("我的议价列表（view=buyer 我发起的 / view=seller 别人对我商品出的价）")
    public Result<PageResult> list(BargainPageQueryDTO query) {
        log.info("查询我的议价：{}", query);
        return Result.success(bargainService.pageQuery(query));
    }

    /**
     * 接受报价（卖家接受出价 / 买家接受还价）→ 成交并生成订单
     */
    @PutMapping("/accept/{id}")
    @ApiOperation("接受报价 → 议价成交并生成订单")
    public Result accept(@PathVariable Long id) {
        log.info("接受报价：{}", id);
        bargainService.accept(id);
        return Result.success();
    }

    /**
     * 拒绝报价（谁能拒绝取决于当前状态）
     */
    @PutMapping("/reject/{id}")
    @ApiOperation("拒绝报价")
    public Result reject(@PathVariable Long id) {
        log.info("拒绝报价：{}", id);
        bargainService.reject(id);
        return Result.success();
    }

    /**
     * 卖家还价
     * body: {"id": 1, "counterOffer": 85.00}
     */
    @PutMapping("/counter")
    @ApiOperation("卖家还价")
    public Result counter(@RequestBody BargainCounterDTO counterDTO) {
        log.info("卖家还价：{}", counterDTO);
        bargainService.counter(counterDTO);
        return Result.success();
    }

}
