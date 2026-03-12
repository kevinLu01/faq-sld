# SLD FAQ 智能整理助手 — 详细设计文档

## 一、系统架构

### 1.1 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                    docker-compose                         │
│                                                          │
│  ┌─────────────┐    ┌─────────────┐   ┌──────────────┐  │
│  │   frontend  │    │   backend   │   │  ocr-service │  │
│  │  Vue3/Nginx │───▶│  Spring Boot│──▶│  GOT-OCR 2.0 │  │
│  └─────────────┘    └──────┬──────┘   │  FastAPI/GPU │  │
│                            │          └──────────────┘  │
│              ┌─────────────┼─────────────┐              │
│              ▼             ▼             ▼              │
│         ┌─────────┐ ┌──────────┐ ┌───────────┐         │
│         │PostgreSQL│ │  Redis   │ │   MinIO   │         │
│         └─────────┘ └──────────┘ └───────────┘         │
└──────────────────────────────────────────────────────────┘
                              │
                    外部服务（出内网）
                              │
                    ┌─────────────────┐
                    │   LLM API       │
                    │ (OpenAI兼容接口) │
                    └─────────────────┘
```

### 1.2 核心流程

**OAuth 登录链路**
```
前端 → GET /api/auth/wecom/url
     ← redirect_url (含 state 写入 Redis TTL=5min)
前端跳转企业微信授权页
企业微信回调 → 前端 /login?code=xxx&state=xxx
前端 → POST /api/auth/wecom/callback { code, state }
后端校验 state → 调企业微信 API 换取用户身份
首次登录自动创建用户(角色默认 SUBMITTER)
后端签发 JWT → 前端存 localStorage
```

**文件上传 & FAQ 生成链路**
```
前端 → POST /api/files/upload (multipart)
后端 → 存文件到 MinIO → 写 kb_file 记录
     ← { fileId }

前端 → POST /api/files/{id}/generate-faq
后端 → 创建 kb_task(PENDING) → 提交 @Async 任务
     ← { taskId }

后台异步执行：
  kb_task(RUNNING)
  → 解析文档(PDF/Word/Excel/txt/csv)
    → 若扫描版 PDF → OcrClient → GOT-OCR 服务
  → TextCleaner 清洗
  → ChunkService 切块(max 800字, 100字重叠)
  → 逐 chunk 调 LlmClient → PromptBuilder(DOCUMENT模式)
  → 解析 JSON → 写 faq_candidate(PENDING)
  kb_task(SUCCESS/FAILED)

前端轮询 GET /api/tasks/{taskId}/status (每3秒)
```

**审核发布链路**
```
REVIEWER → GET /api/faq-candidates?status=PENDING
         → GET /api/faq-candidates/{id}
         → POST /{id}/approve         → 生成 faq_item + faq_source_ref
         → POST /{id}/edit-approve    → 修改后生成 faq_item
         → POST /{id}/reject          → 记录驳回原因
         → POST /{id}/merge           → 关联已有 faq_item + faq_source_ref
