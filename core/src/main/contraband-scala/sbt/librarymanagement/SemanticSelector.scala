/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Semantic version selector API to check if the VersionNumber satisfies
 * conditions described by semantic version selector.
 * 
 * A `comparator` generally consist of an operator and version specifier.
 * The set of operators is
 * - `<`: Less than
 * - `<=`: Less than or equal to
 * - `>`: Greater than
 * - `>=`: Greater than or equal to
 * - `=`: Equal
 * If no operator is specified, `=` is assumed.
 * 
 * If minor or patch versions are not specified, some numbers are assumed.
 * - `<=1.0` is equivalent to `<1.1.0`.
 * - `<1.0` is equivalent to `<1.0.0`.
 * - `>=1.0` is equivalent to `>=1.0.0`.
 * - `>1.0` is equivalent to `>=1.1.0`.
 * - `=1.0` is equivalent to `>=1.0 <=1.0` (so `>=1.0.0 <1.1.0`).
 * 
 * Comparators can be combined by spaces to form the intersection set of the comparators.
 * For example, `>1.2.3 <4.5.6` matches versions that are `greater than 1.2.3 AND less than 4.5.6`.
 * 
 * The (intersection) set of comparators can combined by ` || ` (spaces are required) to form the
 * union set of the intersection sets. So the semantic selector is in disjunctive normal form.
 * 
 * Wildcard (`x`, `X`, `*`) can be used to match any number of minor or patch version.
 * Actually, `1.0.x` is equivalent to `=1.0` (that is equivalent to `>=1.0.0 <1.1.0`)
 * 
 * The hyphen range like `1.2.3 - 4.5.6` matches inclusive set of versions.
 * So `1.2.3 - 4.5.6` is equivalent to `>=1.2.3 <=4.5.6`.
 * Both sides of comparators around - are required and they can not have any operators.
 * For example, `>=1.2.3 - 4.5.6` is invalid.
 * 
 * The order of versions basically follows the rule specified in https://semver.org/#spec-item-11
 * > When major, minor, and patch are equal, a pre-release version has lower precedence
 * > than a normal version. Example: 1.0.0-alpha < 1.0.0.
 * > Precedence for two pre-release versions with the same major, minor, and patch version
 * > Must be determined by comparing each dot separated identifier from left to right
 * > until a difference is found as follows:
 * > identifiers consisting of only digits are compared numerically
 * > and identifiers with letters or hyphens are compared lexically in ASCII sort order.
 * > Numeric identifiers always have lower precedence than non-numeric identifiers.
 * > A larger set of pre-release fields has a higher precedence than a smaller set,
 * > if all of the preceding identifiers are equal.
 * > Example: 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0.
 * 
 * The differences from the original specification are following
 * - `SemanticVersionSelector` separetes the pre-release fields by hyphen instead of dot
 * - hyphen cannot be used in pre-release identifiers because it is used as separator for pre-release fields
 * 
 * Therefore, in order to match pre-release versions like `1.0.0-beta`
 * we need to explicitly specify the pre-release identifiers like `>=1.0.0-alpha`.
 */
final class SemanticSelector private (
  val selectors: Seq[sbt.internal.librarymanagement.SemSelAndChunk]) extends Serializable {
  def matches(versionNumber: VersionNumber): Boolean = selectors.exists(_.matches(versionNumber))
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SemanticSelector => (this.selectors == x.selectors)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.librarymanagement.SemanticSelector".##) + selectors.##)
  }
  override def toString: String = {
    selectors.map(_.toString).mkString(" || ")
  }
  private[this] def copy(selectors: Seq[sbt.internal.librarymanagement.SemSelAndChunk] = selectors): SemanticSelector = {
    new SemanticSelector(selectors)
  }
  def withSelectors(selectors: Seq[sbt.internal.librarymanagement.SemSelAndChunk]): SemanticSelector = {
    copy(selectors = selectors)
  }
}
object SemanticSelector {
  def apply(selector: String): SemanticSelector = {
    val orChunkTokens = selector.split("\\s+\\|\\|\\s+").map(_.trim)
    val orChunks = orChunkTokens.map { chunk => sbt.internal.librarymanagement.SemSelAndChunk(chunk) }
    SemanticSelector(scala.collection.immutable.ArraySeq.unsafeWrapArray(orChunks))
  }
  def apply(selectors: Seq[sbt.internal.librarymanagement.SemSelAndChunk]): SemanticSelector = new SemanticSelector(selectors)
}
