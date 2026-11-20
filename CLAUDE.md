# HomeTube (SmartTube fork)

Branch `hometube` is a personal fork of yuliskov/SmartTube (remote `upstream`).
Upstream changes are merged in regularly, so keep HomeTube changes easy to merge:

- Put new code in separate files: `HomeTube…` classes (mostly `common/.../common/misc/`),
  strings in `res/values*/hometube.xml`. In upstream files add only a small call or hook.
- Gate behavior with `R.bool.hometube_rail_actions` so it only runs in the HomeTube flavor.
- Taking upstream: `git fetch upstream`, check with
  `git merge-tree --write-tree --name-only hometube upstream/master`, then
  `git merge --no-ff upstream/master` (no rebase) and `git submodule update --init --recursive`.
  Build, test on the emulator, then the TV.
- Shared code hooks to recheck after a SharedModules update: `HomeTubeLogger`
  (sets `Log.sLogger` by reflection) and `common/misc/MessageHelpers` (mirrors the shared one).
- Build: `./gradlew --offline :smarttubetv:assembleSthomeRelease`. Release: `scripts/hometube-release.sh`.
- Never commit the signing key (`~/MyProjects/smarttube-keys/`).
