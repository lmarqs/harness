# Setting up your own way of working in a shared repository, and leaving it clean

## Reversibility

Two parts of this decision are one-way doors; the rest are two-way.

- **The repository contract.** This covers the files a repository keeps for the tool and the marked parts it writes
  into shared files, such as ignore files and agent settings. It becomes a contract the moment one repository adopts
  it, and changing it after that is a migration in each repository. Today no repository has adopted the tool, and the
  tool has no code (0 files other than Markdown and images in this repository; `git ls-files`, 2026-10-11; measured).
  So the contract costs only a document edit now, and this is the cheapest moment to get it right.
- **The boundary of what a person publishes.** A person's own setup backed up to a public remote cannot be unpublished
  (constraint C5). The line between what a person shares and what they keep private is a security boundary.
- **Two-way.** Names, the order in which the parts are built, file locations inside the tool's own directory, and
  which file formats are supported first.

## Context

### Current state

**The tool exists only on paper.** This repository holds 71 Markdown files with 21,991 lines, including 28 ADRs, and
no code (`git ls-files`, 2026-10-11; measured). Nobody has used it. The documented design puts a fixed software
development lifecycle inside the binary: eight stages, guards between them, and a proof bound to each change that is
replayed before work counts as done (ADR 0015). On top of that come safety rules the binary enforces on the programs
it starts: refusing bypass arguments, a tamper check, jails (ADR 0011, ADR 0027).

**The workflow that motivated it.** The author runs coding agents unattended with ralph-tui. That is a loop runner:
it hands Claude Code or Codex one task at a time from a tracker, here Beads (`bd`). Setting that up by hand in each
repository was the reason the project started, and configuring it was a nightmare. The workflow lives in three
private repositories of one team, called here the reference, port 1 and port 2:

| Repository | Tracked files | How the workflow is kept | Unattended workers |
| --- | --- | --- | --- |
| reference | 7,456 | a Python engine of 1,180 lines, committed in the repository | permission checks on |
| port 1 | 1,221 | shell tasks copied by hand | permission checks off: `skipPermissions = true`, `fullAuto = true` in a committed `.ralph-tui/config.toml` |
| port 2 | 449 | shell tasks and a forked renderer, copied by hand | the same settings, also committed |

(Tracked files: `git ls-files | wc -l`, 2026-10-11. Engine size and worker settings: survey of 2026-10-09. Both
measured.)

**One person's way of working stays in the shared repository.** Committed files under each tool's directory
(`git ls-files`, 2026-10-11; measured):

| Repository | Committed tool directories (files) | Committed symlinks | Notable |
| --- | --- | --- | --- |
| reference | `.agents` 135, `.claude` 7, `.codex` 5, `.vscode` 1 | 17 | all 12 committed entries under `.claude/` and `.codex/` are links into `.agents/`; 5 launcher files under `.agents/ralph/` |
| port 1 | `.agents` 417, `.beads` 10, `.claude` 3, `.codex` 4, `.cursor` 2, `.ralph-tui` 2 | 3 | `.beads/` includes four git hooks; `.cursor/rules/beads.mdc` was written by the tracker |
| port 2 | `.agents` 13, `.claude` 3, `.codex` 4, `.ralph-tui` 2, `.vscode` 2 | 6 | `.ralph-tui/config.toml` with permission checks off |

The bypass reached ports 1 and 2 by this route. One person's loop-runner config was committed, so everyone who runs
ralph-tui in those clones inherits it. Port 2's policy file says the bypass is intended, because "a worker commits,
reaches the clone's Beads database and starts a Docker stack, which a sandbox refuses". The reference handles all
three without the bypass:

- the launcher commits after its checks;
- the Beads database is a writable root;
- the network is open for the Docker services.

