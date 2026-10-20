package com.campus.service;

import com.campus.vo.AdminDashboardVO;
import com.campus.vo.HotCategoryVO;

import java.util.List;

/**
 * 管理端-数据看板（S4-2 / S6-2）
 */
public interface ReportService {

    /**
     * 看板总览
     *
     * @param lowCreditThreshold 低信用分的判定阈值（比如 80）
     */
    AdminDashboardVO dashboard(int lowCreditThreshold);

    /**
     * 【S6-2】热门品类榜（两级：分类 → 该类 Top 商品）
     *
     * @param topCategories 取前几个分类
     * @param topProducts   每个分类取几件商品
     */
    List<HotCategoryVO> hotCategories(int topCategories, int topProducts);

}
