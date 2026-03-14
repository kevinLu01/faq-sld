FROM python:3.11-slim

WORKDIR /app

# 安装系统依赖
# libglib2.0-0 libsm6 libxrender1 libxext6 libgl1 为 OpenCV / PIL 运行所需的共享库
# curl 用于 healthcheck
RUN apt-get update && apt-get install -y \
    libglib2.0-0 \
    libsm6 \
    libxrender1 \
    libxext6 \
    libgl1 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 先安装 PyTorch nightly (cu128) 以支持 RTX 5090 Blackwell (sm_120)
# stable PyTorch 尚不支持 sm_120，必须使用 nightly build
RUN pip install --no-cache-dir \
    torch torchvision --index-url https://download.pytorch.org/whl/nightly/cu128

# 安装其余依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY . .

EXPOSE 8866

# 注意：GPU 支持通过 docker-compose 的 deploy.resources 配置注入，
# Dockerfile 本身使用 CPU 镜像，nvidia-docker 运行时会自动注入 CUDA 库。
CMD ["python", "main.py"]
