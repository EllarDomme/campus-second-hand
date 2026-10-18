package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理端数据看板（S4-2）
 *
 * 设计思路：管理员一进后台最想知道两件事 ——
 *   ① 现在有什么活儿要干（待办）
 *   ② 平台整体跑得怎么样（交易 / 用户 / 商品）
 * 所以把指标分成四组，而不是摊成 15 个平铺字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------- 待办：管理员进来先看这三个数字 ----------
    /** 待审核商品数 */
    private Integer pendingAuditProducts;

    /** 待处理投诉数（handle_status=0） */
    private Integer pendingComplaints;

    /** 待接单跑腿数（status=0） */
    private Integer waitingDeliveries;

    // ---------- 四组概况 ----------
    private DashboardTradeVO trade;

    private DashboardUserVO user;

    private DashboardProductVO product;

}
