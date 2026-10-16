package com.sky.mapper;

import com.sky.entity.Complaint;
import com.sky.vo.ComplaintVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ComplaintMapper {

    @Insert("insert into complaint (product_id, order_id, complainant_user_id, respondent_user_id, " +
            "complaint_type, complaint_reason, content, images, handle_status, create_time, update_time) " +
            "values (#{productId}, #{orderId}, #{complainantUserId}, #{respondentUserId}, " +
            "#{complaintType}, #{complaintReason}, #{content}, #{images}, #{handleStatus}, " +
            "#{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Complaint complaint);

    @Select("select * from complaint where id = #{id}")
    Complaint getById(Long id);

    /**
     * 【处理工单】条件更新：只有"还没完结"的工单才能被处理
     *
     * ⚠️ 为什么必须带 `and handle_status <> 2`：
     *    两个管理员同时点"处理"，如果不加这个条件，
     *    两次都会成功 → 【信用分被扣两次】（-10 变成 -20）。
     *    所以和抢单/关单一样：把判断写进 where，靠行锁保证只成功一次。
     *
     * 注解里可以直接写 <>（XML 里才需要转义成 &lt;&gt;）
     */
    @Update("update complaint set handle_status = 2, handle_result = #{handleResult}, " +
            "handle_note = #{handleNote}, update_time = #{updateTime} " +
            "where id = #{id} and handle_status <> 2")
    int handleIfNotDone(@Param("id") Long id,
                        @Param("handleResult") Integer handleResult,
                        @Param("handleNote") String handleNote,
                        @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查重：我对同一个对象（订单或商品）有没有【还没完结】的工单
     *
     * 为什么限制"还没完结"：纠纷处理完之前重复投诉没意义（信息都还在工单里），
     * 处理完之后如果又出了新问题，应该允许再投诉。
     */
    @Select("<script>select count(*) from complaint " +
            "where complainant_user_id = #{userId} and handle_status &lt;&gt; 2 " +
            "<if test='orderId != null'> and order_id = #{orderId}</if>" +
            "<if test='productId != null'> and product_id = #{productId}</if>" +
            "</script>")
    int countUnfinished(@Param("userId") Long userId,
                        @Param("orderId") Long orderId,
                        @Param("productId") Long productId);

    /** 我的投诉记录（SQL 在 XML） */
    List<ComplaintVO> listMine(Long complainantUserId);

    /** 工单详情（带商品标题/双方昵称，SQL 在 XML） */
    ComplaintVO getDetailById(Long id);

    /** 管理端工单列表（可按下拉框筛选状态/类型，SQL 在 XML，分页交给 PageHelper） */
    List<ComplaintVO> listForAdmin(@Param("handleStatus") Integer handleStatus,
                                   @Param("complaintType") Integer complaintType);

}
