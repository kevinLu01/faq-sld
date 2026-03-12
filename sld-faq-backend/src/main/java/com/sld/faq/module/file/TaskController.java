package com.sld.faq.module.file;

import com.sld.faq.common.ApiResponse;
import com.sld.faq.module.file.vo.TaskStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务状态查询 Controller
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final FileService fileService;

    /**
     * GET /api/tasks/{id}/status
     * 查询任务状态（前端轮询使用）
     */
    @GetMapping("/{id}/status")
    public ApiResponse<TaskStatusVO> getStatus(@PathVariable Long id) {
        TaskStatusVO vo = fileService.getTaskStatus(id);
        return ApiResponse.ok(vo);
    }
}
