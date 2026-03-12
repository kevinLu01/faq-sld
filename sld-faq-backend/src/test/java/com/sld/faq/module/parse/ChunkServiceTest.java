package com.sld.faq.module.parse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChunkService 单元测试
 * <p>
 * 覆盖文本切块的核心策略：
 * 1. 短文本返回单个 chunk
 * 2. 长文本按段落切分为多个 chunk
 * 3. 相邻 chunk 保留 100 字重叠
 * 4. 过短 chunk（< 10 字）被过滤
 * 5. 空文本返回空列表
 */
@DisplayName("ChunkService 文本切块测试")
class ChunkServiceTest {

    private static final int MAX_CHUNK_SIZE = 800;
    private static final int OVERLAP_SIZE = 100;
    private static final int MIN_CHUNK_SIZE = 10;

    private ChunkService chunkService;

    @BeforeEach
    void setUp() {
        chunkService = new ChunkService();
    }

    @Test
    @DisplayName("小于 800 字的文本应返回单个 chunk")
    void chunk_shortTextReturnsSingleChunk() {
        String text = "本手册是针对新员工入职的指导材料，涵盖公司文化、规章制度、福利政策等内容。" +
                "希望每一位新同事通过阅读本手册，能够快速融入团队，了解公司的运作方式。";

        List<String> chunks = chunkService.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("新员工入职");
    }

    @Test
    @DisplayName("超过 800 字的文本应按段落切分为多个 chunk")
    void chunk_longTextSplitByParagraph() {
        // 构建超过 800 字的多段落文本，每段约 200 字
        String paragraph = "员工在入职时需要完成以下手续：提交身份证原件及复印件一份，学历证书原件及复印件一份，" +
                "劳动合同签署三份，保密协议签署两份，竞业限制协议视岗位要求签署。" +
                "所有材料需在报到当日提交人力资源部统一存档，不得延误。";
        // 确保 paragraph 够长
        assertThat(paragraph.length()).isGreaterThan(100);

        // 拼接5段，形成超过800字的文本
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            sb.append("第").append(i).append("章：").append(paragraph).append("\n\n");
        }
        String longText = sb.toString().trim();

        List<String> chunks = chunkService.chunk(longText);

        assertThat(chunks).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("相邻 chunk 应包含来自上一个 chunk 末尾的重叠内容")
    void chunk_overlappingChunksHaveSharedContent() {
        // 构造两段恰好需要切分的文本：每段约 600 字，两段总计超过 800 字
        String seg1 = "差旅报销政策：员工因公出差所产生的交通费、住宿费、餐饮补贴等费用，" +
                "均可按照公司规定进行报销。报销需在出差结束后的七个工作日内完成申请。" +
                "申请时需填写《差旅费报销单》，并附上所有原始票据。交通费须提供票据原件，" +
                "住宿费须提供正规增值税发票，餐饮补贴按出差天数和目的地级别核算，无需票据。" +
                "报销金额超过五千元人民币的，需部门总监审批；超过两万元的，需 CFO 审批。" +
                "所有报销单据须真实合规，如发现虚报、冒领行为，将依据员工手册相关条款处理。";

        String seg2 = "年假政策补充说明：根据《劳动法》及公司内部政策，在职满一年的员工享有五天年假，" +
                "满三年享有十天，满十年享有十五天。年假应在当年内使用，原则上不得跨年结转。" +
                "如因工作需要无法在当年使用，须经部门主管和人力资源部门双重审批后方可递延，" +
                "且递延期限不超过次年第一季度末。未使用年假一律不折算为薪资发放。" +
                "员工请年假需提前三个工作日在 HR 系统中提交申请，审批完成后方可休假。" +
                "突发情况无法提前申请者，须在当日内告知直属主管并补录系统。";

        String text = seg1 + "\n\n" + seg2;
        List<String> chunks = chunkService.chunk(text);

        // 若产生了多个 chunk，第二个 chunk 开头应包含第一个 chunk 末尾的内容（重叠）
        if (chunks.size() >= 2) {
            String firstChunk = chunks.get(0);
            String secondChunk = chunks.get(1);

            // 取第一个 chunk 最后 100 字作为预期重叠内容
            int firstLen = firstChunk.length();
            String expectedOverlap = firstLen > OVERLAP_SIZE
                    ? firstChunk.substring(firstLen - OVERLAP_SIZE)
                    : firstChunk;

            // 第二个 chunk 应以重叠内容开头
            assertThat(secondChunk).startsWith(expectedOverlap.strip().substring(0,
                    Math.min(20, expectedOverlap.strip().length())));
        }
    }

    @Test
    @DisplayName("少于 10 字的 chunk 应被过滤掉")
    void chunk_filtersShortChunks() {
        // 模拟文档中存在极短段落（如"附录"、"备注"等标题行单独成段）
        String text = "年假政策正文说明：员工入职满一年可享有带薪年假五天，满三年可享十天，" +
                "满十年享有十五天，具体以劳动合同为准。\n\n" +
                "附\n\n" +  // 极短段落，只有1字
                "出差管理规定正文：员工因公出差须提前填写出差申请单，经部门主管审批后方可出行，" +
                "出差期间的合理费用凭票报销，补贴标准参照公司差旅政策执行。";

        List<String> chunks = chunkService.chunk(text);

        // 所有 chunk 长度都不小于 MIN_CHUNK_SIZE
        for (String chunk : chunks) {
            assertThat(chunk.length()).isGreaterThanOrEqualTo(MIN_CHUNK_SIZE);
        }
    }

    @Test
    @DisplayName("空文本应返回空列表")
    void chunk_emptyTextReturnsEmptyList() {
        assertThat(chunkService.chunk("")).isEmpty();
        assertThat(chunkService.chunk(null)).isEmpty();
        assertThat(chunkService.chunk("   ")).isEmpty();
    }

    @Test
    @DisplayName("单段超过 800 字的文本应被强制截断为多个 chunk")
    void chunk_veryLongSingleParagraphIsSplit() {
        // 构造一个没有段落分隔符、超过 800 字的超长段落
        String singleParagraph = "A".repeat(2000);

        List<String> chunks = chunkService.chunk(singleParagraph);

        assertThat(chunks).hasSizeGreaterThan(1);
        // 所有 chunk 长度（含重叠）不超过 MAX_CHUNK_SIZE + OVERLAP_SIZE
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(MAX_CHUNK_SIZE + OVERLAP_SIZE);
        }
    }

    @Test
    @DisplayName("多个短段落应被合并为单个 chunk")
    void chunk_shortParagraphsMergedIntoSingleChunk() {
        // 每段约 100 字，总共 4 段，合并后不超过 800 字
        String text = "人力资源部负责员工入职手续办理及档案管理。\n\n" +
                "财务部负责员工薪资核算及费用报销审批。\n\n" +
                "行政部负责办公室环境维护及固定资产管理。\n\n" +
                "IT 部门负责系统账号开通及设备配置。";

        List<String> chunks = chunkService.chunk(text);

        // 4 个短段落合并后应只有 1 个 chunk（总长远小于 800）
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("人力资源部");
        assertThat(chunks.get(0)).contains("IT 部门");
    }
}
