package com.example.demo.DTOs;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InitUploadRequest {
   private String fileName;
   private Integer totalChunks;
   private String contentType;
   private Long totalSize;
   private Integer chunkSize;
}
