package com.jobpilot.api.domain.board.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class BoardRequestDto {


    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateDto {
        private String title;
        private String content;
        private boolean isPublic;
        private Long BoardId;
        private  Long categoryId; }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CommentDto {
        private String content;
    }
}


