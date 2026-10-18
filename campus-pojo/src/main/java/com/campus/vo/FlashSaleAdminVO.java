package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端-捡漏活动列表项（S4-3）
 *
 * 和用户端的 FlashSaleVO 比，管理员要多看几样东西：
 *   · 现在的 Redis 库存还剩几件（排查"为什么没人抢"）
 *   · 有多少人参与了（判断活动热度）
 *   · 商品当前状态（还在售 / 已被抢走 / 已下架）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品id */
    private Long productId;

    private String title;

    private BigDecimal price;

    private String sellerNickname;

    /** 抢购开始时间 */
    private LocalDateTime flashStartTime;

    /** 商品状态：0在售 1交易中 2已售罄 3已下架 */
    private Integer status;

    private String statusName;

    /** Redis 里的剩余库存（null = key 还没建过，下次抢购会按初始库存重建） */
    private String redisStock;

    /** 参与抢购的人数（flash_sale_record 里这件商品的记录数） */
    private Integer participants;

    /** 抢到的人数（is_success=1） */
    private Integer grabbed;

}
