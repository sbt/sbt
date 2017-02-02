package coursier
package web

import coursier.maven.MavenSource

import japgolly.scalajs.react.vdom.{ TagMod, Attr }
import japgolly.scalajs.react.vdom.Attrs.dangerouslySetInnerHtml
import japgolly.scalajs.react.{ ReactEventI, ReactComponentB, BackendScope }
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.jquery.jQuery

import scala.concurrent.Future

import scala.scalajs.js
import js.Dynamic.{ global => g }

final case class ResolutionOptions(
  followOptional: Boolean = false
)

final case class State(
  modules: Seq[Dependency],
  repositories: Seq[(String, MavenRepository)],
  options: ResolutionOptions,
  resolutionOpt: Option[Resolution],
  editModuleIdx: Int,
  editRepoIdx: Int,
  resolving: Boolean,
  reverseTree: Boolean,
  log: Seq[String]
)

class Backend($: BackendScope[Unit, State]) {

  def fetch(
    repositories: Seq[core.Repository],
    fetch: Fetch.Content[Task]
  ): Fetch.Metadata[Task] = {

    modVers => Task.gatherUnordered(
      modVers.map { case (module, version) =>
        Fetch.find(repositories, module, version, fetch)
          .run
          .map((module, version) -> _)
      }
    )
  }


  def updateDepGraph(resolution: Resolution) = {
    println("Rendering canvas")

    val graph = js.Dynamic.newInstance(g.Graph)()

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

    val layouter = js.Dynamic.newInstance(g.Graph.Layout.Spring)(graph)
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

    val renderer = js.Dynamic.newInstance(g.Graph.Renderer.Raphael)(
      "depgraphcanvas", graph, width, height
    )
    renderer.draw()
    println("Rendered canvas")
  }

  def updateDepGraphBtn(resolution: Resolution)(e: ReactEventI) = {
    updateDepGraph(resolution)
  }

