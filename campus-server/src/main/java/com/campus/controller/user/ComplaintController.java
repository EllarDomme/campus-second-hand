package com.campus.controller.user;

import com.campus.dto.ComplaintSubmitDTO;
import com.campus.result.Result;
import com.campus.service.ComplaintService;
import com.campus.vo.ComplaintVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端-投诉举报接口
 */
@RestController("userComplaintController")
@RequestMapping("/user/complaint")
@Api(tags = "C端-投诉举报接口")
@Slf4j
public class ComplaintController {

    @Autowired
    private ComplaintService complaintService;

    @PostMapping
    @ApiOperation("提交投诉/举报")
    public Result<Long> submit(@RequestBody ComplaintSubmitDTO submitDTO) {
        log.info("提交投诉：type={}, reason={}", submitDTO.getComplaintType(), submitDTO.getComplaintReason());
        return Result.success(complaintService.submit(submitDTO));
    }

    @GetMapping("/my")
    @ApiOperation("我的投诉记录")
    public Result<List<ComplaintVO>> my() {
        return Result.success(complaintService.myComplaints());
    }

    @GetMapping("/{id}")
    @ApiOperation("工单详情（投诉人/被投诉人可见）")
    public Result<ComplaintVO> detail(@PathVariable Long id) {
        return Result.success(complaintService.detail(id));
    }

}
