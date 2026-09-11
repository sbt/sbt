## initialProject setting

By default sbt selects the root project when a build is first loaded. In a
multi-project build whose root is an empty aggregator, that means every session
starts one `project`
command away from where the work is. `initialProject` names the project to select
instead.

### Usage

In `build.sbt`:

```scala
lazy val app = (project in file("app"))

ThisBuild / initialProject := Some(app)
```

The value is a `ProjectReference`, so `LocalProject("app")` is also accepted.

Two scopes are worth keeping apart:

- **Where you set it.** The value is read at the root build's root project, which
  delegates to `ThisBuild` and to `Global`, so all three spellings work — including a
  bare `initialProject := Some(app)` at the top of `build.sbt`. A value set on any
  other project is ignored. One set in a user global `.sbt` applies to every build
  you open.
- **What you may name.** The project must belong to the root build. A reference into
  another build unit warns and falls back to the root project.

This replaces the long-standing workaround:

```scala
Global / onLoad := (Global / onLoad).value.andThen(s => "project app" :: s)
```

which fires on *every* load and therefore re-selects the project after each
`reload`, discarding wherever the user had navigated.

### Details

- Read only when the build is loaded with no existing session — that is, on startup
  and on `reboot`, but **not** on `reload`. `reload` preserves the project you
  navigated to, which is the behaviour the `onLoad` workaround got wrong.
- Because of that, editing `initialProject` takes effect on the next start or
  `reboot`, not on `reload`.
- A reference that names no project in the build logs a warning and falls back to
  the root project. It never fails the load.
- Defaults to `None`, in which case the root project is selected exactly as before.
- The selected project applies to non-interactive invocations too: with
  `ThisBuild / initialProject := Some(app)`, `sbt test` runs `app`'s tests rather
  than the root aggregate's, because commands run after the build is loaded.
