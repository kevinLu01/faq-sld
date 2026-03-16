package com.sld.faq.module.product;

import com.sld.faq.infrastructure.llm.LlmClient;
import com.sld.faq.module.file.entity.KbChunk;
import com.sld.faq.module.product.dto.ProductCandidateDto;
import com.sld.faq.module.product.entity.ProductCandidate;
import com.sld.faq.module.product.mapper.ProductCandidateMapper;
import com.sld.faq.module.generate.PromptBuilder;
import com.sld.faq.module.generate.PromptMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 产品候选提取服务
 * <p>
 * 被 FaqGenerationService 调用，对同一批 chunks 发起 PRODUCT 模式的 LLM 调用，
 * 提取结构化产品信息并保存为 product_candidate 记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductGenerationService {

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ProductJsonParser productJsonParser;
    private final ProductCandidateMapper productCandidateMapper;

    /**
     * 对已切好的 chunks 提取产品候选
     *
     * @param fileId 文件 ID
     * @param chunks 已保存的 chunk 列表
     */
    public void extractProducts(Long fileId, List<KbChunk> chunks) {
        int extracted = 0;
        for (KbChunk chunk : chunks) {
            try {
                String prompt = promptBuilder.build(chunk.getCleanContent(), PromptMode.PRODUCT);
                String llmOutput = llmClient.chat(prompt);
                List<ProductCandidateDto> dtos = productJsonParser.parse(llmOutput);
                saveProductCandidates(fileId, chunk.getId(), dtos);
                extracted += dtos.size();
            } catch (Exception e) {
                log.warn("产品提取跳过 chunk: fileId={}, chunkId={}, error={}",
                        fileId, chunk.getId(), e.getMessage());
            }
        }
        log.info("产品提取完成: fileId={}, 提取候选数={}", fileId, extracted);
    }

    /**
     * 从单个图片文本中提取产品候选（图片文件专用路径）
     *
     * @param fileId  文件 ID
     * @param ocrText OCR 提取的文本内容
     */
    public void extractProductsFromOcrText(Long fileId, String ocrText) {
        try {
            String prompt = promptBuilder.build(ocrText, PromptMode.PRODUCT);
            String llmOutput = llmClient.chat(prompt);
            List<ProductCandidateDto> dtos = productJsonParser.parse(llmOutput);
            saveProductCandidates(fileId, null, dtos);
            log.info("图片产品提取完成: fileId={}, 提取候选数={}", fileId, dtos.size());
        } catch (Exception e) {
            log.warn("图片产品提取失败: fileId={}, error={}", fileId, e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveProductCandidates(Long fileId, Long chunkId, List<ProductCandidateDto> dtos) {
        for (ProductCandidateDto dto : dtos) {
            ProductCandidate candidate = new ProductCandidate();
            candidate.setFileId(fileId);
            candidate.setChunkId(chunkId);
            candidate.setName(dto.getName());
            candidate.setModel(dto.getModel());
            candidate.setBrand(dto.getBrand());
            candidate.setSpecs(dto.getSpecs());
            candidate.setCompatModels(dto.getCompatModels());
            candidate.setCategory(dto.getCategory());
            candidate.setSourceSummary(dto.getSourceSummary());
            candidate.setConfidence(dto.getConfidence());
            candidate.setStatus("PENDING");
            productCandidateMapper.insert(candidate);
        }
    }
}
