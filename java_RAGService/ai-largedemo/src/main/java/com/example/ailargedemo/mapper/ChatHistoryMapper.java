package com.example.ailargedemo.mapper;

import com.example.ailargedemo.entity.ChatHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatHistoryMapper {

    // 存入聊天记录
    @Insert("INSERT INTO chat_history(session_id, question, answer) VALUES(#{sessionId}, #{question}, #{answer})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertChat(ChatHistory chatHistory);

    // 新增：查询所有历史聊天记录，按ID排序保证先后顺序
    @Select("SELECT * FROM chat_history ORDER BY id ASC")
    List<ChatHistory> getAllHistory();
}