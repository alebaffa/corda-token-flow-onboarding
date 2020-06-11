package com.template.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.template.states.CoinState
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class CoinContract : EvolvableTokenContract(), Contract {

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        val coinState = tx.outputStates.single() as CoinState

        coinState.apply {
            require(coinState.name.isNotEmpty()) { "The coin must have a name" }
            require(coinState.currency.isNotEmpty()) { "The currency must be defined" }
            require(coinState.price > 0) { "The coin price must be greater than zero" }
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) = Unit
}