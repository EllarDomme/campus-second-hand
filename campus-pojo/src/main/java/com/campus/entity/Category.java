package com.campus.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    //id
    private Long id;

    //名称
    private String categoryName;

    //创建时间
    private LocalDateTime createTime;

    //更新时间
    private LocalDateTime updateTime;
}
