package com.sky.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 卖家还价入参
 *
 * 单轮制下只有一个场景会用到它：买家出价(status=1) → 卖家还价 → status=2。
 * 到了 status=2 之后买家不能再还价，所以不会出现"第二次还价"。
 */
@Data
public class BargainCounterDTO implements Serializable {

    /** 议价记录id */
    private Long id;

    /** 卖家还价金额 */
    private BigDecimal counterOffer;

}
