package com.bovae.yaj.board;

import com.bovae.yaj.domain.enums.TicketState;
import com.bovae.yaj.domain.enums.TicketType;
import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.web.dto.BoardCardResponse;
import com.bovae.yaj.web.dto.BoardColumnResponse;
import com.bovae.yaj.web.dto.BoardResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BoardService {

    private static final String TEAM_NOT_FOUND_MSG = "Team '%s' was not found.";

    private final TicketRepository ticketRepository;
    private final TeamRepository teamRepository;
    private final EpicRepository epicRepository;

    public BoardResponse board(UUID teamId, @Nullable String type, @Nullable UUID epicId, @Nullable String q) {
        if (!teamRepository.existsById(teamId)) {
            throw new NotFoundException(TEAM_NOT_FOUND_MSG.formatted(teamId));
        }
        // parse() rejects a non-blank unknown code with 400; blank/absent means "no type filter".
        String typeCode =
                StringUtils.isBlank(type) ? null : TicketType.parse(type).code();
        String titlePattern = StringUtils.isBlank(q) ? null : toLikePattern(q.strip());

        List<Ticket> tickets = ticketRepository.findBoardTickets(teamId, typeCode, epicId, titlePattern);
        Map<UUID, String> epicTitles =
                epicRepository.findByTeamId(teamId).stream().collect(Collectors.toMap(Epic::getId, Epic::getTitle));

        // The query already orders by modified_at DESC; groupingBy preserves that encounter order per column.
        Map<String, List<BoardCardResponse>> cardsByState = tickets.stream()
                .collect(Collectors.groupingBy(
                        Ticket::getState, Collectors.mapping(t -> toCard(t, epicTitles), Collectors.toList())));

        List<BoardColumnResponse> columns = Arrays.stream(TicketState.values())
                .map(state -> new BoardColumnResponse(state.code(), cardsByState.getOrDefault(state.code(), List.of())))
                .toList();
        return new BoardResponse(columns);
    }

    private static BoardCardResponse toCard(Ticket ticket, Map<UUID, String> epicTitles) {
        UUID epicId = ticket.getEpicId();
        String epicTitle = (epicId == null) ? null : epicTitles.get(epicId);
        return new BoardCardResponse(ticket.getId(), ticket.getTitle(), ticket.getType(), epicId, epicTitle);
    }

    /**
     * Escapes LIKE wildcards ({@code \}, {@code %}, {@code _}) in user input so the search is a literal
     * substring match, then wraps it as a {@code %substring%} pattern. Case folding is left to the query
     * ({@code LOWER(title) LIKE LOWER(:pattern)}) so both sides fold identically in the database.
     * Backslash is escaped first so it does not double-escape the wildcards added after it.
     */
    private static String toLikePattern(String q) {
        String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
