# 后端代码审查报告

**项目：** 企业微信 FAQ 智能整理助手
**技术栈：** Spring Boot 3.2 + Sa-Token + MyBatis Plus + PostgreSQL + Redis + MinIO
**审查日期：** 2026-03-13
**审查人：** 资深 Java 架构师
**审查范围：** 基础层（配置/基础设施）+ 业务层（核心模块）共 16 个文件

---

## 一、安全性（Security）

---

### [🔴严重] mock-login 接口无环境隔离保护，仅靠配置开关可被误启用

**位置：** `AuthService.java:87` / `SaTokenConfig.java:35`

**问题描述：**
`/api/auth/mock-login` 接口在 `SaTokenConfig` 的白名单中**硬编码排除认证拦截**，任何情况下均可被外部访问。后端虽在 `AuthService.mockLogin()` 中通过 `weComProperties.isMockLogin()` 做了配置开关判断，但防线仅有这一层。

若运维人员在生产环境的 `application.yml` 中误设置 `wecom.mock-login=true`（或部署时忘记覆盖默认值），任何人都可以无需凭据、任意指定 `wecomUserId`、`role` 进行登录，绕过企业微信认证，直接获取任意用户（包括 ADMIN）的 token。

`MockLoginRequest` 本身也没有 `@Validated` 注解，`userId`/`name`/`role` 均未做格式校验。

**风险：**
生产环境权限完全失控；攻击者可以创建 ADMIN 账户并进行任意操作。

**建议修复：**
1. 使用 Spring Profile 或 `@ConditionalOnProperty` 在编译期/启动期隔离该接口，而不是运行期配置判断：
```java
// 方案A：用 Profile 隔离整个 Controller 方法
@PostMapping("/mock-login")
@Profile({"dev", "local"})
public ApiResponse<LoginResponse> mockLogin(...) { ... }
```
2. 或在 `SaTokenConfig` 中同样通过 `@ConditionalOnProperty` 决定是否将该路径加入白名单，避免生产包中白名单永远存在。
3. `MockLoginRequest` 增加 `@Validated` + `@NotBlank` 注解，并限制 role 只能为枚举值。

---

### [🔴严重] WeComOAuthService 中 access_token 每次请求都重新获取，且 corpSecret 出现在 URL 查询参数中

**位置：** `WeComOAuthService.java:110-131`

**问题描述：**
`getAccessToken()` 方法每次 OAuth 回调都调用企业微信接口重新拉取 access_token，而没有缓存。企业微信 access_token 有效期为 7200 秒，且全局唯一——频繁刷新会导致旧 token 立即失效，多实例部署时会互相刷掉彼此的 token，造成并发登录失败。

更严重的是：`corpsecret` 作为 URL QueryParam 拼接到 HTTP GET 请求中（第 112-114 行），这意味着：
- `corpSecret` 会出现在服务器的访问日志（Nginx/LB access log）中；
- 会出现在 HTTP Referer 头中；
- RestTemplate 的 debug 日志也会打印完整 URL。

**风险：**
`corpSecret` 泄露后攻击者可以完全控制企业微信应用；多实例竞争刷 token 导致登录功能间歇性不可用。

**建议修复：**
1. access_token 必须缓存到 Redis，剩余有效期内复用（标准做法）：
```java
private String getAccessToken() {
    String cacheKey = "wecom:access_token:" + weComProperties.getCorpId();
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) return cached;
    // ... 调接口 ...
    // expires_in 通常 7200，保守缓存 7000 秒
    redisTemplate.opsForValue().set(cacheKey, token, 7000, TimeUnit.SECONDS);
    return token;
}
```
2. `corpSecret` 不应出现在 URL，应通过 HTTP Header 或 POST body 传递（但企业微信官方 API 要求 GET+Query 参数，此为其设计限制）。对此，至少确保 RestTemplate 不开启 DEBUG 日志，并在 Nginx 日志中屏蔽该参数。

---

### [🔴严重] 文件上传仅校验后缀名，未校验 MIME type（Magic Number）

**位置：** `FileService.java:59-62`

