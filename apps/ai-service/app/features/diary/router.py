from typing import Optional

from pydantic import BaseModel, Field
from fastapi import APIRouter

from app.features.diary.service import polish_diary

router = APIRouter()


class PolishDiaryRequest(BaseModel):
    request_id: str = Field(alias="requestId")
    user_id: int = Field(alias="userId")
    space_id: int = Field(alias="spaceId")
    post_id: Optional[int] = Field(default=None, alias="postId")
    content: str


class PolishDiaryResponse(BaseModel):
    request_id: str = Field(alias="requestId")
    model_name: str = Field(alias="modelName")
    polished_content: Optional[str] = Field(alias="polishedContent")
    success: bool
    error_message: Optional[str] = Field(default=None, alias="errorMessage")


@router.post("/polish-diary", response_model=PolishDiaryResponse)
def polish_diary_endpoint(request: PolishDiaryRequest) -> PolishDiaryResponse:
    result = polish_diary(request.content)
    return PolishDiaryResponse(
        requestId=request.request_id,
        modelName=result["model_name"],
        polishedContent=result["polished_content"],
        success=result["success"],
        errorMessage=result["error_message"],
    )
