package coursier
package test

import coursier.core.Resolver
import utest._
import scala.async.Async.{async, await}

import coursier.test.compatibility._

object ResolverTests extends TestSuite {

  implicit class ProjectOps(val p: Project) extends AnyVal {
    def kv: (Module, (Repository, Project)) = p.module -> (testRepository, p)
  }

  val projects = Seq(
    Project(Module("acme", "config", "1.3.0")),
  
    Project(Module("acme", "play", "2.4.0"), Seq(
      Dependency(Module("acme", "play-json", "2.4.0")))),
  
    Project(Module("acme", "play-json", "2.4.0")),
  
    Project(Module("acme", "play", "2.4.1"),
      Seq(
        Dependency(Module("acme", "play-json", "${playJsonVersion}")),
        Dependency(Module("${project.groupId}", "${configName}", "1.3.0"))),
      properties = Map(
        "playJsonVersion" -> "2.4.0",
        "configName" -> "config")),
  
    Project(Module("acme", "play-extra-no-config", "2.4.1"),
      Seq(
        Dependency(Module("acme", "play", "2.4.1"),
          exclusions = Set(("acme", "config"))))),

    Project(Module("acme", "play-extra-no-config-no", "2.4.1"),
      Seq(
        Dependency(Module("acme", "play", "2.4.1"),
          exclusions = Set(("*", "config"))))),

    Project(Module("hudsucker", "mail", "10.0"),
      Seq(
        Dependency(Module("${project.groupId}", "test-util", "${project.version}"),
          scope = Scope.Test))),

    Project(Module("hudsucker", "test-util", "10.0")),

    Project(Module("se.ikea", "parent", "18.0"),
      dependencyManagement = Seq(
        Dependency(Module("acme", "play", "2.4.0"),
          exclusions = Set(("acme", "play-json"))))),

    Project(Module("se.ikea", "billy", "18.0"),
      Seq(
        Dependency(Module("acme", "play", ""))
      ),
      parent = Some(Module("se.ikea", "parent", "18.0"))),

    Project(Module("org.gnome", "parent", "7.0"),
      Seq(
        Dependency(Module("org.gnu", "glib", "13.4")))),

    Project(Module("org.gnome", "panel-legacy", "7.0"),
      Seq(
        Dependency(Module("org.gnome", "desktop", "${project.version}"))),
      parent = Some(Module("org.gnome", "parent", "7.0"))),

    Project(Module("gov.nsa", "secure-pgp", "10.0"),
      Seq(
        Dependency(Module("gov.nsa", "crypto", "536.89")))),

    Project(Module("com.mailapp", "mail-client", "2.1"),
      Seq(
        Dependency(Module("gov.nsa", "secure-pgp", "10.0"),
          exclusions = Set(("*", "${crypto.name}")))),
      properties = Map("crypto.name" -> "crypto", "dummy" -> "2")),

    Project(Module("com.thoughtworks.paranamer", "paranamer-parent", "2.6"),
      Seq(
        Dependency(Module("junit", "junit", ""))),
      dependencyManagement = Seq(
        Dependency(Module("junit", "junit", "4.11"), scope = Scope.Test))),

    Project(Module("com.thoughtworks.paranamer", "paranamer", "2.6"),
      parent = Some(Module("com.thoughtworks.paranamer", "paranamer-parent", "2.6"))),

    Project(Module("com.github.dummy", "libb", "0.3.3"),
      profiles = Seq(
        Profile("default", activeByDefault = Some(true), dependencies = Seq(
          Dependency(Module("org.escalier", "librairie-standard", "2.11.6"))
        ))
      )),

    Project(Module("com.github.dummy", "libb", "0.4.2"),
      Seq(
        Dependency(Module("org.scalaverification", "scala-verification", "1.12.4"))
      ),
      profiles = Seq(
        Profile("default", activeByDefault = Some(true), dependencies = Seq(
          Dependency(Module("org.escalier", "librairie-standard", "2.11.6")),
          Dependency(Module("org.scalaverification", "scala-verification", "1.12.4"), scope = Scope.Test)
        ))
      )),

    Project(Module("com.github.dummy", "libb", "0.5.3"),
      properties = Map("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> None)), dependencies = Seq(
          Dependency(Module("org.escalier", "librairie-standard", "2.11.6"))
        ))
      )),

    Project(Module("com.github.dummy", "libb", "0.5.4"),
      properties = Map("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> Some("true"))), dependencies = Seq(
          Dependency(Module("org.escalier", "librairie-standard", "2.11.6"))
        ))
      )),

    Project(Module("com.github.dummy", "libb", "0.5.5"),
      properties = Map("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> Some("!false"))), dependencies = Seq(
          Dependency(Module("org.escalier", "librairie-standard", "2.11.6"))
        ))
      )),

    Project(Module("com.github.dummy", "libb-parent", "0.5.6"),
      properties = Map("special" -> "true")),

    Project(Module("com.github.dummy", "libb", "0.5.6"),
      parent = Some(Module("com.github.dummy", "libb-parent", "0.5.6")),
      properties = Map("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> Some("!false"))), dependencies = Seq(
          Dependency(Module("org.escalier", "librairie-standard", "2.11.6"))
        ))
      ))
  )

  val projectsMap = projects.map(p => p.module -> p).toMap
  val testRepository: Repository = new TestRepository(projectsMap)

  val repositories = Seq[Repository](
    testRepository
  )

  val tests = TestSuite {
    'empty{
      async{
        val res = await(resolve(
          Set.empty,
          fetchFrom(repositories)
        ).runF
        )

        assert(res == Resolution.empty)
      }
    }
    'notFound{
      async {
        val dep = Dependency(Module("acme", "playy", "2.4.0"))
        val res = await(resolve(
          Set(dep),
          fetchFrom(repositories)
        ).runF)

        val expected = Resolution(
          rootDependencies = Set(dep.withCompileScope),
          dependencies = Set(dep.withCompileScope),
          errors = Map(dep.module -> Seq("Not found"))
        )

        assert(res == expected)
      }
    }
    'single{
      async {
      val dep = Dependency(Module("acme", "config", "1.3.0"))
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories)
      ).runF)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope),
        projectsCache = Map(dep.module -> (testRepository, projectsMap(dep.module)))
      )

      assert(res == expected)
      }
    }
    'oneTransitiveDependency{
      async {
      val dep = Dependency(Module("acme", "play", "2.4.0"))
      val trDep = Dependency(Module("acme", "play-json", "2.4.0"))
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories)
      ).runF)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope, trDep.withCompileScope),
        projectsCache = Map(
          projectsMap(dep.module).kv,
          projectsMap(trDep.module).kv
        )
      )

      assert(res == expected)
      }
    }
    'twoTransitiveDependencyWithProps{
      async {
      val dep = Dependency(Module("acme", "play", "2.4.1"))
      val trDeps = Seq(
        Dependency(Module("acme", "play-json", "2.4.0")),
        Dependency(Module("acme", "config", "1.3.0"))
      )
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories)
      ).runF)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope),
        projectsCache = Map(
          projectsMap(dep.module).kv
        ) ++ trDeps.map(trDep => projectsMap(trDep.module).kv)
      )

      assert(res == expected)
      }
    }
    'exclude{
      async {
      val dep = Dependency(Module("acme", "play-extra-no-config", "2.4.1"))
      val trDeps = Seq(
        Dependency(Module("acme", "play", "2.4.1"),
          exclusions = Set(("acme", "config"))),
        Dependency(Module("acme", "play-json", "2.4.0"),
          exclusions = Set(("acme", "config")))
      )
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories)
      ).runF)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope),
        projectsCache = Map(
          projectsMap(dep.module).kv
        ) ++ trDeps.map(trDep => projectsMap(trDep.module).kv)
      )

      assert(res == expected)
      }
    }
    'excludeOrgWildcard{
      async {
      val dep = Dependency(Module("acme", "play-extra-no-config-no", "2.4.1"))
      val trDeps = Seq(
        Dependency(Module("acme", "play", "2.4.1"),
          exclusions = Set(("*", "config"))),
        Dependency(Module("acme", "play-json", "2.4.0"),
          exclusions = Set(("*", "config")))
      )
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories)
      ).runF)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope),
        projectsCache = Map(
          projectsMap(dep.module).kv
        ) ++ trDeps.map(trDep => projectsMap(trDep.module).kv)
      )

      assert(res == expected)
      }
    }
    'filter{
      async {
      val dep = Dependency(Module("hudsucker", "mail", "10.0"))
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories),
        filter = Some(_.scope == Scope.Compile)
      ).runF).copy(filter = None)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope),
        projectsCache = Map(
          projectsMap(dep.module).kv
        )
      )

      assert(res == expected)
      }
    }
    'parentDepMgmt{
      async {
      val dep = Dependency(Module("se.ikea", "billy", "18.0"))
      val trDeps = Seq(
        Dependency(Module("acme", "play", "2.4.0"),
          exclusions = Set(("acme", "play-json")))
      )
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories),
        filter = Some(_.scope == Scope.Compile)
      ).runF).copy(filter = None, projectsCache = Map.empty)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
      )

      assert(res == expected)
      }
    }
    'parentDependencies{
      async {
      val dep = Dependency(Module("org.gnome", "panel-legacy", "7.0"))
      val trDeps = Seq(
        Dependency(Module("org.gnu", "glib", "13.4")),
        Dependency(Module("org.gnome", "desktop", "7.0")))
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories),
        filter = Some(_.scope == Scope.Compile)
      ).runF).copy(filter = None, projectsCache = Map.empty, errors = Map.empty)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
      )

      assert(res == expected)
      }
    }
    'propertiesInExclusions{
      async {
      val dep = Dependency(Module("com.mailapp", "mail-client", "2.1"))
      val trDeps = Seq(
        Dependency(Module("gov.nsa", "secure-pgp", "10.0"), exclusions = Set(("*", "crypto"))))
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories),
        filter = Some(_.scope == Scope.Compile)
      ).runF).copy(filter = None, projectsCache = Map.empty, errors = Map.empty)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
      )

      assert(res == expected)
      }
    }
    'depMgmtInParentDeps{
      async {
      val dep = Dependency(Module("com.thoughtworks.paranamer", "paranamer", "2.6"))
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories),
        filter = Some(_.scope == Scope.Compile)
      ).runF).copy(filter = None, projectsCache = Map.empty, errors = Map.empty)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope)
      )

      assert(res == expected)
      }
    }
    'depsFromDefaultProfile{
      async {
      val dep = Dependency(Module("com.github.dummy", "libb", "0.3.3"))
      val trDeps = Seq(
        Dependency(Module("org.escalier", "librairie-standard", "2.11.6")))
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories),
        filter = Some(_.scope == Scope.Compile)
      ).runF).copy(filter = None, projectsCache = Map.empty, errors = Map.empty)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
      )

      assert(res == expected)
      }
    }
    'depsFromPropertyActivatedProfile{
      val f =
      for (version <- Seq("0.5.3", "0.5.4", "0.5.5", "0.5.6")) yield {
        async {
        val dep = Dependency(Module("com.github.dummy", "libb", version))
        val trDeps = Seq(
          Dependency(Module("org.escalier", "librairie-standard", "2.11.6")))
        val res = await(resolve(
          Set(dep),
          fetchFrom(repositories),
          filter = Some(_.scope == Scope.Compile)
        ).runF).copy(filter = None, projectsCache = Map.empty, errors = Map.empty)

        val expected = Resolution(
          rootDependencies = Set(dep.withCompileScope),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
      }

      scala.concurrent.Future.sequence(f)
    }
    'depsScopeOverrideFromProfile{
      async {
      // Like com.google.inject:guice:3.0 with org.sonatype.sisu.inject:cglib
      val dep = Dependency(Module("com.github.dummy", "libb", "0.4.2"))
      val trDeps = Seq(
        Dependency(Module("org.escalier", "librairie-standard", "2.11.6")))
      val res = await(resolve(
        Set(dep),
        fetchFrom(repositories),
        filter = Some(_.scope == Scope.Compile)
      ).runF).copy(filter = None, projectsCache = Map.empty, errors = Map.empty)

      val expected = Resolution(
        rootDependencies = Set(dep.withCompileScope),
        dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
      )

      assert(res == expected)
      }
    }

    'parts{
      'propertySubstitution{
        val res =
          Resolver.withProperties(
            Seq(Dependency(Module("a-company", "a-name", "${a.property}"))),
            Map("a.property" -> "a-version"))
        val expected = Seq(Dependency(Module("a-company", "a-name", "a-version")))

        assert(res == expected)
      }
    }
  }

}