**问题描述：**
`upload()` 方法的类型校验逻辑如下：
```java
String ext = extractExtension(originalName).toLowerCase();
if (!ALLOWED_EXTENSIONS.contains(ext)) { ... }
```
完全依赖客户端提供的原始文件名后缀。攻击者可以将一个 `.exe` 或含有恶意宏的 `.html` 文件命名为 `malware.pdf` 绕过检查。`file.getContentType()` 同样来自浏览器的 `Content-Type` 头，也可被伪造。

后续 OCR 处理会将文件内容传给本地 FastAPI 服务，恶意文件可能触发 FastAPI 服务或底层解析库的漏洞。

**风险：**
上传任意类型文件；可能触发服务端文件解析漏洞（如 PDF 解析器漏洞、XXE）。

**建议修复：**
使用 Apache Tika 或 `java.nio.file.Files.probeContentType` 读取文件 Magic Number 校验真实类型：
```java
// 读取前 8 字节校验 Magic Number
byte[] header = new byte[8];
file.getInputStream().read(header);
String detectedType = detectMimeType(header);
if (!ALLOWED_MIME_TYPES.contains(detectedType)) {
    throw new BusinessException("文件内容与扩展名不符");
}
```
或引入 Apache Tika：
```java
Tika tika = new Tika();
String detectedType = tika.detect(file.getInputStream());
```

---

### [🟡中等] state 参数未做格式校验，存在 Redis key 注入风险

**位置：** `WeComOAuthService.java:80-87`

**问题描述：**
`validateState(String state)` 直接将外部传入的 `state` 拼接为 Redis key：
```java
String key = STATE_KEY_PREFIX + state;
```
如果 state 未经任何校验，攻击者可以传入包含特殊字符的 state（如 `../../admin:key`），在某些 Redis 版本或配置下可能产生非预期行为。虽然 `getAndDelete` 不会创建键，但恶意构造超长 state 仍可消耗内存。

**风险：**
Redis key 污染；潜在的内存 DoS。

**建议修复：**
校验 state 只能是 32 位十六进制字符（与生成时的格式对应）：
```java
public void validateState(String state) {
    if (state == null || !state.matches("^[a-f0-9]{32}$")) {
        throw new BusinessException(40010, "非法 state 参数");
    }
    // ...
}
```

---

### [🟡中等] LLM API Key 可能出现在异常日志中

**位置：** `LlmClient.java:73-74`

**问题描述：**
当 `restTemplate.postForObject()` 抛出 `HttpClientErrorException`（如 401/403），Spring 的 `RestClientResponseException` 默认会在异常的 `message` 中包含响应体内容。`catch (Exception e)` 中执行了：
```java
throw new BusinessException("LLM 服务调用失败: " + e.getMessage());
```
若 LLM 服务的错误响应体中回显了请求头（某些调试模式的服务会这样做），API Key 可能出现在异常消息中，进而被 `GlobalExceptionHandler` 的 `log.error("系统异常", e)` 打印出来。

另外，`HttpHeaders.setBearerAuth(apiKey)` 中 apiKey 为空时不会抛出异常，会静默发送 `Authorization: Bearer null`。

**风险：**
API Key 泄露到日志系统；空 API Key 发送导致 LLM 调用静默失败。

**建议修复：**
1. 捕获 `HttpStatusCodeException` 时仅记录状态码，不拼接 `e.getMessage()`；
2. 在 `LlmClient` 构造时校验 `apiKey` 非空非 null。

---

### [🟡中等] MinIO 预签名 URL 有效期 1 小时，与业务场景不匹配

**位置：** `MinioStorage.java:100`

**问题描述：**
```java
.expiry(1, TimeUnit.HOURS)
```
所有文件均使用固定 1 小时有效期。对于 FAQ 审核场景，审核员可能打开一个文件列表后放置数小时再操作，此时 URL 已失效，需要重新请求。另一方面，对于某些敏感文件，1 小时是否过长也值得评估。

更大的问题是：前端拿到的 FileVO 中没有 `downloadUrl` 字段，`getPresignedUrl()` 并非在 `upload()` 或 `list()` 时调用，需确认前端是否每次查看都重新请求，还是复用了缓存的 URL。

**风险：**
用户体验降级（URL 失效后文件无法访问）；或 URL 有效期不当导致文件权限管理失当。

**建议修复：**
将有效期配置化（通过 `MinioProperties` 配置），并在 `FileVO` 中明确标注 URL 的到期时间，供前端判断是否需要刷新。

---

