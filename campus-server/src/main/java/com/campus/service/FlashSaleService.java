package com.campus.service;

import com.campus.entity.FlashSaleRecord;
import com.campus.vo.FlashSaleAdminVO;
import com.campus.vo.FlashSaleVO;

import java.util.List;

/**
 * 限时捡漏抢购服务
 *
 * 抢购接口是整个项目并发压力最大的地方，防超卖用了【三层】：
 *
 *   ① Redis 预扣（Lua 原子扣减）
 *        作用：把绝大多数请求挡在数据库之外，高并发时保护 DB。
 *        注意：它【不是】防超卖的根本保证 —— 见第 ②
 *
 *   ② 数据库条件更新（update product set status=交易中 where id=? and status=在售）
 *        作用：这才是"绝不超卖"的最终保证。
 *        即使 Redis 挂了、或者 Redis 与数据库不一致，这一层依然安全。
 *
 *   ③ 抢购记录唯一索引 uk_product_user(product_id, user_id)
 *        作用：防止同一个用户重复抢到（狂点按钮 / 并发请求）。
 *
 * 另外注意：Redis 不参与数据库事务，所以"Redis 扣减成功但数据库失败"时必须补偿
 * （把库存加回去），否则会让商品一直显示"已抢光"。
 */
public interface FlashSaleService {

    /** 捡漏专区列表（带抢购状态：未开始 / 抢购中 / 已抢光） */
    List<FlashSaleVO> listFlashSale();

    /** 抢购一件捡漏商品 */
    void grab(Long productId);

    /** 我的抢购记录（只返回抢到过的） */
    List<FlashSaleRecord> listMyRecord();

    // ==================== 管理端-捡漏活动管理（S4-3）====================

    /**
     * 【管理端】捡漏活动列表
     * 比用户端多三样：Redis 剩余库存、参与人数、抢到人数
     */
    List<FlashSaleAdminVO> listForAdmin();

    /**
     * 【管理端】重置某件捡漏商品的 Redis 库存
     * 用途：活动结束 / 商品重新上架 / 回归测试准备数据（原来是个调试接口，现在正式化）
     */
    void resetStock(Long productId);

    /**
     * 【管理端】提前结束捡漏活动
     * 效果：商品变回普通商品（is_flash_sale = 0），并清掉 Redis 库存
     */
    void endActivity(Long productId);

}
