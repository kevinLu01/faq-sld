# 前端代码审查报告

**项目：** sld-faq-frontend（Vue 3 + Vant + Pinia）
**审查日期：** 2026-03-13
**审查人：** 资深前端工程师（AI Code Review）
**审查范围：** src/api/request.ts、src/stores/user.ts、src/router/index.ts、src/pages/\*、src/components/ExpandableText.vue、src/api/candidate.ts

---

## 一、安全性

### [严重程度: 🔴严重] JWT Token 存入 localStorage，存在 XSS 窃取风险

**位置：** `src/stores/user.ts:6、17、25`
**问题：**
Token 通过 `localStorage.setItem('token', token)` 持久化，且在 state 初始化时直接从 localStorage 读取。任何能执行 JavaScript 的 XSS 漏洞都可以通过 `localStorage.getItem('token')` 直接窃取 JWT。

**风险：**
攻击者一旦在页面内注入 XSS 代码（如用户输入渲染、第三方脚本污染），即可无声息地把 Token 发往远端，实现账号劫持。JWT 无状态、无法服务端主动吊销，泄露即等同于凭证永久丢失（直至过期）。

**建议：**
最优方案是将 Token 存储在 `httpOnly` + `SameSite=Strict` 的 Cookie 中，由后端负责 Set-Cookie，前端完全无法读取。若业务架构暂时无法改造，至少改用 `sessionStorage`（关闭标签即清除），并在后端缩短 Token 有效期并支持主动吊销（黑名单机制）。

---

### [严重程度: 🟡中等] mock-login 端点在生产环境仍可被调用

**位置：** `src/pages/LoginPage.vue:30、81`、`src/api/auth.ts`（未审查但被调用）
**问题：**
前端通过 `import.meta.env.DEV` 控制 mock 按钮的显示，生产构建中按钮不会渲染。但 `authApi.mockLogin()` 函数本身仍会被打包进 bundle，且对应后端接口 `/api/auth/mock-login`（或类似路径）若未在生产环境禁用，攻击者仍可直接构造请求、绕过企业微信认证以任意身份登录。

**风险：**
前端隐藏 ≠ 接口安全。若后端 mock 接口仅依赖前端不调用作为安全保障，则存在越权访问漏洞，严重时可绕过 OAuth 直接获得管理员权限。

**建议：**
1. 后端 mock 接口必须通过 `spring.profiles.active` 或环境变量，在生产 profile 中不注册该路由（或直接返回 404/403）。
2. 前端可进一步在构建时通过 `vite-plugin-remove` 或条件编译移除 `authApi.mockLogin` 调用，减少 bundle 中的攻击面。

---

### [严重程度: 🟡中等] 401 跳转登录丢失当前页面地址（重定向信息缺失）

**位置：** `src/api/request.ts:38`、`src/router/index.ts:65-69`
**问题：**
响应拦截器中 401 时执行 `router.push('/login')`，路由守卫在未登录时也直接返回 `'/login'`，均未携带 `redirect` 参数。用户在深层页面（如审核详情）登录超时后，重新登录只能回到首页，无法回到中断的页面。

**风险：**
用户体验差，且对于工作流程（如审核员正在填写驳回原因被登出）存在数据丢失风险。

**建议：**
```typescript
// router/index.ts
router.beforeEach((to) => {
  if (to.path === '/login') return true
  const userStore = useUserStore()
  if (!userStore.token) return { path: '/login', query: { redirect: to.fullPath } }
  return true
})

// request.ts 401 处理
router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })

// LoginPage.vue 登录成功后
const redirect = route.query.redirect as string
router.replace(redirect || '/home')
```

---

### [严重程度: 🔵建议] API 请求无 CSRF 防护声明

**位置：** `src/api/request.ts`
**问题：**
当前 Token 通过 `Authorization: Bearer` Header 传输，Bearer 方案本身对 CSRF 有一定防护（浏览器不会自动附加自定义 Header）。但项目未显式在文档或注释中说明这一安全假设，若未来某个接口改为 Cookie 认证，将引入 CSRF 漏洞。

**风险：**
低风险（当前架构下 Bearer Header 方式可对抗 CSRF），但缺少安全意识注释。

**建议：**
在 `request.ts` 中添加注释说明当前认证方案的安全属性，并约定"禁止未来引入 Cookie 认证方式"。

---