### [🔵建议] Sa-Token 认证异常返回 HTTP 200 而非标准 HTTP 401

**位置：** `SaTokenConfig.java:44` / `GlobalExceptionHandler.java:35-38`

**问题描述：**
`SaServletFilter.setError()` 返回的是字符串响应体，HTTP 状态码默认仍是 200。`GlobalExceptionHandler` 处理 `NotLoginException` 时也只返回 `ApiResponse.error(401, ...)` 响应体，HTTP 状态码同样是 200。这使得 HTTP 语义不正确，代理服务器、API 网关、监控系统无法通过 HTTP 状态码识别未认证请求。

**建议修复：**
在 `SaServletFilter.setError()` 中通过 `SaHolder.getResponse().setStatus(401)` 设置 HTTP 状态码；`GlobalExceptionHandler` 中增加 `@ResponseStatus(HttpStatus.UNAUTHORIZED)`。

---

## 二、可靠性（Reliability）

---

### [🔴严重] approve() 无并发锁，两个审核人同时审核同一 candidate 会产生重复 FAQ

**位置：** `FaqReviewService.java:44-49`

**问题描述：**
`approve()` 的核心流程为：
1. `getAndCheckPending(candidateId)` — SELECT，检查 status=PENDING
2. `createFaqItemFromCandidate()` — INSERT faq_item
3. `createSourceRef()` — INSERT faq_source_ref
4. `markApproved()` — UPDATE candidate status=APPROVED

在高并发场景（两个审核人同时操作），步骤 1 均查到 status=PENDING，均通过检查，然后各自执行步骤 2-4，结果是：
- 同一 candidate 产生两条 `faq_item`
- `faq_source_ref` 也会重复
- 最终 candidate 的 `status` 会被其中一个写为 APPROVED（最后写入的胜出）

虽然类上有 `@Transactional`，但两个并发事务的 SELECT 在提交前看不到对方的更新，事务隔离级别无法解决这个 lost update 问题（PostgreSQL 默认 READ COMMITTED）。

**风险：**
FAQ 库中出现大量重复条目；数据质量严重下降；后续去重成本极高。

**建议修复：**
方案一（推荐）：使用 `SELECT ... FOR UPDATE` 悲观锁：
```java
// FaqCandidateMapper 中添加：
@Select("SELECT * FROM faq_candidate WHERE id = #{id} FOR UPDATE")
FaqCandidate selectByIdForUpdate(Long candidateId);

// FaqReviewService.getAndCheckPending 改为：
FaqCandidate candidate = candidateMapper.selectByIdForUpdate(candidateId);
```

方案二：使用乐观锁，在 `faq_candidate` 表增加 `version` 字段，或使用 CAS UPDATE：
```sql
UPDATE faq_candidate SET status='APPROVED' WHERE id=? AND status='PENDING'
```
判断影响行数，若为 0 则说明已被他人处理，抛出业务异常。

---

### [🔴严重] findOrCreate 在高并发下存在 race condition，会触发唯一键冲突异常

**位置：** `UserService.java:44-77`

**问题描述：**
```java
SysUser existing = sysUserMapper.selectOne(...eq(wecomUserId));
if (existing != null) return existing;
// ...
sysUserMapper.insert(user);  // 并发时两个线程都能到达这里
```
若 `wecom_user_id` 列有唯一索引（正常设计应有），两个线程同时判断 `existing == null` 后并发执行 INSERT，其中一个会抛出 `DuplicateKeyException`（未被捕获）。若无唯一索引，则会产生重复用户记录，后续登录可能匹配到不同的用户实体。

虽然企业微信场景下同一用户极少并发首次登录，但 mock-login 场景下可能批量触发。

**风险：**
未处理的 `DuplicateKeyException` 向上冒泡，导致登录请求 500 错误；或产生重复用户数据。

**建议修复：**
捕获 `DuplicateKeyException` 并 fallback 重新查询：
```java
try {
    sysUserMapper.insert(user);
} catch (DuplicateKeyException e) {
    // 并发插入，返回已存在的记录
    return sysUserMapper.selectOne(
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getWecomUserId, wecomUserId)
    );
}
```
同时确保 `sys_user.wecom_user_id` 列有唯一约束（数据库层面兜底）。

---

### [🔴严重] @Async 任务中数据库操作没有事务保护，部分写入后失败无法回滚

