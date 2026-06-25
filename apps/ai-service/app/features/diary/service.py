from typing import Dict, Optional, Union

from app.core.config import settings


def polish_diary(content: str) -> Dict[str, Union[str, bool, Optional[str]]]:
    if not settings.aliyun_dashscope_api_key:
        return {
            "model_name": settings.qwen_model_name,
            "polished_content": None,
            "success": False,
            "error_message": "ALIYUN_DASHSCOPE_API_KEY is not configured",
        }

    # TODO: Wire LangChain + Alibaba Bailian/Qwen after confirming the exact package API.
    return {
        "model_name": settings.qwen_model_name,
        "polished_content": content,
        "success": True,
        "error_message": None,
    }
