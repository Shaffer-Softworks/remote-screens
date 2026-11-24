package tech.shaffersoftworks.remotescreens.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class RemoteScreensRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("remotescreens")

    override fun instance(): RuleSet = RuleSet(
        id = ruleSetId,
        rules = listOf(::StatementSpacingRule, ::NameCallArgumentsRule, ::DocumentFlashRule),
    )
}
