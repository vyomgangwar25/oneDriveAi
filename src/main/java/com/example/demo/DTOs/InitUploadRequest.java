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

   /**
    * Content type of the assembled file.
    *
    * Declared here because the individual chunks are raw byte slices and
    * carry no meaningful type of their own.
    */
   private String contentType;

   /** Full size of the file in bytes, used to verify the assembled result. */
   private Long totalSize;

   /** Size of every chunk except the last one, in bytes. */
   private Integer chunkSize;
}