**An ignored file is still in the clone, and it leaves in images.** The reference clone holds `.work/` (3.8 GB of
worktrees and receipts), `.beads/` (76 MB) and `.ralph-tui/` (112 KB) outside commits (`du -sh`, 2026-10-11; measured).
Its Dockerfile runs `COPY . .`. Its `.dockerignore` lists `.claude/`, `.vscode/` and `.mise/`, but none of `.work/`,
`.beads/`, `.codex/`, `.agents/` or `.ralph-tui/`. Docker reads only `.dockerignore` (constraint C3). So an image built
locally from that clone carries the session state. That is inferred from the test behind C3; nobody ran a build of the
reference itself. A clone in CI has none of that state.

**Files divide along two axes.** The first axis is whose a file is: the project's or one person's. The second is how
long it lives: persistent (committed or backed up) or ephemeral (it lives in one clone or one session).

| | Persistent | Ephemeral |
| --- | --- | --- |
| Project | the team's own files, and the files derived from sources the repository declares | the team's build outputs and caches |
| Personal | the person's own setup | the session's state |

Contamination is personal content becoming persistent in the project, or leaving in an image. The program decides
where a file lives, not these axes (constraint C1). So personal files often sit at project paths, such as
`.claude/settings.local.json`, `.vscode/settings.json` and `.idea/workspace.xml`.

**Files derived from declared sources are already committed.** The reference keeps one policy file, and a renderer
writes it into Claude Code's `.claude/settings.json` and into Codex's `.codex/rules/workflow.rules`. The rendered
files are committed, so an agent session reads them without running anything. The three decisions map one to one:
Claude's allow, ask and deny become Codex's allow, prompt and forbidden. A raw Claude pattern with `*` anywhere has no
Codex form, and the renderer skips it. The renderer holds code for each of the two agents (`claude_rules`,
`codex_rules`; measured).

**Team config already in place.** The tool has to work alongside:

- `.editorconfig` in all three repositories, with `root = true` in ports 1 and 2 only;
- ESLint config: `.eslintrc.js` in the reference, `eslint.config.mjs` in port 1;
- `.pylintrc` in port 2;
- shared editor files: `.vscode/launch.json` in the reference and port 2, plus `.vscode/tasks.json` in port 2;
- `.github/` and `.mise/`;
- `.env.test`, which the reference ships in its image on purpose: "CI runs the test suite in the image".

The root `.gitignore` files are 97 lines (reference), 248 (port 1) and 28 (port 2), with 19, 83 and 5 comment lines.
The comments carry reasons, some of them about safety. Port 1's rule for a database dump says a staged backup "is a
production data leak one `git add -A` wide" (2026-10-11; measured).

**A person's own setup does not follow them.** On the author's machine (2026-10-11; measured):

- **Ignore rules.** Git's global ignore file, `~/.config/git/ignore`, has one line. The same 10 Claude Code state
  entries are repeated in `.git/info/exclude` of each of the three clones, so a new clone or machine starts without
  them.
- **`~/.claude`.** It is 1.9 GB, of which 1.8 GB is session transcripts. It also holds credentials, settings, skills
  and 35 MB of plugins. The OAuth account and per-project trust live in `~/.claude.json` (171 KB).
- **Git hooks.** No global `core.hooksPath` is set. Port 1's clone sets a local one, `.beads/hooks`, which replaces
  any global one there.
- **Editor settings.** VS Code's user settings live on the Windows host; the WSL side's machine settings are empty.

**Build and CI files are written by hand too.**

- **Dockerfiles.** The reference has one Dockerfile with a single stage, and it copies the whole context. Port 2 has 5
  Dockerfiles and 2 `.dockerignore` files, each relative to its own build context (2026-10-11; measured).
- **CI workflows.** mise 2026.6.13 ships generators for some of these files.
  `mise generate github-action --write` writes `.github/workflows/<name>.yml`, a workflow that runs one mise task.
  Others write a devcontainer and a git pre-commit hook (`mise generate --help`; measured).
- **Credentials.** A Docker build can read them without keeping them, which C3 records.

**Definitions live at three levels.** Some rules are an organization's, such as never shipping agent session state.
Some are one project's, such as which editor files it shares. Some are one person's, such as their own editor's
leftovers. The author's cookbook, `lmarqs/the-cookbook`, is the only shared source today, and it is public.

