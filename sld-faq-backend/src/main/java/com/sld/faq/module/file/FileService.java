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
import com.sld.faq.module.product.ProductGenerationService;
import com.sld.faq.infrastructure.ocr.OcrClient;
import com.sld.faq.infrastructure.ocr.OcrResult;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.apache.tika.Tika;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import org.springframework.scheduling.annotation.Async;
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
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "xlsx", "txt", "csv",
            "jpg", "jpeg", "png"
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            // Tika returns this generic type for OOXML when exact subtype can't be determined
            "application/x-tika-ooxml",
            "text/plain",
            "text/csv",
            "application/csv",
            "image/jpeg",
            "image/png"
    );
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Tika TIKA = new Tika();

    private final KbFileMapper kbFileMapper;
    private final KbTaskMapper kbTaskMapper;
    private final MinioStorage minioStorage;
    private final FaqGenerationService faqGenerationService;
    private final ProductGenerationService productGenerationService;
    private final OcrClient ocrClient;

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
        String originalName = sanitizeFilename(file.getOriginalFilename());
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }
        String ext = extractExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的文件类型，允许：pdf, docx, xlsx, txt, csv, jpg, jpeg, png");
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
        checkFileOwnership(kbFile);
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
        checkFileOwnership(kbFile);

        // 创建任务记录
        KbTask task = new KbTask();
        task.setFileId(fileId);
        task.setTaskType("GENERATE");
        task.setStatus("PENDING");
        task.setProgress(0);
        kbTaskMapper.insert(task);

        // 图片文件走产品提取专用路径（OCR → LLM → 产品候选）
        if (IMAGE_EXTENSIONS.contains(kbFile.getFileType().toLowerCase())) {
            triggerImageProductExtractAsync(kbFile, task.getId());
        } else {
            // 文档类文件走 FAQ 生成（内部会同时触发产品提取双轨）
            faqGenerationService.generateAsync(fileId, task.getId());
        }

        log.info("生成任务已提交: fileId={}, taskId={}, type={}", fileId, task.getId(), kbFile.getFileType());
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

    /**
     * 校验当前用户是否为文件的提交者或拥有 ADMIN/REVIEWER 角色，防止 IDOR
     */
    private void checkFileOwnership(KbFile kbFile) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        if (kbFile.getSubmitterId() != null && kbFile.getSubmitterId().equals(currentUserId)) {
            return;
        }
        if (StpUtil.hasRole("ADMIN") || StpUtil.hasRole("REVIEWER")) {
            return;
        }
        throw new BusinessException(403, "无权访问该文件");
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return null;
        // Take only the last path segment (strip directory traversal)
        name = name.replaceAll(".*[/\\\\]", "");
        // Remove null bytes and other control characters
        name = name.replaceAll("[\\x00]", "");
        // Remove ".." sequences
        name = name.replace("..", "");
        return name.isBlank() ? null : name;
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
    /**
     * 图片文件异步产品提取：OCR → LLM PRODUCT 模式 → 保存 product_candidate
     */
    @Async("faqTaskExecutor")
    public void triggerImageProductExtractAsync(KbFile kbFile, Long taskId) {
        log.info("图片产品提取任务开始: fileId={}, taskId={}", kbFile.getId(), taskId);
        try {
            updateTaskStatus(taskId, "RUNNING", 10, null);

            byte[] fileBytes;
            try (java.io.InputStream is = minioStorage.download(kbFile.getMinioPath())) {
                fileBytes = is.readAllBytes();
            } catch (Exception e) {
                updateTaskStatus(taskId, "FAILED", 0, "下载图片失败: " + e.getMessage());
                return;
            }

            OcrResult ocrResult;
            try {
                ocrResult = ocrClient.ocr(fileBytes, kbFile.getOriginalName());
            } catch (Exception e) {
                updateTaskStatus(taskId, "FAILED", 0, "OCR 识别失败: " + e.getMessage());
                return;
            }

            if (!ocrResult.isSuccess()) {
                log.warn("OCR 识别失败，任务终止: fileId={}, msg={}", kbFile.getId(), ocrResult.getText());
                updateTaskStatus(taskId, "FAILED", 0, "OCR 识别失败: " + ocrResult.getText());
                return;
            }
            // OCR 成功但无文字内容（如纯图片/空白页），视为正常完成，产品候选为 0
            if (ocrResult.getMarkdown().isBlank()) {
                log.info("OCR 结果为空（图片无文字内容）: fileId={}", kbFile.getId());
                KbFile fileUpdate = new KbFile();
                fileUpdate.setId(kbFile.getId());
                fileUpdate.setParseStatus("SUCCESS");
                kbFileMapper.updateById(fileUpdate);
                updateTaskStatus(taskId, "SUCCESS", 100, null);
                return;
            }
            String ocrText = ocrResult.getMarkdown();

            updateTaskStatus(taskId, "RUNNING", 60, null);
            productGenerationService.extractProductsFromOcrText(kbFile.getId(), ocrText);

            KbFile fileUpdate = new KbFile();
            fileUpdate.setId(kbFile.getId());
            fileUpdate.setParseStatus("SUCCESS");
            kbFileMapper.updateById(fileUpdate);

            updateTaskStatus(taskId, "SUCCESS", 100, null);
            log.info("图片产品提取完成: fileId={}, taskId={}", kbFile.getId(), taskId);
        } catch (Exception e) {
            log.error("图片产品提取任务异常: fileId={}, taskId={}", kbFile.getId(), taskId, e);
            updateTaskStatus(taskId, "FAILED", 0, e.getMessage());
        }
    }

    private void updateTaskStatus(Long taskId, String status, int progress, String errorMsg) {
        KbTask task = new KbTask();
        task.setId(taskId);
        task.setStatus(status);
        task.setProgress(progress);
        task.setErrorMsg(errorMsg);
        kbTaskMapper.updateById(task);
    }

    private void validateMimeType(MultipartFile file) {
        try {
            String detectedType = TIKA.detect(file.getInputStream(), file.getOriginalFilename());
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