**位置：** `FaqGenerationService.java:69-163`

**问题描述：**
`generateAsync()` 方法被 `@Async` 标注，在子线程中执行。其中 `saveChunks()` 方法对 `kb_chunk` 表执行逐条 INSERT（第 172-185 行），`saveCandidates()` 对 `faq_candidate` 表逐条 INSERT。

这些写入操作没有 `@Transactional` 保护，且 `@Async` 方法本身由于通过代理调用，即使加了 `@Transactional` 也可能因自调用问题失效（需验证 Spring 版本行为）。

如果在保存第 50 个 chunk 时异常，前 49 个 chunk 已经持久化，但 `kb_file.parse_status` 和 `kb_file.chunk_count` 可能未更新到 SUCCESS，导致数据库中存在孤儿 chunk 记录。

更关键的是：`failTask()` 只更新 task 状态，不清理已写入的半途 chunk 和 candidate 数据。

**风险：**
重试触发时会产生重复的 chunk 和 candidate 数据；数据库中存在大量脏数据。

**建议修复：**
1. `saveChunks()` 前先删除该 fileId 下所有旧 chunk（幂等设计）：
```java
kbChunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getFileId, fileId));
faqCandidateMapper.delete(new LambdaQueryWrapper<FaqCandidate>().eq(FaqCandidate::getFileId, fileId));
```
2. 或者 `failTask()` 中清理半途数据。
3. 检查 `wecom_user_id` 等关键字段的唯一约束是否存在。

---

### [🟡中等] LlmClient 无重试机制，单次超时即失败

**位置：** `LlmClient.java:61-76`

**问题描述：**
`chat()` 方法对 LLM 接口的调用没有任何重试逻辑。LLM 推理服务（尤其是 Ollama 本地部署）在高负载时可能超时，单次失败后 `generateAsync` 中通过 `catch (Exception e)` 跳过该 chunk（第 148-151 行）。

这意味着即使是瞬时超时，该 chunk 对应的所有 FAQ 候选都会永久丢失，且没有任何告警或记录哪些 chunk 被跳过。

此外，LLM 超时时间（`llmProperties.getTimeout()`）与文本切块大小（MAX_CHUNK_SIZE=800）未联动配置，超时时间如果过短（如默认 30 秒），大段文本可能经常超时。

**风险：**
高负载时大量 chunk 被静默跳过，生成的 FAQ 候选不完整，且无法得知覆盖率。

**建议修复：**
1. 引入 Spring Retry 或简单手写重试（指数退避，最多 3 次）；
2. 在 `catch` 中记录失败的 `chunkId` 列表，任务完成后写入 task 的 errorMsg；
3. 将 chunk 大小与 LLM 超时时间在配置中关联说明。

---

### [🟡中等] MinIO 上传成功但数据库 INSERT 失败后，MinIO 文件成为孤儿

**位置：** `FileService.java:65-88`

**问题描述：**
`upload()` 方法有 `@Transactional` 注解，但 MinIO 上传（第 66-76 行）发生在事务内，MinIO 不是事务资源。流程为：

```
[事务开始] → MinIO.upload() → kbFileMapper.insert() → [事务提交/回滚]
```

如果 `kbFileMapper.insert()` 失败导致事务回滚，MinIO 中已上传的文件不会被删除，成为无法被引用的孤儿文件，占用存储空间，且没有任何清理机制。

**风险：**
MinIO 存储空间持续增长；无法对账文件。

**建议修复：**
1. MinIO 上传移到事务之外，先上传，再开启事务写库，若写库失败则删除 MinIO 文件：
```java
// 事务外上传
String minioPath = minioStorage.upload(...);
try {
    saveToDb(minioPath, ...);  // @Transactional
} catch (Exception e) {
    minioStorage.delete(minioPath);  // 回滚 MinIO
    throw e;
}
```
2. 或建立定期清理任务，扫描 MinIO 中无对应 `kb_file` 记录的孤儿文件。

---

### [🟡中等] FaqGenerationService.generateAsync 被重复触发时无幂等保护

**位置：** `FileService.java:130-148` / `FaqGenerationService.java:69`

**问题描述：**
`triggerGenerateFaq()` 没有检查文件是否已有 RUNNING 或 SUCCESS 状态的任务，每次调用都会创建新的 `KbTask` 并触发新的异步任务。

