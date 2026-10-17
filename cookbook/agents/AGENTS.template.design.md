# Why the AGENTS.md template is shaped this way

`AGENTS.template.md` is seeded into a repository by the `instructions` recipe and read by every agent session there.
Each line costs every session, so the template holds what the evidence says agents use from such a file, and nothing
the repository already shows. The reasons below name their sources; the full list is at the end.

## What the evidence says

- Agents follow the instructions in a context file: a tool the file names is used far more often than one it does
  not. Instructions are the content that works ([1], §4.3).
- A repository overview does not help an agent reach the right files sooner, and LLM-written files mostly repeat the
  README. A context file should hold only what the README lacks ([1], §1, §4.3).
- A context file raises an agent's cost by about a fifth, because the agent runs the checks and tools it names, not
  because the file is long. Length shows no correlation with success or cost ([1], §4.2, appendix B).
- Real files have a median of 335 to 535 words and six or seven top-level sections, grow by small additions and are
  almost never pruned. Testing is their most common topic, present in three files out of four; security appears in
  one out of seven ([2], tables 3 and 5).
- Files that work are specific: exact commands with their flags, examples rather than descriptions, a stack with
  versions, and boundaries in three tiers, always, ask first and never. The constraint teams most often wish they had
  written is that the agent never deletes a failing test ([3]).
- A long file makes the agent ignore its rules. Each line stays only while removing it would cause mistakes; what the
  agent can read from the code, standard conventions and "write clean code" do not belong ([4]).
- The nearest `AGENTS.md` to the edited file wins, so rules for one package go in a file in that package; Codex stops
  reading the chain at 32 KiB ([5], [6]).

## What follows for the template

- **One command table.** Quick reference holds every daily command, including installing and adding a dependency and
  the Done check with how long it takes. No other section repeats a command.
- **Rules are written out; overviews are not.** Boundaries and Conventions state the rules that hold in any
  repository, such as never weakening a check to make it pass, one change per commit, comments only for what the code
  cannot show. Architecture keeps only what the code cannot show: where new code goes, related repositories and a
  link to the design document. There is no component table.
- **A budget and a pruning rule** sit in Boundaries, because real files only grow. The budget is set per repository
  from the filled file; the median real file and the 32 KiB cap are the reference points.
- **Tests is its own section**, covering where a test goes, naming, fixtures and which kind a change needs, because
  testing is the most common topic in real files and the one a command table cannot carry.
- **Agent behavior lives in Boundaries only.** Running the quick check, reporting what ran, reading and adding
  Gotchas, updating the file: each rule is stated once, under Always, not repeated where it applies.
- **Worktrees stay explicit**, with a row for removing one and what that removal cleans up, because removal is the
  step agents skip.
- **The template is filled only when asked.** The file is loaded every session, so an unconditional instruction
  would make the first agent rewrite it during an unrelated task. Placeholders are `[bracketed]`, because GitHub
  renders `<angle brackets>` as HTML tags and hides them. No sample values appear, because agents copy them into
  repositories where they are wrong.

Not established: whether a repository's filled template improves what agents produce there. That needs the setup in
[1], a fixed task set across several agents and models, with and without the file. The `instructions` recipe's yield
asks for the in-scope check instead: a fresh session sets up, makes a small change and passes the Done check from
`AGENTS.md` alone.

## Sources

1. Gloaguen et al., *Evaluating AGENTS.md: Are Repository-Level Context Files Helpful for Coding Agents?*
   (2026), <https://arxiv.org/abs/2602.11988>. Controlled study on SWE-bench and CTXbench.
2. *Agent READMEs: An Empirical Study of Context Files for Agentic Coding* (2025),
   <https://arxiv.org/abs/2511.12884>. 2,303 context files from 1,925 repositories.
3. GitHub, *How to write a great agents.md: lessons from over 2,500 repositories*,
   <https://github.blog/ai-and-ml/github-copilot/how-to-write-a-great-agents-md-lessons-from-over-2500-repositories/>.
4. Anthropic, *Claude Code best practices*, section "Write an effective CLAUDE.md",
   <https://code.claude.com/docs/en/best-practices>.
5. OpenAI, *Custom instructions with AGENTS.md*, <https://learn.chatgpt.com/docs/agent-configuration/agents-md>.
6. agents.md, <https://agents.md/>.
