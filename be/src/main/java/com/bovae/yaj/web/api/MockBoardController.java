package com.bovae.yaj.web.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mock")
public class MockBoardController {

    @GetMapping("/board")
    public BoardView board() {
        return BoardView.sample();
    }
}
