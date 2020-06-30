package com.template.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.template.contracts.CoinContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.util.*

@BelongsToContract(CoinContract::class)
data class CoinState(val issuer: AbstractParty,
                     val name: String, // the name of the coin
                     val valuation: Amount<Currency>,
                     override val fractionDigits: Int = 0,
                     override val linearId: UniqueIdentifier,
                     override val maintainers: List<Party>
) : EvolvableTokenType()