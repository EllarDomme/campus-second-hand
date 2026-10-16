package com.sky.service;

import com.sky.dto.ReviewSubmitDTO;
import com.sky.entity.Review;
import com.sky.vo.ReviewVO;

import java.util.List;

/**
 * 交易评价服务
 *
 * 规则：
 *   ① 只有【已完成】的订单才能评价（没成交就评价 = 刷信誉）
 *   ② 一单一评：唯一索引 uk_order_from(order_id, from_user_id) 兜底
 *   ③ 只能评价交易对手（我是买家就评卖家，反之亦然）—— 不接受前端传"被评价人"
 *   ④ 评价会影响对方的信用分：好评 +1、差评 -2、中评（3 星）不加不减
 */
public interface ReviewService {

    /** 提交评价 */
    void submit(ReviewSubmitDTO submitDTO);

    /** 某商品的评价列表（所有人可见） */
    List<ReviewVO> listByProduct(Long productId);

    /** 某人收到的评价列表（个人主页展示信誉） */
    List<ReviewVO> listByToUser(Long toUserId);

    /** 我发出的评价 */
    List<Review> mySentReviews();

}
