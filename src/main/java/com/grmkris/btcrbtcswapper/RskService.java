package com.grmkris.btcrbtcswapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.*;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class RskService {
    @Value("${rsk.service.url}")
    private String serverurl;

    @Value("${rsk.wallet.public.key}")
    private String rskPublicKey;

    @Value("${rsk.wallet.private.key}")
    private String rskPrivateKey;

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
            if (tx.getTo().equalsIgnoreCase(rskPublicKey)){
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

    public TransactionReceipt sendToBTCSwapContract(BigDecimal amount) {
        try {
            log.info("Sending funds to BTCSwapContract: {}", rskBridgeAddress);
            this.getBlockSize();

            TransactionReceipt transactionReceipt = Transfer.sendFunds(
                    web3j, credentials, rskBridgeAddress,
                    amount, Convert.Unit.GWEI).send();

            log.info("Sent funds to BTCSwapContract: {}, transaction hash: {}", rskBridgeAddress, transactionReceipt.getTransactionHash());
            return transactionReceipt;
        } catch (Exception e) {
            log.error("Error while sending funds to BTCSwapContract", e);
            throw new RuntimeException("Error sending funds to BTCSwapContract", e);
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
        log.info("Retrieved rsk balance: " + balance);
        return balance.toBigInteger();
    }

    public String retrieveRskFederationBtcAddress() {
        //http://docs.web3j.io/4.8.7/transactions/transactions_and_smart_contracts/
        List<Type> inputParameters = new ArrayList<>();
        List<TypeReference<?>> outputParameters = Arrays.asList(new TypeReference<Utf8String>() {});
        Function function = new Function("getFederationAddress",
                inputParameters,
                outputParameters);
        String functionEncoder = FunctionEncoder.encode(function);
        EthCall response = null;
        try {
            response = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            rskPublicKey, "0x0000000000000000000000000000000001000006", "hello"),
                            DefaultBlockParameterName.LATEST
                    ).sendAsync().get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        List<Type> someType = FunctionReturnDecoder.decode(response.getValue(),function.getOutputParameters());
        Iterator<Type> it = someType.iterator();
        Type resault = someType.get(0);
        String a = resault.toString();
        log.info("RSK FEDERATION ADDRESS: {}", a);
        return a;
    }
}
