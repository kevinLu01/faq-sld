package com.sld.faq.module.product;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sld.faq.common.BusinessException;
import com.sld.faq.common.PageResult;
import com.sld.faq.module.file.mapper.KbFileMapper;
import com.sld.faq.module.product.entity.ProductCandidate;
import com.sld.faq.module.product.mapper.ProductCandidateMapper;
import com.sld.faq.module.product.vo.ProductCandidateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductCandidateService {

    private final ProductCandidateMapper productCandidateMapper;
    private final KbFileMapper kbFileMapper;

    public PageResult<ProductCandidateVO> list(String status, Long fileId, int page, int size) {
        Page<ProductCandidate> pageResult = productCandidateMapper.selectPage(
                new Page<>(page + 1, size), status, fileId);

        List<ProductCandidateVO> items = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), items);
    }

    public ProductCandidateVO getById(Long id) {
        ProductCandidate candidate = productCandidateMapper.selectById(id);
        if (candidate == null) {
            throw new BusinessException(40004, "产品候选不存在");
        }
        return toVO(candidate);
    }

    private ProductCandidateVO toVO(ProductCandidate c) {
        ProductCandidateVO vo = new ProductCandidateVO();
        vo.setId(c.getId());
        vo.setFileId(c.getFileId());
        vo.setName(c.getName());
        vo.setModel(c.getModel());
        vo.setBrand(c.getBrand());
        vo.setSpecs(c.getSpecs());
        vo.setCompatModels(c.getCompatModels());
        vo.setCategory(c.getCategory());
        vo.setSourceSummary(c.getSourceSummary());
        vo.setConfidence(c.getConfidence());
        vo.setStatus(c.getStatus());
        vo.setRejectReason(c.getRejectReason());
        vo.setCreatedAt(c.getCreatedAt());

        // 补充文件名
        if (c.getFileId() != null) {
            var kbFile = kbFileMapper.selectById(c.getFileId());
            if (kbFile != null) vo.setFileName(kbFile.getOriginalName());
        }
        return vo;
    }
}
