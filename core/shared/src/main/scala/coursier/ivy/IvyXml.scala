package coursier.ivy

import coursier.core._
import coursier.util.Xml._

object IvyXml {

  val attributesNamespace = "http://ant.apache.org/ivy/extra"

  private def info(node: Node): Either[String, (Module, String)] =
    for {
      org <- node.attribute("organisation").right
      name <- node.attribute("module").right
      version <- node.attribute("revision").right
    } yield {
      val attr = node.attributesFromNamespace(attributesNamespace)
      (Module(org, name, attr.toMap), version)
    }

  // FIXME Errors are ignored here
  private def configurations(node: Node): Seq[(String, Seq[String])] =
    node.children
      .filter(_.label == "conf")
      .flatMap { node =>
        node.attribute("name").right.toOption.toSeq.map(_ -> node)
      }
      .map { case (name, node) =>
        name -> node.attribute("extends").right.toSeq.flatMap(_.split(','))
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
            val org = node0.attribute("org").right.getOrElse("*")
            val name = node0.attribute("module").right.toOption
              .orElse(node0.attribute("name").right.toOption)
              .getOrElse("*")
            val confs = node0.attribute("conf").right.toOption.filter(_.nonEmpty).fold(Seq("*"))(_.split(','))
            confs.map(_ -> (org, name))
          }
          .groupBy { case (conf, _) => conf }
          .map { case (conf, l) => conf -> l.map { case (_, e) => e }.toSet }

        val allConfsExcludes = excludes.getOrElse("*", Set.empty)

        for {
          org <- node.attribute("org").right.toOption.toSeq
          name <- node.attribute("name").right.toOption.toSeq
          version <- node.attribute("rev").right.toOption.toSeq
          rawConf <- node.attribute("conf").right.toOption.toSeq
          (fromConf, toConf) <- mappings(rawConf)
        } yield {
          val attr = node.attributesFromNamespace(attributesNamespace)
          val transitive = node.attribute("transitive") match {
            case Right("false") => false
            case _ => true
          }

          fromConf -> Dependency(
            Module(org, name, attr.toMap),
            version,
            toConf,
            allConfsExcludes ++ excludes.getOrElse(fromConf, Set.empty),
            Attributes("", ""), // should come from possible artifact nodes
            optional = false,
            transitive = transitive
          )
        }
      }

  private def publications(node: Node): Map[String, Seq[Publication]] =
    node.children
      .filter(_.label == "artifact")
      .flatMap { node =>
        val name = node.attribute("name").right.getOrElse("")
        val type0 = node.attribute("type").right.getOrElse("jar")
        val ext = node.attribute("ext").right.getOrElse(type0)
        val confs = node.attribute("conf").fold(_ => Seq("*"), _.split(',').toSeq)
        val classifier = node.attribute("classifier").right.getOrElse("")
        confs.map(_ -> Publication(name, type0, ext, classifier))
      }
      .groupBy { case (conf, _) => conf }
      .map { case (conf, l) => conf -> l.map { case (_, p) => p } }

  def project(node: Node): Either[String, Project] =
    for {
      infoNode <- node.children
        .find(_.label == "info")
        .toRight("Info not found")
        .right

      modVer <- info(infoNode).right
    } yield {

      val (module, version) = modVer

      val dependenciesNodeOpt = node.children
        .find(_.label == "dependencies")

      val dependencies0 = dependenciesNodeOpt.map(dependencies).getOrElse(Nil)

      val configurationsNodeOpt = node.children
        .find(_.label == "configurations")

      val configurationsOpt = configurationsNodeOpt.map(configurations)

      val configurations0 = configurationsOpt.getOrElse(Seq("default" -> Seq.empty[String]))

      val publicationsNodeOpt = node.children
        .find(_.label == "publications")

      val publicationsOpt = publicationsNodeOpt.map(publications)

      val description = infoNode.children
        .find(_.label == "description")
        .map(_.textContent.trim)
        .getOrElse("")

      val licenses = infoNode.children
        .filter(_.label == "license")
        .flatMap { n =>
          n.attribute("name").right.toSeq.map { name =>
            (name, n.attribute("url").right.toOption)
          }
        }

      val publicationDate = infoNode.attribute("publication")
        .right
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
