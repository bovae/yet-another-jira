package com.bovae.yaj.epics;

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

import com.bovae.yaj.error.ValidationException;
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
class EpicControllerTest {

    private static final String EPICS_URL = "/api/v1/epics";
    private static final UUID EPIC_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:00:00Z");
    private static final EpicResponse SAMPLE =
            new EpicResponse(EPIC_ID, TEAM_ID, "Payments", "Q3 scope", CREATED_AT, CREATED_AT);

    @Mock
    private EpicService epicService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EpicController(epicService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200WithEpics() throws Exception {
        when(epicService.list(null)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get(EPICS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(EPIC_ID.toString()))
                .andExpect(jsonPath("$[0].teamId").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$[0].title").value("Payments"));
    }

    @Test
    void list_shouldFilterByTeam_whenTeamIdParamPresent() throws Exception {
        when(epicService.list(TEAM_ID)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get(EPICS_URL).param("teamId", TEAM_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value(TEAM_ID.toString()));

        verify(epicService).list(TEAM_ID);
    }

    @Test
    void create_shouldReturn201WithLocationAndBody() throws Exception {
        when(epicService.create(eq(TEAM_ID), eq("Payments"), any())).thenReturn(SAMPLE);

        mockMvc.perform(post(EPICS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"teamId":"%s","title":"Payments","description":"Q3 scope"}"""
                                        .formatted(TEAM_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith(EPICS_URL + "/" + EPIC_ID)))
                .andExpect(jsonPath("$.id").value(EPIC_ID.toString()))
                .andExpect(jsonPath("$.title").value("Payments"));
    }

    @Test
    void create_shouldReturn400WithServiceMessage_whenServiceRejectsBlankTitle() throws Exception {
        // Blank-title validation now lives in EpicService (DTO @NotBlank was dropped), so the blank
        // value reaches the service and its ValidationException maps to a 400 problem detail.
        when(epicService.create(eq(TEAM_ID), eq("   "), any()))
                .thenThrow(new ValidationException("An epic title is required."));

        mockMvc.perform(post(EPICS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","title":"   "}"""
                                .formatted(TEAM_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("An epic title is required."));
    }

    @Test
    void create_shouldReturn400_whenTeamIdMissing() throws Exception {
        mockMvc.perform(post(EPICS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Payments"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(epicService);
    }

    @Test
    void get_shouldReturn200WithEpic() throws Exception {
        when(epicService.get(EPIC_ID)).thenReturn(SAMPLE);

        mockMvc.perform(get(EPICS_URL + "/" + EPIC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EPIC_ID.toString()));
    }

    @Test
    void update_shouldReturn200WithUpdatedEpic() throws Exception {
        EpicResponse updated = new EpicResponse(EPIC_ID, TEAM_ID, "Billing", null, CREATED_AT, CREATED_AT);
        when(epicService.update(eq(EPIC_ID), eq("Billing"), any())).thenReturn(updated);

        mockMvc.perform(put(EPICS_URL + "/" + EPIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Billing"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Billing"));

        verify(epicService).update(eq(EPIC_ID), eq("Billing"), any());
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete(EPICS_URL + "/" + EPIC_ID)).andExpect(status().isNoContent());

        verify(epicService).delete(EPIC_ID);
    }
}