## 二、用户体验（移动端）

### [严重程度: 🟡中等] 轮询任务进度组件卸载后 setInterval 未清理（内存泄漏）

**位置：** `src/pages/UploadPage.vue:266-291`
**问题：**
`pollTaskStatus` 函数内部创建了 `setInterval`，timer 引用仅在成功/失败分支中 `clearInterval`，但：
1. 函数返回后 `timer` 变量没有暴露给组件作用域，无法在 `onUnmounted` 中清理。
2. 若用户在任务 RUNNING 期间离开页面（路由跳转），组件销毁后 interval 仍在执行，持续发出 API 请求，并且在回调中操作已销毁的响应式变量（`taskProgress.value = ...`），可能引发 Vue warn 和内存泄漏。

**风险：**
内存泄漏；组件销毁后仍有后台请求消耗服务器资源；在某些场景下可能触发 Vue 响应式警告或不可预期的状态突变。

**建议：**
```typescript
import { ref, onUnmounted } from 'vue'

let pollTimer: ReturnType<typeof setInterval> | null = null

function pollTaskStatus(id: number) {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    // ...现有逻辑
    if (status.status === 'SUCCESS' || status.status === 'FAILED') {
      clearInterval(pollTimer!)
      pollTimer = null
    }
  }, 3000)
}

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
```

---

### [严重程度: 🟡中等] Tabbar 缺少 safe-area-inset-bottom 适配

**位置：** `src/pages/HomePage.vue:86`
**问题：**
Vant 的 `<van-tabbar>` 组件本身支持 `safe-area-inset-bottom` prop（默认开启），但代码中未显式声明。更关键的是底部 spacer `<div style="height: 70px" />` 是硬编码高度，iPhone 等带 Home 指示条设备上实际 Tabbar 高度为 `50px + env(safe-area-inset-bottom)`（约 50+34=84px），导致最后一个列表项被底栏遮挡。

**风险：**
在 iPhone X 及以上机型，列表底部内容被 Tabbar 遮挡，影响可用性。

**建议：**
1. 底部 spacer 改为动态高度：`<div style="height: calc(70px + env(safe-area-inset-bottom))" />`
2. 确认 `<van-tabbar safe-area-inset-bottom>` prop 已启用（Vant 默认已开启，但显式声明更清晰）。

---

### [严重程度: 🟡中等] 首页加载出错时静默失败，无用户反馈

**位置：** `src/pages/HomePage.vue:134、147`
**问题：**
`loadStats` 和 `loadRecentCandidates` 的 catch 块均为 `// ignore`，出错时用户看不到任何错误提示，数据区域保持初始值 0，用户误以为数据正确加载。

**风险：**
网络抖动或服务端异常时，用户无法感知，可能做出错误判断（如认为"待审核"为 0 条）。

**建议：**
catch 块至少展示一个 Toast 提示，或在 stats 区域展示错误状态。对于首页这种聚合页，可以接受单个数据块降级显示（展示"--"或错误图标），但不能完全静默。

---

### [严重程度: 🔵建议] ExpandableText 折叠逻辑依赖字符数而非实际行数

**位置：** `src/components/ExpandableText.vue:30`
**问题：**
`needsCollapse` 通过 `props.text.length > props.maxLength` 判断，但 CSS 折叠用的是 `-webkit-line-clamp: 5`。两者逻辑不一致：
- 200 字的短段落可能占 6 行（中英文混合、短行宽），此时 `needsCollapse` 为 false，不显示"展开"按钮，但内容实际上已被 `-webkit-line-clamp` 裁剪（如果 collapsed class 被触发）。
- 反之，200 字但全是长句可能只有 2 行，却显示"展开全文"按钮点击后无变化。

**风险：**
UI 逻辑不一致，在某些内容下可能出现"展开全文"按钮无意义地出现，或内容被截断但无"展开"入口。

**建议：**
改用 `ResizeObserver` 或在 `onMounted`/`onUpdated` 中通过 `el.scrollHeight > el.clientHeight` 判断是否真正溢出，代替基于字符数的启发式判断。或统一仅保留一种折叠策略（纯 CSS line-clamp，不依赖 `maxLength`）。

---

### [严重程度: 🔵建议] 审核列表在审核后不自动刷新

