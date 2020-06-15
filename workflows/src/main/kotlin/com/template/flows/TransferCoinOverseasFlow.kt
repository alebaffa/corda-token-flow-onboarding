package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.selection.database.config.MAX_RETRIES_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.PAGE_SIZE_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.RETRY_CAP_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.RETRY_SLEEP_DEFAULT
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
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
class TransferCoinOverseasFlow(val bank: Party,
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
                status = Vault.StateStatus.UNCONSUMED
        )
        // take my StateAndRef of my tokens in my vault
        val coinStateAndRef: StateAndRef<FungibleToken> = serviceHub.vaultService.queryBy<FungibleToken>(criteria).states.single()
        // create session with counterparty
        val session = initiateFlow(bank)
        // set how many tokens I want to sell
        val amountOfToken = amount of coinStateAndRef.state.data.issuedTokenType
        // create the token move proposal
        addMoveFungibleTokens(txBuilder, serviceHub, Amount(
                amount.toLong(),
                coinStateAndRef.state.data.tokenType
        ), bank, ourIdentity)
        // send the amount of token I want to sell
        session.send(amountOfToken)
        // ask the recipient to send back its StateAndRef of the money in his vault
        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(session))
        // receive the actual money from the recipient
        val moneyReceived = session.receive<List<FungibleToken>>().unwrap { it }
        // move the money to my vault
        addMoveTokens(txBuilder, inputs, moneyReceived)
        /* Sign the transaction with the private key */
        val stx = collectSignatures(txBuilder, session)
        return ("\nCongratulations! $amount of ETH have been sold to ${bank.name.organisation}" +
                "\nTransaction ID: ${stx.id}")
    }

    @Suspendable
    private fun collectSignatures(txBuilder: TransactionBuilder, session: FlowSession): SignedTransaction {
        val initialSignedTx = serviceHub.signInitialTransaction(txBuilder)

        /* Collect the signatures and finality */
        val signedTx = subFlow(CollectSignaturesFlow(initialSignedTx, listOf(session)))
        subFlow(UpdateDistributionListFlow(signedTx))
        return subFlow(FinalityFlow(signedTx, listOf(session)))
    }


    @InitiatedBy(TransferCoinOverseasFlow::class)
    class ResponderFlow(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            /* Receive the amount of tokens to buy */
            val amount = counterpartySession.receive<Amount<IssuedTokenType>>().unwrap { it }
            // retrieve money from vault
            val (partyAndAmount, tokenSelection) = retrieveMoneyFromVault(amount)
            // generate the money move proposal for the initiator
            val inputsAndOutputs: Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> =
                    tokenSelection.generateMove(listOf(Pair(partyAndAmount.party, partyAndAmount.amount)), ourIdentity)
            // send StateAndRef of money to initiator
            subFlow(SendStateAndRefFlow(counterpartySession, inputsAndOutputs.first))
            // send actual money back to initiator
            counterpartySession.send(inputsAndOutputs.second)
            //sign transaction
            subFlow(object : SignTransactionFlow(counterpartySession) {
                @Throws(FlowException::class)
                override fun checkTransaction(stx: SignedTransaction) { // Custom Logic to validate transaction.
                }
            })
            //finalization
            return subFlow(ReceiveFinalityFlow(counterpartySession))
        }

        @Suspendable
        private fun retrieveMoneyFromVault(amount: Amount<IssuedTokenType>): Pair<PartyAndAmount<TokenType>, DatabaseTokenSelection> {
            val criteria = QueryCriteria.FungibleStateQueryCriteria(
                    relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                    status = Vault.StateStatus.UNCONSUMED
            )

            val moneyInVault: StateAndRef<FungibleToken> = serviceHub.vaultService.queryBy<FungibleToken>(criteria).states.single()
            val tokenBack = moneyInVault.state.data

            val partyAndAmount = PartyAndAmount(counterpartySession.counterparty, Amount(amount.quantity, tokenBack.tokenType))
            val tokenSelection = DatabaseTokenSelection(serviceHub, MAX_RETRIES_DEFAULT, RETRY_SLEEP_DEFAULT, RETRY_CAP_DEFAULT, PAGE_SIZE_DEFAULT)
            return Pair(partyAndAmount, tokenSelection)
        }
    }
}