package com.sld.faq.module.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sld.faq.common.BusinessException;
import com.sld.faq.common.PageResult;
import com.sld.faq.module.product.entity.ProductItem;
import com.sld.faq.module.product.mapper.ProductItemMapper;
import com.sld.faq.module.product.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductItemMapper productItemMapper;

    public PageResult<ProductVO> list(String keyword, Long categoryId, int page, int size) {
        LambdaQueryWrapper<ProductItem> wrapper = new LambdaQueryWrapper<ProductItem>()
                .eq(ProductItem::getStatus, 1)
                .eq(categoryId != null, ProductItem::getCategoryId, categoryId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(ProductItem::getName, keyword)
                        .or().like(ProductItem::getModel, keyword)
                        .or().like(ProductItem::getBrand, keyword))
                .orderByDesc(ProductItem::getCreatedAt);

        Page<ProductItem> pageResult = productItemMapper.selectPage(new Page<>(page + 1, size), wrapper);

        List<ProductVO> items = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), items);
    }

    public ProductVO getById(Long id) {
        ProductItem item = productItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(40004, "产品不存在");
        }
        return toVO(item);
    }

    private ProductVO toVO(ProductItem item) {
        ProductVO vo = new ProductVO();
        vo.setId(item.getId());
        vo.setName(item.getName());
        vo.setModel(item.getModel());
        vo.setBrand(item.getBrand());
        vo.setCategoryId(item.getCategoryId());
        vo.setSpecs(item.getSpecs());
        vo.setCompatModels(item.getCompatModels());
        vo.setDescription(item.getDescription());
        vo.setStatus(item.getStatus());
        vo.setPublishedAt(item.getPublishedAt());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }
}
