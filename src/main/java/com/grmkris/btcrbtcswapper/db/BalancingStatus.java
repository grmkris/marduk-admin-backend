package com.grmkris.btcrbtcswapper.db;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalancingStatus {
    @Id
    @Column(name = "id", nullable = false)
    private Long id;
    private BalancingStatusEnum balancingStatus = BalancingStatusEnum.IDLE; // idle, loopin, loopout

}