```

---

## 二、项目目录结构

### 2.1 仓库根目录

```
sld-faq/
├── docker-compose.yml
├── docker-compose.override.yml   # 本地开发覆盖配置
├── docs/
│   ├── design.md                 # 本文档
│   └── sql/
│       └── init.sql              # 建表 + 初始化数据
├── sld-faq-backend/
├── sld-faq-frontend/
├── ocr-service/
└── CLAUDE.md
```

### 2.2 后端目录

```
sld-faq-backend/
├── Dockerfile
├── pom.xml
└── src/main/
    ├── java/com/sld/faq/
    │   ├── SldFaqApplication.java
    │   ├── config/
    │   │   ├── SaTokenConfig.java
    │   │   ├── AsyncConfig.java          # 线程池配置
    │   │   ├── MinioConfig.java
    │   │   └── properties/
    │   │       ├── WeComProperties.java  # @ConfigurationProperties
    │   │       ├── MinioProperties.java
    │   │       ├── LlmProperties.java
    │   │       └── OcrProperties.java
    │   ├── common/
    │   │   ├── ApiResponse.java          # 统一响应体
    │   │   ├── PageResult.java           # 分页响应体
    │   │   ├── BusinessException.java
    │   │   └── GlobalExceptionHandler.java
    │   ├── infrastructure/
    │   │   ├── llm/
    │   │   │   ├── LlmClient.java
    │   │   │   └── dto/LlmRequest.java / LlmResponse.java
    │   │   ├── ocr/
    │   │   │   ├── OcrClient.java        # 统一接口
    │   │   │   └── impl/LocalOcrClient.java
    │   │   ├── storage/
    │   │   │   └── MinioStorage.java
    │   │   └── wecom/
    │   │       └── WeComOAuthService.java
    │   └── module/
    │       ├── auth/
    │       │   ├── AuthController.java
    │       │   ├── AuthService.java
    │       │   └── dto/
    │       │       ├── WeComCallbackRequest.java
    │       │       └── LoginResponse.java
    │       ├── user/
    │       │   ├── UserController.java
    │       │   ├── UserService.java
    │       │   ├── entity/
    │       │   │   ├── SysUser.java
    │       │   │   ├── SysRole.java
    │       │   │   └── SysDepartment.java
    │       │   └── mapper/
    │       │       └── SysUserMapper.java
    │       ├── file/
    │       │   ├── FileController.java
    │       │   ├── FileService.java
    │       │   ├── entity/KbFile.java
    │       │   ├── entity/KbTask.java
    │       │   ├── mapper/KbFileMapper.java
    │       │   ├── mapper/KbTaskMapper.java
    │       │   └── vo/FileVO.java
    │       ├── parse/
    │       │   ├── DocumentParseService.java  # 入口，按类型分发
    │       │   ├── ChunkService.java
    │       │   ├── TextCleaner.java
    │       │   └── parser/
    │       │       ├── PdfParser.java
    │       │       ├── WordParser.java
    │       │       ├── ExcelParser.java
    │       │       └── PlainTextParser.java
    │       ├── generate/
    │       │   ├── FaqGenerationService.java  # @Async 任务入口
    │       │   ├── PromptBuilder.java
    │       │   └── FaqJsonParser.java         # 容错 JSON 解析
    │       ├── candidate/
    │       │   ├── FaqCandidateController.java
    │       │   ├── FaqCandidateService.java
    │       │   ├── FaqReviewService.java
    │       │   ├── entity/FaqCandidate.java
    │       │   ├── mapper/FaqCandidateMapper.java
    │       │   ├── dto/ReviewRequest.java
    │       │   └── vo/CandidateVO.java
    │       └── faq/
    │           ├── FaqController.java
    │           ├── FaqService.java
    │           ├── entity/FaqItem.java
    │           ├── entity/FaqCategory.java
    │           ├── entity/FaqSourceRef.java
    │           ├── mapper/FaqItemMapper.java
    │           └── vo/FaqVO.java
    └── resources/
        ├── application.yml
        ├── application-dev.yml
        └── mapper/                       # MyBatis Plus XML (复杂查询)
```

### 2.3 前端目录

```
sld-faq-frontend/
├── Dockerfile
├── nginx.conf
├── vite.config.ts
├── tsconfig.json
└── src/
    ├── main.ts
    ├── App.vue
    ├── router/
    │   └── index.ts
    ├── stores/
    │   └── user.ts                # Pinia，存 userInfo + token
    ├── api/
    │   ├── request.ts             # Axios 实例 + 拦截器 + 401 处理
    │   ├── auth.ts
    │   ├── file.ts
    │   ├── candidate.ts
    │   └── faq.ts
    ├── pages/
    │   ├── LoginPage.vue
    │   ├── HomePage.vue
    │   ├── UploadPage.vue
    │   ├── review/
    │   │   ├── ReviewListPage.vue
    │   │   └── ReviewDetailPage.vue
    │   ├── faq/
    │   │   ├── FaqListPage.vue
    │   │   └── FaqDetailPage.vue
    │   └── MePage.vue
    ├── components/
    │   ├── AppHeader.vue
    │   └── ExpandableText.vue     # 长文本折叠展开
    └── types/
        └── index.ts               # 所有 TS 接口定义
