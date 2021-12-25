package com.grmkris.mardukadmin.db.balancer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BalancingStatus {
    @Id
    @Column(name = "id", nullable = false)
    private Long id;
    private BalancingStatusEnum balancingStatus = BalancingStatusEnum.IDLE; // idle, loopin, loopout
    private BalancinModeEnum balancinModeEnum;
    private BigDecimal amount;
    private String lastError;
    private String status;
}
