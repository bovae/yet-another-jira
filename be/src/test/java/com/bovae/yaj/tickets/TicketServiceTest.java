package com.bovae.yaj.tickets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.security.CurrentUserProvider;
import com.bovae.yaj.web.dto.TicketResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final Instant OLD_MODIFIED_AT = Instant.parse("2024-01-01T00:00:00Z");
    private static final UUID TICKET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TEAM_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID EPIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String TOO_LONG_TITLE = "a".repeat(201);
    private static final String TOO_LONG_BODY = "b".repeat(10001);

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private EpicRepository epicRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Captor
    private ArgumentCaptor<Ticket> ticketCaptor;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, teamRepository, epicRepository, currentUserProvider);
    }

    // --- create ---

    @Test
    void create_shouldTrimTitleAndBodyAndSetCreatedBy_whenValid() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);
        when(currentUserProvider.requireCurrentUserId()).thenReturn(USER_ID);
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.create(TEAM_ID, "bug", "new", null, "  Fix login  ", "  Steps  ");

        verify(ticketRepository).saveAndFlush(ticketCaptor.capture());
        Ticket saved = ticketCaptor.getValue();
        assertEquals("Fix login", saved.getTitle(), "title must be trimmed");
        assertEquals("Steps", saved.getBody(), "body must be trimmed");
        assertEquals("bug", saved.getType());
        assertEquals("new", saved.getState());
        assertNull(saved.getEpicId(), "epic must be null when omitted");
        assertEquals(USER_ID, saved.getCreatedBy(), "created_by must come from the current user");
    }

    @Test
    void create_shouldSetEpic_whenEpicBelongsToSameTeam() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.of(epicOfTeam(TEAM_ID)));
        when(currentUserProvider.requireCurrentUserId()).thenReturn(USER_ID);
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.create(TEAM_ID, "feature", "new", EPIC_ID, "Title", "Body");

        verify(ticketRepository).saveAndFlush(ticketCaptor.capture());
        assertEquals(EPIC_ID, ticketCaptor.getValue().getEpicId());
    }

    @Test
    void create_shouldThrowNotFound_whenTeamMissing() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> ticketService.create(TEAM_ID, "bug", "new", null, "Title", "Body"));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowNotFound_whenEpicMissing() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> ticketService.create(TEAM_ID, "bug", "new", EPIC_ID, "Title", "Body"));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowValidation_whenEpicBelongsToDifferentTeam() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.of(epicOfTeam(OTHER_TEAM_ID)));

        assertThrows(
                ValidationException.class, () -> ticketService.create(TEAM_ID, "bug", "new", EPIC_ID, "Title", "Body"));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest(name = "type={0}, state={1} -> ValidationException")
    @CsvSource({"bogus, new", "bug, bogus"})
    void create_shouldThrowValidation_whenEnumCodeInvalid(String type, String state) {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);

        assertThrows(
                ValidationException.class, () -> ticketService.create(TEAM_ID, type, state, null, "Title", "Body"));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest(name = "blank title [{0}] -> ValidationException")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void create_shouldThrowValidation_whenTitleBlank(String title) {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);

        assertThrows(ValidationException.class, () -> ticketService.create(TEAM_ID, "bug", "new", null, title, "Body"));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowValidation_whenTitleExceeds200CharsAfterTrim() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);

        assertThrows(
                ValidationException.class,
                () -> ticketService.create(TEAM_ID, "bug", "new", null, "  " + TOO_LONG_TITLE + "  ", "Body"));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest(name = "blank body [{0}] -> ValidationException")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void create_shouldThrowValidation_whenBodyBlank(String body) {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);

        assertThrows(ValidationException.class, () -> ticketService.create(TEAM_ID, "bug", "new", null, "Title", body));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowValidation_whenBodyExceeds10000CharsAfterTrim() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);

        assertThrows(
                ValidationException.class,
                () -> ticketService.create(TEAM_ID, "bug", "new", null, "Title", TOO_LONG_BODY));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    // --- get ---

    @Test
    void get_shouldReturnTicket_whenExists() {
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(existingTicket()));

        TicketResponse response = ticketService.get(TICKET_ID);

        assertEquals(TICKET_ID, response.id());
        assertEquals(TEAM_ID, response.teamId());
        assertEquals("Old title", response.title());
    }

    @Test
    void get_shouldThrowNotFound_whenTicketMissing() {
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> ticketService.get(TICKET_ID));
    }

    // --- update ---

    @Test
    void update_shouldReplaceAllFieldsAndClearEpic_whenTeamChangesWithNoEpic() {
        Ticket existing = existingTicket();
        existing.setEpicId(EPIC_ID);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(existing));
        when(teamRepository.existsById(OTHER_TEAM_ID)).thenReturn(true);
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.update(TICKET_ID, OTHER_TEAM_ID, "fix", "done", null, "New title", "New body");

        verify(ticketRepository).saveAndFlush(ticketCaptor.capture());
        Ticket saved = ticketCaptor.getValue();
        assertEquals(OTHER_TEAM_ID, saved.getTeamId(), "team must be replaced");
        assertNull(saved.getEpicId(), "omitted epic must clear the stored reference");
        assertEquals("fix", saved.getType());
        assertEquals("done", saved.getState());
        assertEquals("New title", saved.getTitle());
        assertEquals("New body", saved.getBody());
    }

    @Test
    void update_shouldThrowValidation_whenTeamChangeKeepsOldTeamEpic() {
        Ticket existing = existingTicket();
        existing.setEpicId(EPIC_ID);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(existing));
        when(teamRepository.existsById(OTHER_TEAM_ID)).thenReturn(true);
        when(epicRepository.findById(EPIC_ID)).thenReturn(Optional.of(epicOfTeam(TEAM_ID)));

        assertThrows(
                ValidationException.class,
                () -> ticketService.update(TICKET_ID, OTHER_TEAM_ID, "bug", "new", EPIC_ID, "Title", "Body"));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_shouldThrowNotFound_whenTicketMissing() {
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());
        assertThrows(
                NotFoundException.class,
                () -> ticketService.update(TICKET_ID, TEAM_ID, "bug", "new", null, "Title", "Body"));
    }

    @Test
    void update_shouldThrowNotFound_whenTeamMissing() {
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(existingTicket()));
        when(teamRepository.existsById(OTHER_TEAM_ID)).thenReturn(false);
        assertThrows(
                NotFoundException.class,
                () -> ticketService.update(TICKET_ID, OTHER_TEAM_ID, "bug", "new", null, "Title", "Body"));
    }

    // --- changeState ---

    @Test
    void changeState_shouldPersistNewState_whenValid() {
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(existingTicket()));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.changeState(TICKET_ID, "in_progress");

        verify(ticketRepository).saveAndFlush(ticketCaptor.capture());
        assertEquals("in_progress", ticketCaptor.getValue().getState());
    }

    @Test
    void changeState_shouldThrowValidation_whenStateCodeInvalid() {
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(existingTicket()));

        assertThrows(ValidationException.class, () -> ticketService.changeState(TICKET_ID, "bogus"));
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeState_shouldThrowNotFound_whenTicketMissing() {
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> ticketService.changeState(TICKET_ID, "done"));
    }

    // --- delete ---

    @Test
    void delete_shouldInvokeRepositoryDelete_whenExists() {
        Ticket ticket = existingTicket();
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        ticketService.delete(TICKET_ID);

        verify(ticketRepository).delete(ticket);
    }

    @Test
    void delete_shouldThrowNotFound_whenTicketMissing() {
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> ticketService.delete(TICKET_ID));
    }

    // --- list filter ---

    @Test
    void list_shouldFilterByTeam_whenTeamIdPresent() {
        when(ticketRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(existingTicket()));

        List<TicketResponse> result = ticketService.list(TEAM_ID);

        assertEquals(1, result.size());
        assertEquals(TEAM_ID, result.get(0).teamId());
        verify(ticketRepository, never()).findAll();
    }

    @Test
    void list_shouldReturnAll_whenTeamIdAbsent() {
        when(ticketRepository.findAll()).thenReturn(List.of(existingTicket()));

        List<TicketResponse> result = ticketService.list(null);

        assertEquals(1, result.size());
        verify(ticketRepository, never()).findByTeamId(any());
    }

    // --- helpers ---

    private static Ticket existingTicket() {
        Ticket ticket = new Ticket();
        ticket.setId(TICKET_ID);
        ticket.setTeamId(TEAM_ID);
        ticket.setType("bug");
        ticket.setState("new");
        ticket.setTitle("Old title");
        ticket.setBody("Old body");
        ticket.setCreatedBy(USER_ID);
        ticket.setCreatedAt(OLD_MODIFIED_AT);
        ticket.setModifiedAt(OLD_MODIFIED_AT);
        return ticket;
    }

    private static Epic epicOfTeam(UUID teamId) {
        Epic epic = new Epic();
        epic.setId(EPIC_ID);
        epic.setTeamId(teamId);
        epic.setTitle("Epic");
        return epic;
    }
}
