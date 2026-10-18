package com.campus.controller.admin;

import com.campus.dto.DeliveryPageQueryDTO;
import com.campus.result.PageResult;
import com.campus.result.Result;
import com.campus.service.DeliveryService;
import com.campus.vo.DeliveryOrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-跑腿单管理（S4-4）
 */
@RestController("adminDeliveryController")
@RequestMapping("/admin/delivery")
@Api(tags = "管理端-跑腿单管理")
@Slf4j
public class DeliveryController {

    @Autowired
    private DeliveryService deliveryService;

    /**
     * 跑腿单分页列表
     * 用法：GET /admin/delivery/page?status=1&runnerUserId=3&page=1&pageSize=10
     */
    @GetMapping("/page")
    @ApiOperation("跑腿单分页列表（可按状态/跑腿员/交易订单筛选）")
    public Result<PageResult> page(DeliveryPageQueryDTO query) {
        return Result.success(deliveryService.pageForAdmin(query));
    }

    /**
     * 强制取消跑腿单（跑腿员失联、纠纷处理等异常场景）
     * 用法：PUT /admin/delivery/cancel/5?reason=跑腿员失联
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("强制取消跑腿单（需填写原因）")
    public Result cancel(@PathVariable Long id, @RequestParam String reason) {
        log.warn("管理员强制取消跑腿单：id={}, reason={}", id, reason);
        deliveryService.cancelByAdmin(id, reason);
        return Result.success();
    }

    /**
     * 管理端查看跑腿单详情（管理员不受"仅买卖双方"限制）
     */
    @GetMapping("/{id}")
    @ApiOperation("跑腿单详情（管理员视角）")
    public Result<DeliveryOrderVO> detail(@PathVariable Long id) {
        return Result.success(deliveryService.getDetailForAdmin(id));
    }

}
