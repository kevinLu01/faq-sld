package com.sld.faq.module.product;

import cn.dev33.satoken.stp.StpUtil;
import com.sld.faq.common.ApiResponse;
import com.sld.faq.common.PageResult;
import com.sld.faq.module.product.vo.ProductCandidateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/product-candidates")
@RequiredArgsConstructor
public class ProductCandidateController {

    private final ProductCandidateService productCandidateService;
    private final ProductReviewService productReviewService;

    @GetMapping
    public ApiResponse<PageResult<ProductCandidateVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long fileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(productCandidateService.list(status, fileId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductCandidateVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(productCandidateService.getById(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Long>> approve(@PathVariable Long id) {
        Long reviewerId = StpUtil.getLoginIdAsLong();
        Long productId = productReviewService.approve(id, reviewerId);
        return ApiResponse.ok(Map.of("productId", productId));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, String> body) {
        Long reviewerId = StpUtil.getLoginIdAsLong();
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        productReviewService.reject(id, reviewerId, reason);
        return ApiResponse.ok(null);
    }
}