```

### 2.4 OCR 服务目录

```
ocr-service/
├── Dockerfile
├── requirements.txt
├── main.py                        # FastAPI 入口
└── ocr_engine.py                  # GOT-OCR 2.0 封装
```

---

## 三、数据库设计

```sql
-- ========== 用户与权限 ==========

CREATE TABLE sys_department (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    parent_id   BIGINT,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,  -- ADMIN | REVIEWER | SUBMITTER
    name        VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE sys_user (
    id              BIGSERIAL PRIMARY KEY,
    wecom_user_id   VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(64)  NOT NULL,
    mobile          VARCHAR(20),
    avatar          VARCHAR(255),
    department_id   BIGINT       REFERENCES sys_department(id),
    status          SMALLINT     NOT NULL DEFAULT 1,  -- 1:正常 0:禁用
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE sys_user_role (
    user_id     BIGINT  NOT NULL REFERENCES sys_user(id),
    role_id     BIGINT  NOT NULL REFERENCES sys_role(id),
    PRIMARY KEY (user_id, role_id)
);

-- ========== 文件与解析 ==========

CREATE TABLE kb_file (
    id              BIGSERIAL PRIMARY KEY,
    original_name   VARCHAR(255) NOT NULL,
    minio_path      VARCHAR(512) NOT NULL,
    file_size       BIGINT,
    file_type       VARCHAR(16)  NOT NULL,  -- pdf|docx|xlsx|txt|csv
    parse_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    -- PENDING|PARSING|SUCCESS|FAILED|SCAN_PDF(扫描版无文本层)
    parse_error     TEXT,
    chunk_count     INT          NOT NULL DEFAULT 0,
    submitter_id    BIGINT       NOT NULL REFERENCES sys_user(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_file_submitter ON kb_file(submitter_id);
CREATE INDEX idx_kb_file_status    ON kb_file(parse_status);

CREATE TABLE kb_chunk (
    id              BIGSERIAL PRIMARY KEY,
    file_id         BIGINT   NOT NULL REFERENCES kb_file(id),
    chunk_index     INT      NOT NULL,
    raw_content     TEXT     NOT NULL,
    clean_content   TEXT     NOT NULL,
    token_count     INT,
    metadata        JSONB,                -- 来源页码、段落标题等
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_chunk_file ON kb_chunk(file_id);

CREATE TABLE kb_task (
    id          BIGSERIAL PRIMARY KEY,
    file_id     BIGINT      NOT NULL REFERENCES kb_file(id),
    task_type   VARCHAR(32) NOT NULL,  -- PARSE | GENERATE
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    -- PENDING|RUNNING|SUCCESS|FAILED
    progress    INT         NOT NULL DEFAULT 0,  -- 0~100
    error_msg   TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_task_file ON kb_task(file_id);

-- ========== FAQ ==========

CREATE TABLE faq_category (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    parent_id   BIGINT,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE faq_candidate (
    id              BIGSERIAL PRIMARY KEY,
    file_id         BIGINT          NOT NULL REFERENCES kb_file(id),
    chunk_id        BIGINT          NOT NULL REFERENCES kb_chunk(id),
    question        TEXT            NOT NULL,
    answer          TEXT            NOT NULL,
    category        VARCHAR(64),
    keywords        VARCHAR(255),
    source_summary  TEXT,
    confidence      DECIMAL(4,3),
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    -- PENDING|APPROVED|REJECTED|MERGED
    reject_reason   TEXT,
    merged_faq_id   BIGINT,                   -- status=MERGED 时关联的 faq_item
    reviewer_id     BIGINT          REFERENCES sys_user(id),
    reviewed_at     TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_candidate_status  ON faq_candidate(status);
CREATE INDEX idx_candidate_file    ON faq_candidate(file_id);
CREATE INDEX idx_candidate_reviewer ON faq_candidate(reviewer_id);

CREATE TABLE faq_item (
    id              BIGSERIAL PRIMARY KEY,
    question        TEXT        NOT NULL,
    answer          TEXT        NOT NULL,
    category_id     BIGINT      REFERENCES faq_category(id),
    keywords        VARCHAR(255),
    status          SMALLINT    NOT NULL DEFAULT 1,  -- 1:已发布 0:已下线
    view_count      INT         NOT NULL DEFAULT 0,
    publisher_id    BIGINT      NOT NULL REFERENCES sys_user(id),
    published_at    TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_faq_item_category ON faq_item(category_id);
CREATE INDEX idx_faq_item_status   ON faq_item(status);

CREATE TABLE faq_source_ref (
    id              BIGSERIAL PRIMARY KEY,
    faq_id          BIGINT  NOT NULL REFERENCES faq_item(id),
    candidate_id    BIGINT  NOT NULL REFERENCES faq_candidate(id),
    chunk_id        BIGINT  NOT NULL REFERENCES kb_chunk(id),
    file_id         BIGINT  NOT NULL REFERENCES kb_file(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_source_ref_faq ON faq_source_ref(faq_id);

-- ========== 初始化数据 ==========

INSERT INTO sys_role(code, name) VALUES
    ('ADMIN',     '管理员'),
    ('REVIEWER',  '审核人'),
    ('SUBMITTER', '提交人');

INSERT INTO faq_category(name, parent_id, sort_order) VALUES
    ('产品规格', NULL, 1),
    ('安装维修', NULL, 2),
    ('故障排查', NULL, 3),
    ('售后政策', NULL, 4),
    ('操作说明', NULL, 5),
    ('其他',     NULL, 9);
```

---

## 四、API 设计

### 统一响应体

```json
{ "code": 0, "message": "ok", "data": {} }
```

错误码约定：`0` 成功，`400xx` 业务错误，`401` 未登录，`403` 无权限，`500` 系统错误。

### 4.1 Auth

```
GET  /api/auth/wecom/url
响应: { "redirectUrl": "https://open.weixin.qq.com/..." }

POST /api/auth/wecom/callback
请求: { "code": "xxx", "state": "xxx" }
响应: { "token": "eyJ...", "user": { "id", "name", "avatar", "roles" } }

GET  /api/auth/me
响应: { "id", "name", "avatar", "mobile", "roles", "department" }
```

### 4.2 文件

```
POST /api/files/upload
请求: multipart/form-data, field: file
响应: { "id", "originalName", "fileType", "fileSize", "parseStatus", "createdAt" }

GET  /api/files?page=0&size=20
响应: { "total", "items": [ FileVO... ] }

GET  /api/files/{id}
响应: FileVO + 最新 task 状态

POST /api/files/{id}/generate-faq
响应: { "taskId" }
```

### 4.3 任务状态轮询

```
GET  /api/tasks/{id}/status
响应: { "id", "status", "progress", "errorMsg" }
```

### 4.4 FAQ 候选

```
GET  /api/faq-candidates?status=PENDING&fileId=&page=0&size=20
响应: { "total", "pendingCount", "items": [ CandidateVO... ] }

GET  /api/faq-candidates/{id}
响应: CandidateVO (含 sourceChunk 原文)

POST /api/faq-candidates/{id}/approve
响应: { "faqId" }

POST /api/faq-candidates/{id}/reject
请求: { "reason": "内容有误" }
响应: {}

POST /api/faq-candidates/{id}/edit-approve
请求: { "question": "...", "answer": "..." }
响应: { "faqId" }

POST /api/faq-candidates/{id}/merge
请求: { "targetFaqId": 123 }
响应: {}
```

### 4.5 正式 FAQ

```
GET  /api/faqs?keyword=&categoryId=&page=0&size=20
响应: { "total", "items": [ FaqVO... ] }

GET  /api/faqs/{id}
响应: FaqVO (含 sourceRefs)
```

---

## 五、核心模块实现要点

### 5.1 Sa-Token 权限配置

角色注解直接用在 Controller 方法上：
```java
@SaCheckRole("REVIEWER")
@PostMapping("/{id}/approve")
```

JWT 模式：Sa-Token 签发 token，存 Redis，每次请求通过 `StpUtil.checkLogin()` 校验。

### 5.2 异步任务设计

```java
// AsyncConfig.java — 线程池
@Bean("faqTaskExecutor")
public Executor faqTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("faq-task-");
    return executor;
}

// FaqGenerationService.java
@Async("faqTaskExecutor")
public void generateAsync(Long fileId, Long taskId) { ... }
```

任务状态写 Redis（key: `task:{taskId}`, TTL 1h）同时同步写 `kb_task` 表，前端轮询读 Redis。

### 5.3 文档解析策略

```
DocumentParseService.parse(KbFile file):
  switch file.type:
    pdf  → PdfParser.extract() → 若 text.length < 50 → OcrClient.ocr(file)
    docx → WordParser.extract()  (段落 + 表格转文本)
    xlsx → ExcelParser.extract() (EasyExcel，处理合并单元格)
    txt/csv → PlainTextParser.extract() (juniversalchardet 检测编码)

→ TextCleaner.clean(rawText)
  - 去连续空白行
  - 去页码行（纯数字/罗马数字单行）
  - 合并断行

→ ChunkService.chunk(cleanText)
  - 按双换行切段落
  - 超 800 字强制截断
  - 相邻 chunk 保留 100 字重叠
  - 过滤 < 10 字的 chunk
```

Word 表格转文本格式：
```
[表格]
型号 | 制冷量 | 适用面积
KFR-35GW | 3500W | 15-25㎡
```

### 5.4 LLM Prompt 模板

**DOCUMENT 模式**

```
你是一个专业的知识库整理助手，服务于制造业（空调配件）企业。
以下是从企业内部文档中提取的文本片段：

---
{chunk_content}
---

请基于以上内容提取 0~3 条有价值的 FAQ。

要求：
1. 只能基于原文，不能编造原文中没有的信息
2. question 用真实用户可能提问的方式表达
3. category 从以下选项选择：产品规格、安装维修、故障排查、售后政策、操作说明、其他
4. keywords 为 3~5 个关键词，逗号分隔
5. confidence 为 0.0~1.0 的置信度
6. 如果文本不含有效知识点，返回空数组

严格按以下 JSON 输出，不要输出任何其他内容：
{"faqs":[{"question":"...","answer":"...","category":"...","keywords":"...","source_summary":"...","confidence":0.9}]}
```

**CONVERSATION 模式**（聊天记录）

```
你是一个专业的知识库整理助手，服务于制造业（空调配件）企业。
以下是企业内部工作群的聊天记录片段：

---
{chunk_content}
---

请从对话中识别有价值的问答对，整理为 0~3 条 FAQ。

要求：
1. 只能基于对话中真实出现的问题和解答
2. 过滤闲聊、表情、无实质内容的消息
3. question 还原提问者真实意图，用标准问句表达
4. 如果对话中没有明确问答对，返回空数组

严格按以下 JSON 输出，不要输出任何其他内容：
{"faqs":[{"question":"...","answer":"...","category":"...","keywords":"...","source_summary":"...","confidence":0.85}]}
```

**FaqJsonParser 容错处理：**
- 尝试提取 `{}` 或 `[]` 包裹的 JSON 片段（应对模型输出前缀文字）
- 字段缺失时用默认值填充，不抛异常
- 解析彻底失败时记录日志，跳过该 chunk，不影响其他 chunk

### 5.5 OCR 服务接口

```
POST http://ocr-service:8866/ocr
Content-Type: multipart/form-data
field: file (图片或PDF页面)

响应:
{
  "text": "识别出的纯文本",
  "markdown": "带结构的 Markdown（表格用 | 分隔）"
}
```

`LocalOcrClient.java` 调用此接口，OcrClient 接口定义：
```java
public interface OcrClient {
    OcrResult ocr(byte[] fileBytes, String filename);
}
```

---

## 六、部署配置

### 6.1 docker-compose.yml 结构

```yaml
services:
  backend:
    build: ./sld-faq-backend
    ports: ["8080:8080"]
    env_file: .env
    depends_on: [postgres, redis, minio]

  frontend:
    build: ./sld-faq-frontend
    ports: ["80:80"]
    depends_on: [backend]

  ocr-service:
    build: ./ocr-service
    ports: ["8866:8866"]
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
    profiles: [ocr]       # docker-compose --profile ocr up -d

  postgres:
    image: postgres:15-alpine
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docs/sql/init.sql:/docker-entrypoint-initdb.d/init.sql

  redis:
    image: redis:7-alpine
    volumes: [redis_data:/data]

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    ports: ["9000:9000", "9001:9001"]
    volumes: [minio_data:/data]

volumes:
  postgres_data:
  redis_data:
  minio_data:
```

### 6.2 application.yml 关键配置

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/sld_faq
    username: ${DB_USER:sld}
    password: ${DB_PASS:sld123}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

sa-token:
  token-name: Authorization
  timeout: 86400
  is-concurrent: false

wecom:
  corp-id: ${WECOM_CORP_ID}
  corp-secret: ${WECOM_CORP_SECRET}
  agent-id: ${WECOM_AGENT_ID}
  redirect-uri: ${WECOM_REDIRECT_URI}
  mock-login: ${WECOM_MOCK_LOGIN:false}  # 本地开发开启

minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: sld-faq

llm:
  base-url: ${LLM_BASE_URL}
  api-key: ${LLM_API_KEY}
  model-name: ${LLM_MODEL_NAME}
  timeout: 60s

ocr:
  provider: local
  enabled: true
  local:
    base-url: ${OCR_BASE_URL:http://ocr-service:8866}
    timeout: 30s

async:
  faq-task:
    core-pool-size: 4
    max-pool-size: 8
    queue-capacity: 100
```

### 6.3 本地开发说明

**不带 OCR 启动（日常开发）：**
```bash
docker-compose up -d   # 启动 postgres/redis/minio
# 后端 IDE 直接运行，application-dev.yml 覆盖配置
# 前端 pnpm dev
```

**带 OCR 启动（需要 NVIDIA 驱动 + nvidia-docker）：**
```bash
docker-compose --profile ocr up -d
```

**企业微信 OAuth 本地调试：**
在 `application-dev.yml` 中设置 `wecom.mock-login: true`，调用：
```
POST /api/auth/mock-login
请求: { "userId": "test001", "name": "测试用户", "role": "REVIEWER" }
响应: { "token": "..." }
```
此接口仅在 `mock-login=true` 时注册，生产环境自动禁用。

---

## 七、前端关键实现

### 7.1 路由守卫

```typescript
router.beforeEach((to) => {
  if (to.path === '/login') return true
  const userStore = useUserStore()
  if (!userStore.token) return '/login'
})
```

### 7.2 Axios 拦截器

```typescript
// 请求拦截：注入 token
instance.interceptors.request.use(config => {
  const token = useUserStore().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截：统一错误处理
instance.interceptors.response.use(
  res => res.data.data,
  err => {
    if (err.response?.status === 401) {
      useUserStore().logout()
      router.push('/login')
    }
    return Promise.reject(err)
  }
)
```

### 7.3 任务状态轮询

```typescript
// UploadPage.vue
async function pollTaskStatus(taskId: number) {
  const timer = setInterval(async () => {
    const { status, progress } = await api.getTaskStatus(taskId)
    taskProgress.value = progress
    if (status === 'SUCCESS' || status === 'FAILED') {
      clearInterval(timer)
      // 跳转或提示
    }
  }, 3000)
}
```

---

## 八、扩展预留

以下能力在架构上已预留，后续可增量接入：

| 能力 | 预留方式 |
|------|---------|
| 向量检索 / FAQ 语义搜索 | `kb_chunk` 预留 `embedding VECTOR` 字段，`FaqItem` 预留 `embedding` |
| 多 OCR 厂商切换 | `OcrClient` 接口 + `provider` 配置，加实现类即可 |
| 多 LLM 切换 | `LlmClient` 用 OpenAI 兼容格式，改 `base-url` 即可 |
| 企业微信 JS-SDK | `WeComOAuthService` 已隔离，可在此扩展 JS-SDK ticket 签名 |
| 异步任务升级 | `@Async` 方法体替换为 XXL-Job Handler，接口不变 |
| OCR 能力升级 | 替换 `LocalOcrClient` 实现为 Qwen2-VL 等，接口不变 |
