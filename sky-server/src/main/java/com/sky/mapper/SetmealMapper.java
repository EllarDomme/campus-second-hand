package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.entity.Setmeal;
import com.sky.enumeration.OperationType;
import com.sky.vo.SetmealPageVO;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SetmealMapper {

    //新增套餐
    @AutoFill(value = OperationType.INSERT)
    @Insert("insert into setmeal(name, price, image, description, category_id, status, create_time, update_time, create_user, update_user) values(#{name}, #{price}, #{image}, #{description}, #{categoryId}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    void insert(Setmeal setmeal);

    Page<SetmealPageVO> selectPage(SetmealPageVO setmealPageVO);

    @Select("select * from setmeal where id = #{id}")
    Setmeal selectById(Integer id);

    @Delete("delete from setmeal where id = #{id}")
    void deleteById(Integer id);

    @AutoFill(value = OperationType.UPDATE)
    void updateById(Setmeal setmeal);

    SetmealVO selectByIdWithCategory(Long id);
}
