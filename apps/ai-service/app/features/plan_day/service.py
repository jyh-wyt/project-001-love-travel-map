import json
import re
from typing import Dict, Generator

import dashscope
from langchain_core.prompts import PromptTemplate

from app.core.config import settings
from app.core.sse import sse_event
from app.features.plan_day.schemas import PlanDayDraft, PlanDayGenerateRequest
from app.features.plan_day.tools import get_weather


PROMPT = PromptTemplate.from_template(
    """
你是一个中文旅行规划 Agent。请只根据用户提供的地点规划当前这一天，不要新增用户没给过的景点。

目的地：{destination}
日期：{plan_date}
用户想去的地点：{places}
必去地点：{must_visit_places}
上午状态：{morning_mode}
下午状态：{afternoon_mode}
晚上状态：{evening_mode}
用户备注：{notes}
重新生成模式：{regenerate_mode}
已有草稿：{source_draft}
天气信息：{weather}

规则：
1. mode 为 PLAY 时安排出去玩，mode 为 REST 时安排酒店休息。
2. 必去地点优先安排。
3. 判断哪些地点适合同一时段顺路游玩，但不要声称你计算了真实地图距离。
4. recommendations 推荐上午、下午、晚上出去玩地点附近的美食、咖啡、网红拍照点或休息点。
5. 如果天气信息提示超过 15 天或天气不可用，必须在 reminders 里提醒用户出行前确认实时天气。
6. 只输出 JSON，不要输出 markdown。

JSON 格式：
{{
  "title": "标题",
  "morning": {{"mode": "PLAY或REST", "content": "安排说明", "places": ["地点"]}},
  "afternoon": {{"mode": "PLAY或REST", "content": "安排说明", "places": ["地点"]}},
  "evening": {{"mode": "PLAY或REST", "content": "安排说明", "places": ["地点"]}},
  "recommendations": [
    {{"title": "某地点附近", "items": ["推荐1", "推荐2", "推荐3"]}}
  ],
  "reminders": ["提醒1"]
}}
Travel memories from this private space: {travel_memories}
"""
)


def stream_plan_day(request: PlanDayGenerateRequest) -> Generator[str, None, None]:
    yield sse_event("progress", {"step": "START", "message": "正在分析当天安排"})
    yield sse_event("progress", {"step": "WEATHER", "message": "正在查询天气"})
    weather = get_weather(request.destination, request.plan_date or "")

    yield sse_event("progress", {"step": "PLACE_GROUPING", "message": "正在分析地点组合"})
    yield sse_event("progress", {"step": "GENERATE_PLAN", "message": "正在安排上午、下午和晚上"})

    try:
        draft = _generate_with_qwen(request, weather.to_dict())
        yield sse_event("draft", draft.to_dict())
    except Exception as exc:
        yield sse_event("error", {"message": str(exc) or "AI 生成失败"})


def _generate_with_qwen(request: PlanDayGenerateRequest, weather: Dict) -> PlanDayDraft:
    if not settings.aliyun_dashscope_api_key:
        raise RuntimeError("ALIYUN_DASHSCOPE_API_KEY is not configured")

    prompt = PROMPT.format(
        destination=request.destination,
        plan_date=request.plan_date or "未设置",
        places=json.dumps(request.places, ensure_ascii=False),
        must_visit_places=json.dumps(request.must_visit_places, ensure_ascii=False),
        morning_mode=request.morning_mode,
        afternoon_mode=request.afternoon_mode,
        evening_mode=request.evening_mode,
        notes=request.notes or "",
        regenerate_mode=request.regenerate_mode,
        source_draft=request.source_draft or "",
        weather=json.dumps(weather, ensure_ascii=False),
        travel_memories=json.dumps(request.travel_memories[:5], ensure_ascii=False),
    )

    response = dashscope.Generation.call(
        api_key=settings.aliyun_dashscope_api_key,
        model=settings.qwen_model_name,
        messages=[
            {"role": "system", "content": "你只输出严格 JSON。"},
            {"role": "user", "content": prompt},
        ],
        result_format="message",
    )
    if getattr(response, "status_code", 200) != 200:
        raise RuntimeError(getattr(response, "message", "Qwen model call failed"))

    content = response.output.choices[0].message.content
    parsed = json.loads(_strip_code_fence(content))
    return PlanDayDraft(**parsed)


def _strip_code_fence(content: str) -> str:
    text = content.strip()
    match = re.search(r"```(?:json)?\s*(.*?)\s*```", text, re.DOTALL)
    if match:
        return match.group(1).strip()
    return text
