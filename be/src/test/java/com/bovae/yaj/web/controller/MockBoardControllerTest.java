package com.bovae.yaj.web.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bovae.yaj.web.dto.BoardView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(controllers = MockBoardController.class)
@Import(TestSecurityConfig.class)
class MockBoardControllerTest {

    private static final String BOARD_URL = "/api/v1/mock/board";
    private static final List<String> CANONICAL_STATES =
            List.of("new", "ready_for_implementation", "in_progress", "ready_for_acceptance", "done");
    private static final Set<String> VALID_TYPES = Set.of("bug", "feature", "fix");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    // --- GET returns 200 ---

    @Test
    void board_shouldReturn200_whenRequested() throws Exception {
        mockMvc.perform(get(BOARD_URL)).andExpect(status().isOk());
    }

    // --- five columns in canonical order ---

    @Test
    void board_shouldReturnExactlyFiveColumns_whenRequested() throws Exception {
        mockMvc.perform(get(BOARD_URL)).andExpect(jsonPath("$.columns.length()").value(5));
    }

    @Test
    void board_shouldReturnColumnsInCanonicalOrder_whenRequested() throws Exception {
        ResultActions result = mockMvc.perform(get(BOARD_URL)).andExpect(status().isOk());
        for (int i = 0; i < CANONICAL_STATES.size(); i++) {
            result.andExpect(jsonPath("$.columns[" + i + "].state").value(CANONICAL_STATES.get(i)));
        }
    }

    // --- at least one card present ---

    @Test
    void board_shouldContainAtLeastOneCard_whenRequested() throws Exception {
        BoardView view = fetchBoard();

        long totalCards =
                view.columns().stream().mapToLong(col -> col.cards().size()).sum();
        assertTrue(totalCards >= 1, "board must contain at least one card");
    }

    // --- card title / type constraints ---

    @Test
    void board_shouldHaveValidCardTitles_whenRequested() throws Exception {
        BoardView view = fetchBoard();

        view.columns().stream().flatMap(col -> col.cards().stream()).forEach(card -> {
            assertNotNull(card.title(), "card title must not be null");
            assertFalse(card.title().isBlank(), "card title must not be blank: id=" + card.id());
            assertTrue(
                    card.title().length() <= 200,
                    "card title must be ≤200 chars: id=" + card.id() + " length="
                            + card.title().length());
        });
    }

    @Test
    void board_shouldHaveValidCardTypes_whenRequested() throws Exception {
        BoardView view = fetchBoard();

        view.columns().stream()
                .flatMap(col -> col.cards().stream())
                .forEach(card -> assertTrue(
                        VALID_TYPES.contains(card.type()),
                        "card type must be one of " + VALID_TYPES + " but was: " + card.type()));
    }

    // --- helpers ---

    private BoardView fetchBoard() throws Exception {
        MvcResult result =
                mockMvc.perform(get(BOARD_URL)).andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BoardView.class);
    }
}
