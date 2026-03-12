package com.sld.faq.module.faq;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sld.faq.common.BusinessException;
import com.sld.faq.common.PageResult;
import com.sld.faq.module.faq.entity.FaqCategory;
import com.sld.faq.module.faq.entity.FaqItem;
import com.sld.faq.module.faq.entity.FaqSourceRef;
import com.sld.faq.module.faq.mapper.FaqCategoryMapper;
import com.sld.faq.module.faq.mapper.FaqItemMapper;
import com.sld.faq.module.faq.mapper.FaqSourceRefMapper;
import com.sld.faq.module.faq.vo.FaqVO;
import com.sld.faq.module.faq.vo.SourceRefVO;
import com.sld.faq.module.file.entity.KbChunk;
import com.sld.faq.module.file.entity.KbFile;
import com.sld.faq.module.file.mapper.KbChunkMapper;
import com.sld.faq.module.file.mapper.KbFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 正式 FAQ Service
 */
@Service
@RequiredArgsConstructor
public class FaqService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int CHUNK_CONTENT_MAX_LEN = 200;

    private final FaqItemMapper faqItemMapper;
    private final FaqCategoryMapper faqCategoryMapper;
    private final FaqSourceRefMapper faqSourceRefMapper;
    private final KbChunkMapper chunkMapper;
    private final KbFileMapper fileMapper;

    /**
     * 分页查询（支持 keyword、categoryId 过滤，status=1）
     */
    public PageResult<FaqVO> list(String keyword, Long categoryId, int page, int size) {
        // MyBatis Plus Page 从 1 开始，外部传入从 0 开始
        Page<FaqItem> pageParam = new Page<>(page + 1, size);
        Page<FaqItem> result = faqItemMapper.searchPage(pageParam, keyword, categoryId);

        List<FaqVO> items = result.getRecords().stream()
                .map(item -> toVO(item, false))
                .collect(Collectors.toList());

        return PageResult.of(result.getTotal(), items);
    }

    /**
     * 根据 id 查详情（含 sourceRefs，每条 chunkContent 截断到 200 字）
     * viewCount +1
     */
    public FaqVO getById(Long id) {
        FaqItem item = faqItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("FAQ 不存在，id=" + id);
        }

        // viewCount +1
        faqItemMapper.updateById(buildViewCountUpdate(id, item.getViewCount()));

        return toVO(item, true);
    }

    /**
     * 创建 faq_item，返回 id（供 FaqReviewService 调用）
     */
    @Transactional
    public Long createFaqItem(FaqItem item) {
        faqItemMapper.insert(item);
        return item.getId();
    }

    /**
     * 将 FaqItem 转为 FaqVO
     *
     * @param item         FAQ 条目
     * @param withSourceRefs 是否附带来源引用（详情接口为 true，列表接口为 false）
     */
    private FaqVO toVO(FaqItem item, boolean withSourceRefs) {
        FaqVO vo = new FaqVO();
        vo.setId(item.getId());
        vo.setQuestion(item.getQuestion());
        vo.setAnswer(item.getAnswer());
        vo.setKeywords(item.getKeywords());
        vo.setStatus(item.getStatus());
        vo.setViewCount(item.getViewCount());

        if (item.getPublishedAt() != null) {
            vo.setPublishedAt(item.getPublishedAt().format(FORMATTER));
        }

        // 查分类名称
        if (item.getCategoryId() != null) {
            FaqCategory category = faqCategoryMapper.selectById(item.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        if (withSourceRefs) {
            List<FaqSourceRef> refs = faqSourceRefMapper.selectByFaqId(item.getId());
            List<SourceRefVO> sourceRefVOs = new ArrayList<>();
            for (FaqSourceRef ref : refs) {
                SourceRefVO refVO = new SourceRefVO();

                // 来源文件名
                if (ref.getFileId() != null) {
                    KbFile file = fileMapper.selectById(ref.getFileId());
                    if (file != null) {
                        refVO.setFileName(file.getOriginalName());
                    }
                }

                // 来源 chunk 原文，截断到 200 字
                if (ref.getChunkId() != null) {
                    KbChunk chunk = chunkMapper.selectById(ref.getChunkId());
                    if (chunk != null && chunk.getCleanContent() != null) {
                        String content = chunk.getCleanContent();
                        if (content.length() > CHUNK_CONTENT_MAX_LEN) {
                            content = content.substring(0, CHUNK_CONTENT_MAX_LEN);
                        }
                        refVO.setChunkContent(content);
                    }
                }

                sourceRefVOs.add(refVO);
            }
            vo.setSourceRefs(sourceRefVOs);
        }

        return vo;
    }

    /**
     * 构造仅更新 view_count 的 FaqItem 对象（避免触发 updatedAt 之外字段的更新）
     */
    private FaqItem buildViewCountUpdate(Long id, Integer currentViewCount) {
        FaqItem update = new FaqItem();
        update.setId(id);
        update.setViewCount(currentViewCount == null ? 1 : currentViewCount + 1);
        return update;
    }
}
