package com.grmkris.btcrbtcswapper;

import com.grmkris.btcrbtcswapper.bitfinex.BitfinexHandler;
import com.grmkris.btcrbtcswapper.db.BalancingStatus;
import com.grmkris.btcrbtcswapper.db.BalancingStatusEnum;
import com.grmkris.btcrbtcswapper.db.BalancingStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Timer;
import java.util.TimerTask;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceCoordinator implements CommandLineRunner {

    @Value("${btc.wallet.public.key}")
    private String btcPublicKey;

    @Value("${balancing.enabled}")
    private Boolean balancingEnabled;

    private final LndHandler lndHandler;
    private final RskHandler rskHandler;
    private final BlockchainWatcher blockchainWatcher;
    private final BalancingStatusRepository balancingStatusRepository;
    private final BitfinexHandler bitfinexHandler;

    @Override
    public void run(String... args) throws Exception {
        //blockchainWatcher.startLNDTransactionWatcher();
        //blockchainWatcher.startBTCTransactionWatcher();

        if (!balancingStatusRepository.findById(1L).isPresent()){
            BalancingStatus balancingStatus = new BalancingStatus(1L, BalancingStatusEnum.IDLE);
            balancingStatusRepository.saveAndFlush(balancingStatus);
        }

        if (balancingEnabled) {
            this.startBalanceChecker();
        }
    }

    public void startBalanceChecker() throws Exception {
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
        if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.IDLE)) {
            log.info("Balance status: {};  Checking balance", balancingStatusRepository.findById(1L).get().getBalancingStatus());
            BigDecimal lndAmount = new BigDecimal(lndHandler.getLightningBalance());
            BigDecimal rskAmount = new BigDecimal(rskHandler.getRskBalance());

            // TODO parameterize ratio for balancing
            if (lndAmount.compareTo(rskAmount) < 0){
                if (lndAmount.divide(lndAmount.add(rskAmount), 2, RoundingMode.UP).compareTo(BigDecimal.valueOf(0.4)) == -1){
                    BigDecimal loopAmount = lndAmount.add(rskAmount).divide(BigDecimal.valueOf(2), 2, RoundingMode.UP).subtract(lndAmount);
                    log.info("Lightning balance below 30%, initiating rsk PEGOUT, amount: {} sats", loopAmount);
                    startLoopInProcess(loopAmount);
                }
                else {
                    log.info("No need for balancing, lnd balance: {}, rsk balance: {}", lndAmount, rskAmount);
                }
            } else if (lndAmount.compareTo(rskAmount) > 0) {
                if (rskAmount.divide(lndAmount.add(rskAmount),2, RoundingMode.UP).compareTo(BigDecimal.valueOf(0.4)) == -1){
                    // Calulating amount to loopout:
                    // (lndAmount + rskAmount) / 2 - rskAmount
                    BigDecimal loopAmount = lndAmount.add(rskAmount).divide(BigDecimal.valueOf(2), 2, RoundingMode.UP).subtract(rskAmount);
                    log.info("RSK balance below 30%, initiating rsk PEGIN, amount: {} sats", loopAmount);
                    startLoopOutProcess(loopAmount);
                }
                else {
                    log.info("No need for balancing, lnd balance: {}, rsk balance: {} ", lndAmount, rskAmount);
                }
            }
        } else {
            log.info("Balancing status: " + balancingStatusRepository.findById(1L).get().getBalancingStatus());
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
        var balancingStatus = balancingStatusRepository.findById(1L).get();
        balancingStatus.setBalancingStatus(BalancingStatusEnum.PEGIN);
        balancingStatusRepository.save(balancingStatus);
        rskHandler.sendToRskBtcBridge(loopAmount.multiply(BigDecimal.TEN));
    }

    /*
    https://developers.rsk.co/rsk/rbtc/conversion/networks/testnet/
     */
    /*
    currently limited to max 500k satoshi loopouts
     */
    private void startLoopOutProcess(BigDecimal loopAmount){
        var balancingStatus = balancingStatusRepository.findById(1L).get();
        balancingStatus.setBalancingStatus(BalancingStatusEnum.PEGOUT);
        balancingStatusRepository.save(balancingStatus);
        if (loopAmount.toBigInteger().compareTo(BigInteger.valueOf(500000L) ) > 0 ){
            loopAmount = BigDecimal.valueOf(500000L);
        }
        lndHandler.initiateLoopOut(loopAmount.toBigInteger(), btcPublicKey);
    }
}
