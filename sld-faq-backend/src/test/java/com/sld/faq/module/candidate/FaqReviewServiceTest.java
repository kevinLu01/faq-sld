package com.sld.faq.module.candidate;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FaqReviewService 单元测试
 * <p>
 * 覆盖候选 FAQ 的审核业务逻辑：
 * 1. 通过审核 → 创建 faq_item + source_ref，candidate 状态变 APPROVED
 * 2. 非 PENDING 状态时通过审核抛 BusinessException
 * 3. 驳回审核 → 更新状态为 REJECTED 并记录原因
 * 4. 编辑后通过 → faq_item 使用新的 question/answer
 * 5. 合并到已有 FAQ → candidate 状态变 MERGED，创建 source_ref
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FaqReviewService 候选 FAQ 审核测试")
class FaqReviewServiceTest {

    @Mock
    private FaqCandidateMapper candidateMapper;

    @Mock
    private FaqItemMapper faqItemMapper;

    @Mock
    private FaqSourceRefMapper faqSourceRefMapper;

    @Mock
    private FaqCategoryMapper faqCategoryMapper;

    @Mock
    private FaqService faqService;

    @InjectMocks
    private FaqReviewService faqReviewService;

    // ========== approve 测试 ==========

    @Test
    @DisplayName("通过审核应创建 faq_item 和 source_ref，并将 candidate 状态更新为 APPROVED")
    void approve_pendingCandidate_createsFaqItemAndSourceRef() {
        // Arrange
        FaqCandidate candidate = buildPendingCandidate(1L);
        candidate.setQuestion("员工报销出差费用的流程是什么？");
        candidate.setAnswer("填写报销单 → 部门主管审批 → 财务审核 → 到账，全程在 OA 系统中完成。");
        candidate.setCategory("差旅报销");
        candidate.setFileId(10L);
        candidate.setChunkId(100L);

        when(candidateMapper.selectForUpdate(1L)).thenReturn(candidate);
        when(faqCategoryMapper.selectList(any())).thenReturn(
                List.of(buildCategory(5L, "差旅报销"))
        );
        when(faqService.createFaqItem(any(FaqItem.class))).thenReturn(888L);
        when(faqSourceRefMapper.insert(any(FaqSourceRef.class))).thenReturn(1);
        when(candidateMapper.updateById(any(FaqCandidate.class))).thenReturn(1);

        // Act
        Long faqId = faqReviewService.approve(1L, 50L);

        // Assert
        assertThat(faqId).isEqualTo(888L);

        // 验证 faq_item 被创建
        ArgumentCaptor<FaqItem> faqItemCaptor = ArgumentCaptor.forClass(FaqItem.class);
        verify(faqService).createFaqItem(faqItemCaptor.capture());
        FaqItem createdItem = faqItemCaptor.getValue();
        assertThat(createdItem.getQuestion()).isEqualTo("员工报销出差费用的流程是什么？");
        assertThat(createdItem.getAnswer()).contains("OA 系统");
        assertThat(createdItem.getStatus()).isEqualTo(1);
        assertThat(createdItem.getPublisherId()).isEqualTo(50L);
        assertThat(createdItem.getCategoryId()).isEqualTo(5L);

        // 验证 source_ref 被创建
        ArgumentCaptor<FaqSourceRef> refCaptor = ArgumentCaptor.forClass(FaqSourceRef.class);
        verify(faqSourceRefMapper).insert(refCaptor.capture());
        FaqSourceRef createdRef = refCaptor.getValue();
        assertThat(createdRef.getFaqId()).isEqualTo(888L);
        assertThat(createdRef.getCandidateId()).isEqualTo(1L);
        assertThat(createdRef.getFileId()).isEqualTo(10L);

        // 验证 candidate 状态被更新为 APPROVED
        ArgumentCaptor<FaqCandidate> candidateCaptor = ArgumentCaptor.forClass(FaqCandidate.class);
        verify(candidateMapper).updateById(candidateCaptor.capture());
        assertThat(candidateCaptor.getValue().getStatus()).isEqualTo("APPROVED");
        assertThat(candidateCaptor.getValue().getReviewerId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("candidate 状态非 PENDING 时通过审核应抛出 BusinessException")
    void approve_nonPendingCandidate_throwsBusinessException() {
        // Arrange - 已审核通过的候选
        FaqCandidate candidate = buildPendingCandidate(2L);
        candidate.setStatus("APPROVED");

        when(candidateMapper.selectForUpdate(2L)).thenReturn(candidate);

        // Act & Assert
        assertThatThrownBy(() -> faqReviewService.approve(2L, 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非 PENDING");

        verify(faqService, never()).createFaqItem(any());
        verify(faqSourceRefMapper, never()).insert(any(FaqSourceRef.class));
    }

    @Test
    @DisplayName("candidate 不存在时应抛出 BusinessException")
    void approve_notExistingCandidate_throwsBusinessException() {
        // selectForUpdate returns null by default (mock), no stubbing needed
        assertThatThrownBy(() -> faqReviewService.approve(999L, 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("候选 FAQ 不存在");
    }

    // ========== reject 测试 ==========

    @Test
    @DisplayName("驳回审核应记录原因并将 candidate 状态更新为 REJECTED")
    void reject_pendingCandidate_updatesStatusAndReason() {
        // Arrange
        FaqCandidate candidate = buildPendingCandidate(3L);
        candidate.setQuestion("这是一个描述不准确的问题？");
        candidate.setAnswer("这是一个不完整的回答。");

        when(candidateMapper.selectForUpdate(3L)).thenReturn(candidate);
        when(candidateMapper.updateById(any(FaqCandidate.class))).thenReturn(1);

        String rejectReason = "问题描述不够准确，与原文内容出入较大，建议重新生成";

        // Act
        faqReviewService.reject(3L, 50L, rejectReason);

        // Assert
        ArgumentCaptor<FaqCandidate> captor = ArgumentCaptor.forClass(FaqCandidate.class);
        verify(candidateMapper).updateById(captor.capture());
        FaqCandidate updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("REJECTED");
        assertThat(updated.getRejectReason()).isEqualTo(rejectReason);
        assertThat(updated.getReviewerId()).isEqualTo(50L);
        assertThat(updated.getReviewedAt()).isNotNull();

        // 驳回不应创建 faq_item
        verify(faqService, never()).createFaqItem(any());
    }

    @Test
    @DisplayName("驳回非 PENDING 状态的候选应抛出 BusinessException")
    void reject_nonPendingCandidate_throwsBusinessException() {
        FaqCandidate candidate = buildPendingCandidate(4L);
        candidate.setStatus("REJECTED");

        when(candidateMapper.selectForUpdate(4L)).thenReturn(candidate);

        assertThatThrownBy(() -> faqReviewService.reject(4L, 50L, "重复驳回"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非 PENDING");
    }

    // ========== editApprove 测试 ==========

    @Test
    @DisplayName("编辑后通过审核应使用新的 question/answer 创建 faq_item")
    void editApprove_overridesQuestionAndAnswer() {
        // Arrange
        FaqCandidate candidate = buildPendingCandidate(5L);
        candidate.setQuestion("原始问题（LLM 提取有误）");
        candidate.setAnswer("原始答案（LLM 提取有误）");
        candidate.setCategory("政策法规");
        candidate.setFileId(20L);
        candidate.setChunkId(200L);

        when(candidateMapper.selectForUpdate(5L)).thenReturn(candidate);
        when(faqCategoryMapper.selectList(any())).thenReturn(
                List.of(buildCategory(6L, "政策法规"))
        );
        when(faqService.createFaqItem(any(FaqItem.class))).thenReturn(999L);
        when(faqSourceRefMapper.insert(any(FaqSourceRef.class))).thenReturn(1);
        when(candidateMapper.updateById(any(FaqCandidate.class))).thenReturn(1);

        String correctedQuestion = "员工申请年假需要提前几个工作日？";
        String correctedAnswer = "根据公司政策，员工申请年假需提前三个工作日在 HR 系统中提交，审批后方可休假。";

        // Act
        Long faqId = faqReviewService.editApprove(5L, 50L, correctedQuestion, correctedAnswer);

        // Assert
        assertThat(faqId).isEqualTo(999L);

        ArgumentCaptor<FaqItem> captor = ArgumentCaptor.forClass(FaqItem.class);
        verify(faqService).createFaqItem(captor.capture());
        FaqItem createdItem = captor.getValue();
        // 确认使用了编辑后的 question/answer，而非原始值
        assertThat(createdItem.getQuestion()).isEqualTo(correctedQuestion);
        assertThat(createdItem.getAnswer()).isEqualTo(correctedAnswer);
        assertThat(createdItem.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("editApprove 传入空 question/answer 时应保留原始值")
    void editApprove_nullOrBlankOverride_keepsOriginalValues() {
        // Arrange
        FaqCandidate candidate = buildPendingCandidate(6L);
        candidate.setQuestion("年终奖的发放标准是什么？");
        candidate.setAnswer("年终奖根据绩效考核结果和公司整体业绩综合确定，一般在春节前发放。");
        candidate.setCategory("薪酬福利");
        candidate.setFileId(30L);
        candidate.setChunkId(300L);

        when(candidateMapper.selectForUpdate(6L)).thenReturn(candidate);
        when(faqCategoryMapper.selectList(any())).thenReturn(List.of());
        when(faqService.createFaqItem(any(FaqItem.class))).thenReturn(1001L);
        when(faqSourceRefMapper.insert(any(FaqSourceRef.class))).thenReturn(1);
        when(candidateMapper.updateById(any(FaqCandidate.class))).thenReturn(1);

        // Act: 传入 null question 和空 answer
        faqReviewService.editApprove(6L, 50L, null, "");

        // Assert: 保留原始值
        ArgumentCaptor<FaqItem> captor = ArgumentCaptor.forClass(FaqItem.class);
        verify(faqService).createFaqItem(captor.capture());
        assertThat(captor.getValue().getQuestion()).isEqualTo("年终奖的发放标准是什么？");
        assertThat(captor.getValue().getAnswer()).contains("春节前发放");
    }

    // ========== merge 测试 ==========

    @Test
    @DisplayName("合并到已有 FAQ 后应创建 source_ref，candidate 状态变 MERGED")
    void merge_validTarget_linksToExistingFaq() {
        // Arrange
        FaqCandidate candidate = buildPendingCandidate(7L);
        candidate.setQuestion("请假流程相关问题");
        candidate.setAnswer("请假流程相关内容（与已有 FAQ 重复）");
        candidate.setFileId(40L);
        candidate.setChunkId(400L);

        FaqItem targetFaq = new FaqItem();
        targetFaq.setId(555L);
        targetFaq.setQuestion("员工如何申请请假？");
        targetFaq.setAnswer("在 HR 系统中提交申请，经主管审批后生效。");
        targetFaq.setStatus(1); // 已发布

        when(candidateMapper.selectForUpdate(7L)).thenReturn(candidate);
        when(faqItemMapper.selectById(555L)).thenReturn(targetFaq);
        when(faqSourceRefMapper.insert(any(FaqSourceRef.class))).thenReturn(1);
        when(candidateMapper.updateById(any(FaqCandidate.class))).thenReturn(1);

        // Act
        faqReviewService.merge(7L, 50L, 555L);

        // Assert: source_ref 关联到 targetFaqId
        ArgumentCaptor<FaqSourceRef> refCaptor = ArgumentCaptor.forClass(FaqSourceRef.class);
        verify(faqSourceRefMapper).insert(refCaptor.capture());
        assertThat(refCaptor.getValue().getFaqId()).isEqualTo(555L);
        assertThat(refCaptor.getValue().getCandidateId()).isEqualTo(7L);

        // candidate 状态变 MERGED，mergedFaqId 指向目标
        ArgumentCaptor<FaqCandidate> candidateCaptor = ArgumentCaptor.forClass(FaqCandidate.class);
        verify(candidateMapper).updateById(candidateCaptor.capture());
        assertThat(candidateCaptor.getValue().getStatus()).isEqualTo("MERGED");
        assertThat(candidateCaptor.getValue().getMergedFaqId()).isEqualTo(555L);
        assertThat(candidateCaptor.getValue().getReviewerId()).isEqualTo(50L);

        // 合并不创建新 faq_item
        verify(faqService, never()).createFaqItem(any());
    }

    @Test
    @DisplayName("合并时目标 FAQ 不存在应抛出 BusinessException")
    void merge_targetFaqNotExist_throwsBusinessException() {
        FaqCandidate candidate = buildPendingCandidate(8L);
        when(candidateMapper.selectForUpdate(8L)).thenReturn(candidate);
        when(faqItemMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> faqReviewService.merge(8L, 50L, 9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标 FAQ 不存在");
    }

    @Test
    @DisplayName("合并时目标 FAQ 未发布应抛出 BusinessException")
    void merge_targetFaqNotPublished_throwsBusinessException() {
        FaqCandidate candidate = buildPendingCandidate(9L);
        FaqItem unpublishedFaq = new FaqItem();
        unpublishedFaq.setId(666L);
        unpublishedFaq.setStatus(0); // 已下线

        when(candidateMapper.selectForUpdate(9L)).thenReturn(candidate);
        when(faqItemMapper.selectById(666L)).thenReturn(unpublishedFaq);

        assertThatThrownBy(() -> faqReviewService.merge(9L, 50L, 666L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未发布");
    }

    // ========== 辅助方法 ==========

    private FaqCandidate buildPendingCandidate(Long id) {
        FaqCandidate candidate = new FaqCandidate();
        candidate.setId(id);
        candidate.setStatus("PENDING");
        candidate.setQuestion("默认问题");
        candidate.setAnswer("默认答案");
        candidate.setCategory("通用");
        candidate.setConfidence(0.85);
        return candidate;
    }

    private FaqCategory buildCategory(Long id, String name) {
        FaqCategory category = new FaqCategory();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
