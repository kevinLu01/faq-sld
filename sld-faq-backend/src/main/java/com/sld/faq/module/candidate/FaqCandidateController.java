package com.sld.faq.module.candidate;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sld.faq.common.ApiResponse;
import com.sld.faq.module.candidate.dto.ReviewRequest;
import com.sld.faq.module.candidate.vo.CandidateListVO;
import com.sld.faq.module.candidate.vo.CandidateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * FAQ 候选审核 Controller
 */
@RestController
@RequestMapping("/api/faq-candidates")
@RequiredArgsConstructor
public class FaqCandidateController {

    private final FaqCandidateService candidateService;
    private final FaqReviewService reviewService;

    /**
     * GET /api/faq-candidates?status=PENDING&fileId=&page=0&size=20
     */
    @GetMapping
    public ApiResponse<CandidateListVO> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long fileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(candidateService.list(status, fileId, page, size));
    }

    /**
     * GET /api/faq-candidates/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<CandidateVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(candidateService.getById(id));
    }

    /**
     * POST /api/faq-candidates/{id}/approve
     */
    @PostMapping("/{id}/approve")
    @SaCheckRole(value = {"REVIEWER", "ADMIN"}, mode = SaMode.OR)
    public ApiResponse<Map<String, Long>> approve(@PathVariable Long id) {
        Long reviewerId = StpUtil.getLoginIdAsLong();
        Long faqId = reviewService.approve(id, reviewerId);
        return ApiResponse.ok(Map.of("faqId", faqId));
    }

    /**
     * POST /api/faq-candidates/{id}/reject
     */
    @PostMapping("/{id}/reject")
    @SaCheckRole(value = {"REVIEWER", "ADMIN"}, mode = SaMode.OR)
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestBody ReviewRequest req) {
        Long reviewerId = StpUtil.getLoginIdAsLong();
        reviewService.reject(id, reviewerId, req.getReason());
        return ApiResponse.ok();
    }

    /**
     * POST /api/faq-candidates/{id}/edit-approve
     */
    @PostMapping("/{id}/edit-approve")
    @SaCheckRole(value = {"REVIEWER", "ADMIN"}, mode = SaMode.OR)
    public ApiResponse<Map<String, Long>> editApprove(@PathVariable Long id, @RequestBody ReviewRequest req) {
        Long reviewerId = StpUtil.getLoginIdAsLong();
        Long faqId = reviewService.editApprove(id, reviewerId, req.getQuestion(), req.getAnswer());
        return ApiResponse.ok(Map.of("faqId", faqId));
    }

    /**
     * POST /api/faq-candidates/{id}/merge
     */
    @PostMapping("/{id}/merge")
    @SaCheckRole(value = {"REVIEWER", "ADMIN"}, mode = SaMode.OR)
    public ApiResponse<Void> merge(@PathVariable Long id, @RequestBody ReviewRequest req) {
        Long reviewerId = StpUtil.getLoginIdAsLong();
        reviewService.merge(id, reviewerId, req.getTargetFaqId());
        return ApiResponse.ok();
    }
}
