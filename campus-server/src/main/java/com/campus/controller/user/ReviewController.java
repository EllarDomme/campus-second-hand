package com.campus.controller.user;

import com.campus.dto.ReviewSubmitDTO;
import com.campus.entity.Review;
import com.campus.result.Result;
import com.campus.service.ReviewService;
import com.campus.vo.ReviewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端-交易评价接口
 */
@RestController("userReviewController")
@RequestMapping("/user/review")
@Api(tags = "C端-交易评价接口")
@Slf4j
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @PostMapping
    @ApiOperation("提交评价（一单一评）")
    public Result submit(@RequestBody ReviewSubmitDTO submitDTO) {
        log.info("提交评价：orderId={}, star={}", submitDTO.getOrderId(), submitDTO.getStar());
        reviewService.submit(submitDTO);
        return Result.success();
    }

    @GetMapping("/product/{productId}")
    @ApiOperation("某商品的评价列表")
    public Result<List<ReviewVO>> byProduct(@PathVariable Long productId) {
        return Result.success(reviewService.listByProduct(productId));
    }

    @GetMapping("/user/{userId}")
    @ApiOperation("某人收到的评价列表")
    public Result<List<ReviewVO>> byUser(@PathVariable Long userId) {
        return Result.success(reviewService.listByToUser(userId));
    }

    @GetMapping("/my")
    @ApiOperation("我发出的评价")
    public Result<List<Review>> my() {
        return Result.success(reviewService.mySentReviews());
    }

}
