# 0016. Adoption is lossless: the permission source expresses every native rule, and adoption proves equivalence before writing

- Status: Accepted
- Date: 2026-10-09

## Context

- Repositories that adopt the tool already have native permission rules. Of 51 surveyed repositories, 8 have a
  project settings file for one agent, and 7 of those hold hand-written permissions (4 to 102 rules)
  ([0008](0008-merge-rendered-native-configuration.md)).
- Native rule languages cover more than shell commands. One supported agent's rules name tools with specifiers:
  web domains, file paths, and tools served by external tool servers, with globs. The other's rule files match the
  leading words of a shell command and decide allow, prompt or forbid.
- Taking ownership of a native permission key replaces its content with the rendered policy
  ([0008](0008-merge-rendered-native-configuration.md)). A rule the source cannot express is lost at that moment.
  A lost deny widens what agents may do; a lost allow stalls unattended runs on prompts.
- Permissions also live in personal scopes: a per-clone override file and per-user settings. They are not shared.
  Committing them publishes one person's choices to the team.
- Some native settings are not permission rules: default permission modes, extra readable directories, sandbox and
  approval settings, tool-server definitions, and settings an administrator manages.
- An imported rule can contradict a built-in invariant ([0011](0011-built-in-safety-invariants.md)), for example by
  allowing a bypass flag or a force push to the default branch.

## Decision

- The repository's permission source can express every native rule of every supported agent. An entry is one of:
  - a shell-command prefix, rendered for each agent in that agent's own syntax;
  - a native rule string, rendered verbatim for the agent whose syntax it is. For another agent it is converted
    only when it is a pure shell-command prefix; otherwise it takes effect for its own agent alone, and the policy
    summary the tool renders lists it as single-agent.
- Precedence is the same in every agent: deny, then ask, then allow.
- Adoption imports existing permissions and hooks from the scopes the person chooses. The project scope is the
  default; personal and user scopes are imported only on request, and every entry from them is listed for review
  because it becomes committed and shared. Source files are read and never written.
- Every imported entry carries its provenance. Exact duplicates merge. A pattern present under both allow and deny
  resolves to deny and is listed as unresolved ([0002](0002-deterministic-work-in-the-binary.md)).
- Invariants override imported rules. An imported entry that would weaken a built-in invariant is dropped and
  reported.
- Before writing anything, adoption renders the imported sources and compares the effective rule set of each agent
  with the set in effect before adoption. Every project-scope rule must still be in effect; otherwise adoption
  writes nothing and names the rule. A preview shows the generated sources and the equivalence report without
  writing.
- Settings that are not permission rules are reported and left where they are.

## Alternatives considered

### Import only what maps to the agent-neutral subset

The source holds only shell-command prefixes that every agent can express. At its best, the source stays small and
every entry means the same thing in every agent ([0009](0009-agent-neutral-sources-and-adapters.md)). Not chosen:
rules outside the subset are lost when the tool takes ownership of the native key, so a repository has to choose
between adopting and keeping its rules.

### Keep native files hand-owned beside rendered ones

The tool renders its rules and leaves people's native rules where they are. At its best, nothing is translated and
nothing is lost. Not chosen: one agent's project settings hold one permission list per kind with no include
mechanism ([0008](0008-merge-rendered-native-configuration.md)), so two writers share one key; rules live in two
places; the drift check cannot cover the hand-owned half; and reviewing the policy means reading several files.

## Consequences

Positive:

- Adoption never changes which rules are in effect, except where an invariant requires it.
- The permission source becomes the one place where the repository's permissions are reviewed, with the origin of
  each imported entry visible.

Negative:

- The source admits agent-specific entries, so one policy can grant different things to different agents; the
  rendered summary lists the entries that apply to one agent only.
- Importing a personal or user scope publishes one person's permissions to the team.
- Equivalence covers only the rules the binary can see. Personal files left unimported and settings an
  administrator manages still widen or narrow permissions outside the policy.
- Imported policies carry entries nobody chose for the team; a person prunes them after adoption.

Must be maintained:

- Each adapter's knowledge of its agent's full rule grammar, the per-agent equivalence comparison, and the
  provenance format ([0009](0009-agent-neutral-sources-and-adapters.md)).

## Confirmation

- Fixtures with shell rules, domain rules, path rules, tool-server rules and rule files with allow, prompt and
  forbid decisions adopt with the equivalence check passing, and the rendered native rules equal the originals.
- A fixture with a rule that cannot be preserved fails adoption, names the rule and writes nothing.
- A fixture that allows a bypass flag or a force push to the default branch has the entry dropped and reported.
- Personal and user source files are byte-identical after adoption.
