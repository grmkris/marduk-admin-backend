package com.grmkris.mardukadmin.db.boltz.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;

@Entity(name = "reverseSwaps")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReverseSwap {
    @Id
    @Column(name = "id", nullable = false)
    private String id;

    private String lockupAddress;
    private Long keyIndex;
    private String redeemScript;

    private String claimAddress;

    private BigDecimal fee;
    private BigDecimal minerFee;

    @NonNull
    private String pair;
    @NonNull
    private BigDecimal orderSide;

    @NonNull
    private String status;
    private String failureReason;

    @NonNull
    private BigDecimal timeoutBlockHeight;
    @NonNull
    private String invoice;

    private String minerFeeInvoice;
    private String minerFeeInvoicePreimage;
    private BigDecimal minerFeeOnchainAmount;

    private String preimageHash;
    private String preimage;

    @NonNull
    private String onchainAmount;
    private String transactionId;
    private BigDecimal transactionVout;
}
