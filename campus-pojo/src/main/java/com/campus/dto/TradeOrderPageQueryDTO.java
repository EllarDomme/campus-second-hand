package com.campus.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 我的订单分页查询条件
 *
 * ⚠️ 注意这里**没有 buyerId / sellerId**：
 *     查"我的订单"时，用户 id 必须由后端从登录态取（BaseContext.getCurrentId()），
 *     如果用前端传的 id，别人传一个别人的 id 就能看别人的订单 —— 这是典型的越权漏洞。
 *     所以这里用 view 表达"我要看买家视角还是卖家视角"，由后端决定去查哪一列。
 *
 * ⚠️ 这里的 view 和 user 表的 role **没有任何关系**：
 *     user.role  = 这个人是什么身份（0普通用户 / 1跑腿员）
 *     view       = 他想看订单的哪一边（buyer 我买到的 / seller 我卖出的）
 *     同一个人两个视角都能看，所以查订单时**不需要去查 user 表**。
 */
@Data
public class TradeOrderPageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    /** 订单状态筛选（可选）：0待确认 1待交易 2已完成 3已取消 4超时取消 */
    private Integer status;

    /**
     * 视角：buyer = 我买到的（查 buyer_id）；seller = 我卖出的（查 seller_id）
     * 不传按 buyer 处理
     *
     * ⚠️ 命名说明：这里原来叫 role，已经改名为 view，原因——
     *    user 表里也有一个 role（0普通用户 / 1跑腿员），两个"role"含义完全不同，
     *    极易让人误以为"看订单要先去查 user.role"，其实毫无关系。
     *    这个字段表达的是「从哪一边看订单」，所以叫 view（视角）更准确。
     */
    private String view;

    /**
     * ⚠️ 下面两个字段是「由后端填充」的，前端传什么都不作数：
     *     Service 里必须**先清空这两个字段**，再按 role 把自己算出来的当前用户 id 填进去。
     *     这样即使前端伪造 buyerId，也会被覆盖掉（防越权）。
     *
     *     为什么放在 DTO 里而不是当方法参数：让 Service 到 Mapper 的传参更直观，
     *     Mapper 的 XML 只需要判断"哪个不为空就按哪个过滤"。
     */
    private Long buyerId;

    private Long sellerId;

}
