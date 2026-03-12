# vLLM Docker 运维手册

本手册面向需要在生产环境中使用 vLLM Docker 服务的运维人员，涵盖环境准备、日常管理、模型切换和常见故障处理。

---

## 一、前置条件

### 1.1 NVIDIA 驱动

RTX 5090（Blackwell 架构）需要 NVIDIA 驱动 **≥ 570.xx**。

检查当前驱动版本：

```bash
nvidia-smi
```

如版本不满足，前往 [NVIDIA 驱动下载](https://www.nvidia.com/Download/index.aspx) 更新。

### 1.2 CUDA 版本

- 要求 **CUDA 12.4+**
- vLLM 官方镜像已内置 CUDA 运行时，宿主机只需安装驱动即可（不必手动装 CUDA Toolkit）

### 1.3 nvidia-container-toolkit（原 nvidia-docker2）

**Linux（Ubuntu/Debian）安装：**

```bash
# 添加 NVIDIA 容器工具包仓库
curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list | \
  sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' | \
  sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list

sudo apt-get update
sudo apt-get install -y nvidia-container-toolkit

# 配置 Docker 运行时
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker
```

**Windows（WSL2 + Docker Desktop）：**

1. 确保 WSL2 已启用，Windows 驱动版本 ≥ 570.xx
2. 打开 Docker Desktop > Settings > Resources > GPU
3. 开启 **Enable GPU support**，重启 Docker Desktop
4. 在 WSL2 终端中验证（见下节）

---

## 二、验证 GPU 可用

在启动 vLLM 容器之前，先验证 Docker 能正常访问 GPU：

```bash
docker run --rm --gpus all nvidia/cuda:12.4-base-ubuntu22.04 nvidia-smi
```

预期输出包含 GPU 型号（如 `NVIDIA GeForce RTX 5090`）和 VRAM 信息。若报错，请检查驱动版本和 nvidia-container-toolkit 安装状态。

---

## 三、常用管理命令

### 启动 vLLM 服务

```bash
# 仅启动 vLLM（使用 llm profile）
docker-compose --profile llm up -d vllm

# 同时启动 vLLM + OCR
docker-compose --profile llm --profile ocr up -d
```

### 查看日志 / 进度

```bash
# 实时跟踪日志（首次启动看下载和加载进度）
docker logs -f sld-faq-vllm

# 只看最近 100 行
docker logs --tail 100 sld-faq-vllm
```

看到如下输出表示服务就绪：

```
INFO:     Application startup complete.
INFO:     Uvicorn running on http://0.0.0.0:8000
```

### 验证服务状态

```bash
# 健康检查
curl http://localhost:8000/health

# 查看已加载的模型列表
curl http://localhost:8000/v1/models

# 发送测试推理请求
curl http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen2.5:14b","messages":[{"role":"user","content":"你好"}]}'
```

### 停止服务

```bash
# 停止 vLLM 容器（不删除）
docker-compose stop vllm

# 停止并移除容器（模型缓存 vllm_models 卷保留）
docker-compose --profile llm down
```

### 重启服务

```bash
docker-compose restart vllm
```

---

## 四、模型切换

### 4.1 修改环境变量

编辑项目根目录的 `.env` 文件，修改模型相关变量：

```env
# 切换到 32B AWQ 量化版本
VLLM_MODEL=Qwen/Qwen2.5-32B-Instruct-AWQ
VLLM_SERVED_NAME=qwen2.5:32b
VLLM_GPU_UTIL=0.90
VLLM_MAX_LEN=8192
```

### 4.2 重启容器使配置生效

```bash
docker-compose --profile llm up -d vllm
```

Docker Compose 会检测到环境变量变化并重建容器，自动下载新模型（已缓存的模型不会重复下载）。

### 4.3 常用模型参考

| 模型 HuggingFace ID | 精度 | 显存（RTX 5090） | 特点 |
|---|---|---|---|
| `Qwen/Qwen2.5-14B-Instruct` | BF16 | ~28GB | 默认推荐，中文效果最佳 |
| `Qwen/Qwen2.5-32B-Instruct-AWQ` | AWQ int4 | ~20GB | 更大参数量，量化损失小 |
| `Qwen/Qwen2.5-7B-Instruct` | BF16 | ~15GB | 速度快，适合开发调试 |
| `deepseek-ai/DeepSeek-R1-Distill-Qwen-14B` | BF16 | ~28GB | 推理增强，复杂文档效果好 |

---

## 五、显存不足时的降级方案

### 5.1 降低显存利用率上限

在 `.env` 中减小 `VLLM_GPU_UTIL`：

```env
VLLM_GPU_UTIL=0.75
```

### 5.2 缩短最大上下文长度

减小 `VLLM_MAX_LEN` 可显著降低 KV Cache 显存占用：

```env
VLLM_MAX_LEN=4096
```

### 5.3 使用量化模型

在 `docker-compose.yml` 的 vllm 服务 `command` 中添加量化参数：

**AWQ 量化**（需要使用 AWQ 版本模型）：

```yaml
command: >
  --model Qwen/Qwen2.5-14B-Instruct-AWQ
  --served-model-name qwen2.5:14b
  --quantization awq
  --dtype half
  --gpu-memory-utilization 0.85
  --max-model-len 8192
  --port 8000
```

**GPTQ 量化**（需要使用 GPTQ 版本模型）：

```yaml
command: >
  --model Qwen/Qwen2.5-14B-Instruct-GPTQ-Int4
  --served-model-name qwen2.5:14b
  --quantization gptq
  --dtype half
  --gpu-memory-utilization 0.85
  --max-model-len 8192
  --port 8000
```

---

## 六、常见问题

### 容器启动慢，长时间没有响应

**原因**：首次启动正在从 HuggingFace 下载模型文件（Qwen2.5-14B BF16 约 28GB）。

**解决**：

```bash
# 查看下载进度
docker logs -f sld-faq-vllm
```

可以看到类似 `Downloading shards: 100%` 的进度条。国内用户建议在 `.env` 中设置：

```env
HF_ENDPOINT=https://hf-mirror.com
```

下载完成后，后续重启只需约 1~3 分钟加载模型权重。

---

### CUDA out of memory

**错误信息**：`torch.cuda.OutOfMemoryError: CUDA out of memory`

**解决方案（按优先级）**：

1. 降低显存利用率：`.env` 中设置 `VLLM_GPU_UTIL=0.75`
2. 缩短上下文：`.env` 中设置 `VLLM_MAX_LEN=4096`
3. 换 AWQ/GPTQ 量化版本模型（见第五节）
4. 换参数量更小的模型（如 7B 代替 14B）

---

### Windows WSL2 GPU 直通配置

**症状**：`docker run --gpus all` 报错，或 `nvidia-smi` 在容器内不可见。

**排查步骤**：

1. 确认 Windows 宿主机驱动版本 ≥ 570.xx（WSL2 CUDA 直通依赖 Windows 驱动）：

   ```powershell
   # PowerShell 中运行
   nvidia-smi
   ```

2. 确认 WSL2 内核支持 GPU 直通（WSL2 内核版本需 ≥ 5.10.43）：

   ```bash
   # WSL2 终端中运行
   uname -r
   ```

3. 在 Docker Desktop 中确认已启用 GPU：
   - Settings > Resources > GPU > Enable GPU support（需重启 Docker Desktop）

4. 验证 Docker 能看到 GPU：

   ```bash
   docker run --rm --gpus all nvidia/cuda:12.4-base-ubuntu22.04 nvidia-smi
   ```

5. 如果仍然失败，尝试在 WSL2 中手动安装 nvidia-container-toolkit（不依赖 Docker Desktop 的 GPU 集成）：

   ```bash
   # 在 WSL2 Ubuntu 中
   sudo apt-get install -y nvidia-container-toolkit
   sudo nvidia-ctk runtime configure --runtime=docker
   # 重启 Docker 服务
   sudo service docker restart
   ```

---

### 模型缓存占用磁盘空间过大

vLLM 模型默认缓存在 Docker volume `vllm_models` 中（映射到 `/root/.cache/huggingface`）。

查看 volume 占用：

```bash
docker system df -v | grep vllm_models
```

清理不再使用的模型（注意：操作不可逆，下次使用时需重新下载）：

```bash
# 进入容器手动删除
docker run --rm -it -v vllm_models:/cache alpine sh
# 在容器内
ls /cache/hub/
rm -rf /cache/hub/models--Qwen--Qwen2.5-14B-Instruct  # 删除指定模型
exit
```