### Current usage

| Role (what they do with the system) | What they do today | Through what | How often or how much (source) |
| --- | --- | --- | --- |
| The cook: a developer bringing their own way of working | copies the workflow into each repository by hand and commits it | editor, git | 3 copies in 10 days, 3 dialects (survey, 2026-10-09; measured) |
| Teammate who works their own way | inherits what was committed: settings, links, loop-runner config, tracker hooks and rules | `git clone` | everyone who clones ports 1 and 2 |
| Agent session (Claude Code, Codex) | reads settings, rules and hooks at fixed paths | `.claude/`, `.codex/` | every session |
| Unattended worker | implements one task with the settings the loop runner passes | ralph-tui | once per task |
| CI pipeline and image build | checks out the repository and builds images from the build context | `.gitignore`, `.dockerignore` | every push |
| The cook on a new clone or machine | retypes personal ignores, hooks, and agent and editor settings | `.git/info/exclude`, home directory files | 10 entries repeated in 3 clones (2026-10-11; measured) |
| Team keeping build and CI files | writes Dockerfiles, `.dockerignore` files and CI workflows by hand in each repository | editor | reference: 1 Dockerfile; port 2: 5 Dockerfiles, 2 `.dockerignore` files (2026-10-11; measured) |
| Cookbook author or organization | publishes definitions that others copy | a public cookbook | one cookbook, the author's |

### Problem

People who share a repository each want to work their own way. Setting up one way of working, such as a loop runner
with two agents and a tracker, is done by hand inside the repository, and it stays there. One person's tool config is
committed, links and tracker hooks reach every clone, and in two of three repositories teammates inherit a loop runner
with permission checks off. What is not committed still sits in the clone, 3.8 GB in one of them, and it leaves in
locally built images. No repository says which tool files it means to share, and a person's own setup does not follow
them: the same ignore entries are retyped in every clone, and a new machine starts empty. The files a team builds and
ships with are kept the same way: each repository writes its own Dockerfiles, ignore files and CI workflows, so a
better way learned in one reaches the others only by copying.

### Goals

| Goal | Who benefits | How we will know |
| --- | --- | --- |
| G1. A teammate works in a shared repository without inheriting anything from another person's way of working | teammate, CI | committed files that belong to one person's tools: 0 after adoption (today: the contamination table above); images built from any clone carry no session state |
| G2. A cook starts their own way of working in any repository with one request, and when they leave, nothing of it remains but the work | the cook | one request to start; after the session, the clone holds the work as commits and no session state |
| G3. Anyone reading a repository can tell which tool files it shares, and why | teammate, reviewer | every shared path and every exclusion has a reason written in the repository |
| G4. A cook's own setup follows them to every clone and every machine | the cook | a new machine is set up in one step; each personal rule is written once (today: 10 entries in each of 3 clones) |
| G5. What a cook keeps private stays private | the cook | no credential, transcript or private setting is ever published |
| G6. A new program, or a new release of one, reaches the cooks who use it without waiting for a release of the tool | the cooks, the tool's maintainer | releases of the tool caused by a program change: 0 |
| G7. A teammate who does not use the tool works exactly as before | teammate, CI | a clone, its CI and its images behave the same with and without the tool installed |
| G8. An organization keeps its shared rules and standards in one place, and each of its repositories takes them in one reviewed change | organization, team keeping build and CI files | a rule or standard changed once, such as an ignore rule, a Dockerfile stage or a CI workflow, reaches a repository as one diff in one review |

### Stakeholders

| Role (what they do with the system) | What they need from this decision | Who speaks for them |
| --- | --- | --- |
| The cook | to start their way of working in one request, have their setup on every machine, and keep private what is private | lmarqs |
| Teammate who works their own way | nothing inherited, nothing broken, no tool to install | the first adopting teams |
| Teammate who shares tool files on purpose (negative) | today they commit any editor or agent file they like; under a default ban, each new shared file costs one allow line and a review | the first adopting teams |
| Owner of a repository whose committed loop-runner config turns permission checks off (negative) | keeps that config working; a kitchen that keeps personal config out of commits removes the shared copy | the owners of ports 1 and 2 |
| Agent session and unattended worker | settings at the paths the program reads, written in its own format | product behaviour (C1) |
| CI pipeline and image build | a way to list contamination with tools it already has, and images without session state | the teams' pipelines |
| Organization or cookbook author | to publish definitions once and see each repository take them as a reviewed copy | lmarqs, as the only author today |
| The tool's maintainer | a core that does not change each time a program does | lmarqs |

