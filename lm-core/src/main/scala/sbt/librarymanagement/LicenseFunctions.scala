package sbt.librarymanagement

import java.net.URI

/**
 * Commonly used software licenses
 * Names are SPDX ids:
 * https://raw.githubusercontent.com/spdx/license-list-data/master/json/licenses.json
 */
trait LicenseFunctions:
  lazy val Apache2: License =
    License("Apache-2.0", URI("https://www.apache.org/licenses/LICENSE-2.0.txt"))

  lazy val MIT: License =
    License("MIT", URI("https://opensource.org/licenses/MIT"))

  lazy val CC0: License =
    License("CC0-1.0", URI("https://creativecommons.org/publicdomain/zero/1.0/legalcode"))

  def PublicDomain: License = CC0

  lazy val GPL3_or_later: License =
    License("GPL-3.0-or-later", URI("https://spdx.org/licenses/GPL-3.0-or-later.html"))
end LicenseFunctions
