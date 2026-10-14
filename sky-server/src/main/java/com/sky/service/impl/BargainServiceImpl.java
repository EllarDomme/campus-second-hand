package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.BargainConstant;
import com.sky.constant.MessageConstant;
import com.sky.constant.ProductConstant;
import com.sky.context.BaseContext;
import com.sky.dto.BargainCounterDTO;
import com.sky.dto.BargainPageQueryDTO;
import com.sky.dto.BargainSubmitDTO;
import com.sky.entity.Bargain;
import com.sky.entity.Product;
import com.sky.exception.BaseException;
import com.sky.mapper.BargainMapper;
import com.sky.mapper.ProductMapper;
import com.sky.result.PageResult;
import com.sky.service.BargainService;
import com.sky.vo.BargainVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
public class BargainServiceImpl implements BargainService {

    @Autowired
    private BargainMapper bargainMapper;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 买家发起议价
     *
     * 结构提示：前面全是「守卫判断」（不满足就抛异常走开），最后一步才是落库。
     * 守卫条件的思维模式跟 timeoutClose 一样：先写清楚"什么情况下不该做这件事"。
     */
    @Transactional
    @Override
    public void submit(BargainSubmitDTO submitDTO) {
        Long buyerId = BaseContext.getCurrentId();

        // ① 查商品 + 不存在校验（照抄）
        Product product = productMapper.getById(submitDTO.getProductId());
        if (product == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }

        // ② 【填空 1】守卫：不能议价自己发布的商品
        //    提示：拿 product.getSellerId() 和 buyerId 比（注意 null 安全，常量/已知非空放前面）
        //    失败信息："不能议价自己发布的商品"
        //    ← 你写
        if(buyerId.equals(product.getSellerId())){
            throw new BaseException("不能议价自己发布的商品");
               }

        // ③ 【填空 2】守卫：商品必须「已过审」且「在售」
        //    提示：product.getAuditStatus() 用 ProductConstant.AUDIT_PASSED 比
        //         product.getStatus() 用 ProductConstant.ON_SALE 比
        //    失败信息：分别是 "商品还未通过审核，暂时不能议价" / "商品当前不可议价（可能已被拍下或已下架）"
        //    ← 你写
        if(!ProductConstant.AUDIT_PASSED.equals(product.getAuditStatus())){
            throw new BaseException("商品还未通过审核，暂时不能议价");
        }
        if(!ProductConstant.ON_SALE.equals(product.getStatus())){
            throw new BaseException("商品当前不可议价（可能已被拍下或已下架）");
        }

        // ④ 【填空 3】守卫：出价必须 ≥ 商品标价 × 50%
        //    提示：① 先判出价非空、且大于 0
        //         ② 下限 = product.getPrice().multiply(BargainConstant.MIN_OFFER_RATE)
        //         ③ ⚠️ BigDecimal 比较大小必须用 compareTo（用 equals 会因为"25.0 vs 25.00"这种精度差异出错）
        //    失败信息："出价不能为空" / "出价必须大于 0" / "出价不能低于标价的 50%"
        //    ← 你写
        // 先判空（必须在使用之前），再判断大小 —— 顺序不能反
        if (submitDTO.getBuyerOffer() == null) {
            throw new BaseException("出价不能为空");
        }
        if (submitDTO.getBuyerOffer().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException("出价必须大于 0");
        }
        // 下限用常量算，不写死 ÷2：
        //   业务规则（"不低于 50%"）只能有一个出处，改了常量这三处就一起变；
        //   写成 divide(2) 的话，哪天改成 60%，这段代码会静静地继续按 50% 算。
        BigDecimal minOffer = product.getPrice().multiply(BargainConstant.MIN_OFFER_RATE);
        if (submitDTO.getBuyerOffer().compareTo(minOffer) < 0) {
            throw new BaseException("出价不能低于标价的 50%");
        }

        // ⑤ 【填空 4】守卫：同一买家对同一商品不能有「进行中」的议价
        //    提示：用 bargainMapper.countInProgress(buyerId, product.getId())
        //    失败信息："你已经对这件商品出过价了，等对方回应吧"
        //    ← 你写
        int count = bargainMapper.countInProgress(buyerId, product.getId());
        if(count > 0){
            throw new BaseException("你已经对这件商品出过价了，等对方回应吧");
        }

        // ⑥ 【填空 5】守卫：被拒绝后 24 小时内不能再议
        //    提示：用 bargainMapper.getLastRejected(buyerId, product.getId())
        //         拿到的是最近一条"已拒绝"的记录（可能为 null）
        //         如果它的 updateTime 晚于"现在 - 24小时"，就拒绝
        //    失败信息："对方刚拒绝过你的议价，24 小时后可以再试"
        //    ← 你写
        Bargain lastRejected = bargainMapper.getLastRejected(buyerId, product.getId());

        if(lastRejected != null){
            // 注意用的是 REJECT_COOLDOWN_HOURS（拒后冷却期），不是 EXPIRE_HOURS（议价有效期）：
            // 两者碰巧都是 24 小时，但语义不同，将来只改其中一个时不能互相影响
            if(lastRejected.getUpdateTime().isAfter(LocalDateTime.now().minusHours(BargainConstant.REJECT_COOLDOWN_HOURS))){
                throw new BaseException("对方刚拒绝过你的议价，24 小时后可以再试");
            }
        }

        // ⑦ 落库（照抄）
        Bargain bargain = Bargain.builder()
                .sellerId(product.getSellerId())
                .buyerId(buyerId)
                .productId(product.getId())
                .buyerOffer(submitDTO.getBuyerOffer())
                .bargainStatus(BargainConstant.STATUS_BUYER_OFFER)
                .expireTime(LocalDateTime.now().plusHours(BargainConstant.EXPIRE_HOURS))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        bargainMapper.insert(bargain);

        log.info("议价发起成功：bargainId={}, buyerId={}, productId={}, 出价={}",
                bargain.getId(), buyerId, product.getId(), bargain.getBuyerOffer());
    }

    /**
     * 我的议价分页
     * 跟订单列表同一套路：view 决定查哪一边，用户 id 只从登录态取
     */
    @Override
    public PageResult pageQuery(BargainPageQueryDTO query) {
        // ① 【填空 6】先清空 buyerId / sellerId（挡前端伪造），再按 view 填当前用户 id
        //    提示：view=seller → setSellerId(我)；否则 → setBuyerId(我)
        //         常量放前面防 NPE
        //    ← 你写
        query.setBuyerId(null);
        query.setSellerId(null);

        Long userId = BaseContext.getCurrentId();

        if("seller".equals(query.getView())){
            query.setSellerId(userId);
        }else{
            query.setBuyerId(userId);
        }

        // ② 分页 + 查询 + 包装（照抄）
        PageHelper.startPage(query.getPage(), query.getPageSize());
        Page<BargainVO> page = bargainMapper.pageQuery(query);
        return new PageResult(page.getTotal(), page.getResult());
    }

    // ==================== 以下三个方法属于 S3-6b，下一轮再写 ====================

    @Override
    public void accept(Long id) {
        throw new UnsupportedOperationException("S3-6b 待实现：接受报价 → 议价成交并生成订单");
    }

    @Override
    public void reject(Long id) {
        throw new UnsupportedOperationException("S3-6b 待实现：拒绝报价");
    }

    @Override
    public void counter(BargainCounterDTO counterDTO) {
        throw new UnsupportedOperationException("S3-6b 待实现：卖家还价");
    }

}
