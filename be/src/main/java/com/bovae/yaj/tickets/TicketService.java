package com.bovae.yaj.tickets;

import com.bovae.yaj.domain.enums.TicketState;
import com.bovae.yaj.domain.enums.TicketType;
import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.security.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
class TicketService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 10000;
    private static final String TITLE_REQUIRED_MSG = "A ticket title is required.";
    private static final String TITLE_TOO_LONG_MSG = "A ticket title must not exceed 200 characters.";
    private static final String BODY_REQUIRED_MSG = "A ticket body is required.";
    private static final String BODY_TOO_LONG_MSG = "A ticket body must not exceed 10000 characters.";
    private static final String NOT_FOUND_MSG = "Ticket '%s' was not found.";
    // Deterministic list order: creation time, ties broken by id (stable under renames/updates).
    private static final Sort CREATED_THEN_ID = Sort.by("createdAt").and(Sort.by("id"));
    private static final String TEAM_NOT_FOUND_MSG = "Team '%s' was not found.";
    private static final String EPIC_NOT_FOUND_MSG = "Epic '%s' was not found.";
    private static final String EPIC_WRONG_TEAM_MSG = "Epic '%s' does not belong to team '%s'.";

    private final TicketRepository ticketRepository;
    private final TeamRepository teamRepository;
    private final EpicRepository epicRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<TicketResponse> list(@Nullable UUID teamId) {
        List<Ticket> tickets = (teamId != null)
                ? ticketRepository.findByTeamId(teamId, CREATED_THEN_ID)
                : ticketRepository.findAll(CREATED_THEN_ID);
        Map<UUID, String> emails =
                emailsByUserId(tickets.stream().map(Ticket::getCreatedBy).toList());
        return tickets.stream()
                .map(ticket -> TicketResponse.from(ticket, emails.get(ticket.getCreatedBy())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(UUID id) {
        return toResponse(require(id));
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
        Ticket saved = flushOrTranslateRace(ticket, teamId, epicId);
        LOG.info("Ticket created: ticketId={}, teamId={}", saved.getId(), teamId);
        return toResponse(saved);
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
        return toResponse(flushOrTranslateRace(ticket, teamId, epicId));
    }

    public TicketResponse changeState(UUID id, String state) {
        Ticket ticket = require(id);
        ticket.setState(TicketState.parse(state).code());
        // A same-state patch writes back an identical value: dirty checking makes it a clean no-op.
        return toResponse(ticketRepository.saveAndFlush(ticket));
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

    /**
     * Flushes the ticket, translating an insert/update FK violation caused by a concurrently deleted
     * team or epic into the same {@code 404} the pre-check would have returned (mirrors the delete-side
     * race handling).
     */
    private Ticket flushOrTranslateRace(Ticket ticket, UUID teamId, @Nullable UUID epicId) {
        try {
            return ticketRepository.saveAndFlush(ticket);
        } catch (DataIntegrityViolationException ex) {
            throw referencedRowVanished(teamId, epicId);
        }
    }

    private NotFoundException referencedRowVanished(UUID teamId, @Nullable UUID epicId) {
        LOG.warn("Ticket write race: a referenced team or epic was deleted before the flush; translating to 404");
        if (!teamRepository.existsById(teamId)) {
            return new NotFoundException(TEAM_NOT_FOUND_MSG.formatted(teamId));
        }
        if (epicId != null && !epicRepository.existsById(epicId)) {
            return new NotFoundException(EPIC_NOT_FOUND_MSG.formatted(epicId));
        }
        return new NotFoundException(TEAM_NOT_FOUND_MSG.formatted(teamId));
    }

    private TicketResponse toResponse(Ticket ticket) {
        String email = userRepository
                .findById(ticket.getCreatedBy())
                .map(User::getEmail)
                .orElse(null);
        return TicketResponse.from(ticket, email);
    }

    private Map<UUID, String> emailsByUserId(List<UUID> userIds) {
        List<UUID> distinct = userIds.stream().distinct().toList();
        return userRepository.findAllById(distinct).stream().collect(Collectors.toMap(User::getId, User::getEmail));
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
