package com.sky.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品发布 / 编辑入参
 *
 * 刻意不包含这些字段（都是为了防越权，前端传了也不理会）：
 *   sellerId     卖家身份必须由后端从登录态取，否则任何人可以冒用别人的身份发布商品
 *   auditStatus  审核结果只能由管理员审核接口写，不能让发布者自己把自己标成"已通过"
 *   status       售卖状态的流转有业务规则（下单变交易中、成交变售罄），不接受直接设置
 *   auditNote / auditTime  审核意见与审核时间由管理员审核时写入
 */
@Data
public class ProductDTO implements Serializable {

    /** 编辑时必填；发布时为空 */
    private Long id;

    /** 商品分类id */
    private Long categoryId;

    /** 商品标题 */
    private String title;

    /** 商品标价 */
    private BigDecimal price;

    /** 图片url，JSON数组字符串 */
    private String images;

    /** 商品描述：成色、瑕疵、配件等细节 */
    private String description;

    /** 新旧程度：1全新 2几乎全新 3轻微使用痕迹 4明显使用痕迹 */
    private Integer conditionLevel;

    /** 是否限时捡漏：0普通 1捡漏（不传按普通处理） */
    private Integer isFlashSale;

    /** 捡漏抢购开始时间（isFlashSale=1 时必填） */
    private LocalDateTime flashStartTime;

}
