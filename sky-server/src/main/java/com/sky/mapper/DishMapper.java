package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper {

    //新增菜品
    @Insert("insert into dish (name, image, price, category_id, status, create_time, update_time,create_user,update_user,description) values (#{name}, #{image}, #{price}, #{categoryId}, #{status}, #{createTime}, #{updateTime},#{createUser},#{updateUser},#{description})")
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    //分页查询菜品
    Page<DishVO> selectList(DishPageQueryDTO dishPageQueryDTO);

    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);

    @Delete("delete from dish where id = #{id}")
    void deleteById(Long id);

    @AutoFill(value = OperationType.UPDATE)
    void updateById(Dish dish);

    @Select("select * from dish where category_id = #{categoryId}")
    List<DishVO> selectByCategoryId(Long categoryId);
}
