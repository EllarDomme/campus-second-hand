package com.sky.service.impl;

import com.google.common.collect.Lists;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    public ReportServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
        log.info("营业额数据统计");

        List<LocalDate> dateList = new ArrayList();
        dateList.add(begin);
        List<Double> turnoverList = new ArrayList();

        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        for(LocalDate date : dateList){
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();

            map.put("status", Orders.COMPLETED);
            map.put("begin", beginTime);
            map.put("end", endTime);

            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        //数据封装
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    @Override
    public UserReportVO userStatistics(LocalDate begin, LocalDate end) {
        log.info("用户数据统计");

        List<LocalDate> dateList = new ArrayList();
        dateList.add(begin);
        List<Long> totalUserList = new ArrayList<>();

        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //定义新增用户列表
        List<Long> newUserList = new ArrayList<>();
        //定义总用户列表
        List<Double> totalTurnoverList = new ArrayList<>();

        for(LocalDate date : dateList){
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //新增用户数量
            Long newUser = userMapper.getUserCount(beginTime, endTime);
            Long totalUser = userMapper.getUserCount(null,endTime);
            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }

        //数据封装
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }
}
