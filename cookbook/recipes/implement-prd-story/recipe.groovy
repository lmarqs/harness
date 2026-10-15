@param(['prd'], default = 'tasks/prd.json', doc = 'The source PRD') String prd
@param(['story'], doc = 'The id of the one story to keep') String story
@param(['selected'], default = '.ralph-tui/selected-prd.json', doc = 'Where the reduced PRD is written') String selected

@ingredient(Ralph) def ralph

ralph.focus(prd, story, selected)
