"""
OCR 引擎封装模块
使用 GOT-OCR 2.0 模型（ucaslcl/GOT-OCR2_0）进行图像文字识别
支持纯文本模式（ocr）和带格式 Markdown 模式（format）
"""

import torch
from transformers import AutoTokenizer, AutoModel
import os

# 模型路径：优先从环境变量读取，默认使用 HuggingFace Hub 上的模型 ID
MODEL_PATH = os.getenv("MODEL_PATH", "ucaslcl/GOT-OCR2_0")


class OcrEngine:
    """GOT-OCR 2.0 模型封装，采用懒加载方式避免启动时阻塞"""

    def __init__(self):
        self.tokenizer = None
        self.model = None
        self._loaded = False

    def load(self):
        """加载模型（首次调用时懒加载，后续调用直接返回）"""
        if self._loaded:
            return

        device = "cuda" if torch.cuda.is_available() else "cpu"
        print(f"[OcrEngine] Loading model from '{MODEL_PATH}' on device={device} ...")

        # TRANSFORMERS_OFFLINE=1 环境变量已在 docker-compose 中设置，
        # 确保不发起网络请求，直接使用本地缓存的模型文件
        self.tokenizer = AutoTokenizer.from_pretrained(
            MODEL_PATH,
            trust_remote_code=True,
        )
        self.model = AutoModel.from_pretrained(
            MODEL_PATH,
            # GPU 使用 bfloat16 节省显存；CPU 使用 float32 保证精度
            torch_dtype=torch.bfloat16 if device == "cuda" else torch.float32,
            device_map=device,
            trust_remote_code=True,
        )
        self.model.eval()
        self._loaded = True
        print(f"[OcrEngine] Model loaded successfully.")

    def ocr(self, image_path: str, ocr_type: str = "ocr") -> str:
        """
        对图片或 PDF 页面文件进行 OCR 识别

        参数:
            image_path: 图片/PDF 文件的本地路径
            ocr_type:   "ocr"    → 返回纯文本（默认）
                        "format" → 返回带结构的 Markdown（表格用 | 分隔）

        返回:
            识别出的文本字符串
        """
        self.load()
        result = self.model.chat(self.tokenizer, image_path, ocr_type=ocr_type)
        return result


# 模块级单例，FastAPI 启动时共享同一个模型实例，避免重复加载
ocr_engine = OcrEngine()
