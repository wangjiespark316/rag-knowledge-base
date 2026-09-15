<template>
  <div class="layout">
    <div class="sidebar">
      <div class="logo">
        🤖 扣子
      </div>

      <button class="new-chat" @click="newChat">
        + 新对话
      </button>

      <div class="chat-list">
        <div
          v-for="(c, index) in chats"
          :key="index"
          class="chat-item"
          :class="{ active: currentChat === index }"
          @click="switchChat(index)"
        >
          对话 {{ index + 1 }}
        </div>
      </div>
    </div>

    <div class="main">
      <div class="chat-header">
        📚 企业知识库 AI 助手
      </div>

      <div class="chat-area" ref="chatArea">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message"
          :class="msg.role"
        >
          <div class="avatar">
            <span v-if="msg.role === 'user'">👤</span>
            <span v-else>🤖</span>
          </div>
          <div class="bubble" v-html="msg.content"></div>
        </div>

        <div v-if="isLoading" class="message ai">
          <div class="avatar">
            <span>🤖</span>
          </div>
          <div class="bubble loading">AI 正在思考...</div>
        </div>
      </div>

      <div class="input-area">
        <div class="input-wrapper">
          <label class="upload-btn" title="上传文件">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            <input type="file" @change="uploadFile" />
          </label>

          <input
            v-model="input"
            placeholder="输入你的问题..."
            @keyup.enter="sendMessage"
          />

          <button class="send-btn" @click="sendMessage" :disabled="!input.trim()">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"></line>
              <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from "vue"
import axios from "axios"

// 生成唯一 sessionId
const generateId = () =>
  "session_" + Date.now() + "_" + Math.floor(Math.random() * 1000)

// 每个聊天都有 id 和 messages
const chats = ref([
  {
    id: generateId(),
    messages: [{ role: "ai", content: "你好，我是企业知识库助手。" }]
  }
])

const currentChat = ref(0)

// 当前显示的消息
const messages = ref(chats.value[0].messages)

const input = ref("")
const isLoading = ref(false)
const chatArea = ref(null)

function scrollBottom() {
  nextTick(() => {
    if (chatArea.value) {
      chatArea.value.scrollTop = chatArea.value.scrollHeight
    }
  })
}

// 新对话
function newChat() {
  const newSession = {
    id: generateId(),
    messages: [{ role: "ai", content: "你好，我是企业知识库助手。开启了新的对话。" }]
  }

  chats.value.push(newSession)
  currentChat.value = chats.value.length - 1
  messages.value = newSession.messages

  scrollBottom()
}

// 切换对话
function switchChat(i) {
  currentChat.value = i
  messages.value = chats.value[i].messages
  scrollBottom()
}

// ===================== 拉取历史记录 =====================
async function loadHistory() {
  try {
    const res = await axios.get("/api/rag/history")
    const historyData = res.data

    if (historyData && historyData.length > 0) {
      const grouped = {}

      historyData.forEach(item => {
        const sId = item.sessionId || "old_default_session"

        if (!grouped[sId]) {
          grouped[sId] = [
            { role: "ai", content: "你好，我是企业知识库助手。(历史记录)" }
          ]
        }

        grouped[sId].push({
          role: "user",
          content: item.question
        })

        grouped[sId].push({
          role: "ai",
          content: item.answer
        })
      })

      const newChats = []

      for (const [id, msgs] of Object.entries(grouped)) {
        newChats.push({
          id: id,
          messages: msgs
        })
      }

      chats.value = newChats
      currentChat.value = chats.value.length - 1
      messages.value = chats.value[currentChat.value].messages

      scrollBottom()
    }
  } catch (e) {
    console.error("拉取历史失败", e)
  }
}
// =====================================================

// 发送消息
async function sendMessage() {
  const q = input.value.trim()
  if (!q) return

  const sessionId = chats.value[currentChat.value].id

  messages.value.push({
    role: "user",
    content: q
  })

  input.value = ""
  isLoading.value = true
  scrollBottom()

  try {
    const res = await axios.get("/api/rag/ask", {
      params: {
        sessionId: sessionId,
        question: q
      }
    })

    messages.value.push({
      role: "ai",
      content: res.data
    })
  } catch (e) {
    messages.value.push({
      role: "ai",
      content: "<span style='color:red'>请求失败，请检查后端</span>"
    })
  }

  isLoading.value = false
  scrollBottom()
}

