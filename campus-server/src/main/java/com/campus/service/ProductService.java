package com.campus.service;

import com.campus.dto.ProductDTO;
import com.campus.dto.ProductPageQueryDTO;
import com.campus.result.PageResult;
import com.campus.vo.ProductVO;

import java.util.List;

/**
 * 商品服务（校园二手）
 *
 * 端点职责划分：
 *   卖家（用户端）：发布、编辑、上下架、删除、查看"我发布的"
 *   管理员（管理端）：分页审核列表、审核、查看详情、删除违规商品
 */
public interface ProductService {

    /** 卖家发布商品（发布后为待审核状态） */
    void save(ProductDTO productDTO);

    /** 卖家编辑自己的商品（编辑后重新回到待审核） */
    void update(ProductDTO productDTO);

    /** 卖家上下架自己的商品 */
    void updateStatus(Integer status, Long id);

    /** 卖家删除自己的商品 */
    void deleteBySeller(Long id);

    /** 管理员删除违规商品（批量） */
    void deleteByIds(List<Long> ids);

    /** 分页查询（管理端审核列表用，条件由调用方给定） */
    PageResult page(ProductPageQueryDTO productPageQueryDTO);

    /** 卖家查看"我发布的"（服务端强制只查当前登录用户） */
    PageResult pageMine(ProductPageQueryDTO productPageQueryDTO);

    /** 商品详情 */
    ProductVO getById(Long id);

    /** 用户端按分类浏览：只返回"审核通过 + 在售"的商品 */
    List<ProductVO> listByCategoryId(Long categoryId);

    /** 管理员审核商品（通过 / 驳回，驳回必须填原因） */
    void audit(Long id, Integer auditStatus, String auditNote);

}
