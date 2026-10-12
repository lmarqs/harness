# the-cook

<p align="center"><img src="docs/assets/the-cook.png" alt="the-cook: pixel-art portrait of a man in a black pork-pie hat lowering dark sunglasses" width="640"></p>

**The cook: cook the best product without any source of contamination.**

## Objective

**Problem.** Teams share repositories, and each person works with their own programs: coding agents, loop runners,
trackers and editors. Those programs leave their files in the repository. One person's setup gets committed, and every
teammate inherits it. A way of working learned in one repository reaches the others only by copying it by hand, and the
copies drift.

**Goal.** A way of working learned once reaches every repository through one reviewed change, and is withdrawn the same
way. Everyone keeps their own way of working, and when they are done only the work remains. People who do not use
the tool never notice it.

## What it is

- **The core** is one Go binary with three parts:
  - the **kitchen**: the team's rules for tool files, kept in the repository;
  - the **kit**: a person's own setup, kept in their own repository;
  - **cooking**: one session for one way of working, which leaves only the work behind.

  It writes files and reports what it did. It does not start programs or make commits, and it controls nobody.
- **The wrapper**, `./cook`, is committed in each repository. It pins one release with its checksums, so every machine
  and pipeline gives the same answer for the same commit, and a newcomer installs nothing.
- **Cookbooks** publish ways of working as groups of rules, ingredients and recipes. A repository or a kit copies what
  it uses, with its origin and sha256, in one reviewed change, and copying again is the update. Safety and compliance
  come from what a cookbook holds, and some recipes are safer than others. The tool helps put them in place; it is not
  a safety tool.

```sh
./cook kitchen apply        # write the kitchen's parts into the team's files
./cook kit apply            # write the kit's parts into the home directory
./cook recipe list          # the recipes this kitchen and kit offer
./cook grill                # run the recipe named grill as a session
```

For a cookbook to read or start from, see [lmarqs/the-cookbook](https://github.com/lmarqs/the-cookbook), the author's
own cookbook.

## Read next

| To learn | Read |
| --- | --- |
| Why the project exists, what it is trying to achieve, and the tenets behind every decision | [Philosophy](docs/philosophy.md) |
| The design: the parts, the commands, the requirements and the decisions behind them | [Architecture](docs/architecture.md) |

## Install

`the-cook` is in design: it has no release and no code. The design is in [the architecture](docs/architecture.md).
