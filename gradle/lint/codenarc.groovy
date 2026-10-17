ruleset {
  ruleset('rulesets/basic.xml')
  ruleset('rulesets/braces.xml')
  ruleset('rulesets/imports.xml')
  ruleset('rulesets/unused.xml')
  ruleset('rulesets/formatting.xml') {
    Indentation { spacesPerIndentLevel = 2 }
    ClassStartsWithBlankLine { blankLineRequired = false }
    ClassEndsWithBlankLine { blankLineRequired = false }
    SpaceAfterOpeningBrace { ignoreEmptyBlock = true }
    SpaceBeforeClosingBrace { ignoreEmptyBlock = true }
    SpaceAroundMapEntryColon {
      characterBeforeColonRegex = /\S/
      characterAfterColonRegex = /\s/
    }
  }
  rule('file:gradle/lint/BlankLinesAroundBlocksRule.groovy')
  rule('file:gradle/lint/ChainedCallsRule.groovy')
}
