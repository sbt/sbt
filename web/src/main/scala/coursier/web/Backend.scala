package coursier.web

import coursier.{Dependency, Fetch, MavenRepository, Module, Platform, Repository, Resolution}
import coursier.maven.MavenSource
import coursier.util.{EitherT, Gather, Task}
import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.jquery.jQuery

import scala.scalajs.js
import scala.util.{Failure, Success}
import js.Dynamic.{global => g}

final class Backend($: BackendScope[_, State]) {

  def fetch(
    repositories: Seq[Repository],
    fetch: Fetch.Content[Task]
  ): Fetch.Metadata[Task] = {

    val fetch0: Fetch.Content[Task] = { a =>
      if (a.url.endsWith("/"))
        // don't fetch directory listings
        EitherT[Task, String, String](Task.point(Left("")))
      else
        fetch(a)
    }

    modVers => Gather[Task].gather(
      modVers.map { case (module, version) =>
        Fetch.find(repositories, module, version, fetch)
          .run
          .map((module, version) -> _)
      }
    )
  }


  def updateDepGraph(resolution: Resolution) = {
    println("Rendering canvas")

    val graph = js.Dynamic.newInstance(Dracula.Graph)()

    var nodes = Set.empty[String]
    def addNode(name: String) =
      if (!nodes(name)) {
        graph.addNode(name)
        nodes += name
      }

    def repr(dep: Dependency) =
      Seq(
        dep.module.organization,
        dep.module.name,
        dep.configuration
      ).mkString(":")

    for {
      (dep, parents) <- resolution
        .reverseDependencies
        .toList
      from = repr(dep)
      _ = addNode(from)
      parDep <- parents
      to = repr(parDep)
      _ = addNode(to)
    } {
      graph.addEdge(from, to)
    }

    val layouter = js.Dynamic.newInstance(Dracula.Layout.Spring)(graph)
    layouter.layout()

    val width = jQuery("#dependencies")
      .width()
    val height = jQuery("#dependencies")
      .height()
      .asInstanceOf[Int]
      .max(400)

    println(s"width: $width, height: $height")

    jQuery("#depgraphcanvas")
      .html("") // empty()

    val renderer = js.Dynamic.newInstance(Dracula.Renderer.Raphael)(
      "#depgraphcanvas", graph, width, height
    )
    renderer.draw()
    println("Rendered canvas")
  }

  def updateDepGraphBtn(resolution: Resolution)(e: raw.SyntheticEvent[_]) = CallbackTo[Unit] {
    updateDepGraph(resolution)
  }

  def updateTree(resolution: Resolution, target: String, reverse: Boolean) = {
    def depsOf(dep: Dependency) =
      resolution.projectCache
        .get(dep.moduleVersion)
        .toSeq
        .flatMap{case (_, proj) =>
          coursier.core.Resolution.finalDependencies(dep, proj)
            .filter(resolution.filter getOrElse coursier.core.Resolution.defaultFilter)
        }

    val minDependencies = resolution.minDependencies

    lazy val reverseDeps = {
      var m = Map.empty[Module, Seq[Dependency]]

      for {
        dep <- minDependencies
        trDep <- depsOf(dep)
      } {
        m += trDep.module -> (m.getOrElse(trDep.module, Nil) :+ dep)
      }

      m
    }

    def tree(dep: Dependency): js.Dictionary[js.Any] =
      js.Dictionary(Seq(
        "text" -> (s"${dep.module}": js.Any)
      ) ++ {
        val deps = if (reverse) reverseDeps.getOrElse(dep.module, Nil) else depsOf(dep)
        if (deps.isEmpty) Seq()
        else Seq("nodes" -> js.Array(deps.map(tree): _*))
      }: _*)

    println(
      minDependencies
        .toList
        .map(tree)
        .map(js.JSON.stringify(_))
    )
    jQuery(target).asInstanceOf[js.Dynamic]
      .treeview(js.Dictionary("data" -> js.Array(minDependencies.toList.map(tree): _*)))
  }

