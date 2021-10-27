package com.grmkris.btcrbtcswapper;

import org.springframework.stereotype.Component;

@Component
public class BalanceStatus {

    private String balancingStatus = "idle"; // idle, loopin, loopout

    public String getBalancingStatus(){
        return balancingStatus;
    }

    public void setBalancingStatus(String status){
        balancingStatus = status;
    }

    public void completeBalancing(){
        balancingStatus = "idle";
    }
}
