package com.sld.faq.module.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sld.faq.common.BusinessException;
import com.sld.faq.common.PageResult;
import com.sld.faq.infrastructure.storage.MinioStorage;
import com.sld.faq.module.file.entity.KbFile;
import com.sld.faq.module.file.entity.KbTask;
import com.sld.faq.module.file.mapper.KbFileMapper;
import com.sld.faq.module.file.mapper.KbTaskMapper;
import com.sld.faq.module.file.vo.FileVO;
import com.sld.faq.module.file.vo.TaskStatusVO;
import com.sld.faq.module.generate.FaqGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.apache.tika.Tika;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件管理 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "txt", "csv");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            // Tika returns this generic type for OOXML when exact subtype can't be determined
            "application/x-tika-ooxml",
            "text/plain",
            "text/csv",
            "application/csv"
    );
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KbFileMapper kbFileMapper;
    private final KbTaskMapper kbTaskMapper;
    private final MinioStorage minioStorage;
    private final FaqGenerationService faqGenerationService;

    /**
     * 上传文件：校验类型 → 存 MinIO → 保存 kb_file，返回 FileVO
     */
    @Transactional
    public FileVO upload(MultipartFile file, Long submitterId) {
        // 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小超过限制（最大 50MB）");
        }

        // 校验文件类型
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }
        String ext = extractExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的文件类型，允许：pdf, docx, xlsx, txt, csv");
        }
        // 校验文件真实类型（Magic Number），防止后缀伪造
        validateMimeType(file);

        // 上传到 MinIO
        String minioPath;
        try {
            minioPath = minioStorage.upload(
                    originalName,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            );
        } catch (Exception e) {
            log.error("文件上传 MinIO 失败: name={}", originalName, e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }

        // 保存 kb_file 记录
        KbFile kbFile = new KbFile();
        kbFile.setOriginalName(originalName);
        kbFile.setMinioPath(minioPath);
        kbFile.setFileSize(file.getSize());
        kbFile.setFileType(ext);
        kbFile.setParseStatus("PENDING");
        kbFile.setChunkCount(0);
        kbFile.setSubmitterId(submitterId);
        kbFileMapper.insert(kbFile);

        log.info("文件上传成功: id={}, name={}, submitterId={}", kbFile.getId(), originalName, submitterId);
        return toFileVO(kbFile, null);
    }

    /**
     * 分页查询文件列表（按 submitterId 过滤，按 createdAt 降序）
     */
    public PageResult<FileVO> list(Long submitterId, int page, int size) {
        IPage<KbFile> pageResult = kbFileMapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<KbFile>()
                        .eq(KbFile::getSubmitterId, submitterId)
                        .orderByDesc(KbFile::getCreatedAt)
        );

        List<FileVO> items = pageResult.getRecords().stream()
                .map(f -> {
                    KbTask latestTask = kbTaskMapper.selectLatestByFileId(f.getId());
                    return toFileVO(f, latestTask);
                })
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), items);
    }

    /**
     * 根据 id 查询文件详情（含最新 task 状态）
     */
    public FileVO getById(Long id) {
        KbFile kbFile = kbFileMapper.selectById(id);
        if (kbFile == null) {
            throw new BusinessException(40004, "文件不存在");
        }
        KbTask latestTask = kbTaskMapper.selectLatestByFileId(id);
        return toFileVO(kbFile, latestTask);
    }

    /**
     * 触发 FAQ 生成：创建 kb_task，提交异步任务，返回 taskId
     */
    @Transactional
    public Long triggerGenerateFaq(Long fileId) {
        KbFile kbFile = kbFileMapper.selectById(fileId);
        if (kbFile == null) {
            throw new BusinessException(40004, "文件不存在");
        }

        // 创建任务记录
        KbTask task = new KbTask();
        task.setFileId(fileId);
        task.setTaskType("GENERATE");
        task.setStatus("PENDING");
        task.setProgress(0);
        kbTaskMapper.insert(task);

        // 提交异步任务
        faqGenerationService.generateAsync(fileId, task.getId());

        log.info("FAQ 生成任务已提交: fileId={}, taskId={}", fileId, task.getId());
        return task.getId();
    }

    /**
     * 查询 task 状态
     */
    public TaskStatusVO getTaskStatus(Long taskId) {
        KbTask task = kbTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(40004, "任务不存在");
        }
        return toTaskStatusVO(task);
    }

    // ========== 私有辅助方法 ==========

    private FileVO toFileVO(KbFile kbFile, KbTask latestTask) {
        FileVO vo = new FileVO();
        vo.setId(kbFile.getId());
        vo.setOriginalName(kbFile.getOriginalName());
        vo.setFileType(kbFile.getFileType());
        vo.setFileSize(kbFile.getFileSize());
        vo.setParseStatus(kbFile.getParseStatus());
        vo.setChunkCount(kbFile.getChunkCount());
        if (kbFile.getCreatedAt() != null) {
            vo.setCreatedAt(kbFile.getCreatedAt().format(DT_FORMATTER));
        }
        if (latestTask != null) {
            vo.setLatestTask(toTaskStatusVO(latestTask));
        }
        return vo;
    }

    private TaskStatusVO toTaskStatusVO(KbTask task) {
        TaskStatusVO vo = new TaskStatusVO();
        vo.setId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setProgress(task.getProgress());
        vo.setErrorMsg(task.getErrorMsg());
        return vo;
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1);
    }

    /**
     * 使用 Apache Tika 检测文件真实 MIME 类型（Magic Number 校验），
     * 防止攻击者将非法文件伪装成合法后缀进行上传。
     */
    private void validateMimeType(MultipartFile file) {
        try {
            Tika tika = new Tika();
            String detectedType = tika.detect(file.getBytes());
            if (!ALLOWED_MIME_TYPES.contains(detectedType)) {
                throw new BusinessException("文件内容与扩展名不匹配，请上传真实的文档文件（检测到: " + detectedType + "）");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("读取文件内容失败（MIME 校验）: name={}", file.getOriginalFilename(), e);
            throw new BusinessException("文件读取失败，请重新上传");
        }
    }
}
