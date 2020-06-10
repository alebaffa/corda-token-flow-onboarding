package com.template.flows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.USD
import com.template.states.CoinState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.template.states"),
                TestCordapp.findCordapp("com.template.flows"),
                TestCordapp.findCordapp("com.template.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")),
                threadPerNode = true,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        ))
        a = network.createPartyNode()
        b = network.createPartyNode()
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `MoneyAreIssuedToRecepient`() {
        val borrower = b.info.singleIdentity()

        a.startFlow(IssueFiatCurrencyFlow(USD.tokenIdentifier, 100, borrower)).getOrThrow()
        network.waitQuiescent()
        val balance = b.services.vaultService.queryBy(FungibleToken::class.java)

        assertEquals(balance.states[0].state.data.amount.quantity, 100)
    }

    @Test
    fun `CoinTokenIsCreatedAndIssuedToHolder`() {
        val holder = b.info.singleIdentity()

        a.startFlow(CreateAndIssueLocalCoinFlow("ETH",DOLLARS(100) , 10000, holder)).getOrThrow()
        network.waitQuiescent()

        assertNotNull(b.services.vaultService.queryBy(CoinState::class.java))

        val balance = b.services.vaultService.queryBy(FungibleToken::class.java)
        assertEquals(balance.states[0].state.data.amount.quantity, 1000)
    }

    @Test
    fun `CoinToken_without_proper_inputs_fails`() {
        val holder = b.info.singleIdentity()

        val future_no_name = a.startFlow(CreateAndIssueLocalCoinFlow("", DOLLARS(100), 10000, holder))
        network.waitQuiescent()
        assertFailsWith<TransactionVerificationException> { future_no_name.getOrThrow() }

        val future_price_zero = a.startFlow(CreateAndIssueLocalCoinFlow("ETH", DOLLARS(100), 0, holder))
        network.waitQuiescent()
        assertFailsWith<TransactionVerificationException> { future_price_zero.getOrThrow() }
    }
}