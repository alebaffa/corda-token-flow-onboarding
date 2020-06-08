package com.template.states

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.r3.corda.lib.tokens.contracts.FungibleTokenContract
import net.corda.core.contracts.Contract

/**
 * This doesn't do anything over and above the [EvolvableTokenContract].
 */
class ExampleEvolvableTokenTypeContract : FungibleTokenContract(), Contract