若用户因前端无响应而多次点击"生成 FAQ"按钮，或通过接口重放，同一文件会同时存在多个并行的 `generateAsync` 任务，产生大量重复的 chunk 和 candidate 数据。

**风险：**
重复数据污染审核队列；LLM 资源浪费；系统负载异常升高。

**建议修复：**
在 `triggerGenerateFaq()` 中检查是否存在 PENDING/RUNNING 状态任务：
```java
KbTask running = kbTaskMapper.selectOne(
    new LambdaQueryWrapper<KbTask>()
        .eq(KbTask::getFileId, fileId)
        .in(KbTask::getStatus, "PENDING", "RUNNING")
);
if (running != null) {
    throw new BusinessException("文件已有进行中的任务，请勿重复提交");
}
```

---

### [🟡中等] wecomCallback 中 @Transactional 与外部 HTTP 调用混用，事务持有时间过长

**位置：** `AuthService.java:46-73`

**问题描述：**
`wecomCallback()` 方法标注了 `@Transactional(rollbackFor = Exception.class)`，但方法体内第 49-52 行调用了：
- `weComOAuthService.validateState(state)` — 操作 Redis
- `weComOAuthService.getUserInfo(code)` — 调用企业微信 HTTP 接口（3 次网络请求）

HTTP 接口调用期间数据库连接一直被占用（Spring 事务默认在方法开始时获取连接），在企业微信响应慢时（如 3-5 秒）会导致连接池耗尽。

**风险：**
数据库连接池耗尽，并发登录时系统整体不可用。

**建议修复：**
将外部调用（Redis + HTTP）提到事务之外，仅对数据库写操作开启事务：
```java
public LoginResponse wecomCallback(String code, String state) {
    weComOAuthService.validateState(state);           // Redis，无需事务
    WeComUserInfo userInfo = weComOAuthService.getUserInfo(code);  // HTTP，无需事务
    return doLogin(userInfo);                         // 仅此方法开 @Transactional
}

@Transactional
private LoginResponse doLogin(WeComUserInfo userInfo) {
    SysUser user = userService.findOrCreate(...);
    StpUtil.login(user.getId());
    // ...
}
```

---

## 三、代码质量（Code Quality）

---

### [🟡中等] FileService.list() 存在 N+1 查询问题

**位置：** `FileService.java:96-112`

**问题描述：**
`list()` 方法在分页查询后，对每条文件记录都单独查询最新任务：
```java
List<FileVO> items = pageResult.getRecords().stream()
    .map(f -> {
        KbTask latestTask = kbTaskMapper.selectLatestByFileId(f.getId());  // N 次查询
        return toFileVO(f, latestTask);
    })
    .collect(Collectors.toList());
```
若 `size=20`，则此处产生 1（分页）+ 20（task 查询）= 21 次 SQL 查询。

**风险：**
页面大小增大时性能指数级下降；高并发下数据库压力过大。

**建议修复：**
批量查询所有 fileId 对应的最新 task，再做内存 join：
```java
List<Long> fileIds = pageResult.getRecords().stream()
    .map(KbFile::getId).collect(Collectors.toList());
Map<Long, KbTask> taskMap = kbTaskMapper.selectLatestByFileIds(fileIds);
// 或直接写一条 SQL：SELECT DISTINCT ON (file_id) ... ORDER BY file_id, created_at DESC
```

---

### [🟡中等] FaqService.toVO() 中存在 N+1 查询，批量 list 接口下尤为严重

**位置：** `FaqService.java:51-53` / `FaqService.java:103-106`

**问题描述：**
`list()` 接口调用 `toVO(item, false)`，其中仍包含：
```java
FaqCategory category = faqCategoryMapper.selectById(item.getCategoryId());  // 每条 FAQ 都查一次分类
```
`size=20` 时产生 1 + 20 次 SQL。`getById()` 中 `withSourceRefs=true` 时，还会对每条 `FaqSourceRef` 各查一次 `KbFile` 和 `KbChunk`（第 118 行和 126 行），若某 FAQ 有 5 个来源，单次详情请求就会产生 1+5+5=11 次查询。

**建议修复：**
分类数据量小，可全量缓存到 Spring Cache（`@Cacheable("faqCategory")`）；`sourceRef` 关联数据通过 JOIN 查询一次性获取。

