package com.campus.mapper;

import com.campus.vo.HotCategoryVO;
import com.campus.vo.HotProductVO;
import com.campus.vo.DashboardProductVO;
import com.campus.vo.DashboardTradeVO;
import com.campus.vo.DashboardUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

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

    // ==========================================================================
    // 热门品类榜（S6-2）—— 【两段 SQL 由你写，都在 ReportMapper.xml 里】
    // ==========================================================================

    /**
     * 【你的任务 A-1】分类聚合：按"该分类下已售出/在售的商品数"统计
     *
     * @param topCategories 取前几个分类
     *
     * 要返回的字段（别名要和 HotCategoryVO 对上）：
     *   categoryId    c.id
     *   categoryName  c.category_name
     *   soldCount     该分类下 status = 2（已售罄=卖掉了）的商品数
     *   onSaleCount   该分类下 status = 0（在售）的商品数
     *
     * 过滤：p.audit_status = 1 且 p.status in (0,1,2)
     *      （下架的、被驳回的不能进榜）
     * 分组：group by c.id, c.category_name
     * 排序：soldCount desc, onSaleCount desc, c.id asc
     * 限量：limit #{topCategories}
     */
    List<HotCategoryVO> listCategoryStat(@Param("topCategories") int topCategories);

    /**
     * 【你的任务 A-2】商品明细：把上面选中的这几个分类下的商品，全查出来
     *
     * @param categoryIds 上一步选中的分类 id 列表（用 in 查询，别按分类循环查 = N+1）
     *
     * 要返回的字段：
     *   productId, title, price, status（直接取）
     *   viewCount        p.view_count
     *   favoriteCount    子查询：select count(*) from favorite where product_id = p.id
     *   sold             1 if p.status = 2 else 0
     *   elapsedDays      ⭐ 本需求的核心难点，见下面的说明
     *
     * ⭐ elapsedDays 怎么算（用 DATEDIFF(结束时刻, 起算时刻)）：
     *     结束时刻：已成交 → 该商品那笔已完成订单的 finish_time
     *              未成交 → 当前时刻 NOW()
     *     起算时刻：捡漏商品（is_flash_sale = 1 且 flash_start_time 不为空）
     *              → flash_start_time（开抢时刻，不是发布时刻！）
     *              普通商品 → online_time
     *
     * 过滤：和 A-1 一样（audit_status = 1 且 status in (0,1,2)），
     *      再加上 category_id in (...)
     */
    List<HotProductVO> listProductStatByCategories(@Param("categoryIds") List<Long> categoryIds);

}
