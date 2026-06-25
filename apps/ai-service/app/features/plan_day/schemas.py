from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class PlanDayGenerateRequest(BaseModel):
    request_id: str = Field(alias="requestId")
    space_id: int = Field(alias="spaceId")
    user_id: int = Field(alias="userId")
    plan_day_id: int = Field(alias="planDayId")
    model_name: str = Field(alias="modelName")
    prompt_version: str = Field(alias="promptVersion")
    destination: str
    plan_date: Optional[str] = Field(default="", alias="planDate")
    places: List[str]
    must_visit_places: List[str] = Field(default_factory=list, alias="mustVisitPlaces")
    morning_mode: str = Field(alias="morningMode")
    afternoon_mode: str = Field(alias="afternoonMode")
    evening_mode: str = Field(alias="eveningMode")
    notes: Optional[str] = ""
    regenerate_mode: str = Field(default="NEW", alias="regenerateMode")
    source_draft: Optional[Any] = Field(default=None, alias="sourceDraft")


class WeatherResult(BaseModel):
    available: bool
    reason: Optional[str] = None
    city: Optional[str] = None
    date: Optional[str] = None
    weather: Optional[str] = None
    temperature_min: Optional[float] = Field(default=None, alias="temperatureMin")
    temperature_max: Optional[float] = Field(default=None, alias="temperatureMax")
    rain_probability: Optional[float] = Field(default=None, alias="rainProbability")
    wind_speed: Optional[float] = Field(default=None, alias="windSpeed")
    tips: List[str] = Field(default_factory=list)

    class Config:
        populate_by_name = True

    def to_dict(self) -> Dict:
        return self.model_dump(by_alias=True) if hasattr(self, "model_dump") else self.dict(by_alias=True)


class PeriodPlan(BaseModel):
    mode: str
    content: str
    places: List[str] = Field(default_factory=list)


class Recommendation(BaseModel):
    title: str
    items: List[str]


class PlanDayDraft(BaseModel):
    title: str
    morning: PeriodPlan
    afternoon: PeriodPlan
    evening: PeriodPlan
    recommendations: List[Recommendation]
    reminders: List[str]

    def to_dict(self) -> Dict:
        return self.model_dump() if hasattr(self, "model_dump") else self.dict()
