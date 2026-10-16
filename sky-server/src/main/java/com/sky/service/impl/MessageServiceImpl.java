package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.sky.constant.ChatConstant;
import com.sky.constant.MessageConstant;
import com.sky.constant.ProductConstant;
import com.sky.context.BaseContext;
import com.sky.dto.MessageSendDTO;
import com.sky.entity.*;
import com.sky.exception.BaseException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.MessageService;
import com.sky.vo.ConversationVO;
import com.sky.vo.MessageSearchVO;
import com.sky.vo.MessageVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私信服务实现
 *
 * ==========================================================================
 * 【你的任务】把下面 4 个方法里的【填空】写完（编号 1~9），
 *   每个待实现方法末尾都有一句 throw new UnsupportedOperationException(...)，
 *   写完后把那一行【删掉】。
 *
 *   已替你写好（不用改）的方法：listConversations()、unreadTotal()
 * ==========================================================================
 */
@Service
@Slf4j
public class MessageServiceImpl implements MessageService {

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private FlashSaleRecordMapper flashSaleRecordMapper;
    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    /**
     * 打开会话（买家在商品详情页点"联系卖家"）
     *
     * 语义约定：**只允许买家发起**（buyer = 我，seller = 商品的卖家）。
     * 卖家要主动找买家，应该从订单详情页发起（那时才拿得到买家 id），
     * 那是后续扩展，本方法不做。
     */
    @Transactional
    @Override
    public Long openConversation(Long productId) {
        Long me = BaseContext.getCurrentId();

        // ① 查商品 + 不存在校验
        Product product = productMapper.getById(productId);
        if (product == null) {
            throw new BaseException(MessageConstant.PRODUCT_NOT_FOUND);
        }

        // ② 不能和自己聊天（我 = 这件商品的卖家）
        if (me.equals(product.getSellerId())) {
            throw new BaseException("不能和自己聊天");
        }

        // ③ 【S3-8b】捡漏商品的私信权限
        //    权限是「会话级」的，所以只在这里判一次，判完把结果落成字段
        //
        // ┌── 【错题标记 #16】判断本身都写对了，但漏了"大括号范围" ────────────
        // │ 你当时写的：if (是捡漏商品) { 预热期判断 }   ← 大括号到这里就结束了
        // │             然后"看自己有没有抢到"写在了 if 外面
        // │ 后果：普通商品也会走"查抢购记录"那段。普通商品根本没有抢购记录，
        // │       于是【所有普通商品都建不了会话】，一律报"您未抢购到该商品" ——
        // │       一个本来完全正常的商品，突然谁都聊不了。
        // │ 这类错最气人：判断逻辑一个字都没错，就是范围错了。
        // │ 自查习惯：写完 if 块，把光标停在右大括号上看一眼高亮范围，
        // │           确认"该在里面的都在里面"。同理，复制粘贴判断条件时尤其要小心。
        // └──────────────────────────────────────────────────────────────
        //    这两个变量的默认值 = 普通商品的情况：不关联订单、不限期
        Long orderId = null;
        LocalDateTime canSendUntil = null;

        if (ProductConstant.FLASH_SALE.equals(product.getIsFlashSale())) {
            // a) 预热期（还没到抢购时间）→ 谁都不能聊
            if (product.getFlashStartTime() != null
                    && product.getFlashStartTime().isAfter(LocalDateTime.now())) {
                throw new BaseException("抢购开始前不能私信卖家，请等抢购开始");
            }

            // b) 抢购已开始 → 只有抢到的人能聊
            FlashSaleRecord record = flashSaleRecordMapper.getSuccessRecord(productId, me);
            if (record == null) {
                throw new BaseException("只有抢到该商品的买家才能联系卖家");
            }

            // c) 抢到了 → 会话的有效期 = 那笔抢购订单的付款截止时间
            //    这就是"10 分钟窗口期"的落地方式：不是在这里写死 10 分钟，
            //    而是复用订单本来就有的 expire_time（抢购下单时按 10 分钟创建的）。
            //    好处：窗口规则只有一处定义，改超时时长不用改这里。
            TradeOrder order = tradeOrderMapper.getById(record.getOrderId());
            if (order == null) {
                throw new BaseException("抢购订单不存在");
            }
            orderId = order.getId();
            canSendUntil = order.getExpireTime();
        }

        // ④ 查已有会话：有就直接返回（不要新建，否则同一对用户会出现两个对话框）
        Conversation exist = conversationMapper.getByProductAndUsers(
                productId, me, product.getSellerId());
        if (exist != null) {
            return exist.getId();
        }

        // ⑤ 新建会话
        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setProductId(productId);
        conversation.setBuyerId(me);
        conversation.setSellerId(product.getSellerId());
        conversation.setOrderId(orderId);            // 捡漏单才有，普通商品为 null
        conversation.setStatus(ChatConstant.CONVERSATION_OPEN);
        conversation.setCanSendUntil(canSendUntil);  // null = 不限期
        conversation.setLastMessageTime(now);
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        // ┌── 【错题标记 #11】新建会话漏了并发兜底（唯一索引冲突没接住）──────
        // │ 你当时写的：直接 insert，没管并发。
        // │ 为什么错：两个人（或同一个人双击）同时点"联系卖家"时，
        // │   两个请求都会在【填空 2】查到"没有会话"，然后都去 insert →
        // │   第二个撞唯一索引 uk_product_buyer_seller → 抛 DuplicateKeyException
        // │   → 用户看到一个数据库异常（500），但其实什么都没做错。
        // │ 正确做法：catch 住它，重新查一次并返回（因为此时会话已经被另一个请求建好了）。
        // │ 这正是"两层防护"的第二层：查重是友好提示，唯一索引是兜底，
        // │   而兜底被触发时要【优雅降级】，不是把异常甩给用户。
        // └──────────────────────────────────────────────────────────────
        try {
            conversationMapper.insert(conversation);
            return conversation.getId();
        } catch (DuplicateKeyException e) {
            // 撞唯一索引 = 另一个请求刚建好了同一个会话，重查一次拿它的 id
            Conversation created = conversationMapper.getByProductAndUsers(
                    productId, me, product.getSellerId());
            if (created == null) {
                // 理论上不会发生（唯一索引冲突说明它一定存在），防御一下
                throw new BaseException("创建会话失败，请重试");
            }
            log.info("并发建会话撞唯一索引，已返回已有会话：conversationId={}", created.getId());
            return created.getId();
        }
    }