  def resolve(action: => Unit = ()): CallbackTo[Unit] = {

    g.$("#resLogTab a:last").tab("show")
    $.modState(_.copy(resolving = true, log = Nil)).runNow()

    val logger: Platform.Logger = new Platform.Logger {
      def fetched(url: String) = {
        println(s"<- $url")
        $.modState(s => s.copy(log = s"<- $url" +: s.log)).runNow()
      }
      def fetching(url: String) = {
        println(s"-> $url")
        $.modState(s => s.copy(log = s"-> $url" +: s.log)).runNow()
      }
      def other(url: String, msg: String) = {
        println(s"$url: $msg")
        $.modState(s => s.copy(log = s"$url: $msg" +: s.log)).runNow()
      }
    }

    $.state.map { s =>

      def task = {
        val res = coursier.Resolution(
          s.modules.toSet,
          filter = Some(dep =>
            s.options.followOptional || !dep.optional
          )
        )

        res
          .process
          .run(fetch(s.repositories.map { case (_, repo) => repo }, Platform.artifactWithLogger(logger)), 100)
      }

      implicit val ec = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

      task.map { res: Resolution =>
        $.modState { s =>
          updateDepGraph(res)
          updateTree(res, "#deptree", reverse = s.reverseTree)

          s.copy(
            resolutionOpt = Some(res),
            resolving = false
          )
        }.runNow()

        g.$("#resResTab a:last")
          .tab("show")
      }.future()(ec).onComplete {
        case Success(_) =>
        case Failure(t) =>
          println(s"Caught exception: $t")
      }

      ()
    }
  }
  def handleResolve(e: raw.SyntheticEvent[_]) = {

    val c = CallbackTo[Unit] {
      println(s"Resolving")
      e.preventDefault()
      jQuery("#results").css("display", "block")
    }

    c.flatMap { _ =>
      resolve()
    }
  }

  def clearLog(e: raw.SyntheticEvent[_]) = {
    $.modState(_.copy(log = Nil))
  }

  def toggleReverseTree(e: raw.SyntheticEvent[_]) =
    $.modState { s =>
      for (res <- s.resolutionOpt)
        updateTree(res, "#deptree", reverse = !s.reverseTree)
      s.copy(reverseTree = !s.reverseTree)
    }

  def editModule(idx: Int)(e: raw.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState(_.copy(editModuleIdx = idx))
  }

  def removeModule(idx: Int)(e: raw.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState(s =>
      s.copy(
        modules = s.modules
          .zipWithIndex
          .filter(_._2 != idx)
          .map(_._1)
      )
    )
  }

  def updateModule(moduleIdx: Int, update: (Dependency, String) => Dependency)(e: raw.SyntheticEvent[dom.raw.HTMLInputElement]) =
    if (moduleIdx >= 0) {
      e.persist()
      $.modState { state =>
        val dep = state.modules(moduleIdx)
        state.copy(
          modules = state.modules
            .updated(moduleIdx, update(dep, e.target.value))
        )
      }
    } else
      CallbackTo.pure(())

  def addModule(e: raw.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState { state =>
      val modules = state.modules :+ Dependency(Module("", ""), "")
      println(s"Modules:\n${modules.mkString("\n")}")
      state.copy(
        modules = modules,
        editModuleIdx = modules.length - 1
      )
    }
  }

  def editRepo(idx: Int)(e: raw.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState(_.copy(editRepoIdx = idx))
  }

  def removeRepo(idx: Int)(e: raw.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState(s =>
      s.copy(
        repositories = s.repositories
          .zipWithIndex
          .filter(_._2 != idx)
          .map(_._1)
      )
    )
  }

  def moveRepo(idx: Int, up: Boolean)(e: raw.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState { s =>
      val idx0 = if (up) idx - 1 else idx + 1
      val n = s.repositories.length

      if (idx >= 0 && idx0 >= 0 && idx < n && idx0 < n) {
        val a = s.repositories(idx)
        val b = s.repositories(idx0)

        s.copy(
          repositories = s.repositories
            .updated(idx, b)
            .updated(idx0, a)
        )
      } else
        s
    }
  }

  def updateRepo(repoIdx: Int, update: ((String, MavenRepository), String) => (String, MavenRepository))(e: raw.SyntheticEvent[dom.raw.HTMLInputElement]) =
    if (repoIdx >= 0)
      $.modState { state =>
        val repo = state.repositories(repoIdx)
        state.copy(
          repositories = state.repositories
            .updated(repoIdx, update(repo, e.target.value))
        )
      }
    else
      CallbackTo.pure(())

  def addRepo(e: raw.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState { state =>
      val repositories = state.repositories :+ ("" -> MavenRepository(""))
      println(s"Repositories:\n${repositories.mkString("\n")}")
      state.copy(
        repositories = repositories,
        editRepoIdx = repositories.length - 1
      )
    }
  }

  def enablePopover(e: raw.SyntheticMouseEvent[_]) = CallbackTo[Unit] {
    g.$("[data-toggle='popover']")
      .popover()
  }

  object options {
    def toggleOptional(e: raw.SyntheticEvent[_]) = {
      $.modState(s =>
        s.copy(
          options = s.options
            .copy(followOptional = !s.options.followOptional)
        )
      )
    }
  }
}
