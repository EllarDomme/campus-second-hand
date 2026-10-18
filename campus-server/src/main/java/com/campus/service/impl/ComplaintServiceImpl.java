package com.campus.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.campus.constant.ComplaintConstant;
import com.campus.constant.CreditConstant;
import com.campus.constant.MessageConstant;
import com.campus.context.BaseContext;
import com.campus.dto.ComplaintHandleDTO;
import com.campus.dto.ComplaintSubmitDTO;
import com.campus.entity.Complaint;
import com.campus.entity.Product;
import com.campus.entity.TradeOrder;
import com.campus.exception.BaseException;
import com.campus.mapper.ComplaintMapper;
import com.campus.mapper.ProductMapper;
import com.campus.mapper.TradeOrderMapper;
import com.campus.result.PageResult;
import com.campus.service.ComplaintService;
import com.campus.service.CreditService;
import com.campus.vo.ComplaintVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投诉举报服务实现
 *
 * ==========================================================================
 * 【你的任务】把两个方法的【填空】写完（共 8 个）：
 *   submit（提交投诉）—— 填空 1~4
 *   handle（管理员处理）—— 填空 5~8
 *
 *   已替你写好（不用改）：myComplaints / detail / pageForAdmin / fillNames
 * ==========================================================================
 */
@Service
@Slf4j
public class ComplaintServiceImpl implements ComplaintService {

    @Autowired
    private ComplaintMapper complaintMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    /** 投诉成立要扣信用分（S3-10a 做的唯一入口） */
    @Autowired
    private CreditService creditService;

