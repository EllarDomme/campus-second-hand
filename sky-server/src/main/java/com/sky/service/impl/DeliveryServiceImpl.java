package com.sky.service.impl;

import com.sky.constant.DeliveryConstant;
import com.sky.constant.OrderConstant;
import com.sky.constant.UserConstant;
import com.sky.context.BaseContext;
import com.sky.dto.TradeOrderSubmitDTO;
import com.sky.entity.DeliveryOrder;
import com.sky.entity.TradeOrder;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.mapper.DeliveryOrderMapper;
import com.sky.mapper.TradeOrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.DeliveryService;
import com.sky.vo.DeliveryOrderVO;
import com.sky.vo.RunnerIncomeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 校园跑腿服务实现
 *
 * ==========================================================================
 * 【你的任务】把 4 个状态机方法的【填空】写完（编号 1~9）：
 *     accept（抢单）／ pickup（取货）／ finish（送达）／ cancel（取消）
 *   每个方法末尾有一句 throw new UnsupportedOperationException(...)，写完删掉。
 *
 *   已替你写好（不用改）：listAvailable / listMyOrders / getDetail / myIncome
 *                        createForTradeOrder（生成跑腿单）
 *                        cancelByTradeOrder（主订单取消时联动）
 * ==========================================================================
 */
@Service
@Slf4j
public class DeliveryServiceImpl implements DeliveryService {

    @Autowired
    private DeliveryOrderMapper deliveryOrderMapper;

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private UserMapper userMapper;

    /** 待接单大厅（已写好） */
    @Override
    public List<DeliveryOrderVO> listAvailable() {
        return deliveryOrderMapper.listAvailable();
    }

    /**
     * 抢单
     *
     * 这是整个跑腿模块最有技术含量的一步：多个跑腿员同时点"接单"，
     * 必须只有一个人成功，而且不能让任何人接"自己的订单"。
     */
    @Override
    public void accept(Long id) {
        Long me = BaseContext.getCurrentId();

        // ① 资格校验：必须是"正常状态"的跑腿员
        User user = userMapper.getById(me);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        if (!UserConstant.ROLE_RUNNER.equals(user.getRole())) {
            throw new BaseException("只有跑腿员可以接单");
        }
        if (!UserConstant.STATUS_NORMAL.equals(user.getStatus())) {
            throw new BaseException("账号已被封禁，不能接单");
        }

        // ② 跑腿单必须存在；且不能接"自己参与的订单"（自己给自己跑腿）
        // ┌── 【错题标记 #17】重复代码块 ────────────────────────────────
        // │ 你当时写的：先查了一遍 delivery 并判空，紧接着又查了一遍
        // │            （deliveryOrderMapper.getById(id) 出现了两次，
        // │              中间还夹着一句重复的"封禁校验"）。
        // │ 后果：功能上没错（第二次覆盖第一次），但读代码的人会以为
        // │       "这里为什么要查两次？是不是漏了什么？" —— 重复代码最大的代价
        // │       不是性能，而是【让后来的人不敢改】。
        // │ 习惯：写完一个方法扫一眼，有没有"同一件事做了两遍"。
        // └──────────────────────────────────────────────────────────────
        DeliveryOrder delivery = deliveryOrderMapper.getById(id);
        if (delivery == null) {
            throw new BaseException("跑腿单不存在");
        }
        TradeOrder tradeOrder = tradeOrderMapper.getById(delivery.getTradeOrderId());
        if (tradeOrder == null) {
            throw new BaseException("主订单不存在");
        }
        if (me.equals(tradeOrder.getBuyerId()) || me.equals(tradeOrder.getSellerId())) {
            throw new BaseException("不能给自己的订单跑腿");
        }

        // ③ 主订单必须还活着（已取消 / 已完成的单子不该再有人去跑）
        boolean alive = OrderConstant.STATUS_PENDING_CONFIRM.equals(tradeOrder.getStatus())
                || OrderConstant.STATUS_PENDING_TRADE.equals(tradeOrder.getStatus());
        if (!alive) {
            throw new BaseException("主订单已结束，不能接单");
        }

        // ④ 抢单：条件更新，影响 0 行说明已经被别人抢走了
        // ┌── 【错题标记 #18】报错文案和实际原因对不上 ────────────────────
        // │ 你当时写的："主订单手慢了，不能接单"
        // │ 问题：rows == 0 的原因是"【这一单】刚被别的跑腿员抢走了"，
        // │       跟主订单没关系。文案里多出来的"主订单"会让用户以为是
        // │       订单出问题了，去反复刷新订单页面 —— 排查方向被带偏。
        // │ 习惯：错误文案要精确指向真正的原因（谁、因为什么、该怎么办）。
        // └──────────────────────────────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();
        int rows = deliveryOrderMapper.acceptIfWaiting(id, me, now, now);
        if (rows == 0) {
            throw new BaseException("手慢了，这单已被别人接走");
        }
        log.info("跑腿员接单成功：deliveryOrderId={}, runnerUserId={}", id, me);
    }

