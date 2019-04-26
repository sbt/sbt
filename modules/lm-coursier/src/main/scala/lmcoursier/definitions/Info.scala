package lmcoursier.definitions

final case class Info(
  description: String,
  homePage: String,
  licenses: Seq[(String, Option[String])],
  developers: Seq[Info.Developer],
  publication: Option[Info.DateTime]
)

object Info {
  final case class Developer(
    id: String,
    name: String,
    url: String
  )

  final case class DateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int
  )
}
