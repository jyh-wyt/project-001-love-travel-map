from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    aliyun_dashscope_api_key: str = ""
    qwen_model_name: str = "qwen-plus"
    qwen_embedding_model_name: str = "text-embedding-v4"
    embedding_dimension: int = 1024
    milvus_uri: str = Field(default="http://127.0.0.1:19530", validation_alias="AI_MILVUS_URI")
    milvus_token: str = Field(default="", validation_alias="AI_MILVUS_TOKEN")
    milvus_collection: str = Field(default="love_travel_memory", validation_alias="AI_MILVUS_COLLECTION")
    rag_top_k: int = 5
    memory_store_enabled: bool = False

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
