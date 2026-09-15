package com.example.ailargedemo.service;

import org.apache.poi.hwpf.HWPFDocument;
import java.io.FileInputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

import com.example.ailargedemo.entity.ChatHistory;
import com.example.ailargedemo.entity.DocumentInfo;
import com.example.ailargedemo.mapper.ChatHistoryMapper;
import com.example.ailargedemo.mapper.DocumentInfoMapper;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RagService {

    @Autowired
    private DocumentInfoMapper documentInfoMapper;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    private final String EMBED_URL = "http://127.0.0.1:8001/embed";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * ⭐ 多会话记忆缓存
     */
    private final Map<String, StringBuilder> memoryCache = new ConcurrentHashMap<>();

    /**
     * Chroma 向量库
     */
    private final ChromaEmbeddingStore chromaStore = ChromaEmbeddingStore.builder()
            .baseUrl("http://localhost:8000")
            .collectionName("rag_real_collection_v1")
            .build();

    /**
     * Ollama 大模型
     */
    private final OllamaChatModel ollamaModel = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("qwen2.5:latest")
            .temperature(0.2)
            .build();

    /**
     * 获取聊天历史
     */
    public List<ChatHistory> getChatHistory() {
        return chatHistoryMapper.getAllHistory();
    }

    /**
     * 获取 embedding
     */
    private Embedding getEmbedding(String text) {

        Map<String, String> request = Map.of("text", text);

        Map response = restTemplate.postForObject(EMBED_URL, request, Map.class);

        List<Number> vectorList = (List<Number>) response.get("embedding");

        float[] floatArray = new float[vectorList.size()];

        for (int i = 0; i < vectorList.size(); i++) {
            floatArray[i] = vectorList.get(i).floatValue();
        }

        return Embedding.from(floatArray);
    }

    /**
     * Query Rewrite
     */
    private String rewriteQuery(String sessionId, String question) {

        StringBuilder chatMemory = memoryCache.get(sessionId);

        if (chatMemory == null || chatMemory.length() == 0) {
            return question;
        }

        String prompt = String.format(
                "根据聊天历史，把用户的新问题补充为完整问题。\n\n聊天历史:\n%s\n\n用户问题:\n%s\n\n完整问题:",
                chatMemory.toString(),
                question
        );

        return ollamaModel.generate(prompt).trim();
    }

    /**
     * 文件上传 + RAG 向量化
     */
    public String uploadAndSavePdf(MultipartFile file) {

        try {

            String uploadDir = System.getProperty("user.dir") + "/rag_uploads/";

            File dir = new File(uploadDir);

            if (!dir.exists()) dir.mkdirs();

            String originalFilename = file.getOriginalFilename();

            String savedFileName = UUID.randomUUID() + "_" + originalFilename;

            String savedFilePath = uploadDir + savedFileName;

            File destFile = new File(savedFilePath);

            file.transferTo(destFile);

            DocumentInfo docInfo = new DocumentInfo();
            docInfo.setFileName(originalFilename);
            docInfo.setFilePath(savedFilePath);

            documentInfoMapper.insertDocument(docInfo);

            String text = "";

            if (originalFilename.endsWith(".pdf")) {

                PDDocument document = PDDocument.load(destFile);
                PDFTextStripper stripper = new PDFTextStripper();
                text = stripper.getText(document);
                document.close();

            } else if (originalFilename.endsWith(".docx")) {

                FileInputStream fis = new FileInputStream(destFile);
                XWPFDocument document = new XWPFDocument(fis);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document);

                text = extractor.getText();

                extractor.close();
                document.close();
                fis.close();

            } else if (originalFilename.endsWith(".doc")) {

                FileInputStream fis = new FileInputStream(destFile);
                HWPFDocument document = new HWPFDocument(fis);

                text = document.getDocumentText();

                document.close();
                fis.close();

            } else {

                return "❌ 仅支持 PDF / DOCX / DOC";

            }

            int chunkSize = 500;

            int count = 0;

            for (int i = 0; i < text.length(); i += chunkSize) {

                int end = Math.min(text.length(), i + chunkSize);

                String chunk = text.substring(i, end);

                Embedding embedding = getEmbedding(chunk);

                chromaStore.add(embedding, TextSegment.from(chunk));

                count++;

            }

            return "✅ 文档解析完成，共生成 " + count + " 个知识块";

        } catch (Exception e) {

            e.printStackTrace();

            return "❌ 上传失败：" + e.getMessage();

        }
    }

    /**
     * ⭐ 核心问答逻辑（多会话版）
     */
    public String askAndSave(String sessionId, String question) {

        System.out.println("\n用户问题: " + question + " [session=" + sessionId + "]");

        // 获取当前会话记忆
        StringBuilder chatMemory =
                memoryCache.computeIfAbsent(sessionId, k -> new StringBuilder());

        // Query rewrite
        String optimizedQuestion = rewriteQuery(sessionId, question);

        Embedding questionEmbedding = getEmbedding(optimizedQuestion);

        List<EmbeddingMatch<TextSegment>> relevantSegments =
                chromaStore.findRelevant(questionEmbedding, 5, 0.7);

        StringBuilder contextBuilder = new StringBuilder();

        for (EmbeddingMatch<TextSegment> match : relevantSegments) {

            String segment = match.embedded().text();

            if (segment != null && segment.length() > 10) {

                contextBuilder.append(segment).append("\n");

            }

        }

        String context = contextBuilder.toString();

        if (context.trim().isEmpty()) {

            return "根据当前企业知识库，没有找到相关信息。";

        }

        String prompt = String.format(
                "你是企业知识库助手。\n\n聊天历史:\n%s\n\n参考知识:\n%s\n\n用户问题:\n%s\n\n回答:",
                chatMemory.toString(),
                context,
                question
        );

        String answer = ollamaModel.generate(prompt);

        // 更新记忆
        chatMemory.append("用户: ").append(question).append("\n");
        chatMemory.append("助手: ").append(answer).append("\n\n");

        if (chatMemory.length() > 2000) {

            chatMemory.delete(0, 1000);

        }

        // 存入数据库
        try {

            ChatHistory chat = new ChatHistory();

            chat.setSessionId(sessionId);
            chat.setQuestion(question);
            chat.setAnswer(answer);

            chatHistoryMapper.insertChat(chat);

        } catch (Exception e) {

            System.out.println("保存聊天记录失败：" + e.getMessage());

        }

        return answer;
    }
}