package com.grmkris.btcrbtcswapper.bitfinex;

import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketClient;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketConfiguration;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexOrderBookEntry;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexWallet;
import com.github.jnidzwetzki.bitfinex.v2.entity.currency.BitfinexCurrencyPair;
import com.github.jnidzwetzki.bitfinex.v2.manager.OrderbookManager;
import com.github.jnidzwetzki.bitfinex.v2.manager.WalletManager;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexOrderBookSymbol;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexSymbols;
import com.grmkris.btcrbtcswapper.db.BalancingStatusRepository;
import io.netty.handler.logging.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.web3j.crypto.Wallet;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

// https://github.com/jnidzwetzki/bitfinex-v2-wss-api-java/blob/master/EXAMPLES.md
@Component
@Slf4j
@RequiredArgsConstructor
public class BitfinexHandler {

    private static final String ALGORITHM_HMACSHA384 = "HmacSHA384";
    @Value("${bitfinex.key}")
    private String apiKey;
    @Value("${bitfinex.secret}")
    private String apiSecret;

    private final BalancingStatusRepository balancingStatusRepository;
    private WebClient webClient;
    private BitfinexWebsocketClient bitfinexClient;
    @PostConstruct
    public void init() throws MalformedURLException, SSLException {
        HttpClient httpClient = HttpClient.create().wiretap("reactor.netty.http.client.HttpClient",
                LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);
        this.webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder().codecs(c ->
                c.defaultCodecs().enableLoggingRequestDetails(true)).build()
        ).clientConnector(new ReactorClientHttpConnector(httpClient)).build();

        final BitfinexWebsocketConfiguration config = new BitfinexWebsocketConfiguration();
        config.setApiCredentials(apiKey, apiSecret);

        this.bitfinexClient = BitfinexClientFactory.newSimpleClient(config);
        bitfinexClient.connect();

        // log.info("Getting bitfinex account info");
        // this.getWalletBalance();
        // var result = this.getBitcoinAPIAddress();
        // log.info("Retrieved bitcoin network deposit address from bitfinex: \r\n {}", result);

        // result = this.getUserInfo();
        // log.info("Retrieved userinfo {} ", result);
        // var result1 = this.getRBTCDepositAddress();
        // log.info("Retrieved rbtc network deposit address from bitfinex: \r\n {}", result1);

        // result = this.getLightningInvoice();
        // log.info("Retrieved lightning invoice {} ", result);

        // var result = this.tradeRBTCforBTC();
        // log.info("Created the trade {}", result);

        // result = this.convertBTCToLightning();
        // log.info("Converted btc to lnx, {}", result);

        // var result = this.withdrawRBTC();
        // log.info("Withdraw rbtc, {}", result);

