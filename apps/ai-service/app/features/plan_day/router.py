from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.features.plan_day.schemas import PlanDayGenerateRequest
from app.features.plan_day.service import stream_plan_day

router = APIRouter()


@router.post("/plan-day/generate-stream")
def generate_plan_day_stream(request: PlanDayGenerateRequest) -> StreamingResponse:
    return StreamingResponse(
        stream_plan_day(request),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
