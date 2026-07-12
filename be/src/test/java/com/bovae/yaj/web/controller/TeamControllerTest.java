package com.bovae.yaj.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bovae.yaj.teams.TeamService;
import com.bovae.yaj.web.dto.TeamResponse;
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
class TeamControllerTest {

    private static final String TEAMS_URL = "/api/v1/teams";
    private static final UUID TEAM_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:00:00Z");
    private static final TeamResponse SAMPLE = new TeamResponse(TEAM_ID, "Platform", CREATED_AT, CREATED_AT);

    @Mock
    private TeamService teamService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TeamController(teamService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200WithTeams() throws Exception {
        when(teamService.list()).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get(TEAMS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Platform"));
    }

    @Test
    void create_shouldReturn201WithLocationAndBody() throws Exception {
        when(teamService.create("Platform")).thenReturn(SAMPLE);

        mockMvc.perform(post(TEAMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Platform"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith(TEAMS_URL + "/" + TEAM_ID)))
                .andExpect(jsonPath("$.id").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$.name").value("Platform"));
    }

    @Test
    void create_shouldReturn400_whenNameBlank() throws Exception {
        mockMvc.perform(post(TEAMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(teamService);
    }

    @Test
    void get_shouldReturn200WithTeam() throws Exception {
        when(teamService.get(TEAM_ID)).thenReturn(SAMPLE);

        mockMvc.perform(get(TEAMS_URL + "/" + TEAM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEAM_ID.toString()));
    }

    @Test
    void rename_shouldReturn200WithUpdatedTeam() throws Exception {
        TeamResponse renamed = new TeamResponse(TEAM_ID, "Payments", CREATED_AT, CREATED_AT);
        when(teamService.rename(eq(TEAM_ID), any())).thenReturn(renamed);

        mockMvc.perform(put(TEAMS_URL + "/" + TEAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Payments"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Payments"));

        verify(teamService).rename(TEAM_ID, "Payments");
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete(TEAMS_URL + "/" + TEAM_ID)).andExpect(status().isNoContent());

        verify(teamService).delete(TEAM_ID);
    }
}