        //var result = this.withdrawLightning();
        //log.info("Withdraw lightning, {}", result);
        log.info("Starting bitfinex watcher");
        watchBitfinexDeposits();
    }

    // https://www.example-code.com/java/bitfinex_v2_rest_user_info.asp
    public String getBitcoinAPIAddress() throws IOException {
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("wallet", "exchange");
        requestBodyMap.put("method", "Bitcoin");
        requestBodyMap.put("op_renew", 0);
        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
        log.info("Request body: {}", json.toString());
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/deposit/address")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/deposit/address", nonce, requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json.toString()))
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }

    // https://www.example-code.com/java/bitfinex_v2_rest_user_info.asp
    public String getRBTCAPIAddress() throws IOException {

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("wallet", "exchange");
        requestBodyMap.put("method", "RBT");
        requestBodyMap.put("op_renew", 0);
        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
        log.info("Request body: {}", json.toString());
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/deposit/address")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/deposit/address", nonce, requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json.toString()))
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }

    // https://www.example-code.com/java/bitfinex_v2_rest_user_info.asp
    public String getLightningInvoice(Double amount) throws IOException {

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("wallet", "exchange");
        requestBodyMap.put("currency", "LNX");
        requestBodyMap.put("amount", amount);
        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
        log.info("Request body: {}", json.toString());
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/deposit/invoice")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/deposit/invoice", nonce, requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json.toString()))
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }

    // https://www.example-code.com/java/bitfinex_v2_rest_user_info.asp
    public String getUserInfo() throws IOException {

        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/r/info/user")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/r/info/user", nonce, null))
                .contentType(MediaType.APPLICATION_JSON)
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }


    public String tradeRBTCforBTC(String amount) {
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("type", "EXCHANGE MARKET");
        requestBodyMap.put("symbol", "tRBTBTC");
        requestBodyMap.put("amount", "-"+amount);
        requestBodyMap.put("flags", 0);
        // TODO meta: {aff_code: "AFF_CODE_HERE"} // optional param to pass an affiliate code
        // https://docs.bitfinex.com/reference#rest-auth-submit-order
        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
        log.info("Request body: {}", json.toString());
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/order/submit")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/order/submit", nonce, requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json.toString()))
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }

    public String tradeBTCforRBTC(String amount) {
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("type", "EXCHANGE MARKET");
        requestBodyMap.put("symbol", "tRBTBTC");
        requestBodyMap.put("amount", amount);
        requestBodyMap.put("flags", 0);
        // TODO meta: {aff_code: "AFF_CODE_HERE"} // optional param to pass an affiliate code
        // https://docs.bitfinex.com/reference#rest-auth-submit-order
        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
        log.info("Request body: {}", json.toString());
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/order/submit")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/order/submit", nonce, requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json.toString()))
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }

    public String convertBTCToLightning(String amount) {
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("from", "exchange");
        requestBodyMap.put("to", "exchange");
        requestBodyMap.put("currency", "BTC");
        requestBodyMap.put("currency_to", "LNX");
        requestBodyMap.put("amount", amount);
        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
        log.info("Request body: {}", json.toString());
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/transfer")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/transfer", nonce, requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json.toString()))
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }

    public String withdrawLightning(String invoice) {
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("wallet", "exchange");
        requestBodyMap.put("method", "LNX");
        requestBodyMap.put("invoice", invoice);
        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
        log.info("Request body: {}", json.toString());
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/withdraw")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/withdraw", nonce, requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json.toString()))
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }

    public String withdrawRBTC(String amount) {
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("wallet", "exchange");
        requestBodyMap.put("method", "RBT");
        requestBodyMap.put("amount", amount);
        requestBodyMap.put("address", "0x0Cf84F01C311Dc093969136B1814F05B5b3167F6"); // TODO parameterize amount
        // requestBodyMap.put("meta", 0.002); {aff_code: "AFF_CODE_HERE"} // optional param to pass an affiliate code
        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
        log.info("Request body: {}", json.toString());
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/withdraw")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/withdraw", nonce, requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json.toString()))
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }



    private String generateSignature(String apiPath, String nonce, Map<String, Object> requestBodyMap) {
        if (requestBodyMap == null) {
            String signature = "/api/" + apiPath + nonce;
            String payload_sha384hmac = encryptPayload(signature, apiSecret, ALGORITHM_HMACSHA384);
            return payload_sha384hmac;
        } else {
            JSONObject json = new JSONObject(requestBodyMap);
            String signature = "/api/" + apiPath + nonce + json;
            log.info("signature: {}", signature);
            String payload_sha384hmac = encryptPayload(signature, apiSecret, ALGORITHM_HMACSHA384);
            return payload_sha384hmac;
        }
    }

    private String encryptPayload(String text, String secretKey, String algorithm) {
        String encryptedText = null;
        try {
            SecretKeySpec key = new SecretKeySpec((secretKey).getBytes("UTF-8"), algorithm);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(key);

            byte[] bytes = mac.doFinal(text.getBytes("ASCII"));

            StringBuffer hash = new StringBuffer();
            for (int i = 0; i < bytes.length; i++) {
                String hex = Integer.toHexString(0xFF & bytes[i]);
                if (hex.length() == 1) {
                    hash.append('0');
                }
                hash.append(hex);
            }
            encryptedText = hash.toString();
        } catch (UnsupportedEncodingException e) {
            log.info("UnsupportedEncodingException: "+e.getMessage());
        } catch (InvalidKeyException e) {
            log.info("Invalid key Exception: "+e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.info("NoSuchAlgorithmException: "+e.getMessage());
        }
        return encryptedText;
    }

    public void watchBitfinexDeposits(){
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

                log.info("RBT wallet: {}", rbtWallet.get().getBalance());
                log.info("LNX wallet: {}", lnxWallet.get().getBalance());

            }
        };

        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 10000L, 10000L);


    }

    public void watchBitfinexOrderBook(){
        BitfinexCurrencyPair.registerDefaults();
        final BitfinexOrderBookSymbol orderbookConfiguration = BitfinexSymbols.orderBook(
                BitfinexCurrencyPair.of("BTC","USD"), BitfinexOrderBookSymbol.Precision.P0, BitfinexOrderBookSymbol.Frequency.F0, 25);

        final OrderbookManager orderbookManager = bitfinexClient.getOrderbookManager();

        final BiConsumer<BitfinexOrderBookSymbol, BitfinexOrderBookEntry> callback = (orderbookConfig, entry) -> {
            log.info("Got entry {} for orderbook {}}", entry, orderbookConfig);
        };

        orderbookManager.registerOrderbookCallback(orderbookConfiguration, callback);
        orderbookManager.subscribeOrderbook(orderbookConfiguration);
    }

}
