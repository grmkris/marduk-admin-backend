package com.grmkris.mardukadmin.db.boltz.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;

@Entity(name = "swaps")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Swap {
    @Id
    @Column(name = "id", nullable = false)
    private String id;

    private Long keyIndex;
    private String redeemScript;

    private BigDecimal fee;
    private BigDecimal routingFee;
    private BigDecimal minerFee;

    @NonNull
    private String pair;
    @NonNull
    private BigDecimal orderSide;

    @NonNull
    private String status;
    private String failureReason;
    @NonNull
    private String preimageHash;
    private String invoice;

    private boolean acceptZeroConf;
    @NonNull
    private BigDecimal timeoutBlockHeight;
    private BigDecimal rate;
    private BigDecimal expectedAmount;
    private BigDecimal onchainAmount;
    @NonNull
    private String lockupAddress;
    private String lockupTransactionId;
    private BigDecimal lockupTransactionVout;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
