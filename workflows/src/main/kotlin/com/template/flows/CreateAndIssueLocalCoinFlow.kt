package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.template.states.CoinState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker
import java.util.*


@StartableByRPC
class CreateAndIssueLocalCoinFlow(val tokenName: String,
                                  val volume: Int,
                                  val holder_account: String,
                                  val valuation: Amount<Currency>) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        val holderAccountInfo = accountService.accountInfo(holder_account)[0].state.data
        val holderAnonymousParty = subFlow(RequestKeyForAccount(holderAccountInfo))
        // 1. get the notary
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // 2. create the transaction components
        val coinState = CoinState(ourIdentity, tokenName, valuation, 0, UniqueIdentifier(), listOf(ourIdentity))

        /* 3. The subFlow provides the TransactionBuilder with the relative Commands and Signatures */
        subFlow(CreateEvolvableTokens(coinState withNotary notary))

        // 4. Create the TokenType from the EvolvableTokenType using the TokenPointer.
        // See the Token types schema on github: shorturl.at/FOWY5
        val issuedToken = coinState.toPointer(coinState.javaClass) issuedBy ourIdentity

        // 5. Create the FungibleToken and issue to the holder
        val tokenCoin = FungibleToken(Amount(volume.toLong(), issuedToken), holderAnonymousParty)

        // 6. Issue the token created
        val stx = subFlow(IssueTokens(listOf(tokenCoin)))

        return ("Created " + volume + " " + tokenName + " tokens for " + valuation.quantity + " " + valuation.token.currencyCode +
                " to " + holder_account + "\nTransaction ID: " + stx.id)
    }
}