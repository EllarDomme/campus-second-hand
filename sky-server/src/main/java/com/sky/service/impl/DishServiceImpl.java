package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setMealDishMapper;

    @Override
    public void saveWithFlavor(DishDTO dishDTO) {

        //创建Dish对象，然后进行浅拷贝
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);

        //向菜品插入一条数据
        dishMapper.insert(dish);

        //获取insert语句返回的id值
        Long id = dish.getId();

        //想菜品口味插入数据
        List<DishFlavor>  flavors = dishDTO.getFlavors();
        if(flavors!=null&&(flavors.size() > 0)){
            flavors.forEach(flavor -> {
                flavor.setDishId(id);
            });
            //向口味里面插入数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    @Override
    public PageResult page(DishPageQueryDTO dishPageQueryDTO) {
        //利用PageHelper实现分页查询菜品
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());

        //查询菜品
        Page<DishVO> list = dishMapper.selectList(dishPageQueryDTO);

        long total = list.getTotal();

        List<DishVO> records = list.getResult();

        return new PageResult(total,records);
    }

    @Transactional//添加事务
    @Override
    public void delete(List<Long> ids) {
        //判断是否存在起售中的菜品
        for(Long id : ids){
            //查询菜品是否起售中
            Dish dish = dishMapper.getById(id);
            if(dish.getStatus() == StatusConstant.ENABLE){
                //起售中的菜品不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        //判断是否存在套餐中的菜品
        List<Long> lister = setMealDishMapper.selectSetmealIdsByDishIds(ids);
        if(lister!=null&&lister.size()>0){
            //套餐中的菜品不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        for(Long id : ids){
            //逐一删除菜品
            dishMapper.deleteById(id);
            //删除相应的菜品口味数据
            dishFlavorMapper.deleteByDishId(id);
        }
    }

    @Override
    public DishVO getById(Long id) {
        //根据id查询菜品数据
        Dish dish = dishMapper.getById(id);
        //根据菜品id查询菜品口味数据
        List<DishFlavor> flavors = dishFlavorMapper.selectByDishId(id);
        //将菜品口味数据添加到菜品VO对象中，首先对菜品VO对象进行浅拷贝
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish,dishVO);
        dishVO.setFlavors(flavors);
        return dishVO;
    }

    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();//创建对象
        BeanUtils.copyProperties(dishDTO,dish);

        List<DishFlavor> flavors = dishDTO.getFlavors();

        //首先根据id更新菜品数据
        dishMapper.updateById(dish);

        //根据菜品id删除菜品口味数据
        dishFlavorMapper.deleteByDishId(dish.getId());

        //根据菜品id更新菜品口味数据
        if(flavors!=null&&flavors.size()>0) {
            for (DishFlavor flavor : flavors) {
                flavor.setDishId(dish.getId());
            }
            //插入菜品口味数据
            dishFlavorMapper.insertBatch(flavors);
        }

    }

    @Override
    public void updateStatus(Integer status,Long id) {
        //根据id更新菜品状态
        Dish dish = new Dish();
        dish.setStatus(status);
        dish.setId(id);
        dishMapper.updateById(dish);
    }

    @Override
    public List<DishVO> listByCategoryId(Long categoryId) {
        //根据分类id查询菜品数据
        return dishMapper.selectByCategoryId(categoryId);
    }


}
