package com.grmkris.btcrbtcswapper.bitfinex;

import com.grmkris.btcrbtcswapper.RskHandler;
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
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class BitfinexHandler {

    private static final String ALGORITHM_HMACSHA384 = "HmacSHA384";
    @Value("${bitfinex.key}")
    private String apiKey;
    @Value("${bitfinex.secret}")
    private String apiSecret;
    private WebClient webClient;
    private final RskHandler rskHandler;

    @PostConstruct
    public void init(){
        HttpClient httpClient = HttpClient.create().wiretap("reactor.netty.http.client.HttpClient",
                LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);
        this.webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder().codecs(c ->
                c.defaultCodecs().enableLoggingRequestDetails(true)).build()
        ).clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    // https://www.example-code.com/java/bitfinex_v2_rest_user_info.asp
    public String getRBTCAPIAddress() throws IOException {

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("wallet", "exchange");
        requestBodyMap.put("method", "RBT");
        requestBodyMap.put("op_renew", 0);
        String nonce = System.currentTimeMillis() + "000";

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
    public String getLightningInvoice(BigDecimal amount) throws IOException {

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("wallet", "exchange");
        requestBodyMap.put("currency", "LNX");
        requestBodyMap.put("amount", amount);
        String nonce = System.currentTimeMillis() + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
        var jsonArrayresponse = webClient.post()
                .uri(bitfinexApiUrl+ "v2/auth/w/deposit/invoice")
                .header("bfx-nonce", nonce)
                .header("bfx-apikey", apiKey)
                .header("bfx-signature", generateSignature("v2/auth/w/deposit/invoice", nonce, requestBodyMap))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json.toString()))
                .exchangeToMono(response ->
                        response.bodyToMono(ArrayList.class)
                                .map(stringBody -> stringBody)
                ).block();
        return jsonArrayresponse.get(1).toString();
    }

    public String tradeRBTCforBTC(String amount) {
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("type", "EXCHANGE MARKET");
        requestBodyMap.put("symbol", "tRBTBTC");
        requestBodyMap.put("amount", "-"+amount);
        requestBodyMap.put("aff_code", "Nm-ntkbPc");

        String nonce = System.currentTimeMillis() + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
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
        requestBodyMap.put("aff_code", "Nm-ntkbPc");

        String nonce = System.currentTimeMillis() + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
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
        String nonce = System.currentTimeMillis() + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
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
    public String convertLightningToBTC(String amount) {

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("from", "exchange");
        requestBodyMap.put("to", "exchange");
        requestBodyMap.put("currency", "LNX");
        requestBodyMap.put("currency_to", "BTC");
        requestBodyMap.put("amount", amount);
        String nonce = System.currentTimeMillis() + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
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
        requestBodyMap.put("address", this.rskHandler.getRskAddress());
        String nonce = String.valueOf(System.currentTimeMillis()) + "000";

        String bitfinexApiUrl = "https://api.bitfinex.com/";
        JSONObject json = new JSONObject(requestBodyMap);
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
}
