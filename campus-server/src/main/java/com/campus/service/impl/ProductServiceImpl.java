package com.campus.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.campus.constant.MessageConstant;
import com.campus.constant.ProductConstant;
import com.campus.context.BaseContext;
import com.campus.dto.ProductDTO;
import com.campus.dto.ProductPageQueryDTO;
import com.campus.entity.Product;
import com.campus.exception.BaseException;
import com.campus.mapper.ProductMapper;
import com.campus.result.PageResult;
import com.campus.service.ProductService;
import com.campus.vo.ProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    /**
     * 卖家发布商品
     */
    @Override
    public void save(ProductDTO productDTO) {
        Product product = new Product();
        BeanUtils.copyProperties(productDTO, product);

        // 卖家身份只能来自登录态。绝不能接受前端传的 sellerId，
        // 否则任何人都能冒用别人的身份发布商品（越权）。
        product.setSellerId(BaseContext.getCurrentId());

        // 设计决定：发布后一律先"待审核"，管理员审核通过才对外展示
        product.setAuditStatus(ProductConstant.AUDIT_PENDING);
        // 初始售卖状态：在售
        product.setStatus(ProductConstant.ON_SALE);

        normalizeFlashSaleFields(product);

        productMapper.insert(product);

        // @Options(useGeneratedKeys=true) 已把自增主键回填到 product.id
        log.info("商品发布成功：id={}, sellerId={}, 状态=待审核", product.getId(), product.getSellerId());
    }

    /**
     * 卖家编辑商品
     */
    @Override
    public void update(ProductDTO productDTO) {
        if (productDTO.getId() == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }
        Product exist = productMapper.getById(productDTO.getId());
        checkExistsAndOwner(exist);

        Product product = new Product();
        BeanUtils.copyProperties(productDTO, product);

        normalizeFlashSaleFields(product);

        // 重要业务规则：编辑内容后必须重新审核。
        // 否则可以"先发一个正常商品过审，拿到已通过后再改成违规内容"。
        product.setAuditStatus(ProductConstant.AUDIT_PENDING);

        productMapper.updateById(product);
        log.info("商品编辑成功：id={}，已回到待审核", product.getId());
    }

    /**
     * 卖家上下架
     */
    @Override
    public void updateStatus(Integer status, Long id) {
        // 只允许在"在售"和"已下架"之间切换。
        // "交易中/已售罄"由订单流程驱动，不能让卖家随手改回去。
        if (!ProductConstant.ON_SALE.equals(status) && !ProductConstant.OFF_SHELF.equals(status)) {
            throw new BaseException("售卖状态只能切换为「在售」或「已下架」");
        }
        Product exist = productMapper.getById(id);
        checkExistsAndOwner(exist);
        if (ProductConstant.IN_TRADE.equals(exist.getStatus())) {
            throw new BaseException(MessageConstant.PRODUCT_IN_TRADE);
        }

        Product product = Product.builder().id(id).status(status).build();
        productMapper.updateById(product);
    }

    /**
     * 卖家删除自己的商品
     */
    @Override
    public void deleteBySeller(Long id) {
        Product exist = productMapper.getById(id);
        checkExistsAndOwner(exist);
        if (ProductConstant.IN_TRADE.equals(exist.getStatus())) {
            throw new BaseException(MessageConstant.PRODUCT_IN_TRADE);
        }
        productMapper.deleteById(id);
    }

    /**
     * 管理员删除违规商品
     */
    @Transactional
    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 先整体校验，避免删一半才发现有商品在交易中
        for (Long id : ids) {
            Product exist = productMapper.getById(id);
            if (exist != null && ProductConstant.IN_TRADE.equals(exist.getStatus())) {
                throw new BaseException(MessageConstant.PRODUCT_IN_TRADE);
            }
        }
        for (Long id : ids) {
            productMapper.deleteById(id);
        }
    }

    /**
     * 分页查询（管理端）
     */
    @Override
    public PageResult page(ProductPageQueryDTO productPageQueryDTO) {
        PageHelper.startPage(productPageQueryDTO.getPage(), productPageQueryDTO.getPageSize());
        Page<ProductVO> page = productMapper.selectList(productPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 卖家查看"我发布的"
     */
    @Override
    public PageResult pageMine(ProductPageQueryDTO productPageQueryDTO) {
        // 强制覆盖为当前登录用户，忽略前端传的 sellerId，
        // 否则传入别人的 sellerId 就能看到别人"我发布的"列表
        productPageQueryDTO.setSellerId(BaseContext.getCurrentId());
        return page(productPageQueryDTO);
    }

    /**
     * 商品详情
     */
    @Override
    public ProductVO getById(Long id) {
        // 详情单独查（带分类名 / 浏览数 / 收藏数 / 卖家昵称）
        ProductVO productVO = productMapper.getDetailVO(id);
        if (productVO == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }
        // 埋点：浏览 +1。
        // 用一条 update 自增，不做"先查再写" —— 并发下后者会丢计数。
        // 返回给前端的是 +1 之后的值，否则用户看到的会是"我进来之前的数字"。
        productMapper.incrViewCount(id);
        productVO.setViewCount((productVO.getViewCount() == null ? 0 : productVO.getViewCount()) + 1);
        return productVO;
    }

    /**
     * 用户端按分类浏览商品
     */
    @Override
    public List<ProductVO> listByCategoryId(Long categoryId) {
        // 只展示「审核通过」且「在售」的商品：
        // 缺了 auditStatus 条件，待审核/被驳回的商品就会直接暴露给用户
        Product query = Product.builder()
                .categoryId(categoryId)
                .status(ProductConstant.ON_SALE)
                .auditStatus(ProductConstant.AUDIT_PASSED)
                .build();

        List<Product> products = productMapper.list(query);

        List<ProductVO> voList = new ArrayList<>();
        for (Product p : products) {
            ProductVO vo = new ProductVO();
            BeanUtils.copyProperties(p, vo);
            voList.add(vo);
        }
        return voList;
    }

    /**
     * 管理员审核商品
     */
    @Override
    public void audit(Long id, Integer auditStatus, String auditNote) {
        if (!ProductConstant.AUDIT_PASSED.equals(auditStatus)
                && !ProductConstant.AUDIT_REJECTED.equals(auditStatus)) {
            throw new BaseException(MessageConstant.PRODUCT_AUDIT_STATUS_ERROR);
        }
        Product exist = productMapper.getById(id);
        if (exist == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }
        if (ProductConstant.AUDIT_REJECTED.equals(auditStatus)
                && (auditNote == null || auditNote.trim().isEmpty())) {
            // 驳回必须说明原因，否则卖家不知道该改什么
            throw new BaseException("驳回商品必须填写原因");
        }

        Product product = Product.builder()
                .id(id)
                .auditStatus(auditStatus)
                .auditNote(auditNote)
                .auditTime(LocalDateTime.now())
                .build();
        productMapper.updateById(product);
        log.info("商品审核完成：id={}, auditStatus={}", id, auditStatus);
    }

    // ================= 私有方法 =================

    /**
     * 捡漏字段归一化：
     *   isFlashSale=1 → 必须有抢购时间
     *   isFlashSale≠1 → 一律按普通商品处理，并清空抢购时间
     * （否则库里会出现"普通商品带着抢购时间"这种自相矛盾的数据）
     */
    private void normalizeFlashSaleFields(Product product) {
        if (ProductConstant.FLASH_SALE.equals(product.getIsFlashSale())) {
            if (product.getFlashStartTime() == null) {
                throw new BaseException("限时捡漏商品必须设置抢购开始时间");
            }
        } else {
            product.setIsFlashSale(ProductConstant.NOT_FLASH_SALE);
            product.setFlashStartTime(null);
        }
    }

    /**
     * 校验商品存在，且属于当前登录用户
     */
    private void checkExistsAndOwner(Product exist) {
        if (exist == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null || !exist.getSellerId().equals(currentId)) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_BELONG_TO_YOU);
        }
    }

}
