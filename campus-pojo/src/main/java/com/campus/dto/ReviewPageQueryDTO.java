package com.campus.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端-评价分页查询条件（S4-5）
 */
@Data
public class ReviewPageQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer page;

    private Integer pageSize;

    /** 只看某个商品的评价 */
    private Long productId;

    /** 只看"某人收到的"评价 */
    private Long toUserId;

    /** 只看某星级（比如查 1 星差评，排查恶意差评） */
    private Integer star;

}
