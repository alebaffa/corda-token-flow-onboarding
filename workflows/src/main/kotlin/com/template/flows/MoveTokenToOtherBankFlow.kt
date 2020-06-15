package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class MoveTokenToOtherBankFlow(val bank: Party,
                               val amount: Int) : FlowLogic<String>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        // take the token from the vault
        val criteria = QueryCriteria.FungibleStateQueryCriteria(
                relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                status = Vault.StateStatus.UNCONSUMED
        )
        // take my StateAndRef of my tokens in my vault
        val coinStateAndRef: StateAndRef<FungibleToken> = serviceHub.vaultService.queryBy<FungibleToken>(criteria).states.single()
        // set how many tokens I want to sell
        val amountOfToken = amount of coinStateAndRef.state.data.issuedTokenType.tokenType

        val tx = subFlow(MoveFungibleTokens(amountOfToken, bank))

        return "Transfered " + amount + " of tokens to " + bank.name.organisation + "\n" +
                "TransactionID: " + tx.id
    }

    @InitiatedBy(MoveTokenToOtherBankFlow::class)
    class MoveTokenToOtherBankResponder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Simply use the MoveFungibleTokensHandler as the responding flow
            return subFlow(MoveFungibleTokensHandler(session))

        }
    }
}