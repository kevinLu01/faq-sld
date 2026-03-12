package com.sld.faq.module.candidate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sld.faq.common.BusinessException;
import com.sld.faq.module.candidate.entity.FaqCandidate;
import com.sld.faq.module.candidate.mapper.FaqCandidateMapper;
import com.sld.faq.module.faq.FaqService;
import com.sld.faq.module.faq.entity.FaqCategory;
import com.sld.faq.module.faq.entity.FaqItem;
import com.sld.faq.module.faq.entity.FaqSourceRef;
import com.sld.faq.module.faq.mapper.FaqCategoryMapper;
import com.sld.faq.module.faq.mapper.FaqItemMapper;
import com.sld.faq.module.faq.mapper.FaqSourceRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FAQ 候选审核 Service
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FaqReviewService {

    private final FaqCandidateMapper candidateMapper;
    private final FaqItemMapper faqItemMapper;
    private final FaqSourceRefMapper faqSourceRefMapper;
    private final FaqCategoryMapper faqCategoryMapper;
    private final FaqService faqService;

    /**
     * 通过审核：
     * 1. 检查 candidate.status == PENDING
     * 2. 创建 faq_item
     * 3. 创建 faq_source_ref
     * 4. 更新 candidate.status = APPROVED
     *
     * @return 新创建的 faqId
     */
    public Long approve(Long candidateId, Long reviewerId) {
        FaqCandidate candidate = getAndCheckPending(candidateId);
        Long faqId = createFaqItemFromCandidate(candidate, reviewerId);
        createSourceRef(faqId, candidate);
        markApproved(candidate, reviewerId);
        return faqId;
    }

    /**
     * 驳回审核：
     * 1. 检查 candidate.status == PENDING
     * 2. 更新 candidate.status = REJECTED，记录驳回原因
     */
    public void reject(Long candidateId, Long reviewerId, String reason) {
        FaqCandidate candidate = getAndCheckPending(candidateId);
        candidate.setStatus("REJECTED");
        candidate.setRejectReason(reason);
        candidate.setReviewerId(reviewerId);
        candidate.setReviewedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
    }

    /**
     * 编辑后通过：
     * 1. 检查 candidate.status == PENDING
     * 2. 用传入的 question/answer 覆盖候选内容
     * 3. 逻辑同 approve
     *
     * @return 新创建的 faqId
     */
    public Long editApprove(Long candidateId, Long reviewerId, String question, String answer) {
        FaqCandidate candidate = getAndCheckPending(candidateId);
        if (question != null && !question.isBlank()) {
            candidate.setQuestion(question);
        }
        if (answer != null && !answer.isBlank()) {
            candidate.setAnswer(answer);
        }
        Long faqId = createFaqItemFromCandidate(candidate, reviewerId);
        createSourceRef(faqId, candidate);
        markApproved(candidate, reviewerId);
        return faqId;
    }

    /**
     * 合并到已有 FAQ：
     * 1. 检查 candidate.status == PENDING
     * 2. 检查 targetFaqId 对应的 faq_item 存在且已发布
     * 3. 创建 faq_source_ref（关联到 targetFaqId）
     * 4. 更新 candidate.status = MERGED
     */
    public void merge(Long candidateId, Long reviewerId, Long targetFaqId) {
        FaqCandidate candidate = getAndCheckPending(candidateId);

        FaqItem targetFaq = faqItemMapper.selectById(targetFaqId);
        if (targetFaq == null) {
            throw new BusinessException("目标 FAQ 不存在，id=" + targetFaqId);
        }
        if (targetFaq.getStatus() == null || targetFaq.getStatus() != 1) {
            throw new BusinessException("目标 FAQ 未发布，id=" + targetFaqId);
        }

        createSourceRef(targetFaqId, candidate);

        candidate.setStatus("MERGED");
        candidate.setMergedFaqId(targetFaqId);
        candidate.setReviewerId(reviewerId);
        candidate.setReviewedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
    }

    // -------------------------------------------------------------------------
    // 私有方法
    // -------------------------------------------------------------------------

    /**
     * 获取候选 FAQ 并校验状态为 PENDING，否则抛 BusinessException
     */
    private FaqCandidate getAndCheckPending(Long candidateId) {
        FaqCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException("候选 FAQ 不存在，id=" + candidateId);
        }
        if (!"PENDING".equals(candidate.getStatus())) {
            throw new BusinessException("候选 FAQ 状态非 PENDING，当前状态=" + candidate.getStatus());
        }
        return candidate;
    }

    /**
     * 根据候选创建 faq_item，返回新 faqId
     */
    private Long createFaqItemFromCandidate(FaqCandidate candidate, Long reviewerId) {
        FaqItem item = new FaqItem();
        item.setQuestion(candidate.getQuestion());
        item.setAnswer(candidate.getAnswer());
        item.setKeywords(candidate.getKeywords());
        item.setCategoryId(resolveCategoryId(candidate.getCategory()));
        item.setStatus(1);
        item.setViewCount(0);
        item.setPublisherId(reviewerId);
        item.setPublishedAt(LocalDateTime.now());
        return faqService.createFaqItem(item);
    }

    /**
     * 创建 faq_source_ref 记录
     */
    private void createSourceRef(Long faqId, FaqCandidate candidate) {
        FaqSourceRef ref = new FaqSourceRef();
        ref.setFaqId(faqId);
        ref.setCandidateId(candidate.getId());
        ref.setChunkId(candidate.getChunkId());
        ref.setFileId(candidate.getFileId());
        ref.setCreatedAt(LocalDateTime.now());
        faqSourceRefMapper.insert(ref);
    }

    /**
     * 将候选状态更新为 APPROVED
     */
    private void markApproved(FaqCandidate candidate, Long reviewerId) {
        candidate.setStatus("APPROVED");
        candidate.setReviewerId(reviewerId);
        candidate.setReviewedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
    }

    /**
     * category 名称 → category_id
     * 查 faq_category WHERE name = ?，查不到返回 null
     */
    private Long resolveCategoryId(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        List<FaqCategory> categories = faqCategoryMapper.selectList(
                new LambdaQueryWrapper<FaqCategory>()
                        .eq(FaqCategory::getName, categoryName)
                        .last("LIMIT 1")
        );
        if (categories.isEmpty()) {
            return null;
        }
        return categories.get(0).getId();
    }
}
