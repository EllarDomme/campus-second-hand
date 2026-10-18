package com.campus.mapper;

import com.github.pagehelper.Page;
import com.campus.dto.TradeOrderPageQueryDTO;
import com.campus.entity.TradeOrder;
import com.campus.vo.TradeOrderVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TradeOrderMapper {

    /**
     * 新增订单
     *
     * useGeneratedKeys：把数据库生成的自增主键回填到 order.id，
     * 下单接口要把 orderId 返回给前端（紧接着去支付）
     */
    @Insert("insert into trade_order (order_no, seller_id, buyer_id, product_id, real_price, deal_type, order_source, " +
            "status, pay_status, pickup_place, remark, expire_time, create_time, update_time) " +
            "values (#{orderNo}, #{sellerId}, #{buyerId}, #{productId}, #{realPrice}, #{dealType}, #{orderSource}, " +
            "#{status}, #{payStatus}, #{pickupPlace}, #{remark}, #{expireTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(TradeOrder order);

    @Select("select * from trade_order where id = #{id}")
    TradeOrder getById(Long id);

    @Select("select * from trade_order where order_no = #{orderNo}")
    TradeOrder getByOrderNo(String orderNo);

    /**
     * 按支付流水号查订单
     * 用途：支付回调只带回来一个 payNo，得靠它找到对应订单
     */
    @Select("select * from trade_order where pay_no = #{payNo}")
    TradeOrder getByPayNo(String payNo);

    /**
     * 动态更新（只更新传了值的字段），SQL 写在 TradeOrderMapper.xml
     */
    void update(TradeOrder order);

    Page<TradeOrderVO> pageQuery(TradeOrderPageQueryDTO query);

    /**
     * 订单详情（也带商品标题/图片，所以返回 TradeOrderVO）
     */
    TradeOrderVO getVOById(Long id);

    /**
     * 查"超时未处理"的订单（定时任务用）
     *
     * status=0 同时覆盖两种情况，靠 pay_status 区分：
     *   待支付（pay_status=0）—— 买家 15 分钟没付钱
     *   待确认（pay_status=1）—— 卖家 30 分钟没确认
     * 两者都由 expire_time 判定到点，具体怎么处理交给 Service（已支付就退款）。
     *
     * limit 500：一次最多处理 500 笔，避免某次积压太多把数据库拖住。
     * 走索引 idx_status_pay_expire(status, pay_status, expire_time)。
     */
    @Select("select * from trade_order where status = 0 and expire_time is not null " +
            "and expire_time < #{now} order by expire_time asc limit 500")
    List<TradeOrder> listExpired(LocalDateTime now);

}
