package com.grmkris.mardukadmin.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class Balance {
    private String walletName;
    private BigDecimal value;
    private String address;
}
