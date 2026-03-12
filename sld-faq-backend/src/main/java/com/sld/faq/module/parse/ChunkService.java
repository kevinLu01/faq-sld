package com.sld.faq.module.parse;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块组件
 * <p>
 * 将清洗后的文本按段落切分为大小合适的 chunk，供 LLM 逐块处理。
 */
@Component
public class ChunkService {

    private static final int MAX_CHUNK_SIZE = 800;
    private static final int OVERLAP_SIZE = 100;
    private static final int MIN_CHUNK_SIZE = 10;

    /**
     * 切分策略：
     * 1. 按双换行（段落边界）切分
     * 2. 合并过短段落直到接近 MAX_CHUNK_SIZE
     * 3. 超过 MAX_CHUNK_SIZE 强制截断
     * 4. 相邻 chunk 保留 OVERLAP_SIZE 字符重叠（取上一个 chunk 末尾）
     * 5. 过滤 length < MIN_CHUNK_SIZE 的 chunk
     *
     * @param cleanText 清洗后的文本
     * @return chunk 文本列表
     */
    public List<String> chunk(String cleanText) {
        if (cleanText == null || cleanText.isBlank()) {
            return List.of();
        }

        // 按段落边界切分
        String[] paragraphs = cleanText.split("\\n\\n+");

        // 合并过短段落，形成目标大小的段落组
        List<String> merged = mergeParagraphs(paragraphs);

        // 超长段落强制截断
        List<String> rawChunks = new ArrayList<>();
        for (String block : merged) {
            if (block.length() <= MAX_CHUNK_SIZE) {
                rawChunks.add(block);
            } else {
                rawChunks.addAll(splitLongBlock(block));
            }
        }

        // 添加重叠并过滤过短 chunk
        List<String> result = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            String current = rawChunks.get(i);
            if (i > 0) {
                // 取上一个 chunk 的末尾作为重叠前缀
                String prev = rawChunks.get(i - 1);
                String overlap = prev.length() > OVERLAP_SIZE
                        ? prev.substring(prev.length() - OVERLAP_SIZE)
                        : prev;
                current = overlap + current;
                // 超出上限时截断
                if (current.length() > MAX_CHUNK_SIZE + OVERLAP_SIZE) {
                    current = current.substring(0, MAX_CHUNK_SIZE + OVERLAP_SIZE);
                }
            }
            String trimmed = current.strip();
            if (trimmed.length() >= MIN_CHUNK_SIZE) {
                result.add(trimmed);
            }
        }

        return result;
    }

    /**
     * 合并过短段落，使每个块接近但不超过 MAX_CHUNK_SIZE
     */
    private List<String> mergeParagraphs(String[] paragraphs) {
        List<String> merged = new ArrayList<>();
        StringBuilder buf = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (buf.length() == 0) {
                buf.append(trimmed);
            } else if (buf.length() + 2 + trimmed.length() <= MAX_CHUNK_SIZE) {
                buf.append("\n\n").append(trimmed);
            } else {
                merged.add(buf.toString());
                buf = new StringBuilder(trimmed);
            }
        }
        if (buf.length() > 0) {
            merged.add(buf.toString());
        }
        return merged;
    }

    /**
     * 将超长块按 MAX_CHUNK_SIZE 强制截断
     */
    private List<String> splitLongBlock(String block) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < block.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, block.length());
            parts.add(block.substring(start, end));
            start = end;
        }
        return parts;
    }
}
