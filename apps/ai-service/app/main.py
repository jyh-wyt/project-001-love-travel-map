from typing import Dict

from fastapi import FastAPI

from app.features.diary.router import router as diary_router
from app.features.memory.router import router as memory_router
from app.features.plan_day.router import router as plan_day_router

app = FastAPI(title="Love Travel AI Service", version="0.1.0")


@app.get("/internal/health")
def health() -> Dict[str, str]:
    return {"status": "ok", "service": "love-travel-ai-service"}


app.include_router(diary_router, prefix="/internal/ai", tags=["ai"])
app.include_router(memory_router, prefix="/internal/ai", tags=["ai"])
app.include_router(plan_day_router, prefix="/internal/ai", tags=["ai"])
