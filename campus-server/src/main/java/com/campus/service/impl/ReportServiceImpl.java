package com.campus.service.impl;

import com.campus.mapper.ReportMapper;
import com.campus.service.ReportService;
import com.campus.vo.AdminDashboardVO;
import com.campus.vo.HotCategoryVO;
import com.campus.vo.HotProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端-数据看板实现（S4-2 / S6-2）
 *
 * 这里只是"把查询结果拼成对象"，逻辑很薄 ——
 * 真正有技术含量的是 ReportMapper.xml 里的聚合 SQL，以及下面的 calcHotScore。
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

    // ==========================================================================
    // 热门品类榜（S6-2）
    //   下面这段"拼装"已经写好了，你只需要实现最底部的 calcHotScore（填空 A1~A3）
    // ==========================================================================

    @Override
    public List<HotCategoryVO> hotCategories(int topCategories, int topProducts) {
        // ① 分类聚合（SQL 由你写在 ReportMapper.xml 的 listCategoryStat）
        List<HotCategoryVO> cats = reportMapper.listCategoryStat(topCategories);
        if (cats == null || cats.isEmpty()) {
            return new ArrayList<>();
        }

        // ② 分类热度分：在 Java 里算而不是 SQL —— 改公式不用动 SQL，也好单测
        for (HotCategoryVO c : cats) {
            int sold = c.getSoldCount() == null ? 0 : c.getSoldCount();
            int onSale = c.getOnSaleCount() == null ? 0 : c.getOnSaleCount();
            c.setCategoryScore(sold * 10 + onSale);
        }

        // ③ 一次把这几类的商品全查出来
        //    注意：不要"遍历分类、每个分类查一次"—— 那是 N+1 查询，分类一多就慢
        List<Long> categoryIds = cats.stream()
                .map(HotCategoryVO::getCategoryId)
                .collect(Collectors.toList());
        List<HotProductVO> all = reportMapper.listProductStatByCategories(categoryIds);
        if (all == null) {
            all = new ArrayList<>();
        }

        // ④ 打分
        for (HotProductVO p : all) {
            p.setHotScore(calcHotScore(p.getSold(), p.getElapsedDays(), p.getFavoriteCount()));
        }

        // ⑤ 按分类分组（一个商品只会属于一个分类）
        Map<Long, List<HotProductVO>> byCategory = new HashMap<>();
        for (HotProductVO p : all) {
            byCategory.computeIfAbsent(p.getCategoryId(), k -> new ArrayList<>()).add(p);
        }

        // ⑥ 每个分类内排序 + 截取 Top N
        //    排序三键：热度分降序 → 浏览降序 → id 升序
        //    ⚠️ 第三个键是"稳定排序"的保证：不然热度/浏览都相同时，每次查出来的顺序可能不同
        Comparator<HotProductVO> order = Comparator
                .comparingDouble((HotProductVO p) -> p.getHotScore() == null ? 0d : p.getHotScore()).reversed()
                .thenComparing(p -> p.getViewCount() == null ? 0 : p.getViewCount(), Comparator.reverseOrder())
                .thenComparing(p -> p.getProductId() == null ? Long.MAX_VALUE : p.getProductId());

        for (HotCategoryVO c : cats) {
            List<HotProductVO> list = byCategory.getOrDefault(c.getCategoryId(), new ArrayList<>());
            list.sort(order);
            c.setProducts(list.size() > topProducts ? new ArrayList<>(list.subList(0, topProducts)) : list);
        }

        // ⑦ 分类整体也按热度分降序（SQL 里已经排过，这里再兜一次，防止 SQL 写漏 order by）
        cats.sort(Comparator
                .comparingInt((HotCategoryVO c) -> c.getCategoryScore() == null ? 0 : c.getCategoryScore()).reversed()
                .thenComparing(c -> c.getCategoryId() == null ? Long.MAX_VALUE : c.getCategoryId()));

        return cats;
    }

/**
     * 单品热度分（0 ~ 100）
     *
     * 为什么是 static + public：
     *   · static —— 它是纯函数，不依赖任何实例状态，测试可以直接调用
     *   · public —— 让 HotScoreTest 能测它（放在同包也行，但 public 更省事）
     * 设计意图：卖出最重要（50 分），其次是"多快卖掉"（30 分），收藏只是参考（20 分）。
     * 这样算出来，卖过的商品最低 50 分，没卖过的最高不到 50 分 —— 自然形成两个梯队。
     */
    public static double calcHotScore(Integer sold, Integer elapsedDays, Integer favoriteCount) {
        // ================= 你的部分：填空 A1 ~ A3 =================
        //
        // 【A1】卖出分：sold == 1 → 50 分，否则 0 分
        //   提示：sold 可能是 null（SQL 没查出来），要当 0 处理
        // sold == 1 → 50 分，否则 0 分（默认值就是 0，不需要再写一个 if 去赋 0）
        // ⚠️ 用 Integer.valueOf(1).equals(sold) 而不是 sold == 1：
        //    后者会触发自动拆箱，sold 为 null 时直接 NPE —— 而 null 恰恰是可能出现的。
        int soldScore = Integer.valueOf(1).equals(sold) ? 50 : 0;

        // 【A2】耗时分（0 ~ 100）：
        //   · elapsedDays 为 null 表示"不知道挂了多久"（老数据没有 online_time）
        //     → 保守给 0 分，不能因为"不知道"就奖励它，否则脏数据会刷上榜
        //   · 归一化：(1 - min(elapsedDays, 90) / 90.0) * 100
        //     → 0 天 = 100 分，90 天及以上 = 0 分
        //   · ⚠️ 90 是封顶：不给它封顶的话，挂了三年的商品会把分数拉到很离谱的值；
        //         封顶后最低就是 0 分（0 分的商品自然上不了榜）
        //   · ⚠️ 用 90.0 而不是 90（整数除法会把小数丢掉）
        double timeScore = 0;
        if(elapsedDays == null){
            timeScore = 0;
        }else{
            timeScore = (1 - Math.min(elapsedDays,90)/90.0) * 100;
        }


        // 【A3】收藏分（0 ~ 100）：
        //   · favoriteCount 可能为 null → 当 0
        //   · 封顶 20 个：min(favoriteCount, 20) / 20.0 * 100
        //   · ⚠️ 为什么要封顶：收藏数是可刷的，不封顶的话"自己点 100 次"就能刷上榜
        double favoriteScore;
        if(favoriteCount == null){
            favoriteScore = 0;
        }else{
        favoriteScore = (Math.min(favoriteCount,20) / 20.0) * 100;
        }

        // 最后返回：卖出分 + 耗时分 × 0.3 + 收藏分 × 0.2
        //   （也就是 50×sold + 30×timeScore/100 + 20×favScore/100）
        double score = soldScore + timeScore * 0.3 + favoriteScore * 0.2;
        // 自测四个例子（写完自己算一遍）：
        //   昨天挂、今天卖、收藏 5  → 50 + 29.7 + 5   ≈ 84.7
        //   卖了但花了 60 天         → 50 + 10   + ?   ≈ 60 出头
        //   挂了 30 天没卖、收藏 3   → 0  + 20   + 3   ≈ 23
        //   挂了 200 天没卖、收藏 0  → 0  + 0    + 0   = 0
        //
        // =======================================================
        return score;
    }

}
