# Installation

`harness` is a single static binary with no runtime dependencies: no interpreter, no package runtime. It is installed
once per machine and serves every repository on it. A repository can also pin a version for itself.

## When to use which install

| You want | Use |
| --- | --- |
| `harness` available in every repository on your machine | [Global install](#global-install) |
| CI, or every teammate, to run the exact same version in one repository | [Per-repository pin](#per-repository-pin-opt-in) on top of, or instead of, the global install |
| No mise on the machine | [Release download](#release-download) |

Supported platforms: linux and macOS, on amd64 and arm64. Windows is not supported.

## Global install

With [mise](https://mise.jdx.dev):

```sh
mise use -g github:lmarqs/harness@<tag>
harness version
```

`mise use -g` records the tool in your global mise configuration and puts `harness` on your PATH in every directory.
Replace `<tag>` with a release tag from the project's GitHub releases page.

## Release download

Download the archive for your operating system and architecture from the project's GitHub releases page, extract it,
and move the `harness` binary to a directory on your PATH:

```sh
tar -xzf harness_<version>_<os>_<arch>.tar.gz harness
install -m 0755 harness ~/.local/bin/harness
harness version
```

## Per-repository pin (opt-in)

A repository that uses mise can pin a version so every checkout of it, including CI, uses the same binary:

```sh
cd ~/src/acme-api
mise use github:lmarqs/harness@<tag>    # writes the pin to this repository's mise.toml
git add mise.toml
```

Inside that repository, mise resolves `harness` to the pinned version; elsewhere, the global install still applies.
The pin is ordinary mise configuration: `harness` does not read it, require it or write it.

Pinning trades convenience for reproducibility: every release needs a bump in each pinned repository. Without a pin,
teammates may run different versions; the `schema` field in the repository files keeps that safe (see
[Version skew](#version-skew)).

## Update notice

`harness doctor` and the `SessionStart` hook check the project's GitHub releases for a newer version:

- at most once per day; the result is cached under `~/.cache/harness/`;
- never blocking: offline, rate-limited or slow checks are skipped silently;
- informational: a newer release never changes an exit code.

When a newer version exists, you see one line naming the current and the newer version. An agent session sees it in
its session context.

`harness version --check-update` checks immediately, ignoring the daily cache.

Turn the check off for a shell, a CI job or a machine:

```sh
export HARNESS_NO_UPDATE_CHECK=1
```

A repository can also turn off the notice in agent sessions with `session_start.update_notice: false` in
[`.agents/hooks.yaml`](hooks.md).

To update a global install:

```sh
mise use -g github:lmarqs/harness@<new-tag>
```

## Version skew

Every repository file carries `schema: 1`. Rendered files carry the schema number, never the binary version, so
upgrading `harness` alone never makes `render --check` fail in CI.

| Your binary vs the repository | What happens |
| --- | --- |
| Same schema | Normal operation |
| Repository uses an older schema | `harness render` migrates the files and shows the diff before writing |
| Repository uses a newer schema | Every command that reads the files refuses with a message naming the schema and asking you to update `harness` (exit 3) |

## Without the binary

A clone of an adopted repository works on a machine without `harness`:

| Entry point | Without the binary |
| --- | --- |
| Agent hooks (implicit: the agent calls them) | print `harness: not installed, hook skipped` on stderr and exit 0 |
| `raw` hooks | keep running; they are rendered as their own commands |
| mise wrappers (explicit: a person or an agent calls them) | print that `harness` is not installed and exit non-zero, so no verification or task command claims work that did not happen |
| Lifecycle events | do not fire; nothing leaves the clone through `harness` |
| Rendered permissions and instructions | apply as committed |

## Uninstall

Remove `harness` from repositories first, if you want them to stop using it; see [remove](remove.md). A repository
that keeps its rendered files keeps working without the binary.

Then remove the binary:

```sh
mise unuse -g github:lmarqs/harness       # global mise install
mise unuse github:lmarqs/harness          # per-repository pin, run inside the repository
rm ~/.local/bin/harness                     # release download
```

Remove the per-user caches and configuration if you no longer need them:

```sh
rm -rf ~/.cache/harness ~/.config/harness
```

| Path | Holds |
| --- | --- |
| `~/.cache/harness/` | update-check result, fetched [presets](presets.md) |
| `~/.config/harness/fleet.yaml` | your default [fleet](fleet.md) file |

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `harness: command not found` after `mise use -g` | mise shims or activation are not on PATH in this shell | Activate mise in your shell profile (`mise activate`), or add mise's shims directory to PATH |
| Agent session shows `harness: not installed, hook skipped` | The binary is not on the PATH the agent sees | Install globally, or start the agent from a shell where `harness` resolves |
| A command refuses because the repository schema is newer than the binary supports | A teammate rendered with a newer release | Update your install |
| The update notice never appears | `HARNESS_NO_UPDATE_CHECK=1` is set, the machine is offline, or `update_notice: false` | Run `harness version --check-update` |
