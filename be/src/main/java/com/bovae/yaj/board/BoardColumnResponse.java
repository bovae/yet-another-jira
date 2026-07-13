package com.bovae.yaj.board;

import java.util.List;

public record BoardColumnResponse(String state, List<BoardCardResponse> cards) {}
