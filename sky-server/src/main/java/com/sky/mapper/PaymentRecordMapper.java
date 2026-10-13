package com.sky.mapper;

import com.sky.entity.PaymentRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentRecordMapper {

    /**
     * 新增支付流水
     *
     * ⚠️ 注意：payment_record 表上 pay_no 有唯一索引（uk_pay_no）。
     * 所以当同一笔支付的通知重复到达时，这里会抛出 DuplicateKeyException ——
     * 这不是 bug，而是"幂等"的最后一道防线。调用方要接住它。
     */
    @Insert("insert into payment_record (order_id, order_no, pay_no, amount, pay_type, status, remark, create_time, update_time) " +
            "values (#{orderId}, #{orderNo}, #{payNo}, #{amount}, #{payType}, #{status}, #{remark}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PaymentRecord record);

    @Select("select * from payment_record where pay_no = #{payNo}")
    PaymentRecord getByPayNo(String payNo);

}
