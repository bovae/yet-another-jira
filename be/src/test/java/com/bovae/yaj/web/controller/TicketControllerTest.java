package com.bovae.yaj.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.tickets.TicketService;
import com.bovae.yaj.web.dto.TicketResponse;
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
class TicketControllerTest {

    private static final String TICKETS_URL = "/api/v1/tickets";
    private static final UUID TICKET_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String USER_EMAIL = "author@example.com";
    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:00:00Z");
    private static final TicketResponse SAMPLE = new TicketResponse(
            TICKET_ID, TEAM_ID, null, "bug", "new", "Fix login", "Steps", USER_ID, USER_EMAIL, CREATED_AT, CREATED_AT);

    @Mock
    private TicketService ticketService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketController(ticketService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200WithTickets() throws Exception {
        when(ticketService.list(null)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get(TICKETS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TICKET_ID.toString()))
                .andExpect(jsonPath("$[0].teamId").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$[0].title").value("Fix login"))
                .andExpect(jsonPath("$[0].createdByEmail").value(USER_EMAIL));
    }

    @Test
    void list_shouldFilterByTeam_whenTeamIdParamPresent() throws Exception {
        when(ticketService.list(TEAM_ID)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get(TICKETS_URL).param("teamId", TEAM_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value(TEAM_ID.toString()));

        verify(ticketService).list(TEAM_ID);
    }

    @Test
    void create_shouldReturn201WithLocationAndBody() throws Exception {
        when(ticketService.create(eq(TEAM_ID), eq("bug"), eq("new"), any(), eq("Fix login"), eq("Steps")))
                .thenReturn(SAMPLE);

        mockMvc.perform(post(TICKETS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"teamId":"%s","type":"bug","state":"new","title":"Fix login","body":"Steps"}"""
                                        .formatted(TEAM_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith(TICKETS_URL + "/" + TICKET_ID)))
                .andExpect(jsonPath("$.id").value(TICKET_ID.toString()))
                .andExpect(jsonPath("$.title").value("Fix login"));
    }

    @Test
    void create_shouldReturn400WithServiceMessage_whenServiceRejectsBlankTitle() throws Exception {
        // Blank-title validation now lives in TicketService (DTO @NotBlank was dropped), so the blank
        // value reaches the service and its ValidationException maps to a 400 problem detail.
        when(ticketService.create(eq(TEAM_ID), eq("bug"), eq("new"), any(), eq("   "), eq("Steps")))
                .thenThrow(new ValidationException("A ticket title is required."));

        mockMvc.perform(post(TICKETS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"teamId":"%s","type":"bug","state":"new","title":"   ","body":"Steps"}"""
                                        .formatted(TEAM_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A ticket title is required."));
    }

    @Test
    void create_shouldReturn400_whenTeamIdMissing() throws Exception {
        mockMvc.perform(
                        post(TICKETS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"type":"bug","state":"new","title":"Fix login","body":"Steps"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ticketService);
    }

    @Test
    void get_shouldReturn200WithTicket() throws Exception {
        when(ticketService.get(TICKET_ID)).thenReturn(SAMPLE);

        mockMvc.perform(get(TICKETS_URL + "/" + TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TICKET_ID.toString()));
    }

    @Test
    void update_shouldReturn200WithUpdatedTicket() throws Exception {
        TicketResponse updated = new TicketResponse(
                TICKET_ID,
                TEAM_ID,
                null,
                "feature",
                "in_progress",
                "Retitle",
                "Body",
                USER_ID,
                USER_EMAIL,
                CREATED_AT,
                CREATED_AT);
        when(ticketService.update(
                        eq(TICKET_ID), eq(TEAM_ID), eq("feature"), eq("in_progress"), any(), eq("Retitle"), eq("Body")))
                .thenReturn(updated);

        mockMvc.perform(put(TICKETS_URL + "/" + TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"teamId":"%s","type":"feature","state":"in_progress","title":"Retitle","body":"Body"}"""
                                        .formatted(TEAM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Retitle"))
                .andExpect(jsonPath("$.state").value("in_progress"));
    }

    @Test
    void changeState_shouldReturn200WithNewState() throws Exception {
        TicketResponse patched = new TicketResponse(
                TICKET_ID,
                TEAM_ID,
                null,
                "bug",
                "done",
                "Fix login",
                "Steps",
                USER_ID,
                USER_EMAIL,
                CREATED_AT,
                CREATED_AT);
        when(ticketService.changeState(TICKET_ID, "done")).thenReturn(patched);

        mockMvc.perform(patch(TICKETS_URL + "/" + TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"done"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("done"));

        verify(ticketService).changeState(TICKET_ID, "done");
    }

    @Test
    void changeState_shouldReturn400_whenStateBlank() throws Exception {
        mockMvc.perform(patch(TICKETS_URL + "/" + TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"   "}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ticketService);
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete(TICKETS_URL + "/" + TICKET_ID)).andExpect(status().isNoContent());

        verify(ticketService).delete(TICKET_ID);
    }

    // --- malformed UUID → 400 problem+json (type mismatch, before the service is touched) ---

    @Test
    void get_shouldReturn400ProblemJson_whenPathIdMalformed() throws Exception {
        mockMvc.perform(get(TICKETS_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(ticketService);
    }

    @Test
    void list_shouldReturn400ProblemJson_whenTeamIdParamMalformed() throws Exception {
        mockMvc.perform(get(TICKETS_URL).param("teamId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(ticketService);
    }
}