    /**
     * 我的会话列表（已写好，不用改）
     */
    @Override
    public List<ConversationVO> listConversations() {
        return conversationMapper.listMine(BaseContext.getCurrentId());
    }

    /**
     * 聊天记录（分页，倒序：最新的在前，前端下拉加载更多）
     */
    @Override
    public PageResult history(Long conversationId, Integer page, Integer pageSize) {
        Long me = BaseContext.getCurrentId();

        // ================= 你的部分：填空 4 =================
        //
        // 【填空 4】权限校验：我必须是这个会话的买家或卖家（「允许法」！）
        //   提示：Conversation c = conversationMapper.getById(conversationId);
        //         · c == null                              → throw "会话不存在"
        //         · !me.equals(c.getBuyerId())
        //           && !me.equals(c.getSellerId())          → throw "无权查看该会话"
        //   ⚠️ 这里千万别写成"如果我是买家就放行"这种排除法 —— 会话之外的第三人
        //      必须被拦住，否则任何人都能翻别人的聊天记录。
        //
        Conversation conversation = conversationMapper.getById(conversationId);
        if(conversation == null){
            throw new BaseException("会话不存在");
        }
        if(!me.equals(conversation.getBuyerId()) && !me.equals(conversation.getSellerId())){
            throw new BaseException("无权查看该会话");
        }

        // ===================================================
        //
        // ---- 分页部分（已写好，你的填空在上面；填空完成后删掉下面的 throw 即可）----
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 20 : pageSize;
        int total = messageMapper.countByConversation(conversationId);
        List<MessageVO> records = messageMapper.pageHistory(conversationId, (p - 1) * size, size);
        return new PageResult((long) total, records);


    }

