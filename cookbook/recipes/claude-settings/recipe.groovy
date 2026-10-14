@param(['settings'], default = null, doc = 'JSON file whose top-level keys are written into .claude/settings.json')
File settings

@param(['instructions'], default = null, doc = 'Instruction text for CLAUDE.md')
String instructions

@ingredient(Claude) def claude

if (settings) claude.settings(new JsonSlurper().parse(settings))
if (instructions) claude.instruct(instructions)
