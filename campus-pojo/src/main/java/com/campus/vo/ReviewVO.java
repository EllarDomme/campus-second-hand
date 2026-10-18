package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评价（带评价人/被评价人昵称，前端直接展示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private Long productId;

    private String productTitle;

    private Long fromUserId;

    private String fromNickname;

    private String fromAvatar;

    private Long toUserId;

    private String toNickname;

    /** 星级 1~5 */
    private Integer star;

    private String content;

    /** 0真实评价 1系统默认好评 */
    private Integer isDefault;

    private LocalDateTime createTime;

}
