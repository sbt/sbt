val selected = ScopeFilter(
  inProjects(LocalProject("api"), LocalProject("engine")),
  inConfigurations(Compile)
)

lazy val lazySelected = ScopeFilter(
  inProjects(LocalProject("api"), LocalProject("engine")),
  inConfigurations(Compile)
)

@transient val fromVal = taskKey[Seq[File]]("Collect sources with a val filter")
@transient val fromLazyVal = taskKey[Seq[File]]("Collect sources with a lazy val filter")
@transient val fromInline = taskKey[Seq[File]]("Collect sources with an inline filter")
@transient val fromBlock = taskKey[Seq[File]]("Collect sources with an enclosing block filter")
@transient val fromHelper = taskKey[Seq[File]]("Collect sources with a helper parameter")
@transient val fromContainedBlock = taskKey[Seq[File]]("Collect sources with a contained filter")
@transient val fromDynamic = taskKey[Seq[File]]("Collect sources with a dynamic task filter")
@transient val fromPatternDynamic = taskKey[Seq[File]]("Collect sources with a pattern-bound dynamic filter")
@transient val sequentialResult = taskKey[Int]("Run a sequential task inside a dynamic task")
@transient val resultLocals = taskKey[Int]("Compute a task result using ordinary locals")
@transient val check = taskKey[Unit]("Check the selected sources and nested task results")

def collect(filter: ScopeFilter) = Def.task {
  sources.all(filter).value.flatten
}

lazy val api = project
lazy val engine = project

lazy val root = project.in(file(".")).settings(
  fromVal := sources.all(selected).value.flatten,
  fromLazyVal := sources.all(lazySelected).value.flatten,
  fromInline := sources.all(
    ScopeFilter(inProjects(api, engine), inConfigurations(Compile))
  ).value.flatten,
  {
    val filter = ScopeFilter(inProjects(api, engine), inConfigurations(Compile))
    fromBlock := sources.all(filter).value.flatten
  },
  fromHelper := collect(selected).value,
  fromContainedBlock := ({
    val filter = ScopeFilter(inProjects(api, engine), inConfigurations(Compile))
    sources.all(filter)
  }).value.flatten,
  fromDynamic := Def.taskDyn {
    val filter = ScopeFilter(inProjects(api, engine), inConfigurations(Compile))
    Def.task { sources.all(filter).value.flatten }
  }.value,
  fromPatternDynamic := Def.taskDyn {
    Option(ScopeFilter(inProjects(api, engine), inConfigurations(Compile))) match {
      case Some(filter) => Def.task { sources.all(filter).value.flatten }
      case None => Def.task { Seq.empty[File] }
    }
  }.value,
  sequentialResult := Def.taskDyn[Int] {
    Def.unit(baseDirectory.value)
    Def.sequential(Def.task(42))
  }.value,
  resultLocals := {
    val collected = fromVal.value
    val increment = 2
    collected.size + increment
  },
  check := {
    val expected = ((api / Compile / sources).value ++ (engine / Compile / sources).value).toSet
    val rootSources = (Compile / sources).value
    val actual = Seq(
      "val" -> fromVal.value,
      "lazy val" -> fromLazyVal.value,
      "inline" -> fromInline.value,
      "enclosing block" -> fromBlock.value,
      "helper parameter" -> fromHelper.value,
      "contained block" -> fromContainedBlock.value,
      "dynamic capture" -> fromDynamic.value,
      "dynamic pattern capture" -> fromPatternDynamic.value
    )
    assert(expected.map(_.getName) == Set("ApiSource.scala", "EngineSource.scala"))
    assert(rootSources.map(_.getName).toSet == Set("ExcludedSource.scala"))
    actual.foreach { case (form, collected) =>
      assert(collected.toSet == expected, s"$form: expected $expected, got ${collected.toSet}")
    }
    assert(sequentialResult.value == 42)
    assert(resultLocals.value == 4)
  }
)
