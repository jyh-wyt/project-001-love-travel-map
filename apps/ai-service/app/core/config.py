from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    aliyun_dashscope_api_key: str = ""
    qwen_model_name: str = "qwen-plus"
    qwen_embedding_model_name: str = "text-embedding-v4"
    embedding_dimension: int = 1024
    milvus_uri: str = "http://127.0.0.1:19530"
    milvus_token: str = ""
    milvus_collection: str = "love_travel_memory"
    rag_top_k: int = 5
    memory_store_enabled: bool = False

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
