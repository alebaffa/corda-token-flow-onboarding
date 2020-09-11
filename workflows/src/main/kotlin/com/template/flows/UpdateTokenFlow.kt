package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken
import com.template.states.CoinState
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.ProgressTracker
import java.util.*

@StartableByRPC
class UpdateTokenFlow(val newValuation: Amount<Currency>,
                      val newName: String) : FlowLogic<String>() {

    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call(): String {
        // take the token from the vault
        val criteria = QueryCriteria.LinearStateQueryCriteria(
                relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                status = Vault.StateStatus.UNCONSUMED,
                contractStateTypes = setOf(CoinState::class.java)
        )
        val coinStateAndRef = serviceHub.vaultService.queryBy<EvolvableTokenType>(criteria).states.first()
        val newCoin = CoinState(
                ourIdentity,
                newName,
                newValuation,
                0,
                coinStateAndRef.state.data.linearId,
                listOf(ourIdentity),
                listOf(ourIdentity)
        )
        val stx = subFlow(UpdateEvolvableToken(coinStateAndRef, newCoin))

        return "Updated token. \nTransaction ID ${stx.id}"
    }
}