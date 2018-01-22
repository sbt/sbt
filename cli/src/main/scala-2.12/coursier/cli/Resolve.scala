package coursier
package cli

import caseapp._
import coursier.cli.options.ResolveOptions

object Resolve extends CaseApp[ResolveOptions] {

  def run(options: ResolveOptions, args: RemainingArgs): Unit = {
    new Helper(options.common, args.all, printResultStdout = true)
  }

}
