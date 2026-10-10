# Installation

## Objective

**Problem.** A developer who clones an adopted repository, a CI pipeline that builds it and a maintainer setting up a
new one all need the tool, at the release the repository expects, wherever they run. When each of them installs it by
hand, a newcomer must find and install the right release before anything works, machines running different releases
disagree about the same commit, and a download nobody checked runs with the person's credentials.

**Goal.** In an adopted repository, a newcomer's first command works with nothing installed beforehand beyond standard
shell tools, and every person, machine and pipeline runs the same verified release.

## Overview

An adopted repository carries its own entry point: [`cook`](wrapper.md), a committed script that downloads the
`the-cook` release the repository pins, verifies its checksum and runs it. Working in such a repository needs nothing
installed beyond a POSIX `sh`, `curl` or `wget`, and `sha256sum` or `shasum`, and the last two only for the first run of
a pinned release. `the-cook` itself is a single binary that needs only the operating system at runtime
([ADR 0004](../adrs/0004-runtime-dependencies.md)). Every program it starts is named by one of the repository's
[recipes](recipes.md), a declared command or an action, and is that repository's requirement: `git`, which the git
recipe runs and every clone already has, and the agents the profile declares. A global install is optional: it puts
`the-cook` on your PATH and, inside a pinned repository, defers to the pinned release.

Examples on this page run commands as `./cook <command>`; with a global install, `the-cook <command>` is
equivalent.

## When to use which