---

### [🟡中等] FaqGenerationService.saveChunks() 中 rawContent 与 cleanContent 存储相同内容

**位置：** `FaqGenerationService.java:172-186`

**问题描述：**
```java
chunk.setRawContent(chunkContent);   // chunkTexts.get(i)
chunk.setCleanContent(chunkContent);  // 同上
```
`chunkTexts` 来自 `ChunkService.chunk(cleanText)`，即已经是清洗后的内容。`rawContent` 和 `cleanContent` 存储了相同的数据，原始文本（未清洗、未切块的 rawText 原文对应段落）实际上没有被正确保存。

**风险：**
数据语义错误；审核时"来源原文"展示的是清洗后而非原始文本，影响溯源准确性。

**建议修复：**
要么真正区分存储（rawContent 存原始 rawText 对应片段），要么删除冗余字段并更新文档说明。

---

### [🟡中等] FaqGenerationService.estimateTokenCount() 实现逻辑有误

**位置：** `FaqGenerationService.java:256-270`

**问题描述：**
```java
for (char c : text.toCharArray()) {
    if (c > 0x7F) {
        count++;  // 中文字符 +1
    } else {
        count += 1;  // ASCII 也 +1，等价
    }
}
// 英文部分按平均 4 字符/token 简化
return count;
```
注释说"英文约 4 字符 1 token"，但代码中英文字符（ASCII）实际上也是 +1，并没有做 `/4` 的处理。对英文文本会高估 4 倍 token 数，对中英混合文本也会偏差较大。

**风险：**
`tokenCount` 字段存储的是错误数据，若后续有基于 token 数的限额判断，会导致误判。

**建议修复：**
修正英文部分的计数：
```java
int chineseCount = 0, asciiCount = 0;
for (char c : text.toCharArray()) {
    if (c > 0x7F) chineseCount++;
    else asciiCount++;
}
return chineseCount + asciiCount / 4;
```

---

### [🔵建议] FaqReviewService 类级 @Transactional 导致只读方法也持有事务

**位置：** `FaqReviewService.java:26`

**问题描述：**
```java
@Transactional  // 类级别，所有方法默认开启事务
public class FaqReviewService {
```
`resolveCategoryId()` 等私有只读方法也会被纳入事务（如果通过代理调用），不必要地占用事务资源。虽然当前都是写操作方法，习惯上类级 `@Transactional` 容易在后续维护中添加查询方法时引入问题。

**建议修复：**
移除类级注解，在 `approve()`、`reject()`、`editApprove()`、`merge()` 方法上单独标注 `@Transactional(rollbackFor = Exception.class)`。

---

### [🔵建议] 状态字符串（PENDING/RUNNING/SUCCESS 等）硬编码分散在多处

**位置：** `FileService.java:84`、`FaqGenerationService.java:74`、`FaqReviewService.java:59`、`FaqCandidate`等多处

**问题描述：**
`"PENDING"`、`"RUNNING"`、`"SUCCESS"`、`"FAILED"`、`"APPROVED"`、`"REJECTED"`、`"MERGED"` 等状态字符串以魔法字符串形式分散在各个 Service 中，没有集中的枚举或常量类。若需要修改某个状态值，必须全局搜索替换，容易遗漏。

**建议修复：**
创建 `TaskStatus` 和 `CandidateStatus` 枚举类：
```java
public enum TaskStatus { PENDING, RUNNING, SUCCESS, FAILED }
public enum CandidateStatus { PENDING, APPROVED, REJECTED, MERGED }
```

---

### [🔵建议] LocalOcrClient 每次调用都将文件完整加载到内存（byte[]）

**位置：** `FaqGenerationService.java:85-91` / `LocalOcrClient.java:51`

**问题描述：**
```java
byte[] fileBytes;
try (InputStream is = minioStorage.download(kbFile.getMinioPath())) {
    fileBytes = is.readAllBytes();  // 50MB 文件全部加载到堆内存
}
```
配合 `LocalOcrClient.ocr(byte[] fileBytes, ...)` 方法签名，文件字节数组在内存中驻留直到 GC。50MB 文件上限 × 并发任务数（最多 8 个线程），峰值内存消耗可达 400MB 仅用于文件内容。

**风险：**
高并发时 OOM；GC 压力大导致系统卡顿。

