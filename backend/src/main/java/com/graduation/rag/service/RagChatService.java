package com.graduation.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graduation.rag.dto.Citation;
import com.graduation.rag.entity.Conversation;
import com.graduation.rag.entity.Message;
import com.graduation.rag.mapper.MessageMapper;
import com.graduation.rag.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * RAG 问答服务（设计文档 2.3 流程二 + 5.2~5.4 关键技术方案）
 *
 * 链路：向量检索(Top-K=5/阈值0.5/按知识库范围过滤)
 *      → 无结果拒答(幻觉抑制)
 *      → 组装提示词(编号引用+会话历史)
 *      → 大模型流式生成 → SSE 推送(delta/citations/done/error)
 *      → 消息与引用持久化
 */
@Service
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

    /** 拒答话术（检索不到相关内容时，宁可拒答不编造） */
    public static final String REFUSAL = "当前知识库中未找到与该问题相关的内容，无法基于教材资料回答。建议上传相关教材或讲义后再提问。";

    private final ChatModel chatModel;
    private final VectorStoreManager vectorStoreManager;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.top-k}")
    private int topK;

    @Value("${app.rag.similarity-threshold}")
    private double similarityThreshold;

    public RagChatService(ChatModel chatModel, VectorStoreManager vectorStoreManager,
                          MessageMapper messageMapper, ConversationService conversationService,
                          ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.vectorStoreManager = vectorStoreManager;
        this.messageMapper = messageMapper;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式问答主流程（异步执行，SSE 推送）
     */
    @Async("chatExecutor")
    public void streamAnswer(Long convId, String question, LoginUser user, SseEmitter emitter) {
        try {
            Conversation conv = conversationService.getForOwner(convId, user);
            List<Long> kbIds = conversationService.kbIdsOf(convId);
            if (kbIds.isEmpty()) {
                send(emitter, "error", "会话未设置知识库范围，请新建会话并选择知识库");
                emitter.complete();
                return;
            }

            conversationService.ensureTitle(conv, question);

            // 先取历史（不含本次提问），再保存用户消息
            List<org.springframework.ai.chat.messages.Message> history = loadHistory(convId);
            saveMessage(convId, "user", question, null);

            // 1. RAG 检索：按会话知识库范围过滤（metadata 为字符串，见 DocumentParseService 注释）
            Filter.Expression filter = new FilterExpressionBuilder()
                    .in("kbId", kbIds.stream().map(String::valueOf).toArray())
                    .build();
            List<Document> docs = vectorStoreManager.search(question, topK, similarityThreshold, filter);
            log.info("RAG 检索: convId={}, kbIds={}, 命中 {} 个片段", convId, kbIds, docs.size());

            // 2. 检索无结果 → 拒答（幻觉抑制核心机制）
            if (docs.isEmpty()) {
                saveMessage(convId, "assistant", REFUSAL, null);
                send(emitter, "delta", REFUSAL);
                send(emitter, "done", "");
                emitter.complete();
                conversationService.touch(conv);
                return;
            }

            // 3. 组装引用与提示词
            List<Citation> citations = buildCitations(docs);
            String citationsJson = writeJson(citations);
            List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
            promptMessages.add(new org.springframework.ai.chat.messages.SystemMessage(buildSystemPrompt(docs)));
            promptMessages.addAll(history);
            promptMessages.add(new org.springframework.ai.chat.messages.UserMessage(question));

            // 4. 大模型流式生成 → SSE 推送
            StringBuilder answer = new StringBuilder();
            chatModel.stream(new Prompt(promptMessages))
                    .doOnNext(resp -> {
                        if (resp.getResult() != null && resp.getResult().getOutput() != null) {
                            String piece = resp.getResult().getOutput().getText();
                            if (piece != null && !piece.isEmpty()) {
                                answer.append(piece);
                                send(emitter, "delta", piece);
                            }
                        }
                    })
                    .doOnError(err -> {
                        log.error("大模型调用失败: convId={}", convId, err);
                        if (answer.length() > 0) {
                            saveMessage(convId, "assistant", answer.toString(), citationsJson);
                        }
                        send(emitter, "error", "AI 服务调用失败，请稍后重试");
                        emitter.complete();
                    })
                    .doOnComplete(() -> {
                        saveMessage(convId, "assistant", answer.toString(), citationsJson);
                        send(emitter, "citations", citationsJson);
                        send(emitter, "done", "");
                        emitter.complete();
                        conversationService.touch(conv);
                        log.info("问答完成: convId={}, 回答 {} 字, 引用 {} 条", convId, answer.length(), citations.size());
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("问答处理异常: convId={}", convId, e);
            send(emitter, "error", "问答处理失败: " + e.getMessage());
            emitter.complete();
        }
    }

    // ===== 提示词工程（设计文档 5.3：幻觉抑制模板）=====
    private String buildSystemPrompt(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名严谨的学习助手。请仅根据下方\"参考资料\"回答用户问题。\n\n");
        sb.append("要求：\n");
        sb.append("1. 回答内容必须来自参考资料，禁止编造资料中不存在的信息；\n");
        sb.append("2. 每处引用参考资料时，在句末用 [引用N] 标注对应编号；\n");
        sb.append("3. 若参考资料不足以完整回答，明确说明哪些部分资料未覆盖，不要臆测；\n");
        sb.append("4. 使用清晰、结构化的中文回答，必要时分点或使用示例。\n\n");
        sb.append("参考资料：\n");
        int i = 1;
        for (Document d : docs) {
            Map<String, Object> md = d.getMetadata();
            sb.append("[引用").append(i).append("]（来源：《").append(md.get("docName"))
              .append("》片段 ").append(parseInt(md.get("chunkIndex")) + 1).append("）：\n")
              .append(d.getText()).append("\n\n");
            i++;
        }
        return sb.toString();
    }

    private List<Citation> buildCitations(List<Document> docs) {
        List<Citation> list = new ArrayList<>();
        int ref = 1;
        for (Document d : docs) {
            Map<String, Object> md = d.getMetadata();
            String text = d.getText() == null ? "" : d.getText();
            String snippet = text.length() > 120 ? text.substring(0, 120) + "…" : text;
            list.add(new Citation(
                    ref++,
                    parseLong(d.getId()),
                    parseLong(md.get("docId")),
                    String.valueOf(md.get("docName")),
                    parseInt(md.get("chunkIndex")),
                    d.getScore(),
                    snippet));
        }
        return list;
    }

    /** 最近 10 条历史消息（旧→新），维持多轮对话上下文 */
    private List<org.springframework.ai.chat.messages.Message> loadHistory(Long convId) {
        List<Message> recent = messageMapper.selectList(new QueryWrapper<Message>()
                .eq("conversation_id", convId)
                .orderByDesc("id")
                .last("LIMIT 10"));
        Collections.reverse(recent);
        List<org.springframework.ai.chat.messages.Message> out = new ArrayList<>();
        for (Message m : recent) {
            if ("user".equals(m.getRole())) {
                out.add(new org.springframework.ai.chat.messages.UserMessage(m.getContent()));
            } else if ("assistant".equals(m.getRole())) {
                out.add(new org.springframework.ai.chat.messages.AssistantMessage(m.getContent()));
            }
        }
        return out;
    }

    private void saveMessage(Long convId, String role, String content, String citationsJson) {
        Message m = new Message();
        m.setConversationId(convId);
        m.setRole(role);
        m.setContent(content);
        m.setCitations(citationsJson);
        messageMapper.insert(m);
    }

    private String writeJson(List<Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (Exception e) {
            log.warn("引用序列化失败", e);
            return "[]";
        }
    }

    /** SSE 发送封装：客户端断开时静默降级，不影响消息落库 */
    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data == null ? "" : data));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private long parseLong(Object v) {
        try {
            return v == null ? 0L : Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private int parseInt(Object v) {
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
