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
            requireNotNull(coinState.valuation.token.currencyCode) { "The valuation must be defined" }
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) = Unit
}