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
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@ToString
@SuppressWarnings("NullAway.Init")
public class User {

    @Id
    @Generated
    @ColumnDefault("gen_random_uuid()")
    private UUID id;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    @ToString.Exclude
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Nullable
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Generated
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;
}
