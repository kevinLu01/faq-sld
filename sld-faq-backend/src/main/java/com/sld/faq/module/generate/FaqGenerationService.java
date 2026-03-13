package com.sld.faq.module.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sld.faq.infrastructure.llm.LlmClient;
import com.sld.faq.infrastructure.storage.MinioStorage;
import com.sld.faq.module.candidate.entity.FaqCandidate;
import com.sld.faq.module.candidate.mapper.FaqCandidateMapper;
import com.sld.faq.module.file.entity.KbChunk;
import com.sld.faq.module.file.entity.KbFile;
import com.sld.faq.module.file.entity.KbTask;
import com.sld.faq.module.file.mapper.KbChunkMapper;
import com.sld.faq.module.file.mapper.KbFileMapper;
import com.sld.faq.module.file.mapper.KbTaskMapper;
import com.sld.faq.module.generate.dto.FaqCandidateDto;
import com.sld.faq.module.parse.ChunkService;
import com.sld.faq.module.parse.DocumentParseService;
import com.sld.faq.module.parse.TextCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * FAQ 异步生成服务
 * <p>
 * 执行完整的文件解析 → 文本切块 → LLM 生成 → 候选保存流程。
 * 任务进度同步写入 Redis（TTL 1小时）和 kb_task 表，供前端轮询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqGenerationService {

    private static final String TASK_REDIS_KEY_PREFIX = "task:";
    private static final long TASK_REDIS_TTL_HOURS = 1L;

    private final KbFileMapper kbFileMapper;
    private final KbTaskMapper kbTaskMapper;
    private final KbChunkMapper kbChunkMapper;
    private final FaqCandidateMapper faqCandidateMapper;
    private final MinioStorage minioStorage;
    private final DocumentParseService documentParseService;
    private final TextCleaner textCleaner;
    private final ChunkService chunkService;
    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final FaqJsonParser faqJsonParser;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 自注入自身代理，使 saveChunks/saveCandidates 上的 @Transactional 通过 AOP 代理生效。
     * 使用 @Lazy 避免循环依赖问题。
     */
    @Lazy
    @Autowired
    private FaqGenerationService self;

    /**
     * 异步生成 FAQ
     * <p>
     * 完整流程：下载文件 → 解析文档 → 清洗文本 → 切块 → 保存 chunks →
     * 逐块调 LLM → 解析 JSON → 保存候选 → 更新任务状态
     *
     * @param fileId 文件 ID
     * @param taskId 任务 ID
     */
    @Async("faqTaskExecutor")
    public void generateAsync(Long fileId, Long taskId) {
        log.info("FAQ 生成任务开始: fileId={}, taskId={}", fileId, taskId);

        try {
            // 1. 更新 task 状态为 RUNNING
            updateTask(taskId, "RUNNING", 0, null);

            // 2. 查询文件记录
            KbFile kbFile = kbFileMapper.selectById(fileId);
            if (kbFile == null) {
                failTask(taskId, "文件记录不存在: fileId=" + fileId);
                return;
            }

            // 3. 从 MinIO 下载文件字节
            byte[] fileBytes;
            try (InputStream is = minioStorage.download(kbFile.getMinioPath())) {
                fileBytes = is.readAllBytes();
            } catch (Exception e) {
                log.error("从 MinIO 下载文件失败: fileId={}", fileId, e);
                failTask(taskId, "下载文件失败: " + e.getMessage());
                return;
            }

            // 4. DocumentParseService.parse() 得到原始文本
            String rawText = documentParseService.parse(kbFile, fileBytes);
            if (rawText == null || rawText.isBlank()) {
                failTask(taskId, "文档解析失败或内容为空");
                return;
            }
            updateTask(taskId, "RUNNING", 10, null);

            // 5. TextCleaner.clean()
            String cleanText = textCleaner.clean(rawText);
            if (cleanText.isBlank()) {
                failTask(taskId, "文本清洗后内容为空");
                return;
            }

            // 6. ChunkService.chunk() 得到 chunk 列表
            List<String> chunks = chunkService.chunk(cleanText);
            if (chunks.isEmpty()) {
                failTask(taskId, "文本切块结果为空");
                return;
            }
            updateTask(taskId, "RUNNING", 15, null);

            // 7. 保存所有 KbChunk 到数据库（通过代理调用确保事务生效）
            List<KbChunk> savedChunks = self.saveChunks(fileId, chunks, rawText, cleanText);

            // 8. 更新 kb_file.chunkCount
            KbFile fileUpdate = new KbFile();
            fileUpdate.setId(fileId);
            fileUpdate.setChunkCount(savedChunks.size());
            fileUpdate.setParseStatus("SUCCESS");
            kbFileMapper.updateById(fileUpdate);

            updateTask(taskId, "RUNNING", 20, null);

            // 9. 遍历 chunks，逐块调 LLM 生成 FAQ 候选
            int total = savedChunks.size();
            int llmFailureCount = 0;
            int generatedCandidateCount = 0;
            List<String> llmErrors = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                KbChunk chunk = savedChunks.get(i);
                int progress = 20 + (i * 80 / total);
                updateTask(taskId, "RUNNING", progress, null);

                try {
                    String prompt = promptBuilder.build(chunk.getCleanContent(), PromptMode.DOCUMENT);
                    String llmOutput = llmClient.chat(prompt);
                    List<FaqCandidateDto> candidates = faqJsonParser.parse(llmOutput);
                    self.saveCandidates(fileId, chunk.getId(), candidates);
                    generatedCandidateCount += candidates.size();
                } catch (Exception e) {
                    llmFailureCount++;
                    llmErrors.add(buildChunkError(chunk.getId(), e));
                    log.warn("处理 chunk 时异常，跳过: fileId={}, chunkId={}, error={}",
                            fileId, chunk.getId(), e.getMessage());
                }
            }

            // 10. 汇总结果，判断是否全部失败
            log.info("FAQ 生成统计: taskId={}, candidates={}, llmFailures={}",
                    taskId, generatedCandidateCount, llmFailureCount);
            if (generatedCandidateCount == 0 && llmFailureCount > 0) {
                failTask(taskId, buildGenerationFailureMessage(llmFailureCount, total, llmErrors));
                return;
            }

            updateTask(taskId, "SUCCESS", 100, null);
            log.info("FAQ 生成任务完成: fileId={}, taskId={}, chunks={}, candidates={}, llmFailures={}",
                    fileId, taskId, total, generatedCandidateCount, llmFailureCount);

        } catch (Exception e) {
            log.error("FAQ 生成任务异常: fileId={}, taskId={}", fileId, taskId, e);
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            failTask(taskId, errMsg.length() > 500 ? errMsg.substring(0, 500) : errMsg);
        }
    }

    /**
     * 保存文本块到 kb_chunk 表（独立事务，确保批量写入原子性）
     * <p>
     * rawContent 使用原始文本（截取对应位置），cleanContent 使用 chunk 清洗内容。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<KbChunk> saveChunks(Long fileId, List<String> chunkTexts, String rawText, String cleanText) {
        List<KbChunk> chunks = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunkContent = chunkTexts.get(i);
            KbChunk chunk = new KbChunk();
            chunk.setFileId(fileId);
            chunk.setChunkIndex(i);
            chunk.setRawContent(chunkContent);
            chunk.setCleanContent(chunkContent);
            chunk.setTokenCount(estimateTokenCount(chunkContent));
            chunk.setMetadata("{\"chunkIndex\":" + i + "}");
            chunk.setCreatedAt(LocalDateTime.now());
            kbChunkMapper.insert(chunk);
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * 保存 FAQ 候选到 faq_candidate 表（独立事务，确保每批候选写入原子性）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveCandidates(Long fileId, Long chunkId, List<FaqCandidateDto> dtos) {
        for (FaqCandidateDto dto : dtos) {
            FaqCandidate candidate = new FaqCandidate();
            candidate.setFileId(fileId);
            candidate.setChunkId(chunkId);
            candidate.setQuestion(dto.getQuestion());
            candidate.setAnswer(dto.getAnswer());
            candidate.setCategory(dto.getCategory());
            candidate.setKeywords(dto.getKeywords());
            candidate.setSourceSummary(dto.getSourceSummary());
            candidate.setConfidence(dto.getConfidence());
            candidate.setStatus("PENDING");
            faqCandidateMapper.insert(candidate);
        }
    }

    /**
     * 更新任务状态，并同步写入 Redis
     */
    private void updateTask(Long taskId, String status, int progress, String errorMsg) {
        KbTask task = new KbTask();
        task.setId(taskId);
        task.setStatus(status);
        task.setProgress(progress);
        task.setErrorMsg(errorMsg);
        kbTaskMapper.updateById(task);

        writeTaskToRedis(taskId, status, progress, errorMsg);
    }

    /**
     * 将任务状态标记为失败
     */
    private void failTask(Long taskId, String errorMsg) {
        log.warn("任务失败: taskId={}, error={}", taskId, errorMsg);
        updateTask(taskId, "FAILED", 0, errorMsg);
    }

    private String buildGenerationFailureMessage(int llmFailureCount, int totalChunks, List<String> llmErrors) {
        String prefix = String.format("FAQ 生成失败：%d/%d 个 chunk 处理异常", llmFailureCount, totalChunks);
        if (llmErrors.isEmpty()) {
            return prefix;
        }
        return prefix + "； " + llmErrors.get(0);
    }

    private String buildChunkError(Long chunkId, Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return String.format("chunkId=%d, error=%s", chunkId, message);
    }

    /**
     * 写入 Redis，key = task:{taskId}，TTL = 1 小时
     * 格式：{"id":1,"status":"RUNNING","progress":45,"errorMsg":null}
     */
    private void writeTaskToRedis(Long taskId, String status, int progress, String errorMsg) {
        try {
            Map<String, Object> taskMap = new HashMap<>();
            taskMap.put("id", taskId);
            taskMap.put("status", status);
            taskMap.put("progress", progress);
            taskMap.put("errorMsg", errorMsg);
            String json = objectMapper.writeValueAsString(taskMap);
            redisTemplate.opsForValue().set(
                    TASK_REDIS_KEY_PREFIX + taskId,
                    json,
                    TASK_REDIS_TTL_HOURS,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("写入 Redis 任务状态失败: taskId={}", taskId, e);
            // Redis 写入失败不影响主流程
        }
    }

    /**
     * 简单估算 token 数（中文约 1 字 1 token，英文约 4 字符 1 token）
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c > 0x7F) {
                count++;
            } else {
                count += 1;
            }
        }
        // 英文部分按平均 4 字符/token 简化
        return count;
    }
}
