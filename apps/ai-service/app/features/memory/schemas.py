from typing import List, Literal, Optional

from pydantic import BaseModel, Field


MemorySourceType = Literal["TRIP_POST", "PLAN_DAY"]


class MemoryUpsertItem(BaseModel):
    memory_id: str = Field(alias="memoryId")
    space_id: int = Field(alias="spaceId")
    user_id: int = Field(alias="userId")
    source_type: MemorySourceType = Field(alias="sourceType")
    source_id: int = Field(alias="sourceId")
    city_code: Optional[str] = Field(default="", alias="cityCode")
    city_name: Optional[str] = Field(default="", alias="cityName")
    content: str
    created_at: Optional[str] = Field(default="", alias="createdAt")

    class Config:
        populate_by_name = True


class MemoryUpsertRequest(BaseModel):
    items: List[MemoryUpsertItem] = Field(default_factory=list)


class MemoryUpsertResponse(BaseModel):
    success: bool
    indexed_count: int = Field(alias="indexedCount")
    skipped_count: int = Field(alias="skippedCount")
    store_enabled: bool = Field(alias="storeEnabled")
    message: str = ""

    class Config:
        populate_by_name = True