    /**
     * 提交投诉/举报
     *
     * 核心是"按类型分派"：两种工单要填的字段不同、被投诉人的推导方式也不同。
     */
    @Transactional
    @Override
    public Long submit(ComplaintSubmitDTO submitDTO) {
        Long me = BaseContext.getCurrentId();

        // ================= 你的部分：填空 1 ~ 4 =================
        //
        // 【填空 1】参数校验
        //   · complaintType == null → "投诉类型不能为空"
        //   · 类型既不是 0 也不是 1    → "投诉类型不合法"
        //     （提示：ComplaintConstant.TYPE_PRODUCT_REPORT / TYPE_TRADE_DISPUTE）
        //   · complaintReason == null → "投诉原因不能为空"
        //   · content 为 null 或 trim 后为空 → "投诉内容不能为空"
        //   · content.trim().length() > ComplaintConstant.MAX_CONTENT_LENGTH
        //                                   → "投诉内容太长了（最多 2000 字）"
        //   · images 不为 null 且 length > ComplaintConstant.MAX_IMAGES_LENGTH
        //                                   → "凭证图片太多了"
        // ┌── 【错题标记 #22】判空判错了对象 + 顺手埋了个 NPE ──────────────────
        // │ 你当时写的：if (submitDTO == null) throw "投诉类型不能为空";
        // │             if (submitDTO.getComplaintType() != 0 && ... != 1) throw "投诉类型不合法";
        // │ 两个问题：
        // │   ① 该判的是 complaintType，你判成了整个 submitDTO —— 提示语和实际检查对不上，
        // │      而真正的 complaintType == null 反而没拦住
        // │   ② getComplaintType() 是 Integer，跟字面量 0 做 != 比较会【自动拆箱】，
        // │      complaintType 为 null 时直接 NullPointerException → 接口 500，
        // │      用户看到的是"系统错误"而不是"投诉类型不能为空"
        // │ 另外：别写死 0/1，用 ComplaintConstant 里的常量 —— 将来加类型时不用全库搜数字
        // └──────────────────────────────────────────────────────────────
        if (submitDTO == null) {
            throw new BaseException("请求参数不能为空");
        }
        if (submitDTO.getComplaintType() == null) {
            throw new BaseException("投诉类型不能为空");
        }
        if (!ComplaintConstant.TYPE_PRODUCT_REPORT.equals(submitDTO.getComplaintType())
                && !ComplaintConstant.TYPE_TRADE_DISPUTE.equals(submitDTO.getComplaintType())) {
            throw new BaseException("投诉类型不合法");
        }
        if(submitDTO.getComplaintReason() == null){
            throw new BaseException("投诉原因不能为空");
        }
        if(submitDTO.getContent() == null || submitDTO.getContent().trim().isEmpty()){
            throw new BaseException("投诉内容不能为空");
        }
        if(submitDTO.getContent().trim().length() > ComplaintConstant.MAX_CONTENT_LENGTH){
            throw new BaseException("投诉内容太长了（最多 " + ComplaintConstant.MAX_CONTENT_LENGTH + " 字）");
        }
        if(submitDTO.getImages() != null && submitDTO.getImages().length() > ComplaintConstant.MAX_IMAGES_LENGTH){
            throw new BaseException("凭证图片太多了");
        }
        // 【填空 2】按类型确定"被投诉人"和"关联对象"（这是本方法的关键分支）
        //
        //   用 if / else 分两路，两路的公共变量先声明在 if 外面：
        //       Long productId; Long orderId = null; Long respondentUserId;
        //
        //   ── 商品举报（TYPE_PRODUCT_REPORT）──
        //     · submitDTO.getProductId() == null → "商品举报必须指定商品"
        //     · Product product = productMapper.getById(submitDTO.getProductId());
        //        product == null → throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND)
        //     · me.equals(product.getSellerId()) → "不能举报自己的商品"（允许法思路：堵住自举报）
        //     · productId = product.getId();
        //       respondentUserId = product.getSellerId();     ← 被投诉人 = 卖家
        // ┌── 【错题标记 #23】两个分支写成了"并列"，不是"二选一" 🔴致命 ──────
        // │ 你当时写的：先 if (productId == null) throw "商品举报必须指定商品";
        // │             紧接着 if (orderId == null) throw "交易纠纷必须指定订单";
        // │             —— 两段都【无条件】执行，等于要求任何投诉都同时填商品和订单。
        // │ 后果：商品举报（本来不需要订单）提交不了；交易纠纷（不需要指定商品）也提交不了。
        // │      **两种投诉全都用不了** —— 这整块功能直接死掉。
        // │ 正确做法：用 if / else if 让两条路【互斥】。
        // │ 记法：只要"两种类型各自的必填项不同"，就一定是 if/else 结构；
        // │      写成两段并列的 if，等于把两边的必填项叠加到了一起。
        // └──────────────────────────────────────────────────────────────
        Long productId;
        Long orderId = null;
        Long respondentUserId;

        if (ComplaintConstant.TYPE_PRODUCT_REPORT.equals(submitDTO.getComplaintType())) {
            // 商品举报：只认商品，被投诉人 = 卖家
            if(submitDTO.getProductId() == null){
                throw new BaseException("商品举报必须指定商品");
            }
            Product product = productMapper.getById(submitDTO.getProductId());
            if(product == null){
                throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
            }
            if(me.equals(product.getSellerId())){
                throw new BaseException("不能举报自己的商品");
            }
            productId = product.getId();
            respondentUserId = product.getSellerId();
        } else {
            // 交易纠纷：只认订单，被投诉人 = 交易对手
            if(submitDTO.getOrderId() == null){
                throw new BaseException("交易纠纷必须指定订单");
            }
            TradeOrder order = tradeOrderMapper.getById(submitDTO.getOrderId());
            if(order == null){
                throw new BaseException("订单不存在");
            }
            if(!me.equals(order.getBuyerId()) && !me.equals(order.getSellerId())){
                throw new BaseException("无权对该订单发起纠纷");
            }
            orderId = order.getId();
            productId = order.getProductId();
            // ┌── 【错题标记 #24】交易纠纷分支漏了给"被投诉人"赋值 ──────────────
            // │ 你当时写的：这一路只算了 orderId 和 productId，
            // │             respondentUserId 还是上一路留下的值（通常是 null）。
            // │ 后果：工单落库时被投诉人是 NULL → 后面"投诉成立扣谁的分"就扣不动
            // │       （CreditService 收到 null userId 会直接 return 0，一声不响）。
            // │ 记法：分支里各自要用的【输出变量】，写完对一遍"每一条路都给它赋值了吗"。
            // └──────────────────────────────────────────────────────────────
            respondentUserId = me.equals(order.getBuyerId())
                    ? order.getSellerId() : order.getBuyerId();
        }


        // 【填空 3】查重：我对同一对象有没有【还没完结】的工单
        if(complaintMapper.countUnfinished(me,orderId,productId) > 0){
            throw new BaseException("你对该对象的投诉还在处理中，请耐心等待");
        }
        // ┌── 【错题标记 #25】build 完忘了 insert，直接 return getId() ────────
        // │ 你当时写的：Complaint complaint = Complaint.builder()...build();
        // │             return complaint.getId();
        // │ 后果：builder 只是"造了个对象"，没有落库 → getId() 是 null，
        // │       可接口照样返回 code=1（成功）—— 用户以为投诉提交了，
        // │       管理端列表里却一条都没有。**这类"静默失效"最难查**：
        // │       没有报错、没有异常，就是数据没进去。
        // │ 记法：写"造对象 → 存库 → 返回 id"这种三步流程时，
        // │      中间那一步（insert/update）最容易被眼睛跳过，检查时重点盯它。
        // └──────────────────────────────────────────────────────────────
        Complaint complaint = Complaint.builder()
                .productId(productId)
                .orderId(orderId)
                .complainantUserId(me)
                .respondentUserId(respondentUserId)
                .complaintType(submitDTO.getComplaintType())
                .complaintReason(submitDTO.getComplaintReason())
                .content(submitDTO.getContent().trim())
                .images(submitDTO.getImages())
                .handleStatus(ComplaintConstant.STATUS_PENDING)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        complaintMapper.insert(complaint);

        log.info("投诉已提交：complaintId={}, 类型={}, 原因={}, 被投诉人={}",
                complaint.getId(), ComplaintConstant.typeName(complaint.getComplaintType()),
                ComplaintConstant.reasonName(complaint.getComplaintReason()), respondentUserId);
        return complaint.getId();
    }