### Constraints

| ID | Constraint | Source (outside the organization) | What it excludes, and the clause |
| --- | --- | --- | --- |
| C1 | Each program reads its files at fixed paths. Claude Code 2.1.296: `.claude/settings.json` and `.claude/settings.local.json` at the project root. Codex 0.160.1: `.codex/config.toml`, `.codex/rules/`, `.codex/hooks.json`. ralph-tui 0.12.0: `<cwd>/.ralph-tui/config.toml`, with no flag to move it. bd 1.3.1: finds `.beads/` by walking up from the working directory, and `BEADS_DIR` moves it | ralph-tui and bd: the installed binaries, checked on 2026-10-09. Claude Code and Codex: their documentation, and the reference's working setup on the installed versions | moving a program's files out of the paths it reads. The tool can keep them out of commits and images, and point a program elsewhere only where the program offers a way |
| C2 | Git 2.43.0 ignore rules: a file under an excluded directory cannot be re-included; a repository's `.gitignore` takes precedence over `.git/info/exclude` and `core.excludesFile`; ignore rules do not apply to files already tracked; `git add -f` adds an ignored file anyway; `git add <ignored file>` exits 1 and says the path is ignored, while `git add -A` skips it without a word | `gitignore(5)`, and tests in a scratch repository on 2026-10-11 | relying on ignore rules to remove files already tracked; an allow inside a banned directory written as one line |
| C3 | Docker buildx 0.37.1 reads only the `.dockerignore` of the build context, never `.gitignore`. A pattern without a leading `/` still matches only at the context root, so `node_modules` excluded the root copy and copied `pkg/node_modules/x`. A negation re-includes a file under an excluded directory in one line. An allow-list (`*`, then `!package.json`, `!package-lock.json`, `!src/`) admitted only those paths and everything under `src/`. A secret passed with `--secret` and mounted with `RUN --mount=type=secret` was readable during that step (18 bytes) and absent from the output filesystem | tests in a scratch build context on 2026-10-11 (`-o type=local`) | using the same text for both ignore files; relying on `.gitignore` to keep anything out of an image |
| C4 | EditorConfig: editors search parent directories for `.editorconfig` until they find one with `root = true` | the EditorConfig specification | none; without `root = true`, a file above the repository applies inside it |
| C5 | A commit pushed to a public repository is published, and clones, forks and caches keep it after deletion | the hosting service's behaviour; `lmarqs/the-cook` and `lmarqs/the-cookbook` are public | pushing a person's setup to a public remote before its public and private parts are separated |

### Prior decisions

