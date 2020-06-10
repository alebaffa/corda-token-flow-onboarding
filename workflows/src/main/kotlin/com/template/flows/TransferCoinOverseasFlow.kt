package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.template.states.CoinState
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.util.*

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
        val coinStateAndRef = serviceHub.vaultService.queryBy<CoinState>().states.single()
        val coinState = coinStateAndRef.state.data

        /* Build the transaction builder */
        val txBuilder = TransactionBuilder(notary)

        val amountToTransfer = amount of coinState.toPointer(coinState.javaClass)
        val tokenValue = coinState.value

        addMoveFungibleTokens(txBuilder, serviceHub, amountToTransfer, ourIdentity, foreignBank)

        /* Initiate a flow session with the buyer to send the house valuation and transfer of the fiat currency */
        val foreignBankSession = initiateFlow(foreignBank)

        // send to the foreign bank the value of the token in fiat currency
        foreignBankSession.send(tokenValue)

        // Recieve inputStatesAndRef for the fiat currency exchange from the foreignBank, these would be inputs to the fiat currency exchange transaction.
        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(foreignBankSession))

        // Recieve output for the fiat currency from the buyer, this would contain the transfered amount from buyer to yourself
        val moneyReceived: List<FungibleToken> = foreignBankSession.receive<List<FungibleToken>>().unwrap { it -> it }

        /* Create a fiat currency proposal for the house token using the helper function provided by Token SDK. */
        addMoveTokens(txBuilder, inputs, moneyReceived)

        /* Sign the transaction with your private */
        val initialSignedTx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to recieve signature of the buyer */
        val ftx = subFlow(CollectSignaturesFlow(initialSignedTx, listOf(foreignBankSession)))

        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */
        //subFlow(UpdateDistributionListFlow(ftx))

        /* Call finality flow to notarise the transaction */
        val stx = subFlow(FinalityFlow(ftx, listOf(foreignBankSession)))
        return ("\n" + amount + " of tokens transferred to foreignBank\nTransaction ID: " + stx.id)
    }


    @InitiatedBy(TransferCoinOverseasFlow::class)
    class HouseSaleResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            /* Receive the value in fiat currency of the token to buy */
            val price = counterpartySession.receive<Amount<Currency>>().unwrap { it }

            // TODO do something to send back the money to the sender

            //signing
            subFlow(object : SignTransactionFlow(counterpartySession) {
                @Throws(FlowException::class)
                override fun checkTransaction(stx: SignedTransaction) { // Custom Logic to validate transaction.
                }
            })
            return subFlow(ReceiveFinalityFlow(counterpartySession))
        }
    }
}