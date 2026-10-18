package com.campus.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品收藏（用户 <-> 商品 的多对多中间表）
 *
 * 说明：类名首字母必须大写（Java 命名规范），文件名也要和类名完全一致。
 *      你原来写的是 public class favorite，文件名是 favorite.java —— 虽然能编译，
 *      但后面别的代码写 new Favorite() / List<Favorite> 时就找不到这个类了。
 *      所以这里把类名和文件名统一改成 Favorite。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Favorite implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 用户id */
    private Long userId;

    /** 商品id */
    private Long productId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
