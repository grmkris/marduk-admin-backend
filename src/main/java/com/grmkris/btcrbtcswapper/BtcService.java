package com.grmkris.btcrbtcswapper;

import lombok.AllArgsConstructor;
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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@Component
@Slf4j
@RequiredArgsConstructor
public class BtcService {

    @Value("${btc.wallet.private.key}")
    private String btcPrivateKey;

    @Value("${btc.service.url}")
    private String btcServiceUrl;

    @Value("${btc.rpc.cookie}")
    private String btcRpcCookie;

    private BitcoindRpcClient bitcoindRpcClient;
    private final LndService lndService;
    private BigDecimal btcWalletBalance;
    private final BalancingService balancingService;
    private final RskService rskService;

    public void run(String... args) throws MalformedURLException {
        URL url;
        if (btcServiceUrl.contains("https")) {
            url = new URL("https://" + btcRpcCookie + "@" + btcServiceUrl.substring(8));
        } else {
            url = new URL("http://" + btcRpcCookie + "@" + btcServiceUrl);
        }
        bitcoindRpcClient = new BitcoinJSONRPCClient(url);
        //bitcoindRpcClient.importPrivKey(btcPrivateKey, "boltz-testnet-btc-wallet");
        startNewTransactionProber();

    }

    public void startNewTransactionProber(){
        log.info("Start probing for Bitcoin new transaction every 10 seconds");
        btcWalletBalance = this.getBtwWalletBalance();
        TimerTask newTransactionProber = new TimerTask() {
            public void run() {
                var newBtcWalletBalance = getBtwWalletBalance();
                if (newBtcWalletBalance.compareTo(btcWalletBalance) > 0){
                    if (balancingService.getBalancingStatus().equals("loopin")) {
                        sendToLightningNode(newBtcWalletBalance.subtract(btcWalletBalance));
                    } else {
                        sendToBtcRskFederationAddress(newBtcWalletBalance.subtract(btcWalletBalance));
                    }
                }
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 10000L, 10000L);
    }

    private BigDecimal getBtwWalletBalance() {
        // TODO parameterize walletname
        log.info("Retrieving BTC onchain balance");
        var confirmedBalance = bitcoindRpcClient.getBalance();
        log.info("Retrieved BTC onchain balance: {}", confirmedBalance);
        return confirmedBalance;
    }

    private void sendToBtcRskFederationAddress(BigDecimal amount) {
        String rskFederationAddress = rskService.retrieveRskFederationBtcAddress();
        bitcoindRpcClient.sendToAddress(rskFederationAddress, amount);
        // https://developers.rsk.co/rsk/rbtc/conversion/networks/mainnet/
    }

    private List<BitcoindRpcClient.Transaction> getTransactions(){
        var transactionList = bitcoindRpcClient.listTransactions("boltz-testnet-btc-wallet");
        log.info(transactionList.toString());
        return transactionList;
    }

    private void sendToLightningNode(BigDecimal amount){
        String onchainLightningAddress = lndService.getNewOnChainAddress();
        bitcoindRpcClient.sendToAddress(onchainLightningAddress, amount);
    }

}
