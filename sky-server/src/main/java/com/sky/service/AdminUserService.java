package com.sky.service;

import com.sky.dto.CreditAdjustDTO;
import com.sky.dto.UserPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.UserAdminVO;

/**
 * 管理端-用户管理服务（S4-1）
 *
 * 这个模块存在的意义：**把"封禁"这条规则真正接上电**。
 * 在此之前 user.status 字段和跑腿抢单里的校验都在，但没有管理员入口能改它 ——
 * 属于"有规则、有检查、没开关"。
 */
public interface AdminUserService {

    /** 用户分页列表（可按状态/角色/关键词筛选） */
    PageResult page(UserPageQueryDTO query);

    /** 用户详情（信用分 + 好评差评数） */
    UserAdminVO detail(Long id);

    /**
     * 封禁 / 解封
     *
     * @param status 0解封（恢复正常） 1封禁
     */
    void updateStatus(Long id, Integer status);

    /**
     * 【功能A】变更用户角色：普通用户 ↔ 跑腿员（跑腿员认证 / 取消资格）
     *
     * @param role 0普通用户 1跑腿员
     */
    void updateRole(Long id, Integer role);

    /**
     * 【功能B】管理员手动调整信用分
     *
     * 内部走 CreditService.changeCredit 这个唯一入口，所以每日加分上限、
     * 分数下限这些边界都不用在这里重复实现。
     */
    void adjustCredit(CreditAdjustDTO adjustDTO);

}
