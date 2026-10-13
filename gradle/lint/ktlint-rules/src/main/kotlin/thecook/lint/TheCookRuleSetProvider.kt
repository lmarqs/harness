package thecook.lint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/** Registers The Cook's ktlint rules under the `thecook` rule set. */
class TheCookRuleSetProvider : RuleSetProviderV3(RuleSetId("thecook")) {
  override fun getRuleProviders(): Set<RuleProvider> = setOf(RuleProvider { MandatoryBracesRule() })
}
