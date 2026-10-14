# Task-tracker configuration

## Objective

**Problem.** Teams use different task trackers, or none, and need their setup to remain useful independently of Cook.

**Goal.** Share native tracker configuration and guidance without making Cook the tracker or the controller of work
on its tasks.

## Native configuration

Cookbooks can supply ingredients that model a tracker's native files and configuration, and recipes can declare the
requested file changes. Cook evaluates those declarations and presents their combined diff for whole-batch approval.
The tracker consumes its native configuration directly. See [Cook's architecture](../architecture.md) and
[apply](render.md).

Task selection, assignment, dependencies, completion, and synchronization belong to the tracker and the people or
programs using it. The engine does not bind workspaces to tasks, assign actors, fire tracker lifecycle events, or close
tasks after a verification receipt. Tracker commands and hosting-platform requests are not batch actions.

A tracker's database and state files remain that program's data. Applying configuration does not implicitly migrate,
clear, or synchronize them. Changes to any such files must be explicitly declared, stay within the invocation
directory (including paths resolved through symlinks), and appear in the reviewed diff. Declared configuration changes
preserve unrelated content according to their file, key, or marked-section scope.

Discard deletes the contributions declared by the supplied recipe version. It does not infer or remove a tracker's
other data. Reverting a configuration run restores that operation's recorded prior values from local history; it does
not reverse task activity performed later by the tracker. See [discard and revert](remove.md).

## Session boundary

Agent-session orchestration is future work tracked in
[issue #6](https://github.com/lmarqs/the-cook/issues/6). Task execution and session integration are outside the engine
responsibilities defined here; no tracker protocol, scheduling system, or recipe hook API is decided by this page.
