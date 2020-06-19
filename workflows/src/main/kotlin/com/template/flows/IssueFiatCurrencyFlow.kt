package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class IssueFiatCurrencyFlow(private val currency: String,
                            private val amount: Long,
                            private val accountName: String) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        val account = accountService.accountInfo(accountName)[0].state.data
        val targetAccount = subFlow(RequestKeyForAccount(account))

        val token: TokenType = FiatCurrency.getInstance(currency)

        val issuedTokenType: IssuedTokenType = token issuedBy ourIdentity

        val fungibleToken: AbstractToken = FungibleToken(Amount(amount, issuedTokenType), targetAccount)

        subFlow(IssueTokens(listOf(fungibleToken)))
        return "Issued $amount $currency token(s) to ${accountName}"

    }
}

