package com.bovae.yaj.teams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.ConflictException;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.web.dto.TeamResponse;
import java.time.Instant;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    private static final Instant OLD_MODIFIED_AT = Instant.parse("2024-01-01T00:00:00Z");
    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TOO_LONG_NAME = "a".repeat(101);

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private EpicRepository epicRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Captor
    private ArgumentCaptor<Team> teamCaptor;

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamRepository, epicRepository, ticketRepository);
    }

    // --- create ---

    @Test
    void create_shouldTrimNameBeforePersisting_whenSurroundedByWhitespace() {
        when(teamRepository.existsByName("Platform")).thenReturn(false);
        when(teamRepository.saveAndFlush(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        teamService.create("  Platform  ");

        verify(teamRepository).saveAndFlush(teamCaptor.capture());
        assertEquals("Platform", teamCaptor.getValue().getName(), "name must be trimmed before persistence");
    }

    @ParameterizedTest(name = "blank name [{0}] -> ValidationException")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void create_shouldThrowValidation_whenNameBlank(String name) {
        assertThrows(ValidationException.class, () -> teamService.create(name));
        verify(teamRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowConflict_whenNameAlreadyExists() {
        when(teamRepository.existsByName("Platform")).thenReturn(true);

        assertThrows(ConflictException.class, () -> teamService.create("Platform"));
        verify(teamRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowValidation_whenNameExceeds100CharsAfterTrim() {
        assertThrows(ValidationException.class, () -> teamService.create("  " + TOO_LONG_NAME + "  "));
        verify(teamRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldTranslateToConflict_whenUniqueConstraintRace() {
        when(teamRepository.existsByName("Platform")).thenReturn(false);
        when(teamRepository.saveAndFlush(any(Team.class))).thenThrow(new DataIntegrityViolationException("unique"));

        assertThrows(ConflictException.class, () -> teamService.create("Platform"));
    }

    // --- rename ---

    @Test
    void rename_shouldThrowConflict_whenNameBelongsToAnotherTeam() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(existingTeam()));
        when(teamRepository.existsByNameAndIdNot("Payments", TEAM_ID)).thenReturn(true);

        assertThrows(ConflictException.class, () -> teamService.rename(TEAM_ID, "Payments"));
        verify(teamRepository, never()).saveAndFlush(any());
    }

    @Test
    void rename_shouldSucceed_whenRenamingToOwnCurrentName() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(existingTeam()));
        when(teamRepository.existsByNameAndIdNot("Platform", TEAM_ID)).thenReturn(false);
        when(teamRepository.saveAndFlush(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamResponse response = teamService.rename(TEAM_ID, "Platform");

        assertEquals("Platform", response.name());
    }

    @Test
    void rename_shouldTrimAndPersistNewName_whenSuccessful() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(existingTeam()));
        when(teamRepository.existsByNameAndIdNot("Renamed", TEAM_ID)).thenReturn(false);
        when(teamRepository.saveAndFlush(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        teamService.rename(TEAM_ID, "  Renamed  ");

        verify(teamRepository).saveAndFlush(teamCaptor.capture());
        assertEquals("Renamed", teamCaptor.getValue().getName(), "rename must trim and apply the new name");
    }

    @Test
    void rename_shouldThrowValidation_whenNameExceeds100CharsAfterTrim() {
        assertThrows(ValidationException.class, () -> teamService.rename(TEAM_ID, "  " + TOO_LONG_NAME + "  "));
        verify(teamRepository, never()).saveAndFlush(any());
    }

    @Test
    void rename_shouldTranslateToConflict_whenUniqueConstraintRace() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(existingTeam()));
        when(teamRepository.existsByNameAndIdNot("Payments", TEAM_ID)).thenReturn(false);
        when(teamRepository.saveAndFlush(any(Team.class))).thenThrow(new DataIntegrityViolationException("unique"));

        assertThrows(ConflictException.class, () -> teamService.rename(TEAM_ID, "Payments"));
    }

    // --- unknown id -> 404 ---

    @Test
    void get_shouldThrowNotFound_whenTeamMissing() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> teamService.get(TEAM_ID));
    }

    @Test
    void rename_shouldThrowNotFound_whenTeamMissing() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> teamService.rename(TEAM_ID, "Platform"));
    }

    @Test
    void delete_shouldThrowNotFound_whenTeamMissing() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> teamService.delete(TEAM_ID));
    }

    // --- delete guard ---

    @Test
    void delete_shouldThrowConflict_whenTeamHasEpics() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(existingTeam()));
        when(epicRepository.existsByTeamId(TEAM_ID)).thenReturn(true);

        assertThrows(ConflictException.class, () -> teamService.delete(TEAM_ID));
        verify(teamRepository, never()).delete(any());
    }

    @Test
    void delete_shouldThrowConflict_whenTeamHasTickets() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(existingTeam()));
        when(epicRepository.existsByTeamId(TEAM_ID)).thenReturn(false);
        when(ticketRepository.existsByTeamId(TEAM_ID)).thenReturn(true);

        assertThrows(ConflictException.class, () -> teamService.delete(TEAM_ID));
        verify(teamRepository, never()).delete(any());
    }

    @Test
    void delete_shouldInvokeRepositoryDelete_whenNoReferences() {
        Team team = existingTeam();
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
        when(epicRepository.existsByTeamId(TEAM_ID)).thenReturn(false);
        when(ticketRepository.existsByTeamId(TEAM_ID)).thenReturn(false);

        teamService.delete(TEAM_ID);

        verify(teamRepository).delete(team);
    }

    @Test
    void delete_shouldTranslateToConflict_whenConcurrentReferenceRace() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(existingTeam()));
        when(epicRepository.existsByTeamId(TEAM_ID)).thenReturn(false);
        when(ticketRepository.existsByTeamId(TEAM_ID)).thenReturn(false);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk"))
                .when(teamRepository)
                .flush();

        assertThrows(ConflictException.class, () -> teamService.delete(TEAM_ID));
    }

    // --- helpers ---

    private static Team existingTeam() {
        Team team = new Team();
        team.setId(TEAM_ID);
        team.setName("Platform");
        team.setCreatedAt(OLD_MODIFIED_AT);
        team.setModifiedAt(OLD_MODIFIED_AT);
        return team;
    }
}
