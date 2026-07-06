import unittest

from app.features.plan_day.output_parser import parse_plan_day_draft_content


class PlanDayOutputParserTest(unittest.TestCase):
    def test_extracts_json_object_from_surrounding_text(self):
        content = """
这是规划结果：
{
  "title": "青岛一日游",
  "morning": {"mode": "PLAY", "content": "上午去小麦岛", "places": ["小麦岛"]},
  "afternoon": {"mode": "PLAY", "content": "下午去八大关", "places": ["八大关"]},
  "evening": {"mode": "REST", "content": "晚上回酒店休息", "places": []},
  "recommendations": [{"title": "小麦岛附近", "items": ["咖啡", "拍照点"]}],
  "reminders": ["出行前确认天气"]
}
祝你旅途愉快。
"""

        draft = parse_plan_day_draft_content(content)

        self.assertEqual("青岛一日游", draft.title)
        self.assertEqual(["小麦岛"], draft.morning.places)

    def test_repairs_trailing_commas_before_parsing(self):
        content = """
```json
{
  "title": "青岛一日游",
  "morning": {"mode": "PLAY", "content": "上午去小麦岛", "places": ["小麦岛",]},
  "afternoon": {"mode": "PLAY", "content": "下午去八大关", "places": ["八大关"]},
  "evening": {"mode": "REST", "content": "晚上回酒店休息", "places": []},
  "recommendations": [{"title": "小麦岛附近", "items": ["咖啡", "拍照点",],},],
  "reminders": ["出行前确认天气",],
}
```
"""

        draft = parse_plan_day_draft_content(content)

        self.assertEqual("青岛一日游", draft.title)
        self.assertEqual(["咖啡", "拍照点"], draft.recommendations[0].items)


if __name__ == "__main__":
    unittest.main()
