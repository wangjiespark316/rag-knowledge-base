import requests
import chromadb
from PyPDF2 import PdfReader

# ================= 配置区 =================
PDF_PATH = "test.pdf"  # 确保你的同级目录下有这个文件
QUESTION = "请根据文档内容，总结一下核心观点？"

EMBED_URL = "http://127.0.0.1:8001/embed"
OLLAMA_URL = "http://127.0.0.1:11434/api/generate"
OLLAMA_MODEL = "qwen:1.8b" 

# ================= 1. 初始化 Chroma =================
print("正在连接 Chroma 数据库...")
chroma_client = chromadb.HttpClient(host='localhost', port=8000)
collection = chroma_client.get_or_create_collection(name="rag_test_collection")

# ================= 优化：检测知识库是否为空 =================
if collection.count() == 0:
    print("检测到知识库为空，开始解析文档并入库...")
    
    # ================= 2. 读取 PDF 并切块 =================
    print(f"正在读取并切分 PDF: {PDF_PATH}...")
    reader = PdfReader(PDF_PATH)
    text = ""
    for page in reader.pages:
        text += page.extract_text()

    chunk_size = 300
    chunks = [text[i:i+chunk_size] for i in range(0, len(text), chunk_size)]
    print(f"文档已切分为 {len(chunks)} 个文本块。")

    # ================= 3. Embedding 并存入 Chroma =================
    print("正在调用 8001 端口进行向量化，并存入 Chroma...")
    for i, chunk in enumerate(chunks):
        
        # 【修复核心 1：将 params 换成 json 请求体】
        response = requests.post(EMBED_URL, json={"text": chunk})
        
        # 增加容错拦截
        if response.status_code != 200:
            print(f"❌ 向量化失败，接口返回: {response.text}")
            continue
            
        res_data = response.json()
        
        # 剥离 JSON 外壳，提取纯数字数组
        if isinstance(res_data, dict) and "embedding" in res_data:
            vector = res_data["embedding"]
        elif isinstance(res_data, list) and isinstance(res_data[0], dict) and "embedding" in res_data[0]:
            vector = res_data[0]["embedding"]
        else:
            vector = res_data
        
        # 存入 Chroma 数据库
        collection.add(
            embeddings=[vector],
            documents=[chunk],
            ids=[f"chunk_{i}"]
        )
    print("🎉 文档知识库构建完成！")
else:
    print(f"✅ 知识库中已有 {collection.count()} 条数据，跳过文档解析和入库，直接提问。")

# ================= 4. 用户提问与向量检索 =================
print(f"\n用户提问: {QUESTION}")
print("正在将问题向量化并在 Chroma 中检索相关内容...")

# 【修复核心 2：同样将提问的 params 换成 json】
query_res = requests.post(EMBED_URL, json={"text": QUESTION})

if query_res.status_code != 200:
    print(f"❌ 问题向量化失败: {query_res.text}")
else:
    query_data = query_res.json()

    if isinstance(query_data, dict) and "embedding" in query_data:
        query_vector = query_data["embedding"]
    elif isinstance(query_data, list) and isinstance(query_data[0], dict) and "embedding" in query_data[0]:
        query_vector = query_data[0]["embedding"]
    else:
        query_vector = query_data

    # 在 Chroma 中检索最相似的 3 个文本块 (Top-K = 3)
    results = collection.query(
        query_embeddings=[query_vector],
        n_results=3
    )
    retrieved_context = "\n".join(results['documents'][0])
    print("\n🔍 检索到的相关上下文如下：")
    print("------------------------")
    print(retrieved_context)
    print("------------------------")

    # ================= 5. 组装 Prompt 并调用 Ollama =================
    print("\n正在调用本地 Qwen 模型生成最终回答...")
    prompt = f"""你是一个智能问答助手。请基于以下提供的参考背景知识，回答用户的问题。如果背景知识中没有相关内容，请回答"我不知道"，不要胡编乱造。

    【背景知识】:
    {retrieved_context}

    【用户问题】:
    {QUESTION}

    【你的回答】:
    """

    ollama_payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False
    }

    ollama_res = requests.post(OLLAMA_URL, json=ollama_payload)
    answer = ollama_res.json().get("response", "")

    print("\n🤖 AI 回答:")
    print(answer)