    /**
     * 发送消息：先落库，再尝试实时推送
     */
    @Transactional
    @Override
    public void send(MessageSendDTO sendDTO) {
        Long me = BaseContext.getCurrentId();

        // ================= 你的部分：填空 5 ~ 8 =================
        //
        // 【填空 5】内容守卫（三个，照抄发起议价的判空套路）
        //   · sendDTO.getConversationId() == null            → "会话id不能为空"
        //   · content 为 null 或 trim() 后为空               → "消息内容不能为空"
        //   · content.trim().length() > ChatConstant.MAX_CONTENT_LENGTH
        //                                                    → "消息太长了（最多 1000 字）"
        //   （1000 是数据库 message.content varchar(1000) 的长度，超了会报 SQL 异常，
        //     所以在业务层先拦住，给出可读提示）
        if(sendDTO.getConversationId() == null){
            throw new BaseException("会话id不能为空");
        }
        if(sendDTO.getContent() == null || sendDTO.getContent().trim().isEmpty()){
            throw new BaseException("消息内容不能为空");
        }
        if(sendDTO.getContent().trim().length() > ChatConstant.MAX_CONTENT_LENGTH){
            throw new BaseException("消息太长了（最多 1000 字）");
        }

        // 【填空 6】查会话 + 四个守卫（权限同样用「允许法」）
        //   Conversation c = conversationMapper.getById(sendDTO.getConversationId());
        //   · c == null                                             → "会话不存在"
        //   · 我不是 c 的买家也不是 c 的卖家                          → "无权在该会话发言"
        //   · ChatConstant.CONVERSATION_CLOSED.equals(c.getStatus()) → "会话已关闭"
        //   · c.getCanSendUntil() != null
        //     && c.getCanSendUntil().isBefore(LocalDateTime.now())   → "会话已超过可发送时间"
        Conversation conversation = conversationMapper.getById(sendDTO.getConversationId());
        if(conversation == null){
            throw new BaseException("会话不存在");
        }
        if(!me.equals(conversation.getSellerId()) && !me.equals(conversation.getBuyerId())){
            throw new BaseException("无权在该会话发言");
        }
        // ┌── 【错题标记 #10】漏了两个守卫（会话已关闭 / 超过可发送时间）──────
        // │ 你当时写的：只做了"存在 + 权限"两个检查，后面两个漏了。
        // │ 为什么重要：这两个字段存在的【唯一目的】就是限制发言，
        // │   不校验它们，conversation.status 和 can_send_until 就成了摆设 ——
        // │   捡漏抢购结束 10 分钟后，双方还能继续私聊，而需求明确不允许。
        // │ 记法：加了字段就要有人读它。字段没人校验 = 白设计。
        // └──────────────────────────────────────────────────────────────
        if (ChatConstant.CONVERSATION_CLOSED.equals(conversation.getStatus())) {
            throw new BaseException("会话已关闭");
        }
        if (conversation.getCanSendUntil() != null
                && conversation.getCanSendUntil().isBefore(LocalDateTime.now())) {
            throw new BaseException("会话已超过可发送时间");
        }

        // 【填空 7】确定收件人：对方是谁取决于我是谁
        //   Long toUserId = me.equals(c.getBuyerId()) ? c.getSellerId() : c.getBuyerId();
        Long toUserId = me.equals(conversation.getBuyerId()) ? conversation.getSellerId() : conversation.getBuyerId();

        // 【填空 8】WebSocket 实时推送（⚠️ 必须在落库【之后】做）—— 一共推【两条】
        //
        // ┌── 【错题标记 #9】推送写在了落库【之前】────────────────────────
        // │ 你当时写的：把 push 放在这里（落库代码的上方），先推再存。
        // │ 为什么错：数据库还没写就告诉对方"你有新消息了"，会出两种事故：
        // │   ① 万一后面的落库失败了（超长/连接断/约束冲突），
        // │      对方界面上已经多了一条消息，但库里根本没有 → "幽灵消息"，
        // │      刷新页面就消失，用户会觉得系统坏了；
        // │   ② 未读数统计也会错（下面 ② 就依赖"本条已入库"）。
        // │ 正确顺序：先落库 → 落库成功了 → 再推。
        // │ 一句话记法：**数据库是真相，推送只是"通知"；先有真相，再发通知。**
        // └──────────────────────────────────────────────────────────────
        //   （推送代码已挪到方法末尾，落库之后）
        //
        //   ① 新消息推给对方（前端把消息追加到聊天窗口）—— 见方法末尾
        //   ② 未读总数推给对方（前端小红点）—— 见方法末尾
        //       提示：int total = messageMapper.countUnread(toUserId, null);
        //       再包一个 {"type":"unread","total":total} 推过去
        //       ⚠️ 顺序陷阱：这次统计必须在【落库之后】，
        //          否则当前这条还没进库，推过去的数字会少 1
        //
        //   · 对方不在线时 sendToUser 会自己跳过并打日志，你不用管
        //   · 为什么要分两个 type：前端"聊天窗口追加消息"和"更新小红点"
        //     是两个不同的动作，混在一起前端不好处理
        //
        // =======================================================
        //
        // ---- 落库部分（已写好，放在你的填空【之后】）----
         LocalDateTime now = LocalDateTime.now();
         messageMapper.insert(Message.builder()
                 .conversationId(sendDTO.getConversationId())
                 .fromUserId(me)
                 .toUserId(toUserId)
                 .content(sendDTO.getContent().trim())
                 .msgType(ChatConstant.MSG_TYPE_TEXT)
                 .isRead(ChatConstant.UNREAD)
                 .createTime(now)
                 .updateTime(now)
                 .build());
        //
        // // 更新会话的最后消息时间 —— 会话列表就是按它排序的
        conversationMapper.update(Conversation.builder()
                .id(sendDTO.getConversationId())
                .lastMessageTime(now)
                .updateTime(now)
                .build());

        // ======================================================================
        // 【填空 8 · 两条推送】放在这里 —— 落库成功之后
        // ======================================================================

        // ① 新消息推给对方（前端把这条消息追加到聊天窗口）
        Map<String, Object> push = new HashMap<>();
        push.put("type", ChatConstant.PUSH_TYPE_MESSAGE);
        push.put("conversationId", sendDTO.getConversationId());
        push.put("fromUserId", me);
        push.put("content", sendDTO.getContent().trim());
        push.put("createTime", now.toString());
        webSocketServer.sendToUser(toUserId, JSON.toJSONString(push));

        // ② 未读总数推给对方（前端更新小红点）
        //    ⚠️ 必须在这里统计：本条已经入库，数字才是准的
        int unreadTotal = messageMapper.countUnread(toUserId, null);
        Map<String, Object> unreadPush = new HashMap<>();
        unreadPush.put("type", ChatConstant.PUSH_TYPE_UNREAD);
        unreadPush.put("total", unreadTotal);
        webSocketServer.sendToUser(toUserId, JSON.toJSONString(unreadPush));

        log.info("私信已发送：conversationId={}, from={}, to={}", sendDTO.getConversationId(), me, toUserId);
    }

