package com.sky.service;

import com.sky.vo.ProductVO;

import java.util.List;

/**
 * 收藏服务
 *
 * 这个文件已经写好了，你可以把它当成"Service 接口长什么样"的参考模板。
 */
public interface FavoriteService {

    /** 收藏商品（重复收藏不报错） */
    void addFavorite(Long productId);

    /** 取消收藏 */
    void removeFavorite(Long productId);

    /** 我的收藏列表（返回商品信息，前端按 status 显示"已售罄"） */
    List<ProductVO> listFavorite();

}
