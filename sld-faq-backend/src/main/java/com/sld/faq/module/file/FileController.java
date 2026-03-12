package com.sld.faq.module.file;

import cn.dev33.satoken.stp.StpUtil;
import com.sld.faq.common.ApiResponse;
import com.sld.faq.common.PageResult;
import com.sld.faq.module.file.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件管理 Controller
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * POST /api/files/upload
     * 上传文件，当前用户作为提交人
     */
    @PostMapping("/upload")
    public ApiResponse<FileVO> upload(@RequestParam("file") MultipartFile file) {
        Long submitterId = StpUtil.getLoginIdAsLong();
        FileVO vo = fileService.upload(file, submitterId);
        return ApiResponse.ok(vo);
    }

    /**
     * GET /api/files?page=0&size=20
     * 查询当前用户的文件列表
     */
    @GetMapping
    public ApiResponse<PageResult<FileVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long submitterId = StpUtil.getLoginIdAsLong();
        PageResult<FileVO> result = fileService.list(submitterId, page, size);
        return ApiResponse.ok(result);
    }

    /**
     * GET /api/files/{id}
     * 查询文件详情（含最新 task 状态）
     */
    @GetMapping("/{id}")
    public ApiResponse<FileVO> getById(@PathVariable Long id) {
        FileVO vo = fileService.getById(id);
        return ApiResponse.ok(vo);
    }

    /**
     * POST /api/files/{id}/generate-faq
     * 触发 FAQ 生成，返回 taskId
     */
    @PostMapping("/{id}/generate-faq")
    public ApiResponse<Map<String, Long>> generateFaq(@PathVariable Long id) {
        Long taskId = fileService.triggerGenerateFaq(id);
        return ApiResponse.ok(Map.of("taskId", taskId));
    }
}
