# 0016. Existing permission changes receive explicit native-file review

## Objective

**Problem.** Existing agent settings can contain permission rules that a shared configuration recipe does not express.
Silently losing a deny can broaden access; silently losing an allow can interrupt work.

**Goal.** Developers can see which existing permission rules a requested change retains, replaces or deletes before
approving it, without first converting their configuration through a separate import step.

## Context

- Permission rules can describe commands, file paths, domains and program-specific tools. Different programs need not
  express these choices in the same language or resolve them with the same precedence.
- Worked example: `acme/api` has a native settings file with a test-command allow, a domain rule and a deny on reading
  a secret file. A recipe updating the test command need not address the domain or secret-file rules.
- Shared project settings and personal settings can have different audiences. Copying personal choices into a shared
  file changes who receives them, even if their text is preserved.
- The [architecture](../architecture.md#ordered-composition-and-declared-targets) defines declared changes against
  current content, with native-file preservation explained in [0008](0008-native-configuration.md).

## Decision

1. Permission settings are native program configuration modeled by [ingredients](../features/glossary.md)
   ([0009](0009-agent-support.md)). Requested changes use declared file, key or section targets and preserve unrelated
   content, including rules and settings the recipe does not address. Targeted edits preserve unrelated keys, entries,
   unknown fields, formatting, order and comments. Declaring an entire permission-list key or whole file makes
   replacement of that scope visible in the diff; it does not promise to retain every rule inside it.
2. Existing and edited settings receive the normal combined terminal diff and whole-batch approval. Cook requires no
   import of existing rules and no proof that new rules are equivalent before a change, and has no `--adopt` flag or
   alias. Cook does not silently drop rules to enforce a built-in minimum permission set, nor prescribe a universal
   cross-program rule grammar or precedence.
3. Personal settings are not implicitly imported into shared configuration. A recipe that explicitly copies choices into
   a declared destination file exposes that output in the same batch review. Recipe and template references come from
   inside the destination repository, from `https://` URLs, or by coordinates; declared writes and deletions remain
   within the destination ([0012](0012-write-boundary.md)).
4. The permission rules and keys a recipe writes are recorded as its own in the committed `cook.lock`
   ([0007](0007-ownership.md)). That ownership decides which rules the recipe's later versions update and remove; it
   never vetoes a change to an edited rule and never extends to rules the recipe did not write. Discarding the recipe
   removes the rules and keys it owns and preserves unrelated content; it restores no rule the recipe replaced.

## Reasons

- **1.** The example contains native rules outside a common command subset. Preserving unrelated targets avoids
  incidental loss without requiring a neutral language to express every program feature. Broad target declarations
  still require the reviewer to assess every removed rule within that scope.
- **2.** The actual diff exposes additions, replacements and deletions for the requested operation. Requiring proof of
  equivalence before writing would introduce a separate import contract and refuse deliberate permission changes. Native
  output review does not prove equivalent effective access across programs or across settings outside the batch.
- **3.** Personal settings carry choices a team may not share. Making the destination change explicit lets a reviewer
  decide whether to share them. Cook does not discover or publish those choices automatically.
- **4.** A recipe's later version can stop declaring a permission rule; without a record of what it wrote, that rule
  would linger with no recipe responsible for it. Recording only what the recipe wrote leaves the example's domain and
  secret-file rules outside its ownership, so no recipe version removes them. The trade is that a removed rule is
  visible only in the diff, so reviewers must read deletions.
  - Discard reads the same record, so stopping a recipe removes exactly its rules and leaves the example's domain and
    secret-file rules in place.

## Rejected alternatives

### Import every existing rule and require equivalent permissions

At its best, importing every rule promises to preserve the effective rule set. It requires scope discovery, provenance
and cross-program equivalence machinery beyond the agreed operation contract, and blocks intentionally changed rules.

### Replace native permission keys from a common subset without review

At its best, a small common model makes shared choices easy to express. It silently loses native-specific rules and
hides the consequences of broad replacement. The actual native-file diff must expose that scope.

### Record every existing rule as owned by the first recipe that touches the file

At its best, a recipe's later versions can then clean up every rule in the file. It loses because the recipe would
remove rules it never wrote, such as the example's secret-file deny, broadening access without anyone requesting it.

## Consequences

Costs:

- Reviewers must understand the native rules affected by a change; a diff is not an effective-permission proof.
- Whole-key or whole-file replacement can remove existing rules when approved. Targeted changes reduce that scope.
- A recipe that copies personal choices into shared files can publish them to teammates; review must consider the
  audience as well as the text.

Must be maintained:

- Preservation of unrelated native content and accurate diffs for permission changes.
- `cook.lock` ownership limited to the rules and keys a recipe wrote, and reverse patches that restore a batch's own
  values.

## Confirmation

- Review the worked example with a targeted test-command change: unrelated domain and secret-file rules remain.
- Review a replacement of an entire permission list: every removed rule appears in the combined diff before approval.
- Review a recipe whose new version stops declaring one rule: the diff proposes removing that rule and keeps the
  domain and secret-file rules.
- Review a discard of a recipe that owns one permission key in a file with other edits: the diff proposes removing
  that key and keeps the other edits. It requires no import of existing rules first.
