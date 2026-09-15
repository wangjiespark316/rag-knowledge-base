package com.example.ailargedemo.mapper;

import com.example.ailargedemo.entity.DocumentInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface DocumentInfoMapper {

    // 注意这里的方法名是 insertDocument
    @Insert("INSERT INTO document_info(file_name, file_path) VALUES(#{fileName}, #{filePath})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDocument(DocumentInfo documentInfo);
}