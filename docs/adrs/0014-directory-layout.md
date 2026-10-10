# 0014. Tool configuration, agent content and state have separate homes

## Objective

**Problem.** The tool adds configuration, agent content and clone-local state to repositories that already hold coding
agents' files, third-party tools that read fixed paths, and directories other products use. Put in the wrong place, its
files are not read by coding agents, collide with other tools, get committed when they should stay local, or cannot be
removed cleanly.

**Goal.** Coding agents and the other programs around them find what they read where they look for it, committed
configuration and local state never mix, and nothing the tool adds collides with another tool.

## Context

Where third-party tools look, checked against their documented behaviour or the installed binary:

- Claude Code (2.1.x) reads project settings, hooks and skills from `.claude/` at the repository root, and reads
  `CLAUDE.md` and the files it imports.
- Codex CLI (0.16x) reads `.codex/config.toml`, `.codex/hooks.json` and `.codex/rules/` at the repository root, and
  reads `AGENTS.md`.
- A widely used skills installer, the `skills` CLI (1.7), writes to `.agents/skills/`.
- The loop runner, ralph-tui (0.12), which runs agents unattended one task after another, reads its configuration only
  from a fixed directory under its working directory and offers no flag to move it.
- The task tracker, Beads (1.3.1), a clone-local task database, finds its directory by walking up from the working
  directory; an environment variable can move it.

What repositories already hold before they adopt the tool:

- Some use `.agents/` as their canonical agent directory, linked from the native agent directories. Its skills, memory
  and review guidance serve people and agents in repositories that never adopt this tool.
- Where `.claude/skills` exists, it has one of three shapes: a link to a whole directory such as `.agents/skills`, a
  real directory of skills people wrote, or a directory of per-entry links. For example, `acme/api` links
  `.claude/skills` to `.agents/skills`, while `acme/web` keeps hand-written skills in a real `.claude/skills` directory.
- Some keep an agent's own worktree directory under `.claude/`.
- Some commit the task tracker's directory to git.

Other forces:

- Committed configuration is reviewed; clone-local state is ignored and can hold uncommitted work.
- People, workers and third-party tools use parts of the tool's clone-local state directly: they work inside the working
  copies the tool creates, and a loop runner runs inside them.
- Removal deletes what the manifest lists ([0007](0007-ownership.md)).
- A directory name at the repository root is not reserved: any program or person may create one, so a directory the
  tool would use can already exist when it arrives.
- Every ingredient and every station comes from a cookbook: it is fetched, verified by its content hash on every load,
  and is the same bytes for every repository that pins that hash ([0021](0021-release-trust.md),
  [0022](0022-tool-integration.md)).
- The XDG Base Directory Specification (0.8) defines a per-user cache directory, `$XDG_CACHE_HOME`, by default
  `~/.cache`, for data a program can fetch again.
- A worker can edit anything in the working tree it works in ([0011](0011-safety-invariants.md)).

## Decision

1. The native directories of worker programs stay where those programs look (Context). They hold rendered configuration
   ([0008](0008-native-configuration.md)) and per-entry links into `.agents/`. Existing real directories and existing
   whole-directory links stay as they are.
2. `.agents/` holds agent content only: skills, including the skill the tool ships ([0002](0002-division-of-work.md)),
   memory, commands and review guidance. It stays meaningful to people, coding agents and other tools without the tool.
3. `.cook/` holds the tool's committed configuration: the repository's data ([0006](0006-repository-contract.md)),
   the manifest ([0007](0007-ownership.md)) and the pin of the engine release ([0017](0017-engine-version.md)). Every
   path in it is tracked; none is ignored.
4. The tool's clone-local state lives at paths the repository's data sets, with defaults under `.work/`. No state path
   is inside `.cook/` or `.agents/`, and state is never committed.
5. Third-party state and configuration stay where their tool requires them and are never relocated through environment
   variables or path overrides: the task tracker's directory at the root of the primary checkout, found by its upward
   walk from nested working copies; the loop runner's configuration in the fixed directory it reads.
6. Ignore rules are managed blocks, one set per capability ([0018](0018-conventions.md)), and omit any path the
   repository tracks.
7. A repository holds none of the ingredients and stations it pins ([0006](0006-repository-contract.md)). Each lives in
   the user's cache directory, outside every repository, under its content hash, and one copy serves every repository on
   the machine that pins that hash.

## Reasons

- **1.** Worker programs read only these locations, so rendered configuration must be there. Linking per entry adds the
  tool's skill without replacing anything: where a native skills directory is a real directory (Context), replacing it
  with a link would move files people own, and a whole-directory link already exposes everything in `.agents/`.
- **2.** `.agents/` is a convention shared across tools and repositories, including repositories that use it without
  this tool. Content there must stay meaningful when the tool is absent or removed. Keeping the tool's configuration out
  of it means readers of agent content never meet files only the tool reads, and removal never has to pick files out of
  a directory others write. The shipped skill lives there because that is where coding agents and skills installers find
  skills.
