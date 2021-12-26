package com.grmkris.mardukadmin.bitfinex;

import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketClient;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketConfiguration;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexWallet;
import com.github.jnidzwetzki.bitfinex.v2.entity.currency.BitfinexCurrencyPair;
import com.grmkris.mardukadmin.LndHandler;
import com.grmkris.mardukadmin.db.balancer.BalancingStatus;
import com.grmkris.mardukadmin.db.balancer.BalancingStatusEnum;
import com.grmkris.mardukadmin.db.balancer.BalancingStatusRepository;
import com.grmkris.mardukadmin.notification.MailgunService;
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
    private final LndHandler lndHandler;
    private final MailgunService mailgunService;
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
                if (balancingStatusRepository.findById(1L).get().getBalancingStatus().equals(BalancingStatusEnum.IDLE)) {
                    return;
                }
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
                        amount = amount.multiply(new BigDecimal("0.995")); // 0.5% "fee" to account for the bitfinex trading fees
                        result = bitfinexHandler.convertBTCToLightning(amount.toString());
                        log.info("Converted BTC to Lightning, {}", result);
                        TimeUnit.SECONDS.sleep(60);
                        String invoice = lndHandler.getLightningInvoice(amount.multiply(BigDecimal.valueOf(100000000)).toBigInteger());
                        result = bitfinexHandler.withdrawLightning(invoice);
                        log.info("Withdrew Lightning, {}", result);
                        log.info("Returning balancing status back to IDLE");
                        var balancingStatus = balancingStatusRepository.findById(1L).get();
                        balancingStatus.setBalancingStatus(BalancingStatusEnum.IDLE);
                        balancingStatus.setAmount(BigDecimal.ZERO);
                        balancingStatusRepository.saveAndFlush(balancingStatus);
                        mailgunService.sendEmail("Balancing status changed to IDLE", "Withdrew Lightning from bitfinex" + result);
                    }
                }
                if (lnxLastBalance.compareTo(lnxWallet.get().getBalance()) < 0) {
                    var amount = lnxWallet.get().getBalance().subtract(lnxLastBalance);
                    log.info("LNX balance changed, amount: {}", amount);
                    if (balancingStatusRepository.findById(1L).get().getBalancingStatus() == BalancingStatusEnum.PEGOUT) {
                        var result = bitfinexHandler.convertLightningToBTC(amount.toString());
                        log.info("Converted Lightning to BTC, {}", result);
                        TimeUnit.SECONDS.sleep(5);
                        amount = amount.multiply(new BigDecimal("0.995")); // 0.5% "fee" to account for the bitfinex trading fees
                        result = bitfinexHandler.tradeBTCforRBTC(amount.toString());
                        log.info("Traded BTC for RBTC, {}", result);
                        TimeUnit.SECONDS.sleep(60);
                        lnxLastBalance = lnxWallet.get().getBalance();
                        result = bitfinexHandler.withdrawRBTC(amount.toString());
                        log.info("Withdrew RBTC, {}", result);
                        log.info("Returning balancing status back to IDLE");
                        var balancingStatus = balancingStatusRepository.findById(1L).get();
                        balancingStatus.setBalancingStatus(BalancingStatusEnum.IDLE);
                        balancingStatus.setAmount(BigDecimal.ZERO);
                        balancingStatusRepository.saveAndFlush(balancingStatus);
                        mailgunService.sendEmail("Balancing status changed to IDLE", "Withdrew RBTC from bitfinex" + result);
                    }
                }
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 1000L, 10000L);
    }
}
