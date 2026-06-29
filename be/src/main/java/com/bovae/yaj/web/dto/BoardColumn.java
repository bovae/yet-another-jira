package com.bovae.yaj.web.dto;

import java.util.List;

public record BoardColumn(String state, String label, List<BoardCard> cards) {}
