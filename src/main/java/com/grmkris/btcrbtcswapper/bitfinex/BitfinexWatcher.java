package com.grmkris.btcrbtcswapper.bitfinex;

import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketClient;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketConfiguration;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexWallet;
import com.github.jnidzwetzki.bitfinex.v2.entity.currency.BitfinexCurrencyPair;
import com.github.jnidzwetzki.bitfinex.v2.entity.currency.BitfinexCurrencyType;
import com.grmkris.btcrbtcswapper.db.BalancingStatus;
import com.grmkris.btcrbtcswapper.db.BalancingStatusEnum;
import com.grmkris.btcrbtcswapper.db.BalancingStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class BitfinexWatcher {

    private final BalancingStatusRepository balancingStatusRepository;
    private final BitfinexHandler bitfinexHandler;
    @Value("${bitfinex.key}")
    private String apiKey;
    @Value("${bitfinex.secret}")
    private String apiSecret;
    private BitfinexWebsocketClient bitfinexClient;
    private BigDecimal rbtLastBalance;
    private BigDecimal lnxLastBalance;

    @PostConstruct
    public void init() {
        final BitfinexWebsocketConfiguration config = new BitfinexWebsocketConfiguration();
        config.setApiCredentials(apiKey, apiSecret);
        this.bitfinexClient = BitfinexClientFactory.newSimpleClient(config);
        bitfinexClient.connect();
        BitfinexCurrencyPair.registerDefaults();
        //BitfinexCurrencyPair.register("RBT", "BTC", BitfinexCurrencyType.CURRENCY, 0.00006);
    }

    public void startBitfinexTransactionWatcher() {

        var walletList = bitfinexClient.getWalletManager().getWallets();
        rbtLastBalance = walletList.stream()
                .filter(wallet -> wallet.getWalletType().equals(BitfinexWallet.Type.EXCHANGE)
                        && wallet.getCurrency().equals("RBT")).findFirst().get().getBalance();
        lnxLastBalance = walletList.stream()
                .filter(wallet -> wallet.getWalletType().equals(BitfinexWallet.Type.EXCHANGE)
                        && wallet.getCurrency().equals("LNX")).findFirst().get().getBalance();

        TimerTask newTransactionProber = new TimerTask() {
            @SneakyThrows
            public void run() {
                var walletList = bitfinexClient.getWalletManager().getWallets();
                var rbtWallet = walletList.stream()
                        .filter(wallet -> wallet.getWalletType().equals(BitfinexWallet.Type.EXCHANGE)
                                && wallet.getCurrency().equals("RBT")).findFirst();
                var lnxWallet = walletList.stream()
                        .filter(wallet -> wallet.getWalletType().equals(BitfinexWallet.Type.EXCHANGE)
                                && wallet.getCurrency().equals("LNX")).findFirst();
                log.info("Balancing status: {}; rbtcWallet: {}; lightningWallet: {}", balancingStatusRepository.findById(1L).get().getBalancingStatus(), rbtWallet.get().getBalance(), lnxWallet.get().getBalance());

                if (rbtLastBalance.compareTo(rbtWallet.get().getBalance()) < 0) {
                    var amount = rbtWallet.get().getBalance().subtract(rbtLastBalance);
                    log.info("RBT balance changed, amount: {}", amount);
                    if (balancingStatusRepository.findById(1L).get().getBalancingStatus() == BalancingStatusEnum.PEGIN) {
                        var result = bitfinexHandler.tradeRBTCforBTC(amount.toString());
                        log.info("Traded RBTC for BTC, {}", result);
                        TimeUnit.SECONDS.sleep(5);
                        rbtLastBalance = rbtWallet.get().getBalance();
                        result = bitfinexHandler.convertBTCToLightning(amount.toString());
                        log.info("Converted BTC to Lightning, {}", result);
                        TimeUnit.SECONDS.sleep(60);
                        result = bitfinexHandler.withdrawLightning(amount.toString());
                        log.info("Withdrew Lightning, {}", result);
                        log.info("Returning balancing status back to IDLE");
                        balancingStatusRepository.saveAndFlush(new BalancingStatus(1L, BalancingStatusEnum.IDLE));
                    }
                }
                if (lnxLastBalance.compareTo(lnxWallet.get().getBalance()) < 0) {
                    var amount = lnxWallet.get().getBalance().subtract(lnxLastBalance);
                    log.info("LNX balance changed, amount: {}", amount);
                    if (balancingStatusRepository.findById(1L).get().getBalancingStatus() == BalancingStatusEnum.PEGOUT) {
                        var result = bitfinexHandler.convertLightningToBTC(amount.toString());
                        log.info("Converted Lightning to BTC, {}", result);
                        TimeUnit.SECONDS.sleep(5);
                        result = bitfinexHandler.tradeBTCforRBTC(amount.toString());
                        log.info("Traded BTC for RBTC, {}", result);
                        TimeUnit.SECONDS.sleep(60);
                        lnxLastBalance = lnxWallet.get().getBalance();
                        result = bitfinexHandler.withdrawRBTC(amount.toString());
                        log.info("Withdrew RBTC, {}", result);
                        log.info("Returning balancing status back to IDLE");
                        balancingStatusRepository.saveAndFlush(new BalancingStatus(1L, BalancingStatusEnum.IDLE));
                    }
                }
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 1000L, 10000L);
    }
}
