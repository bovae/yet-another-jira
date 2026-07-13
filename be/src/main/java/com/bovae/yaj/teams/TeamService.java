package com.bovae.yaj.teams;

import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.ConflictException;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
class TeamService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final String NAME_REQUIRED_MSG = "A team name is required.";
    private static final String NAME_TOO_LONG_MSG = "A team name must not exceed 100 characters.";
    private static final String NAME_TAKEN_MSG = "A team named '%s' already exists.";
    private static final String NOT_FOUND_MSG = "Team '%s' was not found.";
    private static final String HAS_REFERENCES_MSG =
            "Team '%s' cannot be deleted while it has epics or tickets; remove them first.";
    // Deterministic list order: creation time, ties broken by id (stable under renames/updates).
    private static final Sort CREATED_THEN_ID = Sort.by("createdAt").and(Sort.by("id"));

    private final TeamRepository teamRepository;
    private final EpicRepository epicRepository;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public List<TeamResponse> list() {
        return teamRepository.findAll(CREATED_THEN_ID).stream()
                .map(TeamResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse get(UUID id) {
        return TeamResponse.from(require(id));
    }

    public TeamResponse create(String name) {
        String trimmed = normalize(name);
        if (teamRepository.existsByName(trimmed)) {
            throw new ConflictException(NAME_TAKEN_MSG.formatted(trimmed));
        }
        Team team = new Team();
        team.setName(trimmed);
        try {
            Team saved = teamRepository.saveAndFlush(team);
            LOG.info("Team created: teamId={}", saved.getId());
            return TeamResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            LOG.warn("Team create race: name unique-constraint rejected the insert; translating to conflict");
            throw new ConflictException(NAME_TAKEN_MSG.formatted(trimmed));
        }
    }

    public TeamResponse rename(UUID id, String name) {
        String trimmed = normalize(name);
        Team team = require(id);
        if (teamRepository.existsByNameAndIdNot(trimmed, id)) {
            throw new ConflictException(NAME_TAKEN_MSG.formatted(trimmed));
        }
        team.setName(trimmed);
        try {
            return TeamResponse.from(teamRepository.saveAndFlush(team));
        } catch (DataIntegrityViolationException ex) {
            LOG.warn("Team rename race: name unique-constraint rejected the update; translating to conflict");
            throw new ConflictException(NAME_TAKEN_MSG.formatted(trimmed));
        }
    }

    public void delete(UUID id) {
        Team team = require(id);
        if (epicRepository.existsByTeamId(id) || ticketRepository.existsByTeamId(id)) {
            throw new ConflictException(HAS_REFERENCES_MSG.formatted(id));
        }
        try {
            teamRepository.delete(team);
            // Flush inside the try so a concurrently inserted FK reference surfaces here as a
            // DataIntegrityViolationException rather than at commit, outside the catch.
            teamRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            LOG.warn("Team delete race: foreign-key reference rejected the delete; translating to conflict");
            throw new ConflictException(HAS_REFERENCES_MSG.formatted(id));
        }
        LOG.info("Team deleted: teamId={}", id);
    }

    private Team require(UUID id) {
        return teamRepository.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(id)));
    }

    private static String normalize(String name) {
        if (StringUtils.isBlank(name)) {
            throw new ValidationException(NAME_REQUIRED_MSG);
        }
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(NAME_TOO_LONG_MSG);
        }
        return trimmed;
    }
}
