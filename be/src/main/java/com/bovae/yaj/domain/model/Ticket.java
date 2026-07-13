package com.bovae.yaj.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SourceType;
import org.hibernate.generator.EventType;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@ToString
@SuppressWarnings("NullAway.Init")
public class Ticket {

    @Id
    @Generated
    @ColumnDefault("gen_random_uuid()")
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Nullable
    @Column(name = "epic_id")
    private UUID epicId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Generated
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // DB clock (current_timestamp), same source as created_at's now() default, so a fresh row has
    // modified_at == created_at instead of the old @UpdateTimestamp VM clock drifting from the DB.
    @CurrentTimestamp(
            event = {EventType.INSERT, EventType.UPDATE},
            source = SourceType.DB)
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;
}
