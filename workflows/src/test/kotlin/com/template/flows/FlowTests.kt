package com.template.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
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
import kotlin.test.assertTrue

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
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.ci"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")),
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
    fun `create and share account successfully`() {
        val nodeB = b.info.singleIdentity()
        a.startFlow(CreateAndShareAccountFlow("nodeA - Account1", listOf(nodeB)))
        val accountsA = a.startFlow(OurAccounts())

        network.waitQuiescent()

        assertEquals("nodeA - Account1", accountsA.get()[0].state.data.name)

        val shareAccountInfoB = b.services.vaultService.queryBy(AccountInfo::class.java).states.single()
        assertEquals(accountsA.get()[0], shareAccountInfoB)
    }

    @Test
    fun `money are issued from Node A to account on Node B`() {
        val nodeB = b.info.singleIdentity()
        // Create account on Noda A
        a.startFlow(CreateAndShareAccountFlow("nodeA - Account1", listOf(nodeB)))
        // issue some cash to account on Node A
        a.startFlow(IssueFiatCurrencyFlow(USD.tokenIdentifier, 100, "nodeA - Account1")).getOrThrow()
        network.waitQuiescent()

        val accountsWithB = b.startFlow(OurAccounts())
        //TODO continue with fix of tests
    }

    @Test
    fun `coin token is created and issued to holder`() {
        val nodeB = b.info.singleIdentity()
        a.startFlow(CreateAndShareAccountFlow("nodeA - Account1", listOf(nodeB)))

        a.startFlow(CreateAndIssueLocalCoinFlow("ETH", 10000, "nodeA - Account1", Amount.fromDecimal(BigDecimal.valueOf(10), net.corda.finance.USD))).getOrThrow()
        network.waitQuiescent()

        assertNotNull(b.services.vaultService.queryBy(CoinState::class.java))

        val balance = b.services.vaultService.queryBy(FungibleToken::class.java)
        assertEquals(balance.states[0].state.data.amount.quantity, 10000)
    }

    @Test
    fun `Coin token without proper inputs fails`() {
        val nodeB = b.info.singleIdentity()
        a.startFlow(CreateAndShareAccountFlow("nodeA - Account1", listOf(nodeB)))

        val future_no_name = a.startFlow(CreateAndIssueLocalCoinFlow(
                "",
                1000,
                "nodeA - Account1",
                Amount.fromDecimal(BigDecimal.valueOf(10), net.corda.finance.USD)
        ))
        network.waitQuiescent()
        assertFailsWith<TransactionVerificationException> { future_no_name.getOrThrow() }

        val futurePriceZero = a.startFlow(CreateAndIssueLocalCoinFlow(
                "",
                1000,
                "nodeA - Account1",
                Amount.fromDecimal(BigDecimal.valueOf(0), net.corda.finance.USD)
        ))
        network.waitQuiescent()
        assertFailsWith<TransactionVerificationException> { futurePriceZero.getOrThrow() }
    }

    @Test
    fun `deliver token and receive money back`() {
        val company = a.info.singleIdentity()
        val foreignBank = c.info.singleIdentity()
        val amountOfTokenToSell = 20
        val tokenName = "ETH"
        val money = 10000

        val nodeA = a.info.singleIdentity()
        val nodeB = b.info.singleIdentity()
        // Create one account on Noda A and NodeB
        a.startFlow(CreateAndShareAccountFlow("nodeA - Account1", listOf(nodeB)))
        b.startFlow(CreateAndShareAccountFlow("nodeB - Account1", listOf(nodeA)))

        // account in NodeB give some money to the account of nodeA
        a.startFlow(IssueFiatCurrencyFlow(USD.tokenIdentifier, money.toLong(), "nodeA - Account1"))
        network.waitQuiescent()

        // give some token to the Company
        b.startFlow(CreateAndIssueLocalCoinFlow(
                tokenName,
                amountOfTokenToSell,
                "nodeA - Account1",
                Amount.fromDecimal(BigDecimal.valueOf(10), net.corda.finance.USD)
        ))
        network.waitQuiescent()

        val result = a.startFlow(TransferCoinOverseasFlow("nodeA - Account1", "nodeB - Account1",10)).getOrThrow()
        network.waitQuiescent()
        assertTrue { result.contains("\nCongratulations!") }
    }

/*    @Test
    fun `move token from one bank to another`() {
        val foreignBank = c.info.singleIdentity()
        val localBank = b.info.singleIdentity()
        val amountOfTokenToGive = 20
        val amountOfTokenToMove = 10
        val tokenName = "ETH"

        // give some token to the local bank
        a.startFlow(CreateAndIssueLocalCoinFlow(
                tokenName,
                amountOfTokenToGive,
                localBank,
                Amount.fromDecimal(BigDecimal.valueOf(10), net.corda.finance.USD)
        ))
        network.waitQuiescent()

        // localBank moves some tokens to foreignBank for free
        b.startFlow(MoveTokenToOtherBankFlow(foreignBank, amountOfTokenToMove)).getOrThrow()
        network.waitQuiescent()
    }*/
}