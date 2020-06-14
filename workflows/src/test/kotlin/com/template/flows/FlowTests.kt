package com.template.flows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.USD
import com.template.states.CoinState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode
    private lateinit var c: StartedMockNode

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
        c = network.createPartyNode()
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

        a.startFlow(CreateAndIssueLocalCoinFlow("ETH", 10000, holder, Amount.fromDecimal(BigDecimal.valueOf(10), net.corda.finance.USD))).getOrThrow()
        network.waitQuiescent()

        assertNotNull(b.services.vaultService.queryBy(CoinState::class.java))

        val balance = b.services.vaultService.queryBy(FungibleToken::class.java)
        assertEquals(balance.states[0].state.data.amount.quantity, 10000)
    }

    @Test
    fun `CoinToken_without_proper_inputs_fails`() {
        val holder = b.info.singleIdentity()

        val future_no_name = a.startFlow(CreateAndIssueLocalCoinFlow(
                "",
                1000,
                holder,
                Amount.fromDecimal(BigDecimal.valueOf(10), net.corda.finance.USD)
        ))
        network.waitQuiescent()
        assertFailsWith<TransactionVerificationException> { future_no_name.getOrThrow() }

        val future_price_zero = a.startFlow(CreateAndIssueLocalCoinFlow(
                "",
                1000,
                holder,
                Amount.fromDecimal(BigDecimal.valueOf(0), net.corda.finance.USD)
        ))
        network.waitQuiescent()
        assertFailsWith<TransactionVerificationException> { future_price_zero.getOrThrow() }
    }

    @Test
    fun `new_test`() {
        val company = a.info.singleIdentity()
        val foreignBank = c.info.singleIdentity()

        // give some money to the Foreign bank
        b.startFlow(IssueFiatCurrencyFlow(USD.tokenIdentifier, 10000, foreignBank))
        network.waitQuiescent()

        // give some token to the Company
        b.startFlow(CreateAndIssueLocalCoinFlow(
                "ETH",
                20,
                company,
                Amount.fromDecimal(BigDecimal.valueOf(10), net.corda.finance.USD)
        ))
        network.waitQuiescent()

        a.startFlow(TransferCoinOverseasFlow(foreignBank, 10)).getOrThrow()
        network.waitQuiescent()

    }
}