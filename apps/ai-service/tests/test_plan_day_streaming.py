import json
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app.features.plan_day.schemas import PlanDayGenerateRequest, WeatherResult
from app.features.plan_day.service import stream_plan_day


class PlanDayStreamingTest(unittest.TestCase):
    def test_streams_draft_delta_before_final_draft(self):
        content = json.dumps(
            {
                "title": "青岛一日游",
                "morning": {"mode": "PLAY", "content": "上午去小麦岛", "places": ["小麦岛"]},
                "afternoon": {"mode": "PLAY", "content": "下午去八大关", "places": ["八大关"]},
                "evening": {"mode": "REST", "content": "晚上休息", "places": []},
                "recommendations": [{"title": "小麦岛附近", "items": ["咖啡", "拍照点"]}],
                "reminders": ["出行前确认天气"],
            },
            ensure_ascii=False,
        )
        chunks = [content[:20], content[20:]]

        request = PlanDayGenerateRequest(
            requestId="run_1",
            spaceId=1,
            userId=1,
            planDayId=1,
            modelName="qwen-plus",
            promptVersion="test",
            destination="青岛",
            planDate="2026-07-16",
            places=["小麦岛", "八大关"],
            mustVisitPlaces=[],
            morningMode="PLAY",
            afternoonMode="PLAY",
            eveningMode="REST",
        )

        with patch("app.features.plan_day.service.settings.aliyun_dashscope_api_key", "test-key"), patch(
            "app.features.plan_day.agent_tools.get_weather",
            return_value=WeatherResult(available=False, reason="test"),
        ), patch("app.features.plan_day.service.dashscope.Generation.call", return_value=_fake_stream(chunks)):
            events = list(stream_plan_day(request))

        delta_events = [event for event in events if event.startswith("event: draft-delta")]
        draft_events = [event for event in events if event.startswith("event: draft\n")]

        self.assertEqual(2, len(delta_events))
        self.assertEqual(1, len(draft_events))
        self.assertIn("青岛一日游", draft_events[0])

    def test_streams_structured_error_when_output_parse_fails(self):
        request = PlanDayGenerateRequest(
            requestId="run_1",
            spaceId=1,
            userId=1,
            planDayId=1,
            modelName="qwen-plus",
            promptVersion="test",
            destination="青岛",
            planDate="2026-07-16",
            places=["小麦岛", "八大关"],
            mustVisitPlaces=[],
            morningMode="PLAY",
            afternoonMode="PLAY",
            eveningMode="REST",
        )

        with patch("app.features.plan_day.service.settings.aliyun_dashscope_api_key", "test-key"), patch(
            "app.features.plan_day.agent_tools.get_weather",
            return_value=WeatherResult(available=False, reason="test"),
        ), patch("app.features.plan_day.service.dashscope.Generation.call", return_value=_fake_stream(["不是 JSON"])):
            events = list(stream_plan_day(request))

        error_events = [event for event in events if event.startswith("event: error\n")]

        self.assertEqual(1, len(error_events))
        self.assertIn('"type": "OUTPUT_PARSE_FAILED"', error_events[0])
        self.assertIn('"stage": "PARSE"', error_events[0])


def _fake_stream(chunks):
    for chunk in chunks:
        yield SimpleNamespace(
            status_code=200,
            output=SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=chunk))]),
        )


if __name__ == "__main__":
    unittest.main()
