package com.campus.service;

import com.campus.vo.AdminDashboardVO;

/**
 * 管理端-数据看板（S4-2）
 */
public interface ReportService {

    /**
     * 看板总览
     *
     * @param lowCreditThreshold 低信用分的判定阈值（比如 80）
     */
    AdminDashboardVO dashboard(int lowCreditThreshold);

}
