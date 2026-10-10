# 0014. Tool configuration, agent content and state have separate homes

## Objective

**Problem.** The tool adds configuration, agent content and clone-local state to repositories that already hold agent
files, third-party tools that read fixed paths, and directories other products use. Put in the wrong place, its files
are not read by agents, collide with other tools, get committed when they should stay local, or cannot be removed
cleanly.

**Goal.** Agents find what they read where they look for it, committed configuration and local state never mix, and
nothing the tool adds collides with another tool.

## Context

Where third-party tools look, checked against their documented behaviour or the installed binary:

- Claude Code (2.1.x) reads project settings, hooks and skills from `.claude/` at the repository root, and reads
  `CLAUDE.md` and the files it imports.
- Codex CLI (0.16x) reads `.codex/config.toml`, `.codex/hooks.json` and `.codex/rules/` at the repository root, and
  reads `AGENTS.md`.
- A widely used skills installer writes to `.agents/skills/`.
- The loop runner (0.12) reads its configuration only from a fixed directory under its working directory and offers no
  flag to move it.
- The task tracker (1.3.1) finds its directory by walking up from the working directory; an environment variable can
  move it.

Evidence from 51 surveyed repositories:

- 12 use `.agents/` as their canonical agent directory, linked from the native agent directories. Its skills, memory
  and review guidance serve people and agents in repositories that never adopt this tool.
- `.claude/skills` is a whole-directory link in 13 repositories, a real directory in 10, and a set of per-entry links
  in 1.
- An agent's own worktree directory exists under `.claude/` in 28.
- One repository commits its task tracker's directory to git.

Other forces:

- Committed configuration is reviewed; clone-local state is ignored and can hold uncommitted work.
- People, agents and third-party tools use parts of the tool's clone-local state directly: they work inside the working
  copies the tool creates, and the loop runner reads its configuration inside them.
- Removal deletes what the manifest lists ([0007](0007-ownership.md)).
- Another product, a CI/CD platform, uses a `.harness/` directory at the repository root for pipeline definitions and
  ships a command-line program named `harness`. Its users and this tool's users do not overlap.

## Decision

1. Native agent directories (`.claude/`, `.codex/`) stay where agents look. They hold rendered configuration
   ([0008](0008-native-configuration.md)) and per-entry links into `.agents/`. Existing real directories and existing
   whole-directory links stay as they are.
2. `.agents/` holds agent content only: skills, including the skill the tool ships ([0002](0002-division-of-work.md)),
   memory, commands and review guidance. It stays meaningful to people, agents and other tools without the tool.
3. `.harness/` holds the tool's committed configuration: the repository's data ([0006](0006-repository-contract.md)),
   the manifest ([0007](0007-ownership.md)) and the pin of the engine release ([0017](0017-engine-version.md)). Every
   path in it is tracked; none is ignored.
4. The tool's clone-local state lives at paths the repository's data sets, with defaults under `.work/`. No state path
   is inside `.harness/` or `.agents/`, and state is never committed.
5. Third-party state stays where its tool requires it and is never relocated through environment variables or path
   overrides: the task tracker's directory at the root of the primary checkout, found by its upward walk from nested
   working copies; the loop runner's configuration only inside working copies the tool creates for it.
6. Ignore rules are managed blocks, one set per capability ([0018](0018-conventions.md)), and omit any path the
   repository tracks.

## Reasons

- **1.** Agents read only these locations, so rendered configuration must be there. Linking per entry adds the tool's
  skill without replacing anything: in 10 surveyed repositories `.claude/skills` is a real directory, and replacing it
  with a link would move files people own; the 13 whole-directory links already expose everything in `.agents/`.
- **2.** `.agents/` is a convention shared across tools and repositories, including the 12 surveyed repositories that
  use it without this tool. Content there must stay meaningful when the tool is absent or removed. Keeping the tool's
  configuration out of it means readers of agent content never meet files only the tool reads, and removal never has
  to pick files out of a directory others write. The shipped skill lives there because that is where agents and the
  skills installer find skills.
- **3.** One directory for the tool's configuration is one place to review and one directory removal deletes. Holding
  only tracked paths keeps its meaning simple: what is in it is what the repository committed, with no ignore rules
  inside a committed tree.
- **4.** People and third-party tools work directly in the tool's state, chiefly its working copies, and repositories
  have their own conventions for where those go (28 surveyed repositories keep an agent's worktrees under `.claude/`). A
  configurable path with a default lets each repository keep its convention, while the default gives one root to ignore.
  State inside `.harness/` would put ignored paths into the committed configuration directory, contradicting point 3.
  State inside `.agents/` would show clone-local files to every tool that reads agent content. Committed state would
  publish one clone's uncommitted work.
- **5.** The loop runner's configuration location cannot be moved, so the only way to give it tool-generated
  configuration without touching a root configuration people use directly is a working copy the tool creates.
  Relocating the task tracker through an override would make every invocation depend on that override, and people who
  run the tracker directly would see different state.
- **6.** One set of ignore blocks per capability lets one capability be removed without un-ignoring another's paths
  ([0018](0018-conventions.md)). A tracked path in an ignore rule would stop new files in a committed directory, such
  as the task tracker's directory in the one repository that commits it, from being added.

## Rejected alternatives

### One directory for everything, state included

At its best, every file the tool touches is in one place, one ignore rule covers the state, and removal deletes one
directory. It loses because it mixes committed configuration with ignored state. It also fixes one location for working
copies in every repository, and working copies that people and third-party tools use directly would sit inside a
directory that removal must either delete with their contents or leave half removed.

### Configuration inside `.agents/`

At its best, everything agent-related and committed is in one directory that 12 surveyed repositories already use, and
the repository root gains no directory. It loses because the tool's configuration would sit among content the tool
does not own and that must stay meaningful without the tool, and removal would pick files out of a directory others
write.

### One fixed root for state

At its best, there is one ignore rule and one layout to test. It loses because repositories keep working copies where
their own conventions put them, and people and other tools work inside those copies directly.

### Relocate third-party state through overrides

At its best, the repository root stays tidy. It loses because every invocation needs the override, and people who run
those tools directly see different state.

### Whole-directory links from native directories

At its best, each directory needs one link. It loses because in 10 surveyed repositories the target is a real directory,
and replacing it moves files people own.

## Consequences

Costs:

- The repository root holds several dot-directories, `.harness/` among them.
- The layout is set from outside; a change in a third-party tool's discovery rule forces a change here.
- Per-entry links need a filesystem with symbolic links.
- Configurable state paths multiply the layouts the tool renders and tests.
- `.harness/` and the program name are shared with another product. A foreign `.harness/` is refused
  ([0012](0012-write-boundary.md)), so a repository that uses the other product cannot adopt this tool. The committed
  wrapper always runs its own pinned binary ([0017](0017-engine-version.md)), so only a global command can be shadowed
  by the other product's program on `PATH`.

Must be maintained:

- The table of third-party locations and the tool versions they were checked against.
- The rule that `.harness/` holds no state and no ignored path, and the validation that rejects a state path inside
  `.harness/` or `.agents/`.

## Confirmation

- The health check verifies that every link resolves.
- Fixtures with each of the three skills-directory shapes keep their shape after adoption.
- A test asserts that loop-runner configuration is written only inside working copies the tool created.
- A test asserts that every path under `.harness/` of an adopted fixture is tracked and none is ignored.
- A fixture with non-default state paths writes state only at those paths.
- Validation refuses a fixture whose state path points inside `.harness/` or `.agents/`.
- A fixture that commits a directory the tool would ignore gets no ignore entry for it.