  def updateTree(resolution: Resolution, target: String, reverse: Boolean) = {
    def depsOf(dep: Dependency) =
      resolution.projectCache
        .get(dep.moduleVersion)
        .toSeq
        .flatMap{case (_, proj) =>
          core.Resolution.finalDependencies(dep, proj)
            .filter(resolution.filter getOrElse core.Resolution.defaultFilter)
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
    g.$(target)
      .treeview(js.Dictionary("data" -> js.Array(minDependencies.toList.map(tree): _*)))
  }

  def resolve(action: => Unit = ()) = {
    g.$("#resLogTab a:last").tab("show")
    $.modState(_.copy(resolving = true, log = Nil))

    val logger: Platform.Logger = new Platform.Logger {
      def fetched(url: String) = {
        println(s"<- $url")
        $.modState(s => s.copy(log = s"<- $url" +: s.log))
      }
      def fetching(url: String) = {
        println(s"-> $url")
        $.modState(s => s.copy(log = s"-> $url" +: s.log))
      }
      def other(url: String, msg: String) = {
        println(s"$url: $msg")
        $.modState(s => s.copy(log = s"$url: $msg" +: s.log))
      }
    }

    val s = $.state
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

    // For reasons that are unclear to me, not delaying this when using the runNow execution context
    // somehow discards the $.modState above. (Not a major problem as queue is used by default.)
    Future(task)(scala.scalajs.concurrent.JSExecutionContext.Implicits.queue).flatMap(_.runF).foreach { res: Resolution =>
      $.modState{ s =>
        updateDepGraph(res)
        updateTree(res, "#deptree", reverse = s.reverseTree)

        s.copy(
          resolutionOpt = Some(res),
          resolving = false
        )
      }

      g.$("#resResTab a:last")
        .tab("show")
    }
  }
  def handleResolve(e: ReactEventI) = {
    println(s"Resolving")
    e.preventDefault()
    jQuery("#results").css("display", "block")
    resolve()
  }

  def clearLog(e: ReactEventI) = {
    $.modState(_.copy(log = Nil))
  }

  def toggleReverseTree(e: ReactEventI) = {
    $.modState{ s =>
      for (res <- s.resolutionOpt)
        updateTree(res, "#deptree", reverse = !s.reverseTree)
      s.copy(reverseTree = !s.reverseTree)
    }
  }

  def editModule(idx: Int)(e: ReactEventI) = {
    e.preventDefault()
    $.modState(_.copy(editModuleIdx = idx))
  }

  def removeModule(idx: Int)(e: ReactEventI) = {
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

  def updateModule(moduleIdx: Int, update: (Dependency, String) => Dependency)(e: ReactEventI) = {
    if (moduleIdx >= 0) {
      $.modState{ state =>
        val dep = state.modules(moduleIdx)
        state.copy(
          modules = state.modules
            .updated(moduleIdx, update(dep, e.target.value))
        )
      }
    }
  }

  def addModule(e: ReactEventI) = {
    e.preventDefault()
    $.modState{ state =>
      val modules = state.modules :+ Dependency(Module("", ""), "")
      println(s"Modules:\n${modules.mkString("\n")}")
      state.copy(
        modules = modules,
        editModuleIdx = modules.length - 1
      )
    }
  }

  def editRepo(idx: Int)(e: ReactEventI) = {
    e.preventDefault()
    $.modState(_.copy(editRepoIdx = idx))
  }

  def removeRepo(idx: Int)(e: ReactEventI) = {
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

  def moveRepo(idx: Int, up: Boolean)(e: ReactEventI) = {
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

  def updateRepo(repoIdx: Int, update: ((String, MavenRepository), String) => (String, MavenRepository))(e: ReactEventI) = {
    if (repoIdx >= 0) {
      $.modState{ state =>
        val repo = state.repositories(repoIdx)
        state.copy(
          repositories = state.repositories
            .updated(repoIdx, update(repo, e.target.value))
        )
      }
    }
  }

  def addRepo(e: ReactEventI) = {
    e.preventDefault()
    $.modState{ state =>
      val repositories = state.repositories :+ ("" -> MavenRepository(""))
      println(s"Repositories:\n${repositories.mkString("\n")}")
      state.copy(
        repositories = repositories,
        editRepoIdx = repositories.length - 1
      )
    }
  }

  def enablePopover(e: ReactEventI) = {
    g.$("[data-toggle='popover']")
      .popover()
  }

  object options {
    def toggleOptional(e: ReactEventI) = {
      $.modState(s =>
        s.copy(
          options = s.options
            .copy(followOptional = !s.options.followOptional)
        )
      )
    }
  }
}

object App {

  lazy val arbor = g.arbor

  val resultDependencies = ReactComponentB[(Resolution, Backend)]("Result")
    .render{ T =>
      val (res, backend) = T

      def infoLabel(label: String) =
        <.span(^.`class` := "label label-info", label)
      def errorPopOver(label: String, desc: String) =
        popOver("danger", label, desc)
      def infoPopOver(label: String, desc: String) =
        popOver("info", label, desc)
      def popOver(`type`: String, label: String, desc: String) =
        <.button(^.`type` := "button", ^.`class` := s"btn btn-xs btn-${`type`}",
          Attr("data-trigger") := "focus",
          Attr("data-toggle") := "popover", Attr("data-placement") := "bottom",
          Attr("data-content") := desc,
          ^.onClick ==> backend.enablePopover,
          ^.onMouseOver ==> backend.enablePopover,
          label
        )

      def depItem(dep: Dependency, finalVersionOpt: Option[String]) = {
        <.tr(
          ^.`class` := (if (res.errorCache.contains(dep.moduleVersion)) "danger" else ""),
          <.td(dep.module.organization),
          <.td(dep.module.name),
          <.td(finalVersionOpt.fold(dep.version)(finalVersion => s"$finalVersion (for ${dep.version})")),
          <.td(Seq[Seq[TagMod]](
            if (dep.configuration == "compile") Seq() else Seq(infoLabel(dep.configuration)),
            if (dep.attributes.`type`.isEmpty || dep.attributes.`type` == "jar") Seq() else Seq(infoLabel(dep.attributes.`type`)),
            if (dep.attributes.classifier.isEmpty) Seq() else Seq(infoLabel(dep.attributes.classifier)),
            Some(dep.exclusions).filter(_.nonEmpty).map(excls => infoPopOver("Exclusions", excls.toList.sorted.map{case (org, name) => s"$org:$name"}.mkString("; "))).toSeq,
            if (dep.optional) Seq(infoLabel("optional")) else Seq(),
            res.errorCache.get(dep.moduleVersion).map(errs => errorPopOver("Error", errs.mkString("; "))).toSeq
          )),
         <.td(Seq[Seq[TagMod]](
           res.projectCache.get(dep.moduleVersion) match {
             case Some((source: MavenSource, proj)) =>
               // FIXME Maven specific, generalize with source.artifacts
               val version0 = finalVersionOpt getOrElse dep.version
               val relPath =
                 dep.module.organization.split('.').toSeq ++ Seq(
                   dep.module.name,
                   version0,
                   s"${dep.module.name}-$version0"
                 )

               val root = source.root

               Seq(
                 <.a(^.href := s"$root${relPath.mkString("/")}.pom",
                   <.span(^.`class` := "label label-info", "POM")
                 ),
                 <.a(^.href := s"$root${relPath.mkString("/")}.jar",
                   <.span(^.`class` := "label label-info", "JAR")
                 )
               )

             case _ => Seq()
           }
         ))
        )
      }

      val sortedDeps = res.minDependencies.toList
        .sortBy { dep =>
          val (org, name, _) = coursier.core.Module.unapply(dep.module).get
          (org, name)
        }

      <.table(^.`class` := "table",
        <.thead(
          <.tr(
            <.th("Organization"),
            <.th("Name"),
            <.th("Version"),
            <.th("Extra"),
            <.th("Links")
          )
        ),
        <.tbody(
          sortedDeps.map(dep =>
            depItem(
              dep,
              res
                .projectCache
                .get(dep.moduleVersion)
                .map(_._2.version)
                .filter(_ != dep.version)
            )
          )
        )
      )
    }
    .build

  object icon {
    def apply(id: String) = <.span(^.`class` := s"glyphicon glyphicon-$id", ^.aria.hidden := "true")
    def ok = apply("ok")
    def edit = apply("pencil")
    def remove = apply("remove")
    def up = apply("arrow-up")
    def down = apply("arrow-down")
  }

  val moduleEditModal = ReactComponentB[((Module, String), Int, Backend)]("EditModule")
    .render{ P =>
      val ((module, version), moduleIdx, backend) = P
      <.div(^.`class` := "modal fade", ^.id := "moduleEdit", ^.role := "dialog", ^.aria.labelledby := "moduleEditTitle",
        <.div(^.`class` := "modal-dialog", <.div(^.`class` := "modal-content",
          <.div(^.`class` := "modal-header",
            <.button(^.`type` := "button", ^.`class` := "close", Attr("data-dismiss") := "modal", ^.aria.label := "Close",
              <.span(^.aria.hidden := "true", dangerouslySetInnerHtml("&times;"))
            ),
            <.h4(^.`class` := "modal-title", ^.id := "moduleEditTitle", "Dependency")
          ),
          <.div(^.`class` := "modal-body",
            <.form(
              <.div(^.`class` := "form-group",
                <.label(^.`for` := "inputOrganization", "Organization"),
                <.input(^.`class` := "form-control", ^.id := "inputOrganization", ^.placeholder := "Organization",
                  ^.onChange ==> backend.updateModule(moduleIdx, (dep, value) => dep.copy(module = dep.module.copy(organization = value))),
                  ^.value := module.organization
                )
              ),
              <.div(^.`class` := "form-group",
                <.label(^.`for` := "inputName", "Name"),
                <.input(^.`class` := "form-control", ^.id := "inputName", ^.placeholder := "Name",
                  ^.onChange ==> backend.updateModule(moduleIdx, (dep, value) => dep.copy(module = dep.module.copy(name = value))),
                  ^.value := module.name
                )
              ),
              <.div(^.`class` := "form-group",
                <.label(^.`for` := "inputVersion", "Version"),
                <.input(^.`class` := "form-control", ^.id := "inputVersion", ^.placeholder := "Version",
                  ^.onChange ==> backend.updateModule(moduleIdx, (dep, value) => dep.copy(version = value)),
                  ^.value := version
                )
              ),
              <.div(^.`class` := "modal-footer",
                <.button(^.`type` := "submit", ^.`class` := "btn btn-primary", Attr("data-dismiss") := "modal", "Done")
              )
            )
          )
        ))
      )
    }
    .build

  val modules = ReactComponentB[(Seq[Dependency], Int, Backend)]("Dependencies")
    .render{ P =>
      val (deps, editModuleIdx, backend) = P

      def depItem(dep: Dependency, idx: Int) =
        <.tr(
          <.td(dep.module.organization),
          <.td(dep.module.name),
          <.td(dep.version),
          <.td(
            <.a(Attr("data-toggle") := "modal", Attr("data-target") := "#moduleEdit", ^.`class` := "icon-action",
              ^.onClick ==> backend.editModule(idx),
              icon.edit
            )
          ),
          <.td(
            <.a(Attr("data-toggle") := "modal", Attr("data-target") := "#moduleRemove", ^.`class` := "icon-action",
              ^.onClick ==> backend.removeModule(idx),
              icon.remove
            )
          )
        )

      <.div(
        <.p(
          <.button(^.`type` := "button", ^.`class` := "btn btn-default customButton",
            ^.onClick ==> backend.addModule,
            Attr("data-toggle") := "modal",
            Attr("data-target") := "#moduleEdit",
            "Add"
          )
        ),
        <.table(^.`class` := "table",
          <.thead(
            <.tr(
              <.th("Organization"),
              <.th("Name"),
              <.th("Version"),
              <.th(""),
              <.th("")
            )
          ),
          <.tbody(
            deps.zipWithIndex
              .map((depItem _).tupled)
          )
        ),
        moduleEditModal((
          deps
            .lift(editModuleIdx)
            .fold(Module("", "") -> "")(_.moduleVersion),
          editModuleIdx,
          backend
        ))
      )
    }
    .build

  val repoEditModal = ReactComponentB[((String, MavenRepository), Int, Backend)]("EditRepo")
    .render{ P =>
      val ((name, repo), repoIdx, backend) = P
      <.div(^.`class` := "modal fade", ^.id := "repoEdit", ^.role := "dialog", ^.aria.labelledby := "repoEditTitle",
        <.div(^.`class` := "modal-dialog", <.div(^.`class` := "modal-content",
          <.div(^.`class` := "modal-header",
            <.button(^.`type` := "button", ^.`class` := "close", Attr("data-dismiss") := "modal", ^.aria.label := "Close",
              <.span(^.aria.hidden := "true", dangerouslySetInnerHtml("&times;"))
            ),
            <.h4(^.`class` := "modal-title", ^.id := "repoEditTitle", "Repository")
          ),
          <.div(^.`class` := "modal-body",
            <.form(
              <.div(^.`class` := "form-group",
                <.label(^.`for` := "inputName", "Name"),
                <.input(^.`class` := "form-control", ^.id := "inputName", ^.placeholder := "Name",
                  ^.onChange ==> backend.updateRepo(repoIdx, (item, value) => (value, item._2)),
                  ^.value := name
                )
              ),
              <.div(^.`class` := "form-group",
                <.label(^.`for` := "inputVersion", "Root"),
                <.input(^.`class` := "form-control", ^.id := "inputVersion", ^.placeholder := "Root",
                  ^.onChange ==> backend.updateRepo(repoIdx, (item, value) => (item._1, item._2.copy(root = value))),
                  ^.value := repo.root
                )
              ),
              <.div(^.`class` := "modal-footer",
                <.button(^.`type` := "submit", ^.`class` := "btn btn-primary", Attr("data-dismiss") := "modal", "Done")
              )
            )
          )
        ))
      )
    }
    .build

  val repositories = ReactComponentB[(Seq[(String, MavenRepository)], Int, Backend)]("Repositories")
    .render{ P =>
      val (repos, editRepoIdx, backend) = P

      def repoItem(item: (String, MavenRepository), idx: Int, isLast: Boolean) =
        <.tr(
          <.td(item._1),
          <.td(item._2.root),
          <.td(
            <.a(Attr("data-toggle") := "modal", Attr("data-target") := "#repoEdit", ^.`class` := "icon-action",
              ^.onClick ==> backend.editRepo(idx),
              icon.edit
            )
          ),
          <.td(
            <.a(Attr("data-toggle") := "modal", Attr("data-target") := "#repoRemove", ^.`class` := "icon-action",
              ^.onClick ==> backend.removeRepo(idx),
              icon.remove
            )
          ),
          <.td(
            if (idx > 0)
              Seq(<.a(Attr("data-toggle") := "modal", Attr("data-target") := "#repoUp", ^.`class` := "icon-action",
                ^.onClick ==> backend.moveRepo(idx, up = true),
                icon.up
              ))
            else
              Seq()
          ),
          <.td(
            if (isLast)
              Seq()
            else
              Seq(<.a(Attr("data-toggle") := "modal", Attr("data-target") := "#repoDown", ^.`class` := "icon-action",
                ^.onClick ==> backend.moveRepo(idx, up = false),
                icon.down
              ))
          )
        )

      <.div(
        <.p(
          <.button(^.`type` := "button", ^.`class` := "btn btn-default customButton",
            ^.onClick ==> backend.addRepo,
            Attr("data-toggle") := "modal",
            Attr("data-target") := "#repoEdit",
            "Add"
          )
        ),
        <.table(^.`class` := "table",
          <.thead(
            <.tr(
              <.th("Name"),
              <.th("Root"),
              <.th(""),
              <.th(""),
              <.th(""),
              <.th("")
            )
          ),
          <.tbody(
            repos.init.zipWithIndex
              .map(t => repoItem(t._1, t._2, isLast = false)) ++
            repos.lastOption.map(repoItem(_, repos.length - 1, isLast = true))
          )
        ),
        repoEditModal((
          repos
            .lift(editRepoIdx)
            .getOrElse("" -> MavenRepository("")),
          editRepoIdx,
          backend
        ))
      )
    }
    .build

  val options = ReactComponentB[(ResolutionOptions, Backend)]("ResolutionOptions")
    .render{ P =>
      val (options, backend) = P

      <.div(
        <.div(^.`class` := "checkbox",
          <.label(
            <.input(^.`type` := "checkbox",
              ^.onChange ==> backend.options.toggleOptional,
              if (options.followOptional) Seq(^.checked := "checked") else Seq(),
              "Follow optional dependencies"
            )
          )
        )
      )
    }
    .build

  val resolution = ReactComponentB[(Option[Resolution], Backend)]("Resolution")
    .render{ T =>
      val (resOpt, backend) = T

      resOpt match {
        case Some(res) =>
          <.div(
            <.div(^.`class` := "page-header",
              <.h1("Resolution")
            ),
            resultDependencies((res, backend))
          )

        case None =>
          <.div()
      }
    }
    .build

  val initialState = State(
    Nil,
    Seq("central" -> MavenRepository("https://repo1.maven.org/maven2/")),
    ResolutionOptions(),
    None,
    -1,
    -1,
    resolving = false,
    reverseTree = false,
    log = Nil
  )

  val app = ReactComponentB[Unit]("Coursier")
    .initialState(initialState)
    .backend(new Backend(_))
    .render((_,S,B) =>
      <.div(
        <.div(^.role := "tabpanel",
          <.ul(^.`class` := "nav nav-tabs", ^.role := "tablist",
            <.li(^.role := "presentation", ^.`class` := "active",
              <.a(^.href := "#dependencies", ^.aria.controls := "dependencies", ^.role := "tab", Attr("data-toggle") := "tab",
                s"Dependencies (${S.modules.length})"
              )
            ),
            <.li(^.role := "presentation",
              <.a(^.href := "#repositories", ^.aria.controls := "repositories", ^.role := "tab", Attr("data-toggle") := "tab",
                s"Repositories (${S.repositories.length})"
              )
            ),
            <.li(^.role := "presentation",
              <.a(^.href := "#options", ^.aria.controls := "options", ^.role := "tab", Attr("data-toggle") := "tab",
                "Options"
              )
            )
          ),
          <.div(^.`class` := "tab-content",
            <.div(^.role := "tabpanel", ^.`class` := "tab-pane active", ^.id := "dependencies",
              modules((S.modules, S.editModuleIdx, B))
            ),
            <.div(^.role := "tabpanel", ^.`class` := "tab-pane", ^.id := "repositories",
              repositories((S.repositories, S.editRepoIdx, B))
            ),
            <.div(^.role := "tabpanel", ^.`class` := "tab-pane", ^.id := "options",
              options((S.options, B))
            )
          )
        ),

        <.div(<.form(^.onSubmit ==> B.handleResolve,
          <.button(^.`type` := "submit", ^.id := "resolveButton", ^.`class` := "btn btn-lg btn-primary",
            if (S.resolving) ^.disabled := "true" else Attr("active") := "true",
            if (S.resolving) "Resolving..." else "Resolve"
          )
        )),


        <.div(^.role := "tabpanel", ^.id := "results",
          <.ul(^.`class` := "nav nav-tabs", ^.role := "tablist", ^.id := "resTabs",
            <.li(^.role := "presentation", ^.id := "resResTab",
              <.a(^.href := "#resolution", ^.aria.controls := "resolution", ^.role := "tab", Attr("data-toggle") := "tab",
                "Resolution"
              )
            ),
            <.li(^.role := "presentation", ^.id := "resLogTab",
              <.a(^.href := "#log", ^.aria.controls := "log", ^.role := "tab", Attr("data-toggle") := "tab",
                "Log"
              )
            ),
            <.li(^.role := "presentation",
              <.a(^.href := "#depgraph", ^.aria.controls := "depgraph", ^.role := "tab", Attr("data-toggle") := "tab",
                "Graph"
              )
            ),
            <.li(^.role := "presentation",
              <.a(^.href := "#deptreepanel", ^.aria.controls := "deptreepanel", ^.role := "tab", Attr("data-toggle") := "tab",
                "Tree"
              )
            )
          ),
          <.div(^.`class` := "tab-content",
            <.div(^.role := "tabpanel", ^.`class` := "tab-pane", ^.id := "resolution",
              resolution((S.resolutionOpt, B))
            ),
            <.div(^.role := "tabpanel", ^.`class` := "tab-pane", ^.id := "log",
              <.button(^.`type` := "button", ^.`class` := "btn btn-default",
                ^.onClick ==> B.clearLog,
                "Clear"
              ),
              <.div(^.`class` := "well",
                <.ul(^.`class` := "log",
                  S.log.map(e => <.li(e))
                )
              )
            ),
            <.div(^.role := "tabpanel", ^.`class` := "tab-pane", ^.id := "depgraph",
              <.button(^.`type` := "button", ^.`class` := "btn btn-default",
                ^.onClick ==> B.updateDepGraphBtn(S.resolutionOpt.getOrElse(Resolution.empty)),
                "Redraw"
              ),
              <.div(^.id := "depgraphcanvas")
            ),
            <.div(^.role := "tabpanel", ^.`class` := "tab-pane", ^.id := "deptreepanel",
              <.div(^.`class` := "checkbox",
                <.label(
                  <.input(^.`type` := "checkbox",
                    ^.onChange ==> B.toggleReverseTree,
                    if (S.reverseTree) Seq(^.checked := "checked") else Seq(),
                    "Reverse"
                  )
                )
              ),
              <.div(^.id := "deptree")
            )
          )
        )
      )
    )
    .buildU

}
