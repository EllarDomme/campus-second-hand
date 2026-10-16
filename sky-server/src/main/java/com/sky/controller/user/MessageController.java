package com.sky.controller.user;

import com.sky.dto.MessageSendDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.MessageService;
import com.sky.vo.ConversationVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端-私信接口
 */
@RestController("userMessageController")
@RequestMapping("/user/message")
@Api(tags = "C端-私信接口")
@Slf4j
public class MessageController {

    @Autowired
    private MessageService messageService;

    /**
     * 打开会话（点商品详情页的"联系卖家"）
     * 用法：POST /user/message/conversation?productId=3
     */
    @PostMapping("/conversation")
    @ApiOperation("打开与卖家的会话（已有则直接返回）")
    public Result<Long> openConversation(@RequestParam Long productId) {
        return Result.success(messageService.openConversation(productId));
    }

    @GetMapping("/conversation/list")
    @ApiOperation("我的会话列表")
    public Result<List<ConversationVO>> listConversations() {
        return Result.success(messageService.listConversations());
    }

    /**
     * 聊天记录
     * 用法：GET /user/message/history?conversationId=1&page=1&pageSize=20
     */
    @GetMapping("/history")
    @ApiOperation("聊天记录（分页，倒序）")
    public Result<PageResult> history(@RequestParam Long conversationId,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(messageService.history(conversationId, page, pageSize));
    }

    @PostMapping("/send")
    @ApiOperation("发送消息")
    public Result send(@RequestBody MessageSendDTO sendDTO) {
        log.info("发送私信：conversationId={}", sendDTO.getConversationId());
        messageService.send(sendDTO);
        return Result.success();
    }

    @PutMapping("/read/{conversationId}")
    @ApiOperation("把该会话的未读消息标记为已读")
    public Result markRead(@PathVariable Long conversationId) {
        messageService.markRead(conversationId);
        return Result.success();
    }

    @GetMapping("/unread")
    @ApiOperation("我的未读消息总数")
    public Result<Integer> unreadTotal() {
        return Result.success(messageService.unreadTotal());
    }

    /**
     * 消息搜索（新功能 A）
     * 用法：GET /user/message/search?keyword=宿舍&page=1&pageSize=20
     */
    @GetMapping("/search")
    @ApiOperation("在聊天记录里搜关键词（只搜我参与的会话）")
    public Result<PageResult> search(@RequestParam String keyword,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(messageService.search(keyword, page, pageSize));
    }

}
