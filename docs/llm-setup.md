# 本地大模型部署指南（RTX 5090 / Windows 11）

本指南面向拥有 RTX 5090（32GB VRAM）的 Windows 11 用户，目标是在本地运行支持 OpenAI 兼容接口的大模型，为 FAQ 智能整理助手提供推理服务。

---

## 一、方案选型

| 对比项 | Ollama | vLLM |
|---|---|---|
| 安装难度 | 一键安装 | 需要 Python 环境 |
| 模型管理 | 自动下载/管理 | 手动下载 |
| API 兼容性 | OpenAI 兼容 | OpenAI 兼容 |
| 推理性能 | 中等 | 高（更好的并发） |
| 适合场景 | 新手、开发调试 | 生产、高并发 |
| Windows 支持 | 原生支持 | 需要 WSL2 或 Linux |

**推荐策略：先用 Ollama 快速验证，生产环境再迁移到 vLLM。**

---

## 二、方案一：Ollama（推荐优先尝试）

### 安装

**方式一：winget（推荐）**

```bash
winget install Ollama.Ollama
```

**方式二：直接下载安装包**

前往 [https://ollama.com/download](https://ollama.com/download) 下载 Windows 安装包（OllamaSetup.exe），双击安装。

验证安装成功：

```bash
ollama --version
```

---

### 推荐模型

按 FAQ 生成效果排序：

#### 1. `qwen2.5:14b` — 首选

```bash
ollama pull qwen2.5:14b
```

- **显存占用**：Q4 量化约 9GB
- **适合场景**：中文 FAQ 生成主力模型，中文理解能力强，32GB 显存绰绰有余，可同时运行 OCR 服务

#### 2. `qwen2.5:32b` — 效果最佳

```bash
ollama pull qwen2.5:32b
```

- **显存占用**：Q4 量化约 20GB
- **适合场景**：对 FAQ 质量要求较高时使用，RTX 5090 32GB 刚好能跑 Q4 版本；注意此时无法同时运行 OCR 服务

#### 3. `deepseek-r1:14b` — 复杂文档推理

```bash
ollama pull deepseek-r1:14b
```

- **显存占用**：Q4 量化约 9GB
- **适合场景**：文档结构复杂、逻辑层次多时，推理能力更强，可作为 `qwen2.5:14b` 的备选

#### 4. `qwen2.5:7b` — 开发调试专用

```bash
ollama pull qwen2.5:7b
```

- **显存占用**：Q4 量化约 5GB
- **适合场景**：本地开发调试、快速迭代 Prompt，速度最快但效果略差，不建议用于生产

---

### 启动服务

```bash
# 启动 Ollama 服务（默认监听 localhost:11434）
ollama serve
```

> 安装后 Ollama 通常已作为后台服务自动运行，无需手动执行 `ollama serve`。可在系统托盘图标确认。

测试 OpenAI 兼容接口：

```bash
curl http://localhost:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"qwen2.5:14b\",\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"
```

---

### 配置到项目

在项目根目录 `.env` 文件中添加：

```env
LLM_BASE_URL=http://host.docker.internal:11434/v1
LLM_API_KEY=ollama
LLM_MODEL_NAME=qwen2.5:14b
```

> **`host.docker.internal`** 是 Docker 容器访问宿主机的特殊域名，Windows Docker Desktop 自动支持，无需额外配置。如果在宿主机直接调用（非容器内），将 `host.docker.internal` 替换为 `localhost`。

---

## 三、方案二：vLLM（更高性能）

vLLM 在高并发场景下吞吐量显著优于 Ollama，适合生产部署。**Windows 上需通过 WSL2 运行。**

### 环境要求

- Python **3.10+**
- CUDA **12.4+**（RTX 5090 要求，低版本不支持 Blackwell 架构）
- NVIDIA 驱动 **≥ 570.xx**

确认 CUDA 版本：

```bash
nvcc --version
nvidia-smi
```

---

### 安装

```bash
pip install vllm
```

> 建议在 Python 虚拟环境中安装：
> ```bash
> python -m venv vllm-env
> source vllm-env/bin/activate  # WSL2/Linux
> pip install vllm
> ```

---

### 下载模型

**ModelScope（国内推荐，速度快）：**

```bash
pip install modelscope
modelscope download --model Qwen/Qwen2.5-14B-Instruct
```

**HuggingFace（需要网络或镜像）：**

```bash
pip install huggingface_hub
# 设置镜像加速（可选）
export HF_ENDPOINT=https://hf-mirror.com
huggingface-cli download Qwen/Qwen2.5-14B-Instruct
```

---

### 启动服务

```bash
python -m vllm.entrypoints.openai.api_server \
  --model Qwen/Qwen2.5-14B-Instruct \
  --served-model-name qwen2.5:14b \
  --port 8000 \
  --dtype bfloat16 \
  --gpu-memory-utilization 0.85
```

参数说明：

| 参数 | 说明 |
|---|---|
| `--model` | 模型路径或 HuggingFace 模型 ID |
| `--served-model-name` | API 中使用的模型名称，与 `.env` 中 `LLM_MODEL_NAME` 对应 |
| `--port` | 服务端口，默认 8000 |
| `--dtype bfloat16` | RTX 5090 使用 BF16 精度，显存占用约 28GB，效果最佳 |
| `--gpu-memory-utilization` | 显存利用率上限（0~1），建议 0.85，预留系统开销 |

---

### 配置到项目

```env
LLM_BASE_URL=http://host.docker.internal:8000/v1
LLM_API_KEY=EMPTY
LLM_MODEL_NAME=qwen2.5:14b
```

---

## 四、RTX 5090 显存规划（32GB）

| 组件 | 显存占用 | 备注 |
|---|---|---|
| GOT-OCR 2.0 | ~2GB | OCR 服务 |
| Qwen2.5-7B Q4 | ~5GB | 开发调试用 |
| Qwen2.5-14B Q4 | ~9GB | 推荐生产配置 |
| Qwen2.5-14B BF16 | ~28GB | vLLM 全精度，效果最佳 |
| Qwen2.5-32B Q4 | ~20GB | 效果最强的量化方案 |

**推荐组合：**

- **OCR + 14B 模型**（日常推荐）：GOT-OCR 2.0（2GB）+ Qwen2.5-14B Q4（9GB）= **11GB**，显存非常充裕
- **单独 32B 模型**（高质量优先）：Qwen2.5-32B Q4（20GB），关闭 OCR 服务或分开运行
- **BF16 全精度 14B**（vLLM 生产）：Qwen2.5-14B BF16（28GB），性能最优，不建议同时运行其他 GPU 任务

---

## 五、FAQ 生成效果调优

### 降低 JSON 输出随机性

调用接口时设置低 temperature：

```json
{
  "model": "qwen2.5:14b",
  "temperature": 0.1,
  "messages": [...]
}
```

- **`temperature: 0.1~0.3`**：降低随机性，JSON 结构更稳定，减少格式错误
- **`temperature: 0`**：完全确定性输出，适合严格 JSON schema 场景

### 启用 JSON 模式

如果模型支持，显式指定输出格式：

```json
{
  "model": "qwen2.5:14b",
  "response_format": {"type": "json_object"},
  "temperature": 0.1,
  "messages": [...]
}
```

### Ollama 自定义 Modelfile（预设 System Prompt）

在项目目录创建 `Modelfile`：

```dockerfile
FROM qwen2.5:14b

SYSTEM """
你是一个专业的 FAQ 整理助手。根据用户提供的文档内容，提取关键问题和答案，
严格以 JSON 格式输出，不要输出任何额外文字或解释。
输出格式：{"faqs": [{"question": "...", "answer": "..."}]}
"""

PARAMETER temperature 0.1
PARAMETER num_ctx 8192
```

创建并使用自定义模型：

```bash
ollama create faq-assistant -f Modelfile
ollama run faq-assistant
```

在 `.env` 中将 `LLM_MODEL_NAME` 改为 `faq-assistant`。

---

## 六、常见问题

### RTX 5090 驱动版本要求

RTX 5090 基于 Blackwell 架构，**需要 NVIDIA 驱动 ≥ 570.xx**。

检查当前驱动版本：

```bash
nvidia-smi
```

前往 [NVIDIA 驱动下载](https://www.nvidia.com/Download/index.aspx) 更新至最新版本。

---

### CUDA 版本兼容

- RTX 5090 需要 **CUDA 12.4+** 才能完整支持 Blackwell 架构特性
- vLLM 建议使用 CUDA 12.4 或 12.6
- Ollama 自带 CUDA 运行时，无需手动安装 CUDA Toolkit

---

### Ollama：WSL2 vs 原生 Windows

| 对比项 | 原生 Windows | WSL2 |
|---|---|---|
| 安装复杂度 | 简单（一键安装） | 需要配置 WSL2 + CUDA |
| GPU 支持 | 直接调用 | 通过 WSL2 CUDA 支持 |
| 性能 | 略低 | 接近原生 Linux |
| 推荐场景 | 开发调试 | 对性能有要求时 |

**结论：RTX 5090 日常使用原生 Windows 版 Ollama 即可满足需求。**

---

### 模型下载慢

**方案一：HuggingFace 镜像**

```bash
# 设置镜像（在 WSL2 或命令行中执行）
export HF_ENDPOINT=https://hf-mirror.com
```

Windows 系统环境变量设置（PowerShell）：

```powershell
[System.Environment]::SetEnvironmentVariable("HF_ENDPOINT", "https://hf-mirror.com", "User")
```

**方案二：使用 ModelScope（无需魔法，国内直连）**

```bash
pip install modelscope
modelscope download --model Qwen/Qwen2.5-14B-Instruct
```

**方案三：Ollama 模型库直接下载（最简单）**

Ollama 的模型托管在独立 CDN，通常无需额外配置即可正常下载：

```bash
ollama pull qwen2.5:14b
```
