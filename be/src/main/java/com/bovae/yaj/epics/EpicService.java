package com.bovae.yaj.epics;

import com.bovae.yaj.domain.model.Epic;
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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
class EpicService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 10000;
    private static final String TITLE_REQUIRED_MSG = "An epic title is required.";
    private static final String TITLE_TOO_LONG_MSG = "An epic title must not exceed 200 characters.";
    private static final String DESCRIPTION_TOO_LONG_MSG = "An epic description must not exceed 10000 characters.";
    private static final String NOT_FOUND_MSG = "Epic '%s' was not found.";
    private static final String TEAM_NOT_FOUND_MSG = "Team '%s' was not found.";
    private static final String HAS_TICKETS_MSG =
            "Epic '%s' cannot be deleted while tickets reference it; remove them first.";
    // Deterministic list order: creation time, ties broken by id (stable under renames/updates).
    private static final Sort CREATED_THEN_ID = Sort.by("createdAt").and(Sort.by("id"));

    private final EpicRepository epicRepository;
    private final TeamRepository teamRepository;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public List<EpicResponse> list(@Nullable UUID teamId) {
        List<Epic> epics = (teamId != null)
                ? epicRepository.findByTeamId(teamId, CREATED_THEN_ID)
                : epicRepository.findAll(CREATED_THEN_ID);
        return epics.stream().map(EpicResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EpicResponse get(UUID id) {
        return EpicResponse.from(require(id));
    }

    public EpicResponse create(UUID teamId, String title, @Nullable String description) {
        if (!teamRepository.existsById(teamId)) {
            throw new NotFoundException(TEAM_NOT_FOUND_MSG.formatted(teamId));
        }
        Epic epic = new Epic();
        epic.setTeamId(teamId);
        epic.setTitle(normalizeTitle(title));
        epic.setDescription(normalizeDescription(description));
        Epic saved;
        try {
            saved = epicRepository.saveAndFlush(epic);
        } catch (DataIntegrityViolationException ex) {
            // The team was deleted between the pre-check and the insert flush; the FK violation is a
            // vanished parent — surface the same 404 the pre-check would have returned.
            LOG.warn("Epic insert race: team deleted before the flush; translating to 404");
            throw new NotFoundException(TEAM_NOT_FOUND_MSG.formatted(teamId));
        }
        LOG.info("Epic created: epicId={}, teamId={}", saved.getId(), teamId);
        return EpicResponse.from(saved);
    }

    public EpicResponse update(UUID id, String title, @Nullable String description) {
        Epic epic = require(id);
        // Only title and description are touched; the epic's team is fixed at creation.
        epic.setTitle(normalizeTitle(title));
        epic.setDescription(normalizeDescription(description));
        return EpicResponse.from(epicRepository.saveAndFlush(epic));
    }

    public void delete(UUID id) {
        Epic epic = require(id);
        if (ticketRepository.existsByEpicId(id)) {
            throw new ConflictException(HAS_TICKETS_MSG.formatted(id));
        }
        try {
            epicRepository.delete(epic);
            // Flush inside the try so a concurrently inserted referencing ticket surfaces here as a
            // DataIntegrityViolationException rather than at commit, outside the catch.
            epicRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            LOG.warn("Epic delete race: foreign-key reference rejected the delete; translating to conflict");
            throw new ConflictException(HAS_TICKETS_MSG.formatted(id));
        }
        LOG.info("Epic deleted: epicId={}", id);
    }

    private Epic require(UUID id) {
        return epicRepository.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(id)));
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

    @Nullable
    private static String normalizeDescription(@Nullable String description) {
        if (StringUtils.isBlank(description)) {
            return null;
        }
        String trimmed = description.strip();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException(DESCRIPTION_TOO_LONG_MSG);
        }
        return trimmed;
    }
}
