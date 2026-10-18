package com.campus.service;

import com.campus.dto.BargainCounterDTO;
import com.campus.dto.BargainPageQueryDTO;
import com.campus.dto.BargainSubmitDTO;
import com.campus.result.PageResult;

/**
 * 议价服务（校园二手）—— 单轮制
 *
 * 一条议价记录的生命周期：
 *   买家出价(1) ──┬─ 卖家接受 → 已接受(3) + 生成订单
 *                 ├─ 卖家还价 → 卖家还价(2) ──┬─ 买家接受 → 已接受(3) + 生成订单
 *                 │                            └─ 买家拒绝 → 已拒绝(4)
 *                 └─ 卖家拒绝 → 已拒绝(4)
 *   超过 24 小时没人响应 → 已过期(5)
 */
public interface BargainService {

    /**
     * 买家发起议价
     *
     * 要校验的边界（见 docs/04 的二、2.3）：
     *   出价不低于标价 50%、不能议价自己的商品、商品必须"在售"、
     *   同一买家对同一商品不能有进行中的议价、被拒后 24 小时内不能再议
     */
    void submit(BargainSubmitDTO submitDTO);

    /**
     * 接受报价 → 议价成交
     *
     * 谁能接受取决于当前状态：
     *   状态 1（买家出价）→ 只有卖家能接受
     *   状态 2（卖家还价）→ 只有买家能接受
     * 成交后：锁商品 + 生成订单（order_source=2，成交价取议价金额）+ 回填 order_id
     */
    void accept(Long id);

    /**
     * 拒绝报价
     *
     * 同样按状态区分谁有权拒绝：
     *   状态 1 → 卖家拒绝
     *   状态 2 → 买家拒绝
     */
    void reject(Long id);

    /**
     * 卖家还价（单轮制：只有状态 1 时卖家能还价，还完就轮到买家二选一）
     */
    void counter(BargainCounterDTO counterDTO);

    /**
     * 我的议价分页
     * view=buyer → 我发起的；view=seller → 别人对我的商品出的价
     */
    PageResult pageQuery(BargainPageQueryDTO query);

}
