from typing import List

from app.core.config import settings


class EmbeddingService:
    """Placeholder embedding service for the RAG memory module.

    The interface is intentionally stable now so Java and router code can be
    wired first. The real DashScope embedding call will be added when Milvus is
    enabled for local and server environments.
    """

    def __init__(self, model_name: str = "") -> None:
        self.model_name = model_name or settings.qwen_embedding_model_name
        self.dimension = settings.embedding_dimension

    def embed_texts(self, texts: List[str]) -> List[List[float]]:
        return [[0.0] * self.dimension for _ in texts]
