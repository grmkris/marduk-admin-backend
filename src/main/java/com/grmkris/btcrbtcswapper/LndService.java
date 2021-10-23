package com.grmkris.btcrbtcswapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class LndService {

    @Value("{lnd.admin.macaroon}")
    private String lndAdminMacaroon;
    @Value("{lnd.rest}")
    private String lndRestEndpoint;
    private WebClient webClient = WebClient.create();

    public void initiateLoopIn(BigInteger value){
        MultiValueMap<String, String> bodyValues = new LinkedMultiValueMap<>();
        bodyValues.add("amt", value.toString());
        bodyValues.add("max_swap_fee", "500"); // TODO parameterize this

        WebClient.ResponseSpec responseSpec = webClient.post()
                .uri("https://185.217.125.196:8081/v1/loop/in")
                .header("Grpc-Metadata-macaroon", lndAdminMacaroon)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(bodyValues))
                .retrieve();
        String responseBody = responseSpec.bodyToMono(String.class).block();
        log.info("Sent loop in request:");
        log.info(responseBody);
    }

    public void initiateLoopOut(BigInteger value){
        MultiValueMap<String, String> bodyValues = new LinkedMultiValueMap<>();
        bodyValues.add("amt", value.toString());
        bodyValues.add("max_swap_fee", "500"); // TODO parameterize this
        bodyValues.add("max_swap_routing_fee", "500");

        WebClient.ResponseSpec responseSpec = webClient.post()
                .uri("https://185.217.125.196:8081/v1/loop/out")
                .header("Grpc-Metadata-macaroon", lndAdminMacaroon)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(bodyValues))
                .retrieve();
        String responseBody = responseSpec.bodyToMono(String.class).block();
        log.info("Sent loop out request:");
        log.info(responseBody);
    }

    public String getNewOnChainAddress(){
        // TODO
        return null;
    }
}
