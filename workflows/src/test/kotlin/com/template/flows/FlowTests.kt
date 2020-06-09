package com.template.flows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.USD
import com.template.states.CoinState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.template.states"),
            TestCordapp.findCordapp("com.template.flows"),
            TestCordapp.findCordapp("com.template.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")),
            threadPerNode = true,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    ))
    private val a = network.createNode()
    private val b = network.createNode()
    private val c = network.createNode()
    private val d = network.createNode()

    @Before
    fun setup() {

    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `MoneyAreIssuedToRecepient`() {
        val borrower = b.info.chooseIdentityAndCert().party

        a.startFlow(IssueFiatCurrencyFlow(USD.tokenIdentifier, 100, borrower)).getOrThrow()
        network.waitQuiescent()
        val balance = b.services.vaultService.queryBy(FungibleToken::class.java)

        assertEquals(balance.states[0].state.data.amount.quantity, 100)
    }

    @Test
    fun `CoinTokenIsCreatedAndIssuedToHolder`() {
        val holder = b.info.chooseIdentityAndCert().party

        a.startFlow(CreateAndIssueLocalCoinFlow("ETH", "USD", 10000, 100, holder)).getOrThrow()
        network.waitQuiescent()

        assertNotNull(b.services.vaultService.queryBy(CoinState::class.java))

        val balance = b.services.vaultService.queryBy(FungibleToken::class.java)
        assertEquals(balance.states[0].state.data.amount.quantity, 100)
    }
}