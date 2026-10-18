package com.sky.service;

import com.sky.dto.ReviewPageQueryDTO;
import com.sky.dto.ReviewSubmitDTO;
import com.sky.entity.Review;
import com.sky.result.PageResult;
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

    // ==================== 管理端-评价管理（S4-5）====================

    /** 【管理端】评价分页列表（可按商品/被评价人/星级筛选） */
    PageResult pageForAdmin(ReviewPageQueryDTO query);

    /**
     * 【管理端】删除违规评价（恶意差评、广告等）
     *
     * ⭐ 关键：删除时要【回退这条评价当时造成的信用分变化】——
     *    差评当时扣了对方 2 分，删掉差评却不还分，等于处罚没撤销。
     *
     * @param id     评价id
     * @param reason 删除原因（必填，便于事后举证）
     */
    void deleteByAdmin(Long id, String reason);

}
