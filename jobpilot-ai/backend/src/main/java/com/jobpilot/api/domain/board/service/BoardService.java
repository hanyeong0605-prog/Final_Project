package com.jobpilot.api.domain.board.service;
/*
import com.jobpilot.api.domain.board.dto.BoardResponseDto;
import com.jobpilot.api.domain.board.entity.Board;
import com.jobpilot.api.domain.board.repository.BoardRepository;

import java.util.List;

public class BoardService {}
// 다시 수정하기.
/*{

    public List<BoardResponseDto.FeedDto> getBoardsByCategory(Long categoryId) {

        List<Board> boards =
                BoardRepository.findAllByCategoryIdAndIsPublicTrueOrderByCreatedAtDesc(
                        categoryId
                );

        return boards.stream()
                .map(BoardResponseDto.FeedDto::from)
                .toList();
    }
}*/