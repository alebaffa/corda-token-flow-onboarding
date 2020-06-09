package com.template.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.template.contracts.CoinContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.schemas.StatePersistable
import java.math.BigDecimal

@BelongsToContract(CoinContract::class)
data class CoinState(val issuer: Party,
                     val name: String, // the name of the coin
                     val currency: String, // the currency of its underline price in fiat currency
                     val price: Int, // its price in fiat currency
                     override val fractionDigits: Int = 0,
                     override val linearId: UniqueIdentifier,
                     override val maintainers: List<Party>
                     ) : EvolvableTokenType(), StatePersistable