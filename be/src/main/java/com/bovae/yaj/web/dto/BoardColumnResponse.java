package com.bovae.yaj.web.dto;

import java.util.List;

public record BoardColumnResponse(String state, List<BoardCardResponse> cards) {}
