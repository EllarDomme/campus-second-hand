package com.sky.service;

import com.sky.dto.ComplaintHandleDTO;
import com.sky.dto.ComplaintSubmitDTO;
import com.sky.result.PageResult;
import com.sky.vo.ComplaintVO;

import java.util.List;

/**
 * 投诉举报服务
 *
 * 两类工单：
 *   · 商品举报（type=0）—— 举报商品本身（虚假描述、货不对板），可不关联订单
 *   · 交易纠纷（type=1）—— 针对某笔交易，必须关联订单，被投诉人是交易对手
 *
 * 闭环：用户提交 → 管理员处理（成立/不成立）→ 成立则扣被投诉人信用分。
 */
public interface ComplaintService {

    /**
     * 提交投诉/举报
     * @return 工单id
     */
    Long submit(ComplaintSubmitDTO submitDTO);

    /** 我的投诉记录 */
    List<ComplaintVO> myComplaints();

    /** 工单详情（投诉人和被投诉人都能看，其他人不行） */
    ComplaintVO detail(Long id);

    /** 【管理端】工单分页列表（可按状态/类型筛选） */
    PageResult pageForAdmin(Integer handleStatus, Integer complaintType, Integer page, Integer pageSize);

    /** 【管理端】处理工单：投诉成立则扣被投诉人信用分 */
    void handle(ComplaintHandleDTO handleDTO);

}
