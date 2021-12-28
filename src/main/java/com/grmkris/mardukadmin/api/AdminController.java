package com.grmkris.mardukadmin.api;

import com.grmkris.mardukadmin.LndHandler;
import com.grmkris.mardukadmin.RskHandler;
import com.grmkris.mardukadmin.db.balancer.BalancingStatus;
import com.grmkris.mardukadmin.db.balancer.BalancingStatusRepository;
import com.grmkris.mardukadmin.db.boltz.model.ReverseSwap;
import com.grmkris.mardukadmin.db.boltz.model.Swap;
import com.grmkris.mardukadmin.db.boltz.repository.ReverseSwapRepository;
import com.grmkris.mardukadmin.db.boltz.repository.SwapRepository;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
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
@CrossOrigin("*")
public class AdminController {

    private final SwapRepository swapRepository;
    private final ReverseSwapRepository reverseSwapRepository;
    private final BalancingStatusRepository balancingStatusRepository;
    private final RskHandler rskHandler;
    private final LndHandler lndHandler;

    @RequestMapping(value = "/api/admin/swaps", method = RequestMethod.GET)
    public List<Swap> getSwaps() {
        return swapRepository.findAll();
    }

    @RequestMapping(value = "/api/admin/swaps/reverse", method = RequestMethod.GET)
    public List<ReverseSwap> getReverseSwaps() {
        return reverseSwapRepository.findAll();
    }

    @RequestMapping(value = "/api/admin/lnd/balance", method = RequestMethod.GET)
    public Mono<BigInteger> getLndBalance() {
        return lndHandler.getLightningBalanceReactive();
    }

    @RequestMapping(value = "/api/admin/rbtc/balance", method = RequestMethod.GET)
    public BigInteger getRskBalance() {
        return rskHandler.getRskBalance();
    }

    @RequestMapping(value = "/api/admin/profit", method = RequestMethod.GET)
    public String getProfit() {
        return null;
    }

    @RequestMapping(value = "/api/admin/status", method = RequestMethod.GET)
    public BalancingStatus getStatus() {
        return balancingStatusRepository.findById(1L).get();
    }
}



