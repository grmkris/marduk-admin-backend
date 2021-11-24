package com.grmkris.btcrbtcswapper.notification;

import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLException;

import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
public class MailgunService {
    private WebClient webClient;

    private String mailGunUrl = "api.mailgun.net";
    @Value("${mailgun.api-key}")
    private String apiKey;
    @Value("${mailgun.base-url}")
    private String mailgunBaseUrl;
    @Value("${mailgun.email}")
    private String email;
    @Value("${mailgun.email1}")
    private String email1;

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

    public String sendEmail(String subject, String body) {
        System.out.println("Sending email with subject " + subject + " and body " + body);;
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host(mailGunUrl)
                        .path("/v3/" + mailgunBaseUrl + ".mailgun.org/messages")
                        .queryParam("from", "rbtc-swapper" + email)
                        .queryParam("to", email)
                        .queryParamIfPresent("to", Optional.ofNullable(email1))
                        .queryParam("subject", subject)
                        .queryParam("text", body)
                        .build())
                .header("Authorization", "Basic " + Base64Utils
                        .encodeToString(("api:" + apiKey).getBytes(UTF_8)))
                .contentType(MediaType.APPLICATION_JSON)
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .map(stringBody -> stringBody)
                ).block();
    }
}
