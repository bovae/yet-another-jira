package com.bovae.yaj.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.domain.enums.TicketState;
import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.web.dto.BoardCardResponse;
import com.bovae.yaj.web.dto.BoardColumnResponse;
import com.bovae.yaj.web.dto.BoardResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EPIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final List<String> WORKFLOW_ORDER =
            List.of("new", "ready_for_implementation", "in_progress", "ready_for_acceptance", "done");

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private EpicRepository epicRepository;

    @Captor
    private ArgumentCaptor<String> titlePatternCaptor;

    private BoardService boardService;

    @BeforeEach
    void setUp() {
        boardService = new BoardService(ticketRepository, teamRepository, epicRepository);
    }

    // --- structure & grouping ---

    @Test
    void board_shouldReturnFiveEmptyColumnsInWorkflowOrder_whenNoTickets() {
        stubTeamExists();
        when(ticketRepository.findBoardTickets(eq(TEAM_ID), any(), any(), any()))
                .thenReturn(List.of());
        when(epicRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        BoardResponse board = boardService.board(TEAM_ID, null, null, null);

        assertEquals(
                WORKFLOW_ORDER,
                board.columns().stream().map(BoardColumnResponse::state).toList());
        board.columns().forEach(column -> assertTrue(column.cards().isEmpty(), "every column starts empty"));
    }

    @Test
    void board_shouldGroupCardsByStatePreservingModifiedOrder_whenTicketsAcrossStates() {
        stubTeamExists();
        // Repository returns modified_at DESC; grouping must keep that per-column order.
        when(ticketRepository.findBoardTickets(eq(TEAM_ID), any(), any(), any()))
                .thenReturn(List.of(
                        ticket("newer", "new", null),
                        ticket("older", "new", null),
                        ticket("wip", "in_progress", null)));
        when(epicRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        BoardResponse board = boardService.board(TEAM_ID, null, null, null);

        assertEquals(List.of("newer", "older"), cardTitles(board, "new"));
        assertEquals(List.of("wip"), cardTitles(board, "in_progress"));
        assertTrue(cardTitles(board, "done").isEmpty());
    }

    // --- epic title resolution ---

    @Test
    void board_shouldAttachEpicTitle_whenTicketReferencesEpic() {
        stubTeamExists();
        when(ticketRepository.findBoardTickets(eq(TEAM_ID), any(), any(), any()))
                .thenReturn(List.of(ticket("with-epic", "new", EPIC_ID)));
        when(epicRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(epic(EPIC_ID, "Onboarding")));

        BoardCardResponse card = firstCard(boardService.board(TEAM_ID, null, null, null), "new");

        assertEquals(EPIC_ID, card.epicId());
        assertEquals("Onboarding", card.epicTitle());
    }

    @Test
    void board_shouldLeaveEpicNull_whenTicketHasNoEpic() {
        stubTeamExists();
        when(ticketRepository.findBoardTickets(eq(TEAM_ID), any(), any(), any()))
                .thenReturn(List.of(ticket("no-epic", "new", null)));
        when(epicRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(epic(EPIC_ID, "Onboarding")));

        BoardCardResponse card = firstCard(boardService.board(TEAM_ID, null, null, null), "new");

        assertNull(card.epicId(), "epicId absent when the ticket has no epic");
        assertNull(card.epicTitle(), "epicTitle absent when the ticket has no epic");
    }

    // --- team & type validation ---

    @Test
    void board_shouldThrowNotFound_whenTeamMissing() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> boardService.board(TEAM_ID, null, null, null));
        verify(ticketRepository, never()).findBoardTickets(any(), any(), any(), any());
    }

    @Test
    void board_shouldThrowValidation_whenTypeCodeInvalid() {
        stubTeamExists();

        assertThrows(ValidationException.class, () -> boardService.board(TEAM_ID, "banana", null, null));
        verify(ticketRepository, never()).findBoardTickets(any(), any(), any(), any());
    }

    // --- filter normalization ---

    @Test
    void board_shouldNormalizeBlankFiltersToNull_whenTypeAndQBlank() {
        stubTeamExists();
        when(ticketRepository.findBoardTickets(eq(TEAM_ID), isNull(), any(), isNull()))
                .thenReturn(List.of());
        when(epicRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        boardService.board(TEAM_ID, "   ", null, "  ");

        // Blank type/q must not be parsed or wrapped into a pattern; they disable their filters.
        verify(ticketRepository).findBoardTickets(TEAM_ID, null, null, null);
    }

    // --- LIKE pattern building ---

    @ParameterizedTest(name = "q={0} -> pattern={1}")
    @CsvSource({
        "login, %login%",
        "LoGin, %login%",
        "100%, %100\\%%",
        "a_b, %a\\_b%",
        "c\\d, %c\\\\d%",
    })
    void board_shouldBuildEscapedSubstringPattern_whenSearching(String q, String expectedPattern) {
        stubTeamExists();
        when(ticketRepository.findBoardTickets(eq(TEAM_ID), any(), any(), titlePatternCaptor.capture()))
                .thenReturn(List.of());
        when(epicRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        boardService.board(TEAM_ID, null, null, q);

        assertEquals(expectedPattern, titlePatternCaptor.getValue());
    }

    // --- helpers ---

    private void stubTeamExists() {
        when(teamRepository.existsById(TEAM_ID)).thenReturn(true);
    }

    private static List<String> cardTitles(BoardResponse board, String state) {
        return column(board, state).cards().stream()
                .map(BoardCardResponse::title)
                .toList();
    }

    private static BoardCardResponse firstCard(BoardResponse board, String state) {
        return column(board, state).cards().get(0);
    }

    private static BoardColumnResponse column(BoardResponse board, String state) {
        return board.columns().stream()
                .filter(c -> c.state().equals(state))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing column: " + state));
    }

    private static Ticket ticket(String title, String state, UUID epicId) {
        Ticket ticket = new Ticket();
        ticket.setId(UUID.randomUUID());
        ticket.setTeamId(TEAM_ID);
        ticket.setEpicId(epicId);
        ticket.setType("bug");
        ticket.setState(TicketState.parse(state).code());
        ticket.setTitle(title);
        ticket.setBody("body");
        return ticket;
    }

    private static Epic epic(UUID id, String title) {
        Epic epic = new Epic();
        epic.setId(id);
        epic.setTeamId(TEAM_ID);
        epic.setTitle(title);
        return epic;
    }
}
