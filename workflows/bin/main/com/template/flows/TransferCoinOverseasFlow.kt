package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.template.states.CoinState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
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

        // take the token from the vault
        val coinStateAndRef: StateAndRef<CoinState> = serviceHub.vaultService.queryBy<CoinState>().states.single()
        val coinState = coinStateAndRef.state.data

        /* Build the transaction with a notary */
        val txBuilder = TransactionBuilder(notary)

        val tokenToTransfer = amount of coinState.toPointer(coinState.javaClass)

        /* Initiate a session with the counterparty */
        val session = initiateFlow(foreignBank)

        // send the amount of token to transfer to the counterparty
        session.send(tokenToTransfer)

        // retrieve the StateAndRef or the money from the counterparty
        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(session))

        // retrieve the actual money from the counterparty
        val moneyReceived: List<FungibleToken> = session.receive<List<FungibleToken>>().unwrap { it -> it }

        // Proposal to move the tokens to counterparty (the new holder)
        addMoveFungibleTokens(txBuilder, serviceHub, tokenToTransfer, foreignBank, ourIdentity)

        // propose to move the money to myself
        addMoveTokens(txBuilder, inputs, moneyReceived)

        /* Sign the transaction with your private */
        val initialSignedTrnx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to recieve signature of the buyer */
        val ftx = subFlow(CollectSignaturesFlow(initialSignedTrnx, listOf(session)))

        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */
        subFlow(UpdateDistributionListFlow(ftx))

        /* Call finality flow to notarise the transaction */
        val stx = subFlow(FinalityFlow(ftx, listOf(session)))
        return ("\nCongratulations! " + amount + " of " + coinState.name + " have been sold to " + foreignBank.name.organisation + "\nTransaction ID: "
                + stx.id)
    }


    @InitiatedBy(TransferCoinOverseasFlow::class)
    class HouseSaleResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            /* Receive the value in fiat currency of the token to buy */
            val tokenAmount = counterpartySession.receive<FungibleToken>().unwrap { it }

            // retrieve the money from the vault (stored as FungibleToken from previous flow)
            val moneyStateRef = serviceHub.vaultService.queryBy<FungibleToken>().states.single()
            val money = moneyStateRef.state.data

            subFlow(SendStateAndRefFlow(counterpartySession, listOf(moneyStateRef)))
            counterpartySession.send(listOf(money))

            //signing
            subFlow(object : SignTransactionFlow(counterpartySession) {
                @Throws(FlowException::class)
                override fun checkTransaction(stx: SignedTransaction) = Unit
            })
            return subFlow(ReceiveFinalityFlow(counterpartySession))
        }
    }
}