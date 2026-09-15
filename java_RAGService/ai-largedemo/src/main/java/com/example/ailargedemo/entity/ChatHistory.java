package com.example.ailargedemo.entity;

import java.time.LocalDateTime;

public class ChatHistory {

    private Long id;

    // ⭐ 新增字段（放这里）
    private String sessionId;

    private String question;

    private String answer;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // ⭐ 新增 getter
    public String getSessionId() {
        return sessionId;
    }

    // ⭐ 新增 setter
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}