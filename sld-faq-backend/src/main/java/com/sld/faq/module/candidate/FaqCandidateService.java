package com.sld.faq.module.candidate;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sld.faq.common.BusinessException;
import com.sld.faq.module.candidate.entity.FaqCandidate;
import com.sld.faq.module.candidate.mapper.FaqCandidateMapper;
import com.sld.faq.module.candidate.vo.CandidateListVO;
import com.sld.faq.module.candidate.vo.CandidateVO;
import com.sld.faq.module.file.entity.KbChunk;
import com.sld.faq.module.file.entity.KbFile;
import com.sld.faq.module.file.mapper.KbChunkMapper;
import com.sld.faq.module.file.mapper.KbFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FAQ 候选 Service
 */
@Service
@RequiredArgsConstructor
public class FaqCandidateService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FaqCandidateMapper candidateMapper;
    private final KbChunkMapper chunkMapper;
    private final KbFileMapper fileMapper;

    /**
     * 分页查询候选列表（含来源文件名、来源 chunk 原文）
     * pendingCount 始终查全局 PENDING 数量
     */
    public CandidateListVO list(String status, Long fileId, int page, int size) {
        // MyBatis Plus Page 从 1 开始，外部传入从 0 开始
        Page<FaqCandidate> pageParam = new Page<>(page + 1, size);
        Page<FaqCandidate> result = candidateMapper.selectPage(pageParam, status, fileId);

        long pendingCount = candidateMapper.countByStatus("PENDING");

        List<CandidateVO> items = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        CandidateListVO vo = new CandidateListVO();
        vo.setTotal(result.getTotal());
        vo.setPendingCount(pendingCount);
        vo.setItems(items);
        return vo;
    }

    /**
     * 根据 id 查询详情（含来源 chunk 原文 + 文件名）
     */
    public CandidateVO getById(Long id) {
        FaqCandidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BusinessException("候选 FAQ 不存在，id=" + id);
        }
        return toVO(candidate);
    }

    /**
     * 保存候选 FAQ 列表（由 FaqGenerationService 调用）
     */
    @Transactional
    public void saveCandidates(List<FaqCandidate> candidates) {
        for (FaqCandidate candidate : candidates) {
            if (candidate.getStatus() == null) {
                candidate.setStatus("PENDING");
            }
            candidateMapper.insert(candidate);
        }
    }

    /**
     * 将 FaqCandidate 转换为 CandidateVO，填充来源文件名与来源 chunk 原文
     */
    private CandidateVO toVO(FaqCandidate candidate) {
        CandidateVO vo = new CandidateVO();
        vo.setId(candidate.getId());
        vo.setQuestion(candidate.getQuestion());
        vo.setAnswer(candidate.getAnswer());
        vo.setCategory(candidate.getCategory());
        vo.setKeywords(candidate.getKeywords());
        vo.setSourceSummary(candidate.getSourceSummary());
        vo.setConfidence(candidate.getConfidence());
        vo.setStatus(candidate.getStatus());
        vo.setRejectReason(candidate.getRejectReason());
        vo.setFileId(candidate.getFileId());

        if (candidate.getCreatedAt() != null) {
            vo.setCreatedAt(candidate.getCreatedAt().format(FORMATTER));
        }

        // 填充来源 chunk 原文
        if (candidate.getChunkId() != null) {
            KbChunk chunk = chunkMapper.selectById(candidate.getChunkId());
            if (chunk != null) {
                vo.setSourceChunk(chunk.getCleanContent());
            }
        }

        // 填充来源文件名
        if (candidate.getFileId() != null) {
            KbFile file = fileMapper.selectById(candidate.getFileId());
            if (file != null) {
                vo.setFileName(file.getOriginalName());
            }
        }

        return vo;
    }
}
