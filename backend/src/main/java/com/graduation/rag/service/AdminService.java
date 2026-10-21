package com.graduation.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.rag.dto.StatsResponse;
import com.graduation.rag.entity.ChunkMeta;
import com.graduation.rag.entity.Conversation;
import com.graduation.rag.entity.Document;
import com.graduation.rag.entity.Exercise;
import com.graduation.rag.entity.KnowledgeBase;
import com.graduation.rag.entity.Message;
import com.graduation.rag.entity.User;
import com.graduation.rag.exception.BusinessException;
import com.graduation.rag.mapper.ChunkMetaMapper;
import com.graduation.rag.mapper.ConversationMapper;
import com.graduation.rag.mapper.DocumentMapper;
import com.graduation.rag.mapper.ExerciseMapper;
import com.graduation.rag.mapper.KnowledgeBaseMapper;
import com.graduation.rag.mapper.MessageMapper;
import com.graduation.rag.mapper.UserMapper;
import com.graduation.rag.security.LoginUser;
import org.springframework.stereotype.Service;

/**
 * 管理服务：仪表盘统计 + 用户管理
 * 安全设计：不能禁用/改角色自己的账户（防止管理员自锁）
 */
@Service
public class AdminService {

    private final UserMapper userMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper documentMapper;
    private final ChunkMetaMapper chunkMetaMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ExerciseMapper exerciseMapper;

    public AdminService(UserMapper userMapper, KnowledgeBaseMapper kbMapper,
                        DocumentMapper documentMapper, ChunkMetaMapper chunkMetaMapper,
                        ConversationMapper conversationMapper, MessageMapper messageMapper,
                        ExerciseMapper exerciseMapper) {
        this.userMapper = userMapper;
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.chunkMetaMapper = chunkMetaMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.exerciseMapper = exerciseMapper;
    }

    /** 仪表盘八维统计（问答数 = AI 回答消息数） */
    public StatsResponse stats() {
        return new StatsResponse(
                userMapper.selectCount(null),
                kbMapper.selectCount(null),
                kbMapper.selectCount(new QueryWrapper<KnowledgeBase>().eq("visibility", "public")),
                documentMapper.selectCount(null),
                chunkMetaMapper.selectCount(null),
                conversationMapper.selectCount(null),
                messageMapper.selectCount(new QueryWrapper<Message>().eq("role", "assistant")),
                exerciseMapper.selectCount(null));
    }

    /** 用户分页列表（可按用户名/昵称搜索） */
    public Page<User> listUsers(long page, long size, String keyword) {
        QueryWrapper<User> w = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("username", keyword).or().like("nickname", keyword));
        }
        w.orderByDesc("id");
        return userMapper.selectPage(new Page<>(page, size), w);
    }

    public void updateStatus(Long id, Integer status, LoginUser admin) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不合法（0 或 1）");
        }
        if (id.equals(admin.userId())) {
            throw new BusinessException("不能禁用自己的账户");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    public void updateRole(Long id, String role, LoginUser admin) {
        if (id.equals(admin.userId())) {
            throw new BusinessException("不能修改自己的角色");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setRole(role);
        userMapper.updateById(user);
    }
}
