package com.graduation.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.graduation.rag.dto.ConversationDetail;
import com.graduation.rag.dto.CreateConversationRequest;
import com.graduation.rag.entity.Conversation;
import com.graduation.rag.entity.ConversationKb;
import com.graduation.rag.entity.KnowledgeBase;
import com.graduation.rag.entity.Message;
import com.graduation.rag.exception.BusinessException;
import com.graduation.rag.mapper.ConversationKbMapper;
import com.graduation.rag.mapper.ConversationMapper;
import com.graduation.rag.mapper.KnowledgeBaseMapper;
import com.graduation.rag.mapper.MessageMapper;
import com.graduation.rag.security.LoginUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话服务：创建（含知识库范围与访问校验）、列表、重命名、删除、历史消息
 * 权限：会话仅所有者可访问
 */
@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationKbMapper conversationKbMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final MessageMapper messageMapper;
    private final KnowledgeBaseService kbService;

    public ConversationService(ConversationMapper conversationMapper,
                               ConversationKbMapper conversationKbMapper,
                               KnowledgeBaseMapper kbMapper,
                               MessageMapper messageMapper,
                               KnowledgeBaseService kbService) {
        this.conversationMapper = conversationMapper;
        this.conversationKbMapper = conversationKbMapper;
        this.kbMapper = kbMapper;
        this.messageMapper = messageMapper;
        this.kbService = kbService;
    }

    /**
     * 创建会话：逐个校验知识库读权限（防止把别人的私有库选进检索范围）
     */
    public ConversationDetail create(LoginUser user, CreateConversationRequest req) {
        List<String> kbNames = new ArrayList<>();
        for (Long kbId : req.getKbIds()) {
            KnowledgeBase kb = kbService.getForRead(kbId, user);
            kbNames.add(kb.getName());
        }
        Conversation conv = new Conversation();
        conv.setUserId(user.userId());
        conv.setTitle(req.getTitle() != null && !req.getTitle().isBlank() ? req.getTitle().trim() : null);
        conversationMapper.insert(conv);

        for (Long kbId : req.getKbIds()) {
            ConversationKb ck = new ConversationKb();
            ck.setConversationId(conv.getId());
            ck.setKbId(kbId);
            conversationKbMapper.insert(ck);
        }
        return new ConversationDetail(conv, List.copyOf(req.getKbIds()), kbNames);
    }

    public List<Conversation> listMine(LoginUser user) {
        return conversationMapper.selectList(new QueryWrapper<Conversation>()
                .eq("user_id", user.userId())
                .orderByDesc("updated_at"));
    }

    public ConversationDetail detail(Long id, LoginUser user) {
        Conversation conv = getForOwner(id, user);
        List<Long> kbIds = kbIdsOf(id);
        List<String> kbNames = new ArrayList<>();
        if (!kbIds.isEmpty()) {
            for (KnowledgeBase kb : kbMapper.selectBatchIds(kbIds)) {
                kbNames.add(kb.getName());
            }
        }
        return new ConversationDetail(conv, kbIds, kbNames);
    }

    public List<Long> kbIdsOf(Long conversationId) {
        return conversationKbMapper.selectList(new QueryWrapper<ConversationKb>()
                        .eq("conversation_id", conversationId))
                .stream().map(ConversationKb::getKbId).toList();
    }

    public void rename(Long id, String title, LoginUser user) {
        Conversation conv = getForOwner(id, user);
        conv.setTitle(title.trim());
        conversationMapper.updateById(conv);
    }

    public void delete(Long id, LoginUser user) {
        getForOwner(id, user);
        conversationMapper.deleteById(id);   // message / conversation_kb 外键级联删除
    }

    public List<Message> messages(Long id, LoginUser user) {
        getForOwner(id, user);
        return messageMapper.selectList(new QueryWrapper<Message>()
                .eq("conversation_id", id)
                .orderByAsc("id"));
    }

    /** 会话仅所有者可访问 */
    public Conversation getForOwner(Long id, LoginUser user) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!conv.getUserId().equals(user.userId())) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return conv;
    }

    /** 首问自动生成会话标题（前 20 字） */
    public void ensureTitle(Conversation conv, String question) {
        if (conv.getTitle() == null || conv.getTitle().isBlank()) {
            String title = question.length() > 20 ? question.substring(0, 20) + "…" : question;
            conv.setTitle(title);
            conversationMapper.updateById(conv);
        }
    }

    /** 刷新会话活跃时间（侧边栏按最近更新排序） */
    public void touch(Conversation conv) {
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }
}
