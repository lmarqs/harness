# Installation

An adopted repository carries its own entry point: [`harnessw`](wrapper.md), a committed script that downloads the
`harness` release the repository pins, verifies its checksum and runs it. Working in such a repository needs nothing
installed beyond a POSIX `sh`, `curl` or `wget`, and `sha256sum` or `shasum`, and the last two only for the first run
of a pinned release. `harness` itself is a single static binary with no runtime dependencies. A global install is
optional: it puts `harness` on your PATH and, inside a pinned repository, defers to the pinned release.

Examples on this page run commands as `./harnessw <command>`; with a global install, `harness <command>` is
equivalent.

## When to use which

| You want | Use |
| --- | --- |
| Work in a repository that already uses `harness` | [The wrapper](#the-wrapper-default): `./harnessw <command>`, nothing to install |
| Set up a repository that has no `harnessw` yet | [A `harness` binary, once](#scaffolding-a-new-repository) |
| `harness` on your PATH in every directory, or [`harness fleet`](fleet.md) across repositories | [Global install](#global-install-optional) |
| A machine without network access | [`HARNESS_BINARY` or a pre-filled cache](wrapper.md#air-gapped-machines) |

Supported platforms: linux and macOS, on amd64 and arm64. Windows is not supported.

## The wrapper (default)

```sh
cd ~/src/acme-api
./harnessw version
```

The first run downloads the release pinned in `.harness/wrapper.yaml` from the project's GitHub releases over HTTPS,
checks its sha256 against the pinned checksum, stores it in the per-user cache, `$HARNESS_HOME/versions/<version>/`
(by default `$XDG_CACHE_HOME/harness`, or `~/.cache/harness` when `XDG_CACHE_HOME` is unset), and runs it. Later
runs start from the cache and need no network. Every clone, teammate and CI job that goes through the wrapper
runs the same release.

| Needed | When |
| --- | --- |
| POSIX `sh` | always |
| `curl` or `wget` | the first run of a pinned release on a machine |
| `sha256sum` or `shasum` | the first run of a pinned release on a machine |
| HTTPS access to the release source | the first run of a pinned release on a machine |

Run `./harnessw version` once after cloning: agent hooks never download, so they skip with one line until the pinned
release is cached. An agent session on such a machine also gets one line in its session context, so the agent can
fetch it for you:

```text
harness v0.9.0 is not cached on this machine; run `./harnessw version` once to fetch it.
```

The [wrapper](wrapper.md) page covers the cache, `HARNESS_HOME`, the checksum and `HARNESS_BINARY`.

## Scaffolding a new repository

[`harness scaffold`](scaffold.md) writes `harnessw` and `.harness/wrapper.yaml`, pinned to the release that runs it,
with checksums it accepts only after verifying that release's signed checksum file ([release trust](#release-trust)).
So the first scaffold of a repository needs a `harness` binary from somewhere, once:

```sh
cd ~/src/acme-api
mise exec github:lmarqs/harness@<tag> -- harness scaffold    # one-off, nothing installed globally
harness scaffold                                            # with a global install
```

From then on the repository uses `./harnessw`. Replace `<tag>` with a release tag from the project's GitHub releases
page.

## Global install (optional)

A global install is a convenience: `harness` on your PATH everywhere, for scaffolding and for
[fleet](fleet.md) runs. It never changes what a pinned repository runs.

With [mise](https://mise.jdx.dev):

```sh
mise use -g github:lmarqs/harness@<tag>
harness version
```

`mise use -g` records the tool in your global mise configuration and puts `harness` on your PATH in every directory.
Keep it out of a repository's own `mise.toml`, `.mise.toml` or `.tool-versions`: a repository pins `harness` only in
`.harness/wrapper.yaml`, and `doctor` reports a second pin as a finding
([compatibility](compatibility.md#mise)).

Without mise, download the archive for your operating system and architecture from the project's GitHub releases
page, extract it, and move the `harness` binary to a directory on your PATH:

```sh
tar -xzf harness_<version>_<os>_<arch>.tar.gz harness
install -m 0755 harness ~/.local/bin/harness
harness version
```

### How a global install defers to the pin

Inside a repository whose `.harness/wrapper.yaml` pins another release, a global `harness` execs the pinned release
from the cache. When that release is not cached, it downloads it through the wrapper's verified path if you typed the
command, and never from a hook. Everywhere else it runs itself. See
[wrapper](wrapper.md#a-global-install-defers-to-the-pin).

## Upgrading

A repository moves to another release by changing its pin, in a commit:

```sh
./harnessw wrapper --latest          # or --version <tag>: rewrites .harness/wrapper.yaml and, when needed, harnessw
./harnessw render                    # the new release refreshes the harness skill and migrates older-schema files
git diff
git add -A && git commit -m "chore: upgrade harness"
```

`wrapper` writes nothing until the running release has verified the target release's signed checksum file with its
embedded release keys, downloaded the binary for this platform and matched it against that file; the new release then
writes `harnessw` and `wrapper.yaml`. A step that fails leaves both untouched.

Many repositories at once: `harness fleet wrapper --version <tag> --deliver`
([wrapper](wrapper.md#many-repositories)).

A global install updates with `mise use -g github:lmarqs/harness@<new-tag>`. That changes only what runs outside
pinned repositories.

## Release trust

Every release publishes a checksum file covering its platform binaries, and a signature over that file. Every `harness`
binary embeds the release keys it trusts. `scaffold` and `harness wrapper` write a pin only after the running binary has
verified the target release's checksum file against those keys; `harnessw`, a plain `sh` script, then checks each
download against the checksums committed in `wrapper.yaml`. `harness doctor`, when online, re-verifies the pinned
release's signed checksum file against `wrapper.yaml`.

Trust starts with the first binary you install, from the project's GitHub releases or a package manager. From then on,
every pin change is verified by a binary you already trust, and shows in review as a diff of `.harness/wrapper.yaml`
([wrapper](wrapper.md#security)).

## Update notice

`harness doctor` and the `SessionStart` hook check the project's GitHub releases for a release newer than the one the
repository pins:

- at most once per day; the result is cached under `$HARNESS_HOME`;
- never blocking: offline, rate-limited or slow checks are skipped silently;
- informational: a newer release never changes an exit code.

When a newer release exists, you see one line naming the pinned and the newer version, and the command that moves the
pin: `./harnessw wrapper --latest`. An agent session sees it in its session context.

`./harnessw version --check-update` checks immediately, ignoring the daily cache.

Turn the check off for a shell, a CI job or a machine:

```sh
export HARNESS_NO_UPDATE_CHECK=1
```

A repository can also turn off the notice in agent sessions with `session_start.update_notice: false` in
[`.harness/hooks.yaml`](hooks.md).

## Version skew

Within one repository there is no version skew: `.harness/wrapper.yaml` names one release, and both `harnessw` and a
global install run it. Every repository file carries `schema: 1`. Rendered files carry the schema number, never the
binary version.

| The running release vs the repository files | What happens |
| --- | --- |
| Same schema | Normal operation |
| The files use an older schema, after an upgrade | `./harnessw render` migrates them and shows the diff before writing |
| The files use a newer schema | Happens only when the pin is bypassed: `HARNESS_BINARY` names an older release, or a global binary too old to defer to the pin runs the command. Every command that reads the files refuses with a message naming the schema (exit 3) |

## Before the first download

A clone of an adopted repository works before its pinned release is cached on a machine:

| Entry point | Before the pinned release is cached |
| --- | --- |
| `./harnessw <command>` (explicit: a person or an agent calls it) | downloads, verifies and runs; without network, a downloader or a sha256 tool it exits 1 and runs nothing |
| Task shims (explicit) | the same: they exec `./harnessw <command>` |
| Agent hooks (implicit: the agent calls them) | print `harness: v0.9.0 not downloaded, hook skipped; run: ./harnessw version` on stderr and exit 0; the session-start hook also adds the one line above to the agent's session context |
| `raw` hooks | keep running; they are rendered as their own commands |
| Lifecycle events | do not fire: they fire from `harness` commands |
| Rendered permissions and instructions | apply as committed |

An explicit entry point never succeeds without running, so no verification or task command claims work that did not
happen.

## Uninstall

To take `harness` out of a repository, see [remove](remove.md): `./harnessw remove` undoes the adoption, and
`./harnessw remove --eject` stops managing the repository and keeps the result. Both delete `harnessw` and
`.harness/`.

Remove a global install:

```sh
mise unuse -g github:lmarqs/harness       # global mise install
rm ~/.local/bin/harness                     # release download
```

Remove the per-user caches and configuration if you no longer need them. The next `./harnessw` in a pinned
repository downloads its release again.

```sh
rm -rf "${HARNESS_HOME:-${XDG_CACHE_HOME:-$HOME/.cache}/harness}" ~/.config/harness
```

| Path | Holds |
| --- | --- |
| `$HARNESS_HOME` (default `$XDG_CACHE_HOME/harness`, else `~/.cache/harness`) | `versions/<version>/harness` for each downloaded release, the update-check result, fetched [presets](presets.md) |
| `~/.config/harness/fleet.yaml` | your default [fleet](fleet.md) file |

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `./harnessw: Permission denied` | The executable bit was lost | `chmod +x harnessw`, then commit the mode with `git add --chmod=+x harnessw` |
| The first run exits 1 naming `curl`, `wget`, `sha256sum` or `shasum` | No downloader or no checksum tool on PATH | Install one; see [wrapper](wrapper.md#troubleshooting) |
| `harness: checksum mismatch`, exit 1 | The download is not the pinned binary | Do not bypass it; see [wrapper](wrapper.md#troubleshooting) |
| `harness wrapper` or `scaffold` exits 1: the checksum file's signature does not verify | The release is not signed by a key the running binary trusts | Do not pin it; reinstall `harness` from the project's releases and report it ([safety](safety.md#the-pinned-binary)) |
| `harness version` prints another product's version | Another tool named `harness`, such as Harness.io's CLI, is earlier on PATH | Inside a repository, `./harnessw` is unaffected; for the global command, reorder PATH ([compatibility](compatibility.md#harnessio)) |
| Agent session shows `harness: v0.9.0 not downloaded, hook skipped; run: ./harnessw version` | Hooks never download, and the pinned release is not cached on this machine | Run `./harnessw version` once |
| `harness: command not found` after `mise use -g` | mise shims or activation are not on PATH in this shell | Activate mise in your shell profile (`mise activate`), or add mise's shims directory to PATH; inside an adopted repository, `./harnessw` needs neither |
| A command refuses because the repository schema is newer than the binary supports | The pin is bypassed: `HARNESS_BINARY` names an older release, or a global binary is too old to defer to the pin | Unset `HARNESS_BINARY`, or run `./harnessw <command>`; update the global install |
| The update notice never appears | `HARNESS_NO_UPDATE_CHECK=1` is set, the machine is offline, or `update_notice: false` | Run `./harnessw version --check-update` |
