package com.campus.mapper;

import com.github.pagehelper.Page;
import com.campus.annotation.AutoFill;
import com.campus.dto.ProductPageQueryDTO;
import com.campus.entity.Product;
import com.campus.enumeration.OperationType;
import com.campus.vo.FlashSaleAdminVO;
import com.campus.vo.FlashSaleVO;
import com.campus.vo.ProductVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper {

    /**
     * 新增商品
     *
     * useGeneratedKeys：把数据库生成的自增主键回填到 product.id（发布接口要把商品id返回给前端）
     * AutoFill：自动填充 createTime / updateTime。
     *           Product 实体没有 createUser / updateUser 字段，切面会自动跳过这两个，不会再抛
     *           NoSuchMethodException（这一处已在新版 AutoFillAspect 里做过容错）
     */
    @Insert("insert into product (seller_id, category_id, title, price, images, description, " +
            "condition_level, is_flash_sale, flash_start_time, audit_status, status, create_time, update_time) " +
            "values (#{sellerId}, #{categoryId}, #{title}, #{price}, #{images}, #{description}, " +
            "#{conditionLevel}, #{isFlashSale}, #{flashStartTime}, #{auditStatus}, #{status}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @AutoFill(value = OperationType.INSERT)
    void insert(Product product);

    /**
     * 分页查询商品（管理端审核列表、卖家"我发布的"共用；条件在 XML 里动态拼接）
     */
    Page<ProductVO> selectList(ProductPageQueryDTO productPageQueryDTO);

    @Select("select * from product where id = #{id}")
    Product getById(Long id);

    @Delete("delete from product where id = #{id}")
    void deleteById(Long id);

    /**
     * 更新商品（动态更新非空字段，条件在 XML 里）
     */
    @AutoFill(value = OperationType.UPDATE)
    void updateById(Product product);

    /**
     * 条件查询商品列表
     * 用户端按分类浏览时传 categoryId + status=在售 + auditStatus=已通过
     * 管理端按分类查询时只传 categoryId
     */
    List<Product> list(Product product);

    // ==========================================================================
    // 【公共逻辑 · 条件更新商品状态】全项目复用最多的一处（4 个调用点）
    // --------------------------------------------------------------------------
    //   TradeOrderServiceImpl.submit        在售(0) → 交易中(1)   下单锁商品
    //   TradeOrderServiceImpl.doCancelOrder 交易中(1) → 在售(0)   拒单/取消/超时 回退
    //   TradeOrderServiceImpl.complete      交易中(1) → 已售罄(2) 买家确认收货
    //
    //   ⚠️ 调用后【必须】判断影响行数：
    //        返回 1 = 改成功；返回 0 = 商品当前状态不是 fromStatus（比如已被别人拍下）
    //        返回 0 时应当抛异常 → 让整个事务回滚，避免"订单变了、商品没变"
    // ==========================================================================
    /**
     * 条件更新商品状态：只有当前状态等于 fromStatus 时才改成 toStatus，返回影响行数
     *
     * 为什么必须用条件更新，而不是"先查再改"：
     *   两个买家同时下单时，"先查再改"会让两人都查到"在售"，然后都改成"交易中"——
     *   结果一件商品卖出两份。
     *   条件更新把"判断"和"修改"合成一条 SQL，靠数据库行锁保证原子性：
     *   第一条 update 影响 1 行，第二条影响 0 行（因为 status 已经不是 fromStatus 了）。
     *   调用方只需判断返回值。
     */
    @Update("update product set status = #{toStatus}, update_time = now() " +
            "where id = #{id} and status = #{fromStatus}")
    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("fromStatus") Integer fromStatus,
                            @Param("toStatus") Integer toStatus);

    /**
     * 按条件统计商品数量（数据统计用）
     */
    Integer countByMap(Map map);

    /**
     * 捡漏专区：捡漏商品 + 已过审 + 未被下架（在售或已被抢走的都列出来，后者显示"已抢光"）
     * SQL 写在 ProductMapper.xml
     */
    List<FlashSaleVO> listFlashSale();

    /**
     * 管理端-捡漏活动列表（S4-3）
     *
     * 和用户端的区别：不过滤商品状态（已抢走、已下架的也要给管理员看到），
     * 并带出参与人数 / 抢到人数（子查询）。
     */
    List<FlashSaleAdminVO> listFlashSaleForAdmin();

}
