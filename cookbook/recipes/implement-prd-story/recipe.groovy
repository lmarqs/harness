@param(['prd'], default = 'tasks/prd.json', doc = 'The source PRD') File prd
@param(['story'], doc = 'The id of the one story to keep') String story
@param(['selected'], default = '.ralph-tui/selected-prd.json', doc = 'Where the reduced PRD is written') String selected

@ingredient(Ralph) def ralph

def document = new JsonSlurper().parse(prd)
def stories = document.userStories.findAll { it.id == story }
if (stories.size() != 1) throw new IllegalArgumentException("${prd} must contain exactly one story ${story}")

ralph.prd = selected
ralph.prdContent = JsonOutput.prettyPrint(JsonOutput.toJson(document + [userStories: stories])) + '\n'
