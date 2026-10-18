package com.campus.service;

import com.campus.dto.DeliveryPageQueryDTO;
import com.campus.dto.TradeOrderSubmitDTO;
import com.campus.entity.TradeOrder;
import com.campus.result.PageResult;
import com.campus.vo.DeliveryOrderVO;
import com.campus.vo.RunnerIncomeVO;

import java.util.List;

/**
 * 校园跑腿服务
 *
 * 与主交易订单是一对一：买家下单时选 dealType=1（跑腿），订单创建后同步生成跑腿单。
 *
 * 状态机：
 *   0 待接单 ──跑腿员抢单──> 1 已接单 ──确认取货──> 2 取货中 ──确认送达──> 3 已送达
 *     任一环节可取消 ──> 4 已取消
 *
 * ⚠️ 注意：`3 已送达` ≠ 主订单完成。主订单的"已完成"仍要买卖双方确认收货
 *    （跑腿员说送到了，不等于买家收到了）。
 */
public interface DeliveryService {

    /** 待接单大厅（跑腿员视角） */
    List<DeliveryOrderVO> listAvailable();

    /** 抢单：多个跑腿员同时抢，只有一个人能成功（条件更新） */
    void accept(Long id);

    /** 跑腿员确认取货：1 已接单 → 2 取货中 */
    void pickup(Long id);

    /** 跑腿员确认送达：2 取货中 → 3 已送达（并记录送达时间） */
    void finish(Long id);

    /** 取消跑腿单：买家/卖家可取消，跑腿员不能自己取消 */
    void cancel(Long id);

    /** 我的跑腿单（跑腿员视角） */
    List<DeliveryOrderVO> listMyOrders();

    /** 跑腿单详情（买卖双方 / 跑腿员都可看） */
    DeliveryOrderVO getDetail(Long id);

    /**
     * 按主交易订单查跑腿单（买家/卖家视角）
     *
     * 为什么需要它：买家下单选了跑腿之后，手里只有【交易订单号】，
     * 需要能顺着订单找到"谁在给我送、送到哪一步了"。
     */
    DeliveryOrderVO getByTradeOrderId(Long tradeOrderId);

    /** 我的跑腿收入统计（跑腿员视角）—— 只统计展示，平台不做资金流转 */
    RunnerIncomeVO myIncome();

    /**
     * 交易订单创建后生成跑腿单（供 TradeOrderService 调用，只在 dealType=1 时调用）
     *
     * 跑腿费在这里【算好并落库】（计费快照）：
     * 以后计费规则改了，历史订单的金额也不会被改动。
     */
    void createForTradeOrder(TradeOrder tradeOrder, TradeOrderSubmitDTO submitDTO);

    /**
     * 主订单取消后联动取消跑腿单（供 TradeOrderService 调用）
     *
     * 为什么必须联动：主订单都取消了，跑腿员还跑去取货就是白跑一趟。
     * 已经送达（3）的不动 —— 人家确实送了，只是订单后来被取消/纠纷处理。
     */
    void cancelByTradeOrder(Long tradeOrderId, String reason);

    // ==================== 管理端-跑腿单管理（S4-4）====================

    /**
     * 【管理端】跑腿单分页列表（可按状态/跑腿员/交易订单筛选）
     *
     * 为什么需要它：跑腿员视角的 /my 只能看自己的单子，
     * 管理员需要全局视角 —— 谁手上压着单子、哪些单子卡住了。
     */
    PageResult pageForAdmin(DeliveryPageQueryDTO query);

    /**
     * 【管理端】跑腿单详情（不受"仅买卖双方+跑腿员"的限制）
     *
     * 用户端的 getDetail 会校验"你是不是当事人"，管理员当然不是当事人，
     * 所以需要这个不校验权限的版本（管理端令牌本身就是权限凭证）。
     */
    DeliveryOrderVO getDetailForAdmin(Long id);

    /**
     * 【管理端】强制取消跑腿单
     *
     * 为什么需要它：现在跑腿单只有两条"退出路径"——
     *   ① 跑腿员自己推进到已送达
     *   ② 买家/卖家主动取消
     * 如果跑腿员接单后失联，单子就卡死了，只能靠管理员介入。
     *
     * @param id     跑腿单id
     * @param reason 强制取消的原因（必填，便于事后追溯）
     */
    void cancelByAdmin(Long id, String reason);

}
