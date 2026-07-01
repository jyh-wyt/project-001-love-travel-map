import unittest

from app.features.memory.milvus_store import MilvusMemoryStore
from app.features.memory.schemas import MemoryUpsertItem


class FakeMilvusClient:
    def __init__(self) -> None:
        self.created = False
        self.upserted_data = []
        self.search_calls = []

    def has_collection(self, collection_name: str) -> bool:
        return self.created

    def create_collection(self, **kwargs) -> None:
        self.created = True
        self.create_kwargs = kwargs

    def upsert(self, collection_name: str, data: list[dict]) -> None:
        self.upserted_data.extend(data)

    def search(self, **kwargs) -> list[list[dict]]:
        self.search_calls.append(kwargs)
        return [[
            {
                "id": "trip_post_1",
                "distance": 0.12,
                "entity": {
                    "memory_id": "trip_post_1",
                    "space_id": 8,
                    "user_id": 4,
                    "source_type": "TRIP_POST",
                    "source_id": 1,
                    "city_code": "370200",
                    "city_name": "Qingdao",
                    "content": "A sea walk",
                    "created_at": "2026-06-19",
                },
            }
        ]]


class MilvusMemoryStoreTest(unittest.TestCase):
    def test_disabled_store_accepts_items_without_client(self) -> None:
        item = MemoryUpsertItem(
            memoryId="trip_post_1",
            spaceId=7,
            userId=3,
            sourceType="TRIP_POST",
            sourceId=1,
            cityCode="370200",
            cityName="Qingdao",
            content="A sea walk",
        )

        store = MilvusMemoryStore(enabled=False)

        self.assertEqual(1, store.upsert_memories([item], [[0.1, 0.2]]))

    def test_enabled_store_upserts_metadata_and_embedding(self) -> None:
        item = MemoryUpsertItem(
            memoryId="plan_day_2",
            spaceId=8,
            userId=4,
            sourceType="PLAN_DAY",
            sourceId=2,
            cityCode="",
            cityName="",
            content="Rest in hotel at night",
        )
        client = FakeMilvusClient()
        store = MilvusMemoryStore(
            enabled=True,
            client=client,
            collection="test_memory",
            dimension=2,
        )

        inserted = store.upsert_memories([item], [[0.3, 0.4]])

        self.assertEqual(1, inserted)
        self.assertTrue(client.created)
        self.assertEqual("test_memory", client.create_kwargs["collection_name"])
        self.assertEqual("memory_id", client.create_kwargs["primary_field_name"])
        self.assertEqual("embedding", client.create_kwargs["vector_field_name"])
        self.assertEqual("plan_day_2", client.upserted_data[0]["memory_id"])
        self.assertEqual(8, client.upserted_data[0]["space_id"])
        self.assertEqual([0.3, 0.4], client.upserted_data[0]["embedding"])

    def test_search_filters_by_space_id(self) -> None:
        client = FakeMilvusClient()
        client.created = True
        store = MilvusMemoryStore(
            enabled=True,
            client=client,
            collection="test_memory",
            dimension=2,
        )

        results = store.search_memories(space_id=8, query_embedding=[0.1, 0.2], top_k=3)

        self.assertEqual(1, len(results))
        self.assertEqual("trip_post_1", results[0].memory_id)
        self.assertEqual(8, results[0].space_id)
        self.assertEqual("space_id == 8", client.search_calls[0]["filter"])
        self.assertEqual(["memory_id", "space_id", "user_id", "source_type", "source_id", "city_code", "city_name", "content", "created_at"], client.search_calls[0]["output_fields"])


if __name__ == "__main__":
    unittest.main()
