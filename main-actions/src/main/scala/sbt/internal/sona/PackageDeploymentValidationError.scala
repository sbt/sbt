/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.sona

import sjsonnew.shaded.scalajson.ast.unsafe.*

/**
 * Represents validation errors for one of the deployed packages in case deployment to sonatype has failed
 *
 * @param packageDescriptor package descriptor<br>
 *                          (e.g. "pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6")
 * @param packageErrors     list of validation errors for the package<br>
 *                          (e.g. ""Component with package url: 'pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6' already exists")
 * @see https://central.sonatype.org/publish/publish-portal-api/#verify-status-of-the-deployment
 */
private case class PackageDeploymentValidationError(
    packageDescriptor: String,
    packageErrors: Seq[String]
)

private object PackageDeploymentValidationError {

  /**
   * Example: (it's not an array but an object which makes it hard to parse with the standard contraband means)
   *  {{{
   *  {
   *      <OTHER_FIELDS>,
   *      "errors": {
   *          "pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6": [
   *            "Component with package url: 'pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6' already exists"
   *          ],
   *          "pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6": [
   *            "Component with package url: 'pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6' already exists"
   *          ]
   *      }
   *  }
   * }}}
   *
   * @param errorsNode - the JSON node that contains the errors
   * @return Some(errors) - if the JSON structure matches our expectations<br>
   *         None - otherwise (Sonatype Central could change the format of the output)
   */
  def parse(errorsNode: JValue): Option[Seq[PackageDeploymentValidationError]] =
    errorsNode match {
      case JObject(fields) =>
        val errors = fields.toSeq.flatMap {
          case JField(packageInfo, JArray(packageErrors)) =>
            val packageErrorsTexts = packageErrors.flatMap {
              case JString(value) => Some(value)
              case other          => None
            }
            val noParsingIssues = packageErrors.length == packageErrorsTexts.length
            if (noParsingIssues)
              Some(PackageDeploymentValidationError(packageInfo, packageErrorsTexts.toSeq))
            else
              None
          case _ =>
            None
        }
        val noParsingIssues = errors.size == fields.length
        if (noParsingIssues)
          Some(errors)
        else
          None
      case _ =>
        None
    }
}
