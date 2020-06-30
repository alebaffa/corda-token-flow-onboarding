package com.template.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.template.states.CoinState
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

class CoinContract : EvolvableTokenContract(), Contract {

    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.CoinContract"
    }

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        val coinState = tx.outputStates.single() as CoinState

        coinState.apply {
            require(coinState.name.isNotEmpty()) { "The coin must have a name" }
            requireNotNull(coinState.valuation.token.currencyCode) { "The valuation must be defined" }
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) = Unit
}