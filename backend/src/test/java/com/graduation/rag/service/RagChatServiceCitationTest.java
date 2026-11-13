package com.graduation.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graduation.rag.dto.Citation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引用溯源对齐测试：锁定「提示词 [引用N] 编号 ↔ Citation.ref ↔ chunkIndex」三者一致。
 *
 * 这是引用溯源功能的核心契约：前端按 Citation.ref 渲染角标，用户点击后
 * 按 chunkId 回查原文；若编号与提示词错位，溯源即失效。
 */
class RagChatServiceCitationTest {

    private final RagChatService service = new RagChatService(
            Mockito.mock(ChatModel.class),
            Mockito.mock(VectorStoreManager.class),
            Mockito.mock(com.graduation.rag.mapper.MessageMapper.class),
            Mockito.mock(ConversationService.class),
            new ObjectMapper());

    private Document doc(String id, String docName, int chunkIndex, String text, double score) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(Map.of(
                        "docId", "7",
                        "docName", docName,
                        "kbId", "3",
                        "chunkIndex", chunkIndex))
                .score(score)
                .build();
    }

    @Test
    @DisplayName("提示词编号与 Citation.ref 逐一对应")
    void promptRefNumbersMatchCitations() {
        List<Document> docs = List.of(
                doc("101", "Java讲义", 0, "JVM 是 Java 虚拟机。", 0.42),
                doc("102", "Java讲义", 3, "垃圾回收由 GC 线程负责。", 0.35),
                doc("103", "网络笔记", 1, "TCP 三次握手建立连接。", 0.31));

        String prompt = service.buildSystemPrompt(docs);
        List<Citation> citations = service.buildCitations(docs);

        assertEquals(3, citations.size());
        for (int i = 0; i < docs.size(); i++) {
            int ref = i + 1;
            Citation c = citations.get(i);
            assertEquals(ref, c.ref(), "Citation 编号应连续从 1 开始");
            assertTrue(prompt.contains("[引用" + ref + "]"), "提示词应包含编号 " + ref);
            // 提示词中的片段序号是 1-based 展示，与 0-based chunkIndex 差 1
            assertTrue(prompt.contains("片段 " + (c.chunkIndex() + 1) + "）"));
            // 同一段落的原文必须出现在对应编号之后、下一编号之前
            int from = prompt.indexOf("[引用" + ref + "]");
            int to = prompt.indexOf("[引用" + (ref + 1) + "]");
            String section = to > 0 ? prompt.substring(from, to) : prompt.substring(from);
            assertTrue(section.contains(docs.get(i).getText()), "编号 " + ref + " 的正文应是对应片段原文");
        }
    }

    @Test
    @DisplayName("Citation 携带 chunkId/docName/score 供前端溯源")
    void citationCarriesTraceFields() {
        Document d = doc("101", "Java讲义", 4, "HashMap 非线程安全。", 0.38);
        Citation c = service.buildCitations(List.of(d)).get(0);
        assertEquals(101L, c.chunkId(), "chunkId 应等于向量库 Document id（与 chunk_meta.id 对齐）");
        assertEquals("Java讲义", c.docName());
        assertEquals(4, c.chunkIndex());
        assertEquals(0.38, c.score(), 1e-9);
        assertEquals("HashMap 非线程安全。", c.snippet());
    }

    @Test
    @DisplayName("超长片段摘要截断到 120 字符并加省略号")
    void snippetTruncatedAt120() {
        String longText = "甲".repeat(200);
        Citation c = service.buildCitations(List.of(doc("1", "书", 0, longText, 0.3))).get(0);
        assertEquals(121, c.snippet().length());
        assertTrue(c.snippet().endsWith("…"));
    }
}
