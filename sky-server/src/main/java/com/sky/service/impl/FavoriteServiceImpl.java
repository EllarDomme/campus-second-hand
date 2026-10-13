package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.Favorite;
import com.sky.mapper.FavoriteMapper;
import com.sky.service.FavoriteService;
import com.sky.vo.ProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收藏服务实现
 *
 * 这里一共留了 3 个空（④⑤⑥），每个空上面都有 TODO 说明要写什么、参考哪里。
 * 其余代码都写好了，你只改这 3 处。
 */
@Service
@Slf4j
public class FavoriteServiceImpl implements FavoriteService {

    @Autowired
    private FavoriteMapper favoriteMapper;

    /**
     * 收藏商品
     */
    @Override
    public void addFavorite(Long productId) {

        // ===== 填空 ④ =====
        // TODO ④：把下面的 null 换成"取当前登录用户的id"
        //   提示：参考 ProductServiceImpl.save() 里那一行 product.setSellerId(...) 的写法，
        //        它用的是 BaseContext.getCurrentId()（这个类上面已经 import 了）
        Long userId = BaseContext.getCurrentId();

        // 先查一下这个用户有没有收藏过这件商品
        Favorite exist = favoriteMapper.selectByUserAndProduct(userId, productId);

        // ===== 填空 ⑤ =====
        // TODO ⑤：把下面的 false 换成"已经收藏过"的判断，这样重复点收藏不会报错
        //   提示：上面查出来的 exist 就是收藏记录，判断它是不是空的即可（用 != null）
        boolean alreadyFavorited = (exist != null);

        if (alreadyFavorited) {
            log.info("已经收藏过了，直接返回：userId={}, productId={}", userId, productId);
            return;
        }

        Favorite favorite = Favorite.builder()
                .userId(userId)
                .productId(productId)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        favoriteMapper.insert(favorite);
        log.info("收藏成功：userId={}, productId={}", userId, productId);
    }

    /**
     * 取消收藏（这个方法已经写好了，作为参考）
     */
    @Override
    public void removeFavorite(Long productId) {
        Long userId = BaseContext.getCurrentId();
        favoriteMapper.deleteByUserAndProduct(userId, productId);
        log.info("取消收藏：userId={}, productId={}", userId, productId);
    }

    /**
     * 我的收藏列表
     */
    @Override
    public List<ProductVO> listFavorite() {
        Long userId = BaseContext.getCurrentId();

        // ===== 填空 ⑥ =====
        // TODO ⑥：调用 favoriteMapper 查出这个用户的收藏列表并返回
        //   提示：mapper 里那个方法的参数就是 userId，返回类型就是 List<ProductVO>，
        //        所以这里一句 return 就够了（把 return null 换成正确的调用）
        return favoriteMapper.listByUserId(userId);
    }

}
