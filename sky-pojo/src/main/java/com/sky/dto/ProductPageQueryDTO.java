package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品分页查询条件
 */
@Data
public class ProductPageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    /** 商品标题，模糊匹配 */
    private String title;

    /** 分类id（注意：category.id 是 bigint，这里不能用 Integer） */
    private Long categoryId;

    /** 售卖状态：0在售 1交易中 2已售罄 3已下架 */
    private Integer status;

    /** 审核状态：0待审核 1已通过 2已驳回（管理端审核列表用） */
    private Integer auditStatus;

    /** 卖家用户id（"我发布的"用；服务端会强制覆盖为当前登录用户） */
    private Long sellerId;

}