| You want | Use |
| --- | --- |
| Work in a repository that already uses `the-cook` | [The wrapper](#the-wrapper-default): `./cook <command>`, nothing to install |
| Set up a repository that has no `cook` yet | [A `the-cook` binary, once](#scaffolding-a-new-repository) |
| `the-cook` on your PATH in every directory, or [`the-cook fleet`](fleet.md) across repositories | [Global install](#global-install-optional) |
| A machine without network access | [`COOK_BINARY` or a pre-filled cache](wrapper.md#air-gapped-machines) |

Supported platforms: linux and macOS, on amd64 and arm64. Windows is not supported.

## The wrapper (default)

```sh
cd ~/src/acme-api
./cook version
```

The first run downloads the release pinned in `.cook/wrapper.yaml` over HTTPS from the release source the file names:
the project's GitHub releases by default, or a mirror ([release source](wrapper.md#release-source)). It checks the
release's sha256 against the pinned checksum, stores it in the per-user cache, `$COOK_HOME/versions/<version>/` (by
default `$XDG_CACHE_HOME/the-cook`, or `~/.cache/the-cook` when `XDG_CACHE_HOME` is unset), and runs it. Later runs
start from the cache and need no network. Every clone, teammate and CI job that goes through the wrapper runs the same
release.

| Needed | When |
| --- | --- |
| POSIX `sh` | always |
| `curl` or `wget` | the first run of a pinned release on a machine |
| `sha256sum` or `shasum` | the first run of a pinned release on a machine |
| HTTPS access to the release source | the first run of a pinned release on a machine |

The wrapper needs nothing else. The programs the repository's recipes name, such as `git` for the git recipe, are
needed when a command uses them, and `./cook doctor` reports each one that is missing.

Run `./cook version` once after cloning: agent hooks never download, so they skip with one line until the pinned
release is cached. An agent session on such a machine also gets one line in its session context, so the agent can
fetch it for you:

```text
the-cook v0.9.0 is not cached on this machine; run ./cook version once to fetch it.
```

The [wrapper](wrapper.md) page covers the cache, `COOK_HOME`, the checksum and `COOK_BINARY`.

## Scaffolding a new repository

[`the-cook scaffold`](scaffold.md) writes `cook` and `.cook/wrapper.yaml`, pinned to the release that runs it, with
checksums it accepts only after verifying that release's signed checksum file ([release trust](#release-trust)). Without
network access, `the-cook scaffold --offline` writes the pin [pending](wrapper.md#a-pending-pin), and you complete it
later with `the-cook wrapper --version <tag>`. So the first scaffold of a repository needs a `the-cook` binary from
somewhere, once. The first command below runs one through [mise](https://mise.jdx.dev), a tool-version manager, without
a global install; the second uses a [global install](#global-install-optional):

```sh
cd ~/src/acme-api
mise exec github:lmarqs/the-cook@<tag> -- the-cook scaffold    # one-off, nothing installed globally
the-cook scaffold                                            # with a global install
```

From then on the repository uses `./cook`. Replace `<tag>` with a release tag from the project's GitHub releases
page, such as `v0.9.0`.

## Global install (optional)

A global install puts `the-cook` on your PATH everywhere, for scaffolding and for [fleet](fleet.md) runs. It never
changes what a pinned repository runs.

With [mise](https://mise.jdx.dev):

```sh
mise use -g github:lmarqs/the-cook@<tag>
the-cook version
```

`mise use -g` records the tool in your global mise configuration and puts `the-cook` on your PATH in every directory.
Keep it out of a repository's own `mise.toml`, `.mise.toml` or `.tool-versions`: `.cook/wrapper.yaml` is the pin
`the-cook` runs, and a second pin drifts from it ([compatibility](compatibility.md#mise)).

Without mise, download the archive for your operating system and architecture from the project's GitHub releases
page, extract it, and move the `the-cook` binary to a directory on your PATH:

```sh
tar -xzf the-cook_<version>_<os>_<arch>.tar.gz the-cook
install -m 0755 the-cook ~/.local/bin/the-cook
the-cook version
```

### How a global install defers to the pin

Inside a repository whose `.cook/wrapper.yaml` pins another release, a global `the-cook` execs the pinned release
from the cache. When that release is not cached, it downloads it through the wrapper's verified path if you typed the
command, and never from a hook. Everywhere else it runs itself. See
[wrapper](wrapper.md#a-global-install-defers-to-the-pin).

## Upgrading

A repository moves to another release by changing its pin, in a commit:

```sh
./cook wrapper --version <tag>   # or --latest: pins the release and renders the repository with it
git diff
git add -A && git commit -m "chore: upgrade the-cook"
```

`wrapper` writes nothing until the running release has verified the target release's signed checksum file with its
embedded release keys, downloaded the binary for this platform and matched it against that file. The new release then
renders the repository: `cook`, `wrapper.yaml`, every file it renders differently, and a migration of the
repository files when it reads a newer `schema`. The release carries its builtin [recipes](recipes.md#builtin-recipes),
so their changed renders are part of that diff. A step that fails leaves everything untouched. The pin and its effect
are one diff ([upgrading](wrapper.md#upgrading)).

Many repositories at once: `the-cook fleet wrapper --version <tag> --deliver`
([wrapper](wrapper.md#many-repositories)).

A global install updates with `mise use -g github:lmarqs/the-cook@<new-tag>`. That changes only what runs outside
pinned repositories.

## Release trust

Every release publishes a checksum file covering its platform binaries, and a signature over that file. Every `the-cook`
binary embeds the release keys it trusts. `scaffold` and `the-cook wrapper` write a pin's checksums only after the
running binary has verified the target release's checksum file against those keys, and a
[pending pin](wrapper.md#a-pending-pin) from `scaffold --offline` admits no download until they do. `cook`, a plain `sh`
script, then checks each download against the checksums committed in `wrapper.yaml`. `the-cook doctor`, when online,
re-verifies the pinned release's signed checksum file against `wrapper.yaml`.

Trust starts with the first binary you install, from the project's releases or a package manager. From then on,
every pin change is verified by a binary you already trust, and shows in review as a diff of `.cook/wrapper.yaml`
([wrapper](wrapper.md#security)).

## Update notice

Explicit commands and `the-cook doctor` look for a release newer than the one the repository pins. The check fetches
the newest release's signed checksum file from a fixed path under the release source and verifies its signature
before naming the release; it calls no hosting platform's API ([release source](wrapper.md#release-source)). It is:

- at most once per day; the result is cached under `$COOK_HOME`;
- never blocking: an offline, unreachable or slow check is skipped silently;
- informational: a newer release never changes an exit code.

When a newer release exists, an explicit command prints one line on stderr, and `the-cook doctor` a note, naming the
pinned and the newer version and the exact command that moves the pin:

```text
the-cook v0.10.0 is available (this repository pins v0.9.0): run ./cook wrapper --version v0.10.0
```

Agent sessions never get the notice, and hooks never check: moving the pin is a supply-chain decision a person
reviews, not a step an agent takes on its own.

`./cook version --check-update` checks immediately, ignoring the daily cache.

Turn the check off for a shell, a CI job or a machine:

```sh
export COOK_NO_UPDATE_CHECK=1
```

## Version skew

Within one repository there is no version skew: `.cook/wrapper.yaml` names one release, and both `cook` and a
global install run it. Every repository file carries `schema: 1`. Rendered files carry the schema number, never the
binary version.

| The running release vs the repository files | What happens |
| --- | --- |
| Same schema | Normal operation |
| The files use an older schema | `./cook wrapper --version <tag>` migrates them as part of the upgrade, showing the diff before writing; `./cook render` does the same when a pinned release meets older files |
| The files use a newer schema | Happens only when the pin is bypassed: `COOK_BINARY` names an older release, or a global binary too old to defer to the pin runs the command. Every command that reads the files refuses with exit 3 and a message naming the file's schema, the newest schema the binary supports, and the release to run, the one `.cook/wrapper.yaml` pins, through `./cook` |

## Before the first download

A clone of an adopted repository works before its pinned release is cached on a machine:

| Entry point | Before the pinned release is cached |
| --- | --- |
| `./cook <command>` (explicit: a person or an agent calls it) | downloads, verifies and runs; without network it exits 1, and without a downloader or a sha256 tool, or under a [pending pin](wrapper.md#a-pending-pin), it exits 3; either way it runs nothing |
| Agent hooks (implicit: the agent calls them) | print `the-cook: v0.9.0 not downloaded, hook skipped; run: ./cook version` on stderr and exit 0; the session-start hook also adds the `the-cook v0.9.0 is not cached on this machine` line ([the wrapper](#the-wrapper-default)) to the agent's session context |
| `raw` hooks | keep running; they are rendered as their own commands |
| Lifecycle events | do not fire: they fire from `the-cook` commands |
| Rendered permissions and instructions | apply as committed |

An explicit entry point never succeeds without running, so no verification command claims work that did not happen.

A [fetched recipe](recipes.md#fetched-recipes) the profile declares is a second download, and only
`./cook recipe fetch` or `./cook recipe apply` makes it. Until it is cached on the machine, hooks and worker
attempts that need it exit 3 naming `./cook recipe fetch`; nothing fetches it implicitly.

## Uninstall

To take `the-cook` out of a repository, see [remove](remove.md): `./cook remove` undoes the adoption, and
`./cook remove --eject` stops managing the repository and keeps the result. Both delete `cook` and every file
`the-cook` recorded in `.cook/`; a file it did not record is listed and left, and `.cook/` goes once nothing else
is in it.

Remove a global install:

```sh
mise unuse -g github:lmarqs/the-cook       # global mise install
rm ~/.local/bin/the-cook                     # release download
```

Remove the per-user caches and configuration if you no longer need them. The next `./cook` in a pinned
repository downloads its release again.

```sh
rm -rf "${COOK_HOME:-${XDG_CACHE_HOME:-$HOME/.cache}/the-cook}" ~/.config/the-cook
```

| Path | Holds |
| --- | --- |
| `$COOK_HOME` (default `$XDG_CACHE_HOME/the-cook`, else `~/.cache/the-cook`) | `versions/<version>/the-cook` for each downloaded release, each fetched recipe under its hash, the update-check result, the signature-check result of `doctor` |
| `~/.config/the-cook/fleet.yaml` | your default [fleet](fleet.md) file |

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `./cook: Permission denied` | The executable bit was lost | `chmod +x cook`, then commit the mode with `git add --chmod=+x cook` |
| The first run exits 1 naming `curl`, `wget`, `sha256sum` or `shasum` | No downloader or no checksum tool on PATH | Install one; see [wrapper](wrapper.md#troubleshooting) |
| `the-cook: checksum mismatch`, exit 1 | The download is not the pinned binary | Do not bypass it; see [wrapper](wrapper.md#troubleshooting) |
| `the-cook wrapper` or `scaffold` exits 1: the checksum file's signature does not verify | The release is not signed by a key the running binary trusts | Do not pin it; reinstall `the-cook` from the project's releases and report it ([safety](safety.md#the-pinned-binary)) |
| Agent session shows `the-cook: v0.9.0 not downloaded, hook skipped; run: ./cook version` | Hooks never download, and the pinned release is not cached on this machine | Run `./cook version` once |
| `the-cook: command not found` after `mise use -g` | mise shims or activation are not on PATH in this shell | Activate mise in your shell profile (`mise activate`), or add mise's shims directory to PATH; inside an adopted repository, `./cook` needs neither |
| A command refuses because the repository schema is newer than the binary supports | The pin is bypassed: `COOK_BINARY` names an older release, or a global binary is too old to defer to the pin | Unset `COOK_BINARY`, or run `./cook <command>`; update the global install |
| The update notice never appears | `COOK_NO_UPDATE_CHECK=1` is set, the machine is offline, or the release source is unreachable; agent sessions never show it | Run `./cook version --check-update` |
