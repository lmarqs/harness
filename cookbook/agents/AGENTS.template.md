> **This file is an unfilled template.** When the developer asks you to fill it in, replace each [placeholder] and
> empty table cell with what this repository shows, and run every command you write down. Text outside square
> brackets holds for any repository: keep it unless it is wrong here. Fill only what you can verify now, and delete
> the rows, sections and groups that do not apply. Keep the line under each heading, link to details instead of
> copying them, and delete this note when you are done. Until then, tell the developer that this file is unfilled
> before you start other work.

# [Project name]

[What this repository is and who uses it, in one paragraph.]

## Quick reference

The commands for daily work, each verified here, with the flags that matter.

| Need | Command |
| --- | --- |
| Run the application | |
| Build | |
| Test one file or path | |
| Lint or format one file | |
| Install dependencies | |
| Add a dependency | |
| Generate code | |
| Run a migration or a one-off admin task | |
| Done check: everything that must pass before a change is done, in order | |

## Boundaries

What an agent always does here, what it asks before doing, what it never does, and when it stops to ask.

**Always**

- A change is done only when the Done check passes.
- Read an area's Gotchas entry before changing it; add one when you get something wrong.
- Update this file in the same change that alters a command, path or rule it names.
- Keep this file near [N] words: a line stays while removing it would cause mistakes, a check that catches a rule
  replaces it, and other knowledge goes where Documentation says.
- [What else an agent does on every change here]

**Ask first**

- [What needs the developer's request, such as new dependencies, schema changes, pushes and writes outside this
  machine]

**Never**

- Commit secrets, credentials or tokens.
- Put personal data ([the fields that count as personal here]) in logs, fixtures, documents, issues or commits.
- Edit generated files ([paths]) by hand. Change their source and run the generator.

**Stop and ask** when a check keeps failing after you fixed what it reported, when a command is refused, or when the
request leaves a decision open.

Enforced by [the file that makes agent tools refuse these], where one exists. A refusal from it is the rule working,
not a failure to retry.

## Understand

### Stack

The main languages, frameworks and services, each with its major version and the file that pins it.

| Part | Technology and major version | Pinned in |
| --- | --- | --- |
| Language | | |
| Framework | | |
| Data store | | |
| Build and task tools | | |
| Test tools | | |

### Architecture

What the code cannot show: where new code goes, related repositories, and where the design is written down.

| New code | Goes in |
| --- | --- |
| [A module, a test, a migration, a script] | |

Other repositories: [each one and how this repository depends on it, or is deployed by it].

Full document: [path or link].

### Configuration

Where settings come from, which source wins, and where a new setting goes.

| Source | Where | Holds |
| --- | --- | --- |
| Committed defaults | | |
| Local overrides | | |
| Environment variables | | |
| Secret store | | Names only, never values |

[Which source wins when two set the same value.] A new setting goes in [file].

## Develop

### Environment

How a fresh checkout becomes runnable, where it differs from production, and how to take it apart again.

| Step | Command |
| --- | --- |
| Set up: log in to what the other steps need | |
| Start: run the local services | |
| Stop: halt the local services, keeping their data | |
| Tear down: remove the local services and their data | |
| Clean up: delete generated files and installed dependencies | |

Differs from production: [emulated services, seeded data, missing integrations].

### Worktrees

Where extra checkouts go, how one runs beside the main one, and how one is removed leaving nothing behind.

| Need | Command or place |
| --- | --- |
| Where worktrees are created | |
| Create one ready to run, with its own ports, container and database names | |
| Remove one, with its local services, their data and its generated files | |

### Verification

While editing, run the Quick reference test and lint commands on what changed. The Done check takes [time] and needs
[local services, or none].

### Tests

How a test is written here, each rule with a file that follows it.

| Need | Rule |
| --- | --- |
| Where a test goes | |
| Naming of files and cases | |
| Fixtures and test data | |
| Which kind (unit, integration, end-to-end) a change needs | |

### Conventions

Rules the checks do not catch: code that breaks them fails review. Each rule added here points to a file that
follows it or shows a short good and bad pair.

- Code explains itself through names and structure; where it needs a comment to be understood, rename or extract.
- A comment records only a reason the code cannot show, such as a workaround or an outside constraint. API doc
  comments, license headers and tool directives follow the language's standard.
- Names and comments describe the code as it is, never the change that made it ("new", "now uses X"). The change
  goes in the commit message.
- Dead code is deleted, not commented out.
- New code matches the code around it in naming, error handling and idiom.
- [Rule], as in [path to a file that follows it].

## Ship

### Workflow

How a change moves from a branch to the main branch, and who may do which step.

| Step | Rule |
| --- | --- |
| Tracker, and how a commit references it | |
| Branch | |
| Commit message | |
| Pull request | |
| CI | |
| Merge | |

### Deploy and monitor

What puts a change in production, how to tell it worked, and where the running system reports. Write "none" where
there is none.

| Need | Command, trigger or place |
| --- | --- |
| Deploy | |
| Check a deploy, and see what production runs | |
| Roll back | |
| Logs | |
| Metrics and dashboards | |

Full procedure: [path or link].

## Gotchas

Behavior that surprises newcomers or fails silently, one entry per area.

- [What goes wrong] when [what you do]: [what to do instead].

## Documentation

### Where a document goes

Where each kind of written knowledge belongs.

| Kind | Where it goes |
| --- | --- |
| A decision that binds code not yet written | |
| A business rule the code cannot show | |
| A domain term | |
| A runbook | |
| A procedure an agent repeats | |
| Setup and commands | |
| Rules for one package or directory | |
| A trap that costs time and reports nothing | Gotchas, in this file |
| Why a change was made | The commit message |
| What the code already says | Nowhere |

### Deep dive (read on demand)

Documents and skills for specific topics. Read one when the task touches its topic, not before.

- [Topic]: [document or skill].