    /**
     * 【管理端】处理工单
     *
     * 一句话概括：**这是"投诉"和"信用分"两条线的接缝**。
     */
    @Transactional
    @Override
    public void handle(ComplaintHandleDTO handleDTO) {
        // ================= 你的部分：填空 5 ~ 8 =================
        //
        // 【填空 5】参数校验
        //   · handleDTO.getId() == null → "工单id不能为空"
        //   · handleResult 既不是 1（成立）也不是 2（不成立） → "处理结果不合法"
        //     （提示：ComplaintConstant.RESULT_UPHELD / RESULT_REJECTED）
        //   · handleNote 为 null 或 trim 后为空 → "必须填写处理说明"
        //     （备注会展示给双方看，空备注等于"没给理由就判了"，用户会不服）
        if(handleDTO.getId() == null){
            throw new BaseException("工单id不能为空");
        }
        if(handleDTO.getHandleResult() != ComplaintConstant.RESULT_UPHELD && handleDTO.getHandleResult() != ComplaintConstant.RESULT_REJECTED){
            throw new BaseException("处理结果不合法");
        }
        if(handleDTO.getHandleNote() == null || handleDTO.getHandleNote().trim().isEmpty()){
            throw new BaseException("必须填写处理说明");
        }
        // 【填空 6】查工单 + 状态校验
        //   Complaint complaint = complaintMapper.getById(handleDTO.getId());
        //   · complaint == null → "工单不存在"
        //   · ComplaintConstant.STATUS_DONE.equals(complaint.getHandleStatus())
        //        → "该工单已处理完毕"
        Complaint complaint = complaintMapper.getById(handleDTO.getId());
        if(complaint == null){
            throw new BaseException("工单不存在");
        }
        if(ComplaintConstant.STATUS_DONE.equals(complaint.getHandleStatus())){
            throw new BaseException("该工单已处理完毕");
        }
        // 【填空 7】条件更新（防两个管理员同时处理）
        //   int rows = complaintMapper.handleIfNotDone(
        //           handleDTO.getId(), handleDTO.getHandleResult(),
        //           handleDTO.getHandleNote(), LocalDateTime.now());
        //   · rows == 0 → "该工单已被其他管理员处理"
        //   ⚠️ 为什么必须先条件更新、再扣信用分：
        //      如果把扣分写在前面，两个管理员同时点"成立" → 扣两次 -10。
        //      条件更新只会有一个人 rows==1，扣分就跟着只发生一次。
        //      （顺序：**先抢到"处理权"，再做有副作用的操作**）
        int rows = complaintMapper.handleIfNotDone(handleDTO.getId(),handleDTO.getHandleResult(),handleDTO.getHandleNote(),LocalDateTime.now());
        if(rows == 0){
            throw new BaseException("该工单已被其他管理员处理");
        }

        // 【填空 8】⭐ 投诉成立 → 扣被投诉人信用分
        //   · 只有 handleResult == RESULT_UPHELD(1) 才扣
        //   · 调用 creditService.changeCredit(
        //           complaint.getRespondentUserId(),
        //           CreditConstant.SCORE_COMPLAINT_UPHELD,          // -10
        //           CreditConstant.REASON_COMPLAINT_UPHELD,          // 原因类型 6
        //           complaint.getId(),                               // refId 指向工单，便于溯源
        //           "投诉成立：" + ComplaintConstant.reasonName(complaint.getComplaintReason()));
        //   · 不成立就什么都不做（但建议打一条日志，方便排查"为什么没扣分"）
        //
        // =======================================================
        // ┌── 【错题标记 #26】Integer 又用 != 比较（第 4 次了）────────────────
        // │ 你当时写的：handleDTO.getHandleResult() != ComplaintConstant.RESULT_UPHELD
        // │ 为什么错：Integer 与 Integer 用 != 比的是【对象引用】，不是值。
        // │   1/2 落在 Integer 缓存(-128~127)里所以侥幸能跑对，
        // │   哪天给 handleResult 加个 200 之类的取值就会突然失效。
        // │ 这是本项目【第 4 次】同类问题：抢购状态 → 议价 → 跑腿取消 → 现在投诉。
        // │ 改法：常量在前 + equals：
        // │   ComplaintConstant.RESULT_UPHELD.equals(handleDTO.getHandleResult())
        // │ 强记一句：**只要看到两个包装类型用 == 或 !=，立刻换成 equals，没有例外。**
        // └──────────────────────────────────────────────────────────────
        if(ComplaintConstant.RESULT_UPHELD.equals(handleDTO.getHandleResult())){
            creditService.changeCredit(complaint.getRespondentUserId(),CreditConstant.SCORE_COMPLAINT_UPHELD,CreditConstant.REASON_COMPLAINT_UPHELD,complaint.getId(),"投诉成立：" + ComplaintConstant.reasonName(complaint.getComplaintReason()));
        }else{
            log.info("投诉不成立，不扣信用分：complaintId={}, 原因={}, 被投诉人={}",
                    complaint.getId(), ComplaintConstant.reasonName(complaint.getComplaintReason()),
                    complaint.getRespondentUserId());
        }
    }

