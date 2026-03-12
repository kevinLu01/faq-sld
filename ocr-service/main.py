"""
OCR 服务 FastAPI 入口
提供 /health 健康检查和 /ocr 识别接口
端口：8866
"""

from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
import tempfile
import os
import traceback

from ocr_engine import ocr_engine

app = FastAPI(title="OCR Service", version="1.0.0")


@app.get("/health")
def health():
    """健康检查接口，供 docker-compose healthcheck 调用"""
    return {"status": "ok"}


@app.post("/ocr")
async def ocr(file: UploadFile = File(...)):
    """
    接收图片或 PDF 页面文件，返回 OCR 识别结果。

    请求：multipart/form-data, field: file（图片或 PDF 页面）

    成功响应：
        {
            "text":     "识别出的纯文本",
            "markdown": "带结构的 Markdown（表格用 | 分隔）",
            "success":  true
        }

    失败响应（HTTP 200 + success=false，由业务层处理）：
        {
            "success":  false,
            "errorMsg": "错误原因",
            "text":     "",
            "markdown": ""
        }
    """
    # 根据原始文件名后缀创建临时文件，保证模型能识别文件类型
    suffix = os.path.splitext(file.filename)[1] if file.filename else ".png"
    tmp_path = None

    try:
        # 1. 将上传内容写入临时文件
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            content = await file.read()
            tmp.write(content)
            tmp_path = tmp.name

        # 2. 调用 OCR 引擎获取纯文本（ocr 模式）
        text = ocr_engine.ocr(tmp_path, "ocr")

        # 3. 调用 OCR 引擎获取带格式 Markdown（format 模式）
        markdown = ocr_engine.ocr(tmp_path, "format")

        # 4. 返回识别结果
        return {"text": text, "markdown": markdown, "success": True}

    except Exception as e:
        # 捕获所有异常，打印堆栈用于排查，但不向上 raise 500
        traceback.print_exc()
        return JSONResponse(
            content={
                "success": False,
                "errorMsg": str(e),
                "text": "",
                "markdown": ""
            },
            # 业务层错误统一返回 HTTP 200，由调用方根据 success 字段判断
            status_code=200
        )

    finally:
        # 5. 无论成功或失败，都清理临时文件
        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8866)
