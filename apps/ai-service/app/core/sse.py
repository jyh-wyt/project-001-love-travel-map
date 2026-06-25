import json
from typing import Dict


def sse_event(event: str, data: Dict) -> str:
    return "event: {0}\ndata: {1}\n\n".format(event, json.dumps(data, ensure_ascii=False))