    /**
     * 把该会话里"发给我的未读消息"标记为已读
     */
    @Transactional
    @Override
    public void markRead(Long conversationId) {
        Long me = BaseContext.getCurrentId();

        // ================= 你的部分：填空 9（含新功能 B）=================
        //
        // 【填空 9】权限 + 标记已读 + 已读回执推送
        //
        //   ① 权限：我必须是这个会话的买家或卖家（「允许法」）
        //      提示：Conversation c = conversationMapper.getById(conversationId);
        //            · c == null → "会话不存在"
        //            · 我不是买家也不是卖家 → "无权操作该会话"
        Conversation conversation = conversationMapper.getById(conversationId);
        if(conversation == null){
            throw new BaseException("会话不存在");
        }
        if(!me.equals(conversation.getSellerId()) && !me.equals(conversation.getBuyerId())){
            throw new BaseException("无权操作该会话");
        }
        // ┌── 【错题标记 #12】异常类型用错了：IllegalArgumentException ────────
        // │ 你当时写的：throw new IllegalArgumentException("会话不存在")
        // │ 为什么错：项目有全局异常处理器，它认的是【BaseException】。
        // │   抛 IllegalArgumentException 会走"兜底分支"，前端拿到的提示是
        // │   "未知错误"之类的话 —— 用户看不懂，你也排查不出原因。
        // │ 记法：业务异常一律用项目自己的 BaseException（它还会带上你写的中文提示）。
        // └──────────────────────────────────────────────────────────────
        //
        //   ② 标记：int rows = messageMapper.markRead(conversationId, me, LocalDateTime.now());
        //      rows = 本次被标记的条数，建议打日志（便于排查"为什么小红点没消"）
        //      ⚠️ 不要自己写 SQL 去"把这个会话的消息全标已读" ——
        //         Mapper 里的 SQL 已经带了 to_user_id = 我 的条件，
        //         少了它等于允许把"别人收到的消息"也标成已读（越权）。
        int rows = messageMapper.markRead(conversationId, me, LocalDateTime.now());
        // ┌── 【错题标记 #13】"没有未读消息"不该报错 ────────────────────
        // │ 你当时写的：if (rows == 0) throw new BaseException("没有未读消息");
        // │ 为什么错：rows = 0 是完全正常的情况！
        // │   · 用户进聊天页 → 标记已读；退出再进来 → 又标记一次 → rows 必然是 0
        // │   · 一条新消息都没有的会话，点进去也应该是 0
        // │   给它报错，等于"用户什么都没做错，接口却报错"，前端还得专门吞掉这个错。
        // │ 正确做法：rows 只是用来决定【要不要推已读回执】——
        // │   本来就没有未读，就别去打扰对方（省一次无意义的推送）。
        // │ 记法：区分"业务错误"和"业务上的空结果"。空结果通常不是错误。
        // └──────────────────────────────────────────────────────────────
        log.info("标记已读：conversationId={}, 本次标记 {} 条", conversationId, rows);
        //
        //   ③ ⭐【新功能 B：已读回执】推给【对方】，让他知道自己的消息被读了
        //      为什么要推：不推的话，发送方界面上永远显示"已送达"，
        //                 他以为对方没看，可能反复追问（微信的"已读"就是这么做出来的）
        //      对方是谁：me.equals(c.getBuyerId()) ? c.getSellerId() : c.getBuyerId()
        //      推什么：{"type":"read","conversationId":1,"readerId":我}
        //              type 用 ChatConstant.PUSH_TYPE_READ
        //      ⚠️ 只在 rows > 0 时推（本来就没有未读消息，就别去打扰对方）
        //
        // ======================================================================
        // 【填空 9 · 新功能 B：已读回执】推给【对方】
        //
        // 完整的故事线（这就是"微信已读"的实现原理）：
        //   ① 小明打开和小红的聊天窗口 → 前端调 PUT /user/message/read/{会话}
        //   ② 服务端：把"发给小明的未读消息"标成已读（上面那句 markRead）
        //   ③ 服务端：推给小【红】一条 {"type":"read","conversationId":1,"readerId":小明}
        //   ④ 小红的界面收到后：把自己发出的、在这个会话里的消息 → 显示"已读"
        //
        // 三个要点：
        //   · 推给谁：不是推给我自己，而是推给【对方】
        //     （我读消息，需要知道这件事的是"消息的发送方"，也就是对方）
        //   · 对方是谁：和 send 里一样，取决于我是买家还是卖家
        //         me.equals(buyerId) ? sellerId : buyerId
        //   · 推什么：必须带上 readerId（谁读的），
        //     对方界面上可能有多个会话，得靠 conversationId + readerId 才知道该点亮哪条
        //
        // 为什么只在 rows > 0 时推：没有真的标记成功（本来就没未读），
        //   推过去对方也做不了任何事，纯属白唤醒一次客户端。
        // ======================================================================
        if (rows > 0) {
            Long otherUserId = me.equals(conversation.getBuyerId())
                    ? conversation.getSellerId()
                    : conversation.getBuyerId();

            Map<String, Object> readPush = new HashMap<>();
            readPush.put("type", ChatConstant.PUSH_TYPE_READ);
            readPush.put("conversationId", conversationId);
            readPush.put("readerId", me);            // ← 谁读的（对方据此把自己的消息标成已读）
            webSocketServer.sendToUser(otherUserId, JSON.toJSONString(readPush));
        }
    }

