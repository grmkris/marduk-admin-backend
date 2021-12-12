package com.grmkris.mardukadmin;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@RequiredArgsConstructor
class LndHandlerTest {

    private LndHandler lndHandler;
    @Test
    void payInvoice() {
        lndHandler.payInvoice("lntb50u1psed0t7pp5x5m3z8kgjmwdul4cymqlv8aj922zgdheph5fp6ayz866txssg6wsdqqcqzpgxqyz5vqsp5q4n8984dynzrrxnx25yvzuy0a65snwawr669qhk6px3ax3acd70s9qyyssq3gj7d9defppsasf5ttqn0zsdqjs8k0fkvjtgfa2ufcpzu4rq0smqp9ek4wt4e5mtpst746nrqeu4e2tr46hkveuzruer78rwketkhlqqywf59x");
    }
}