| ID | Prior decision | When, and where recorded | Incumbent it implies | Cost to reverse |
| --- | --- | --- | --- | --- |
| P1 | Help, never control. The tool does its part and reports truthfully what it did. It refuses no caller and judges nobody, and a person or agent who ignores the settings they are given is acting on their own | 2026-10-11, #3; philosophy, ADR 0028 | a core without policing: no bypass refusal, tamper check, jails or untouchable floor | low: documents only |
| P2 | The tool becomes a thin layer that sets up a session for one way of working and leaves the repository clean. The development lifecycle moves out of the core into recipes | 2026-10-11, #3 | the three parts below, in place of the lifecycle core of ADR 0015 | low now: no code, no adopters |
| P3 | Three parts, designed and built in order: the repository's rules (kitchen), the person's own setup (kit), the session (cook) | 2026-10-11, #3 | this order of work | low |
| P4 | Documentation and architecture are finished before any implementation starts | 2026-10-11, #4 | this document, then the ADRs, then code | low |
| P5 | One executable written in Go, needing only the operating system at runtime | 2026-10-09; ADR 0003, ADR 0004 | a single binary | high once released |
| P6 | The tool does not depend on git | 2026-10-11 | git is reached like any other program, and whoever wants git's verdict runs git | low |
| P7 | Program knowledge is data. The binary holds one reader and writer per file format and knows no program | 2026-10-11, #3, #4 | facts about each program live in ingredient files | medium once ingredients are published |
| P8 | Ingredients are copied into the repository with their origin and sha256. Nothing is fetched or cached when rendering | 2026-10-11, #4 | a copy reviewed as a diff | low |
| P9 | Files the tool derives may be committed. They are derived from sources the repository declares, and the tool writes them | 2026-10-11; ADR 0006, ADR 0008 | committed renders that teammates and agents read without the tool | low |
| P10 | The kit and the cookbook are one thing: the cook's own repository, which others may copy from | 2026-10-11, #5 | one repository per cook | low |

Six choices about the kitchen were agreed in #4. They are choices of how to meet the requirements below, so they are
evaluated in the design and the trade-off analysis rather than recorded here:

1. a default ban on hidden top-level entries;
2. a marked section in `.gitignore`, with no checker of the tool's own;
3. ordered groups of rules, the last match winning;
4. an owner mark on groups;
5. no links between agent directories;
6. an init that asks nothing.

### Assumptions and open questions

**Blocking for the kit:**

| Question | Owner | Date | If one way | If the other |
| --- | --- | --- | --- | --- |
| Which parts of a kit do followers see, and how are the private parts backed up? (G5, C5) | lmarqs | before the kit's design | one repository, with private parts kept elsewhere | two repositories, or one private repository with a public export |

This does not block the kitchen. It blocks any push of a kit.

**Non-blocking:**

- Skills that two agents both read: a link, or a copy rendered into each agent's directory. Assumed: a rendered copy.
  The kitchen's design settles it.
- Whether the parts each ingredient says it writes are enough to remove the tool, so that no separate record of
  ownership is needed (ADR 0007). Assumed: enough. A removal test on a fixture closes it.
- Whether a committed wrapper that pins the tool's version stays (ADR 0017). Assumed: it leaves the core.
- How a session runs a program that keeps its state in its working directory with no way to move it, such as
  ralph-tui (C1). Assumed: in a workspace that the session removes. The cook's design settles it.
