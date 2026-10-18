package com.campus.controller.admin;

import com.campus.dto.ReviewPageQueryDTO;
import com.campus.result.PageResult;
import com.campus.result.Result;
import com.campus.service.ReviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-评价管理（S4-5）
 *
 * 需求里的"内容治理"：恶意差评、广告评价要能被下架。
 */
@RestController("adminReviewController")
@RequestMapping("/admin/review")
@Api(tags = "管理端-评价管理")
@Slf4j
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    /**
     * 评价分页列表
     * 用法：GET /admin/review/page?star=1&toUserId=2&page=1&pageSize=10
     */
    @GetMapping("/page")
    @ApiOperation("评价分页列表（可按商品/被评价人/星级筛选）")
    public Result<PageResult> page(ReviewPageQueryDTO query) {
        return Result.success(reviewService.pageForAdmin(query));
    }

    /**
     * 删除违规评价
     * 用法：DELETE /admin/review/5?reason=恶意差评
     */
    @DeleteMapping("/{id}")
    @ApiOperation("删除违规评价（会自动回退信用分，需填写原因）")
    public Result delete(@PathVariable Long id, @RequestParam String reason) {
        log.warn("管理员删除评价：id={}, reason={}", id, reason);
        reviewService.deleteByAdmin(id, reason);
        return Result.success();
    }

}
