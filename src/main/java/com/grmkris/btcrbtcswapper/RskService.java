package com.grmkris.btcrbtcswapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.tx.ManagedTransaction;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

@Component
@Slf4j
public class RskService {
    @Value("${rsk.service.url}")
    private String serverurl;

    @Value("${rsk.wallet.public.key}")
    private String swapperRskAddress;

    @Value("${rsk.wallet.private.key}")
    private String rskPrivateKey;
    private String rskPublicKey;

    @Value("${rsk.bridge.address}")
    private String rskBridgeAddress;

    private Web3j web3j;
    private Credentials credentials;

    @PostConstruct
    void init() {
        web3j = Web3j.build(new HttpService(serverurl));  //web3j  object after connecting with server for transaction
        credentials = Credentials.create(rskPrivateKey);

        String privateKey = credentials.getEcKeyPair().getPrivateKey().toString(16);
        String publicKey = credentials.getEcKeyPair().getPublicKey().toString(16);
        String addr = credentials.getAddress();

        rskPublicKey = addr;
    }

    public void run(String... args) throws Exception {
        log.info("Starting rsk service");
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
                //sendToBTCSwapContract(tx.getValue().longValue());
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

    public void sendToBTCSwapContract(BigDecimal amount) {
        try {
            log.info("Sending funds to BTCSwapContract: {}", rskBridgeAddress);
            this.getBlockSize();

            TransactionReceipt transactionReceipt = Transfer.sendFunds(
                    web3j, credentials, rskBridgeAddress,
                    BigDecimal.valueOf(1.0), Convert.Unit.GWEI).send();

            log.info("Sent funds to BTCSwapContract: {}, transaction hash: {}", rskBridgeAddress, transactionReceipt.getTransactionHash());
        } catch (Exception e) {
            log.error("Error while sending funds to BTCSwapContract", e);
        }
    }

    public BigInteger getRskBalance() {
        EthGetBalance balanceWei = null;
        try {
            log.info("Retrieving rsk balance");
            balanceWei = web3j.ethGetBalance(rskPublicKey, DefaultBlockParameterName.LATEST).send();
        } catch (IOException e) {
            log.error("Error retrieving rsk balance");
        }
        BigDecimal balance = Convert.fromWei(balanceWei.getBalance().toString(), Convert.Unit.GWEI).divide(BigDecimal.TEN);
        log.info("rsk balance: " + balance);
        return balance.toBigInteger();
    }
}
