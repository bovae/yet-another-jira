package com.bovae.yaj.teams;

import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.ConflictException;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.web.dto.TeamResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TeamService {

    private static final String NAME_REQUIRED_MSG = "A team name is required.";
    private static final String NAME_TAKEN_MSG = "A team named '%s' already exists.";
    private static final String NOT_FOUND_MSG = "Team '%s' was not found.";
    private static final String HAS_REFERENCES_MSG =
            "Team '%s' cannot be deleted while it has epics or tickets; remove them first.";

    private final TeamRepository teamRepository;
    private final EpicRepository epicRepository;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public List<TeamResponse> list() {
        return teamRepository.findAll().stream().map(TeamResponse::from).toList();
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
        Team saved = teamRepository.saveAndFlush(team);
        LOG.info("Team created: teamId={}", saved.getId());
        return TeamResponse.from(saved);
    }

    public TeamResponse rename(UUID id, String name) {
        String trimmed = normalize(name);
        Team team = require(id);
        if (teamRepository.existsByNameAndIdNot(trimmed, id)) {
            throw new ConflictException(NAME_TAKEN_MSG.formatted(trimmed));
        }
        team.setName(trimmed);
        return TeamResponse.from(teamRepository.saveAndFlush(team));
    }

    public void delete(UUID id) {
        Team team = require(id);
        if (epicRepository.existsByTeamId(id) || ticketRepository.existsByTeamId(id)) {
            throw new ConflictException(HAS_REFERENCES_MSG.formatted(id));
        }
        teamRepository.delete(team);
        LOG.info("Team deleted: teamId={}", id);
    }

    private Team require(UUID id) {
        return teamRepository.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG.formatted(id)));
    }

    private static String normalize(String name) {
        if (StringUtils.isBlank(name)) {
            throw new ValidationException(NAME_REQUIRED_MSG);
        }
        return name.strip();
    }
}
