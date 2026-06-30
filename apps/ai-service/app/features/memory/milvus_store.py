from typing import List

from app.core.config import settings
from app.features.memory.schemas import MemoryUpsertItem


class MilvusMemoryStore:
    """Milvus adapter shell.

    `memory_store_enabled=false` keeps this as a no-op store so the new internal
    API can be integrated before Milvus is deployed. The real pymilvus client,
    collection creation, vector upsert, delete and search will be added in the
    next step.
    """

    def __init__(self) -> None:
        self.uri = settings.milvus_uri
        self.token = settings.milvus_token
        self.collection = settings.milvus_collection
        self.enabled = settings.memory_store_enabled

    def upsert_memories(self, items: List[MemoryUpsertItem], embeddings: List[List[float]]) -> int:
        if not self.enabled:
            return len(items)
        if len(items) != len(embeddings):
            raise ValueError("memory items and embeddings size mismatch")
        raise NotImplementedError("Milvus upsert is not implemented yet")
