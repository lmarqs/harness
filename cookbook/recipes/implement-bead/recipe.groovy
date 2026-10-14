@param(['issue'], doc = 'The Beads issue to work on') String issue
@param(['run'], doc = 'The command that works on the issue; it must name the issue') String run

@ingredient(Mise) def mise

if (!run.contains(issue)) throw new IllegalArgumentException('run must select the supplied issue')
mise.task('ralph:bead', "bd show ${Mise.quote(issue)} && ${run}")
