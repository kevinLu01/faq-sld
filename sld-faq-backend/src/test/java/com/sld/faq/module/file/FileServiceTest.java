package com.sld.faq.module.file;

import com.sld.faq.common.BusinessException;
import com.sld.faq.infrastructure.storage.MinioStorage;
import com.sld.faq.module.file.entity.KbTask;
import com.sld.faq.module.file.mapper.KbFileMapper;
import com.sld.faq.module.file.mapper.KbTaskMapper;
import com.sld.faq.module.file.vo.TaskStatusVO;
import com.sld.faq.module.generate.FaqGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * FileService 单元测试（可纯单元测试的部分）
 * <p>
 * 覆盖不依赖外部存储的业务逻辑：
 * 1. 不支持的文件类型应抛出 BusinessException
 * 2. 超过 50MB 的文件应抛出 BusinessException
 * 3. 正常查询 task 状态返回 TaskStatusVO
 * 4. 不存在的 taskId 查询应抛出 BusinessException
 * <p>
 * 注意：upload 方法中调用 MinioStorage 的部分需要 Mock，
 * 因此只测试在到达 MinIO 调用之前即可触发的校验逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileService 文件服务测试")
class FileServiceTest {

    @Mock
    private KbFileMapper kbFileMapper;

    @Mock
    private KbTaskMapper kbTaskMapper;

    @Mock
    private MinioStorage minioStorage;

    @Mock
    private FaqGenerationService faqGenerationService;

    @InjectMocks
    private FileService fileService;

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    // ========== upload 校验测试 ==========

    @Test
    @DisplayName("上传不支持的文件类型（.exe）应抛出 BusinessException")
    void upload_invalidFileType_throwsBusinessException() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file",
                "恶意程序.exe",
                "application/octet-stream",
                new byte[1024]
        );

        assertThatThrownBy(() -> fileService.upload(exeFile, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");

        // 不支持类型时不应访问 MinIO
        verifyNoInteractions(minioStorage);
    }

    @Test
    @DisplayName("上传不支持的文件类型（.pptx）应抛出 BusinessException")
    void upload_pptxFileType_throwsBusinessException() {
        MockMultipartFile pptxFile = new MockMultipartFile(
                "file",
                "季度汇报.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                new byte[2048]
        );

        assertThatThrownBy(() -> fileService.upload(pptxFile, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    @DisplayName("上传超过 50MB 的文件应抛出 BusinessException")
    void upload_oversizedFile_throwsBusinessException() {
        // 构造 50MB + 1 字节的文件
        byte[] oversizedContent = new byte[(int) MAX_FILE_SIZE + 1];
        MockMultipartFile oversizedFile = new MockMultipartFile(
                "file",
                "超大文档.pdf",
                "application/pdf",
                oversizedContent
        );

        assertThatThrownBy(() -> fileService.upload(oversizedFile, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小超过限制");

        verifyNoInteractions(minioStorage);
    }

    @Test
    @DisplayName("上传恰好 50MB 的文件不应因大小抛出异常（边界值测试）")
    void upload_exactlyMaxSizeFile_doesNotThrowSizeException() throws Exception {
        byte[] maxContent = new byte[(int) MAX_FILE_SIZE];
        MockMultipartFile maxFile = new MockMultipartFile(
                "file",
                "刚好50MB.pdf",
                "application/pdf",
                maxContent
        );

        // 配置 MinIO mock 使其不抛异常
        when(minioStorage.upload(anyString(), any(), anyLong(), anyString()))
                .thenReturn("kb-files/刚好50MB.pdf");
        when(kbFileMapper.insert(any())).thenReturn(1);

        // 不抛大小超限异常（可能因其他原因失败，但不是大小问题）
        // 此处验证大小校验通过
        fileService.upload(maxFile, 1L);

        verify(minioStorage).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("上传空文件名应抛出 BusinessException")
    void upload_emptyFileName_throwsBusinessException() {
        MockMultipartFile emptyNameFile = new MockMultipartFile(
                "file",
                "",   // 空文件名
                "application/pdf",
                new byte[1024]
        );

        assertThatThrownBy(() -> fileService.upload(emptyNameFile, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名不能为空");
    }

    @Test
    @DisplayName("允许的文件类型（pdf, docx, xlsx, txt, csv）不应因类型校验抛出异常")
    void upload_allowedFileTypes_passSizeAndTypeValidation() throws Exception {
        String[] allowedTypes = {"report.pdf", "handbook.docx", "data.xlsx", "readme.txt", "export.csv"};

        for (String filename : allowedTypes) {
            MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain", new byte[1024]);
            when(minioStorage.upload(anyString(), any(), anyLong(), anyString()))
                    .thenReturn("kb-files/" + filename);
            when(kbFileMapper.insert(any())).thenReturn(1);

            // 不应因类型校验失败
            fileService.upload(file, 1L);
        }

        // 5 个文件都应调用 MinIO 上传
        verify(minioStorage, times(5)).upload(anyString(), any(), anyLong(), anyString());
    }

    // ========== getTaskStatus 测试 ==========

    @Test
    @DisplayName("查询已存在的 taskId 应返回正确的 TaskStatusVO")
    void getTaskStatus_existingTask_returnsVO() {
        // Arrange
        KbTask task = new KbTask();
        task.setId(1001L);
        task.setStatus("RUNNING");
        task.setProgress(65);
        task.setErrorMsg(null);

        when(kbTaskMapper.selectById(1001L)).thenReturn(task);

        // Act
        TaskStatusVO vo = fileService.getTaskStatus(1001L);

        // Assert
        assertThat(vo).isNotNull();
        assertThat(vo.getId()).isEqualTo(1001L);
        assertThat(vo.getStatus()).isEqualTo("RUNNING");
        assertThat(vo.getProgress()).isEqualTo(65);
        assertThat(vo.getErrorMsg()).isNull();
    }

    @Test
    @DisplayName("查询已完成的 task 应正确映射 SUCCESS 状态和 100% 进度")
    void getTaskStatus_completedTask_returnsSuccessVO() {
        KbTask task = new KbTask();
        task.setId(1002L);
        task.setStatus("SUCCESS");
        task.setProgress(100);
        task.setErrorMsg(null);

        when(kbTaskMapper.selectById(1002L)).thenReturn(task);

        TaskStatusVO vo = fileService.getTaskStatus(1002L);

        assertThat(vo.getStatus()).isEqualTo("SUCCESS");
        assertThat(vo.getProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("查询失败的 task 应正确映射 FAILED 状态和错误信息")
    void getTaskStatus_failedTask_returnsFailedVO() {
        KbTask task = new KbTask();
        task.setId(1003L);
        task.setStatus("FAILED");
        task.setProgress(30);
        task.setErrorMsg("LLM 服务调用超时，请稍后重试");

        when(kbTaskMapper.selectById(1003L)).thenReturn(task);

        TaskStatusVO vo = fileService.getTaskStatus(1003L);

        assertThat(vo.getStatus()).isEqualTo("FAILED");
        assertThat(vo.getErrorMsg()).isEqualTo("LLM 服务调用超时，请稍后重试");
    }

    @Test
    @DisplayName("查询不存在的 taskId 应抛出 BusinessException（40004）")
    void getTaskStatus_nonExistingTask_throwsBusinessException() {
        when(kbTaskMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> fileService.getTaskStatus(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在")
                .extracting("code")
                .isEqualTo(40004);
    }
}
