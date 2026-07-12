package com.bovae.yaj.domain.repository;

import com.bovae.yaj.domain.model.Ticket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByTeamId(UUID teamId);

    boolean existsByEpicId(UUID epicId);

    List<Ticket> findByTeamId(UUID teamId);
}
