package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.template.states.CoinState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@StartableByRPC
@InitiatingFlow
class TransferCoinOverseasFlow(val foreignBank: Party,
                               val amount: Int) : FlowLogic<String>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): String {
        /* Choose the notary for the transaction */
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        /* Build the transaction with a notary */
        val txBuilder = TransactionBuilder(notary)
        // take the token from the vault
        val criteria = QueryCriteria.FungibleStateQueryCriteria(
                relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                status = Vault.StateStatus.UNCONSUMED,
                contractStateTypes = setOf(CoinState::class.java)
        )
        val coinStateAndRef: StateAndRef<FungibleToken> = serviceHub.vaultService.queryBy<FungibleToken>(criteria).states.single()
        //val partyAndAmount = PartyAndAmount(ourIdentity, Amount(amount.toLong(), coinStateAndRef.state.data.tokenType))
        addMoveFungibleTokens(txBuilder, serviceHub, Amount(amount.toLong(), coinStateAndRef.state.data.tokenType), foreignBank, ourIdentity)
        // create session with counterparty
        val session = initiateFlow(foreignBank)
        // how many tokens I have in the vault
        val amountOfToken = amount of coinStateAndRef.state.data.issuedTokenType
        // send the amount of token I want to sell
        session.send(amountOfToken)

        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(session))
        val moneyReceived = session.receive<FungibleToken>().unwrap { it }

        addMoveTokens(txBuilder, inputs, listOf(moneyReceived))

        /* Sign the transaction with your private */
        val initialSignedTx = serviceHub.signInitialTransaction(txBuilder)

        /* Collect the signatures and finality */
        val signedTx = subFlow(CollectSignaturesFlow(initialSignedTx, listOf(session)))
        subFlow(UpdateDistributionListFlow(signedTx))
        val stx = subFlow(FinalityFlow(signedTx, listOf(session)))
        return ("\nCongratulations! " + amount + " of ETH have been sold to " + foreignBank.name.organisation + "\nTransaction ID: "
                + stx.id)
    }


    @InitiatedBy(TransferCoinOverseasFlow::class)
    class ResponderFlow(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            /* Receive the amount of tokens to buy */
            val amount = counterpartySession.receive<Amount<IssuedTokenType>>().unwrap { it }

            val criteria = QueryCriteria.FungibleStateQueryCriteria(
                    relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                    status = Vault.StateStatus.UNCONSUMED
            )
            val moneyInVault: StateAndRef<FungibleToken> = serviceHub.vaultService.queryBy<FungibleToken>(criteria).states.single()
            val tokenBack = moneyInVault.state.data
            subFlow(SendStateAndRefFlow(counterpartySession, listOf(moneyInVault)))
            counterpartySession.send(tokenBack)

            //signing
            subFlow(object : SignTransactionFlow(counterpartySession) {
                @Throws(FlowException::class)
                override fun checkTransaction(stx: SignedTransaction) { // Custom Logic to validate transaction.
                }
            })
            //signing
            return subFlow(ReceiveFinalityFlow(counterpartySession))
        }
    }
}