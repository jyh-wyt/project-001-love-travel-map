import json
import urllib.parse
import urllib.request
from datetime import date, datetime
from typing import Any, Dict

from app.features.plan_day.schemas import WeatherResult


FORECAST_LIMIT_DAYS = 15


def get_weather(city: str, plan_date: str) -> WeatherResult:
    if not plan_date:
        return WeatherResult(available=False, reason="NO_DATE", tips=["当前 Day 未设置日期，AI 会按常规天气建议规划。"])

    try:
        target = datetime.strptime(plan_date, "%Y-%m-%d").date()
    except ValueError:
        return WeatherResult(available=False, reason="BAD_DATE", tips=["计划日期格式不正确，AI 会按常规天气建议规划。"])

    today = date.today()
    diff_days = (target - today).days
    if diff_days > FORECAST_LIMIT_DAYS:
        return WeatherResult(
            available=False,
            reason="OUT_OF_FORECAST_RANGE",
            city=city,
            date=plan_date,
            tips=["当前计划日期距离今天超过 15 天，天气预报可能不稳定，出行前请再确认实时天气。"],
        )
    if diff_days < 0:
        return WeatherResult(
            available=False,
            reason="PAST_DATE",
            city=city,
            date=plan_date,
            tips=["计划日期早于今天，AI 会按常规天气建议规划。"],
        )

    try:
        geo = _fetch_json(
            "https://geocoding-api.open-meteo.com/v1/search?"
            + urllib.parse.urlencode({"name": city, "count": 1, "language": "zh", "format": "json"})
        )
        results = geo.get("results") or []
        if not results:
            return WeatherResult(available=False, reason="CITY_NOT_FOUND", city=city, date=plan_date, tips=["没有查到这个城市的天气，AI 会按常规天气建议规划。"])

        first = results[0]
        forecast = _fetch_json(
            "https://api.open-meteo.com/v1/forecast?"
            + urllib.parse.urlencode(
                {
                    "latitude": first["latitude"],
                    "longitude": first["longitude"],
                    "daily": "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max",
                    "timezone": "Asia/Shanghai",
                    "start_date": plan_date,
                    "end_date": plan_date,
                }
            )
        )
        daily = forecast.get("daily") or {}
        return WeatherResult(
            available=True,
            city=city,
            date=plan_date,
            weather=_weather_code_name(_first(daily, "weather_code")),
            temperatureMin=_first(daily, "temperature_2m_min"),
            temperatureMax=_first(daily, "temperature_2m_max"),
            rainProbability=_first(daily, "precipitation_probability_max"),
            windSpeed=_first(daily, "wind_speed_10m_max"),
            tips=_weather_tips(_first(daily, "precipitation_probability_max")),
        )
    except Exception:
        return WeatherResult(available=False, reason="WEATHER_SERVICE_ERROR", city=city, date=plan_date, tips=["天气服务暂时不可用，AI 会按常规天气建议规划。"])


def _fetch_json(url: str) -> Dict[str, Any]:
    request = urllib.request.Request(url, headers={"User-Agent": "love-travel-ai-service/0.1"})
    with urllib.request.urlopen(request, timeout=8) as response:
        return json.loads(response.read().decode("utf-8"))


def _first(data: Dict[str, Any], key: str) -> Any:
    value = data.get(key)
    if isinstance(value, list) and value:
        return value[0]
    return None


def _weather_code_name(code: Any) -> str:
    if code in (0,):
        return "晴"
    if code in (1, 2, 3):
        return "多云"
    if code in (45, 48):
        return "有雾"
    if code in (51, 53, 55, 61, 63, 65, 80, 81, 82):
        return "有雨"
    if code in (71, 73, 75, 85, 86):
        return "有雪"
    if code in (95, 96, 99):
        return "雷雨"
    return "天气不明"


def _weather_tips(rain_probability: Any) -> list:
    if isinstance(rain_probability, (int, float)) and rain_probability >= 50:
        return ["降雨概率较高，优先安排室内、咖啡店或酒店休息，户外地点尽量放到雨小的时段。"]
    return ["天气适合正常安排户外游玩，出行前仍建议再看一次实时预报。"]
