package com.grmkris.mardukadmin.db.balancer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BalancingStatusRepository extends JpaRepository<BalancingStatus, Long> {

    Optional<BalancingStatus> findById(Long id);
}
