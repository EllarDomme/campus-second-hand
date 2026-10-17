package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.CreditConstant;
import com.sky.constant.UserConstant;
import com.sky.context.BaseContext;
import com.sky.dto.CreditAdjustDTO;
import com.sky.dto.UserPageQueryDTO;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.mapper.DeliveryOrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;
import com.sky.service.AdminUserService;
import com.sky.service.CreditService;
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

    /** 功能A 要用：检查跑腿员手上还有没有进行中的单子 */
    @Autowired
    private DeliveryOrderMapper deliveryOrderMapper;

    /** 功能B 要用：信用分变更的唯一入口 */
    @Autowired
    private CreditService creditService;

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

    // ==========================================================================
    // 下面两个方法是【留给你的练习】—— 我一个字没写，只给了提示
    // ==========================================================================

    /**
     * 【功能A】变更用户角色：普通用户 ↔ 跑腿员（跑腿员认证 / 取消资格）
     */
    @Override
    public void updateRole(Long id, Integer role) {
        // ================= 你的部分：填空 A1 ~ A4 =================
        //
        // 【A1】角色只能是 0 或 1
        //   if (!UserConstant.ROLE_NORMAL.equals(role) && !UserConstant.ROLE_RUNNER.equals(role)) {
        //       throw new BaseException("角色不合法（0普通用户 1跑腿员）");
        //   }
        if(!UserConstant.ROLE_NORMAL.equals(role) && !UserConstant.ROLE_RUNNER.equals(role)) {
            throw new BaseException("角色不合法（0普通用户 1跑腿员）");
        }
        // 【A2】用户必须存在
        //   User user = userMapper.getById(id);
        //   · user == null → throw new BaseException("用户不存在")
        User user = userMapper.getById(id);
        if(user == null) {
            throw new BaseException("用户不存在");
        }
        // 【A3】幂等：他已经是这个角色了就直接返回（打条 info 日志即可）
        //   提示：if (role.equals(user.getRole())) { log.info(...); return; }
        if(role.equals(user.getRole())){
            log.info("用户角色未变化，跳过更新：userId={}, role={}", id, role);
            return;
        }
        // 【A4】⭐ 关键守卫：要把他【降级】成普通用户之前，
        //      必须先确认他手上没有"进行中"的跑腿单
        //   if (UserConstant.ROLE_NORMAL.equals(role)) {
        //       int ongoing = deliveryOrderMapper.countOngoingByRunner(id);
        //       if (ongoing > 0) {
        //           throw new BaseException("该跑腿员还有 " + ongoing + " 个进行中的跑腿单，不能取消资格");
        //       }
        //   }
        //   ⚠️ 为什么这条最重要：跑腿单是"别人在等"的业务 ——
        //      你把他降级了，那几单就没人送了，买家干等、卖家也白等。
        //      **改一个状态之前，先问一句"现在还有谁在依赖这个状态"。**
        if(UserConstant.ROLE_NORMAL.equals(role)) {
            int ongoing = deliveryOrderMapper.countOngoingByRunner(id);
            if(ongoing > 0) {
                throw new BaseException("该跑腿员还有 " + ongoing + " 个进行中的跑腿单，不能取消资格");
            }
        }
        // 最后两步（照 updateStatus 的写法）：
        //   userMapper.updateRole(id, role);
        //   log.warn("管理员变更用户角色：userId={}, nickname={}, {} → {}",
        //           id, user.getNickname(), UserConstant.roleName(user.getRole()),
        //           UserConstant.roleName(role));
        //   （角色没做缓存，所以不需要像封禁那样清 Redis）
        //
        // =======================================================
        userMapper.updateRole(id,role);
        log.warn("管理员变更用户角色：userId={}, nickname={}, {} → {}",
                id, user.getNickname(), UserConstant.roleName(user.getRole()),
                UserConstant.roleName(role));
    }

    /**
     * 【功能B】管理员手动调整信用分
     */
    @Override
    public void adjustCredit(CreditAdjustDTO adjustDTO) {
        // ================= 你的部分：填空 B1 ~ B4 =================
        //
        // 【B1】参数校验（三段）
        //   · adjustDTO.getUserId() == null → "用户id不能为空"
        //   · adjustDTO.getChangeScore() == null
        //     || adjustDTO.getChangeScore() == 0        → "调整分值不能为空且不能为 0"
        //   · adjustDTO.getRemark() == null
        //     || adjustDTO.getRemark().trim().isEmpty()  → "必须填写调整理由"
        //   ⚠️ 第二个判断别写成基本类型的 ==（Integer 拆箱会 NPE），
        //      先判 null，再判等于 0。
        //   理由必填是审计要求：信用分是奖励/处罚，必须能说清"为什么"。
        // ┌── 【错题标记 #27】三个校验合成一句话，用户不知道到底错在哪 ─────────
        // │ 你当时写的：把"userId 为空 / 分值为空或 0 / 理由为空"合成一个 if，
        // │             抛一句"用户id不能为空、调整分值不能为空且不能为 0、必须填写调整理由"
        // │ 为什么不好：这条提示像"三条规定一次性念完"——
        // │   管理员其实只漏填了理由，却看到开头是"用户id不能为空"，得自己挨个排查；
        // │   前端也没法针对出错的那一个输入框做高亮。
        // │ 正确做法：一个条件配一句提示，错误信息要【精确指向】出问题的那一项。
        // │ 记法：校验"合并"只在代码紧凑度上有意义，**提示文案永远要一对一**。
        // │      （顺便：`changeScore == 0` 是 Integer 与 int 比较会自动拆箱，
        // │        这里因为前面已经判了 null、且 || 短路，所以是安全的 ✓）
        // └──────────────────────────────────────────────────────────────
        if (adjustDTO.getUserId() == null) {
            throw new BaseException("用户id不能为空");
        }
        if (adjustDTO.getChangeScore() == null || adjustDTO.getChangeScore() == 0) {
            throw new BaseException("调整分值不能为空且不能为 0");
        }
        if (adjustDTO.getRemark() == null || adjustDTO.getRemark().trim().isEmpty()) {
            throw new BaseException("必须填写调整理由");
        }
        //
        // 【B2】用户必须存在
        //   userMapper.getById(adjustDTO.getUserId()) == null → "用户不存在"
        User user = userMapper.getById(adjustDTO.getUserId());
        if(user == null) {
            throw new BaseException("用户不存在");
        }
        // 【B3】⭐ 幅度限制：单次调整不能超过 ±20 分
        //   if (Math.abs(adjustDTO.getChangeScore()) > 20) {
        //       throw new BaseException("单次调整幅度不能超过 ±20 分");
        //   }
        //   ⚠️ 为什么要限制：我们之前聊过"固定分值 vs 管理员裁量"——
        //      完全放开裁量权的风险是"关系好就加 100、看不顺眼就扣光"。
        //      给个上限，是【用规则约束管理员】，同时保留人工干预的灵活性。
        if(Math.abs(adjustDTO.getChangeScore()) > 20){
            throw new BaseException("单次调整幅度不能超过 ±20 分");
        }
        // 【B4】⭐ 走 CreditService 唯一入口（千万别直接 update user.credit_score！）
        //   creditService.changeCredit(
        //           adjustDTO.getUserId(),
        //           adjustDTO.getChangeScore(),
        //           CreditConstant.REASON_PLATFORM_TASK,        // 原因类型 8「平台任务」
        //           null,                                        // 没有关联业务单据
        //           "管理员(id=" + BaseContext.getCurrentId() + ")手动调整：" + adjustDTO.getRemark());
        //   ⚠️ 为什么必须走唯一入口：直接改 user 表的话 credit_log 里没记录 →
        //      流水和总分对不上 → "每日加分上限"和"扣分溯源"全部失效。
        //   （每日加分上限、分数下限这些边界 CreditService 里已经兜住了，不用重复写；
        //     加分被上限截断、被下限拦住时，changeCredit 会返回 0 —— 可以打条日志说明一下）
        //
        // =======================================================
        creditService.changeCredit(
                adjustDTO.getUserId(),
                adjustDTO.getChangeScore(),
                CreditConstant.REASON_PLATFORM_TASK,
                null,
                "管理员(id=" + BaseContext.getCurrentId() + ")手动调整：" + adjustDTO.getRemark()
        );
        log.warn("管理员手动调整用户分：userId={}, changeScore={}, remark={}",
                adjustDTO.getUserId(), adjustDTO.getChangeScore(), adjustDTO.getRemark());
    }

}