    /**
     * 消息搜索（新功能 A）—— Mapper 的 SQL + 这个方法的逻辑，都由你写
     */
    @Override
    public PageResult search(String keyword, Integer page, Integer pageSize) {
        Long me = BaseContext.getCurrentId();

        // ================= 你的部分：新功能 A =================
        //
        // ① 关键词守卫：keyword 为 null 或 trim 后为空 → throw "搜索关键词不能为空"
        //    ⚠️ 为什么必须拦：like '%%' 会匹配全部消息，
        //       等于把"我所有聊过什么"一次性倒出来（既是性能问题也是隐私问题）
        if(keyword == null || keyword.trim().isEmpty()){
            throw new BaseException("搜索关键词不能为空");
        }
        //
        // ② 分页参数规整（别信前端）
        // ┌── 【错题标记 #14】超上限时应该"取上限"，不是"重置成默认值" ───────
        // │ 你当时写的：pageSize > MAX_PAGE_SIZE 时 pageSize = 20
        // │ 为什么错：用户明明要求 100 条，你只给他 20 条 —— 他会以为"就这么多"。
        // │   正确语义是"你要多了，我最多给 50" → pageSize = MAX_PAGE_SIZE。
        // │   （两种写法都不会把库拖垮，但给用户的感觉完全不同）
        // └──────────────────────────────────────────────────────────────
        // ┌── 【错题标记 #15】判空丢了 → NPE 风险 ────────────────────────
        // │ 你当时写的：page = page < 1 ? 1 : page;   ← page 为 null 时直接 NPE
        // │ 现在走 HTTP 有 defaultValue 兜着，看着没事；但只要有别的调用方
        // │ （定时任务 / 内部调用 / 前端传空）传了 null，接口就 500。
        // │ 记法：对外部传进来的包装类型（Integer/Long），先判空再用。
        // └──────────────────────────────────────────────────────────────
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 20 : pageSize;
        if (size > ChatConstant.MAX_PAGE_SIZE) {
            size = ChatConstant.MAX_PAGE_SIZE;
        }

        // ③ 拿总数：messageMapper.countMyMessages(me, keyword.trim())
        // ④ 拿列表：messageMapper.searchMyMessages(me, keyword.trim(), (p-1)*size, size)
        // ⑤ 返回 new PageResult((long) total, records)
        //
        // ⚠️ 权限过滤写在 SQL 里（where c.buyer_id = 我 or c.seller_id = 我），
        //    Java 层不用再过滤，但你要确认 SQL 真的写了这个条件 ——
        //    忘了写它，搜索结果就是"全站聊天记录"，这是最严重的数据泄露。
        //
        // ===================================================
        long total = messageMapper.countMyMessages(me, keyword.trim());
        List<MessageSearchVO> messageList = messageMapper.searchMyMessages(
                me, keyword.trim(), (p - 1) * size, size);
        log.info("消息搜索：userId={}, keyword={}, 命中 {} 条", me, keyword.trim(), total);
        return new PageResult(total, messageList);
    }

    /**
     * 我的未读消息总数（已写好，不用改）
     */
    @Override
    public Integer unreadTotal() {
        return messageMapper.countUnread(BaseContext.getCurrentId(), null);
    }

}
