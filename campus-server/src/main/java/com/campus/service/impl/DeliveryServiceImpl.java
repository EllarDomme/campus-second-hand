package com.campus.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.campus.constant.DeliveryConstant;
import com.campus.constant.OrderConstant;
import com.campus.constant.UserConstant;
import com.campus.context.BaseContext;
import com.campus.dto.DeliveryPageQueryDTO;
import com.campus.dto.TradeOrderSubmitDTO;
import com.campus.entity.DeliveryOrder;
import com.campus.entity.TradeOrder;
import com.campus.entity.User;
import com.campus.exception.BaseException;
import com.campus.mapper.DeliveryOrderMapper;
import com.campus.mapper.TradeOrderMapper;
import com.campus.mapper.UserMapper;
import com.campus.result.PageResult;
import com.campus.service.DeliveryService;
import com.campus.vo.DeliveryOrderVO;
import com.campus.vo.RunnerIncomeVO;
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

    // ==========================================================================
    // 管理端-跑腿单管理（S4-4）
    //   pageForAdmin / getDetailForAdmin 已写好；cancelByAdmin 是【留给你的练习】
    // ==========================================================================

    /** 【管理端】跑腿单分页列表（已写好） */
    @Override
    public PageResult pageForAdmin(DeliveryPageQueryDTO query) {
        int p = (query.getPage() == null || query.getPage() < 1) ? 1 : query.getPage();
        int size = (query.getPageSize() == null || query.getPageSize() < 1) ? 10 : query.getPageSize();
        PageHelper.startPage(p, size);
        List<DeliveryOrderVO> list = deliveryOrderMapper.listForAdmin(query);
        Page<DeliveryOrderVO> pageInfo = (Page<DeliveryOrderVO>) list;
        return new PageResult(pageInfo.getTotal(), list);
    }

    /** 【管理端】跑腿单详情（已写好，不校验"当事人"） */
    @Override
    public DeliveryOrderVO getDetailForAdmin(Long id) {
        DeliveryOrderVO vo = deliveryOrderMapper.getDetailById(id);
        if (vo == null) {
            throw new BaseException("跑腿单不存在");
        }
        return vo;
    }

    /**
     * 【管理端】强制取消跑腿单
     *
     * 这是"运营兜底"能力：用户端那两条退出路径都走不通时（比如跑腿员失联），
     * 只能由管理员人工介入。
     */
    @Override
    public void cancelByAdmin(Long id, String reason) {
        // ================= 你的部分：填空 A1 ~ A4 =================
        //
        // 【A1】原因必填
        //   · reason 为 null 或 trim 后为空
        //        → throw new BaseException("强制取消必须填写原因")
        //   ⚠️ 为什么：管理员的强制操作是"权力的行使"，必须留下"为什么"，
        //      否则事后没人说得清这单为什么被取消（用户来投诉时你就是举证方）。
        if(reason == null || reason.trim().isEmpty()){
            throw new BaseException("强制取消必须填写原因");
        }
        // 【A2】跑腿单必须存在
        //   DeliveryOrder delivery = deliveryOrderMapper.getById(id);
        //   · delivery == null → throw new BaseException("跑腿单不存在")
        DeliveryOrder deliveryOrder = deliveryOrderMapper.getById(id);
        if(deliveryOrder == null){
            throw new BaseException("跑腿单不存在");
        }
        // 【A3】⭐ 状态校验：已经结束的单子不能动
        //   ⚠️ 用常量在前 + equals，不要再写 ==（这个坑你已经踩了 5 次）
        //   ⚠️ 为什么"已送达"也不能取消：人家确实把货送到了，
        //      管理员取消它 = 抹掉跑腿员的劳动记录（钱和统计都会受影响）。
        //      真要处理纠纷，应该走投诉工单，而不是把已完成的单子抹掉。
        // ┌── 【错题标记 #30】只拦了"已送达"，漏了"已取消" ──────────────────
        // │ 你当时写的：if (STATUS_FINISHED.equals(status)) throw "已经送达，不能取消";
        // │ 漏了：状态 4（已取消）也要拦。
        // │ 后果：对一张【已经取消过】的单子再点一次"强制取消"，
        // │   条件更新 `where status = 4` 会匹配到它自己 →
        // │   MySQL 默认返回的是"匹配行数"=1 → 接口报成功 ✓
        // │   于是管理员会以为"操作生效了"，实际上什么都没发生。
        // │   **这类"操作成功但没有任何效果"最容易让人误判。**
        // │ 记法：状态机里的【终态】通常不止一个 —— 写"不能操作"的守卫时，
        // │      把终态列全（这里是 3已送达 + 4已取消），别只想到最显眼的那个。
        // └──────────────────────────────────────────────────────────────
        if (DeliveryConstant.STATUS_FINISHED.equals(deliveryOrder.getStatus())) {
            throw new BaseException("该跑腿单已送达，不能再取消");
        }
        if (DeliveryConstant.STATUS_CANCELLED.equals(deliveryOrder.getStatus())) {
            throw new BaseException("该跑腿单已取消，不需要重复操作");
        }
        // 【A4】条件更新成"已取消" + 留痕
        //   int rows = deliveryOrderMapper.updateStatusIfMatch(
        //           id, delivery.getStatus(), DeliveryConstant.STATUS_CANCELLED,
        //           null, LocalDateTime.now());
        //   · rows == 0 → throw new BaseException("状态已变化，请刷新后重试")
        //   log.warn("管理员强制取消跑腿单：deliveryId={}, 原状态={}, 跑腿员={}, 原因={}, 管理员={}",
        //           id, delivery.getStatus(), delivery.getRunnerUserId(), reason,
        //           BaseContext.getCurrentId());
        //   ⚠️ 记得把【管理员自己的 id】也记进去 —— 强制操作必须能追到人。
        int rows = deliveryOrderMapper.updateStatusIfMatch(
                id,deliveryOrder.getStatus(),DeliveryConstant.STATUS_CANCELLED,
                null,LocalDateTime.now());
        if(rows == 0){
            throw new BaseException("状态已变化，请刷新后重试");
        }
        // ┌── 【错题标记 #31】漏了留痕日志 ────────────────────────────────
        // │ 你当时写的：条件更新完就结束了，没有任何日志。
        // │ 为什么必须有：这是【管理员行使权力】的操作 ——
        // │   事后用户来投诉"我的单子怎么没了"，平台要能拿出"谁、什么时候、
        // │   因为什么取消的"。没有日志就没有举证能力。
        // │   而且要把【管理员自己的 id】记进去（BaseContext.getCurrentId()），
        // │   否则只能证明"有人取消了"，追不到人。
        // │ 记法：凡是"绕过正常流程、由人手工干预"的操作，一律留痕：
        // │      谁 + 对什么 + 原状态 → 新状态 + 为什么。
        // └──────────────────────────────────────────────────────────────
        log.warn("管理员强制取消跑腿单：deliveryId={}, 原状态={}, 跑腿员={}, 原因={}, 管理员={}",
                id, deliveryOrder.getStatus(), deliveryOrder.getRunnerUserId(), reason,
                BaseContext.getCurrentId());
    }

}
