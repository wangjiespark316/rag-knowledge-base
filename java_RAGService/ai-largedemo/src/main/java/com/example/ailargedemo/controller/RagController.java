package com.example.ailargedemo.controller;

import com.example.ailargedemo.entity.ChatHistory;
import com.example.ailargedemo.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    /**
     * 上传知识库文件
     */
    @PostMapping("/upload")
    public String uploadKnowledge(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return "❌ 文件不能为空";
        }

        return ragService.uploadAndSavePdf(file);
    }

    /**
     * RAG 问答
     */
    @GetMapping("/ask")
    public String askQuestion(
            @RequestParam(defaultValue = "default") String sessionId,
            @RequestParam String question) {

        return ragService.askAndSave(sessionId, question);
    }

    /**
     * 获取聊天历史
     */
    @GetMapping("/history")
    public List<ChatHistory> getHistory() {

        return ragService.getChatHistory();
    }
}