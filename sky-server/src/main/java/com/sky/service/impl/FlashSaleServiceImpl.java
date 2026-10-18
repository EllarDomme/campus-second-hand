package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.ProductConstant;
import com.sky.context.BaseContext;
import com.sky.entity.FlashSaleRecord;
import com.sky.entity.Product;
import com.sky.entity.TradeOrder;
import com.sky.exception.BaseException;
import com.sky.mapper.FlashSaleRecordMapper;
import com.sky.mapper.ProductMapper;
import com.sky.service.FlashSaleService;
import com.sky.service.TradeOrderService;
import com.sky.utils.FlashSaleStockUtil;
import com.sky.vo.FlashSaleAdminVO;
import com.sky.vo.FlashSaleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class FlashSaleServiceImpl implements FlashSaleService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private FlashSaleRecordMapper flashSaleRecordMapper;

    @Autowired
    private FlashSaleStockUtil stockUtil;

    @Autowired
    private TradeOrderService tradeOrderService;

    /**
     * 捡漏专区列表（骨架已写好，不用改）
     */
    @Override
    public List<FlashSaleVO> listFlashSale() {
        List<FlashSaleVO> list = productMapper.listFlashSale();
        LocalDateTime now = LocalDateTime.now();
        for (FlashSaleVO vo : list) {
            vo.setFlashStatus(calcFlashStatus(vo, now));
        }
        return list;
    }

    /**
     * 算抢购状态：0未开始 1抢购中 2已抢光
     *
     * 【填空 1】
     *   ① 还没开始（flashStartTime 不为 null 且 now 比它早）→ 返回 0
     *   ② 已经开始了：商品还在售（ProductConstant.ON_SALE）→ 返回 1（抢购中）
     *      否则（状态变成"交易中"，说明被抢走了）→ 返回 2（已抢光）
     */
    private Integer calcFlashStatus(FlashSaleVO vo, LocalDateTime now) {
        // ① 还没开始：有开始时间、且现在还没到
        if (vo.getFlashStartTime() != null && vo.getFlashStartTime().isAfter(now)) {
            return 0;
        }
        // ② 已开始：商品还在售 → 抢购中
        // ┌── 【错题标记 #1】Integer 不能用 == 比较 ──────────────────────
        // │ 你当时写的：if (vo.getStatus() == ProductConstant.ON_SALE)
        // │ 为什么错：== 比的是"是不是同一个对象"，不是"值相不相等"。
        // │           状态码 0 正好落在 Integer 缓存(-128~127)里，所以侥幸能跑对；
        // │           一旦值超出这个范围，就会突然判错，这类 bug 极难排查。
        // │ 正确写法：常量放前面 + equals —— 顺手还能防 status 为 null 时的 NPE
        // └──────────────────────────────────────────────────────────────
        if (ProductConstant.ON_SALE.equals(vo.getStatus())) {
            return 1;
        }
        // ③ 其余情况（状态已是"交易中"，说明被抢走了）→ 已抢光
        return 2;
    }

    /**
     * 抢购
     *
     * 执行顺序很重要：**先做便宜的检查（校验/查重/Redis），再做贵的数据库操作**
     * —— 高并发下，绝大部分请求会在前面几步就被挡掉，根本碰不到数据库。
     */
    @Transactional
    @Override
    public void grab(Long productId) {
        Long me = BaseContext.getCurrentId();

        // ① 查商品 + 不存在校验（照抄）
        Product product = productMapper.getById(productId);
        if (product == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }

        // ② 【填空 2】四个守卫（照抄议价/下单的套路，注意全用常量在前）：
        //    - 必须是捡漏商品：product.getIsFlashSale() 与 ProductConstant.FLASH_SALE 比较
        //      失败："这件商品不是限时捡漏商品"
        //    - 必须已过审：ProductConstant.AUDIT_PASSED
        //      失败："商品还未通过审核，暂时不能抢购"
        //    - 必须仍在售：ProductConstant.ON_SALE
        //      失败："手慢了，这件商品已经被抢走了"
        //    - 不能抢自己发布的：me.equals(product.getSellerId())
        //      失败："不能抢自己发布的商品"
        //    ← 你写
        if(!ProductConstant.FLASH_SALE.equals(product.getIsFlashSale())) {
            throw new BaseException("这件商品不是限时捡漏商品");
        }
        if(!ProductConstant.AUDIT_PASSED.equals(product.getAuditStatus())) {
            throw new BaseException("商品还未通过审核，暂时不能抢购");
        }
        if(!ProductConstant.ON_SALE.equals(product.getStatus())) {
            throw new BaseException("手慢了，这件商品已经被抢走了");
        }
        if(me.equals(product.getSellerId())){
            throw new BaseException("不能抢自己发布的商品");
        }

        // ③ 抢购时间校验：flashStartTime 为 null 视为"立即开始"
        // ┌── 【错题标记 #2】调用方法前没判空，踩 NPE ────────────────────
        // │ 你当时写的：if (LocalDateTime.now().isBefore(product.getFlashStartTime()))
        // │ 为什么错：flash_start_time 建表时是 DEFAULT NULL，可能为空；
        // │           空值上调用 isBefore → NullPointerException → 接口 500
        // │ 改法：用 && 短路。为 null 就视为"立即开始"，不拦
        // │       （这是之前讲过的 fail-open：数据缺失时选择"不阻止"）
        // └──────────────────────────────────────────────────────────────
        if (product.getFlashStartTime() != null
                && LocalDateTime.now().isBefore(product.getFlashStartTime())) {
            throw new BaseException("抢购还未开始，请稍后再来");
        }

        // ④ 友好提示：是否已经抢到过（唯一索引才是并发兜底，这里只是给用户的提示）
        if (flashSaleRecordMapper.countSuccess(productId, me) > 0) {
            throw new BaseException("你已经抢到这件商品了");
        }

        // ⑤ 【填空 4】Redis 预扣库存（二手商品库存 = 1）
        //    提示：stockUtil.deduct(productId, 1)；返回 false → 抛 "手慢了，这件商品已经被抢光了"
        //    ← 你写
        boolean isSuccess = stockUtil.deduct(productId,1);
        if(!isSuccess){
            throw new BaseException("手慢了，这件商品已经被抢光了");
        }

        // ⑥ 生成订单 + 写抢购记录（任何一步失败都要补偿 Redis，并让数据库事务回滚）
        TradeOrder order;
        try {
            // 5) 生成订单：内部会【锁定商品】（条件更新，失败抛"手慢了，商品已被别人拍下"）
            order = tradeOrderService.createFlashSaleOrder(product, me, product.getPrice());

            // 6) 写抢购记录（is_success=1 + 回填订单id）
            //    并发下重复插入会撞唯一索引 uk_product_user → DuplicateKeyException
            // ┌── 【错题标记 #3】builder 漏字段：编译能过，运行时才炸 ─────────
            // │ 你当时写的：builder 里只写了 isSuccess / orderId / createTime / updateTime
            // │ 为什么错：漏了 productId 和 userId，而这两列在数据库里是 NOT NULL
            // │           → 运行时报 "Column 'product_id' cannot be null"
            // │ 教训：builder 少写字段【编译器完全不管】。
            // │       写完 insert 要对着表结构把字段核一遍。
            // └──────────────────────────────────────────────────────────────
            try {
                flashSaleRecordMapper.insert(FlashSaleRecord.builder()
                        .productId(productId)
                        .userId(me)
                        .isSuccess(1)
                        .orderId(order.getId())
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build());
            } catch (DuplicateKeyException e) {
                // 撞唯一索引 = 这个用户已经抢到过了（唯一索引是并发下的兜底防线）
                throw new BaseException("你已经抢到这件商品了");
            }

        } catch (Exception e) {
            // 7) 补偿：数据库这边失败了，把刚才在 Redis 扣掉的库存加回去
            //    补偿方向"宁可偏高"：
            //      Redis 说"还有货" → 安全的错（下一步会被数据库拦下，用户只是白点一次）
            //      Redis 说"没货了" → 灾难（商品被永久锁死，谁也抢不到）
            // ┌── 【错题标记 #5】补偿传错了参数：改的是别的商品的库存 ─────────
            // │ 你当时写的：stockUtil.restore((long)1)   ← 传的是字面量 1！
            // │ 为什么错：这样会把"1 号商品"的库存加回去，而当前抢的是另一件。
            // │           编译完全能过（类型正好是 long），所以非常隐蔽 ——
            // │           只有跑起来看数据才发现"抢购失败后，库存没回来"。
            // │ 改法：传当前这件商品的 id
            // └──────────────────────────────────────────────────────────────
            stockUtil.restore(productId);
            // ┌── 【错题标记 #4】catch 里忘了 throw e（最严重的一个）──────────
            // │ 你当时写的：catch 里只做了补偿，没有把异常抛出去
            // │ 后果一：就是你在 order.getId() 上看到的爆红
            // │         原因：catch 把异常吞了以后，编译器认为"执行可能正常走到 try-catch 之后"，
            // │               而那时 order 还没被赋值 → "变量可能未初始化"
            // │ 后果二（更严重）：@Transactional 收不到异常 → MySQL 不回滚
            // │         → 半成品数据（订单/记录）留在库里
            // │ 记法：补偿之后必须把异常"接着抛出去"，catch 绝不能吞异常
            // └──────────────────────────────────────────────────────────────
            throw e;
        }

        log.info("抢购成功：productId={}, userId={}, orderId={}", productId, me, order.getId());
    }

    /**
     * 我的抢购记录（骨架已写好，不用改）
     */
    @Override
    public List<FlashSaleRecord> listMyRecord() {
        return flashSaleRecordMapper.listMySuccess(BaseContext.getCurrentId());
    }

    // ==========================================================================
    // 管理端-捡漏活动管理（S4-3）
    //   listForAdmin 已写好；下面两个方法是【留给你的练习】
    // ==========================================================================

    /**
     * 【管理端】捡漏活动列表（已写好）
     *
     * SQL 只查数据库，Redis 库存要单独补 —— 因为库存本来就不在数据库里（这也是"两套系统"的体现）。
     */
    @Override
    public List<FlashSaleAdminVO> listForAdmin() {
        List<FlashSaleAdminVO> list = productMapper.listFlashSaleForAdmin();
        for (FlashSaleAdminVO vo : list) {
            vo.setRedisStock(stockUtil.getStock(vo.getProductId()));
            vo.setStatusName(productStatusName(vo.getStatus()));
        }
        return list;
    }

    /**
     * 【管理端】重置某件捡漏商品的 Redis 库存
     */
    @Override
    public void resetStock(Long productId) {
        // ================= 你的部分：填空 A1 ~ A4 =================
        //
        // 【A1】商品必须存在
        //   Product product = productMapper.getById(productId);
        //   · product == null → throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND)
        Product product = productMapper.getById(productId);
        if(product == null){
            // 提示语要写"发生了什么"（商品不存在），不要写"应该怎样"（商品必须存在）
            // —— 后者读起来像需求文档，用户看到会愣一下
            throw new BaseException("商品不存在");
        }
        // 【A2】必须是捡漏商品
        //   · !ProductConstant.FLASH_SALE.equals(product.getIsFlashSale())
        //        → throw new BaseException("该商品不是捡漏商品")
        if(!ProductConstant.FLASH_SALE.equals(product.getIsFlashSale())){
            throw new BaseException("该商品不是捡漏商品");
        }
        // 【A3】⭐ 关键守卫：商品必须还在"在售"状态
        //   · !ProductConstant.ON_SALE.equals(product.getStatus())
        //        → throw new BaseException("商品当前不是'在售'状态（可能已被抢走或下架），重置库存没有意义")
        //   ⚠️ 为什么：给一件已经卖掉的商品"重置库存"，等于让 Redis 说"还有货"，
        //      而数据库里它是"交易中" —— 虽然数据库的状态校验会兜住（第①步就拦了），
        //      但那属于"靠别人兜底"。**库存应该自己符合事实**，别指望下游替你擦屁股。
        if(!ProductConstant.ON_SALE.equals(product.getStatus())){
            throw new BaseException("商品不是在售状态（可能是已经被抢走或者下架）");
        }
        // 【A4】清掉库存 key（下次抢购会按初始库存重建）+ 留痕日志
        //   stockUtil.clear(productId);
        //   log.warn("管理端重置捡漏库存：productId={}, title={}", productId, product.getTitle());
        //
        // =======================================================
        stockUtil.clear(productId);
        log.warn("管理端重置捡漏库存：productId={},title={}",productId,product.getTitle());
    }

    /**
     * 【管理端】提前结束捡漏活动
     */
    @Override
    public void endActivity(Long productId) {
        // ================= 你的部分：填空 B1 ~ B4 =================
        //
        // 【B1】商品必须存在（同 A1）
        Product product = productMapper.getById(productId);
        if(product == null){
            throw new BaseException("商品不存在");
        }
        // 【B2】必须是捡漏商品（同 A2）
        //   ⚠️ 为什么这里也要判：如果他本来就是普通商品，你还"结束活动"，
        //      会让人误以为这个操作有别的副作用，语义不清。
        // ┌── 【错题标记 #29】Integer 用 == 比较（第 5 次了！）─────────────────
        // │ 你当时写的：if (!(product.getIsFlashSale() == 1))
        // │ 为什么错：getIsFlashSale() 是 Integer，和字面量 1 用 == 比较会【自动拆箱】，
        // │   而且它和常量 1 比的是值（拆箱后），虽然这次能跑对，
        // │   但——它依赖"拆箱成 int 再比"这个隐含行为，一旦哪天字段变成 Long/null 就崩。
        // │   更要命的是：你写到第 5 次了（抢购状态 → 议价 → 跑腿取消 → 投诉 → 现在）。
        // │ 改法：常量在前 + equals，和你在 A2 里写的一模一样：
        // │   !ProductConstant.FLASH_SALE.equals(product.getIsFlashSale())
        // │ 强记：**看到包装类型用 == / != / 和数字比，一律换成 equals 或先拆箱判空。**
        // └──────────────────────────────────────────────────────────────
        if(!ProductConstant.FLASH_SALE.equals(product.getIsFlashSale())){
            throw new BaseException("该商品本来就是普通商品，不需要结束");
        }
        // 【B3】把 is_flash_sale 改回 0（变回普通商品）
        //   productMapper.updateById(Product.builder()
        //           .id(productId)
        //           .isFlashSale(0)
        //           .updateTime(LocalDateTime.now())
        //           .build());
        //   提示：updateById 是【动态更新】，只会改你传了值的字段，
        //        不会把标题、价格、状态这些字段一起刷掉 ✓
        productMapper.updateById(Product.builder()
                .id(productId)
                .isFlashSale(0)
                .updateTime(LocalDateTime.now())
                .build());
        // 【B4】清掉 Redis 库存 + 留痕日志
        //   stockUtil.clear(productId);
        //   log.warn("管理端结束捡漏活动：productId={}, title={}", productId, product.getTitle());
        //   ⚠️ 注意：如果这件商品已经被抢走了（status = 交易中），
        //      结束活动【不影响已有的订单】—— 活动归活动、订单归订单，两者别混。
        //
        // =======================================================
        stockUtil.clear(productId);
        // 管理员操作必须留痕（含操作内容，便于事后审计"谁在什么时候结束了哪场活动"）
        log.warn("管理端结束捡漏活动：productId={}, title={}（已变回普通商品并清空库存）",
                productId, product.getTitle());
    }

    /** 商品状态 → 中文（管理端列表展示用） */
    private String productStatusName(Integer status) {
        if (ProductConstant.ON_SALE.equals(status)) {
            return "在售";
        }
        if (ProductConstant.IN_TRADE.equals(status)) {
            return "交易中";
        }
        if (ProductConstant.SOLD_OUT.equals(status)) {
            return "已售罄";
        }
        return "已下架";
    }

}
