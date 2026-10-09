# 0016. Adoption keeps every native permission rule in effect

## Context

- Repositories that adopt the tool already have native permission rules. Of 51 surveyed repositories, 8 have a project
  settings file for one agent, and 7 of those hold hand-written permissions (4 to 102 rules).
- Native rule languages cover more than shell commands. Claude Code 2.1.x rules name tools with specifiers: web
  domains, file paths, and tools served by external tool servers, with globs. Codex CLI 0.16x rule files match the
  leading words of a shell command and decide allow, prompt or forbid.
- Taking ownership of a native permission key replaces its content with the rendered policy
  ([0008](0008-native-configuration.md)). A rule the source cannot express is lost at that moment. A lost deny widens
  what agents may do; a lost allow stalls unattended runs on prompts.
- Permissions also live in personal scopes: a per-clone override file and per-user settings. They are not shared.
  Committing them publishes one person's choices to the team.
- Some native settings are not permission rules: default permission modes, extra readable directories, sandbox and
  approval settings, tool-server definitions, and settings an administrator manages.
- An imported rule can contradict a built-in invariant ([0011](0011-safety-invariants.md)), for example by allowing a
  bypass flag or a force push to the default branch.
- The same rule can appear in several scopes, and a pattern can appear under both allow and deny.

## Decision

1. The repository's permission source can express every native rule of every supported agent. An entry is either a
   shell-command prefix, rendered for each agent in that agent's own syntax, or a native rule string, rendered verbatim
   for the agent whose syntax it is. A native rule string is converted for another agent only when it is a pure
   shell-command prefix; otherwise it takes effect for its own agent alone, and the policy summary the tool renders
   lists it as single-agent.
2. Precedence is the same in every agent: deny, then ask, then allow.
3. Adoption imports existing permissions and hooks from the scopes the person chooses. The project scope is the
   default; personal and user scopes are imported only on request, and every entry from them is listed for review
   because it becomes committed and shared. Source files are read and never written.
4. Every imported entry carries its provenance. Exact duplicates merge. A pattern present under both allow and deny
   resolves to deny and is listed as unresolved ([0002](0002-division-of-work.md)).
5. Invariants override imported rules. An imported entry that would weaken a built-in invariant is dropped and
   reported ([0011](0011-safety-invariants.md)).
6. Before writing anything, adoption renders the imported sources and compares the effective rule set of each agent
   with the set in effect before adoption. Every project-scope rule must still be in effect; otherwise adoption writes
   nothing and names the rule. A preview shows the generated sources and the equivalence report without writing.
7. Settings that are not permission rules are reported and left where they are.

## Reasons

- **1.** Ownership of a native permission key replaces its content ([0008](0008-native-configuration.md)), so any rule
  the source cannot hold is lost at the moment of adoption, and the survey shows hand-written rule sets of up to 102
  rules that include domains, paths and tool-server rules. A source that holds every native rule makes adoption safe for
  every repository instead of only those whose rules fit a common subset. The trade is that the source admits entries
  that apply to one agent only; the rendered summary makes those visible instead of hiding the difference.
- **2.** With different precedence per agent, the same policy would grant different things to different agents.
  Deny-first is the order in which a conflict resolves toward the narrower permission.
- **3.** The project scope is what the team already shares, so importing it changes nothing about who sees what.
  Personal and user scopes hold one person's choices; committing them is a decision that person must make, entry by
  entry. Reading without writing keeps the person's own files working exactly as before
  ([0012](0012-write-boundary.md)).
- **4.** Provenance shows a reviewer which entries came from where, so entries nobody chose for the team can be found
  and pruned. Duplicates across scopes carry no information. An allow and a deny on the same pattern is a conflict only
  a person can settle ([0002](0002-division-of-work.md)); resolving it to deny until then keeps the narrower effect.
- **5.** The invariants are the set that is never a legitimate choice ([0011](0011-safety-invariants.md)). Importing an
  entry that weakens one would carry the weakness into the source, where the invariants exist to prevent it.
- **6.** The equivalence check turns the promise of point 1 into a fact for each repository: either every project-scope
  rule is still in effect, or nothing is written and the failing rule is named. Running it before any write means a
  failed adoption leaves the repository untouched. The preview lets a person review the import before it happens.
- **7.** These settings are outside the keys the tool owns ([0008](0008-native-configuration.md)); moving or
  translating them would change behaviour the tool does not model.

## Rejected alternatives

### Import only what maps to the agent-neutral subset

The source holds only shell-command prefixes that every agent can express. At its best, the source stays small and
every entry means the same thing in every agent ([0009](0009-agent-support.md)). It loses because rules outside the
subset are lost when the tool takes ownership of the native key, so a repository has to choose between adopting and
keeping its rules.

### Native files hand-owned beside rendered ones

The tool renders its rules and leaves people's native rules where they are. At its best, nothing is translated and
nothing is lost. It loses because Claude Code's project settings hold one permission list per kind with no include
mechanism ([0008](0008-native-configuration.md)), so two writers share one key; rules live in two places; the drift
check cannot cover the hand-owned half; and reviewing the policy means reading several files.

## Consequences

Costs:

- One policy can grant different things to different agents; the rendered summary lists the entries that apply to one
  agent only.
- Importing a personal or user scope publishes one person's permissions to the team.
- Equivalence covers only the rules the binary can see. Personal files left unimported and settings an administrator
  manages still widen or narrow permissions outside the policy.
- Imported policies carry entries nobody chose for the team; a person prunes them after adoption.

Must be maintained:

- Each adapter's knowledge of its agent's full rule grammar, the per-agent equivalence comparison, and the provenance
  format ([0009](0009-agent-support.md)).

## Confirmation

- Fixtures with shell rules, domain rules, path rules, tool-server rules and rule files with allow, prompt and forbid
  decisions adopt with the equivalence check passing, and the rendered native rules equal the originals.
- A fixture with a rule that cannot be preserved fails adoption, names the rule and writes nothing.
- A fixture that allows a bypass flag or a force push to the default branch has the entry dropped and reported.
- A fixture with the same pattern under allow and deny renders it as deny and lists it as unresolved.
- Personal and user source files are byte-identical after adoption.
