package com.sky.service;

import com.sky.entity.CreditLog;
import com.sky.vo.CreditVO;

import java.util.List;

/**
 * 信用分服务
 *
 * 这张模块的意义（面试可以讲）：**信用分不是"一个数字"，而是一套可追溯的规则体系**。
 *
 *   ① 唯一入口：所有加分/扣分都走 changeCredit，不允许别处直接 update user.credit_score
 *      —— 否则流水和总分必然对不上
 *   ② 流水可追溯：每次变更都写 credit_log（谁、多少分、为什么、关联哪笔业务、变更后总分）
 *   ③ 每日加分上限：【只能靠流水算出来】——这是 credit_log 存在的核心理由
 *   ④ 扣分不设上限：否则"每天最多扣 10 分"会变成恶意用户的护身符
 */
public interface CreditService {

    /**
     * 变更信用分（唯一入口）
     *
     * @param userId      被变更的用户
     * @param changeScore 变动分值（加分为正、扣分为负）
     * @param reasonType  原因类型（CreditConstant.REASON_*）
     * @param refId       关联业务id（订单id / 投诉id），用于溯源
     * @param remark      备注
     * @return 实际生效的分值（可能因为"每日加分上限"或"分数下限"被截断，甚至为 0）
     */
    int changeCredit(Long userId, int changeScore, Integer reasonType, Long refId, String remark);

    /**
     * 变更信用分（可指定是否计入"每日加分上限"）
     *
     * ⚠️ 为什么需要这个重载：**补偿性的加分会撞上防刷上限**。
     *   场景：某人今天已经加满 10 分 → 管理员删掉一条恶意差评，要还他 2 分 →
     *   如果这 2 分也算"今日加分"，就会被上限直接吞掉 ✗
     *   但"撤销处罚"根本不是刷分行为，不该受防刷规则约束 ✗
     *
     *   所以：正常加分（完成交易/好评）countTowardDailyCap = true；
     *        补偿性加分（撤销差评扣分）countTowardDailyCap = false。
     *
     * @param countTowardDailyCap true = 计入每日加分上限（默认）；false = 不受上限约束
     */
    int changeCredit(Long userId, int changeScore, Integer reasonType, Long refId,
                     String remark, boolean countTowardDailyCap);

    /** 我的信用分概览（当前分数 + 今日已加 + 好评/差评数） */
    CreditVO myCredit();

    /** 我的信用分流水 */
    List<CreditLog> myLogs();

}