- **3.** One directory for the tool's configuration is one place to review and one directory removal deletes. Holding
  only tracked paths keeps its meaning simple: what is in it is what the repository committed, with no ignore rules
  inside a committed tree.
- **4.** People and third-party tools work directly in the tool's state, chiefly its working copies, and repositories
  have their own conventions for where those go (some keep a coding agent's working copies under its native directory,
  as Context shows). A configurable path with a default lets each repository keep its convention, while the default
  gives one root to ignore. State inside `.cook/` would put ignored paths into the committed configuration directory,
  contradicting point 3. State inside `.agents/` would show clone-local files to every tool that reads agent content.
  Committed state would publish one clone's uncommitted work.
- **5.** The loop runner's configuration location cannot be moved, so its configuration, whether a person wrote it or a
  station's template wrote it ([0013](0013-organization-content.md)), lives where the loop runner looks, and a committed
  file reaches every working copy through version control. Relocating the task tracker through an override would make
  every invocation depend on that override, and people who run the tracker directly would see different state.
- **6.** One set of ignore blocks per capability lets one capability be removed without un-ignoring another's paths
  ([0018](0018-conventions.md)). A tracked path in an ignore rule would stop new files in a committed directory, such as
  the task tracker's directory in a repository that commits it, from being added.
- **7.** An ingredient in the working tree would be a description the worker working there can edit before the next
  attempt reads it ([0022](0022-tool-integration.md)), and a copy of an ingredient or a station in every repository that
  uses it would be one more copy to keep equal (Context). The user's cache directory is where data a program can fetch
  again belongs (Context), outside every working tree, and a copy there that anyone changes, a worker included, fails
  the hash check on its next load instead of being used. Keying by content hash lets every repository that pins the same
  ingredient or station share one copy, and two versions never overwrite each other. The trade: an ingredient or a
  station is on a machine only after someone fetched it there, and clearing the cache removes it until the next fetch.

## Rejected alternatives

### One directory for everything, state included

At its best, every file the tool touches is in one place, one ignore rule covers the state, and removal deletes one
directory. It loses because it mixes committed configuration with ignored state. It also fixes one location for working
copies in every repository, and working copies that people and third-party tools use directly would sit inside a
directory that removal must either delete with their contents or leave half removed.

### Configuration inside `.agents/`

At its best, the tool's configuration and the content coding agents read sit in one committed directory that
repositories already use, and the repository root gains no directory. It loses because the tool's configuration would
sit among content the tool does not own and that must stay meaningful without the tool, and removal would pick files out
of a directory others write.

### One fixed root for state

At its best, there is one ignore rule and one layout to test. It loses because repositories keep working copies where
their own conventions put them, and people and other tools work inside those copies directly.

### Relocate third-party state through overrides

At its best, the repository root stays tidy. It loses because every invocation needs the override, and people who run
those tools directly see different state.

### Ingredients and stations kept in the repository

The repository commits each ingredient and station it uses beside its data. At its best, a clone carries everything it
needs, renders with no fetch, and a reviewer reads the ingredient's description in the diff that adds it. It loses for
the reason [0022](0022-tool-integration.md) gives against descriptions read from the working tree, and because every
repository that uses an ingredient or a station would carry its own copy, which local edits make diverge
([0006](0006-repository-contract.md)).

### Whole-directory links from native directories

At its best, each directory needs one link. It loses because where a native skills directory is already a real directory
(Context), replacing it moves files people own.

## Consequences

Costs:

- The repository root holds several dot-directories, `.cook/` among them.
- The layout is set from outside; a change in a third-party tool's discovery rule forces a change here.
- Per-entry links need a filesystem with symbolic links.
- An ingredient or a station is absent on a machine until someone fetches it there.
- Configurable state paths multiply the layouts the tool renders and tests.
- A `.cook/` the tool did not create is foreign and refused ([0012](0012-write-boundary.md)), so a repository whose
  root already holds one cannot adopt this tool until it is moved.

Must be maintained:

- The table of third-party locations and the tool versions they were checked against.
- The rule that `.cook/` holds no state and no ignored path, and the validation that rejects a state path inside
  `.cook/` or `.agents/`.

## Confirmation

- The health check verifies that every link resolves.
- Fixtures with each of the three skills-directory shapes keep their shape after adoption.
- A test asserts that no command of the tool sets an environment variable or path override that moves a third-party
  tool's directory or configuration.
- A test asserts that every path under `.cook/` of an adopted fixture is tracked and none is ignored.
- A fixture with non-default state paths writes state only at those paths.
- Validation refuses a fixture whose state path points inside `.cook/` or `.agents/`.
- A fixture that commits a directory the tool would ignore gets no ignore entry for it.
- A test asserts that fetching an ingredient or a station writes only under the user's cache directory, and that an
  adopted fixture holds none of the ingredients and stations it pins.
