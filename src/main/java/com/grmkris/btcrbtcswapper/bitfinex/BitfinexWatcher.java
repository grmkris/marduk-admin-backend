package com.grmkris.btcrbtcswapper.bitfinex;

import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketClient;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketConfiguration;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexWallet;
import com.grmkris.btcrbtcswapper.db.BalancingStatus;
import com.grmkris.btcrbtcswapper.db.BalancingStatusEnum;
import com.grmkris.btcrbtcswapper.db.BalancingStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Timer;
import java.util.TimerTask;

@Component
@RequiredArgsConstructor
@Slf4j
public class BitfinexWatcher {

    @Value("${bitfinex.key}")
    private String apiKey;
    @Value("${bitfinex.secret}")
    private String apiSecret;

    private final BalancingStatusRepository balancingStatusRepository;
    private final BitfinexHandler bitfinexHandler;

    private BitfinexWebsocketClient bitfinexClient;
    private BigDecimal rbtLastBalance;
    private BigDecimal lnxLastBalance;

    @PostConstruct
    public void init() {
        final BitfinexWebsocketConfiguration config = new BitfinexWebsocketConfiguration();
        config.setApiCredentials(apiKey, apiSecret);
        this.bitfinexClient = BitfinexClientFactory.newSimpleClient(config);
        bitfinexClient.connect();
    }


    public void startBitfinexTransactionWatcher(){

        var walletList = bitfinexClient.getWalletManager().getWallets();
        rbtLastBalance = walletList.stream()
                .filter(wallet -> wallet.getWalletType().equals(BitfinexWallet.Type.EXCHANGE)
                        && wallet.getCurrency().equals("RBT")).findFirst().get().getBalance();
        lnxLastBalance = walletList.stream()
                .filter(wallet -> wallet.getWalletType().equals(BitfinexWallet.Type.EXCHANGE)
                        && wallet.getCurrency().equals("LNX")).findFirst().get().getBalance();

        TimerTask newTransactionProber = new TimerTask() {
            public void run() {
                log.info("Balancing status: " + balancingStatusRepository.findById(1L).get().getBalancingStatus());
                var walletList = bitfinexClient.getWalletManager().getWallets();
                var rbtWallet = walletList.stream()
                        .filter(wallet -> wallet.getWalletType().equals(BitfinexWallet.Type.EXCHANGE)
                                && wallet.getCurrency().equals("RBT")).findFirst();
                var lnxWallet = walletList.stream()
                        .filter(wallet -> wallet.getWalletType().equals(BitfinexWallet.Type.EXCHANGE)
                                && wallet.getCurrency().equals("LNX")).findFirst();

                if (rbtLastBalance.compareTo(rbtWallet.get().getBalance()) < 0) {
                    var amount = rbtWallet.get().getBalance().subtract(rbtLastBalance);
                    log.info("RBT balance changed, amount: {}", amount);
                    var result = bitfinexHandler.tradeRBTCforBTC(amount.toString());
                    rbtLastBalance = rbtWallet.get().getBalance();
                    log.info("Traded RBTC for BTC, {}", result);
                    result = bitfinexHandler.convertBTCToLightning(amount.toString());
                    log.info("Converted BTC to Lightning, {}", result);
                    result = bitfinexHandler.withdrawLightning(amount.toString());
                    log.info("Withdrew Lightning, {}", result);
                    log.info("Returning balancing status back to IDLE");
                    balancingStatusRepository.saveAndFlush(new BalancingStatus(1L, BalancingStatusEnum.IDLE));
                }
                if (lnxLastBalance.compareTo(lnxWallet.get().getBalance()) < 0) {
                    var amount = lnxWallet.get().getBalance().subtract(lnxLastBalance);
                    log.info("LNX balance changed, amount: {}", amount);
                    var result = bitfinexHandler.convertLightningToBTC(amount.toString());
                    log.info("Converted Lightning to BTC, {}", result);
                    result = bitfinexHandler.tradeBTCforRBTC(amount.toString());
                    lnxLastBalance = lnxWallet.get().getBalance();
                    log.info("Traded BTC for RBTC, {}", result);
                    result = bitfinexHandler.withdrawRBTC(amount.toString());
                    log.info("Withdrew RBTC, {}", result);
                    log.info("Returning balancing status back to IDLE");
                    balancingStatusRepository.saveAndFlush(new BalancingStatus(1L, BalancingStatusEnum.IDLE));
                }
            }
        };

        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 1000L, 1000L);
    }
}
