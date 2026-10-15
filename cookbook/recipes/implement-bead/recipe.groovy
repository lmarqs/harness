@param(['issue'], doc = 'The Beads issue to work on') String issue

@ingredient(Mise) def mise

mise.task('ralph:bead', [run: [['bd', 'show', issue], ['mise', 'run', 'ralph:run', issue]]])
