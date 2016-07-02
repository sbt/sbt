# Internals / architecture

## The model

Mainly in [Definitions.scala](https://github.com/alexarchambault/coursier/blob/master/core/shared/src/main/scala/coursier/core/Definitions.scala), [Resolution.scala](https://github.com/alexarchambault/coursier/blob/master/core/shared/src/main/scala/coursier/core/Resolution.scala), and [ResolutionProcess.scala](https://github.com/alexarchambault/coursier/blob/master/core/shared/src/main/scala/coursier/core/ResolutionProcess.scala).

### Module

[definition](https://github.com/alexarchambault/coursier/blob/462c16d6db98d35e180a25b0f87aa47083ad98aa/core/shared/src/main/scala/coursier/core/Definitions.scala#L12-L16)

Uniquely designates a... module. Typically, just an organisation (`org.scala-lang`) and a name (`scala-library`). At times, can also contains so called attributes: (unordered) key-value pairs. E.g. the SBT plugins usually have some, like `scalaVersion` with value `2.10` and `sbtVersion` with value `0.13`.

Two modules having different organisation / name / attributes are simply considered different.

During resolution, all dependencies with the same module have their versions reconciled if possible. If not possible, we have a version conflict (conflicting versions of a module are needed), which makes the resolution fail.

### Dependency

[definition](https://github.com/alexarchambault/coursier/blob/462c16d6db98d35e180a25b0f87aa47083ad98aa/core/shared/src/main/scala/coursier/core/Definitions.scala#L43-L54)

A dependency towards a given module, with a given version (can be a version interval too, like `[2.2,2.3)` - TODO add support for `last.revision`).

TODO Add a word about the various fields of `Dependency`

### Project

[definition](https://github.com/alexarchambault/coursier/blob/462c16d6db98d35e180a25b0f87aa47083ad98aa/core/shared/src/main/scala/coursier/core/Definitions.scala#L69-L96)

Metadata about a given version of a module.

Usually comes from a POM file (Maven) or an `ivy.xml` file (Ivy). In the former case, the infos in `Project` may originate from several POMs, if the main one has a parent POM, or some import dependencies. Raw `Project`s are obtained from the various individual POMs, and merged back into one (done during resolution by `Resolution` - TODO give more details).

TODO Describe the various fields of `Project`

### Resolution

[definition](https://github.com/alexarchambault/coursier/blob/462c16d6db98d35e180a25b0f87aa47083ad98aa/core/shared/src/main/scala/coursier/core/Resolution.scala#L477-L487)

State of the resolution.

At any given point during resolution, we have:
- ... (TODO describe the various fields)

Properties of Resolution: TODO describe the most important methods of `Resolution`

### ResolutionProcess

[definition](https://github.com/alexarchambault/coursier/blob/462c16d6db98d35e180a25b0f87aa47083ad98aa/core/shared/src/main/scala/coursier/core/ResolutionProcess.scala)

Proceeds with the resolution per se, starting from an initial state (a `Resolution` instance), until the final one.

Goes from one state to another by determining whether the current state:
- needs some extra metadata about some versions of some modules (some IO is needed, with a cache and/or repositories), or
- needs to take into account previously fed metadata (just calculations, no IO, giving the next state).

So each step is either IO or calculations. Several IO steps can occur in a row, if some parent POMs or dependency imports are needed to get the full picture about a given module. A calculation step is either the last step, or followed by some IO. So the steps are like: IO, IO, IO, calculations, IO, IO, calculations, IO, calculations -> done. The last step is necessarily some calculations (IO can't end it - the newly fetched metadata of an IO step needs to be taken into account by a calculation step).

TODO Describe ResolutionProcess a bit more...

### Artifacts

Once the resolution is done, we get the final `Resolution`. This `Resolution`can provide:
- final dependency list,
- artifact list,
- dependency graph,
- ...

TODO Describe all of these a bit more

## Cache / downloading

### Fetching metadata

What we need during resolution: fetching metadata, possibly several ones at once.

TODO Describe the function to supply to `ResolutionProcess` to fetch metadata

### Resolution result: artifact list

TODO How we can fetch artifacts per se...

### Cache structure on disk

TODO More about the cache (structure on disk, error tracking, TTL tracking, locks)
