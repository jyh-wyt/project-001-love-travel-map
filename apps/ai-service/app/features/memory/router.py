from fastapi import APIRouter

from app.core.config import settings
from app.features.memory.embedding_service import EmbeddingService
from app.features.memory.milvus_store import MilvusMemoryStore
from app.features.memory.schemas import MemoryUpsertRequest, MemoryUpsertResponse

router = APIRouter()


@router.post("/memories/upsert", response_model=MemoryUpsertResponse)
def upsert_memories(request: MemoryUpsertRequest) -> MemoryUpsertResponse:
    valid_items = [item for item in request.items if item.content.strip()]
    embeddings = EmbeddingService().embed_texts([item.content for item in valid_items])
    indexed_count = MilvusMemoryStore().upsert_memories(valid_items, embeddings)

    return MemoryUpsertResponse(
        success=True,
        indexedCount=indexed_count,
        skippedCount=len(request.items) - len(valid_items),
        storeEnabled=settings.memory_store_enabled,
        message="memory upsert accepted; Milvus store is disabled" if not settings.memory_store_enabled else "memory upsert accepted",
    )
