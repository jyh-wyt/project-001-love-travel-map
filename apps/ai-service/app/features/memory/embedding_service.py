from typing import List

import dashscope

from app.core.config import settings


class EmbeddingService:
    """DashScope text embedding adapter for the RAG memory module."""

    def __init__(self, model_name: str = "") -> None:
        self.model_name = model_name or settings.qwen_embedding_model_name
        self.dimension = settings.embedding_dimension

    def embed_texts(self, texts: List[str]) -> List[List[float]]:
        if not texts:
            return []
        if not settings.aliyun_dashscope_api_key:
            raise RuntimeError("ALIYUN_DASHSCOPE_API_KEY is not configured")

        response = dashscope.TextEmbedding.call(
            api_key=settings.aliyun_dashscope_api_key,
            model=self.model_name,
            input=texts,
        )
        if getattr(response, "status_code", 200) != 200:
            raise RuntimeError(getattr(response, "message", "DashScope embedding call failed"))

        embeddings = []
        for item in response.output["embeddings"]:
            vector = item["embedding"]
            if len(vector) != self.dimension:
                raise RuntimeError(f"Embedding dimension mismatch: expected {self.dimension}, got {len(vector)}")
            embeddings.append(vector)
        return embeddings
