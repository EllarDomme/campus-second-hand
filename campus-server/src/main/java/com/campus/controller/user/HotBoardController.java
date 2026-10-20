package com.campus.controller.user;

import com.campus.exception.BaseException;
import com.campus.result.Result;
import com.campus.service.ReportService;
import com.campus.vo.HotCategoryVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端-热门品类榜（S6-2）
 *
 * 为什么 C 端也要：卖家需要知道"最近什么好卖"，才好决定把闲置的什么挂出来；
 * 买家也能看到"同学们都在买什么"，是个天然的信任信号。
 *
 * 复用 ReportService.hotCategories —— 同一个统计口径，
 * 只是 C 端默认取得更少（3 个分类 × 2 件商品），避免首页太挤。
 */
@RestController("userHotBoardController")
@RequestMapping("/user/hot")
@Api(tags = "用户端-热门品类榜")
@Slf4j
public class HotBoardController {

    @Autowired
    private ReportService reportService;

    /**
     * 热门品类榜（C 端默认只取前 3 个分类、每类 2 件商品）
     * 用法：GET /user/hot/categories?topCategories=3&topProducts=2
     */
    @GetMapping("/categories")
    @ApiOperation("热门品类榜（用户端）")
    public Result<List<HotCategoryVO>> categories(
            @RequestParam(required = false, defaultValue = "3") Integer topCategories,
            @RequestParam(required = false, defaultValue = "2") Integer topProducts) {

        if (topCategories == null || topCategories < 1 || topCategories > 20) {
            throw new BaseException("分类数只能是 1~20");
        }
        if (topProducts == null || topProducts < 1 || topProducts > 10) {
            throw new BaseException("每个分类的商品数只能是 1~10");
        }
        return Result.success(reportService.hotCategories(topCategories, topProducts));
    }

}
