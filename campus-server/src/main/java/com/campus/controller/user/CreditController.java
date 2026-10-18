package com.campus.controller.user;

import com.campus.entity.CreditLog;
import com.campus.result.Result;
import com.campus.service.CreditService;
import com.campus.vo.CreditVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端-信用分接口
 */
@RestController("userCreditController")
@RequestMapping("/user/credit")
@Api(tags = "C端-信用分接口")
@Slf4j
public class CreditController {

    @Autowired
    private CreditService creditService;

    @GetMapping("/my")
    @ApiOperation("我的信用分概览")
    public Result<CreditVO> my() {
        return Result.success(creditService.myCredit());
    }

    @GetMapping("/logs")
    @ApiOperation("我的信用分流水")
    public Result<List<CreditLog>> logs() {
        return Result.success(creditService.myLogs());
    }

}