**位置：** `src/pages/review/ReviewDetailPage.vue:212-221`、`src/components/CandidateListPanel.vue`
**问题：**
用户在 ReviewDetailPage 执行通过/驳回操作后，直接 `router.back()` 回到 ReviewListPage，但列表数据不会重新加载（CandidateListPanel 的数据已缓存在内存中，router 返回不触发 onMounted）。

**风险：**
用户已操作的条目仍显示"待审核"状态，直到手动下拉刷新，体验欠佳，且可能重复操作。

**建议：**
使用 Vue Router 的路由守卫或 `activated` 生命周期（需配合 `<keep-alive>`），或通过 Pinia 状态 / emit 事件通知列表页刷新。最简单方案是在 ReviewListPage 中监听路由 `activated`：

```typescript
// ReviewListPage.vue - 使用 keep-alive 时
onActivated(() => {
  refreshTrigger.value++
})
```

---

## 三、代码质量

### [严重程度: 🟡中等] getStars 函数在两个文件中重复定义

**位置：** `src/components/CandidateListPanel.vue:101-105`、`src/pages/review/ReviewDetailPage.vue:205-209`
**问题：**
完全相同的 `getStars(confidence)` 函数逻辑在两处各自定义了一份，如果星级计算规则变化，需要同步修改两处。

**风险：**
代码维护成本增加，容易出现不一致。

**建议：**
将 `getStars` 提取到 `src/utils/format.ts` 中统一导出，两处均从工具函数导入。

---

### [严重程度: 🟡中等] 进度条使用假进度模拟，存在用户误导和计时器泄漏

**位置：** `src/pages/UploadPage.vue:211-213`
**问题：**
文件上传时用 `setInterval` 每 200ms 增加 10% 进度，这是一个纯粹的假进度条。问题有两个：
1. 如果上传耗时超过 1800ms（进度到 90%），进度条就卡在 90% 不动，体验反而更差。
2. 若上传快速成功或失败，`progressTimer` 仍在运行（虽然最终会被 `clearInterval`，但若 `doUpload` 抛出异常在 `clearInterval` 之前，timer 会泄漏——当前代码 `clearInterval` 在 try 块内，若 `fileApi.upload` reject，catch 中未清理）。

**风险：**
`catch` 块未调用 `clearInterval(progressTimer)`，导致上传失败后进度条继续增长，直到下次操作或组件卸载，且 uploadProgress 可能超过 90 继续增长（但受限于 `< 90` 条件，实际会停在 90，此处风险可控但逻辑不严谨）。

**建议：**
```typescript
let progressTimer: ReturnType<typeof setInterval> | null = null
// ...
try {
  progressTimer = setInterval(...)
  const res = await fileApi.upload(formData)
  clearInterval(progressTimer)
  // ...
} catch {
  if (progressTimer) clearInterval(progressTimer)
  showToast('上传失败，请重试')
} finally {
  uploading.value = false
}
```
或改用 axios 的 `onUploadProgress` 回调获取真实进度。

---

### [严重程度: 🟡中等] 路由守卫未区分 meta.requiresAuth，全局判断过于粗暴

**位置：** `src/router/index.ts:65-70`
**问题：**
当前守卫逻辑是：非 `/login` 路径 + 无 token → 重定向登录。这意味着若未来添加公开页面（如 FAQ 搜索、分享链接），需要改动守卫逻辑，而不是仅在路由 meta 中标记。现有路由已有 `meta: { requiresAuth: true }` 字段，但守卫代码完全忽略了它。

**风险：**
扩展性差；`meta.requiresAuth` 声明与实际逻辑不一致，造成代码阅读误导。

**建议：**
```typescript
router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true
  const userStore = useUserStore()
  if (!userStore.token) return { path: '/login', query: { redirect: to.fullPath } }
  return true
})
```

---

### [严重程度: 🟡中等] REVIEWED tab 在 CandidateListPanel 中映射为 APPROVED，但 tab 也应显示 REJECTED 条目

**位置：** `src/components/CandidateListPanel.vue:59-62`
**问题：**
`getQueryStatus()` 将 `REVIEWED` 映射为 `APPROVED`，意味着"已审核" tab 只显示已通过的条目，已驳回（REJECTED）的条目被遗漏。从业务逻辑看，"已审核"应该包含 APPROVED 和 REJECTED 两种状态。

**风险：**
业务逻辑不完整；审核员无法在"已审核"列表中查看自己驳回过的条目，数据不一致。

