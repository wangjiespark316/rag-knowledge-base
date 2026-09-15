from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

app = FastAPI()

model = SentenceTransformer("BAAI/bge-base-zh-v1.5")

class Item(BaseModel):
    text: str

@app.post("/embed")
def embed(item: Item):
    vector = model.encode(item.text).tolist()
    return {"embedding": vector}