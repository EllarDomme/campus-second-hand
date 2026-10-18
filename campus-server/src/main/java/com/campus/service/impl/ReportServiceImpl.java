package com.campus.service.impl;

import com.campus.mapper.ReportMapper;
import com.campus.service.ReportService;
import com.campus.vo.AdminDashboardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端-数据看板实现（S4-2）
 *
 * 这里只是"把六个查询的结果拼成一个对象"，逻辑很薄 ——
 * 真正有技术含量的是 ReportMapper.xml 里的三段聚合 SQL。
 */
@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private ReportMapper reportMapper;

    @Override
    public AdminDashboardVO dashboard(int lowCreditThreshold) {
        // 时间边界：今天 00:00 与本月 1 号 00:00
        // 放在 Java 里算而不是 SQL 里写 CURDATE()，好处是【时区/口径由应用统一控制】，
        // 也便于测试时直接传任意时间做验证。
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return AdminDashboardVO.builder()
                // 待办
                .pendingAuditProducts(reportMapper.countPendingAuditProducts())
                .pendingComplaints(reportMapper.countPendingComplaints())
                .waitingDeliveries(reportMapper.countWaitingDeliveries())
                // 三组概况
                .trade(reportMapper.tradeSummary(todayStart, monthStart))
                .user(reportMapper.userSummary(lowCreditThreshold))
                .product(reportMapper.productSummary())
                .build();
    }

}
