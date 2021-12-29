package com.grmkris.mardukadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grmkris.mardukadmin.api.Balance;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CovalentService {

    private WebClient webClient;

    @Value("${covalent.api}")
    private String apikey;

    @PostConstruct
    public void init() throws SSLException {
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

    /*
    Use https://api.covalenthq.com/v1/30/address/0x4f3B4f618B9b23CCc33BEB6352Df2f93F082CAD4/balances_v2/?&key=ckey_6d8d660d68664959b0e56c177b7
    to get balances and then parse them to Map object
     */
    public List<Balance> getBalancesForAddress(String address) {
        var balances = webClient.get().uri("https://api.covalenthq.com/v1/30/address/" + address + "/balances_v2/?&key=" + apikey).retrieve().bodyToMono(Map.class).block();
        var returnData = new ArrayList<Balance>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNodeMap = mapper.convertValue(balances, JsonNode.class);
        jsonNodeMap.get("data").withArray("items").forEach(item -> {
            var balance = item.get("balance").asText();
            var token = item.get("contract_ticker_symbol").asText();
            var balanceBigDecimal = new BigDecimal(balance);
            Balance balanceObject = new Balance(token, balanceBigDecimal.divide(new BigDecimal("1000000000000000000")), address);
            returnData.add(balanceObject);
        });
        return returnData;
    }
}
