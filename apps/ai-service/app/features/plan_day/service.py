import json
from typing import Dict, Generator, Iterable, List, Union

import dashscope
from langchain_core.prompts import PromptTemplate

from app.core.config import settings
from app.core.sse import sse_event
from app.features.plan_day.agent_tools import (
    build_memory_retrieval_result,
    build_place_constraint_result,
    build_weather_context_result,
)
from app.features.plan_day.output_parser import parse_plan_day_draft_content
from app.features.plan_day.schemas import PlanDayDraft, PlanDayGenerateRequest


PROMPT = PromptTemplate.from_template(
    """
你是一个中文旅行规划 Agent。请只根据用户提供的地点规划当前这一天，不要新增用户没给过的景点。

目的地：{destination}
日期：{plan_date}
用户备注：{notes}
重新生成模式：{regenerate_mode}
已有草稿：{source_draft}
修改要求：{revision_instruction}

Agent Tool Results:
{tool_results}

规则：
1. place_constraint 是硬约束：只能围绕 data.places 里的地点规划；不得新增用户没有输入过的景点。
2. data.mustVisitPlaces 是必去地点，必须优先安排；mode 为 PLAY 时安排出去玩，mode 为 REST 时安排酒店休息。
3. 如果 place_constraint.data.hotelLocation 不为空，把酒店地点作为出发和返回参考；不要声称你计算了真实地图距离。
4. memory_retrieval 是软参考：只能用来理解用户偏好和历史体验，不要编造工具结果里没有的历史。
5. weather_context 是环境约束：如果天气不可用或超过预报范围，必须在 reminders 里提醒用户出行前确认实时天气。
6. recommendations 只能围绕用户输入地点附近推荐美食、咖啡、拍照点或休息点，不要新增独立景点。
7. 只输出 JSON，不要输出 markdown。
8. 如果重新生成模式为 REVISE，必须优先保留已有草稿结构，只按“修改要求”调整相关部分。

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
"""
)


def stream_plan_day(request: PlanDayGenerateRequest) -> Generator[str, None, None]:
    yield sse_event("progress", {"step": "START", "message": "正在分析当天安排"})
    place_constraint = build_place_constraint_result(request)
    yield _tool_result_event(place_constraint)
    yield sse_event("progress", {"step": "WEATHER", "message": "正在查询天气"})
    weather_context = build_weather_context_result(request)
    yield _tool_result_event(weather_context)
    memory_retrieval = build_memory_retrieval_result(request.travel_memories)

    yield sse_event("progress", {"step": "PLACE_GROUPING", "message": "正在分析地点组合"})
    yield sse_event("progress", {"step": "GENERATE_PLAN", "message": "正在安排上午、下午和晚上"})

    try:
        draft = None
        for event in _stream_generate_with_qwen(request, [place_constraint, weather_context, memory_retrieval]):
            if isinstance(event, PlanDayDraft):
                draft = event
            else:
                yield event
        if draft is None:
            raise RuntimeError("AI 没有返回计划草稿")
        yield sse_event("draft", draft.to_dict())
    except Exception as exc:
        yield sse_event("error", {"message": str(exc) or "AI 生成失败"})


def _tool_result_event(result: Dict) -> str:
    return sse_event("tool-result", result)


def _stream_generate_with_qwen(request: PlanDayGenerateRequest, tool_results: List[Dict]) -> Generator[Union[str, PlanDayDraft], None, None]:
    if not settings.aliyun_dashscope_api_key:
        raise RuntimeError("ALIYUN_DASHSCOPE_API_KEY is not configured")

    prompt = PROMPT.format(
        destination=request.destination,
        plan_date=request.plan_date or "未设置",
        notes=request.notes or "",
        revision_instruction=request.revision_instruction or "",
        regenerate_mode=request.regenerate_mode,
        source_draft=request.source_draft or "",
        tool_results=json.dumps(tool_results, ensure_ascii=False, indent=2),
    )

    stream = dashscope.Generation.call(
        api_key=settings.aliyun_dashscope_api_key,
        model=settings.qwen_model_name,
        messages=[
            {"role": "system", "content": "你只输出严格 JSON。"},
            {"role": "user", "content": prompt},
        ],
        result_format="message",
        stream=True,
        incremental_output=True,
    )

    content_parts = []
    for chunk in _iter_dashscope_stream(stream):
        if getattr(chunk, "status_code", 200) != 200:
            raise RuntimeError(getattr(chunk, "message", "Qwen model call failed"))
        delta = _extract_message_content(chunk)
        if not delta:
            continue
        content_parts.append(delta)
        yield sse_event("draft-delta", {"text": delta})

    content = "".join(content_parts)
    yield parse_plan_day_draft_content(content)


def _iter_dashscope_stream(stream) -> Iterable:
    if isinstance(stream, (str, bytes)):
        return []
    try:
        return iter(stream)
    except TypeError:
        return iter([stream])


def _extract_message_content(response) -> str:
    try:
        return response.output.choices[0].message.content or ""
    except (AttributeError, IndexError, TypeError):
        return ""

