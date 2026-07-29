package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealPageVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    @Transactional
    @Override
    public void insert(SetmealDTO setmealDTO) {
        //新增套餐,浅拷贝
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.insert(setmeal);
        //新增套餐菜品
        setmealDTO.getSetmealDishes().forEach(item -> {
            item.setSetmealId(setmeal.getId());
        });

        //逐个新增套餐菜品
        for(SetmealDish item : setmealDTO.getSetmealDishes()){
            setmealDishMapper.insert(item);
        }
    }

    @Override
    public PageResult page(SetmealPageQueryDTO setmealPageQueryDTO) {
        //分页查询套餐
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());

        //浅拷贝
        SetmealPageVO setmealPageVO = new SetmealPageVO();
        BeanUtils.copyProperties(setmealPageQueryDTO, setmealPageVO);

        Page<SetmealPageVO> page = setmealMapper.selectPage(setmealPageVO);
        long total = page.getTotal();
        List<SetmealPageVO> list = page.getResult();
        return new PageResult(total, list);
    }

    @Transactional
    @Override
    public void delete(List<Long> ids) {
        //判断是否在起售中
        for(Long id : ids){
            Setmeal setmeal = setmealMapper.selectById(id);
            if(setmeal.getStatus() == StatusConstant.ENABLE){
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }

        //判断是否存在套餐的菜品并删除
        for(Long id : ids){
            setmealDishMapper.deleteBySetmealId(id);
        }

        //删除套餐
        for(Long id : ids){
            setmealMapper.deleteById(id);
        }


    }

    @Transactional
    @Override
    public void update(SetmealDTO setmealDTO) {
        //修改套餐
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.updateById(setmeal);

        //先删除原有关联菜品
        setmealDishMapper.deleteBySetmealId((long) Math.toIntExact(setmealDTO.getId()));

        //再重新插入新菜品
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            for (SetmealDish item : setmealDishes) {
                item.setSetmealId(setmealDTO.getId());
                setmealDishMapper.insert(item);
            }
        }
    }

    @Override
    public void status(Integer status, Long id) {
        //修改套餐状态
        Setmeal setmeal = new Setmeal();
        setmeal.setId(id);
        setmeal.setStatus(status);
        setmealMapper.updateById(setmeal);
    }

    @Override
    public SetmealVO getById(Long id) {
        //查询套餐基本信息（含分类名称）
        SetmealVO setmealVO = setmealMapper.selectByIdWithCategory(id);
        //查询套餐关联的菜品
        List<SetmealDish> setmealDishes = setmealDishMapper.selectBySetmealId(id);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
