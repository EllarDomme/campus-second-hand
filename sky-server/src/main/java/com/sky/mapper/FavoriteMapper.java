package com.sky.mapper;

import com.sky.entity.Favorite;
import com.sky.vo.ProductVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FavoriteMapper {

    /**
     * 新增一条收藏记录
     *
     * 【填空 ①】把 FILL_IN_HERE_1 换成 favorite 表的列名。
     *   提示：打开数据库表 favorite 看一眼，一共 4 个业务列，
     *        对应下面 values 里的 4 个 #{...}（userId / productId / createTime / updateTime）。
     *   参考：CategoryMapper.insert 的写法（注解里直接写 SQL）。
     */
    @Insert("insert into favorite (user_id, product_id, create_time, update_time) values (#{userId}, #{productId}, #{createTime}, #{updateTime})")
    void insert(Favorite favorite);

    /**
     * 查询"这个用户有没有收藏过这件商品"（用来防止重复收藏）
     *
     * 【填空 ②】把 FILL_IN_HERE_2 换成 where 条件。
     *   提示：方法有两个参数，用 #{userId} 和 #{productId} 两个占位符，
     *        中间用 AND 连接，写法参考上面的 @Delete。
     */
    @Select("select * from favorite where user_id = #{userId} and product_id = #{productId}")
    Favorite selectByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * 取消收藏（这个已经写好了，作为对比参考）
     */
    @Delete("delete from favorite where user_id = #{userId} and product_id = #{productId}")
    void deleteByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * 查询某用户的收藏列表（要关联商品表，才能拿到标题/价格/图片）
     *
     * SQL 写在 FavoriteMapper.xml 里（凡是有关联查询的，这个项目都写在 XML 里）
     */
    List<ProductVO> listByUserId(Long userId);

    //查询的是productVo
    /*select p.id,p.seller_id,p.category_id,p.title,p.price,p.images,p.description,p.condition_level,p.is_flash_sale,p.flash_start_time,p.audit_status,p.audit_note,p.status,p.update_time,p.category_name
    from favorite f left join product p on f.product_id = p.id where f.user_id = #{userId}
*/
}
