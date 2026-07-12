package com.bovae.yaj.web.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bovae.yaj.comments.CommentService;
import com.bovae.yaj.web.dto.CommentResponse;
import com.bovae.yaj.web.error.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    private static final UUID TICKET_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID COMMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:00:00Z");
    private static final String COMMENTS_URL = "/api/v1/tickets/" + TICKET_ID + "/comments";
    private static final CommentResponse SAMPLE =
            new CommentResponse(COMMENT_ID, TICKET_ID, USER_ID, "Looks good", CREATED_AT);

    @Mock
    private CommentService commentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CommentController(commentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200WithComments() throws Exception {
        when(commentService.list(TICKET_ID)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get(COMMENTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(COMMENT_ID.toString()))
                .andExpect(jsonPath("$[0].ticketId").value(TICKET_ID.toString()))
                .andExpect(jsonPath("$[0].authorId").value(USER_ID.toString()))
                .andExpect(jsonPath("$[0].body").value("Looks good"));
    }

    @Test
    void add_shouldReturn201WithLocationAndBody() throws Exception {
        when(commentService.add(eq(TICKET_ID), eq("Looks good"))).thenReturn(SAMPLE);

        mockMvc.perform(post(COMMENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Looks good"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith(COMMENTS_URL + "/" + COMMENT_ID)))
                .andExpect(jsonPath("$.id").value(COMMENT_ID.toString()))
                .andExpect(jsonPath("$.body").value("Looks good"));

        verify(commentService).add(TICKET_ID, "Looks good");
    }

    @Test
    void add_shouldReturn400_whenBodyBlank() throws Exception {
        mockMvc.perform(post(COMMENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"   "}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commentService);
    }

    @Test
    void add_shouldReturn400_whenBodyMissing() throws Exception {
        mockMvc.perform(post(COMMENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commentService);
    }
}
