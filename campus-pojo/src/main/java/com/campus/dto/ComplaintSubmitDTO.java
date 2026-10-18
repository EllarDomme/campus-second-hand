package com.campus.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 提交投诉/举报入参
 *
 * 刻意不包含这些字段：
 *   complainantUserId  投诉人从登录态取
 *   respondentUserId   被投诉人由服务端推导（商品举报→卖家；交易纠纷→交易对手）
 *                      不接受前端传，否则可以伪造"我投诉任何人"
 *   handleStatus / handleResult / handleNote  处理相关字段只有管理员能动
 */
@Data
public class ComplaintSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单类型：0商品举报 1交易纠纷 */
    private Integer complaintType;

    /** 商品id（商品举报必填） */
    private Long productId;

    /** 订单id（交易纠纷必填） */
    private Long orderId;

    /** 投诉原因：1货不对板 2虚假描述 3对方失联 4跑腿纠纷 9其他 */
    private Integer complaintReason;

    /** 投诉内容 */
    private String content;

    /**
     * 凭证图片 url 的 JSON 数组串，例如 ["https://.../1.jpg","https://.../2.jpg"]
     * 由前端上传图片后拿到 url 再传进来（本项目不做图片上传，直接用外链/示例地址）
     */
    private String images;

}
