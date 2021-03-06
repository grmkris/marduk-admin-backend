package com.grmkris.mardukadmin;

import com.grmkris.mardukadmin.bitfinex.BitfinexHandler;
import com.grmkris.mardukadmin.bitfinex.BitfinexWatcher;
import com.grmkris.mardukadmin.db.balancer.BalancinModeEnum;
import com.grmkris.mardukadmin.db.balancer.BalancingStatus;
import com.grmkris.mardukadmin.db.balancer.BalancingStatusEnum;
import com.grmkris.mardukadmin.db.balancer.BalancingStatusRepository;
import com.grmkris.mardukadmin.notification.MailgunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

    @Value("${balancing.mode}")
    private BalancinModeEnum balancingMode; // none, powpeg, bitfinex

    private final LndHandler lndHandler;
    private final RskHandler rskHandler;
    private final BlockchainWatcher blockchainWatcher;
    private final BitfinexWatcher bitfinexWatcher;
    private final BalancingStatusRepository balancingStatusRepository;
    private final BitfinexHandler bitfinexHandler;
    private final MailgunService mailgunService;

    @Override
    public void run(String... args) {
        if (balancingStatusRepository.findById(1L).isEmpty()) {
            BalancingStatus balancingStatus = BalancingStatus.builder().id(1L).balancingStatus(BalancingStatusEnum.IDLE).balancinModeEnum(balancingMode).build();
            balancingStatusRepository.saveAndFlush(balancingStatus);
        }
        mailgunService.sendEmail("Balance Coordinator Started", "Balance Coordinator Started");
        this.startBalanceChecker();
        if (!balancingMode.equals(BalancinModeEnum.none)) {
            if (balancingMode.equals(BalancinModeEnum.powpeg)) {
                blockchainWatcher.startBTCTransactionWatcher();
                blockchainWatcher.startLNDTransactionWatcher();
            }
            if (balancingMode.equals(BalancinModeEnum.bitfinex)) {
                bitfinexWatcher.startBitfinexTransactionWatcher();
            }
        }
    }

    public void startBalanceChecker() {
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
        if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.IDLE) && !balancingMode.equals(BalancinModeEnum.none)) {
            log.info("Balance status: {};  Checking balance", balancingStatusRepository.findById(1L).get().getBalancingStatus());
            BigDecimal lndAmount = new BigDecimal(lndHandler.getLightningBalance());
            BigDecimal rskAmount = new BigDecimal(rskHandler.getRskBalance());
            log.info("LND amount: {}; RSK amount: {}", lndAmount, rskAmount);

            BigDecimal ratio = lndAmount.add(rskAmount).divide(BigDecimal.valueOf(3), 2, RoundingMode.UP);
            BigDecimal lndBalancedAmount = ratio.multiply(BigDecimal.valueOf(2));
            BigDecimal rskBalancedAmount = ratio;
            if (lndAmount.compareTo(lndBalancedAmount) < 0){
                if (lndAmount.divide(lndBalancedAmount, 2, RoundingMode.UP).compareTo(BigDecimal.valueOf(0.65)) < 0){
                    BigDecimal amount = lndBalancedAmount.subtract(lndAmount);
                    log.info("Lightning balance below 30%, initiating {} PEGIN, amount: {} sats", balancingMode, amount);
                    mailgunService.sendEmail("rbtc-btc-swapper Lightning balance below 30%","Lightning balance below 30%, initiating " + balancingMode + " PEGIN, amount: " + amount + " sats");
                    if (balancingMode.equals(BalancinModeEnum.powpeg)) {
                        startPeginProcess(amount);
                    }
                    else if (balancingMode.equals(BalancinModeEnum.bitfinex)) {
                        startBitfinexPeginProcess(amount);
                    }
                }
                else {
                    log.info("No need for balancing, lnd balance: {}, rsk balance: {}", lndAmount, rskAmount);
                }
            } else if (lndAmount.compareTo(lndBalancedAmount) > 0) {
                if (rskAmount.divide(rskBalancedAmount,2, RoundingMode.UP).compareTo(BigDecimal.valueOf(0.65)) < 0){
                    // Calulating amount to loopout:
                    // (lndAmount + rskAmount) / 2 - rskAmount
                    BigDecimal amount = rskBalancedAmount.subtract(rskAmount);
                    log.info("RSK balance below 30%, initiating {} PEGOUT, amount: {} sats", balancingMode, amount);
                    mailgunService.sendEmail("rbtc-btc-swapper RSK balance below 30%","Lightning balance below 30%, initiating " + balancingMode + " PEGOUT, amount: " + amount + " sats");
                    if (balancingMode.equals(BalancinModeEnum.powpeg)) {
                        startPegoutProcess(amount);
                    }
                    else if (balancingMode.equals(BalancinModeEnum.bitfinex)) {
                        startBitfinexPegoutProcess(amount);
                    }
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
        -  When new transaction arrives it and is related to loopin it should send new transaction to lightning wallet by calling lndservice.getnewOnchainaddress
    4.  It should monitor lnd for new transactions and once it is confirmed it should initiate a loopin
    5. it should monitor the loopin swap to see if it was successfully completed
    */
    private void startPeginProcess(BigDecimal loopAmount){
        var balancingStatus = balancingStatusRepository.findById(1L).get();
        balancingStatus.setBalancingStatus(BalancingStatusEnum.PEGIN);
        balancingStatus.setAmount(loopAmount);
        balancingStatus.setStatus("Starting loopin process, sending to rskBtcBtcSwapContract");
        balancingStatusRepository.save(balancingStatus);
        rskHandler.sendToRskBtcBridge(loopAmount.multiply(BigDecimal.TEN));
    }

    /*
    https://developers.rsk.co/rsk/rbtc/conversion/networks/testnet/
     */
    /*
    currently limited to max 500k satoshi loopouts
     */
    private void startPegoutProcess(BigDecimal loopAmount){
        var balancingStatus = balancingStatusRepository.findById(1L).get();
        balancingStatus.setBalancingStatus(BalancingStatusEnum.PEGOUT);
        balancingStatusRepository.save(balancingStatus);
        if (loopAmount.toBigInteger().compareTo(BigInteger.valueOf(500000L) ) > 0 ){
            loopAmount = BigDecimal.valueOf(500000L);
        }
        lndHandler.initiateLoopOut(loopAmount.toBigInteger(), btcPublicKey);
    }

    private void startBitfinexPeginProcess(BigDecimal amount) {
        var balancingStatus = balancingStatusRepository.findById(1L).get();
        balancingStatus.setBalancingStatus(BalancingStatusEnum.PEGIN);
        balancingStatus.setAmount(amount);
        balancingStatus.setStatus("Starting loopin process, sending to bitfinex");
        balancingStatusRepository.save(balancingStatus);
        String bitfinexAddress = null;
        try {
            bitfinexAddress = bitfinexHandler.getRBTCAddress();
        } catch (IOException e) {
            balancingStatus.setStatus("Error retrieving bitfinex address");
            balancingStatusRepository.save(balancingStatus);
            e.printStackTrace();
        }
        rskHandler.sendRBTCtoAddress(amount, bitfinexAddress);
    }

    private void startBitfinexPegoutProcess(BigDecimal amount) {
        var balancingStatus = balancingStatusRepository.findById(1L).get();
        balancingStatus.setBalancingStatus(BalancingStatusEnum.PEGOUT);
        balancingStatusRepository.save(balancingStatus);
        String bitfinexInvoice = null;
        try {
            var amountInteger = amount.toBigInteger().toString();
            var lnAmount = BigDecimal.valueOf(Long.valueOf(amountInteger)).divide(BigDecimal.valueOf(100000000));
            log.info("Retrieving bitfinex invoice, amount: {}", lnAmount);

            bitfinexInvoice = bitfinexHandler.getLightningInvoice(lnAmount);
            log.info("Invoice: {}", bitfinexInvoice);
        } catch (IOException e) {
            e.printStackTrace();
        }
        lndHandler.payInvoice(bitfinexInvoice);
    }
}
