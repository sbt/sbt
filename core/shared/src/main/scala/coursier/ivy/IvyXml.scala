package coursier.ivy

import coursier.core._
import coursier.util.Xml._

import scalaz.{ Node => _, _ }, Scalaz._

object IvyXml {

  val attributesNamespace = "http://ant.apache.org/ivy/extra"

  private def info(node: Node): String \/ (Module, String) =
    for {
      org <- node.attribute("organisation")
      name <- node.attribute("module")
      version <- node.attribute("revision")
      attr = node.attributesFromNamespace(attributesNamespace)
    } yield (Module(org, name, attr.toMap), version)

  // FIXME Errors are ignored here
  private def configurations(node: Node): Seq[(String, Seq[String])] =
    node.children
      .filter(_.label == "conf")
      .flatMap { node =>
        node.attribute("name").toOption.toSeq.map(_ -> node)
      }
      .map { case (name, node) =>
        name -> node.attribute("extends").toOption.toSeq.flatMap(_.split(','))
      }

  // FIXME "default(compile)" likely not to be always the default
  def mappings(mapping: String): Seq[(String, String)] =
    mapping.split(';').flatMap { m =>
      val (froms, tos) = m.split("->", 2) match {
        case Array(from) => (from, "default(compile)")
        case Array(from, to) => (from, to)
      }

      for {
        from <- froms.split(',')
        to <- tos.split(',')
      } yield (from.trim, to.trim)
    }

  // FIXME Errors ignored as above - warnings should be reported at least for anything suspicious
  private def dependencies(node: Node): Seq[(String, Dependency)] =
    node.children
      .filter(_.label == "dependency")
      .flatMap { node =>
        // artifact and include sub-nodes are ignored here

        val excludes = node.children
          .filter(_.label == "exclude")
          .flatMap { node0 =>
            val org = node0.attribute("org").getOrElse("*")
            val name = node0.attribute("module").orElse(node0.attribute("name")).getOrElse("*")
            val confs = node0.attribute("conf").toOption.filter(_.nonEmpty).fold(Seq("*"))(_.split(','))
            confs.map(_ -> (org, name))
          }
          .groupBy { case (conf, _) => conf }
          .map { case (conf, l) => conf -> l.map { case (_, e) => e }.toSet }

        val allConfsExcludes = excludes.getOrElse("*", Set.empty)

        for {
          org <- node.attribute("org").toOption.toSeq
          name <- node.attribute("name").toOption.toSeq
          version <- node.attribute("rev").toOption.toSeq
          rawConf <- node.attribute("conf").toOption.toSeq
          (fromConf, toConf) <- mappings(rawConf)
          attr = node.attributesFromNamespace(attributesNamespace)
        } yield {
          val transitive = node.attribute("transitive").toOption match {
            case Some("false") => false
            case _ => true
          }

          fromConf -> Dependency(
            Module(org, name, attr.toMap),
            version,
            toConf,
            allConfsExcludes ++ excludes.getOrElse(fromConf, Set.empty),
            Attributes("jar", ""), // should come from possible artifact nodes
            optional = false,
            transitive = transitive
          )
        }
      }

  private def publications(node: Node): Map[String, Seq[Publication]] =
    node.children
      .filter(_.label == "artifact")
      .flatMap { node =>
        val name = node.attribute("name").getOrElse("")
        val type0 = node.attribute("type").getOrElse("jar")
        val ext = node.attribute("ext").getOrElse(type0)
        val confs = node.attribute("conf").toOption.fold(Seq("*"))(_.split(','))
        val classifier = node.attribute("classifier").toOption.getOrElse("")
        confs.map(_ -> Publication(name, type0, ext, classifier))
      }
      .groupBy { case (conf, _) => conf }
      .map { case (conf, l) => conf -> l.map { case (_, p) => p } }

  def project(node: Node): String \/ Project =
    for {
      infoNode <- node.children
        .find(_.label == "info")
        .toRightDisjunction("Info not found")

      (module, version) <- info(infoNode)

      dependenciesNodeOpt = node.children
        .find(_.label == "dependencies")

      dependencies0 = dependenciesNodeOpt.map(dependencies).getOrElse(Nil)

      configurationsNodeOpt = node.children
        .find(_.label == "configurations")

      configurationsOpt = configurationsNodeOpt.map(configurations)

      configurations0 = configurationsOpt.getOrElse(Seq("default" -> Seq.empty[String]))

      publicationsNodeOpt = node.children
        .find(_.label == "publications")

      publicationsOpt = publicationsNodeOpt.map(publications)

    } yield {

      val description = infoNode.children
        .find(_.label == "description")
        .map(_.textContent.trim)
        .getOrElse("")

      val licenses = infoNode.children
        .filter(_.label == "license")
        .flatMap { n =>
          n.attribute("name").toOption.map { name =>
            (name, n.attribute("url").toOption)
          }.toSeq
        }

      val publicationDate = infoNode.attribute("publication")
        .toOption
        .flatMap(parseDateTime)

      Project(
        module,
        version,
        dependencies0,
        configurations0.toMap,
        None,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        if (publicationsOpt.isEmpty)
          // no publications node -> default JAR artifact
          Seq("*" -> Publication(module.name, "jar", "jar", ""))
        else {
          // publications node is there -> only its content (if it is empty, no artifacts,
          // as per the Ivy manual)
          val inAllConfs = publicationsOpt.flatMap(_.get("*")).getOrElse(Nil)
          configurations0.flatMap { case (conf, _) =>
            (publicationsOpt.flatMap(_.get(conf)).getOrElse(Nil) ++ inAllConfs).map(conf -> _)
          }
        },
        Info(
          description,
          "",
          licenses,
          Nil,
          publicationDate
        )
      )
    }

}
