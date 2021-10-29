package com.grmkris.btcrbtcswapper;

import com.grmkris.btcrbtcswapper.db.BalancingStatus;
import com.grmkris.btcrbtcswapper.db.BalancingStatusEnum;
import com.grmkris.btcrbtcswapper.db.BalancingStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class BtcHandler {

    @Value("${btc.wallet.private.key}")
    private String btcPrivateKey;

    @Value("${btc.service.url}")
    private String btcServiceUrl;

    @Value("${btc.rpc.cookie}")
    private String btcRpcCookie;

    private BitcoindRpcClient bitcoindRpcClient;
    private final LndHandler lndHandler;
    private final RskHandler rskHandler;
    private final BalancingStatusRepository balancingStatusRepository;

    @PostConstruct
    public void init() throws MalformedURLException {
        //bitcoindRpcClient.importPrivKey(btcPrivateKey, "boltz-testnet-btc-wallet");
        URL url;
        if (btcServiceUrl.contains("https")) {
            url = new URL("https://" + btcRpcCookie + "@" + btcServiceUrl.substring(8));
            bitcoindRpcClient = new BitcoinJSONRPCClient(url);
        } else if (!btcServiceUrl.equals("")){
            url = new URL("http://" + btcRpcCookie + "@" + btcServiceUrl);
            bitcoindRpcClient = new BitcoinJSONRPCClient(url);
        }

    }

    public BigDecimal getBtcWalletBalance() {
        // TODO parameterize walletname
        log.info("Retrieving BTC onchain balance");
        var confirmedBalance = bitcoindRpcClient.getBalance();
        log.info("Retrieved BTC onchain balance: {}", confirmedBalance);
        return confirmedBalance;
    }

    public void sendToBtcRskFederationAddress(BigDecimal amount) {
        String rskFederationAddress = rskHandler.retrieveRskFederationBtcAddress();
        bitcoindRpcClient.sendToAddress(rskFederationAddress, amount);
        log.info("Sent funds to RSK federation address, LOOPOUT complete");
        // since this is last step of the loop out process we put service back to idle
        BalancingStatus balancingStatus = balancingStatusRepository.findById(1L).get();
        balancingStatus.setBalancingStatus(BalancingStatusEnum.IDLE);
        balancingStatusRepository.save(balancingStatus);
        log.info("Returning balancing status to IDLE");
        // https://developers.rsk.co/rsk/rbtc/conversion/networks/mainnet/
    }

    private List<BitcoindRpcClient.Transaction> getTransactions(){
        var transactionList = bitcoindRpcClient.listTransactions("boltz-testnet-btc-wallet");
        log.info(transactionList.toString());
        return transactionList;
    }

    public void sendToLightningNode(BigDecimal amount){
        String onchainLightningAddress = lndHandler.getNewOnChainAddress();
        bitcoindRpcClient.sendToAddress(onchainLightningAddress, amount);
    }

}
