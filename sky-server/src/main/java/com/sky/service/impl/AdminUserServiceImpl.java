package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.UserConstant;
import com.sky.dto.UserPageQueryDTO;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;
import com.sky.service.AdminUserService;
import com.sky.vo.UserAdminVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    @Autowired
    private UserMapper userMapper;

    /** 用来清"封禁状态缓存"，让封禁/解封立即生效 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult page(UserPageQueryDTO query) {
        int p = (query.getPage() == null || query.getPage() < 1) ? 1 : query.getPage();
        int size = (query.getPageSize() == null || query.getPageSize() < 1) ? 10 : query.getPageSize();
        PageHelper.startPage(p, size);
        List<UserAdminVO> list = userMapper.listForAdmin(query);
        Page<UserAdminVO> pageInfo = (Page<UserAdminVO>) list;
        list.forEach(this::fillNames);
        return new PageResult(pageInfo.getTotal(), list);
    }

    @Override
    public UserAdminVO detail(Long id) {
        UserAdminVO vo = userMapper.getAdminDetailById(id);
        if (vo == null) {
            throw new BaseException("用户不存在");
        }
        fillNames(vo);
        return vo;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        // ① 只允许两个取值（管理员也不能把状态改成 99）
        if (!UserConstant.STATUS_NORMAL.equals(status) && !UserConstant.STATUS_BANNED.equals(status)) {
            throw new BaseException("状态不合法（0正常 1封禁）");
        }
        // ② 用户必须存在
        User user = userMapper.getById(id);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        // ③ 幂等：状态没变就不用写库（也让日志干净，不产生一堆"改了但没变"的记录）
        if (status.equals(user.getStatus())) {
            log.info("用户状态未变化，跳过更新：userId={}, status={}", id, status);
            return;
        }

        // ④ 更新状态
        userMapper.updateStatus(id, status);

        // ⑤ ⚠️ 删掉封禁状态缓存 —— 这一步不能漏！
        //    拦截器为了性能把状态缓存在 Redis 里（5 分钟），
        //    不删的话：刚封禁的人还能继续用 5 分钟，刚解封的人还要等 5 分钟 ✗
        //    这类"改了数据但缓存没跟上"的问题，表现就是"操作不生效"，极难排查。
        stringRedisTemplate.delete(UserConstant.STATUS_CACHE_KEY + id);

        log.warn("管理员变更用户状态：userId={}, nickname={}, {} → {}",
                id, user.getNickname(),
                UserConstant.statusName(user.getStatus()), UserConstant.statusName(status));
    }

    /** 把角色/状态翻译成中文（省得前端维护映射表） */
    private void fillNames(UserAdminVO vo) {
        vo.setRoleName(UserConstant.roleName(vo.getRole()));
        vo.setStatusName(UserConstant.statusName(vo.getStatus()));
    }

}