    /**
     * 确认取货：1 已接单 → 2 取货中
     */
    @Override
    public void pickup(Long id) {
        Long me = BaseContext.getCurrentId();
        DeliveryOrder delivery = getDeliveryOrThrow(id);

        // 只有接单的跑腿员本人能操作（允许法：不是他就拒绝）
        if (!me.equals(delivery.getRunnerUserId())) {
            throw new BaseException("只有接单的跑腿员可以操作");
        }

        // 状态流转：只有当前是"已接单"才能变"取货中"
        int rows = deliveryOrderMapper.updateStatusIfMatch(id,
                DeliveryConstant.STATUS_ACCEPTED, DeliveryConstant.STATUS_PICKED,
                null, LocalDateTime.now());     // 取货不写 finishTime
        if (rows == 0) {
            throw new BaseException("状态已变化，请刷新后重试");
        }
        log.info("跑腿员确认取货：deliveryOrderId={}, runnerUserId={}", id, me);
    }

    /**
     * 确认送达：2 取货中 → 3 已送达（并记录送达时间，收入统计要用）
     */
    @Override
    public void finish(Long id) {
        Long me = BaseContext.getCurrentId();
        DeliveryOrder delivery = getDeliveryOrThrow(id);

        if (!me.equals(delivery.getRunnerUserId())) {
            throw new BaseException("只有接单的跑腿员可以操作");
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = deliveryOrderMapper.updateStatusIfMatch(id,
                DeliveryConstant.STATUS_PICKED, DeliveryConstant.STATUS_FINISHED,
                now,     // ← finish_time：送达时间（统计与超时判定的依据，不能漏）
                now);
        if (rows == 0) {
            throw new BaseException("状态已变化，请刷新后重试");
        }
        log.info("跑腿员确认送达：deliveryOrderId={}, runnerUserId={}", id, me);
    }

    /**
     * 取消跑腿单（买家 / 卖家）
     *
     * 跑腿员不能自己取消 —— 接了单就该跑完，真有意外走客服/管理员介入。
     */
    @Override
    public void cancel(Long id) {
        Long me = BaseContext.getCurrentId();
        DeliveryOrder delivery = getDeliveryOrThrow(id);

        TradeOrder tradeOrder = tradeOrderMapper.getById(delivery.getTradeOrderId());
        if (tradeOrder == null) {
            throw new BaseException("主订单不存在");
        }

        // 权限：只有主订单的买卖双方能取消（允许法）
        if (!me.equals(tradeOrder.getBuyerId()) && !me.equals(tradeOrder.getSellerId())) {
            throw new BaseException("无权取消该跑腿单");
        }

        // 终态不能再取消
        // ┌── 【错题标记 #19】Integer 又用 == 比较了 ─────────────────────
        // │ 你当时写的：deliveryOrder.getStatus() == DeliveryConstant.STATUS_FINISHED
        // │ 为什么错：== 比的是对象引用。状态值 3/4 正好落在 Integer 缓存
        // │           (-128~127) 里所以侥幸能跑对 —— 这是【第 3 次】出现同类问题了
        // │           （前面在 FlashSale 的 calcFlashStatus 也踩过）。
        // │ 改法：常量在前 + equals → DeliveryConstant.STATUS_FINISHED.equals(status)
        // │ 记忆点：只要看到两个 Integer/Long 用 == 比大小，就该警觉。
        // └──────────────────────────────────────────────────────────────
        if (DeliveryConstant.STATUS_FINISHED.equals(delivery.getStatus())
                || DeliveryConstant.STATUS_CANCELLED.equals(delivery.getStatus())) {
            throw new BaseException("跑腿单已结束，不能取消");
        }

        // 条件更新：把"当前状态"作为前置条件 —— 只有状态没被人改过才能取消
        int rows = deliveryOrderMapper.updateStatusIfMatch(id, delivery.getStatus(),
                DeliveryConstant.STATUS_CANCELLED, null, LocalDateTime.now());
        if (rows == 0) {
            throw new BaseException("状态已变化，请刷新后重试");
        }
        log.info("跑腿单已取消：deliveryOrderId={}, 操作人={}", id, me);
    }

    /**
     * 查跑腿单，不存在就抛异常（公共小工具，4 个状态机方法都复用）
     * 和订单模块的 getOrderOrThrow 是同一个套路：把"查不到就报错"收敛到一处。
     */
    private DeliveryOrder getDeliveryOrThrow(Long id) {
        DeliveryOrder delivery = deliveryOrderMapper.getById(id);
        if (delivery == null) {
            throw new BaseException("跑腿单不存在");
        }
        return delivery;
    }

    /** 我的跑腿单（已写好） */
    @Override
    public List<DeliveryOrderVO> listMyOrders() {
        return deliveryOrderMapper.listMyOrders(BaseContext.getCurrentId());
    }

    /**
     * 跑腿单详情（已写好）
     *
     * ⚠️ 加了权限校验：跑腿单里有【取货/收货地址】，是敏感信息。
     *    只有"这笔交易的买卖双方"和"接了单的跑腿员"能看，其他人一律拒绝。
     */
    @Override
    public DeliveryOrderVO getDetail(Long id) {
        return checkAndReturn(deliveryOrderMapper.getDetailById(id));
    }

    /** 按主交易订单查跑腿单（已写好，权限同上） */
    @Override
    public DeliveryOrderVO getByTradeOrderId(Long tradeOrderId) {
        DeliveryOrder delivery = deliveryOrderMapper.getByTradeOrderId(tradeOrderId);
        if (delivery == null) {
            throw new BaseException("这笔交易没有走跑腿");
        }
        return checkAndReturn(deliveryOrderMapper.getDetailById(delivery.getId()));
    }

    /**
     * 查看跑腿单的公共权限校验：只有买卖双方、或已接单的跑腿员能看
     * （允许法：不是这三种人一律拒绝）
     */
    private DeliveryOrderVO checkAndReturn(DeliveryOrderVO vo) {
        if (vo == null) {
            throw new BaseException("跑腿单不存在");
        }
        Long me = BaseContext.getCurrentId();
        boolean allowed = me.equals(vo.getBuyerId())
                || me.equals(vo.getSellerId())
                || me.equals(vo.getRunnerUserId());
        if (!allowed) {
            throw new BaseException("无权查看该跑腿单");
        }
        return vo;
    }

    /** 我的跑腿收入统计（已写好） */
    @Override
    public RunnerIncomeVO myIncome() {
        return deliveryOrderMapper.getIncome(BaseContext.getCurrentId());
    }

    /**
     * 交易订单创建后生成跑腿单（已写好）
     *
     * 跑腿费在这里算好并落库 —— 这就是"计费快照"：
     * 以后计费规则改成 10 元起步，这笔历史订单仍然是当时的 5 元。
     */
    @Override
    public void createForTradeOrder(TradeOrder tradeOrder, TradeOrderSubmitDTO submitDTO) {
        BigDecimal km = DeliveryConstant.kmOf(submitDTO.getDistanceLevel());
        BigDecimal fee = DeliveryConstant.calcFee(km);

        LocalDateTime now = LocalDateTime.now();
        deliveryOrderMapper.insert(DeliveryOrder.builder()
                .tradeOrderId(tradeOrder.getId())
                .runnerUserId(null)                                   // 还没人接单
                .pickAddress(submitDTO.getPickAddress())
                .receiveAddress(submitDTO.getReceiveAddress())
                .deliveryFee(fee)                                     // 计费快照
                .status(DeliveryConstant.STATUS_WAITING)
                .createTime(now)
                .updateTime(now)
                .build());

        log.info("已生成跑腿单：tradeOrderId={}, 距离档位={}, 估算{}km, 跑腿费={}",
                tradeOrder.getId(), submitDTO.getDistanceLevel(), km, fee);
    }

    /**
     * 主订单取消后联动取消跑腿单（已写好）
     *
     * ⚠️ 只取消"还没送达"的（0/1/2）；已经送达(3)的不动 ——
     *    人家确实把货送到了，取消它等于抹掉跑腿员的劳动记录。
     */
    @Override
    public void cancelByTradeOrder(Long tradeOrderId, String reason) {
        DeliveryOrder delivery = deliveryOrderMapper.getByTradeOrderId(tradeOrderId);
        if (delivery == null) {
            return;   // 这笔交易本来就没走跑腿
        }
        Integer status = delivery.getStatus();
        if (DeliveryConstant.STATUS_FINISHED.equals(status)
                || DeliveryConstant.STATUS_CANCELLED.equals(status)) {
            return;   // 已送达或已取消，不用再动
        }
        int rows = deliveryOrderMapper.updateStatusIfMatch(delivery.getId(), status,
                DeliveryConstant.STATUS_CANCELLED, null, LocalDateTime.now());
        log.info("主订单取消，联动取消跑腿单：deliveryId={}, rows={}, 原因={}",
                delivery.getId(), rows, reason);
    }

}
