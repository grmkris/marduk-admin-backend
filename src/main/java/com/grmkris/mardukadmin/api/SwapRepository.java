package com.grmkris.mardukadmin.api;

import com.grmkris.mardukadmin.db.boltz.Swap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SwapRepository extends JpaRepository<Swap, Long> {
}
