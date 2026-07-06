import json
import unittest

from pydantic import ValidationError

from app.features.plan_day.errors import build_plan_day_error_payload
from app.features.plan_day.schemas import PlanDayDraft


class PlanDayErrorsTest(unittest.TestCase):
    def test_classifies_missing_dashscope_config(self):
        payload = build_plan_day_error_payload(RuntimeError("ALIYUN_DASHSCOPE_API_KEY is not configured"))

        self.assertEqual("CONFIG_MISSING", payload["type"])
        self.assertEqual("CONFIG", payload["stage"])
        self.assertIn("AI 服务配置", payload["message"])

    def test_classifies_json_parse_failure(self):
        error = json.JSONDecodeError("Expecting property name enclosed in double quotes", "{bad", 1)

        payload = build_plan_day_error_payload(error)

        self.assertEqual("OUTPUT_PARSE_FAILED", payload["type"])
        self.assertEqual("PARSE", payload["stage"])
        self.assertIn("JSON", payload["message"])

    def test_classifies_schema_validation_failure(self):
        with self.assertRaises(ValidationError) as context:
            PlanDayDraft(title="缺字段")

        payload = build_plan_day_error_payload(context.exception)

        self.assertEqual("OUTPUT_SCHEMA_INVALID", payload["type"])
        self.assertEqual("VALIDATE", payload["stage"])
        self.assertIn("字段", payload["message"])


if __name__ == "__main__":
    unittest.main()
