from typing import Dict, List

from app.features.plan_day.schemas import PlanDayGenerateRequest
from app.features.plan_day.tools import get_weather


def build_place_constraint_result(request: PlanDayGenerateRequest) -> Dict:
    must_visit_count = len(request.must_visit_places or [])
    hotel_summary = (
        f"酒店参考点：{request.hotel_location}"
        if request.hotel_location
        else "未填写酒店地点，将只按用户地点做顺路安排"
    )
    return {
        "toolName": "place_constraint",
        "label": "地点约束工具",
        "status": "done",
        "summary": f"已锁定 {len(request.places)} 个用户地点，{must_visit_count} 个必去地点；{hotel_summary}",
        "data": {
            "places": request.places,
            "mustVisitPlaces": request.must_visit_places,
            "hotelLocation": request.hotel_location,
            "morningMode": request.morning_mode,
            "afternoonMode": request.afternoon_mode,
            "eveningMode": request.evening_mode,
        },
    }


def build_weather_context_result(request: PlanDayGenerateRequest) -> Dict:
    weather = get_weather(request.destination, request.plan_date or "")
    return build_weather_tool_result(weather.to_dict())


def build_weather_tool_result(weather: Dict) -> Dict:
    if weather.get("available"):
        summary = (
            f"{weather.get('city') or '目的地'} {weather.get('date') or ''}："
            f"{weather.get('weather') or '天气不明'}，"
            f"{weather.get('temperatureMin')}~{weather.get('temperatureMax')}℃，"
            f"降雨概率 {weather.get('rainProbability')}%"
        )
        status = "done"
    else:
        tips = weather.get("tips") or []
        summary = tips[0] if tips else "天气信息不可用，AI 会按常规出行建议规划。"
        status = "failed" if weather.get("reason") == "WEATHER_SERVICE_ERROR" else "skipped"
    return {
        "toolName": "weather_context",
        "label": "天气上下文工具",
        "status": status,
        "summary": summary,
        "data": weather,
    }


def build_memory_retrieval_result(memories: List[Dict]) -> Dict:
    top_memories = memories[:3]
    if top_memories:
        status = "done"
        summary = f"已提供 {len(top_memories)} 条 RAG Top 记忆作为偏好参考"
    else:
        status = "skipped"
        summary = "没有可用的历史记忆，按本次输入规划"
    return {
        "toolName": "memory_retrieval",
        "label": "历史记忆检索工具",
        "status": status,
        "summary": summary,
        "data": {"topMemories": top_memories},
    }
