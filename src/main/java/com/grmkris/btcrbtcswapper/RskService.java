package com.grmkris.btcrbtcswapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

@Component
@Slf4j
public class RskService implements CommandLineRunner {
    @Value("${rsk.service.url}")
    private String serverurl;

    @Value("${rsk.public.address}")
    private String swapperRskAddress;

    @Value("${rsk.private.key}")
    private String rskPrivateKey;

    @Value("${rsk.bridge.address}")
    private String rskBridgeAddress;

    private Web3j web3j;

    @PostConstruct
    void init() {
        web3j = Web3j.build(new HttpService(serverurl));  //web3j  object after connecting with server for transaction
    }

    @Override
    public void run(String... args) throws Exception {
        getBlockSize();
        startNewTransactionListener();
    }

    private void getBlockSize() throws IOException {
        log.info(web3j.ethBlockNumber().send().getBlockNumber().toString());
    }
    private void startNewTransactionListener() {
        //Ethereum listener started here tx is web3j transaction object described below
        web3j.pendingTransactionObservable().subscribe(tx -> {
            log.info("New transaction arrived to the chain");
            // using equalsIgnoreCase because for some reason the addresses don't match when comparing them without ignoring case
            // probably rsk <> eth compatibilty stuff
            if (tx.getTo().equalsIgnoreCase(swapperRskAddress)){
                log.info("Found transaction from: {}", tx.getFrom());
                confirmTransaction(tx);
                log.info("Transaction confirmed");
                sendToBTCSwapContract(tx);
            }
        });
    }
    private void confirmTransaction(Transaction trx) {
        log.info("Waiting for transaction to confirm");
        final CountDownLatch latch = new CountDownLatch(1);
        TimerTask getTransactionConfirmations = new TimerTask() {
            public void run() {
                if ( getTransactionConfirmations(trx) > 10 ) {
                    latch.countDown();
                    cancel();
                };
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(getTransactionConfirmations, 1000L, 1000L);
        try {
            latch.await();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private long getTransactionConfirmations(Transaction trx) {
        try {
            long currentBlockNumber = web3j.ethBlockNumber().send().getBlockNumber().longValue();

            long numberOfConfirmations = trx.getBlockNumberRaw() == null ? 0 : currentBlockNumber - trx.getBlockNumber().longValue();
            log.info("Number of confirmations: {}", numberOfConfirmations);
            return numberOfConfirmations;
        } catch (IOException e) {
            log.error("Error getting transaction confirmation number");
        }
        return 0;
    }

    private void sendToBTCSwapContract(Transaction tx) {
        Credentials credentials = Credentials.create(rskPrivateKey);
        try {
            log.info("Sending funds to BTCSwapContract: {}", rskBridgeAddress);
            TransactionReceipt transactionReceipt = Transfer.sendFunds(
                    web3j, credentials, rskBridgeAddress,
                    BigDecimal.valueOf(tx.getValue().longValue()), Convert.Unit.ETHER).send();
            log.info("Sent funds to BTCSwapContract: {}, transaction hash: {}", transactionReceipt.getTo(), transactionReceipt.getTransactionHash());
        } catch (Exception e) {
            log.error("Error while sending funds to BTCSwapContract", e);
        }
    }

}
