package com.sky.mapper;

import com.sky.vo.DashboardProductVO;
import com.sky.vo.DashboardTradeVO;
import com.sky.vo.DashboardUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 数据看板统计（S4-2）
 *
 * ==========================================================================
 * 【你的任务】把 report 的 6 段 SQL 写完（都在 ReportMapper.xml 里）：
 *   三个待办 count（简单）+ 三个聚合查询（有技术含量）
 * ==========================================================================
 */
@Mapper
public interface ReportMapper {

    // ---------- 待办（这三个很简单，直接返回 int）----------

    /** 待审核商品数：product.audit_status = 0 */
    @Select("select count(*) from product where audit_status = 0")
    int countPendingAuditProducts();

    /**
     * 待处理投诉数：complaint.handle_status = 0
     * 【待办 1】把这条 SQL 写完（提示：一行就够）
     */
    @Select("select count(*) from complaint where handle_status = 0")
    int countPendingComplaints();

    /**
     * 待接单跑腿数：delivery_order.status = 0
     * 【待办 2】把这条 SQL 写完
     */
    @Select("select count(*) from delivery_order where status = 0")
    int countWaitingDeliveries();

    // ---------- 三个聚合查询（SQL 在 ReportMapper.xml）----------

    /**
     * 【待办 3】交易概况
     *
     * 要算 5 个数字，**一条 SQL 搞定**（提示：用 sum(case when ... then ... else ... end)）：
     *   todayOrders   今日成交单数     order.status = 2（已完成）且 finish_time >= todayStart
     *   todayAmount   今日成交额       sum(real_price)
     *   monthOrders   本月成交单数     finish_time >= monthStart
     *   monthAmount   本月成交额
     *   totalAmount   累计成交额       sum(real_price) where status = 2
     *
     * ⚠️ 三个坑：
     *   ① 一条数据都没匹配上时 sum() 返回 NULL → 用 ifnull(..., 0) 包起来
     *   ② 金额要处理成 0 而不是 null（前端做加法会炸）
     *   ③ 时间过滤用【已完成时间 finish_time】（不是 create_time ——
     *      一个月前下的单今天才收货，这笔成交应该算今天）
     */
    DashboardTradeVO tradeSummary(@Param("todayStart") LocalDateTime todayStart,
                                  @Param("monthStart") LocalDateTime monthStart);

    /**
     * 【待办 4】用户概况
     *
     *   totalUsers      用户总数
     *   totalRunners    role = 1 的人数
     *   bannedUsers     status = 1 的人数
     *   lowCreditUsers  credit_score < #{lowCredit} 的人数
     */
    DashboardUserVO userSummary(@Param("lowCredit") int lowCredit);

    /**
     * 【待办 5】商品概况
     *
     *   onSaleProducts       status = 0
     *   inTradeProducts      status = 1
     *   soldOutProducts      status = 2
     *   pendingAuditProducts audit_status = 0
     * （提示：和交易概况一样，用 sum(case when ...) 一条 SQL 算完）
     */
    DashboardProductVO productSummary();

}
