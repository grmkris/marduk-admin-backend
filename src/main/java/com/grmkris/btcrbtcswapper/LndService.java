package com.grmkris.btcrbtcswapper;

import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import javax.net.ssl.SSLException;
import java.math.BigInteger;
import java.util.*;

@Component
@Slf4j
public class LndService {

    @Value("${lnd.loop.admin.macaroon}")
    private String loopAdminMacaroon;
    @Value("${lnd.loop.url}")
    private String loopRestEndpoint;

    @Value("${lnd.admin.macaroon}")
    private String lndAdminMacaroon;
    @Value("${lnd.url}")
    private String lndRestEndpoint;
    private WebClient webClient;

    private BigInteger lndOnchainWalletBalance;

    public LndService() throws SSLException {
        SslContext sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext)).wiretap("reactor.netty.http.client.HttpClient",
                LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);
        this.webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder().codecs(c ->
                c.defaultCodecs().enableLoggingRequestDetails(true)).build()
        ).clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    public void run(String... args) throws Exception {
        log.info("Starting lnd service");
        lndWalletBalanceChecker();
    }

    // periodically check lightning wallet balance, if it increases send the dela amount through loopin
    public void lndWalletBalanceChecker(){
        log.info("Start probing for new lightning transaction every 1000 seconds");
        lndOnchainWalletBalance = this.getLightningOnChainBalance();
        TimerTask newTransactionProber = new TimerTask() {
            public void run() {
                var newlndWalletBalance = getLightningOnChainBalance();
                if (newlndWalletBalance.compareTo(lndOnchainWalletBalance) > 0){
                    initiateLoopIn(newlndWalletBalance.subtract(lndOnchainWalletBalance));
                }
            }
        };
        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(newTransactionProber, 10000L, 10000L);

    }

    private BigInteger getLightningOnChainBalance() {
        log.info("Retrieving lightning onchain balance");
        String responseBody = webClient.get()
                .uri(lndRestEndpoint+ "/v1/balance/blockchain")
                .header("Grpc-Metadata-macaroon", lndAdminMacaroon)
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
        String localbalance = "0";
        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            localbalance = jsonObject.getString("confirmed_balance");
        } catch (JSONException e) {
            log.error("Error parsing lnd api /v1/balance/channels");
        }
        log.info("Retrieved Lightning onchain balance: {}", localbalance);
        return BigInteger.valueOf(Long.parseLong(localbalance));
    }


    public void initiateLoopIn(BigInteger value){
        Map<String, String> requestBodyMap = new HashMap<>();
        requestBodyMap.put("amt", value.toString());
        requestBodyMap.put("max_swap_fee", "500"); // TODO parameterize this

        String responseBody = webClient.post()
                .uri(loopRestEndpoint+ "/v1/loop/in")
                .header("Grpc-Metadata-macaroon", loopAdminMacaroon)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(requestBodyMap), Map.class)
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();

        log.info("Sent loop in request:");
        log.info(responseBody);
    }

    public void initiateLoopOut(BigInteger value){
        Map<String, String> requestBodyMap = new HashMap<>();
        requestBodyMap.put("amt", value.toString());
        requestBodyMap.put("max_swap_fee", "500"); // TODO parameterize this
        requestBodyMap.put("max_swap_routing_fee", "500");
        requestBodyMap.put("max_prepay_amt", "2000"); // TODO parameterize this, with lower amounts swaps are failing

        String responseBody = webClient.post()
                .uri(loopRestEndpoint+ "/v1/loop/out")
                .header("Grpc-Metadata-macaroon", loopAdminMacaroon)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(requestBodyMap), Map.class)
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
        log.info("Sent loop out request:");
        log.info(responseBody);
    }

    public String getNewOnChainAddress(){
        log.info("Getting new onchain address");
        String responseBody = webClient.get()
                .uri(lndRestEndpoint+ "/v1/newaddress")
                .header("Grpc-Metadata-macaroon", lndAdminMacaroon)
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
        String address = "0";
        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            address = jsonObject.getString("address");
        } catch (JSONException e) {
            log.error("Error parsing lnd api /v1/balance/channels");
        }
        log.info("New onchain Lightning address: {}", address);
        return address;
    }


    public BigInteger getLightningBalance() {
        log.info("Retrieving lightning balance");
        String responseBody = webClient.get()
                .uri(lndRestEndpoint+ "/v1/balance/channels")
                .header("Grpc-Metadata-macaroon", lndAdminMacaroon)
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
        String localbalance = "0";
        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            localbalance = jsonObject.getJSONObject("local_balance").getString("sat");
        } catch (JSONException e) {
            log.error("Error parsing lnd api /v1/balance/channels");
        }
        log.info("Retrieved Lightning balance: {}", localbalance);
        return BigInteger.valueOf(Long.parseLong(localbalance));
    }

    public List<String> getCurrentLoopOutList(){
        return this.getCurrentLoopOutList();
    }
}
