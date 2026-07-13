package com.bovae.yaj.epics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.ConflictException;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.web.dto.EpicResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class EpicServiceTest {

    private static final Instant OLD_MODIFIED_AT = Instant.parse("2024-01-01T00:00:00Z");
    private static final UUID EPIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TOO_LONG_TITLE = "a".repeat(201);

    @Mock
    private EpicRepository epicRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Captor
    private ArgumentCaptor<Epic> epicCaptor;

    private EpicService epicService;

    @BeforeEach
    void setUp() {
        epicService = new EpicService(epicRepository, teamRepository, ticketRepository);
    }

    // --- create ---

    @Test
    void create_shouldTrimTitleBeforePersisting_whenSurroundedByWhitespace() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);
        when(epicRepository.saveAndFlush(any(Epic.class))).thenAnswer(invocation -> invocation.getArgument(0));

        epicService.create(TEAM_ID, "  Payments  ", "Q3 scope");

        verify(epicRepository).saveAndFlush(epicCaptor.capture());
        assertEquals("Payments", epicCaptor.getValue().getTitle(), "title must be trimmed before persistence");
    }

    @ParameterizedTest(name = "blank title [{0}] -> ValidationException")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void create_shouldThrowValidation_whenTitleBlank(String title) {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);

        assertThrows(ValidationException.class, () -> epicService.create(TEAM_ID, title, null));
        verify(epicRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowValidation_whenTitleExceeds200CharsAfterTrim() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);

        assertThrows(ValidationException.class, () -> epicService.create(TEAM_ID, "  " + TOO_LONG_TITLE + "  ", null));
        verify(epicRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowNotFound_whenTeamDoesNotExist() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> epicService.create(TEAM_ID, "Payments", null));
        verifyNoInteractions(epicRepository);
    }

    @Test
    void create_shouldStoreNullDescription_whenDescriptionBlank() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);
        when(epicRepository.saveAndFlush(any(Epic.class))).thenAnswer(invocation -> invocation.getArgument(0));

        epicService.create(TEAM_ID, "Payments", "   ");

        verify(epicRepository).saveAndFlush(epicCaptor.capture());
        assertNull(epicCaptor.getValue().getDescription(), "blank description must collapse to null");
    }

    @Test
    void create_shouldThrowValidation_whenDescriptionExceeds10000CharsAfterTrim() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);

        assertThrows(ValidationException.class, () -> epicService.create(TEAM_ID, "Payments", "d".repeat(10001)));
        verify(epicRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldTranslateToNotFound_whenTeamVanishesBeforeFlush() {
        // team exists at the pre-check, then the FK-violating insert flush reports the parent is gone.
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);
        when(epicRepository.saveAndFlush(any(Epic.class))).thenThrow(new DataIntegrityViolationException("fk_team"));

        assertThrows(NotFoundException.class, () -> epicService.create(TEAM_ID, "Payments", null));
    }

    // --- get ---

    @Test
    void get_shouldReturnEpic_whenExists() {
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.of(existingEpic()));

        EpicResponse response = epicService.get(EPIC_ID);

        assertEquals(EPIC_ID, response.id());
        assertEquals(TEAM_ID, response.teamId());
        assertEquals("Payments", response.title());
    }

    // --- update ---

    @Test
    void update_shouldLeaveTeamUntouchedAndClearOmittedDescription_whenDescriptionNull() {
        Epic existing = existingEpic();
        existing.setDescription("old description");
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.of(existing));
        when(epicRepository.saveAndFlush(any(Epic.class))).thenAnswer(invocation -> invocation.getArgument(0));

        epicService.update(EPIC_ID, "Billing", null);

        verify(epicRepository).saveAndFlush(epicCaptor.capture());
        Epic saved = epicCaptor.getValue();
        assertEquals("Billing", saved.getTitle(), "title must be updated");
        assertNull(saved.getDescription(), "omitted description must clear the stored one");
        assertEquals(TEAM_ID, saved.getTeamId(), "team must remain unchanged on update");
    }

    // --- unknown id -> 404 ---

    @Test
    void get_shouldThrowNotFound_whenEpicMissing() {
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> epicService.get(EPIC_ID));
    }

    @Test
    void update_shouldThrowNotFound_whenEpicMissing() {
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> epicService.update(EPIC_ID, "Billing", null));
    }

    @Test
    void delete_shouldThrowNotFound_whenEpicMissing() {
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> epicService.delete(EPIC_ID));
    }

    // --- delete guard ---

    @Test
    void delete_shouldThrowConflict_whenTicketsReferenceEpic() {
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.of(existingEpic()));
        when(ticketRepository.existsByEpicId(EPIC_ID)).thenReturn(true);

        assertThrows(ConflictException.class, () -> epicService.delete(EPIC_ID));
        verify(epicRepository, never()).delete(any());
    }

    @Test
    void delete_shouldInvokeRepositoryDelete_whenNoReferences() {
        Epic epic = existingEpic();
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.of(epic));
        when(ticketRepository.existsByEpicId(EPIC_ID)).thenReturn(false);

        epicService.delete(EPIC_ID);

        verify(epicRepository).delete(epic);
    }

    @Test
    void delete_shouldTranslateToConflict_whenConcurrentReferenceRace() {
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.of(existingEpic()));
        when(ticketRepository.existsByEpicId(EPIC_ID)).thenReturn(false);
        Mockito.doThrow(new DataIntegrityViolationException("fk"))
                .when(epicRepository)
                .flush();

        assertThrows(ConflictException.class, () -> epicService.delete(EPIC_ID));
    }

    // --- list filter ---

    @Test
    void list_shouldFilterByTeam_whenTeamIdPresent() {
        when(epicRepository.findByTeamId(eq(TEAM_ID), any(Sort.class))).thenReturn(List.of(existingEpic()));

        List<EpicResponse> result = epicService.list(TEAM_ID);

        assertEquals(1, result.size());
        assertEquals(TEAM_ID, result.get(0).teamId());
        verify(epicRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void list_shouldReturnAll_whenTeamIdAbsent() {
        when(epicRepository.findAll(any(Sort.class))).thenReturn(List.of(existingEpic()));

        List<EpicResponse> result = epicService.list(null);

        assertEquals(1, result.size());
        verify(epicRepository, never()).findByTeamId(any(), any());
    }

    // --- helpers ---

    private static Epic existingEpic() {
        Epic epic = new Epic();
        epic.setId(EPIC_ID);
        epic.setTeamId(TEAM_ID);
        epic.setTitle("Payments");
        epic.setCreatedAt(OLD_MODIFIED_AT);
        epic.setModifiedAt(OLD_MODIFIED_AT);
        return epic;
    }
}