**建议：**
与后端确认接口是否支持多状态查询（如 `status=APPROVED,REJECTED`），或在父组件 ReviewListPage 中将 "已审核" tab 拆分为 "已通过" 和 "已驳回" 两个 tab。

---

### [严重程度: 🔵建议] Pinia store 中 userInfo 读取无法持久化，刷新后丢失

**位置：** `src/stores/user.ts:7`
**问题：**
`token` 从 localStorage 恢复，但 `userInfo` 初始值为 `null`。页面刷新后，虽然 token 存在（`isLoggedIn` 为 true），但 `userInfo` 为 null，所有依赖 `userStore.userInfo?.name`、`userStore.userInfo?.roles` 的地方都会显示默认值或逻辑判断错误（如 `isReviewer` 返回 false）。

**风险：**
刷新后用户名显示为"用户"，审核员权限判断失效，可能导致审核入口不显示。

**建议：**
方案一：将 `userInfo` 也序列化存入 localStorage（注意敏感字段）。
方案二：在应用启动时（`App.vue` 的 `onMounted`），若有 token 则调用 `/api/me` 接口刷新用户信息。
方案三：使用 `pinia-plugin-persistedstate` 插件统一处理 store 持久化。

---

### [严重程度: 🔵建议] FaqVO.status 使用 number 类型，缺乏类型语义

**位置：** `src/types/index.ts:58`
**问题：**
`FaqVO.status` 定义为 `number`，而 `CandidateVO.status` 是有语义的联合字符串类型 `'PENDING' | 'APPROVED' | 'REJECTED' | 'MERGED'`。数字状态码可读性极差，且无法在 TypeScript 层面提供拼写保护。

**风险：**
开发时需要查文档才知道 0/1/2 各自的含义，增加认知负担，容易出现状态判断错误。

**建议：**
```typescript
export type FaqStatus = 0 | 1 | 2  // 搭配注释说明各值含义
// 或更佳：
export type FaqStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
```

---

## 四、性能

### [严重程度: 🔵建议] 路由已全部使用懒加载，但 CandidateListPanel 未考虑长列表虚拟滚动

**位置：** `src/components/CandidateListPanel.vue`
**问题：**
路由均使用 `() => import(...)` 动态导入（正确）。但候选列表使用 `van-list` 无限加载，每次加载 20 条追加到 DOM，当总数达到几百甚至上千条时，DOM 节点数量线性增长，滚动性能下降。`list.value = [...list.value, ...res.items]` 在每次加载后都创建新数组，对内存有轻微压力。

**风险：**
候选列表条目多时页面滚动卡顿，尤其在中低端 Android 设备上。

**建议：**
对于企业内部工具且候选总量通常不超过几百条，当前方案可接受。若需要进一步优化，可引入 `vue-virtual-scroller` 或 Vant 的虚拟列表方案，或限制单次查询范围（按任务/文件筛选）。

---

### [严重程度: 🔵建议] 默认头像使用 CDN 外部链接，存在可用性风险

**位置：** `src/pages/HomePage.vue:113`
**问题：**
```typescript
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'
```
依赖 jsDelivr CDN，若该 CDN 不可访问（网络限制、CDN 故障），头像显示为裂图。

**风险：**
企业内部应用在某些网络环境下可能无法访问境外 CDN，导致头像区域异常。

**建议：**
将默认头像图片下载到 `src/assets/` 目录，使用本地相对路径引用。

---

### [严重程度: 🔵建议] HomePage 两次独立调用 candidateApi.list 获取相同数据

**位置：** `src/pages/HomePage.vue:129-151`
**问题：**
`loadStats` 请求 `{ status: 'PENDING', page: 0, size: 1 }` 获取 total 数量，`loadRecentCandidates` 请求 `{ status: 'PENDING', page: 0, size: 5 }` 获取列表。两次请求可以合并：size=5 的请求已经包含了 `res.total`，不需要单独发一次 size=1 的请求。

**风险：**
首页挂载时发出 2 个相同接口的请求（虽然 size 不同），浪费网络资源。

**建议：**
```typescript
async function loadHomeData() {
  statsLoading.value = true
  recentLoading.value = true
  try {
    const res = await candidateApi.list({ status: 'PENDING', page: 0, size: 5 })
    pendingCount.value = res.total
    recentCandidates.value = res.items
  } catch {
    showToast('数据加载失败')
  } finally {
    statsLoading.value = false
    recentLoading.value = false
  }
}
```

