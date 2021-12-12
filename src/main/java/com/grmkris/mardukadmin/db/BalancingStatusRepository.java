package com.grmkris.mardukadmin.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BalancingStatusRepository extends JpaRepository<BalancingStatus, Long> {

    Optional<BalancingStatus> findById(Long id);
    BalancingStatus getById(Long id);
}
