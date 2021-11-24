package com.grmkris.btcrbtcswapper;

import com.grmkris.btcrbtcswapper.db.BalancingStatusEnum;
import com.grmkris.btcrbtcswapper.db.BalancingStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.MnemonicUtils;
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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static org.web3j.crypto.Bip32ECKeyPair.HARDENED_BIT;

@Component
@Slf4j
@RequiredArgsConstructor
public class RskHandler {
    @Value("${rsk.service.url}")
    private String serverurl;

    @Value("${rsk.wallet.seed}")
    private String rskWalletSeed;

    @Value("${rsk.bridge.address}")
    private String rskBridgeAddress;

    private Web3j web3j;
    private Credentials credentials;
    private final BalancingStatusRepository balancingStatusRepository;


    @PostConstruct
    void init() {
        web3j = Web3j.build(new HttpService(serverurl));
        //web3j  object after connecting with server for transaction
        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(MnemonicUtils.generateSeed(rskWalletSeed, ""));
        int[] path = {44 | HARDENED_BIT, 60 | HARDENED_BIT, HARDENED_BIT, 0, 0};
        Bip32ECKeyPair  x = Bip32ECKeyPair.deriveKeyPair(masterKeypair, path);
        credentials = Credentials.create(x);
        log.info("address from seed: {}", credentials.getAddress());
    }

    public String getRskAddress() {
        return credentials.getAddress();
    }

    public TransactionReceipt sendToRskBtcBridge(BigDecimal amount) {
        while (true) {
            try {
                log.info("Sending {} sats to BTCSwapContract: {}", amount, rskBridgeAddress);
                var result = this.sendRBTCtoAddress(amount, rskBridgeAddress);
                log.info("Sent {} sats to BTCSwapContract: {}, transaction hash: {}", amount, rskBridgeAddress, result.getTransactionHash());
                return result;
            } catch (Exception e) {
                log.warn("Error while sending funds to BTCSwapContract");
                log.warn(e.getMessage());
                try {
                    log.warn("Retrying in 10 seconds");
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public BigInteger getRskBalance() {
        EthGetBalance balanceWei = null;
        try {
            log.info("Retrieving rsk balance");
            balanceWei = web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BigDecimal balance = Convert.fromWei(balanceWei.getBalance().toString(), Convert.Unit.GWEI).divide(BigDecimal.TEN);
        log.info("Retrieved rsk balance: " + balance);
        return balance.toBigInteger();
    }

    public String retrieveRskFederationBtcAddress() {
        //http://docs.web3j.io/4.8.7/transactions/transactions_and_smart_contracts/
        // good example: https://ethereum.stackexchange.com/questions/13387/how-to-query-the-state-of-a-smart-contract-using-web3j-in-android
        List<Type> inputParameters = new ArrayList<>();
        List<TypeReference<?>> outputParameters = Arrays.asList(new TypeReference<Utf8String>() {});
        Function function = new Function("getFederationAddress",
                inputParameters,
                outputParameters);
        EthCall response = null;
        try {
            // https://mycrypto.testnet.rsk.co/
            // DATA FIELD 0x6923fa85 CAN BE FOUND on https://app.mycrypto.com/interact-with-contracts
            // interact with smart contract and check the request
            // TODO add mainnet contract address
            response = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            credentials.getAddress(), "0x0000000000000000000000000000000001000006", "0x6923fa85"),
                            DefaultBlockParameterName.LATEST
                    ).sendAsync().get();

            List<Type> someType = FunctionReturnDecoder.decode(response.getValue(),function.getOutputParameters());
            Iterator<Type> it = someType.iterator();
            Type result = someType.get(0);
            String a = result.toString();
            log.info("RSK FEDERATION BITCOIN ADDRESS: {}", a);
            return a;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException("Error retrieving RSK FEDERATION BITCOIN ADDRESS");
        }
    }

    public TransactionReceipt sendRBTCtoAddress(BigDecimal amount, String address) {
        while (true) {
            try {
                log.info("Sending {} sats to address: {}", amount, address);
                TransactionReceipt transactionReceipt = Transfer.sendFunds(
                        web3j, credentials, address,
                        amount.multiply(BigDecimal.TEN), Convert.Unit.GWEI).sendAsync().get();
                log.info("Sent {} sats to address: {}, transaction hash: {}", amount, address, transactionReceipt.getTransactionHash());
                return transactionReceipt;
            } catch (Exception e) {
                log.warn("Error while sending funds to address {}", address);
                log.warn(e.getMessage());
                try {
                    log.warn("Retrying in 10 seconds");
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}
