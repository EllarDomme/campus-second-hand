package com.campus.controller.admin;

import com.campus.exception.BaseException;
import com.campus.result.Result;
import com.campus.service.ReportService;
import com.campus.vo.AdminDashboardVO;
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
 * 管理端-数据看板接口（S4-2）
 */
@RestController("adminReportController")
@RequestMapping("/admin/dashboard")
@Api(tags = "管理端-数据看板接口")
@Slf4j
public class ReportController {

    @Autowired
    private ReportService reportService;

    /** 低信用分的判定阈值：低于 80 分视为"高风险用户" */
    private static final int LOW_CREDIT_THRESHOLD = 80;

    /**
     * 看板总览
     * 用法：GET /admin/dashboard?lowCredit=80
     */
    @GetMapping
    @ApiOperation("数据看板总览")
    public Result<AdminDashboardVO> dashboard(@RequestParam(required = false, defaultValue = "80") Integer lowCredit) {
        return Result.success(reportService.dashboard(lowCredit));
    }

    // ==========================================================================
    // 热门品类榜（S6-2）
    //
    // ⚠️ 这里原来是 getHotProducts(days, limit) 的空壳 —— 那是"一级平铺"的旧契约。
    //    和需求方聊过之后改成了【两级结构 + 不按时间窗】，原因：
    //      ① 二手商品唯一，一个商品最多成交一次 → "成交单数"恒为 0/1，没有区分度
    //      ② 所以改成"分类聚合（能聚合）+ 分类内单品按热度分排序（卖出/耗时/收藏）"
    // ==========================================================================

    /**
     * 热门品类榜
     * 用法：GET /admin/dashboard/hotCategories?topCategories=5&topProducts=3
     */
    @GetMapping("/hotCategories")
    @ApiOperation("热门品类榜（两级：分类 → 该类 Top 商品）")
    public Result<List<HotCategoryVO>> hotCategories(
            @RequestParam(required = false, defaultValue = "5") Integer topCategories,
            @RequestParam(required = false, defaultValue = "3") Integer topProducts) {

        // 参数校验放在后端做 —— 前端传什么都不可信
        if (topCategories == null || topCategories < 1 || topCategories > 20) {
            throw new BaseException("分类数只能是 1~20");
        }
        if (topProducts == null || topProducts < 1 || topProducts > 10) {
            throw new BaseException("每个分类的商品数只能是 1~10");
        }
        return Result.success(reportService.hotCategories(topCategories, topProducts));
    }

}
