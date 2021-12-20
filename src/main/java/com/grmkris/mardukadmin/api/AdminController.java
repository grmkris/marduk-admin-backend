package com.grmkris.mardukadmin.api;

import com.grmkris.mardukadmin.LndHandler;
import com.grmkris.mardukadmin.RskHandler;
import com.grmkris.mardukadmin.db.balancer.BalancingStatusRepository;
import com.grmkris.mardukadmin.db.boltz.model.ReverseSwap;
import com.grmkris.mardukadmin.db.boltz.model.Swap;
import com.grmkris.mardukadmin.db.boltz.repository.ReverseSwapRepository;
import com.grmkris.mardukadmin.db.boltz.repository.SwapRepository;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.util.List;

/*
Admin Controller supports retrieving all recent boltz swaps, it supports filtering by type (reverse, and regular)
Admin Controller has API for calculating current P/L
Admin controller has API for retrieving current balance of all wallets that the service is supporting
-- LNX, RBTC, SOV
 */
@RestController
@AllArgsConstructor
public class AdminController {

    private final SwapRepository swapRepository;
    private final ReverseSwapRepository reverseSwapRepository;
    private final BalancingStatusRepository balancingStatusRepository;
    private final RskHandler rskHandler;
    private final LndHandler lndHandler;

    @RequestMapping(value = "/admin/swaps", method = RequestMethod.GET)
    public List<Swap> getSwaps() {
        return swapRepository.findAll();
    }

    @RequestMapping(value = "/admin/swaps/reverse", method = RequestMethod.GET)
    public List<ReverseSwap> getReverseSwaps() {
        return reverseSwapRepository.findAll();
    }

    @RequestMapping(value = "/admin/balance", method = RequestMethod.GET)
    public Mono<BigInteger> getBalances() {
        return lndHandler.getLightningBalanceReactive();
    }

    @RequestMapping(value = "/admin/profit", method = RequestMethod.GET)
    public String getProfit() {
        return null;
    }

    @RequestMapping(value = "/admin/status", method = RequestMethod.GET)
    public String getStatus() {
        return balancingStatusRepository.findById(1L).get().toString();
    }
}



