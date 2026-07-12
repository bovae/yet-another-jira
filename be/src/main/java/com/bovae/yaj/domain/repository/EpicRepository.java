package com.bovae.yaj.domain.repository;

import com.bovae.yaj.domain.model.Epic;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpicRepository extends JpaRepository<Epic, UUID> {

    boolean existsByTeamId(UUID teamId);

    List<Epic> findByTeamId(UUID teamId);
}
