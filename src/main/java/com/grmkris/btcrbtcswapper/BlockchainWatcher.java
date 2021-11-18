package com.grmkris.btcrbtcswapper;

import com.grmkris.btcrbtcswapper.db.BalancingStatus;
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

    // THIS IS CHECKING BTC WALLET BALANCE THAT IS DERVIED FROM RSK WALLET
    public void startBTCTransactionWatcher() {
        btcWalletBalance = btcHandler.getBtcWalletBalance();
        TimerTask newTransactionProber = new TimerTask() {
            public void run() {
                log.info("Balancing status: " + balancingStatusRepository.findById(1L).get().getBalancingStatus());
                if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.IDLE)) {
                    return;
                }
                var newBtcWalletBalance = btcHandler.getBtcWalletBalance();
                var amountToSend= newBtcWalletBalance.subtract(btcWalletBalance);
                if (newBtcWalletBalance.compareTo(btcWalletBalance) > 0){
                    if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.PEGOUT)) {
                        log.info("PEGOUT: new BTC transaction detected, amount {}", amountToSend);
                        btcHandler.sendToLightningNode(amountToSend);
                        log.info("Sent funds to lightning node");
                    } else if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.PEGIN)) {
                        log.info("PEGIN: new BTC transaction detected, amount {}", amountToSend);
                        btcHandler.sendToBtcRskFederationAddress(amountToSend);
                        log.info("Sent funds to RSK federation address, LOOPOUT complete");
                        // since this is last step of the loop out process we put service back to idle
                        BalancingStatus balancingStatus = balancingStatusRepository.findById(1L).get();
                        balancingStatus.setBalancingStatus(BalancingStatusEnum.IDLE);
                        balancingStatusRepository.save(balancingStatus);
                        log.info("Returning balancing status to IDLE");
                        // https://developers.rsk.co/rsk/rbtc/conversion/networks/mainnet/
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
    // THIS IS CHECKING ONCHAIN LIGHTNING WALLET BALANCE
    public void startLNDTransactionWatcher(){
        lndOnchainWalletBalance = lndHandler.getLightningOnChainBalance();
        TimerTask newTransactionProber = new TimerTask() {
            public void run() {
                log.info("Balancing status: " + balancingStatusRepository.findById(1L).get().getBalancingStatus());
                if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.IDLE) || balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.PEGIN)) {
                    return;
                }
                var newlndOnchainWalletBalance = lndHandler.getLightningOnChainBalance();
                if (newlndOnchainWalletBalance.compareTo(lndOnchainWalletBalance) > 0){
                    var amount = newlndOnchainWalletBalance.subtract(lndOnchainWalletBalance);
                    log.info("Received new onchain transaction to LND wallet, amount: {} ", lndOnchainWalletBalance);
                    var response = lndHandler.initiateLoopIn(amount);
                    log.info("Sent loop in request through LND, amount: {}", amount);
                    log.info(response);
                    // loopin is last step of the swap, so we put service back to idle
                    BalancingStatus balancingStatus = balancingStatusRepository.findById(1L).get();
                    balancingStatus.setBalancingStatus(BalancingStatusEnum.IDLE);
                    balancingStatusRepository.save(balancingStatus);
                    log.info("Returned balancing status to IDLE");
                }
                lndOnchainWalletBalance = newlndOnchainWalletBalance;
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 10000L, 100000L);

    }

}
