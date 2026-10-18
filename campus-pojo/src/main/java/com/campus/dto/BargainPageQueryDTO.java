package com.campus.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 我的议价 分页查询条件
 *
 * 和订单列表一个套路：用 view 表达"看哪一边"，用户 id 由后端填。
 *   view=buyer  → 我发起的议价（查 buyer_id）
 *   view=seller → 别人对我的商品出的价（查 seller_id）
 */
@Data
public class BargainPageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    /** 议价状态筛选（可选）：1买家出价 2卖家还价 3已接受 4已拒绝 5已过期 */
    private Integer bargainStatus;

    /** 视角：buyer / seller（不传按 buyer） */
    private String view;

    /** ⚠️ 下面两个由后端填充：Service 先清空，再按 view 填入当前用户 id（防前端伪造）*/
    private Long buyerId;

    private Long sellerId;

}
