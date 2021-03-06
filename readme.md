## Code
- https://github.com/grmkris/marduk-admin-frontend
- https://github.com/grmkris/marduk-admin-backend

## RSK balances
- https://wiki.sovryn.app/en/technical-documents/mainnet-contract-addresses


## TODO
- 2fa authenticator
- write operations (update balancer configuration, update&restart boltz??)
- dockerization
- add more docs
- add more tests


## Swagger UI
- https://localhost:18080/swagger-ui/
- type: `thisisunsafe`
## Description
This service main purpose is to be a "balancing" tool for lnsov bridge.  
The lnsov bridge is an exchange that offers rbtc / lightning swaps. It should maintain ~50% of funds in lightning wallet and 50% funds in rsk wallet.  
It uses lightning loop service to do either:
 - loop in 
   - when lightning balance falls below X%
     1. this service sends funds from rsk wallet to RSK federation address
     2. after 100 confirmations RSK federation address sends funds to "rsk-paired" bitcoin wallet 
     3. this service is monitoring "rsk-paried" bitcoin address for new transactions
     4. after new transaction is found it sends it to lightning wallet
     5. after funds arrive to lightning wallet it initiates a loop in
 - loop out
   - when RBTC balance falls below X%
     1. this service initiates a loop out 
     2. loop out service sends funds to our lightning wallet
        - **it is possible to loop out to specific bitcoin address, so by modfying step1, we might skip straight to step4 and save 1 onchain transaction**
     3. this service monitors loop service for loop SWAP status, 
     4. when swap status=COMPLETE it sends onchain transaction to "rsk-paired" bitcoin wallet
     5. this service sends coins from "rsk-paired" wallet to RSK-Federation address
     6. after 100 confirmations funds are available in RSK wallet


## Before running
1. unlock lnd wallet you are connecting to (`lncli unlock`)
2. load new account to bitcoind 
   - `bitcoin-cli -chain=test -rpcpassword=password -rpcuser=rpcuser createwallet boltz-testnet-btc-wallet`
   - `bitcoin-cli -chain=test -rpcpassword=password -rpcuser=rpcuser loadwallet boltz-testnet-btc-wallet`
   Verify by: 
     - `bitcoin-cli -chain=test -rpcpassword=qqqwwweeerrrtttooo -rpcuser=kristjan1234 listwallets`
       - `["boltz-testnet-btc-wallet"]`
3. start loop client
    -eg: `./loopd --network=testnet --lnd.macaroonpath=/var/lib/docker/volumes/bitcoind-lnd_bitcoin/_data/data/chain/bitcoin/testnet/admin.macaroon  --lnd.tlspath=/var/lib/docker/volumes/bitcoind-lnd_bitcoin/_data/tls.cert --tlsextraip=185.217.125.196 --restlisten=185.217.125.196:8081`
   1. Java stuff
      1. make sure you have java11, then run `mvn package -Dmaven.test.skip=true`
      2. go to `cd target`
      3. start with `java jar btc-rbtc-swapper-0.0.1-SNAPSHOT.jar -DRSK_SERVICE_URL=URL` etc... etc.
        example: 
       ```shell
           java \
           -DRSK_BRIDGE_ADDRESS=some_value \
           -DRSK_WALLET_PRIVATE_KEY=some_value \
           -DRSK_WALLET_PUBLIC_KEY=some_value \
           -DRSK_SERVICE_URL=https://public-node.testnet.rsk.co \
           -DBTC_SERVICE_URL=some_value \
           -DBTC_RPC_COOKIE=some_value \
           -DLND_WALLET_ADDRESS= \
           -DLND_LOOP_URL=some_value \
           -DLND_LOOP_ADMIN_MACAROON=some_value \
           -DBTC_WALLET_PRIVATE_KEY=some_value \
           -DBTC_WALLET_PUBLIC_KEY=some_value \
           -DLND_ADMIN_MACAROON=some_value \
           -DLND_URL=some_value \
           -jar target/btc-rbtc-swapper-0.0.1-SNAPSHOT.jar
       ```

### This service requires access to the following information:

```properties
# RSK NODE URL FOR MONITORING RSK CHAIN AND SENDING TRANSACTIONS
rsk.service.url=${RSK_SERVICE_URL}
# RSK WALLET PUBLIC ADDRESS FOR MONITORING BALANCE AND CHECKING FOR NEW TRANSACTIONS
rsk.wallet.public.key=${RSK_WALLET_PUBLIC_KEY}
# RSK WALLET PRIVATE KEY FOR SENDING TRANSACTIONS TO RSK-BRIDGE-CONTRACT
rsk.wallet.private.key=${RSK_WALLET_PRIVATE_KEY}

# RSK BRIDGE CONTRACT FOR COINS TO BE SENT TO
# mainnet RSK Bridge Contract address: 0x0000000000000000000000000000000001000006
# https://developers.rsk.co/rsk/rbtc/conversion/networks/mainnet/
# testnet 0x0000000000000000000000000000000001000006
# https://developers.rsk.co/rsk/rbtc/conversion/networks/testnet/
rsk.bridge.address=${RSK_BRIDGE_ADDRESS} 

# BITCOIN NODE URL FOR MONITORING BTC CHAIN AND SENDING TRANSACTIONS
btc.service.url=${BTC_SERVICE_URL}
# USER/PW OR COOKIE VALUE FOR COMMUNICATION WITH BITCOIN NODE
btc.rpc.cookie=${$BTC_RPC_COOKIE}
# BITCOIN WALLET PUBLIC ADDRESS FOR MONITORING FOR NEW TRANSACTIONS
# IT SHOULD BE DERIVED FROM RSK_PUBLIC_KEY
# https://github.com/rsksmart/utils
# https://developers.rsk.co/rsk/rbtc/conversion/networks/mainnet/
btc.wallet.public.key=${RSK_WALLET_PUBLIC_KEY}
btc.wallet.private.key=${RSK_WALLET_PRIVATE_KEY}

# LIGHTNING WALLET ONCHAIN ADDRESS TO SEND FUNDS BEFORE LOOP IN
lnd.wallet.address=${LND_WALLET_ADDRESS}

# LND LOOP HTTP API ENDPOINT FOR INITIAING LOOPIN/LOOPOUT
lnd.loop.url=${LND_LOOP_URL}
# ADMIN MACAROON GENERATED BY LOOP CLIENT, IN HEX VALUE
# COMMAND: xxd -p -c2000 ~/.loop/testnet/loop.macaroon
# https://github.com/lightningnetwork/lnd/issues/2951
lnd.loop.admin.macaroon=${LND_LOOP_ADMIN_MACAROON}

```

## Helpful information
### RSK
- https://blockchain.oodles.io/dev-blog/implementing-ethereum-listener-for-listening-transactions/
- http://docs.web3j.io/4.8.7/advanced/filters_and_events/
- https://www.baeldung.com/web3j
### BTC
- https://bitcoinj.org/working-with-the-wallet#learning-about-changes
- https://bitcoin.stackexchange.com/questions/70063/how-do-i-parse-the-zeromq-messages-in-java


Multiple databases:
- https://www.baeldung.com/spring-data-jpa-multiple-databases
ReadOnly access:
- https://www.baeldung.com/spring-data-read-only-repository
Sqlite3:
- https://www.baeldung.com/spring-boot-sqlite

testtest