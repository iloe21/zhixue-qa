package com.graduation.rag.controller;

import com.graduation.rag.common.Result;
import com.graduation.rag.dto.ChatRequest;
import com.graduation.rag.dto.ConversationDetail;
import com.graduation.rag.dto.CreateConversationRequest;
import com.graduation.rag.dto.RenameRequest;
import com.graduation.rag.entity.Conversation;
import com.graduation.rag.entity.Message;
import com.graduation.rag.security.LoginUser;
import com.graduation.rag.service.ConversationService;
import com.graduation.rag.service.RagChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 会话与问答接口（设计文档第四章）
 *
 * SSE 事件协议（POST /{id}/chat）：
 *   delta     增量回答文本
 *   citations 引用列表 JSON（生成结束后推送一次）
 *   done      本轮结束
 *   error     错误信息（拒答走 delta+done，不走 error）
 */
@RestController
@RequestMapping("/api/conversations")
public class ChatController {

    private final ConversationService conversationService;
    private final RagChatService ragChatService;

    public ChatController(ConversationService conversationService, RagChatService ragChatService) {
        this.conversationService = conversationService;
        this.ragChatService = ragChatService;
    }

    @PostMapping
    public Result<ConversationDetail> create(@AuthenticationPrincipal LoginUser user,
                                             @Valid @RequestBody CreateConversationRequest req) {
        return Result.ok(conversationService.create(user, req));
    }

    @GetMapping
    public Result<List<Conversation>> list(@AuthenticationPrincipal LoginUser user) {
        return Result.ok(conversationService.listMine(user));
    }

    @GetMapping("/{id}")
    public Result<ConversationDetail> detail(@AuthenticationPrincipal LoginUser user,
                                             @PathVariable Long id) {
        return Result.ok(conversationService.detail(id, user));
    }

    @PutMapping("/{id}")
    public Result<Void> rename(@AuthenticationPrincipal LoginUser user,
                               @PathVariable Long id,
                               @Valid @RequestBody RenameRequest req) {
        conversationService.rename(id, req.getTitle(), user);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal LoginUser user,
                               @PathVariable Long id) {
        conversationService.delete(id, user);
        return Result.ok();
    }

    @GetMapping("/{id}/messages")
    public Result<List<Message>> messages(@AuthenticationPrincipal LoginUser user,
                                          @PathVariable Long id) {
        return Result.ok(conversationService.messages(id, user));
    }

    /** RAG 问答（SSE 流式）：5 分钟超时 */
    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@AuthenticationPrincipal LoginUser user,
                           @PathVariable Long id,
                           @Valid @RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(300_000L);
        ragChatService.streamAnswer(id, req.getQuestion(), user, emitter);
        return emitter;
    }
}
