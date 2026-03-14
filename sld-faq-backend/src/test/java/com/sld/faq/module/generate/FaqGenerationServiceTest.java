package com.sld.faq.module.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sld.faq.infrastructure.llm.LlmClient;
import com.sld.faq.infrastructure.storage.MinioStorage;
import com.sld.faq.module.candidate.mapper.FaqCandidateMapper;
import com.sld.faq.module.file.entity.KbChunk;
import com.sld.faq.module.file.entity.KbFile;
import com.sld.faq.module.file.entity.KbTask;
import com.sld.faq.module.file.mapper.KbChunkMapper;
import com.sld.faq.module.file.mapper.KbFileMapper;
import com.sld.faq.module.file.mapper.KbTaskMapper;
import com.sld.faq.module.parse.ChunkService;
import com.sld.faq.module.parse.DocumentParseService;
import com.sld.faq.module.parse.TextCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FaqGenerationService 浠诲姟鐘舵€佹祴璇?")
class FaqGenerationServiceTest {

    @Mock
    private KbFileMapper kbFileMapper;

    @Mock
    private KbTaskMapper kbTaskMapper;

    @Mock
    private KbChunkMapper kbChunkMapper;

    @Mock
    private FaqCandidateMapper faqCandidateMapper;

    @Mock
    private MinioStorage minioStorage;

    @Mock
    private DocumentParseService documentParseService;

    @Mock
    private TextCleaner textCleaner;

    @Mock
    private ChunkService chunkService;

    @Mock
    private LlmClient llmClient;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private FaqJsonParser faqJsonParser;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    @InjectMocks
    private FaqGenerationService faqGenerationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(faqGenerationService, "self", faqGenerationService);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("LLM 鍏ㄩ儴澶辫触涓斾负 0 鍊欓€夋椂搴旀爣璁颁负 FAILED")
    void generateAsync_whenAllChunksFail_marksTaskFailed() throws Exception {
        KbFile kbFile = buildFile(11L);
        when(kbFileMapper.selectById(11L)).thenReturn(kbFile);
        when(minioStorage.download(anyString())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(documentParseService.parse(any(KbFile.class), any())).thenReturn("raw text");
        when(textCleaner.clean("raw text")).thenReturn("clean text");
        when(chunkService.chunk("clean text")).thenReturn(List.of("chunk-a"));
        when(promptBuilder.build("chunk-a", PromptMode.DOCUMENT)).thenReturn("prompt-a");
        when(llmClient.chat("prompt-a")).thenThrow(new RuntimeException("vllm unavailable"));
        doReturn(List.of(buildChunk(101L, 11L, "chunk-a"))).when(faqGenerationService)
                .saveChunks(anyLong(), any(), anyString(), anyString());

        faqGenerationService.generateAsync(11L, 21L);

        ArgumentCaptor<KbTask> taskCaptor = ArgumentCaptor.forClass(KbTask.class);
        verify(kbTaskMapper, org.mockito.Mockito.atLeastOnce()).updateById(taskCaptor.capture());
        KbTask lastTask = taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1);
        assertThat(lastTask.getStatus()).isEqualTo("FAILED");
        assertThat(lastTask.getErrorMsg()).contains("chunkId=101");
        assertThat(lastTask.getErrorMsg()).contains("vllm unavailable");
        verify(faqGenerationService, never()).saveCandidates(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("LLM 鎴愬姛浣嗘湭鐢熸垚 FAQ 鍊欓€夋椂浠诲姟浠嶇劧鎴愬姛")
    void generateAsync_whenNoCandidatesButNoErrors_marksTaskSuccess() throws Exception {
        KbFile kbFile = buildFile(12L);
        when(kbFileMapper.selectById(12L)).thenReturn(kbFile);
        when(minioStorage.download(anyString())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(documentParseService.parse(any(KbFile.class), any())).thenReturn("raw text");
        when(textCleaner.clean("raw text")).thenReturn("clean text");
        when(chunkService.chunk("clean text")).thenReturn(List.of("chunk-a"));
        when(promptBuilder.build("chunk-a", PromptMode.DOCUMENT)).thenReturn("prompt-a");
        when(llmClient.chat("prompt-a")).thenReturn("{\"faqs\":[]}");
        when(faqJsonParser.parse("{\"faqs\":[]}")).thenReturn(List.of());
        doReturn(List.of(buildChunk(102L, 12L, "chunk-a"))).when(faqGenerationService)
                .saveChunks(anyLong(), any(), anyString(), anyString());

        faqGenerationService.generateAsync(12L, 22L);

        ArgumentCaptor<KbTask> taskCaptor = ArgumentCaptor.forClass(KbTask.class);
        verify(kbTaskMapper, org.mockito.Mockito.atLeastOnce()).updateById(taskCaptor.capture());
        KbTask lastTask = taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1);
        assertThat(lastTask.getStatus()).isEqualTo("SUCCESS");
        assertThat(lastTask.getErrorMsg()).isNull();
    }

    private KbFile buildFile(Long fileId) {
        KbFile kbFile = new KbFile();
        kbFile.setId(fileId);
        kbFile.setMinioPath("test/file.txt");
        kbFile.setFileType("txt");
        return kbFile;
    }

    private KbChunk buildChunk(Long chunkId, Long fileId, String content) {
        KbChunk chunk = new KbChunk();
        chunk.setId(chunkId);
        chunk.setFileId(fileId);
        chunk.setChunkIndex(0);
        chunk.setRawContent(content);
        chunk.setCleanContent(content);
        return chunk;
    }
}