    /** 我的投诉记录（已写好） */
    @Override
    public List<ComplaintVO> myComplaints() {
        List<ComplaintVO> list = complaintMapper.listMine(BaseContext.getCurrentId());
        list.forEach(this::fillNames);
        return list;
    }

    /** 工单详情（已写好，含权限校验） */
    @Override
    public ComplaintVO detail(Long id) {
        ComplaintVO vo = complaintMapper.getDetailById(id);
        if (vo == null) {
            throw new BaseException("工单不存在");
        }
        Long me = BaseContext.getCurrentId();
        // 只有投诉人和被投诉人能看（工单里有别人的纠纷内容，不能让第三人围观）
        boolean allowed = me.equals(vo.getComplainantUserId()) || me.equals(vo.getRespondentUserId());
        if (!allowed) {
            throw new BaseException("无权查看该工单");
        }
        fillNames(vo);
        return vo;
    }

    /** 【管理端】工单分页列表（已写好） */
    @Override
    public PageResult pageForAdmin(Integer handleStatus, Integer complaintType,
                                   Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        PageHelper.startPage(p, size);
        List<ComplaintVO> list = complaintMapper.listForAdmin(handleStatus, complaintType);
        Page<ComplaintVO> pageInfo = (Page<ComplaintVO>) list;
        list.forEach(this::fillNames);
        return new PageResult(pageInfo.getTotal(), list);
    }

    /** 把枚举值翻译成中文（已写好，省得前端维护一份映射表） */
    private void fillNames(ComplaintVO vo) {
        vo.setComplaintTypeName(ComplaintConstant.typeName(vo.getComplaintType()));
        vo.setComplaintReasonName(ComplaintConstant.reasonName(vo.getComplaintReason()));
        vo.setHandleStatusName(ComplaintConstant.statusName(vo.getHandleStatus()));
    }

}
