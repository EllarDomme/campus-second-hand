package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端处理投诉入参
 */
@Data
public class ComplaintHandleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单id */
    private Long id;

    /**
     * 处理结果：1投诉成立 2投诉不成立
     *
     * ⚠️ 用 Integer 而不是 boolean：将来可能加"3部分成立""4已撤销"之类，
     *    用 tinyint 语义和数据库字段也对得上。
     */
    private Integer handleResult;

    /** 处理备注（会展示给双方看，所以要写清楚依据） */
    private String handleNote;

}
