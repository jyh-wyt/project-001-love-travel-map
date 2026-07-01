from fastapi import APIRouter
from fastapi import HTTPException

from app.core.config import settings
from app.features.memory.embedding_service import EmbeddingService
from app.features.memory.milvus_store import MilvusMemoryStore
from app.features.memory.schemas import (
    MemorySearchRequest,
    MemorySearchResponse,
    MemoryUpsertRequest,
    MemoryUpsertResponse,
)

router = APIRouter()


@router.post("/memories/upsert", response_model=MemoryUpsertResponse)
def upsert_memories(request: MemoryUpsertRequest) -> MemoryUpsertResponse:
    valid_items = [item for item in request.items if item.content.strip()]
    if not valid_items:
        return MemoryUpsertResponse(
            success=True,
            indexedCount=0,
            skippedCount=len(request.items),
            storeEnabled=settings.memory_store_enabled,
            message="memory upsert accepted; no valid content",
        )
    try:
        embeddings = EmbeddingService().embed_texts([item.content for item in valid_items])
        indexed_count = MilvusMemoryStore().upsert_memories(valid_items, embeddings)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc) or "memory upsert failed") from exc

    return MemoryUpsertResponse(
        success=True,
        indexedCount=indexed_count,
        skippedCount=len(request.items) - len(valid_items),
        storeEnabled=settings.memory_store_enabled,
        message="memory upsert accepted; Milvus store is disabled" if not settings.memory_store_enabled else "memory upsert accepted",
    )


@router.post("/memories/search", response_model=MemorySearchResponse)
def search_memories(request: MemorySearchRequest) -> MemorySearchResponse:
    if not request.query.strip():
        return MemorySearchResponse(
            success=True,
            results=[],
            storeEnabled=settings.memory_store_enabled,
            message="memory search accepted; empty query",
        )
    try:
        query_embedding = EmbeddingService().embed_texts([request.query])[0]
        results = MilvusMemoryStore().search_memories(
            space_id=request.space_id,
            query_embedding=query_embedding,
            top_k=request.top_k,
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc) or "memory search failed") from exc

    return MemorySearchResponse(
        success=True,
        results=results,
        storeEnabled=settings.memory_store_enabled,
        message="memory search accepted; Milvus store is disabled" if not settings.memory_store_enabled else "memory search accepted",
    )
