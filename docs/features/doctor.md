# Configuration diagnostics

## Objective

When configuration does not work as expected, a contributor needs to distinguish a wrong file change from a program
that did not load it, an unavailable source, or missing recovery information. Useful diagnostics identify the path,
operation and cause without treating a repository as a lifecycle setup to be completed.

## Reviewing an operation

The [architecture](../architecture.md) defines Cook's file-operation engine and its result or failure record. It does
not define a `doctor` command, health-check catalog, JSON diagnostic schema or diagnostic exit-code contract.

Use the relevant evidence to investigate a problem:

| Question | Evidence and responsibility |
| --- | --- |
| Why is a different file changing? | The invocation directory is the destination; source recipe/template references resolve from the original recipe location. Review declared paths and symlink resolution against the destination boundary. |
| Why is a value replaced? | Recipes and operations compose left to right. Later declarations can replace earlier values; review the final combined diff against current content. |
| Why does the program ignore a setting or instruction? | Consult the program's native configuration rules and the ingredient's cookbook documentation, including supported settings and trust prerequisites. Writing configuration does not enforce its loading. |
| Why did a write fail? | Inspect the run's failure record and retained recovery patches. Cook rolls back changes already made by the batch; arbitrary recipe effects are outside that guarantee. |
| Why can a prior run no longer be reverted? | Revert requires that run's local history under the destination's `.cook/`. Losing history does not prevent new apply/discard operations. |

See [apply](render.md) for review and source resolution, [discard and revert](remove.md) for reversal semantics, and
[safety](safety.md) for the trusted-code boundary. The history captures evaluated and consumed local or downloaded
sources, their origins, arguments, execution order, operation and result or failure log. It is recovery information,
not an ownership database or proof that checks passed.

## Scope

Cook does not diagnose lifecycle stages, verification receipts, worker launches or jails, adoption to-dos, landed
branches or task delivery. Repository checks run through the team's programs; see [check configuration](verify.md).
Agent-session orchestration remains future work in
[issue #6](https://github.com/lmarqs/the-cook/issues/6); this page defines no session health service.

For commands available in the current implementation, use the [root README](../../README.md) and
[core implementation guide](../../core/README.md). The design requirements here do not establish that the target
CLI or recovery behaviour is already implemented.
