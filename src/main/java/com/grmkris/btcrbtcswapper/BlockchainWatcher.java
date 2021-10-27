package com.grmkris.btcrbtcswapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@RequiredArgsConstructor
@Component
public class BlockchainWatcher {

    private final BalanceStatus balanceStatus;
    private final LndHandler lndHandler;
    private final BtcHandler btcHandler;

    private BigDecimal btcWalletBalance;
    private BigInteger lndOnchainWalletBalance;

    public void startBTCTransactionWatcher() throws MalformedURLException {
        btcWalletBalance = btcHandler.getBtcWalletBalance();
        TimerTask newTransactionProber = new TimerTask() {
            public void run() {
                if (balanceStatus.getBalancingStatus().equals("idle")) {
                    return;
                }
                log.info("Service status: {} Probing for new Bitcoin transaction every 100 seconds", balanceStatus.getBalancingStatus());
                var newBtcWalletBalance = btcHandler.getBtcWalletBalance();
                var amountToSend= newBtcWalletBalance.subtract(btcWalletBalance);
                if (newBtcWalletBalance.compareTo(btcWalletBalance) > 0){
                    if (balanceStatus.getBalancingStatus().equals("loopin")) {
                        btcHandler.sendToLightningNode(amountToSend);
                    } else if (balanceStatus.getBalancingStatus().equals("loopout")){
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
                if (balanceStatus.getBalancingStatus().equals("idle")) {
                    return;
                }
                log.info("Service status: {} Probing for new Lightning onchain transaction every 100 seconds", balanceStatus.getBalancingStatus());
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
