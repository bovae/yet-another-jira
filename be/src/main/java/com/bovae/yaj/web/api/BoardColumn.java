package com.bovae.yaj.web.api;

import java.util.List;

public record BoardColumn(String state, String label, List<BoardCard> cards) {}
