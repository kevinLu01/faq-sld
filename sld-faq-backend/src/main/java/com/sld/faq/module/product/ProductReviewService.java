package com.sld.faq.module.product;

import com.sld.faq.common.BusinessException;
import com.sld.faq.module.product.entity.ProductCandidate;
import com.sld.faq.module.product.entity.ProductItem;
import com.sld.faq.module.product.entity.ProductSourceRef;
import com.sld.faq.module.product.mapper.ProductCandidateMapper;
import com.sld.faq.module.product.mapper.ProductItemMapper;
import com.sld.faq.module.product.mapper.ProductSourceRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductCandidateMapper productCandidateMapper;
    private final ProductItemMapper productItemMapper;
    private final ProductSourceRefMapper productSourceRefMapper;

    /**
     * 审核通过：创建 product_item + product_source_ref
     */
    @Transactional(rollbackFor = Exception.class)
    public Long approve(Long candidateId, Long reviewerId) {
        ProductCandidate candidate = getAndCheckPending(candidateId);

        ProductItem item = new ProductItem();
        item.setName(candidate.getName());
        item.setModel(candidate.getModel());
        item.setBrand(candidate.getBrand());
        item.setSpecs(candidate.getSpecs());
        item.setCompatModels(candidate.getCompatModels());
        item.setDescription(candidate.getSourceSummary());
        item.setStatus(1);
        item.setPublisherId(reviewerId);
        item.setPublishedAt(LocalDateTime.now());
        productItemMapper.insert(item);

        ProductSourceRef ref = new ProductSourceRef();
        ref.setProductId(item.getId());
        ref.setCandidateId(candidateId);
        ref.setFileId(candidate.getFileId());
        productSourceRefMapper.insert(ref);

        candidate.setStatus("APPROVED");
        candidate.setReviewerId(reviewerId);
        candidate.setReviewedAt(LocalDateTime.now());
        productCandidateMapper.updateById(candidate);

        return item.getId();
    }

    /**
     * 审核拒绝
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long candidateId, Long reviewerId, String reason) {
        ProductCandidate candidate = getAndCheckPending(candidateId);
        candidate.setStatus("REJECTED");
        candidate.setRejectReason(reason);
        candidate.setReviewerId(reviewerId);
        candidate.setReviewedAt(LocalDateTime.now());
        productCandidateMapper.updateById(candidate);
    }

    private ProductCandidate getAndCheckPending(Long candidateId) {
        ProductCandidate candidate = productCandidateMapper.selectForUpdate(candidateId);
        if (candidate == null) {
            throw new BusinessException(40004, "产品候选不存在");
        }
        if (!"PENDING".equals(candidate.getStatus())) {
            throw new BusinessException(40009, "该候选已处理，请勿重复操作");
        }
        return candidate;
    }
}
