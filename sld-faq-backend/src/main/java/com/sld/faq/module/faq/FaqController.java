package com.sld.faq.module.faq;

import com.sld.faq.common.ApiResponse;
import com.sld.faq.common.PageResult;
import com.sld.faq.module.faq.vo.FaqVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 正式 FAQ Controller
 */
@RestController
@RequestMapping("/api/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    /**
     * GET /api/faqs?keyword=&categoryId=&page=0&size=20
     */
    @GetMapping
    public ApiResponse<PageResult<FaqVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(faqService.list(keyword, categoryId, page, size));
    }

    /**
     * GET /api/faqs/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<FaqVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(faqService.getById(id));
    }
}
