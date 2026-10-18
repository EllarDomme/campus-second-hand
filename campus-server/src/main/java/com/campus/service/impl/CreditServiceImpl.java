package com.campus.service.impl;

import com.campus.constant.CreditConstant;
import com.campus.context.BaseContext;
import com.campus.entity.CreditLog;
import com.campus.entity.User;
import com.campus.exception.BaseException;
import com.campus.mapper.CreditLogMapper;
import com.campus.mapper.ReviewMapper;
import com.campus.mapper.UserMapper;
import com.campus.service.CreditService;
import com.campus.vo.CreditVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 信用分服务实现
 *
 * ==========================================================================
 * 【你的任务】把 changeCredit 的【填空 1~2】写完（这是全模块最核心的一段逻辑）：
 *   填空 1 = 每日加分上限（加分才限，扣分不限）
 *   填空 2 = 分数下限保护（不能扣成负数）
 *
 *   已替你写好（不用改）：myCredit / myLogs / 原子更新总分 + 写流水 的骨架
 * ==========================================================================
 */
@Service
@Slf4j
public class CreditServiceImpl implements CreditService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CreditLogMapper creditLogMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    /**
     * 变更信用分（全项目唯一入口）
     *
     * 为什么必须"唯一入口"：如果允许别处直接 update user.credit_score，
     * 流水（credit_log）和总分就会对不上 —— 而"每日加分上限"和"扣分溯源"
     * 全都依赖流水，一旦对不上，规则就形同虚设。
     */
    @Transactional
    @Override
    public int changeCredit(Long userId, int changeScore, Integer reasonType, Long refId, String remark) {
        // 默认计入每日加分上限（正常场景：完成交易、收到好评）
        return changeCredit(userId, changeScore, reasonType, refId, remark, true);
    }

    /**
     * @param countTowardDailyCap 是否计入"每日加分上限"
     *        true  = 正常加分（完成交易 / 好评）
     *        false = 补偿性加分（撤销差评扣分、管理员纠正误判）
     *                —— 撤销处罚不是刷分行为，不该被防刷上限吞掉
     */
    @Transactional
    @Override
    public int changeCredit(Long userId, int changeScore, Integer reasonType, Long refId,
                            String remark, boolean countTowardDailyCap) {
        if (userId == null || changeScore == 0) {
            return 0;
        }
        String reason = CreditConstant.reasonName(reasonType);

        // ================= 你的部分：填空 1 ~ 2 =================
        //
        // 【填空 1】加分才受"每日上限"约束（扣分不设限！）
        //
        //   if (changeScore > 0) {
        //       LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        //       Integer todayAdded = creditLogMapper.sumAddedSince(userId, todayStart);
        //       int remain = CreditConstant.DAILY_ADD_LIMIT
        //                    - (todayAdded == null ? 0 : todayAdded);
        //
        //       if (remain <= 0) {
        //           log.info("今日加分已达上限，本次不加分：userId={}, 今日已加={}", userId, todayAdded);
        //           return 0;                       // ← 加满了，这次就不加了
        //       }
        //       if (changeScore > remain) {
        //           changeScore = remain;           // ← 只加剩余额度，超出的部分作废
        //       }
        //   }
        //   ⚠️ 注意 if 的范围：只有加分才走这段，扣分（负数）直接跳过。
        //      写成 if (changeScore != 0) 就错了 —— 那会让恶意用户
        //      "每天最多扣 10 分"，反而成了护身符。
        //
        //   ⚠️ 补充：还要判 countTowardDailyCap ——
        //      补偿性加分（撤销差评扣分）不受上限约束，否则"处罚撤销了但分数不还"。
        if (countTowardDailyCap && changeScore > 0) {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            Integer todayAdded = creditLogMapper.sumAddedSince(userId,todayStart);
            int remain = CreditConstant.DAILY_ADD_LIMIT - (todayAdded == null ? 0 : todayAdded);

            if(remain <= 0){
                log.info("今日加分已经达到上限，本次不加分：userId={},今日已加={}",userId,todayAdded);
                return 0;
            }

            //如果超过了剩余可以加的分数，那就只加剩余可以加的分数
            if(changeScore > remain){
                changeScore = remain;
            }
        }

        //
        // 【填空 2】分数下限保护 + 算出变更后的总分
        //
        //   User user = userMapper.getById(userId);
        //   if (user == null) {
        //       throw new BaseException("用户不存在");
        //   }
        //   int current = (user.getCreditScore() == null
        //                  ? CreditConstant.INIT_SCORE : user.getCreditScore());
        //
        //   if (current + changeScore < CreditConstant.MIN_SCORE) {
        //       changeScore = CreditConstant.MIN_SCORE - current;   // 最多扣到 0 分
        //   }
        //   if (changeScore == 0) {
        //       return 0;                       // 已经是 0 分，扣不动了
        //   }
        //   int scoreAfter = current + changeScore;     // ← 这个变量下面要用
        User user = userMapper.getById(userId);
        if(user == null){
            throw new BaseException("用户不存在");
        }

        int current = (user.getCreditScore() == null ? CreditConstant.INIT_SCORE : user.getCreditScore());

        if(current + changeScore < CreditConstant.MIN_SCORE){
            changeScore = CreditConstant.MIN_SCORE - current;
        }
        if(changeScore == 0){
            return 0; // 已经是0分了，已经扣不动了
        }


        int scoreAfter = current + changeScore;


        // =======================================================
        //
        // ---- 以下已写好：原子更新总分 + 写流水（等你的填空算好 changeScore 和 scoreAfter）----
         userMapper.addCreditScore(userId, changeScore);
         creditLogMapper.insert(CreditLog.builder()
                 .userId(userId)
                 .changeScore(changeScore)
                 .scoreAfter(scoreAfter)
                 .reasonType(reasonType)
                 .refId(refId)
                 .remark(remark == null ? reason : remark)
                 .createTime(LocalDateTime.now())
                 .updateTime(LocalDateTime.now())
                 .build());
         log.info("信用分变更：userId={}, {}{} 分 → {}（{}）",
                 userId, changeScore > 0 ? "+" : "", changeScore, scoreAfter, reason);
         return changeScore;
    }

    /** 我的信用分概览（已写好） */
    @Override
    public CreditVO myCredit() {
        Long me = BaseContext.getCurrentId();
        User user = userMapper.getById(me);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        Integer todayAdded = creditLogMapper.sumAddedSince(me, LocalDate.now().atStartOfDay());
        return CreditVO.builder()
                .creditScore(user.getCreditScore())
                .todayAdded(todayAdded == null ? 0 : todayAdded)
                .dailyAddLimit(CreditConstant.DAILY_ADD_LIMIT)
                .goodReviewCount(reviewMapper.countByStarAtLeast(me, CreditConstant.GOOD_REVIEW_STAR))
                .badReviewCount(reviewMapper.countByStarAtMost(me, CreditConstant.BAD_REVIEW_STAR))
                .build();
    }

    /** 我的信用分流水（已写好） */
    @Override
    public List<CreditLog> myLogs() {
        return creditLogMapper.listByUser(BaseContext.getCurrentId());
    }

}
