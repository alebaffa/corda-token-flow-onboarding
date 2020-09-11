package com.template.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class IssuanceRecordContract : Contract {

    companion object
    {
        const val ID = "com.template.contracts.IssuanceRecordContract"
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when(command.value) {
            is Commands.Issue -> requireThat {
                // Verify input and output counts
                "Input should be empty." using (tx.inputs.isEmpty())
            }
        }
    }
}