// 上传文件
async function uploadFile(e) {
  const file = e.target.files[0]
  if (!file) return

  const form = new FormData()
  form.append("file", file)

  try {
    await axios.post("/api/rag/upload", form)
    alert("✅ 上传成功")
  } catch (e) {
    alert("❌ 上传失败")
  }
}

onMounted(() => {
  loadHistory()
  scrollBottom()
})
</script>

<style scoped>
/* 整体布局 - 使用更柔和的背景色 */
.layout {
  display: flex;
  height: 100vh;
  background-color: #f5f5f7; /* Apple 经典的浅灰背景 */
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

/* 左侧侧边栏 */
.sidebar {
  width: 260px;
  background-color: #ffffff;
  border-right: 1px solid rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.logo {
  padding: 20px 10px;
  font-size: 19px;
  font-weight: 600;
  color: #1d1d1f;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 新对话按钮 */
.new-chat {
  margin: 10px 0;
  padding: 12px;
  border: 1px solid #d2d2d7;
  background: #ffffff;
  color: #1d1d1f;
  cursor: pointer;
  border-radius: 12px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.new-chat:hover {
  background: #f5f5f7;
  border-color: #86868b;
}

.chat-list {
  flex: 1;
  overflow: auto;
  margin-top: 10px;
}

.chat-item {
  padding: 10px 14px;
  margin-bottom: 4px;
  cursor: pointer;
  border-radius: 10px;
  color: #424245;
  font-size: 14px;
  transition: background 0.2s;
}

.chat-item:hover {
  background: rgba(0, 0, 0, 0.05);
}

.chat-item.active {
  background: rgba(0, 0, 0, 0.08);
  color: #000;
  font-weight: 600;
}

/* 右侧主区域 */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  margin: 16px; 
  border-radius: 20px; 
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.chat-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  color: #1d1d1f;
}

.chat-area {
  flex: 1;
  overflow: auto;
  padding: 20px;
  /* 隐藏滚动条让页面看起来更干净(可选) */
  scrollbar-width: thin; 
}

/* 消息气泡优化 */
.message {
  display: flex;
  margin-bottom: 24px;
  align-items: flex-start;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%; /* 变成纯正的圆形 */
  background: #f5f5f7;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0; /* 防止头像在长文本时被挤扁 */
  overflow: hidden; /* 核心：超出圆形范围的图片部分会被隐藏 */
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); /* 加一点极淡的阴影，更有层次感 */
}

/* 针对里面的图片进行填充缩放 */
.avatar span {
  font-size: 18px;
  line-height: 1;
  display: block;
}

.bubble {
  padding: 12px 16px;
  border-radius: 18px;
  max-width: 75%;
  line-height: 1.5;
  font-size: 15px;
}

.message.user {
  flex-direction: row-reverse;
}

.message.user .bubble {
  background: #007aff;
  color: #ffffff;
  border-bottom-right-radius: 4px; 
}

.message.ai .bubble {
  background: #f5f5f7;
  color: #1d1d1f;
  border-bottom-left-radius: 4px;
}

.loading {
  color: #999;
  font-style: italic;
}

/* ================= 胶囊输入区样式 ================= */
.input-area {
  padding: 10px 40px 30px;
  background: transparent;
  display: flex;
  justify-content: center; 
  border-top: none; 
}

.input-wrapper {
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 760px;
  background: #f4f4f5;
  border-radius: 24px;
  padding: 8px 12px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.input-wrapper:focus-within {
  background: #ffffff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border-color: rgba(0, 0, 0, 0.1);
}

.upload-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: #606266;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
  margin-right: 8px;
}

.upload-btn:hover {
  background: #e4e4e7;
  color: #1d1d1f;
}

.upload-btn input {
  display: none;
}

.input-wrapper input {
  flex: 1;
  border: none;
  background: transparent;
  padding: 10px 8px;
  font-size: 15px;
  color: #1d1d1f;
  outline: none;
}

.input-wrapper input::placeholder {
  color: #a1a1aa;
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: #000000; 
  color: white;
  cursor: pointer;
  transition: background 0.2s, transform 0.1s;
}

.send-btn:hover {
  background: #333333;
}

.send-btn:disabled {
  background: #e4e4e7;
  color: #a1a1aa;
  cursor: not-allowed;
}
</style>