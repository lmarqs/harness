# 0016. Existing permission changes receive explicit native-file review

## Objective

**Problem.** Existing agent settings can contain permission rules that a shared configuration recipe does not express.
Silently losing a deny can broaden access; silently losing an allow can interrupt work.

**Goal.** Developers can see which existing permission rules a requested change retains, replaces or deletes before
approving it, without converting their configuration through a separate adoption process.

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

1. Permission settings are native program configuration modeled by ingredients ([0009](0009-agent-support.md)).
   Requested changes use declared file, key or section targets and preserve unrelated content, including rules and
   settings the recipe does not address. Targeted edits preserve unrelated keys, entries, unknown fields, formatting,
   order and comments. Declaring an entire permission-list key or whole file makes replacement of that scope visible
   in the diff; it does not promise to retain every rule inside it.
2. Existing and edited settings receive the normal combined terminal diff and whole-batch approval. There is no
   mandatory import, equivalence gate, `--adopt` flag or alias. Cook does not silently drop rules to enforce a built-in
   safety floor, nor prescribe a universal cross-program rule grammar or precedence.
3. Personal settings are not implicitly imported into shared configuration. A recipe that explicitly copies choices
   into a declared destination file exposes that output in the same batch review. Source references may originate
   outside the destination; declared writes and deletions remain within it. This decision does not define an import
   API or personal-backup capability.
4. Discard evaluates the supplied recipe version and proposes deletion of its declared contributions, including
   edited current values. It does not restore adopted originals. Reverting a recorded operation instead restores its
   prior values against current content, preserving unrelated content, without an ownership or drift veto.

## Reasons

- **1.** The example contains native rules outside a common command subset. Preserving unrelated targets avoids
  incidental loss without requiring a neutral language to express every program feature. Broad target declarations
  still require the reviewer to assess every removed rule within that scope.
- **2.** The actual diff exposes additions, replacements and deletions for the requested operation. An equivalence
  gate would introduce a separate adoption contract and refuse deliberate permission changes. Native output review
  does not prove equivalent effective access across programs or across settings outside the batch.
- **3.** Personal settings carry choices a team may not share. Making the destination change explicit lets a reviewer
  decide whether to share them. Cook does not discover or publish those choices automatically.
- **4.** Deleting declared contributions and restoring a past operation answer different requests. Restoration needs
  the run's history; deletion needs only the supplied declarations. Neither depends on a persistent recipe identity.

## Rejected alternatives

### Import every existing rule and require equivalent permissions

At its best, adoption promises to preserve the effective rule set. It requires scope discovery, provenance and
cross-program equivalence machinery beyond the agreed operation contract, and blocks intentionally changed rules.

### Replace native permission keys from a common subset without review

At its best, a small common model makes shared choices easy to express. It silently loses native-specific rules and
hides the consequences of broad replacement. The actual native-file diff must expose that scope.

### Restore imported originals when discarding a recipe

At its best, stopping use of a recipe returns to an earlier setup. It conflates declaration-based deletion with
history-based reversal and requires stored adoption state that apply and discard do not need.

## Consequences

Costs:

- Reviewers must understand the native rules affected by a change; a diff is not an effective-permission proof.
- Whole-key or whole-file replacement can remove existing rules when approved. Targeted changes reduce that scope.
- A recipe that copies personal choices into shared files can publish them to teammates; review must consider the
  audience as well as the text.

Must be maintained:

- Preservation of unrelated native content and accurate diffs for permission changes.
- A clear distinction between deletion of declarations and reversal using run history.

## Confirmation

- Review the worked example with a targeted test-command change: unrelated domain and secret-file rules remain.
- Review a replacement of an entire permission list: every removed rule appears in the combined diff before approval.
- Review a key changed from `old` to `new`, then manually to `custom`: discard proposes deletion; reversing the original
  run proposes `custom` to `old`, preserving unrelated keys.
- Review both requests without adoption or drift gates. Only reversal requires the original run's history.
