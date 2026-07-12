package com.bovae.yaj.tickets;

import com.bovae.yaj.domain.enums.TicketState;
import com.bovae.yaj.domain.enums.TicketType;
import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.security.CurrentUserProvider;
import com.bovae.yaj.web.dto.TicketResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TicketService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 10000;
    private static final String TITLE_REQUIRED_MSG = "A ticket title is required.";
    private static final String TITLE_TOO_LONG_MSG = "A ticket title must not exceed 200 characters.";
    private static final String BODY_REQUIRED_MSG = "A ticket body is required.";
    private static final String BODY_TOO_LONG_MSG = "A ticket body must not exceed 10000 characters.";
    private static final String NOT_FOUND_MSG = "Ticket '%s' was not found.";
    private static final String TEAM_NOT_FOUND_MSG = "Team '%s' was not found.";
    private static final String EPIC_NOT_FOUND_MSG = "Epic '%s' was not found.";
    private static final String EPIC_WRONG_TEAM_MSG = "Epic '%s' does not belong to team '%s'.";

    private final TicketRepository ticketRepository;
    private final TeamRepository teamRepository;
    private final EpicRepository epicRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<TicketResponse> list(@Nullable UUID teamId) {
        List<Ticket> tickets = (teamId != null) ? ticketRepository.findByTeamId(teamId) : ticketRepository.findAll();
        return tickets.stream().map(TicketResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(UUID id) {
        return TicketResponse.from(require(id));
    }

    public TicketResponse create(
            UUID teamId, String type, String state, @Nullable UUID epicId, String title, String body) {
        requireTeam(teamId);
        String typeCode = TicketType.parse(type).code();
        String stateCode = TicketState.parse(state).code();
        requireEpicOfTeam(epicId, teamId);

        Ticket ticket = new Ticket();
        ticket.setTeamId(teamId);
        ticket.setEpicId(epicId);
        ticket.setType(typeCode);
        ticket.setState(stateCode);
        ticket.setTitle(normalizeTitle(title));
        ticket.setBody(normalizeBody(body));
        ticket.setCreatedBy(currentUserProvider.requireCurrentUserId());
        Ticket saved = ticketRepository.saveAndFlush(ticket);
        LOG.info("Ticket created: ticketId={}, teamId={}", saved.getId(), teamId);
        return TicketResponse.from(saved);
    }

    public TicketResponse update(
            UUID id, UUID teamId, String type, String state, @Nullable UUID epicId, String title, String body) {
        Ticket ticket = require(id);
        requireTeam(teamId);
        String typeCode = TicketType.parse(type).code();
        String stateCode = TicketState.parse(state).code();
        // Validate the epic against the INCOMING team so a team change that keeps a stale
        // old-team epic is rejected rather than silently orphaned.
        requireEpicOfTeam(epicId, teamId);

        // Full replacement: an omitted epicId clears the reference.
        ticket.setTeamId(teamId);
        ticket.setEpicId(epicId);
        ticket.setType(typeCode);
        ticket.setState(stateCode);
        ticket.setTitle(normalizeTitle(title));
        ticket.setBody(normalizeBody(body));
        // Hibernate dirty checking: writing identical values leaves the row clean, so @UpdateTimestamp
        // does not advance modified_at on a no-op save.
        return TicketResponse.from(ticketRepository.saveAndFlush(ticket));
    }

    public TicketResponse changeState(UUID id, String state) {
        Ticket ticket = require(id);
        ticket.setState(TicketState.parse(state).code());
        // A same-state patch writes back an identical value: dirty checking makes it a clean no-op.
        return TicketResponse.from(ticketRepository.saveAndFlush(ticket));
    }

    public void delete(UUID id) {
        Ticket ticket = require(id);
        // Comments disappear via the DB ON DELETE CASCADE; nothing else references tickets.
        ticketRepository.delete(ticket);
        LOG.info("Ticket deleted: ticketId={}", id);
    }

    private Ticket require(UUID id) {
        return ticketRepository.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(id)));
    }

    private void requireTeam(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new NotFoundException(TEAM_NOT_FOUND_MSG.formatted(teamId));
        }
    }

    private void requireEpicOfTeam(@Nullable UUID epicId, UUID teamId) {
        if (epicId == null) {
            return;
        }
        Epic epic = epicRepository
                .findById(epicId)
                .orElseThrow(() -> new NotFoundException(EPIC_NOT_FOUND_MSG.formatted(epicId)));
        if (!epic.getTeamId().equals(teamId)) {
            throw new ValidationException(EPIC_WRONG_TEAM_MSG.formatted(epicId, teamId));
        }
    }

    private static String normalizeTitle(String title) {
        if (StringUtils.isBlank(title)) {
            throw new ValidationException(TITLE_REQUIRED_MSG);
        }
        String trimmed = title.strip();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new ValidationException(TITLE_TOO_LONG_MSG);
        }
        return trimmed;
    }

    private static String normalizeBody(String body) {
        if (StringUtils.isBlank(body)) {
            throw new ValidationException(BODY_REQUIRED_MSG);
        }
        String trimmed = body.strip();
        if (trimmed.length() > MAX_BODY_LENGTH) {
            throw new ValidationException(BODY_TOO_LONG_MSG);
        }
        return trimmed;
    }
}