**建议修复：**
如果 OCR 服务支持流式传输，应将 `OcrClient` 接口改为接受 `InputStream` 参数；若必须传 `byte[]`，至少在调用完成后显式将引用置 null 并触发 GC hint。

---

## 四、性能隐患（Performance）

---

### [🟡中等] chunk 循环调 LLM 无任何限速/限流保护

**位置：** `FaqGenerationService.java:130-152`

**问题描述：**
```java
for (int i = 0; i < total; i++) {
    // ...
    String llmOutput = llmClient.chat(prompt);  // 无间隔，全速调用
}
```
假设文件切出 100 个 chunk，会连续发出 100 个 LLM 请求，没有任何间隔或并发控制。对于：
- **商业 LLM API**（如 OpenAI）：会触发 Rate Limit（429 Too Many Requests），导致大量 chunk 失败；
- **本地 Ollama**：连续请求会打满 GPU，影响其他用户的实时响应；
- **多文件并发**：多个 `faqTaskExecutor` 线程同时运行时，LLM 请求数 = 线程数 × chunk 速度。

**风险：**
触发 API Rate Limit 导致大量静默失败；Ollama 服务过载崩溃。

**建议修复：**
1. 针对商业 API：使用 `Bucket4j` 或 `RateLimiter` 控制调用频率；
2. 针对本地 Ollama：LLM 本身是顺序执行的，可配置 chunk 间的休眠间隔（即使 100ms 也有帮助）；
3. 或将 chunk 并发度配置到 `LlmProperties` 中，使用 `Semaphore` 控制同时进行的 LLM 请求数。

---

### [🟡中等] viewCount 自增存在并发写问题，且每次 getById 都写库

**位置：** `FaqService.java:69`

**问题描述：**
```java
faqItemMapper.updateById(buildViewCountUpdate(id, item.getViewCount()));
```
先读后写（非原子操作），并发下存在 lost update（A 读到 viewCount=5，B 读到 viewCount=5，A 写 6，B 写 6，最终 6 而非 7）。同时每次查询详情都触发一次 UPDATE，高访问量 FAQ 的详情页会产生大量无意义的 UPDATE 请求。

**建议修复：**
使用数据库原子自增：
```sql
UPDATE faq_item SET view_count = view_count + 1 WHERE id = ?
```
或将浏览量写入 Redis（`INCR faq:view:{id}`），定期批量回刷数据库。

---

### [🔵建议] Redis task key 的 TTL 在每次 updateTask 时被重置为 1 小时

**位置：** `FaqGenerationService.java:241-245`

**问题描述：**
每次调用 `updateTask()` 都会重新 `SET key value EX 3600`，TTL 从任务最后一次更新时间起算而非任务创建时间起算。一个长时间运行的任务（如处理超大文件）的 key 会持续存活，直到任务完成后 1 小时才过期，这是合理的。

但如果任务卡在中间某个进度不动（如 LLM 服务假死），每次 updateTask 重置 TTL 导致 key 永不过期，需依赖数据库中的 task 记录做状态核对。

**建议修复：**
在 `writeTaskToRedis` 中增加绝对过期时间记录，并在任务监控中识别"超时未完成"的任务。

---

## 五、业务逻辑（Business Logic）

---

### [🔴严重] approve 并发重复问题（已在可靠性章节详述）

参见"可靠性"章节 —— `FaqReviewService.approve()` 的并发 race condition 既是可靠性问题也是业务逻辑问题，两个审核人同时通过同一候选会产生重复正式 FAQ。

---

### [🟡中等] editApprove() 会修改 candidate 实体但只更新 status，修改后的 question/answer 不持久化到 candidate

**位置：** `FaqReviewService.java:74-86`

**问题描述：**
```java
candidate.setQuestion(question);   // 修改内存对象
candidate.setAnswer(answer);
Long faqId = createFaqItemFromCandidate(candidate, reviewerId);  // 用修改后的内容创建 faq_item ✓
createSourceRef(faqId, candidate);
markApproved(candidate, reviewerId);  // 只更新 status/reviewerId/reviewedAt
```
`markApproved()` 调用 `candidateMapper.updateById(candidate)`，MyBatis Plus 的 `updateById` 默认会更新所有非 null 字段（取决于 `updateStrategy` 配置）。若配置为 `NOT_NULL`，则 question/answer 也会被更新到 candidate 表；若为 `NOT_EMPTY`，也类似。

