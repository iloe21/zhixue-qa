package com.graduation.rag.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自研切分器单元测试：锁定窗口/重叠/边界回退三个核心行为
 */
class TextChunkSplitterTest {

    private final TextChunkSplitter splitter = new TextChunkSplitter(100, 20);

    @Test
    @DisplayName("null 与空白文本返回空列表")
    void nullAndBlankInput() {
        assertTrue(splitter.split(null).isEmpty());
        assertTrue(splitter.split("").isEmpty());
        assertTrue(splitter.split("   \n\t  ").isEmpty());
    }

    @Test
    @DisplayName("短于窗口的文本整体成一块并去除首尾空白")
    void shortTextSingleChunk() {
        List<String> chunks = splitter.split("  一段不长的内容。  ");
        assertEquals(1, chunks.size());
        assertEquals("一段不长的内容。", chunks.get(0));
    }

    @Test
    @DisplayName("恰好等于窗口长度的文本不切分")
    void exactWindowSize() {
        String text = repeat("x", 100);
        assertEquals(1, splitter.split(text).size());
    }

    @Test
    @DisplayName("相邻块保留 overlap 重叠，防止语义断裂")
    void overlapBetweenChunks() {
        String text = cycle("abcdefghij", 25);   // 250 字符，无边界符 → 硬切
        List<String> chunks = splitter.split(text);
        assertEquals(3, chunks.size());
        // 第二块的前 20 字符必须等于第一块的最后 20 字符（overlap=20）
        String first = chunks.get(0);
        String second = chunks.get(1);
        assertEquals(first.substring(first.length() - 20), second.substring(0, 20));
        // 全文覆盖：最后一块必须到达文末
        assertTrue(chunks.get(chunks.size() - 1).endsWith(text.substring(text.length() - 5)));
    }

    @Test
    @DisplayName("切点优先回退到窗口后 20% 内的句子边界")
    void boundaryFallback() {
        // 90 个 x + 句号 + 60 个 y：句号落在窗口 [80,100] 回退区内
        String text = repeat("x", 90) + "。" + repeat("y", 60);
        List<String> chunks = splitter.split(text);
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).endsWith("。"), "第一块应在句号处收尾");
        assertEquals(91, chunks.get(0).length());
        // 重叠使第二块从 71 开始，覆盖到文末
        assertTrue(chunks.get(1).startsWith(text.substring(71, 76)));
        assertTrue(chunks.get(1).endsWith("y"));
    }

    @Test
    @DisplayName("回退区内无边界时硬切窗口，不回退过远")
    void hardCutWhenNoBoundary() {
        // 边界符在 index 50，位于回退区 (80,100] 之外
        String text = repeat("a", 50) + "。" + repeat("b", 100);
        List<String> chunks = splitter.split(text);
        assertEquals(100, chunks.get(0).length());
        assertTrue(chunks.get(0).endsWith("b"));
    }

    @Test
    @DisplayName("CRLF/CR 统一归一为 LF")
    void lineEndingNormalization() {
        List<String> chunks = splitter.split("第一行\r\n第二行\r第三行");
        assertEquals(1, chunks.size());
        assertEquals("第一行\n第二行\n第三行", chunks.get(0));
    }

    @Test
    @DisplayName("纯空白窗口被跳过，不产生空块")
    void blankWindowSkipped() {
        String text = repeat("a", 10) + repeat(" ", 10) + repeat("b", 10);
        List<String> chunks = new TextChunkSplitter(10, 0).split(text);
        assertEquals(2, chunks.size());
        assertEquals(repeat("a", 10), chunks.get(0));
        assertEquals(repeat("b", 10), chunks.get(1));
    }

    @Test
    @DisplayName("overlap 大于窗口时循环仍前进并终止")
    void overlapLargerThanWindowStillProgresses() {
        String text = cycle("abcde", 12);  // 60 字符
        List<String> chunks = new TextChunkSplitter(10, 50).split(text);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(chunks.size() - 1).endsWith(text.substring(text.length() - 3)));
    }

    private static String repeat(String s, int times) {
        return s.repeat(times);
    }

    private static String cycle(String unit, int times) {
        return unit.repeat(times);
    }
}
