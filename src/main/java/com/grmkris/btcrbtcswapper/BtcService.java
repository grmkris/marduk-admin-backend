package com.grmkris.btcrbtcswapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@Component
@Slf4j
public class BtcService {

    @Value("${btc.wallet.private.key}")
    private String btcPrivateKey;

    @Value("${btc.service.url}")
    private String btcServiceUrl;

    @Value("${btc.rpc.cookie}")
    private String btcRpcCookie;

    @Value("{lnd.wallet.address}")
    private String lightningAddress;

    private BitcoindRpcClient bitcoindRpcClient;
    List<BitcoindRpcClient.Transaction> transactionList;

    public void run(String... args) throws MalformedURLException {
        URL url;
        if (btcServiceUrl.contains("https")) {
            url = new URL("https://" + btcRpcCookie + "@" + btcServiceUrl.substring(8));
        } else {
            url = new URL("http://" + btcRpcCookie + "@" + btcServiceUrl);
        }
        bitcoindRpcClient = new BitcoinJSONRPCClient(url);
        //bitcoindRpcClient.importPrivKey(btcPrivateKey, "testing_wallet");
        startNewTransactionProber();

    }

    private void startNewTransactionProber(){
        log.info("Start probing for new transaction every 10 seconds");
        transactionList = getTransactions();
        TimerTask newTransactionProber = new TimerTask() {
            public void run() {
                var newTransactionList = getTransactions();
                if (transactionList.size() < newTransactionList.size()){
                    log.info("New bitcoin transaction");
                    sendToLightningNode(CollectionUtils.lastElement(newTransactionList).amount());
                    transactionList = newTransactionList;
                }
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 10000L, 10000L);
    }

    private List<BitcoindRpcClient.Transaction> getTransactions(){
        var transactionList = bitcoindRpcClient.listTransactions("testing_wallet");
        log.info(transactionList.toString());
        return transactionList;
    }

    private void sendToLightningNode(BigDecimal amount){
        // todo get lightning address from lndservice.getOnChainAddress
        bitcoindRpcClient.sendToAddress(lightningAddress, amount);
    }
}
