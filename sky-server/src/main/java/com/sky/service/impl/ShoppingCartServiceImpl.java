package com.sky.service.impl;

import ch.qos.logback.core.joran.util.beans.BeanUtil;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Product;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.ProductMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private ProductMapper dishMapper;

    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //实现添加购物车逻辑，判断是菜品还是套餐，根据用户id添加到购物车
        ShoppingCart shoppingCart = new ShoppingCart();
        //浅拷贝
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());

        //判断该东西是否已经存在购物车
        List<ShoppingCart> list = shoppingCartMapper.selectList(shoppingCart);

        System.out.println("列表长度为：" + list.size());
        System.out.println("使用者id为：" + BaseContext.getCurrentId());
        if(list != null && list.size() == 1){
            //如果已经存在，那么就更新数量
            System.out.println("购物车数量为0");
            ShoppingCart existShoppingCart = list.get(0);
            existShoppingCart.setNumber(existShoppingCart.getNumber() + 1);
            shoppingCartMapper.updateById(existShoppingCart);
        }else{
            //如果不存在，那么久添加到购物车
            //判断当前是菜品还是套餐
            System.out.println("一直都要加到购物车");
            System.out.println();
            Long dishId = shoppingCartDTO.getDishId();
            // 本模块（购物车）在改造计划里属于"批次C - 待删除"：二手商品每件唯一，不需要购物车。
            // T3-3 已删除套餐模块，所以这里不再有"套餐分支"，只保留商品分支，
            // 等阶段 3 重写订单时整个模块一并删除。
            Product dish = dishId == null ? null : dishMapper.getById(dishId);
            if (dish != null) {
                shoppingCart.setName(dish.getTitle());
                shoppingCart.setImage(dish.getImages());
                shoppingCart.setAmount(dish.getPrice());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    @Override
    public List<ShoppingCart> list() {
        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> list = shoppingCartMapper.returnList(userId);
        return list;
    }

    @Override
    public void cleanShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteAll(userId);
    }
}
