package com.template.flows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.USD
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FlowTests {
    val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.template.states"),
            TestCordapp.findCordapp("com.template.flows"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")),
            threadPerNode = true
    ))
    val a = network.createNode()
    val b = network.createNode()

    @Before
    fun setup() {

    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `MoneyAreIssuedToRecepient`() {
        val borrower = b.info.chooseIdentityAndCert().party

        a.startFlow(IssueUSDFlow(USD.tokenIdentifier, 100, borrower)).getOrThrow()
        network.waitQuiescent()
        val balance = b.services.vaultService.queryBy(FungibleToken::class.java)

        assertEquals(balance.states[0].state.data.amount.quantity, 100)
    }
}