- Where personal state that must outlive a session lives when it belongs to one project, such as a tracker's
  database. Assumed: in the kit, with the program pointed there where it allows it (bd's `BEADS_DIR`). The kit's
  design settles it.
- Whether a recipe can bring its own kitchen rules. The cook's design settles it.

### Out of scope

- How a change moves through stages such as plan, verify and deliver. This is one way of working, carried by recipes,
  and outside the core (P2).
- Whether people and agents follow the settings they are given (P1).
- Trusting a cookbook beyond its copied content and hash, such as signatures.
- Repositories whose programs read settings from subdirectories as well as the root.
- How the tool compares with nearby projects (#2).

## Requirements

**What was reclassified.**

- **Prior decisions.** "Help, not control", "a thin layer", "three parts", "may not depend on git", "Go binary",
  "program knowledge is data", "derived files may be committed, and the tool writes them" and "documentation first"
  were decided by the maintainer. They are prior decisions (P1 to P10), not requirements.
- **Design choices.** "Ban `.vscode` and `.claude`, with config to allow", "generate `.gitignore` entries", "group
  rules like an ESLint ruleset", "no symlinks between agent directories", "readers and writers built into the binary"
  and "init asks nothing" are ways of meeting a requirement. They are evaluated in the design.
- **Constraints.** What each program reads, and how git and Docker match patterns, are constraints C1 to C5.
- **Cookbook content.** Three examples were requested:
  - `.dockerignore` and `.gitignore` entries derived from the tools in use, such as leaving `.mise/` tasks out of an
    image;
  - a layered Dockerfile that receives AWS credentials as a build secret;
  - CI workflows built from the tooling, such as mise and Node.

  Each is a recipe and its ingredients, which makes it a roadmap item for a cookbook, not a capability of the tool.
  What the tool must offer for them to exist are F14 to F16.

**What was challenged.**

- "Banning keeps traces out": a ban in `.gitignore` keeps nothing out of an image (C3). That adds F3.
- "The cook checks the kitchen in a pipeline": git already lists committed files the rules exclude. So F2 asks for
  git alone, and the tool adds no check of its own.
- "Leave the kitchen clean": F8 reads this as removing the session's ignored state too, not only keeping it out of
  commits. #3 counted ignored state left in a clone as contamination. Confirmed by the maintainer.
- "Generate them based on my setup": if a committed file were rendered from one person's kit, no teammate or pipeline
  could render it again, and the next render from another kit would rewrite it. That is contamination by generator.
  So F16 requires the recipe to be copied into the repository first. A kit is where a recipe can come from, never
  where a committed file is rendered from.

**What was assumed.** The 1-second init target (N2) has no measurement behind it, and it is measured first.

### Functional

| ID | Goal | Requirement (the role, and what the system does for it) | Proof (the scenario, and how it is run) | Source |
| --- | --- | --- | --- | --- |
| F1 | G1, G3, G7 | A team states in the repository which tool files it shares. For anyone using git, with or without the tool, every other hidden top-level entry and every listed trace stays out of commits | Given a repository that shares `.claude/settings.json` and `.github/`, when a teammate without the tool creates `.claude/settings.local.json`, `.vscode/settings.json`, `.newtool/x` and `prd.json` and runs `git add -A`, then only the shared files are staged. Run as a fixture test | contamination table; C2 |
| F2 | G1 | Anyone, a pipeline included, lists the committed files the repository's rules exclude, using git alone | Given committed `.ralph-tui/config.toml` and `.beads/hooks/pre-commit`, both excluded, when `git ls-files -ci --exclude-standard` runs, then it lists both and no shared file. Run as a fixture test | contamination table; P6 |
| F3 | G1 | No personal state reaches an image built from a clone | Given rules that mark `.work/` and `.beads/` as personal, a Dockerfile with `COPY . .`, and both directories present, when the image is built, then neither is in the output. Run as a fixture build with `-o type=local` | ignored-state finding; C3 |
| F4 | G3 | Every rule the tool writes into an ignore file carries its reason, in the file the program reads | Given a group of rules with a reason, when rendered, then the reason is the comment above the group's lines in `.gitignore` | the comments in the three `.gitignore` files |
| F5 | G1, G3 | A team states its agent rules once. Each agent receives them in its own files and format, and every rule an agent cannot express is reported by name | Given a rule that allows `mise run test` and a raw Claude Code pattern, when rendered, then Claude Code's settings and Codex's rules both allow the command, and the report names the pattern as one Codex cannot express | the reference's renderer |
| F6 | G3, G7 | Files the tool derives are committed and read without the tool. Only the tool writes their derived parts, and a hand edit to a derived part shows as a difference at the next render | Given committed derived agent settings, when an agent starts in a clone without the tool, then it reads them; when a derived key is edited by hand, then the next render shows the difference from its sources | P9; the reference commits its renders |
| F7 | G7 | The team's own content stays as written. The tool changes only the marked sections and named keys it derives, and leaves every other byte | Given a `.claude/settings.json` with a team key `env` and a `.gitignore` with team lines, when rendered, then only the derived keys and the marked section change | the team config listed in Context |
| F8 | G2 | A cook sets up a session for one named way of working with one request. When the session ends, the clone holds the work as commits and none of the session's state | Given a repository and a cook whose kit holds a way of working named `ralph`, when they start a ralph session, then the loop runner and agents run with the settings it needs; when it ends, then `git status --ignored` shows none of the session's state and `git ls-files -ci --exclude-standard` lists nothing. Run as an end-to-end test with stand-in agents | the motivating scenario; #3, #6 |
| F9 | G4 | A cook sets up a new machine from their kit in one step: their ignore groups, their git hooks, the parts of their agent setup they chose, and their editor settings | Given an empty home directory and the kit's remote, when the cook restores, then git's global ignore file holds their groups, `core.hooksPath` points at their hooks, and the chosen `~/.claude` files are in place. Run in a clean container | the personal setup finding |
| F10 | G5 | A kit backup holds only what the cook chose. It never holds credentials or transcripts, and nothing the cook marked private reaches a public remote | Given a home with `~/.claude/.credentials.json`, `~/.claude/projects/` and `~/.claude/history.jsonl`, when the cook picks all of `~/.claude` and backs up, then none of the three is in the kit; given a part marked private and a public remote, when pushed, then the push carries none of it | C5; #5 |
| F11 | G8, G3 | A repository takes definitions from a cookbook, an organization's included, by copying them with their origin and sha256. Taking a newer version is copying again, reviewed as a diff. A person's own definitions never enter the repository | Given versions 1 and 2 of a cookbook's group, when version 1 is copied, then its origin and hash are recorded; when version 2 is copied, then only the changed lines differ | the three levels; P8 |
| F12 | G1, G3 | A team brings its existing `.gitignore` under the repository's rules without changing what git ignores | Given each of the three `.gitignore` files, when imported, then `git ls-files -oi --exclude-standard` and `git ls-files -ci --exclude-standard` give the same output before and after; when they differ, then nothing is written | the `.gitignore` comments; #7 |
| F13 | G7 | A team removes the tool in one reviewed change, and the repository keeps working | Given an adopted fixture, when the tool is removed, then none of its own files remain and the fixture's own CI commands pass without it | ADR 0010 |
| F14 | G8, G7 | A recipe the repository has taken writes parts of the team's own files, in any format the binary supports: a stage of a Dockerfile, a whole CI workflow, a section of an ignore file. The rest of each file stays as the team wrote it | Given a recipe that owns the `deps` stage of a Dockerfile and the workflow `.github/workflows/cook-ci.yml`, when rendered, then only that stage and that file change, and a stage and a workflow the team wrote stay byte for byte | build and CI files written by hand |
| F15 | G5 | Rendered files name secrets but never hold their values | Given a recipe whose image build needs AWS credentials, when rendered, then the Dockerfile mounts a secret by id and the workflow passes it from the CI's secret store; when the image is built, then no rendered file and no file in the output holds the credential. Run as a fixture build with a fake credential | C3 |
| F16 | G1, G7 | Committed files are rendered only from sources in the repository, so every teammate and pipeline renders the same bytes from the same commit | Given two cooks with different kits, when both render the same commit, then every committed file is byte-identical | the axes; P9 |

### Non-functional

| ID | Goal | Requirement (metric, target, condition) | Derived from | Proof (measurement) | Source |
| --- | --- | --- | --- | --- | --- |
| N1 | G6 | Changes to the binary needed to support a new program: 0. Condition: the program's files use a format the binary reads and writes (JSON, TOML, YAML, ignore-file lines, line files with `#` comments and marked blocks such as a Dockerfile, Markdown with marked blocks, or a whole file the tool owns) | Today, the reference's renderer holds code for each of its 2 agents (measured). The maintainer decided program knowledge is data (P7) | a test adds an ingredient for a made-up program and renders it with an unchanged binary | P7 |
| N2 | G2 | Init asks 0 questions and finishes within 1 s of wall time on a repository of 7,456 tracked files (the reference's size), on a local disk | "Init is simple and quick" (maintainer, 2026-10-11). The 1 s target is assumed, and it is measured first | timed fixture run in CI | #4 |
| N3 | G7 | Programs the binary needs at runtime besides the operating system: 0, git included | ADR 0004 and P6 | init and render run in a container that holds only the binary | P5, P6 |
| N4 | G3 | Rendering is deterministic: identical sources give byte-identical files on Linux and macOS, and a second render changes nothing | F6's difference is meaningful only if a render reproduces its own output | CI renders on both systems and compares, then renders again and compares | F6 |
