import json
from typing import Dict

from pydantic import ValidationError


def build_plan_day_error_payload(exc: Exception) -> Dict[str, str]:
    detail = str(exc) or exc.__class__.__name__

    if "ALIYUN_DASHSCOPE_API_KEY" in detail:
        return _payload(
            "CONFIG_MISSING",
            "CONFIG",
            "AI 服务配置缺失，请联系管理员检查模型 API Key。",
            detail,
        )
    if isinstance(exc, ValidationError):
        return _payload(
            "OUTPUT_SCHEMA_INVALID",
            "VALIDATE",
            "AI 返回的计划字段不完整，请重新生成一次。",
            detail,
        )
    if isinstance(exc, json.JSONDecodeError) or isinstance(exc, ValueError):
        return _payload(
            "OUTPUT_PARSE_FAILED",
            "PARSE",
            "AI 返回内容不是有效 JSON，请重新生成一次。",
            detail,
        )
    if "Qwen model call failed" in detail:
        return _payload(
            "MODEL_CALL_FAILED",
            "MODEL",
            "模型服务调用失败，请稍后重试。",
            detail,
        )
    return _payload(
        "UNKNOWN",
        "UNKNOWN",
        "AI 生成失败，请稍后重试。",
        detail,
    )


def _payload(error_type: str, stage: str, message: str, detail: str) -> Dict[str, str]:
    return {
        "type": error_type,
        "stage": stage,
        "message": message,
        "detail": detail,
    }
