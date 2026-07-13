package com.bovae.yaj.domain.repository;

import com.bovae.yaj.domain.model.Ticket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByTeamId(UUID teamId);

    boolean existsByEpicId(UUID epicId);

    List<Ticket> findByTeamId(UUID teamId, Sort sort);

    /**
     * Board read query: a team's tickets, optionally narrowed by type, epic, and a pre-escaped title
     * LIKE pattern (all AND-combined; a {@code null} argument disables that filter), ordered
     * most-recently-modified first with an {@code id} tie-break so equal {@code modified_at} rows keep a
     * stable order. Backed by the {@code (team_id, state)} index. Case folding is done in the database on
     * both sides — {@code LOWER(title) LIKE LOWER(:titlePattern)} — so folding matches the DB's collation
     * rather than the JVM's; the caller passes the pattern in its original case, escaping only.
     *
     * <p>The nullable {@code String} params are wrapped in {@code CAST(... AS String)}: the runtime
     * datasource sets {@code stringtype=unspecified} (needed for the {@code citext} columns), so pgjdbc
     * sends a bare {@code String} bind with an unknown type and Postgres cannot type a standalone
     * {@code $n IS NULL} or {@code LOWER($n)} — the cast supplies the type. The {@code = :type} use is
     * already typed by its column side.
     */
    @Query(
            """
            SELECT t FROM Ticket t
            WHERE t.teamId = :teamId
              AND (CAST(:type AS String) IS NULL OR t.type = :type)
              AND (:epicId IS NULL OR t.epicId = :epicId)
              AND (CAST(:titlePattern AS String) IS NULL
                   OR LOWER(t.title) LIKE LOWER(CAST(:titlePattern AS String)) ESCAPE '\\')
            ORDER BY t.modifiedAt DESC, t.id
            """)
    List<Ticket> findBoardTickets(
            @Param("teamId") UUID teamId,
            @Param("type") @Nullable String type,
            @Param("epicId") @Nullable UUID epicId,
            @Param("titlePattern") @Nullable String titlePattern);
}
