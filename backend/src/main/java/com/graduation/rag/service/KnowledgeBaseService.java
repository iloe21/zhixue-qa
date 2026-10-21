package com.graduation.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.graduation.rag.dto.KbCreateRequest;
import com.graduation.rag.dto.KbUpdateRequest;
import com.graduation.rag.entity.ChunkMeta;
import com.graduation.rag.entity.Document;
import com.graduation.rag.entity.KnowledgeBase;
import com.graduation.rag.exception.BusinessException;
import com.graduation.rag.mapper.ChunkMetaMapper;
import com.graduation.rag.mapper.DocumentMapper;
import com.graduation.rag.mapper.KnowledgeBaseMapper;
import com.graduation.rag.security.LoginUser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 知识库服务：CRUD + 权限校验（所有者/管理员可管理，公共库所有人可读）
 */
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper documentMapper;
    private final ChunkMetaMapper chunkMetaMapper;
    private final VectorStoreManager vectorStoreManager;

    public KnowledgeBaseService(KnowledgeBaseMapper kbMapper, DocumentMapper documentMapper,
                                ChunkMetaMapper chunkMetaMapper, VectorStoreManager vectorStoreManager) {
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.chunkMetaMapper = chunkMetaMapper;
        this.vectorStoreManager = vectorStoreManager;
    }

    /** 可见知识库列表：公共库 + 自己的私有库 */
    public List<KnowledgeBase> listVisible(LoginUser user) {
        QueryWrapper<KnowledgeBase> w = new QueryWrapper<>();
        w.and(q -> q.eq("visibility", "public").or().eq("owner_id", user.userId()));
        w.orderByDesc("updated_at");
        return kbMapper.selectList(w);
    }

    public KnowledgeBase create(LoginUser user, KbCreateRequest req) {
        String visibility = req.getVisibility() == null ? "private" : req.getVisibility();
        if ("public".equals(visibility) && !isAdmin(user)) {
            throw new BusinessException(403, "只有管理员可以创建公共知识库");
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(req.getName().trim());
        kb.setDescription(req.getDescription());
        kb.setVisibility(visibility);
        kb.setOwnerId(user.userId());
        kb.setDocCount(0);
        kbMapper.insert(kb);
        return kb;
    }

    /** 读权限：公共库所有登录用户可读；私有库仅所有者与管理员 */
    public KnowledgeBase getForRead(Long id, LoginUser user) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(404, "知识库不存在");
        }
        if (!"public".equals(kb.getVisibility()) && !isOwnerOrAdmin(kb, user)) {
            throw new BusinessException(403, "无权访问该知识库");
        }
        return kb;
    }

    /** 管理权限：仅所有者与管理员 */
    public KnowledgeBase getForManage(Long id, LoginUser user) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(404, "知识库不存在");
        }
        if (!isOwnerOrAdmin(kb, user)) {
            throw new BusinessException(403, "只有所有者或管理员可以执行此操作");
        }
        return kb;
    }

    public KnowledgeBase update(Long id, KbUpdateRequest req, LoginUser user) {
        KnowledgeBase kb = getForManage(id, user);
        if (req.getName() != null && !req.getName().isBlank()) {
            kb.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            kb.setDescription(req.getDescription());
        }
        if (req.getVisibility() != null) {
            if ("public".equals(req.getVisibility()) && !isAdmin(user)) {
                throw new BusinessException(403, "只有管理员可以设置公共知识库");
            }
            kb.setVisibility(req.getVisibility());
        }
        kbMapper.updateById(kb);
        return kb;
    }

    /**
     * 删除知识库：向量 → 磁盘文件 → 数据库记录（三级清理）
     * document / chunk_meta / conversation_kb 由外键级联删除
     */
    public void delete(Long id, LoginUser user) {
        getForManage(id, user);
        // 1. 删除该库全部向量
        List<ChunkMeta> chunks = chunkMetaMapper.selectList(
                new QueryWrapper<ChunkMeta>().eq("kb_id", id));
        vectorStoreManager.deleteByIds(chunks.stream().map(c -> String.valueOf(c.getId())).toList());
        // 2. 删除磁盘上传文件
        List<Document> docs = documentMapper.selectList(
                new QueryWrapper<Document>().eq("kb_id", id));
        for (Document d : docs) {
            deleteFileQuietly(d.getFilePath());
        }
        // 3. 删除数据库记录
        kbMapper.deleteById(id);
    }

    public boolean isAdmin(LoginUser user) {
        return "admin".equals(user.role());
    }

    public boolean isOwnerOrAdmin(KnowledgeBase kb, LoginUser user) {
        return isAdmin(user) || (kb.getOwnerId() != null && kb.getOwnerId().equals(user.userId()));
    }

    static void deleteFileQuietly(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException ignored) {
            // 文件删除失败不影响主流程（记录已删）
        }
    }
}
