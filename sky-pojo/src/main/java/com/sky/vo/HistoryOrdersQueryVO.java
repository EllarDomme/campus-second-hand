package com.sky.vo;

import com.sky.entity.OrderDetail;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class HistoryOrdersQueryVO {
    // 订单基础字段
    private Long id;
    private String number;
    private Integer status;
    private Long userId;
    private Long addressBookId;
    private LocalDateTime orderTime;
    private LocalDateTime checkoutTime;
    private Integer payMethod;
    private Integer payStatus;
    private java.math.BigDecimal amount;
    private String remark;
    private String userName;
    private String phone;
    private String address;
    private String consignee;
    private String cancelReason;
    private String rejectionReason;
    private LocalDateTime cancelTime;
    private LocalDateTime estimatedDeliveryTime;
    private Integer deliveryStatus;
    private LocalDateTime deliveryTime;
    private Integer packAmount;
    private Integer tablewareNumber;
    private Integer tablewareStatus;

    // 订单详情数组
    private List<OrderDetail> orderDetailList;
}
