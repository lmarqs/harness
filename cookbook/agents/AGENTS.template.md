> **This file is an unfilled template.** When the developer asks you to fill it in, replace each [placeholder] and
> empty table cell with what this repository shows, and run every command you write down. Text outside square
> brackets holds for any repository: keep it unless it is wrong here. Fill only what you can verify now, and delete
> the rows, sections and groups that do not apply. Keep the line under each heading, link to details instead of
> copying them, and delete this note when you are done. Until then, tell the developer that this file is unfilled
> before you start other work.

# [Project name]

[What this repository is and who uses it, in one paragraph.]

## Quick reference

The commands for daily work, each verified here. A change that alters one updates this file in the same pull request.

| Need | Command |
| --- | --- |
| Run the application | |
| Build | |
| Test one file or path | |
| Lint or format one file | |
| Generate code | |
| Run a migration or a one-off admin task | |

## Boundaries

What an agent always does here, what it asks before doing, what it never does, and when it stops to ask.

**Always**

- [What an agent does on every change]

**Ask first**

- [What needs the developer's request, such as new dependencies, schema changes, pushes and writes outside this
  machine]

**Never**

- Commit secrets, credentials or tokens.
- Put personal data ([the fields that count as personal here]) in logs, fixtures, documents, issues or commits.

**Stop and ask** when a check keeps failing after you fixed what it reported, when a command is refused, or when the
request leaves a decision open.

Enforced by [the file that makes the agent tools refuse what this section forbids]. A refusal from it is the rule
working, not a failure to retry.

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

The main components and how they depend on each other, where each one's code lives, and the other repositories this
one depends on or is deployed by.

| Component | What it does | Depends on | Code in |
| --- | --- | --- | --- |
| [Deployable unit, data store, queue or external service] | | | |

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
| Feature flags | | |

[Which source wins when two set the same value.] A new setting goes in [file].

## Develop

### Environment

How a fresh checkout becomes runnable, where it differs from production, and how to take it apart again.

| Step | Command | Deletes |
| --- | --- | --- |
| Set up: log in to what the other steps need | | |
| Set up: install tools and dependencies | | |
| Start: run the local services | | |
| Stop: halt the local services, keeping their data | | |
| Tear down: remove the local services and their data | | |
| Clean up: delete generated files and installed dependencies | | |

Differs from production: [emulated services, seeded data, missing integrations].

### Worktrees

Where extra checkouts go, how one gets ready to run, how its local services stay apart from other checkouts, and when
to remove it.

| Need | Command or place |
| --- | --- |
| Where worktrees go | |
| Create one ready to run | |
| Give it its own ports and project names | |
| Remove one | |

### Verification

What to run while editing and before calling a change done. A change is done only when the Done check passes.

| Check | Command | Needs local services |
| --- | --- | --- |
| Quick: while editing, scoped to the change | | |
| Done: everything that must pass, in order | | |

[Which changes need which tests, such as the changes that need end-to-end tests.]

### Conventions

Rules this code follows that the checks do not catch, each pointing to a real file that follows it or showing a
short good and bad pair. Code that breaks them passes the checks and fails review.

- [Rule], as in [path to a file that follows it].

## Ship

### Workflow

How a change moves from a branch to the main branch, and who may do which step.

| Step | Rule |
| --- | --- |
| Branch | |
| Commit message | |
| Pull request | |
| CI | |
| Merge | |

### Deploy

What puts a change in production and how to tell it worked. Write "none" where there is none.

| Step | Command or trigger |
| --- | --- |
| Deploy | |
| See what production runs | |
| Check a deploy | |
| Roll back | |

Full procedure: [path or link].

### Monitoring

Where the running system reports, how to read it, and the rules new logs and metrics follow.

| Need | Where |
| --- | --- |
| Logs | |
| Metrics | |
| Traces | |
| Dashboards | |
| Alerts | |
| Rules for new logs and metrics | |

Full procedure: [path or link].

## Gotchas

Behavior that surprises newcomers or fails silently. Read the matching entry before changing that area, and add one
when an agent gets something wrong.

- [What goes wrong] when [what you do]: [what to do instead].

## Documentation

### Where a document goes

Where each kind of written knowledge belongs. Durable knowledge learned during a task goes where this table says,
not into this file.

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
