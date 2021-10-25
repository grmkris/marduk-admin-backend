package com.grmkris.btcrbtcswapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Timer;
import java.util.TimerTask;

@Service
@AllArgsConstructor
@Slf4j
public class BalancingService {

    private LndService lndService;
    private RskService rskService;

    public void startBalanceChecker(){
        log.info("Starting balance checker and checking every 10 seconds");
        TimerTask newTransactionProber = new TimerTask() {
            @Override
            public void run() {
                balanceChecker();
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 0L, 10000L);
    }

    private void balanceChecker(){
        log.info("Checking balance");
        BigDecimal lndAmount = new BigDecimal(lndService.getLightningBalance());
        BigDecimal rskAmount = new BigDecimal(rskService.getRskBalance());

        if (lndAmount.compareTo(rskAmount) < 0){
            if (lndAmount.divide(lndAmount.add(rskAmount), 2, RoundingMode.UP).compareTo(BigDecimal.valueOf(0.3)) == -1){
                log.info("Lightning balance below 30%, initiating loop in");
            }
        } else if (lndAmount.compareTo(rskAmount) > 0) {
            if (rskAmount.divide(lndAmount.add(rskAmount),2, RoundingMode.UP).compareTo(BigDecimal.valueOf(0.3)) == -1){
                // Calulating amount to loopout:
                // (lndAmount + rskAmount) / 2 - rskAmount
                BigDecimal loopAmount = lndAmount.add(rskAmount).divide(BigDecimal.valueOf(2), 2, RoundingMode.UP).subtract(rskAmount);
                log.info("RSK balance below 30%, initiating loop out, amount: {}", loopAmount);
                //lndService.initiateLoopOut(loopAmount.unscaledValue());
            }
        } else {
            log.info("No need for balancing, lnd balance: {}, rsk balance: {}", lndAmount, rskAmount);
        }
    }
}
