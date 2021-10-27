package com.grmkris.btcrbtcswapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@Service
@AllArgsConstructor
@Slf4j
public class BalancingService {

    private LndService lndService;
    private RskService rskService;
    private BtcService btcService;

    public void startBalanceChecker(){
        log.info("Starting balance checker and checking every 1000 seconds");
        TimerTask newTransactionProber = new TimerTask() {
            @Override
            public void run() {
                balanceChecker();
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 0L, 1000000L);
    }

    private void balanceChecker(){
        log.info("Checking balance");
        BigDecimal lndAmount = new BigDecimal(lndService.getLightningBalance());
        BigDecimal rskAmount = new BigDecimal(rskService.getRskBalance());

        // TODO parameterize ratio for balancing
        if (lndAmount.compareTo(rskAmount) < 0){
            if (lndAmount.divide(lndAmount.add(rskAmount), 2, RoundingMode.UP).compareTo(BigDecimal.valueOf(0.4)) == -1){
                BigDecimal loopAmount = lndAmount.add(rskAmount).divide(BigDecimal.valueOf(2), 2, RoundingMode.UP).subtract(lndAmount);
                log.info("Lightning balance below 30%, initiating loop in, amount: {} sats", loopAmount);
                startLoopInProcess(loopAmount);
            }
        } else if (lndAmount.compareTo(rskAmount) > 0) {
            if (rskAmount.divide(lndAmount.add(rskAmount),2, RoundingMode.UP).compareTo(BigDecimal.valueOf(0.4)) == -1){
                // Calulating amount to loopout:
                // (lndAmount + rskAmount) / 2 - rskAmount
                BigDecimal loopAmount = lndAmount.add(rskAmount).divide(BigDecimal.valueOf(2), 2, RoundingMode.UP).subtract(rskAmount);
                log.info("RSK balance below 30%, initiating loop out, amount: {} sats", loopAmount);
                //lndService.initiateLoopOut(loopAmount.unscaledValue());
            }
        } else {
            log.info("No need for balancing, lnd balance: {}, rsk balance: {}", lndAmount, rskAmount);
        }
    }

    /* when loopin is initiated this service should:
    1.  This service sends funds to BTCSwapContract to Transfer funds from rbtc wallet to RSK contract "rsk paired bitcoin wallet"
    2.  BtcService is monitoring blockchain for any new transaction - Monitor "rsk paired bitcoin wallet" for new transactions from RSK contract
        -  When new transaction arrives it and is related to LOOPIN it should send new transaction to lightning wallet by calling lndservice.getnewOnchainaddress
    4.  It should monitor lnd for new transactions and once it is confirmed it should initiate a loopin
    5. it shoudl monitor the loopin swap to see if it was sucesfull
    */
    private void startLoopInProcess(BigDecimal loopAmount){
         // TODO UNCOMMENT WHEN DONE WITH BItCOIN SIDE
        // rskService.sendToBTCSwapContract(loopAmount.multiply(BigDecimal.TEN));
    }

    /*
    https://developers.rsk.co/rsk/rbtc/conversion/networks/testnet/
     */
    private void startLoopOutProcess(BigDecimal loopAmount){
        lndService.initiateLoopOut(loopAmount.toBigInteger());
    }
}
