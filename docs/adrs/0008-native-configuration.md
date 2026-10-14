# 0008. Declared native-file targets preserve unrelated content

## Objective

**Problem.** Programs read configuration from files people and other tools also edit. Replacing a shared file to
change one setting can erase unrelated settings, and rewriting its formatting can hide the meaningful change.

**Goal.** A developer can review a native configuration change without losing unrelated settings or obscuring the
change with unrelated formatting edits.

## Context

- [Ingredients](../features/glossary.md) model programs' native files and configuration; cookbook repositories hold
  and ship ingredients and recipes separately from the engine
  ([architecture](../architecture.md#ingredients-recipes-and-cookbooks)).
- Native configuration can be structured content, shared text, or separate files in a directory. Where a program
  supports separate files or inclusion, a recipe can use that facility. A single shared file still needs targeted edits.
- Worked example: `acme/api` has a JSON settings file containing permission lists, a formatting hook and a database
  environment setting. Changing one declared permission key need not replace the hook or environment setting.
- Worked example: a teammate keeps a personal override file alongside shared settings. Copying its contents into
  shared configuration would publish that person's choices to the team.
- JSON has no comment syntax. TOML comments, key order and surrounding whitespace can carry information useful to
  people reviewing configuration, even when they do not change what the program reads.

## Decision

1. Recipes declare targets in native files: whole-file paths, structured key paths or marked text sections
   ([0007](0007-ownership.md)). Where a program supports separate files, a recipe can target one file without touching
   neighboring files.
2. Structured operations preserve unrelated keys and entries, including fields the model does not interpret. Their
   formatting, order, comments and surrounding text are preserved so the diff remains focused on declared changes.
   Whole-file declarations have whole-file scope and require review of that full diff.
3. Existing and manually edited values follow the ordered composition, approval and ownership rules of
   [0007](0007-ownership.md). In a native file, removing a key a recipe no longer declares removes its current value.
4. Personal override files are affected only when recipes explicitly declare them as targets. Their mere presence does
   not authorize writing them or importing their contents into shared configuration. Declared operations remain within
   the invocation directory, including symlink resolution ([0012](0012-write-boundary.md)).

Native settings can configure permissions, skills or a program's own hooks; the trust limits of declared operations
follow [0011](0011-safety-invariants.md).

## Reasons

- **1.** Native formats determine where configuration can live. A separate file can make a change easy to review, but
  cannot be a universal rule when a program consumes one shared file. Explicit targets work in both cases
  without requiring one exclusive file per program.
- **2.** In the shared-settings example, a change to permissions does not request a new hook or database setting.
  Preserving unrecognized fields avoids silently dropping configuration that the model does not understand.
  Preserving surrounding bytes keeps formatting noise from burying the meaningful diff, at the cost of careful
  format-specific editing rather than a serializer that rewrites the whole document.
- **3.** A developer needs to see the replacement of a manual value, rather than import it before any change is
  allowed. Actual diff review exposes that replacement and ordered composition makes overlap intentional. Removal
  follows the recipe's declarations, so the diff shows the current value, edited or not, that a removed key takes
  away.
- **4.** A personal choice is not automatically a team choice, as the override example shows. Requiring an explicit
  target makes that scope visible in review without permanently forbidding recipes from configuring local files.
  Destination containment limits declared writes; it is not a restriction on everything trusted recipe code can do.

## Rejected alternatives

### Replace every native file wholesale

At its best, whole-file serialization is simple and produces uniform formatting. It loses as a general strategy
because changing permissions could erase the example's hook and environment setting. Whole-file operations remain
available when the recipe actually declares that scope and the developer reviews it.

### Require a separate included configuration file everywhere

At its best, a separate file avoids editing shared keys. It loses as a universal rule because inclusion or directory
loading depends on the program's native format. Use it when supported, rather than inventing a Cook-specific format
that the program cannot read.

### Import existing settings before editing

At its best, importing settings into one source retains their effect in future generated output. It loses as a
mandatory step because it requires bringing settings into a second configuration representation for a request that
can be reviewed directly against current native content.

## Consequences

Costs:

- Native formats need editing support that preserves unrelated content. A full serializer round trip can change
  formatting or drop unknown fields and therefore needs particular care.
- A declared key can cover an entire list or object. Review must expose entries that replacing that target removes.
- People and Cook can change the same file; a later proposal uses its current content, and replacing a person's edit
  to an owned key appears in the diff rather than happening silently.

Must be maintained:

- Native-format knowledge in program models and editing behavior that preserves content outside declared targets.
- Diffs that reveal the actual scope of whole-file, key and section changes.

## Confirmation

- A permission-key update preserves the example's hook, environment setting, unknown fields and unrelated formatting.
- A targeted change preserves unrelated key order and TOML comments; a whole-file declaration shows its full diff.
- A separate-file target leaves neighboring configuration files intact.
- Personal overrides are not implicitly written or imported; an explicit in-destination target follows normal review.
- A manually edited key can be updated or deleted through batch review.
