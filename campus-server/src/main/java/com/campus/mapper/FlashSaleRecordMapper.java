package com.campus.mapper;

import com.campus.entity.FlashSaleRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FlashSaleRecordMapper {

    /**
     * 写一条抢购记录
     *
     * ⚠️ 表上有唯一索引 uk_product_user(product_id, user_id)，
     * 所以并发下重复插入会抛 DuplicateKeyException —— 这不是 bug，是兜底防线，
     * 调用方要接住它（跟我们做收藏、支付幂等时的套路一样）。
     */
    @Insert("insert into flash_sale_record (product_id, user_id, is_success, order_id, create_time, update_time) " +
            "values (#{productId}, #{userId}, #{isSuccess}, #{orderId}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(FlashSaleRecord record);

    /**
     * 查这个用户对这件商品是否已经有"抢购成功"的记录
     *
     * 用途：给用户一个友好提示（"你已经抢到过了"），
     * 而唯一索引是并发场景下的兜底 —— 又快又好的组合。
     */
    @Select("select count(*) from flash_sale_record where product_id = #{productId} " +
            "and user_id = #{userId} and is_success = 1")
    int countSuccess(@Param("productId") Long productId, @Param("userId") Long userId);

    /**
     * 查这个用户对这件商品的抢购成功记录（S3-8b 用：要拿到 orderId）
     *
     * 和 countSuccess 的区别：countSuccess 只回答"有没有"，这个方法要把记录本身取出来。
     * limit 1 是防御性写法 —— 有唯一索引在，理论上最多一条。
     */
    @Select("select * from flash_sale_record where product_id = #{productId} " +
            "and user_id = #{userId} and is_success = 1 limit 1")
    FlashSaleRecord getSuccessRecord(@Param("productId") Long productId, @Param("userId") Long userId);

    /**
     * 我的抢购记录（只返回抢到的，按时间倒序）
     */
    @Select("select * from flash_sale_record where user_id = #{userId} and is_success = 1 " +
            "order by create_time desc limit 100")
    List<FlashSaleRecord> listMySuccess(Long userId);

}
