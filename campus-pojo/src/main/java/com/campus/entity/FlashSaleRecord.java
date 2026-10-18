package com.campus.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 捡漏抢购记录
 *
 * 这张表有两个作用，缺一不可：
 *   ① 【记录】谁参与了哪件商品的抢购、有没有抢到、抢到后生成了哪个订单
 *   ② 【兜底防线】表上有唯一索引 uk_product_user(product_id, user_id)——
 *      同一个用户对同一件商品只能有一条记录。
 *      用户狂点按钮、或者两个请求同时到达时，第二条会撞唯一索引，
 *      从而避免"同一个人抢到两次"。
 *
 * 注意 is_success 的设计：表里允许存"未抢到"的记录。
 * 我们当前的实现只在抢到成功时写记录（失败会让事务回滚），
 * 如果将来要统计"多少人参与了但没抢到"，需要把这条记录放到独立事务里写。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 捡漏商品id */
    private Long productId;

    /** 参与抢购的用户id */
    private Long userId;

    /** 是否抢到：0未抢到 1抢购成功 */
    private Integer isSuccess;

    /** 抢购成功生成的订单id */
    private Long orderId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
