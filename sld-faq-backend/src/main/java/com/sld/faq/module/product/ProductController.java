package com.sld.faq.module.product;

import com.sld.faq.common.ApiResponse;
import com.sld.faq.common.PageResult;
import com.sld.faq.module.product.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<PageResult<ProductVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(productService.list(keyword, categoryId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }
}
