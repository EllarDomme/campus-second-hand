package com.sky.vo;

import com.sky.entity.Orders;
import lombok.Data;

import java.io.Serializable;

@Data
public class OrderSearchVO extends Orders implements Serializable {
    private String orderDishes;
}
