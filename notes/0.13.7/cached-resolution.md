  [@eed3si9n]: https://github.com/eed3si9n
  [1631]: https://github.com/sbt/sbt/pull/1631

### cached resolution (minigraph caching)

sbt 0.13.7 adds a new update option called *cached resolution*, which replaces consolidated resolution:

    updateOptions := updateOptions.value.withCachedResolution(true)

Unlike consolidated resolution, which only consolidated subprojects with identical dependency graph, cached resolution create an artificial graph for each direct dependency (minigraph) for all subprojects, resolves them independently, saves them into json file, and stiches the minigraphs together.

Once the minigraphs are resolved and saved as files, dependency resolution turns into a matter of loading json file from the second run onwards, which should complete in a matter of seconds even for large projects. Also, because the files are saved under a global `~/.sbt/0.13/dependency` (or what's specified by `sbt.dependency.base` flag), the resolution result is shared across all builds.

Breaking graphs into minigraphs allows partial resolution results to be shared, which scales better for subprojects with similar but slightly different dependencies, and also for making small changes to the dependencies graph over time.

[#1631][1631] by [@eed3si9n][@eed3si9n].
