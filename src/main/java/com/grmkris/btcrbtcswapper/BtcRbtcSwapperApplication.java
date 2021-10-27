package com.grmkris.btcrbtcswapper;

import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@AllArgsConstructor
public class BtcRbtcSwapperApplication implements CommandLineRunner {

    private final BalanceCoordinator balancingService;
    private final BlockchainWatcher blockchainWatcher;

    public static void main(String[] args) {
        SpringApplication.run(BtcRbtcSwapperApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        blockchainWatcher.startLNDTransactionWatcher();
        blockchainWatcher.startBTCTransactionWatcher();

        balancingService.startBalanceChecker();
    }
}
