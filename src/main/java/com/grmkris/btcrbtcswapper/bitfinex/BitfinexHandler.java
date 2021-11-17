package com.grmkris.btcrbtcswapper.bitfinex;

import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.service.account.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;
import wf.bitcoin.krotjson.JSON;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

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

    private Exchange bitfinex;
    private WebClient webClient;
    @PostConstruct
    public void init() throws MalformedURLException, SSLException {
        ExchangeSpecification exSpec = new BitfinexExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        bitfinex = ExchangeFactory.INSTANCE.createExchange(exSpec);

        HttpClient httpClient = HttpClient.create().wiretap("reactor.netty.http.client.HttpClient",
                LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);
        this.webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder().codecs(c ->
                c.defaultCodecs().enableLoggingRequestDetails(true)).build()
        ).clientConnector(new ReactorClientHttpConnector(httpClient)).build();

        try {
            log.info("Getting bitfinex account info");
            // this.getWalletBalance();
            var result = this.getBitcoinAPIAddress();
            log.info("Retrieved bitcoin network deposit address from bitfinex: \r\n {}", result);
            // var result1 = this.getRBTCDepositAddress();
            // log.info("Retrieved rbtc network deposit address from bitfinex: \r\n {}", result1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getWalletBalance() throws IOException {
        // Get the account information
        AccountService accountService = bitfinex.getAccountService();
        AccountInfo accountInfo = accountService.getAccountInfo();
        log.info(accountInfo.toString());
    }

    private String getBitcoinDepositAddress() throws IOException {
        Currency currency = Currency.getInstance("BTC");
        var result = bitfinex.getAccountService().requestDepositAddress(currency);
        return  result;
    }

    private String getRBTCDepositAddress() throws IOException {
        Currency currency = Currency.getInstanceNoCreate("RBT");
        var result = bitfinex.getAccountService().requestDepositAddress(currency);
        return  result;
    }

    private String getBitcoinAPIAddress() throws IOException {

        Map<String, String> requestBodyMap = new HashMap<>();
        requestBodyMap.put("wallet", "trading");
        requestBodyMap.put("method", "bitcoin"); // TODO parameterize this
        requestBodyMap.put("op_renew", "0");


        String bitfinexApiUrl = "https://api.bitfinex.com/";
        return webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/deposit/address")
                .header("bfx-nonce", String.valueOf(System.currentTimeMillis()))
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/deposit/address", String.valueOf(nonce), requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(requestBodyMap), Map.class)
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }

    private long nonce = System.currentTimeMillis()*10;


    private String generateSignature(String apiPath, String nonce, Map<String, String> requestBodyMap) {

        // let signature = `/api/${apiPath}${nonce}${
         //   JSON.stringify(body)}`
// Consists of the complete url, nonce, and request body
        JSONObject json = new JSONObject(requestBodyMap);
        String signature = "/api/" + apiPath + nonce + json;
        String payload_sha384hmac = encryptPayload(signature, apiSecret, ALGORITHM_HMACSHA384);
        return payload_sha384hmac;
    }

    public static String encryptPayload(String text, String secretKey, String algorithm) {
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

}
