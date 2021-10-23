package com.grmkris.btcrbtcswapper;

import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@AllArgsConstructor
public class BtcRbtcSwapperApplication implements CommandLineRunner {

    private final BtcService btcService;
    private final LndService lndService;
    private final RskService rskService;

    public static void main(String[] args) {
        SpringApplication.run(BtcRbtcSwapperApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        btcService.run();
        lndService.run();
        rskService.run();
    }
}