---

## 五、业务逻辑

### [严重程度: 🟡中等] handleRejectConfirm / handleEditConfirm 在 van-dialog 的 before-close 回调中直接执行异步操作存在潜在问题

**位置：** `src/pages/review/ReviewDetailPage.vue:224-271`
**问题：**
Vant Dialog 的 `before-close` 回调接收到的 `action` 参数在 Vant 4.x 中类型为 `string`，函数签名 `(action: string)` 正确。但两个 handler 中：
1. 返回 `Promise<boolean>` 时，Vant 会等待 Promise resolve 再关闭对话框（文档有说明），这部分逻辑正确。
2. 但 `handleRejectConfirm` 在 API 成功后先执行 `router.back()`，再 `return true`（让 Dialog 关闭），而实际上 Dialog 关闭和路由跳转会同时触发，可能引发 transition 动画冲突或组件卸载时的警告。

**风险：**
偶发路由跳转与 Dialog 关闭动画冲突，在低端设备上可能出现界面闪烁。

**建议：**
API 成功后，直接 `showRejectDialog.value = false` 关闭对话框，然后延迟一个 tick 再跳转：
```typescript
showRejectDialog.value = false
await nextTick()
router.back()
return true
```

---

### [严重程度: 🔵建议] 轮询任务在成功后使用 setTimeout 延迟跳转，但未在 onUnmounted 中清理

**位置：** `src/pages/UploadPage.vue:278-280`
**问题：**
```typescript
setTimeout(() => {
  router.push('/review/list')
}, 1500)
```
若用户在 1500ms 内手动跳转离开（如点击返回），组件已卸载，setTimeout 回调仍会执行 `router.push`，引发不必要的路由跳转。

**风险：**
用户操作被静默覆盖，体验差；在某些场景下可能导致路由循环。

**建议：**
```typescript
const jumpTimer = setTimeout(...)
onUnmounted(() => clearTimeout(jumpTimer))
```
或直接去掉 1500ms 延迟，成功后立即跳转，Toast 提示在跳转后仍然可见。

---

## 六、其他发现

### [严重程度: 🔵建议] van-tabbar 的 activeTab 初始值为硬编码 'home'，无法响应直接导航

**位置：** `src/pages/HomePage.vue:111`
**问题：**
`activeTab` 初始值为 `'home'`，当用户通过直接 URL 进入 `/home` 时正确，但若 HomePage 被用作其他场景（如从消息通知跳转），activeTab 不会跟随实际路由变化。此外，Tabbar 的 tab 切换目前依靠 `@click` 手动 `router.push`，而非通过 Vue Router 路由状态驱动，可能导致浏览器前进/后退时 Tabbar 高亮状态不同步。

**建议：**
使用 `useRoute().name` 计算 activeTab：
```typescript
const activeTab = computed(() => {
  const name = route.name as string
  if (['ReviewList', 'ReviewDetail'].includes(name)) return 'review'
  if (['FaqList', 'FaqDetail'].includes(name)) return 'faq'
  if (name === 'Me') return 'me'
  return 'home'
})
```

---

## 总结

| 严重程度 | 数量 |
|---------|------|
| 🔴 严重 | 1 条 |
| 🟡 中等 | 8 条 |
| 🔵 建议 | 9 条 |

### 最优先修复的 3 件事

**第 1 优先：🔴 Token 存储安全（localStorage → httpOnly Cookie）**
这是唯一的严重级别问题，JWT 存入 localStorage 在有 XSS 漏洞时等同于明文暴露凭证。建议与后端协商改为 Cookie 方案，或至少配合后端缩短 Token 有效期 + 支持主动吊销。

**第 2 优先：🟡 setInterval 未清理导致内存泄漏（UploadPage 轮询）**
`pollTaskStatus` 中的定时器引用未暴露给组件作用域，组件卸载后仍持续轮询，是典型的内存泄漏 + 后台请求问题。修复成本极低（4 行代码），影响却明显。

**第 3 优先：🟡 刷新后 userInfo 为 null 导致权限判断失效**
token 持久化但 userInfo 不持久化，刷新页面后 `isReviewer` getter 返回 false，审核相关功能入口可能全部消失。建议在 App.vue 启动时检查 token 并 fetch `/api/me` 重建用户信息。
