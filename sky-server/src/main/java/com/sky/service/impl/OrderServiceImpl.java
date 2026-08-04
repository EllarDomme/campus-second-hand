package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.properties.ShopProperties;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.BaiduMapUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.*;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private BaiduMapUtil baiduMapUtil;
    @Autowired
    private ShopProperties shopProperties;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 订单提交
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        //异常情况的处理，比如地址为空，超出送货距离，购物车为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //订单超出送货距离
        //首先获取用户地址
        String userAddress = addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail();

        //拿到用户地址坐标
        String userCoordinate = baiduMapUtil.getCoordinate(userAddress);

        //拿到店铺地址坐标
        String shopCoordinate = baiduMapUtil.getCoordinate(shopProperties.getAddress());

        //计算距离
        long distance = baiduMapUtil.getDistance(userCoordinate,shopCoordinate);

        //判断是否超出送货距离
        if(distance > 5000){
            throw new OrderBusinessException(MessageConstant.ADDRESS_OUT_OF_RANGE);
        }



        //查看当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        ShoppingCart  shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> list = shoppingCartMapper.selectList(shoppingCart);
        if(list == null || list.size() == 0){
            throw new OrderBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);

        }

        //构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,order);
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());
        order.setUserId(userId);
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());
        //向订购单表插入一条数据
        orderMapper.insert(order);

        //向订单详情表插入多条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for(ShoppingCart cart : list){
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);

        //清空购物车中的数据
        shoppingCartMapper.deleteAll(userId);

        //封装结果返回
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime()).build();

        return  orderSubmitVO;
    }

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // Mock模式：跳过真实微信支付接口调用，直接返回模拟的预支付数据
        log.info("【Mock支付】订单号：{}，金额：0.01元", ordersPaymentDTO.getOrderNumber());

        // Mock支付没有真实微信回调，这里直接更新订单状态为已支付、待接单
        Long userId = BaseContext.getCurrentId();
        Orders ordersDB = orderMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), userId);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())) {
            Orders orders = Orders.builder()
                    .id(ordersDB.getId())
                    .status(Orders.TO_BE_CONFIRMED)
                    .payStatus(Orders.PAID)
                    .checkoutTime(LocalDateTime.now())
                    .build();
            orderMapper.update(orders);

            // 来单提醒：Mock模式没有微信回调，这里手动推送
            sendNewOrderReminder(ordersDB);
        }

        OrderPaymentVO vo = OrderPaymentVO.builder()
                .nonceStr(UUID.randomUUID().toString().replace("-", ""))
                .paySign("MOCK_SIGN_" + System.currentTimeMillis())
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                .signType("RSA")
                .packageStr("prepay_id=mock_" + System.currentTimeMillis())
                .build();

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    @Override
    public void paySuccess(String outTradeNo) {
        // 根据订单号查询订单（支付回调无JWT上下文，不能用userId查询）
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 来单提醒：通过WebSocket向店铺端推送新订单消息
        sendNewOrderReminder(ordersDB);

    }

    /**
     * 来单提醒：通过WebSocket向店铺端推送新订单消息
     *
     * @param order 已支付的新订单
     */
    private void sendNewOrderReminder(Orders order) {
        Map map = new HashMap();
        map.put("type", 1);
        map.put("orderId", order.getId());
        map.put("content", "订单号：" + order.getNumber());
        webSocketServer.sendToAllClient(JSONObject.toJSONString(map));
    }

    /**
     * 订单历史查询
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        //利用PageHelper进行分页查询
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());

        Page<Orders> historyHistoryList = orderMapper.selectList(ordersPageQueryDTO);

        Long total = historyHistoryList.getTotal();

        List<Orders> list = historyHistoryList.getResult();

        List<HistoryOrdersQueryVO> historyOrdersQueryVOList = new ArrayList<>();

        //根据订单id查询订单详情
        for(Orders order : list){
            HistoryOrdersQueryVO historyOrdersQueryVO = new HistoryOrdersQueryVO();
            List<OrderDetail> orderDetailList = orderDetailMapper.selectList(order.getId());
            //首先进行浅拷贝
            BeanUtils.copyProperties(order,historyOrdersQueryVO);
            historyOrdersQueryVO.setOrderDetailList(orderDetailList);
            historyOrdersQueryVOList.add(historyOrdersQueryVO);
        }


        return new PageResult(total,historyOrdersQueryVOList);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO orderDetail(Long id) {
        //根据订单id查询订单详情
        Orders order = orderMapper.selectById(id);
        if (order == null || !order.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order,orderVO);
        //根据订单id查询订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(id);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 取消订单
     * @param id
     */
    @Override
    public void cancel(Long id) {
        //根据id查询订单
        Orders order = orderMapper.selectById(id);
        if (order == null || !order.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        //已接单及之后的状态不允许用户直接取消
        if (order.getStatus() > Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //已支付订单（待接单）取消，支付状态置为退款
        if (Orders.PAID.equals(order.getPayStatus())) {
            order.setPayStatus(Orders.REFUND);
        }

        order.setStatus(Orders.CANCELLED);
        order.setCancelReason(MessageConstant.USER_CANCEL_ORDER);
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 重复订单
     * @param orders
     */
    @Override
    @Transactional
    public void repetition(Orders orders) {
        //根据订单id查询订单
        Orders order = orderMapper.selectById(orders.getId());
        if (order == null || !order.getUserId().equals(orders.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //根据订单id查询订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(order.getId());

        //生成新订单（待付款），清掉原订单的支付/取消/送达等脏数据
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());
        order.setCheckoutTime(null);
        order.setCancelReason(null);
        order.setRejectionReason(null);
        order.setCancelTime(null);
        order.setDeliveryTime(null);
        order.setId(null);
        orderMapper.insert(order);

        //重置明细id并指向新订单
        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setId(null);
            orderDetail.setOrderId(order.getId());
        }
        orderDetailMapper.insertBatch(orderDetailList);
    }

    /**
     * 根据条件分页查询订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult searchOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        //利用PageHelper进行分页查询
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.selectList(ordersPageQueryDTO);
        Long total = page.getTotal();
        List<Orders> list = page.getResult();
        List<OrderSearchVO> orderSearchVOList = new ArrayList<>();
        for(Orders order : list){
            OrderSearchVO orderSearchVO = new OrderSearchVO();
            BeanUtils.copyProperties(order,orderSearchVO);
            List<OrderDetail> orderDetailList = orderDetailMapper.selectList(order.getId());
            String orderDishes = "";
            for(OrderDetail orderDetail : orderDetailList){
                orderDishes += orderDetail.getName() + " x " + orderDetail.getNumber() + " ";
            }
            orderSearchVO.setOrderDishes(orderDishes);
            orderSearchVOList.add(orderSearchVO);
        }
        return new PageResult(total,orderSearchVOList);
    }

    /**
     * 获取各个状态的订单统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        //根据状态查询对应订单数量
        Long confirmed = orderMapper.selectCountByStatus(Orders.CONFIRMED);
        Long deliveryInProgress = orderMapper.selectCountByStatus(Orders.DELIVERY_IN_PROGRESS);
        Long toBeConfirmed = orderMapper.selectCountByStatus(Orders.TO_BE_CONFIRMED);

        System.out.println("待接单数量：" + toBeConfirmed);
        System.out.println("待派送数量：" + confirmed);
        System.out.println("派送中数量：" + deliveryInProgress);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setConfirmed(confirmed.intValue());
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress.intValue());
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed.intValue());
        return orderStatisticsVO;
    }

    /**
     * 根据订单id查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO getDetails(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order,orderVO);
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(order.getId());
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        //根据id查询订单
        Orders order = orderMapper.selectById(ordersConfirmDTO.getId());
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //只有待接单状态的订单才能接单
        if (!Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        order.setStatus(Orders.CONFIRMED);
        orderMapper.update(order);
    }

    /**
     * 拒绝订单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        //根据id查询订单
        Orders order = orderMapper.selectById(ordersRejectionDTO.getId());
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //只有待接单状态的订单才能拒单
        if (!Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //已支付订单拒单，支付状态置为退款
        if (Orders.PAID.equals(order.getPayStatus())) {
            order.setPayStatus(Orders.REFUND);
        }
        order.setStatus(Orders.CANCELLED);
        order.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        order.setCancelReason("店铺拒绝接单");
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 管理员取消订单
     * @param ordersCancelDTO
     */
    @Override
    public void adminCancel(OrdersCancelDTO ordersCancelDTO) {
        //判断订单存不存在
        Orders order = orderMapper.selectById(ordersCancelDTO.getId());
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //判断订单状态，派送中、已完成、已取消的订单无法取消
        if (order.getStatus() >= Orders.DELIVERY_IN_PROGRESS) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //判断支付状态，已支付订单取消时支付状态置为退款
        if (Orders.PAID.equals(order.getPayStatus())) {
            order.setPayStatus(Orders.REFUND);
        }
        order.setStatus(Orders.CANCELLED);
        order.setCancelReason(ordersCancelDTO.getCancelReason());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    @Override
    public void delivery(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //只有已接单状态的订单才能配送
        if (!Orders.CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        order.setStatus(Orders.DELIVERY_IN_PROGRESS);
        order.setDeliveryStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(order);
    }

    @Override
    public void complete(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //只有派送中状态的订单才能完成
        if (!Orders.DELIVERY_IN_PROGRESS.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        order.setStatus(Orders.COMPLETED);
        order.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 定时取消超时未支付的订单（每分钟执行一次）
     */
    @Scheduled(cron = "0 * * * * ?")
    public void handleTimeoutOrders() {
        log.info("定时任务：扫描超时未支付订单");
        Integer count = orderMapper.cancelExpiredOrders(
                MessageConstant.TIMEOUT_CANCEL_ORDER,
                LocalDateTime.now(),
                LocalDateTime.now().minusMinutes(15));
        if (count != null && count > 0) {
            log.info("定时任务：自动取消 {} 笔超时未支付订单", count);
        }
    }

    @Override
    public void reminder(Long id) {
        //查询订单是否存在
        Orders order = orderMapper.selectById(id);

        if(order == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //利用webSocket实现用户催单
        Map map = new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        map.put("content","订单号:" + order.getNumber());
        webSocketServer.sendToAllClient(JSONObject.toJSONString(map));
    }

}
