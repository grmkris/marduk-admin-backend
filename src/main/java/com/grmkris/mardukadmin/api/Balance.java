package com.grmkris.mardukadmin.api;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;

@Getter
@Builder
public class Balance {
    private String walletName;
    private BigInteger value;
}
