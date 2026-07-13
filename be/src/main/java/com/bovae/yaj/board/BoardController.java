package com.bovae.yaj.board;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams/{teamId}/board")
@RequiredArgsConstructor
class BoardController {

    private final BoardService boardService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BoardResponse> board(
            @PathVariable UUID teamId,
            @RequestParam(required = false) @Nullable String type,
            @RequestParam(required = false) @Nullable UUID epicId,
            @RequestParam(required = false) @Nullable String q) {
        return ResponseEntity.ok(boardService.board(teamId, type, epicId, q));
    }
}
