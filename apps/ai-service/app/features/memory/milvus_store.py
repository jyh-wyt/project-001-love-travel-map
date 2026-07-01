from datetime import datetime, timezone
from typing import Any, List, Optional

from app.core.config import settings
from app.features.memory.schemas import MemorySearchResult, MemoryUpsertItem


class MilvusMemoryStore:
    """Milvus adapter for travel memories."""

    def __init__(
        self,
        enabled: Optional[bool] = None,
        client: Any = None,
        collection: str = "",
        dimension: int = 0,
    ) -> None:
        self.uri = settings.milvus_uri
        self.token = settings.milvus_token
        self.collection = collection or settings.milvus_collection
        self.enabled = settings.memory_store_enabled if enabled is None else enabled
        self.dimension = dimension or settings.embedding_dimension
        self._client = client

    def upsert_memories(self, items: List[MemoryUpsertItem], embeddings: List[List[float]]) -> int:
        if not self.enabled:
            return len(items)
        if len(items) != len(embeddings):
            raise ValueError("memory items and embeddings size mismatch")
        if not items:
            return 0

        self._ensure_collection()
        data = [self._to_entity(item, embedding) for item, embedding in zip(items, embeddings)]
        self.client.upsert(collection_name=self.collection, data=data)
        return len(data)

    def search_memories(self, space_id: int, query_embedding: List[float], top_k: int) -> List[MemorySearchResult]:
        if not self.enabled:
            return []
        self._ensure_collection()
        response = self.client.search(
            collection_name=self.collection,
            data=[query_embedding],
            limit=top_k,
            filter=f"space_id == {space_id}",
            output_fields=[
                "memory_id",
                "space_id",
                "user_id",
                "source_type",
                "source_id",
                "city_code",
                "city_name",
                "content",
                "created_at",
            ],
        )
        results = []
        for hit in response[0] if response else []:
            entity = hit.get("entity", {})
            results.append(MemorySearchResult(
                memoryId=entity.get("memory_id", hit.get("id", "")),
                spaceId=entity.get("space_id", space_id),
                userId=entity.get("user_id", 0),
                sourceType=entity.get("source_type", "TRIP_POST"),
                sourceId=entity.get("source_id", 0),
                cityCode=entity.get("city_code", ""),
                cityName=entity.get("city_name", ""),
                content=entity.get("content", ""),
                createdAt=entity.get("created_at", ""),
                score=float(hit.get("distance", 0.0)),
            ))
        return results

    @property
    def client(self) -> Any:
        if self._client is None:
            from pymilvus import MilvusClient

            kwargs = {"uri": self.uri}
            if self.token:
                kwargs["token"] = self.token
            self._client = MilvusClient(**kwargs)
        return self._client

    def _ensure_collection(self) -> None:
        if self.client.has_collection(collection_name=self.collection):
            return
        self.client.create_collection(
            collection_name=self.collection,
            dimension=self.dimension,
            primary_field_name="memory_id",
            vector_field_name="embedding",
            id_type="string",
            max_length=80,
            metric_type="COSINE",
            auto_id=False,
        )

    def _to_entity(self, item: MemoryUpsertItem, embedding: List[float]) -> dict:
        return {
            "memory_id": item.memory_id,
            "space_id": item.space_id,
            "user_id": item.user_id,
            "source_type": item.source_type,
            "source_id": item.source_id,
            "city_code": item.city_code or "",
            "city_name": item.city_name or "",
            "content": item.content,
            "created_at": item.created_at or datetime.now(timezone.utc).isoformat(),
            "embedding": embedding,
        }
