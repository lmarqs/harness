# Task-tracker configuration

## Objective

**Problem.** Teams use different task trackers, or none, and need their setup to remain useful independently of Cook.

**Goal.** Share native tracker configuration and guidance, while the tracker and the people or programs using it
control work on its tasks.

## Native configuration

Cookbooks can supply ingredients that model a tracker's native files and configuration, and recipes can declare the
requested file changes. Cook evaluates those declarations and presents their combined diff for whole-batch approval.
The tracker consumes its native configuration directly. See [Cook's architecture](../architecture.md) and
[apply](render.md).

Task selection, assignment, dependencies, completion, and synchronization belong to the tracker and the people or
programs using it. Tracker commands and hosting-platform requests are not batch actions.

A tracker's database and state files remain that program's data. Applying configuration does not implicitly migrate,
clear, or synchronize them. Changes to any such files must be explicitly declared, stay within the invocation
directory (including paths resolved through symlinks), and appear in the reviewed diff. Declared configuration changes
preserve unrelated content according to their file, key, or marked-section scope.

Applying a new version of a recipe removes only the configuration its previous version owned and the new version no
longer declares; it leaves a tracker's other data in place. Reverting a configuration batch applies its recorded reverse
patches; it does not reverse task activity performed later by the tracker. See [revert](remove.md).

## Session boundary

Task execution and session integration are outside the engine responsibilities defined here.
