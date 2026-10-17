package com.sky.controller.admin;

import com.sky.dto.CreditAdjustDTO;
import com.sky.dto.UserPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.AdminUserService;
import com.sky.vo.UserAdminVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-用户管理接口（S4-1）
 */
@RestController("adminUserController")
@RequestMapping("/admin/user")
@Api(tags = "管理端-用户管理接口")
@Slf4j
public class UserController {

    @Autowired
    private AdminUserService adminUserService;

    /**
     * 用户分页列表
     * 用法：GET /admin/user/page?status=1&role=1&keyword=小张&page=1&pageSize=10
     */
    @GetMapping("/page")
    @ApiOperation("用户分页列表（可按状态/角色/关键词筛选）")
    public Result<PageResult> page(UserPageQueryDTO query) {
        return Result.success(adminUserService.page(query));
    }

    @GetMapping("/{id}")
    @ApiOperation("用户详情（含信用分与好评差评数）")
    public Result<UserAdminVO> detail(@PathVariable Long id) {
        return Result.success(adminUserService.detail(id));
    }

    /**
     * 封禁 / 解封
     * 用法：PUT /admin/user/status/1?id=5   （1封禁、0解封）
     */
    @PutMapping("/status/{status}")
    @ApiOperation("封禁(1)/解封(0)用户")
    public Result updateStatus(@PathVariable Integer status, @RequestParam Long id) {
        log.info("管理员变更用户状态：id={}, status={}", id, status);
        adminUserService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 【功能A】变更用户角色（跑腿员认证）
     * 用法：PUT /admin/user/role/1?id=5   （1升级为跑腿员、0取消跑腿员）
     */
    @PutMapping("/role/{role}")
    @ApiOperation("变更角色：1跑腿员 0普通用户")
    public Result updateRole(@PathVariable Integer role, @RequestParam Long id) {
        log.info("管理员变更用户角色：id={}, role={}", id, role);
        adminUserService.updateRole(id, role);
        return Result.success();
    }

    /**
     * 【功能B】手动调整信用分
     * 用法：PUT /admin/user/credit   body: {"userId":5,"changeScore":-5,"remark":"..."}
     */
    @PutMapping("/credit")
    @ApiOperation("手动调整信用分（需填写理由）")
    public Result adjustCredit(@RequestBody CreditAdjustDTO adjustDTO) {
        log.info("管理员调整信用分：{}", adjustDTO);
        adminUserService.adjustCredit(adjustDTO);
        return Result.success();
    }

}
