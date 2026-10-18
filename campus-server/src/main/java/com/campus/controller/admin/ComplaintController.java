package com.campus.controller.admin;

import com.campus.dto.ComplaintHandleDTO;
import com.campus.result.PageResult;
import com.campus.result.Result;
import com.campus.service.ComplaintService;
import com.campus.vo.ComplaintVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端-投诉工单接口
 *
 * 认证走管理端令牌（请求头 token），由 JwtTokenAdminInterceptor 保护。
 */
@RestController("adminComplaintController")
@RequestMapping("/admin/complaint")
@Api(tags = "管理端-投诉工单接口")
@Slf4j
public class ComplaintController {

    @Autowired
    private ComplaintService complaintService;

    /**
     * 工单分页列表
     * 用法：GET /admin/complaint/page?handleStatus=0&complaintType=1&page=1&pageSize=10
     */
    @GetMapping("/page")
    @ApiOperation("工单分页列表（可按状态/类型筛选）")
    public Result<PageResult> page(@RequestParam(required = false) Integer handleStatus,
                                   @RequestParam(required = false) Integer complaintType,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(complaintService.pageForAdmin(handleStatus, complaintType, page, pageSize));
    }

    @PutMapping("/handle")
    @ApiOperation("处理工单（成立/不成立；成立会扣被投诉人信用分）")
    public Result handle(@RequestBody ComplaintHandleDTO handleDTO) {
        log.info("处理投诉工单：id={}, 结果={}", handleDTO.getId(), handleDTO.getHandleResult());
        complaintService.handle(handleDTO);
        return Result.success();
    }

}
