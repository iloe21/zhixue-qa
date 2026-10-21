package com.graduation.rag.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 自研文本切分器：固定窗口 + 段落边界回退 + 重叠窗口
 * （对应设计文档 5.1 切分策略）
 *
 * 算法：
 * 1. 以 chunkChars（默认800字符，中文约500 token）为窗口滑动切分
 * 2. 每个切点优先回退到最近的段落/句子边界（\n 。！？；.!?;），
 *    回退范围限制在窗口后 20% 内，避免块过短
 * 3. 相邻块保留 overlapChars（默认120字符）重叠，防止语义在切点处断裂
 */
@Component
public class TextChunkSplitter {

    private static final char[] BOUNDARY_CHARS = {'\n', '。', '！', '？', '；', '.', '!', '?', ';'};

    private final int chunkChars;
    private final int overlapChars;

    public TextChunkSplitter(@Value("${app.parse.chunk-chars}") int chunkChars,
                             @Value("${app.parse.overlap-chars}") int overlapChars) {
        this.chunkChars = chunkChars;
        this.overlapChars = overlapChars;
    }

    public List<String> split(String text) {
        if (text == null) {
            return List.of();
        }
        String t = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (t.isEmpty()) {
            return List.of();
        }
        if (t.length() <= chunkChars) {
            return List.of(t);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < t.length()) {
            int end = Math.min(start + chunkChars, t.length());
            if (end < t.length()) {
                // 在窗口后 20% 范围内向前找段落/句子边界
                int boundary = findBoundary(t, start + (int) (chunkChars * 0.8), end);
                if (boundary > start) {
                    end = boundary;
                }
            }
            String chunk = t.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= t.length()) {
                break;
            }
            // 下一块从重叠位置开始；start+1 兜底保证循环必然前进
            start = Math.max(end - overlapChars, start + 1);
        }
        return chunks;
    }

    /** 从 to 向 from 反向找边界字符，返回边界字符之后的位置；找不到返回 -1 */
    private int findBoundary(String t, int from, int to) {
        for (int i = to; i > from; i--) {
            char c = t.charAt(i - 1);
            for (char b : BOUNDARY_CHARS) {
                if (c == b) {
                    return i;
                }
            }
        }
        return -1;
    }
}
