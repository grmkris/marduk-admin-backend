package com.grmkris.btcrbtcswapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

@Component
@Slf4j
public class BtcService implements CommandLineRunner {

    @Value("${btc.private.key}")
    private String btcPrivateKey;

    @Value("${btc.service.url}")
    private String btcServiceUrl;

    @Value("${btc.rpc.cookie}")
    private String btcRpcCookie;

    @Override
    public void run(String... args) throws MalformedURLException {
        URL url;
        if (btcServiceUrl.contains("https")) {
            url = new URL("https://" + btcRpcCookie + "@" + btcServiceUrl.substring(8));
        } else {
            url = new URL("http://" + btcRpcCookie + "@" + btcServiceUrl.substring(7));
        }
        BitcoindRpcClient bitcoindRpcClient = new BitcoinJSONRPCClient(url);
        bitcoindRpcClient.importPrivKey(btcPrivateKey, "testing_wallet");
        var transactionList = bitcoindRpcClient.listTransactions("testing_wallet");
        log.info(transactionList.toString());
    }

    private void startNewTransactionListener(){

    }
}