但如果 MyBatis Plus 配置为只更新显式 set 的字段（需要使用 `UpdateWrapper`），则 candidate 表中存储的仍是原始问题，而 faq_item 中是修改后的问题，两者不一致，审计追溯时会产生混淆。

**风险：**
candidate 与 faq_item 内容不一致；审核记录与实际发布内容不符。

**建议修复：**
在 `markApproved()` 前显式更新 candidate 的 question/answer 到数据库，或 `candidateMapper.updateById` 前明确使用 `UpdateWrapper` 指定需要更新的字段。

---

### [🟡中等] FaqGenerationService 重复触发时旧 chunk 和 candidate 数据不清理

**位置：** `FaqGenerationService.java:116-117`（已在可靠性章节提及）

**问题描述（补充业务逻辑层面）：**
从业务角度，同一文件多次生成 FAQ 的需求可能是合理的（如更换了更好的模型后希望重新生成）。但当前设计缺乏对"重新生成"场景的明确处理：
- 旧 chunk 不会被删除，新 chunk 又插入一批，导致同一 `fileId` 对应多批 chunk；
- 旧 PENDING 状态的 candidate 不会被清理，审核员看到的候选列表中既有旧批次的也有新批次的，无从区分；
- `kb_file.chunk_count` 会被更新为最新批次的数量，但实际数据库中有多倍的 chunk。

**建议修复：**
在 `generateAsync` 开始时，根据 `fileId` 清理旧的 PENDING candidate 和该 fileId 的全部 chunk，确保每次生成覆盖上一次的结果（或设计版本号机制区分多次生成）。

---

### [🔵建议] merge() 中 targetFaqId 状态校验使用 `!= 1`，未覆盖 status 为负数或其他无效值的情况

**位置：** `FaqReviewService.java:102`

**问题描述：**
```java
if (targetFaq.getStatus() == null || targetFaq.getStatus() != 1) {
    throw new BusinessException("目标 FAQ 未发布，id=" + targetFaqId);
}
```
这里实际上已经覆盖了 status 不为 1 的所有情况，业务逻辑正确。但从代码可读性角度，应引入常量或枚举表达 `status=1` 代表"已发布"的语义（参见代码质量中的魔法数字问题）。

---

### [🔵建议] FaqService.getById() 无权限校验，任意已登录用户可查看所有 FAQ 详情

**位置：** `FaqService.java:62-72`

**问题描述：**
`getById()` 只检查 FAQ 是否存在，未检查 status=1（已发布）。若某 FAQ 被管理员下线（status=0），普通用户仍可通过 ID 直接访问。虽然 `list()` 通过 `searchPage` 只返回 status=1 的数据，但 `getById` 存在越权读取的风险。

**建议修复：**
```java
if (item.getStatus() == null || item.getStatus() != 1) {
    // 对非管理员返回 404，对管理员可查看
    throw new BusinessException("FAQ 不存在或已下线");
}
```

---

## 总结

### 问题统计

| 严重程度 | 数量 |
|---------|------|
| 🔴 严重  | 6    |
| 🟡 中等  | 12   |
| 🔵 建议  | 6    |
| **合计** | **24** |

### 最优先修复的 3 件事

#### 第一优先：审核并发 race condition（FaqReviewService.approve）
**为什么最紧急：** 数据质量一旦被污染，重复 FAQ 难以自动清理，会直接影响用户体验和知识库可信度。实现成本低（加一行 `FOR UPDATE`），但不修复代价极高。

#### 第二优先：mock-login 生产环境安全隔离
**为什么第二紧急：** 这是权限绕过漏洞，一旦运维误配置即可被任意用户利用，且无任何审计痕迹。应在代码层用 `@Profile` 而不是运行时配置开关做隔离，防止人为失误导致安全事故。

#### 第三优先：文件上传 MIME 类型校验 + access_token 缓存
**为什么第三紧急（并列）：**
- 文件上传的 Magic Number 校验可防止类型欺骗攻击，代码量极小但安全价值高；
- access_token 未缓存在多实例部署下会导致登录功能不稳定（互相刷掉 token），且 `corpSecret` 出现在 URL 日志中是信息安全风险，在生产上线前必须解决。
