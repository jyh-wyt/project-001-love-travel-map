import json
import re

from app.features.plan_day.schemas import PlanDayDraft


def parse_plan_day_draft_content(content: str) -> PlanDayDraft:
    json_text = _extract_json_text(content)
    parsed = json.loads(_repair_common_json_issues(json_text))
    return PlanDayDraft(**parsed)


def _extract_json_text(content: str) -> str:
    text = content.strip()
    fenced = re.search(r"```(?:json)?\s*(.*?)\s*```", text, re.DOTALL | re.IGNORECASE)
    if fenced:
        text = fenced.group(1).strip()

    start = text.find("{")
    if start < 0:
        raise ValueError("AI output does not contain a JSON object")

    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(text)):
        char = text[index]
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start : index + 1]

    raise ValueError("AI output contains an incomplete JSON object")


def _repair_common_json_issues(json_text: str) -> str:
    return re.sub(r",\s*([}\]])", r"\1", json_text)
