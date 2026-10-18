package com.campus.controller.admin;

import com.campus.result.Result;
import com.campus.service.ReportService;
import com.campus.vo.AdminDashboardVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

}
