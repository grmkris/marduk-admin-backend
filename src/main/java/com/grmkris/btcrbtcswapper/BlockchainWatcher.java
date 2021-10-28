package com.grmkris.btcrbtcswapper;

import com.grmkris.btcrbtcswapper.db.BalancingStatusEnum;
import com.grmkris.btcrbtcswapper.db.BalancingStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@RequiredArgsConstructor
@Component
public class BlockchainWatcher {

    private final BalancingStatusRepository balancingStatusRepository;
    private final LndHandler lndHandler;
    private final BtcHandler btcHandler;

    private BigDecimal btcWalletBalance;
    private BigInteger lndOnchainWalletBalance;

    public void startBTCTransactionWatcher() throws MalformedURLException {
        btcWalletBalance = btcHandler.getBtcWalletBalance();
        TimerTask newTransactionProber = new TimerTask() {
            public void run() {
                if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.IDLE)) {
                    return;
                }
                log.info("Service status: {} Probing for new Bitcoin transaction every 100 seconds", balancingStatusRepository.findById(1L).get().getBalancingStatus());
                var newBtcWalletBalance = btcHandler.getBtcWalletBalance();
                var amountToSend= newBtcWalletBalance.subtract(btcWalletBalance);
                if (newBtcWalletBalance.compareTo(btcWalletBalance) > 0){
                    if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.LOOPIN)) {
                        btcHandler.sendToLightningNode(amountToSend);
                    } else if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.LOOPOUT)) {
                        btcHandler.sendToBtcRskFederationAddress(amountToSend);
                    } else {
                        log.warn("Received transaction to bitcoin private key, while idling");
                    }
                }
                btcWalletBalance = newBtcWalletBalance;
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 10000L, 100000L);
    }

    // periodically check lightning wallet balance, if it increases send the dela amount through loopin
    public void startLNDTransactionWatcher(){
        lndOnchainWalletBalance = lndHandler.getLightningOnChainBalance();
        TimerTask newTransactionProber = new TimerTask() {
            public void run() {
                if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.IDLE)) {
                    return;
                }
                log.info("Service status: {} Probing for new Lightning onchain transaction every 100 seconds", balancingStatusRepository.findById(1L).get().getBalancingStatus());
                var newlndOnchainWalletBalance = lndHandler.getLightningOnChainBalance();
                if (newlndOnchainWalletBalance.compareTo(lndOnchainWalletBalance) > 0){
                    log.info("Received new onchain transaction to LND wallet, amount: {} ", lndOnchainWalletBalance);
                    lndHandler.initiateLoopIn(newlndOnchainWalletBalance.subtract(lndOnchainWalletBalance));
                }
                lndOnchainWalletBalance = newlndOnchainWalletBalance;
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 10000L, 100000L);

    }

}
