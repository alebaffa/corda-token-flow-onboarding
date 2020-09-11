package com.template.states

import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.template.contracts.IssuanceRecordContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

@BelongsToContract(IssuanceRecordContract::class)
class IssuanceRecord(
        val holder: AbstractParty,
        val amount: Amount<IssuedTokenType>
) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(amount.token.issuer)
}