Tech stack
==========

This page explains some of the libraries and tooling used in the code base.

Testing
-------

See the previous sections for test libraries:

- [Unit tests](04_unit_tests.md)
- [scripted tests](05_scripted_tests.md)

sbt
---

The main features of sbt, such as compilation and testing are implemented
using the sbt's settings and task DSL as well as its submodules like IO.

Generally this means that user-facing and immutable configurations
should be expressed as settings, and side effects should be wrapped in a task.

Coursier for dependency management
----------------------------------

Since sbt 1.3, we have switched its library dependency management engine
from Apache Ivy to [Coursier][coursier].

Contraband for datatype
-----------------------

[Contraband][contraband] is a datatype description language that can generate:

- pseudo case classes in Scala or Java
- JSON bindings

```graphql
## Position in a text document expressed as zero-based line and zero-based character offset.
## A position is between two characters like an 'insert' cursor in a editor.
type Position {
  ## Line position in a document (zero-based).
  line: Long!

  ## Character offset on a line in a document (zero-based).
  character: Long!
}

## A range in a text document expressed as (zero-based) start and end positions. A range is comparable to a selection in an editor.
## Therefore the end position is exclusive.
type Range {
  ## The range's start position.
  start: sbt.internal.bsp.Position!

  ## The range's end position.
  end: sbt.internal.bsp.Position!
}
````

Unlike Scala's case classes, Contraband's pseudo case class can evolve over time without breaking binary compatibility of the generated Scala code.

sjson-new, Jawn, and SLIP-28 ScalaJson for JSON
-----------------------------------------------

For JSON serialization, sbt uses [sjson-new][sjson-new], a typeclass-based JSON codec library.
It offers `JsonFormat[A1]` typeclass, which lets you define how a datatype should be
broken down, but it does not specify which JSON AST should be used (backend-independent).
If you used Contraband for data, it can generate sjson-new codec for you.

Jawn is a fast JSON parsing library, that's also backend-independent.

As the concrete JSON AST, sbt adopts SLIP-28 [ScalaJSON][scalajson], a reference implementation of
a proposed effort to create a standard JSON AST. We also forked and shaded it, so it wouldn't clash
if the API changed over time.

Gigahorse for HTTP
------------------

[Gigahorse][gigahorse] is a backend-independent HTTP library sbt uses for some features.
As the concrete backend we use Apache HttpClient 5.x.

```scala
private val http = {
  val defaultHttpRequestTimeout = 2.minutes

  val gigahorseConfig = Gigahorse.config
    .withRequestTimeout(defaultHttpRequestTimeout)
    .withReadTimeout(defaultHttpRequestTimeout)

  Gigahorse.http(gigahorseConfig)
}
val req = Gigahorse
  .url(s"${baseUrl}/publisher/upload?$q")
  .post(
    MultipartFormBody(
      FormPart("bundle", bundleZipPath.toFile())
    )
  )

http.run(reqTransform(req), Gigahorse.asString)

http.close()
```

Putting them all together
-------------------------

Sonatype Publisher API combines Contraband, Jawn, sjson-new, and Gigahorse to access Sonatype's RESTful API.
First, one of its HTTP API is described as a Contraband type:

```graphql
type PublisherStatus {
  deploymentId: String!
  deploymentName: String!
  deploymentState: sbt.internal.sona.DeploymentState!
  purls: [String]
  # Optional errors.
  errors: sjsonnew.shaded.scalajson.ast.unsafe.JValue
}
```

Next, the HTTP endpoint is described as a Gigahorse request.
Finally the HTTP body is transformed via Jawn and sjson-new asynchronously in `http.run(...)`.

```scala
  /**
   * https://central.sonatype.org/publish/publish-portal-api/#verify-status-of-the-deployment
   */
  private def deploymentStatusF(deploymentId: String): Future[PublisherStatus] = {
    val req = Gigahorse
      .url(s"${baseUrl}/publisher/status")
      .addQueryString("id" -> deploymentId)
      .post("", StandardCharsets.UTF_8)
    http.run(reqTransform(req), SonaClient.asPublisherStatus)
  }
```

  [coursier]: https://github.com/coursier/coursier
  [contraband]: https://www.scala-sbt.org/contraband/
  [sjson-new]: https://github.com/eed3si9n/sjson-new
  [scalajson]: https://github.com/sbt/scalajson
  [jawn]: https://github.com/typelevel/jawn
  [gigahorse]: https://eed3si9n.com/gigahorse/
