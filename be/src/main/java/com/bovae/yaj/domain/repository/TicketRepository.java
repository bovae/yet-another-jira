package com.bovae.yaj.domain.repository;

import com.bovae.yaj.domain.model.Ticket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByTeamId(UUID teamId);

    boolean existsByEpicId(UUID epicId);

    List<Ticket> findByTeamId(UUID teamId);

    /**
     * Board read query: a team's tickets, optionally narrowed by type, epic, and a pre-escaped
     * lower-cased title LIKE pattern (all AND-combined; a {@code null} argument disables that filter),
     * ordered most-recently-modified first. Backed by the {@code (team_id, state)} index. The pattern
     * is lower-cased by the caller — the column side is {@code LOWER(title)} — so no {@code LOWER()} is
     * applied to the bind parameter (a null bind would otherwise resolve to {@code lower(bytea)}).
     *
     * <p>The nullable {@code String} params are wrapped in {@code CAST(... AS String)} inside their
     * {@code IS NULL} checks: the runtime datasource sets {@code stringtype=unspecified} (needed for the
     * {@code citext} columns), so pgjdbc sends a bare {@code String} bind with an unknown type and
     * Postgres cannot type a standalone {@code $n IS NULL} — the cast supplies the type. The
     * {@code = :type} / {@code LIKE :titlePattern} uses are already typed by their column side.
     */
    @Query(
            """
            SELECT t FROM Ticket t
            WHERE t.teamId = :teamId
              AND (CAST(:type AS String) IS NULL OR t.type = :type)
              AND (:epicId IS NULL OR t.epicId = :epicId)
              AND (CAST(:titlePattern AS String) IS NULL OR LOWER(t.title) LIKE :titlePattern ESCAPE '\\')
            ORDER BY t.modifiedAt DESC
            """)
    List<Ticket> findBoardTickets(
            @Param("teamId") UUID teamId,
            @Param("type") @Nullable String type,
            @Param("epicId") @Nullable UUID epicId,
            @Param("titlePattern") @Nullable String titlePattern);
}
