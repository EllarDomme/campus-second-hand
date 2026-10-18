package com.campus.mapper;

import com.github.pagehelper.Page;
import com.campus.annotation.AutoFill;
import com.campus.dto.CategoryPageQueryDTO;
import com.campus.entity.Category;
import com.campus.enumeration.OperationType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryMapper {
    /**
     * 添加分类
     * @param category
     */
    @Insert("insert into category(category_name,create_time,update_time) values(#{categoryName},#{createTime},#{updateTime})")
    @AutoFill(value = OperationType.INSERT)
    void insert(Category category);


    /**
     * 分页查询分类
     * @param categoryPageQueryDTO
     * @return
     */
    Page<Category> selectList(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 删除分类
     * @param id
     */
    @Delete("delete from category where id = #{id}")
    void deleteById(Long id);

    /**
     * 更新分类
     * @param category
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(Category category);

    /**
     * 根据类型查询分类，type为null则查询所有
     * @param
     * @return
     */
    @Select("select * from category order by id")
    List<Category> selectAll();
}
