from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    aliyun_dashscope_api_key: str = ""
    qwen_model_name: str = "qwen-plus"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
