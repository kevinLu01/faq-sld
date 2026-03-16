-- SLD FAQ 数据库初始化脚本
-- 创建时间：2026-03
-- 说明：由 docker-compose 启动时自动执行（挂载至 /docker-entrypoint-initdb.d/）
--       仅在数据库首次初始化时执行，已有数据不会被覆盖

SET client_encoding = 'UTF8';

-- ============================================================
-- 用户与权限模块
-- ============================================================

-- ----------------------------------------------------------
-- sys_department：部门表（支持多级树形结构）
-- ----------------------------------------------------------
CREATE TABLE sys_department (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    parent_id   BIGINT,                          -- NULL 表示顶级部门
    sort_order  INT          NOT NULL DEFAULT 0, -- 同级排序权重，越小越靠前
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  sys_department             IS '部门表';
COMMENT ON COLUMN sys_department.id          IS '主键';
COMMENT ON COLUMN sys_department.name        IS '部门名称';
COMMENT ON COLUMN sys_department.parent_id   IS '父部门 ID，NULL 表示顶级部门';
COMMENT ON COLUMN sys_department.sort_order  IS '同级排序，升序';
COMMENT ON COLUMN sys_department.created_at  IS '创建时间';

-- ----------------------------------------------------------
-- sys_role：角色表
-- ----------------------------------------------------------
CREATE TABLE sys_role (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,    -- ADMIN | REVIEWER | SUBMITTER
    name        VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  sys_role            IS '角色表';
COMMENT ON COLUMN sys_role.id         IS '主键';
COMMENT ON COLUMN sys_role.code       IS '角色编码：ADMIN / REVIEWER / SUBMITTER';
COMMENT ON COLUMN sys_role.name       IS '角色显示名称';
COMMENT ON COLUMN sys_role.created_at IS '创建时间';

-- ----------------------------------------------------------
-- sys_user：用户表（通过企业微信 OAuth 登录）
-- ----------------------------------------------------------
CREATE TABLE sys_user (
    id              BIGSERIAL    PRIMARY KEY,
    wecom_user_id   VARCHAR(64)  NOT NULL UNIQUE, -- 企业微信成员账号 userid
    name            VARCHAR(64)  NOT NULL,
    mobile          VARCHAR(20),
    avatar          VARCHAR(255),                 -- 头像 URL
    department_id   BIGINT       REFERENCES sys_department(id),
    status          SMALLINT     NOT NULL DEFAULT 1, -- 1:正常 0:禁用
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  sys_user                  IS '用户表';
COMMENT ON COLUMN sys_user.id               IS '主键';
COMMENT ON COLUMN sys_user.wecom_user_id    IS '企业微信成员账号 userid，全局唯一';
COMMENT ON COLUMN sys_user.name             IS '姓名';
COMMENT ON COLUMN sys_user.mobile          IS '手机号';
COMMENT ON COLUMN sys_user.avatar           IS '头像 URL';
COMMENT ON COLUMN sys_user.department_id    IS '所属部门 ID';
COMMENT ON COLUMN sys_user.status           IS '账号状态：1=正常，0=禁用';
COMMENT ON COLUMN sys_user.created_at       IS '首次登录（创建）时间';
COMMENT ON COLUMN sys_user.updated_at       IS '最后更新时间';

-- ----------------------------------------------------------
-- sys_user_role：用户-角色关联表（多对多）
-- ----------------------------------------------------------
CREATE TABLE sys_user_role (
    user_id     BIGINT  NOT NULL REFERENCES sys_user(id),
    role_id     BIGINT  NOT NULL REFERENCES sys_role(id),
    PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE  sys_user_role         IS '用户角色关联表';
COMMENT ON COLUMN sys_user_role.user_id IS '用户 ID';
COMMENT ON COLUMN sys_user_role.role_id IS '角色 ID';

-- ============================================================
-- 文件与解析模块
-- ============================================================

-- ----------------------------------------------------------
-- kb_file：上传的知识库文件记录
-- ----------------------------------------------------------
CREATE TABLE kb_file (
    id              BIGSERIAL    PRIMARY KEY,
    original_name   VARCHAR(255) NOT NULL,          -- 原始文件名（含扩展名）
    minio_path      VARCHAR(512) NOT NULL,           -- MinIO 中的对象路径
    file_size       BIGINT,                          -- 文件大小（字节）
    file_type       VARCHAR(16)  NOT NULL,           -- pdf|docx|xlsx|txt|csv
    parse_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    -- 解析状态：PENDING|PARSING|SUCCESS|FAILED|SCAN_PDF（扫描版无文本层）
    parse_error     TEXT,                            -- 解析失败时的错误信息
    chunk_count     INT          NOT NULL DEFAULT 0, -- 切块成功后的 chunk 总数
    submitter_id    BIGINT       NOT NULL REFERENCES sys_user(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kb_file_submitter ON kb_file(submitter_id);
CREATE INDEX idx_kb_file_status    ON kb_file(parse_status);

COMMENT ON TABLE  kb_file                IS '知识库文件表';
COMMENT ON COLUMN kb_file.id             IS '主键';
COMMENT ON COLUMN kb_file.original_name  IS '原始上传文件名（含扩展名）';
COMMENT ON COLUMN kb_file.minio_path     IS 'MinIO 对象存储路径';
COMMENT ON COLUMN kb_file.file_size      IS '文件大小（字节）';
COMMENT ON COLUMN kb_file.file_type      IS '文件类型：pdf / docx / xlsx / txt / csv';
COMMENT ON COLUMN kb_file.parse_status   IS '解析状态：PENDING / PARSING / SUCCESS / FAILED / SCAN_PDF';
COMMENT ON COLUMN kb_file.parse_error    IS '解析失败时的错误信息';
COMMENT ON COLUMN kb_file.chunk_count    IS '文本切块数量';
COMMENT ON COLUMN kb_file.submitter_id   IS '上传人用户 ID';
COMMENT ON COLUMN kb_file.created_at     IS '上传时间';
COMMENT ON COLUMN kb_file.updated_at     IS '最后更新时间';

-- ----------------------------------------------------------
-- kb_chunk：文本切块表
-- ----------------------------------------------------------
CREATE TABLE kb_chunk (
    id              BIGSERIAL   PRIMARY KEY,
    file_id         BIGINT      NOT NULL REFERENCES kb_file(id),
    chunk_index     INT         NOT NULL,       -- 块在文件中的顺序（从 0 开始）
    raw_content     TEXT        NOT NULL,       -- 原始提取文本
    clean_content   TEXT        NOT NULL,       -- 清洗后文本（用于 LLM 输入）
    token_count     INT,                        -- 估算 token 数
    metadata        JSONB,                      -- 扩展信息：来源页码、段落标题等
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kb_chunk_file ON kb_chunk(file_id);

COMMENT ON TABLE  kb_chunk               IS '知识库文本切块表';
COMMENT ON COLUMN kb_chunk.id            IS '主键';
COMMENT ON COLUMN kb_chunk.file_id       IS '所属文件 ID';
COMMENT ON COLUMN kb_chunk.chunk_index   IS '块顺序编号，同文件内从 0 开始';
COMMENT ON COLUMN kb_chunk.raw_content   IS '原始提取文本（未清洗）';
COMMENT ON COLUMN kb_chunk.clean_content IS '清洗后文本，作为 LLM 输入';
COMMENT ON COLUMN kb_chunk.token_count   IS '估算 token 数（tiktoken 计算）';
COMMENT ON COLUMN kb_chunk.metadata      IS 'JSON 扩展字段：来源页码、段落标题等';
COMMENT ON COLUMN kb_chunk.created_at    IS '创建时间';

-- ----------------------------------------------------------
-- kb_task：异步任务状态表
-- ----------------------------------------------------------
CREATE TABLE kb_task (
    id          BIGSERIAL   PRIMARY KEY,
    file_id     BIGINT      NOT NULL REFERENCES kb_file(id),
    task_type   VARCHAR(32) NOT NULL,           -- PARSE | GENERATE
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    -- 任务状态：PENDING | RUNNING | SUCCESS | FAILED
    progress    INT         NOT NULL DEFAULT 0, -- 进度 0~100
    error_msg   TEXT,                           -- 失败时的错误信息
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kb_task_file ON kb_task(file_id);

COMMENT ON TABLE  kb_task            IS '异步任务状态表';
COMMENT ON COLUMN kb_task.id         IS '主键';
COMMENT ON COLUMN kb_task.file_id    IS '关联文件 ID';
COMMENT ON COLUMN kb_task.task_type  IS '任务类型：PARSE（文档解析）/ GENERATE（FAQ 生成）';
COMMENT ON COLUMN kb_task.status     IS '任务状态：PENDING / RUNNING / SUCCESS / FAILED';
COMMENT ON COLUMN kb_task.progress   IS '任务进度，取值 0~100';
COMMENT ON COLUMN kb_task.error_msg  IS '失败时的错误信息';
COMMENT ON COLUMN kb_task.created_at IS '创建时间';
COMMENT ON COLUMN kb_task.updated_at IS '最后更新时间';

-- ============================================================
-- FAQ 模块
-- ============================================================

-- ----------------------------------------------------------
-- faq_category：FAQ 分类表（支持树形结构）
-- ----------------------------------------------------------
CREATE TABLE faq_category (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    parent_id   BIGINT,                          -- NULL 表示顶级分类
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  faq_category             IS 'FAQ 分类表';
COMMENT ON COLUMN faq_category.id          IS '主键';
COMMENT ON COLUMN faq_category.name        IS '分类名称';
COMMENT ON COLUMN faq_category.parent_id   IS '父分类 ID，NULL 表示顶级分类';
COMMENT ON COLUMN faq_category.sort_order  IS '同级排序，升序';
COMMENT ON COLUMN faq_category.created_at  IS '创建时间';

-- ----------------------------------------------------------
-- faq_candidate：LLM 生成的 FAQ 候选（待审核）
-- ----------------------------------------------------------
CREATE TABLE faq_candidate (
    id              BIGSERIAL       PRIMARY KEY,
    file_id         BIGINT          NOT NULL REFERENCES kb_file(id),
    chunk_id        BIGINT          NOT NULL REFERENCES kb_chunk(id),
    question        TEXT            NOT NULL,   -- LLM 生成的问题
    answer          TEXT            NOT NULL,   -- LLM 生成的答案
    category        VARCHAR(64),                -- LLM 预测的分类名称
    keywords        VARCHAR(255),               -- 关键词，逗号分隔
    source_summary  TEXT,                       -- LLM 生成的原文摘要
    confidence      DECIMAL(4,3),               -- 置信度 0.000~1.000
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    -- 审核状态：PENDING | APPROVED | REJECTED | MERGED
    reject_reason   TEXT,                       -- 驳回原因
    merged_faq_id   BIGINT,                     -- status=MERGED 时关联的正式 faq_item ID
    reviewer_id     BIGINT          REFERENCES sys_user(id),
    reviewed_at     TIMESTAMP,                  -- 审核操作时间
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_candidate_status   ON faq_candidate(status);
CREATE INDEX idx_candidate_file     ON faq_candidate(file_id);
CREATE INDEX idx_candidate_reviewer ON faq_candidate(reviewer_id);

COMMENT ON TABLE  faq_candidate                IS 'FAQ 候选表（LLM 生成，待审核）';
COMMENT ON COLUMN faq_candidate.id             IS '主键';
COMMENT ON COLUMN faq_candidate.file_id        IS '来源文件 ID';
COMMENT ON COLUMN faq_candidate.chunk_id       IS '来源文本块 ID';
COMMENT ON COLUMN faq_candidate.question       IS 'LLM 生成的问题';
COMMENT ON COLUMN faq_candidate.answer         IS 'LLM 生成的答案';
COMMENT ON COLUMN faq_candidate.category       IS 'LLM 预测的分类名称';
COMMENT ON COLUMN faq_candidate.keywords       IS '关键词，逗号分隔，3~5 个';
COMMENT ON COLUMN faq_candidate.source_summary IS '原文内容摘要';
COMMENT ON COLUMN faq_candidate.confidence     IS 'LLM 置信度，范围 0.000~1.000';
COMMENT ON COLUMN faq_candidate.status         IS '审核状态：PENDING / APPROVED / REJECTED / MERGED';
COMMENT ON COLUMN faq_candidate.reject_reason  IS '驳回原因（status=REJECTED 时填写）';
COMMENT ON COLUMN faq_candidate.merged_faq_id  IS '合并目标的正式 FAQ ID（status=MERGED 时填写）';
COMMENT ON COLUMN faq_candidate.reviewer_id    IS '审核人用户 ID';
COMMENT ON COLUMN faq_candidate.reviewed_at    IS '审核操作时间';
COMMENT ON COLUMN faq_candidate.created_at     IS '候选记录创建时间';
COMMENT ON COLUMN faq_candidate.updated_at     IS '最后更新时间';

-- ----------------------------------------------------------
-- faq_item：已审核发布的正式 FAQ 条目
-- ----------------------------------------------------------
CREATE TABLE faq_item (
    id              BIGSERIAL   PRIMARY KEY,
    question        TEXT        NOT NULL,
    answer          TEXT        NOT NULL,
    category_id     BIGINT      REFERENCES faq_category(id),
    keywords        VARCHAR(255),               -- 关键词，逗号分隔
    status          SMALLINT    NOT NULL DEFAULT 1, -- 1:已发布 0:已下线
    view_count      INT         NOT NULL DEFAULT 0, -- 浏览次数
    publisher_id    BIGINT      NOT NULL REFERENCES sys_user(id),
    published_at    TIMESTAMP,                  -- 首次发布时间
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_faq_item_category ON faq_item(category_id);
CREATE INDEX idx_faq_item_status   ON faq_item(status);

COMMENT ON TABLE  faq_item               IS '正式 FAQ 条目表';
COMMENT ON COLUMN faq_item.id            IS '主键';
COMMENT ON COLUMN faq_item.question      IS '问题';
COMMENT ON COLUMN faq_item.answer        IS '答案';
COMMENT ON COLUMN faq_item.category_id   IS 'FAQ 分类 ID';
COMMENT ON COLUMN faq_item.keywords      IS '关键词，逗号分隔';
COMMENT ON COLUMN faq_item.status        IS '状态：1=已发布，0=已下线';
COMMENT ON COLUMN faq_item.view_count    IS '浏览次数';
COMMENT ON COLUMN faq_item.publisher_id  IS '发布人用户 ID（审核通过时自动填写）';
COMMENT ON COLUMN faq_item.published_at  IS '首次发布时间';
COMMENT ON COLUMN faq_item.created_at    IS '创建时间';
COMMENT ON COLUMN faq_item.updated_at    IS '最后更新时间';

-- ----------------------------------------------------------
-- faq_source_ref：FAQ 与来源 chunk/文件的关联（溯源）
-- ----------------------------------------------------------
CREATE TABLE faq_source_ref (
    id              BIGSERIAL   PRIMARY KEY,
    faq_id          BIGINT      NOT NULL REFERENCES faq_item(id),
    candidate_id    BIGINT      NOT NULL REFERENCES faq_candidate(id),
    chunk_id        BIGINT      NOT NULL REFERENCES kb_chunk(id),
    file_id         BIGINT      NOT NULL REFERENCES kb_file(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_source_ref_faq ON faq_source_ref(faq_id);

COMMENT ON TABLE  faq_source_ref               IS 'FAQ 来源溯源表';
COMMENT ON COLUMN faq_source_ref.id            IS '主键';
COMMENT ON COLUMN faq_source_ref.faq_id        IS '正式 FAQ 条目 ID';
COMMENT ON COLUMN faq_source_ref.candidate_id  IS '来源候选记录 ID';
COMMENT ON COLUMN faq_source_ref.chunk_id      IS '来源文本块 ID';
COMMENT ON COLUMN faq_source_ref.file_id       IS '来源文件 ID';
COMMENT ON COLUMN faq_source_ref.created_at    IS '创建时间';

-- ============================================================
-- 初始化数据
-- ============================================================

-- 角色：ADMIN / REVIEWER / SUBMITTER
INSERT INTO sys_role (code, name) VALUES
    ('ADMIN',     '管理员'),
    ('REVIEWER',  '审核人'),
    ('SUBMITTER', '提交人');

-- FAQ 分类（一级，与 LLM Prompt 中的 category 选项保持一致）
INSERT INTO faq_category (name, parent_id, sort_order) VALUES
    ('产品规格', NULL, 1),
    ('安装维修', NULL, 2),
    ('故障排查', NULL, 3),
    ('售后政策', NULL, 4),
    ('操作说明', NULL, 5),
    ('其他',     NULL, 9);

-- 测试管理员账号（企业微信 mock-login 使用）
INSERT INTO sys_user (wecom_user_id, name, mobile, status) VALUES
    ('admin001', '管理员', NULL, 1);

-- 给 admin001 绑定 ADMIN 角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
         JOIN sys_role r ON r.code = 'ADMIN'
WHERE u.wecom_user_id = 'admin001';

-- ============================================================
-- 产品库模块
-- ============================================================

-- ----------------------------------------------------------
-- product_candidate：LLM 提取的产品候选，待人工审核
-- ----------------------------------------------------------
CREATE TABLE product_candidate (
    id             BIGSERIAL    PRIMARY KEY,
    file_id        BIGINT       NOT NULL REFERENCES kb_file(id),
    chunk_id       BIGINT       REFERENCES kb_chunk(id),
    name           VARCHAR(200),                        -- 产品名称
    model          VARCHAR(100),                        -- 型号
    brand          VARCHAR(100),                        -- 厂家/品牌
    specs          JSONB,                               -- 规格参数 {"制冷量":"3.5kW","电压":"220V"}
    compat_models  TEXT,                               -- 适配机型（逗号分隔）
    category       VARCHAR(100),
    source_summary TEXT,
    confidence     FLOAT        NOT NULL DEFAULT 0.5,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED
    reject_reason  TEXT,
    reviewer_id    BIGINT       REFERENCES sys_user(id),
    reviewed_at    TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_candidate_file   ON product_candidate(file_id);
CREATE INDEX idx_product_candidate_status ON product_candidate(status);

COMMENT ON TABLE  product_candidate                IS '产品候选表（LLM 提取，待审核）';
COMMENT ON COLUMN product_candidate.specs          IS '规格参数 JSON，键值对灵活存储';
COMMENT ON COLUMN product_candidate.compat_models  IS '适配机型，逗号分隔';

-- ----------------------------------------------------------
-- product_item：已审核通过的正式产品档案
-- ----------------------------------------------------------
CREATE TABLE product_item (
    id             BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    model          VARCHAR(100),
    brand          VARCHAR(100),
    category_id    BIGINT       REFERENCES faq_category(id),
    specs          JSONB,
    compat_models  TEXT,
    description    TEXT,
    status         INTEGER      NOT NULL DEFAULT 1,     -- 1=上架 0=下架
    publisher_id   BIGINT       REFERENCES sys_user(id),
    published_at   TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_item_category ON product_item(category_id);
CREATE INDEX idx_product_item_status   ON product_item(status);
CREATE INDEX idx_product_item_model    ON product_item(model);

COMMENT ON TABLE  product_item              IS '产品档案表（已审核发布）';
COMMENT ON COLUMN product_item.specs        IS '规格参数 JSON';
COMMENT ON COLUMN product_item.compat_models IS '适配机型，逗号分隔';

-- ----------------------------------------------------------
-- product_source_ref：产品档案 ↔ 来源文件的溯源关联
-- ----------------------------------------------------------
CREATE TABLE product_source_ref (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT    NOT NULL REFERENCES product_item(id),
    candidate_id   BIGINT    REFERENCES product_candidate(id),
    file_id        BIGINT    REFERENCES kb_file(id),
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE product_source_ref IS '产品档案来源溯源表';
