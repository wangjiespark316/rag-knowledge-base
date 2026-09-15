package com.example.ailargedemo.controller;

import com.example.ailargedemo.entity.ChatHistory;
import com.example.ailargedemo.mapper.ChatHistoryMapper;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @GetMapping("/hello")
    public String sayHello() {

        String question = "你好，请用一句话介绍一下你自己。";
        System.out.println("正在呼叫本地 Qwen 大模型...");

        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("qwen:1.8b")
                .build();

        String answer = model.generate(question);
        System.out.println("大模型回答成功！");

        // 保存聊天记录
        ChatHistory chat = new ChatHistory();
        chat.setQuestion(question);
        chat.setAnswer(answer);

        // 【核心修复】这里使用统一的方法名 insertChat
        chatHistoryMapper.insertChat(chat);

        System.out.println("聊天记录已保存数据库");

        return "🤖 AI 回答：<br><br>" + answer;
    }
}