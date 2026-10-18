package com.campus.mapper;

import com.github.pagehelper.Page;
import com.campus.dto.BargainPageQueryDTO;
import com.campus.entity.Bargain;
import com.campus.vo.BargainVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BargainMapper {

    @Insert("insert into bargain (seller_id, buyer_id, product_id, buyer_offer, bargain_status, expire_time, create_time, update_time) " +
            "values (#{sellerId}, #{buyerId}, #{productId}, #{buyerOffer}, #{bargainStatus}, #{expireTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Bargain bargain);

    @Select("select * from bargain where id = #{id}")
    Bargain getById(Long id);

    /**
     * 动态更新（只更新传了值的字段）：改状态、写还价、回填订单id 都用它
     */
    void update(Bargain bargain);

    /**
     * 这个买家对这件商品，是否还有【进行中】的议价
     *
     * 进行中 = 状态 1（买家出价，等卖家）或 2（卖家还价，等买家）
     * 3/4/5 都是终态，不阻塞新的议价。
     */
    @Select("select count(*) from bargain where buyer_id = #{buyerId} and product_id = #{productId} " +
            "and bargain_status in (1, 2)")
    int countInProgress(@Param("buyerId") Long buyerId, @Param("productId") Long productId);

    /**
     * 这个买家对这件商品，最近一条【被拒绝】的议价（用来判断 24 小时冷却期）
     */
    @Select("select * from bargain where buyer_id = #{buyerId} and product_id = #{productId} " +
            "and bargain_status = 4 order by update_time desc limit 1")
    Bargain getLastRejected(@Param("buyerId") Long buyerId, @Param("productId") Long productId);

    /**
     * 我的议价分页（带商品信息和双方昵称），SQL 写在 BargainMapper.xml
     */
    Page<BargainVO> pageQuery(BargainPageQueryDTO query);

    /**
     * 查已过期的"进行中"议价（定时任务用，S3-6c）
     */
    @Select("select * from bargain where bargain_status in (1, 2) and expire_time is not null " +
            "and expire_time < #{now} order by expire_time asc limit 500")
    List<Bargain> listExpired(LocalDateTime now);

}
