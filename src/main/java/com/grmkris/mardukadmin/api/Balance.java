package com.grmkris.mardukadmin.api;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class Balance {
    private String walletName;
    private BigDecimal value;
}
