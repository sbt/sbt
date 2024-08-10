package sbt
package internal
package util
package appmacro

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.quoted.*
import sjsonnew.{ BasicJsonProtocol, HashWriter, JsonFormat }
import sbt.util.{
  ActionCache,
  Applicative,
  BuildWideCacheConfiguration,
  CacheLevelTag,
  Digest,
  Monad,
}
import xsbti.{ VirtualFile, VirtualFileRef }

/**
 * Implementation of a macro that provides a direct syntax for applicative functors and monads. It
 * is intended to be used in conjunction with another macro that conditions the inputs.
 */
trait Cont:
  final val InstanceTCName = "F"

  extension [C <: Quotes & Singleton](conv: Convert[C])
    /**
     * Implementation of a macro that provides a direct syntax for applicative functors. It is
     * intended to be used in conjunction with another macro that conditions the inputs.
     */
    def contMapN[A: Type, F[_], Effect[_]: Type](
        tree: Expr[A],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExpr: Option[Expr[BuildWideCacheConfiguration]]
    )(using iftpe: Type[F], eatpe: Type[Effect[A]]): Expr[F[Effect[A]]] =
      contMapN[A, F, Effect](tree, applicativeExpr, cacheConfigExpr, conv.idTransform)

    /**
     * Implementation of a macro that provides a direct syntax for applicative functors. It is
     * intended to be used in conjunction with another macro that conditions the inputs.
     */
    def contMapN[A: Type, F[_], Effect[_]: Type](
        tree: Expr[A],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExpr: Option[Expr[BuildWideCacheConfiguration]],
        inner: conv.TermTransform[Effect]
    )(using
        iftpe: Type[F],
        eatpe: Type[Effect[A]],
    ): Expr[F[Effect[A]]] =
      contImpl[A, F, Effect](Left(tree), applicativeExpr, cacheConfigExpr, inner)

    /**
     * Implementation of a macro that provides a direct syntax for applicative functors. It is
     * intended to be used in conjunction with another macro that conditions the inputs.
     */
    def contFlatMap[A: Type, F[_], Effect[_]: Type](
        tree: Expr[F[A]],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExpr: Option[Expr[BuildWideCacheConfiguration]],
    )(using
        iftpe: Type[F],
        eatpe: Type[Effect[A]],
    ): Expr[F[Effect[A]]] =
      contFlatMap[A, F, Effect](tree, applicativeExpr, cacheConfigExpr, conv.idTransform)

    /**
     * Implementation of a macro that provides a direct syntax for applicative functors. It is
     * intended to be used in conjunction with another macro that conditions the inputs.
     */
    def contFlatMap[A: Type, F[_], Effect[_]: Type](
        tree: Expr[F[A]],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExpr: Option[Expr[BuildWideCacheConfiguration]],
        inner: conv.TermTransform[Effect]
    )(using
        iftpe: Type[F],
        eatpe: Type[Effect[A]],
    ): Expr[F[Effect[A]]] =
      contImpl[A, F, Effect](Right(tree), applicativeExpr, cacheConfigExpr, inner)

    def summonAppExpr[F[_]: Type]: Expr[Applicative[F]] =
      import conv.qctx
      import qctx.reflect.*
      given qctx.type = qctx
      Expr
        .summon[Applicative[F]]
        .getOrElse(
          report.errorAndAbort(s"Applicative[F] not found for ${TypeRepr.of[F].typeSymbol}")
        )

    def summonHashWriter[A: Type]: Expr[HashWriter[A]] =
      import conv.qctx
      import qctx.reflect.*
      given qctx.type = qctx
      Expr
        .summon[HashWriter[A]]
        .getOrElse(sys.error(s"HashWriter[A] not found for ${TypeRepr.of[A].show}"))

    def summonJsonFormat[A: Type]: Expr[JsonFormat[A]] =
      import conv.qctx
      import qctx.reflect.*
      given qctx.type = qctx
      Expr
        .summon[JsonFormat[A]]
        .getOrElse(sys.error(s"JsonFormat[A] not found for ${TypeRepr.of[A].show}"))

    def summonClassTag[A: Type]: Expr[ClassTag[A]] =
      import conv.qctx
      import qctx.reflect.*
      given qctx.type = qctx
      Expr
        .summon[ClassTag[A]]
        .getOrElse(sys.error(s"ClassTag[A] not found for ${TypeRepr.of[A].show}"))

    /**
     * Implementation of a macro that provides a direct syntax for applicative functors and monads.
     * It is intended to bcke used in conjunction with another macro that conditions the inputs.
     *
     * This method processes the Term `t` to find inputs of the form `wrap[A]( input )` This form is
     * typically constructed by another macro that pretends to be able to get a value of type `A`
     * from a value convertible to `F[A]`. This `wrap(input)` form has two main purposes. First, it
     * identifies the inputs that should be transformed. Second, it allows the input trees to be
     * wrapped for later conversion into the appropriate `F[A]` type by `convert`. This wrapping is
     * necessary because applying the first macro must preserve the original type, but it is useful
     * to delay conversion until the outer, second macro is called. The `wrap` method accomplishes
     * this by allowing the original `Term` and `Type` to be hidden behind the raw `A` type. This
     * method will remove the call to `wrap` so that it is not actually called at runtime.
     *
     * Each `input` in each expression of the form `wrap[A]( input )` is transformed by `convert`.
     * This transformation converts the input Term to a Term of type `F[A]`. The original wrapped
     * expression `wrap(input)` is replaced by a reference to a new local `val x: A`, where `x` is a
     * fresh name. These converted inputs are passed to `builder` as well as the list of these
     * synthetic `ValDef`s. The `TupleBuilder` instance constructs a tuple (Tree) from the inputs
     * and defines the right hand side of the vals that unpacks the tuple containing the results of
     * the inputs.
     *
     * The constructed tuple of inputs and the code that unpacks the results of the inputs are then
     * passed to the `i`, which is an implementation of `Instance` that is statically accessible. An
     * Instance defines a applicative functor associated with a specific type constructor and, if it
     * implements MonadInstance as well, a monad. Typically, it will be either a top-level module or
     * a stable member of a top-level module (such as a val or a nested module). The `with
     * Singleton` part of the type verifies some cases at macro compilation time, while the full
     * check for static accessibility is done at macro expansion time. Note: Ideally, the types
     * would verify that `i: MonadInstance` when `t.isRight`. With the various dependent types
     * involved, this is not worth it.
     *
     * The `eitherTree` argument is the argument of the macro that will be transformed as described
     * above. If the macro that calls this method is for a multi-input map (app followed by map),
     * `in` should be the argument wrapped in Left. If this is for multi-input flatMap (app followed
     * by flatMap), this should be the argument wrapped in Right.
     */
    def contImpl[A: Type, F[_], Effect[_]: Type](
        eitherTree: Either[Expr[A], Expr[F[A]]],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExprOpt: Option[Expr[BuildWideCacheConfiguration]],
        inner: conv.TermTransform[Effect]
    )(using
        iftpe: Type[F],
        eatpe: Type[Effect[A]],
    ): Expr[F[Effect[A]]] =
      import conv.*
      import qctx.reflect.*
      given qctx.type = qctx

      val fTypeCon = TypeRepr.of[F]
      val faTpe = fTypeCon.appliedTo(TypeRepr.of[Effect[A]])
      val (expr, treeType) = eitherTree match
        case Left(l)  => (l, TypeRepr.of[Effect[A]])
        case Right(r) => (r, faTpe)

      val inputBuf = ListBuffer[Input]()
      val outputBuf = ListBuffer[Output]()

      def unitExpr: Expr[Unit] = '{ () }

      // no inputs, so construct F[A] via Instance.pure or pure+flatten
      def pure(body: Term): Expr[F[Effect[A]]] =
        val tags = CacheLevelTag.all.toList
        def pure0[A1: Type](body: Expr[A1]): Expr[F[A1]] =
          '{
            $applicativeExpr.pure[A1] { () => $body }
          }
        eitherTree match
          case Left(_) => pure0[Effect[A]](inner(body).asExprOf[Effect[A]])
          case Right(_) =>
            flatten(pure0[F[Effect[A]]](inner(body).asExprOf[F[Effect[A]]]))

      // m should have type F[F[A]]
      // the returned Tree will have type F[A]
      def flatten(m: Expr[F[F[Effect[A]]]]): Expr[F[Effect[A]]] =
        '{
          {
            val i1 = $applicativeExpr.asInstanceOf[Monad[F]]
            i1.flatten[Effect[A]]($m.asInstanceOf[F[F[Effect[A]]]])
          }
        }

      def genMap(body: Term, input: Input): Expr[F[Effect[A]]] =
        def genMap0[A1: Type](body: Expr[A1]): Expr[F[A1]] =
          input.tpe.asType match
            case '[a] =>
              val tpe =
                MethodType(List(input.name))(_ => List(TypeRepr.of[a]), _ => TypeRepr.of[A1])
              val lambda = Lambda(
                owner = Symbol.spliceOwner,
                tpe = tpe,
                rhsFn = (sym, params) => {
                  val param = params.head.asInstanceOf[Term]
                  // Called when transforming the tree to add an input.
                  //  For `qual` of type F[A], and a `selection` qual.value,
                  //  the call is addType(Type A, Tree qual)
                  // The result is a Tree representing a reference to
                  //  the bound value of the input.
                  val substitute = [x] =>
                    (name: String, tpe: Type[x], qual: Term, replace: Term) =>
                      given t: Type[x] = tpe
                      convert[x](name, qual).transform { _ => typed[x](Ref(param.symbol)) }
                  val modifiedBody =
                    transformWrappers(body.asTerm.changeOwner(sym), substitute, sym).asExprOf[A1]
                  cacheConfigExprOpt match
                    case Some(cacheConfigExpr) =>
                      val modifiedCacheConfigExpr =
                        transformWrappers(cacheConfigExpr.asTerm.changeOwner(sym), substitute, sym)
                          .asExprOf[BuildWideCacheConfiguration]
                      val tags = CacheLevelTag.all.toList
                      callActionCache(outputBuf.toList, modifiedCacheConfigExpr, tags)(
                        body = modifiedBody,
                        input = unitExpr,
                      ).asTerm
                        .changeOwner(sym)
                    case None => modifiedBody.asTerm
                }
              ).asExprOf[a => A1]
              val expr = input.term.asExprOf[F[a]]
              typed[F[A1]](
                '{
                  $applicativeExpr.map[a, A1]($expr.asInstanceOf[F[a]])($lambda)
                }.asTerm
              ).asExprOf[F[A1]]
        eitherTree match
          case Left(_) =>
            genMap0[Effect[A]](inner(body).asExprOf[Effect[A]])
          case Right(_) =>
            flatten(genMap0[F[Effect[A]]](inner(body).asExprOf[F[Effect[A]]]))

      def genMapN(body: Term, inputs: List[Input]): Expr[F[Effect[A]]] =
        def genMapN0[A1: Type](body: Expr[A1]): Expr[F[A1]] =
          val br = makeTuple(inputs)
          val lambdaTpe =
            MethodType(List("$p0"))(_ => List(br.inputTupleTypeRepr), _ => TypeRepr.of[A1])
          br.inputTupleTypeRepr.asType match
            case '[inputTypeTpe] =>
              val lambda = Lambda(
                owner = Symbol.spliceOwner,
                tpe = lambdaTpe,
                rhsFn = (sym, params) => {
                  val p0 = params.head.asInstanceOf[Term]
                  // Called when transforming the tree to add an input.
                  //  For `qual` of type F[A], and a `selection` qual.value,
                  //  the call is addType(Type A, Tree qual)
                  // The result is a Tree representing a reference to
                  //  the bound value of the input.
                  val substitute = [x] =>
                    (name: String, tpe: Type[x], qual: Term, oldTree: Term) =>
                      given Type[x] = tpe
                      convert[x](name, qual) transform { (replacement: Term) =>
                        val idx = inputs.indexWhere(input => input.qual == qual)
                        applyTuple(p0, br.inputTupleTypeRepr, idx)
                    }
                  val modifiedBody =
                    transformWrappers(body.asTerm.changeOwner(sym), substitute, sym).asExprOf[A1]
                  cacheConfigExprOpt match
                    case Some(cacheConfigExpr) =>
                      val modifiedCacheConfigExpr =
                        transformWrappers(cacheConfigExpr.asTerm.changeOwner(sym), substitute, sym)
                          .asExprOf[BuildWideCacheConfiguration]
                      if inputs.exists(_.isCacheInput) then
                        val tags = inputs
                          .filter(_.isCacheInput)
                          .map(_.tags.toSet)
                          .reduce(_ & _)
                          .toList
                        require(
                          tags.nonEmpty,
                          s"""cacheLevelTag union must be non-empty: ${inputs.mkString("\n")}"""
                        )
                        br.cacheInputTupleTypeRepr.asType match
                          case '[cacheInputTpe] =>
                            callActionCache(outputBuf.toList, modifiedCacheConfigExpr, tags)(
                              body = modifiedBody,
                              input = br.cacheInputExpr(p0).asExprOf[cacheInputTpe],
                            ).asTerm.changeOwner(sym)
                      else
                        val tags = CacheLevelTag.all.toList
                        callActionCache(outputBuf.toList, cacheConfigExpr, tags)(
                          body = modifiedBody,
                          input = unitExpr,
                        ).asTerm.changeOwner(sym)
                    case None =>
                      modifiedBody.asTerm
                }
              )
              val tupleMapRepr = TypeRepr
                .of[Tuple.Map]
                .appliedTo(List(br.inputTupleTypeRepr, TypeRepr.of[F]))
              tupleMapRepr.asType match
                case '[tupleMap] =>
                  '{
                    given Applicative[F] = $applicativeExpr
                    import TupleMapExtension.*
                    ${ br.tupleExpr.asInstanceOf[Expr[Tuple.Map[inputTypeTpe & Tuple, F]]] }
                      .mapN(${ lambda.asExprOf[inputTypeTpe & Tuple => A1] })
                  }
        eitherTree match
          case Left(_) =>
            genMapN0[Effect[A]](inner(body).asExprOf[Effect[A]])
          case Right(_) =>
            flatten(genMapN0[F[Effect[A]]](inner(body).asExprOf[F[Effect[A]]]))

      // call `ActionCache.cache`
      def callActionCache[A1: Type, A2: Type](
          outputs: List[Output],
          cacheConfigExpr: Expr[BuildWideCacheConfiguration],
          tags: List[CacheLevelTag],
      )(body: Expr[A1], input: Expr[A2]): Expr[A1] =
        val codeContentHash = Expr[Long](body.show.##)
        val extraHash = Expr[Long](0L)
        val aJsonFormat = summonJsonFormat[A1]
        val aClassTag = summonClassTag[A1]
        val inputHashWriter =
          if TypeRepr.of[A2] =:= TypeRepr.of[Unit] then
            '{
              import BasicJsonProtocol.*
              summon[HashWriter[Unit]]
            }.asExprOf[HashWriter[A2]]
          else summonHashWriter[A2]
        val tagsExpr = '{ List(${ Varargs(tags.map(Expr[CacheLevelTag](_))) }: _*) }
        val block = letOutput(outputs, cacheConfigExpr)(body)
        '{
          given HashWriter[A2] = $inputHashWriter
          given JsonFormat[A1] = $aJsonFormat
          given ClassTag[A1] = $aClassTag
          ActionCache
            .cache(
              $input,
              codeContentHash = Digest.dummy($codeContentHash),
              extraHash = Digest.dummy($extraHash),
              tags = $tagsExpr
            )({ _ =>
              $block
            })($cacheConfigExpr)
        }

      def toVirtualFileExpr(
          cacheConfigExpr: Expr[BuildWideCacheConfiguration]
      )(out: Output): Expr[VirtualFile] =
        if out.isFile then out.toRef.asExprOf[VirtualFile]
        else
          '{
            ActionCache.packageDirectory(
              dir = ${ out.toRef.asExprOf[VirtualFileRef] },
              conv = $cacheConfigExpr.fileConverter,
            )
          }

      // This will generate following code for Def.declareOutput(...):
      //   var $o1: VirtualFile = null
      //   ActionCache.ActionResult({
      //     body...
      //     $o1 = out // Def.declareOutput(out)
      //     result
      //   }, List($o1))
      def letOutput[A1: Type](
          outputs: List[Output],
          cacheConfigExpr: Expr[BuildWideCacheConfiguration],
      )(body: Expr[A1]): Expr[ActionCache.InternalActionResult[A1]] =
        Block(
          outputs.map(_.toVarDef),
          '{
            ActionCache.InternalActionResult(
              value = $body,
              outputs = List(${
                Varargs[VirtualFile](outputs.map(toVirtualFileExpr(cacheConfigExpr)))
              }: _*),
            )
          }.asTerm
        ).asExprOf[ActionCache.InternalActionResult[A1]]

      val WrapOutputName = "wrapOutput_\u2603\u2603"
      val WrapOutputDirectoryName = "wrapOutputDirectory_\u2603\u2603"
      // Called when transforming the tree to add an input.
      //  For `qual` of type F[A], and a `selection` qual.value.
      val record = [a] =>
        (name: String, tpe: Type[a], qual: Term, oldTree: Term) =>
          given t: Type[a] = tpe
          convert[a](name, qual) transform { (replacement: Term) =>
            name match
              case WrapOutputName | WrapOutputDirectoryName =>
                val output = Output(
                  tpe = TypeRepr.of[a],
                  term = qual,
                  name = freshName("o"),
                  parent = Symbol.spliceOwner,
                  outputType = name match
                    case WrapOutputName          => OutputType.File
                    case WrapOutputDirectoryName => OutputType.Directory,
                )
                outputBuf += output
                if cacheConfigExprOpt.isDefined then output.toAssign
                else oldTree
              case _ =>
                // todo cache opt-out attribute
                inputBuf += Input(TypeRepr.of[a], qual, replacement, freshName("q"))
                oldTree
        }
      val exprWithConfig =
        cacheConfigExprOpt.map(config => '{ $config; $expr }).getOrElse(expr)
      val body = transformWrappers(exprWithConfig.asTerm, record, Symbol.spliceOwner)
      inputBuf.toList match
        case Nil      => pure(body)
        case x :: Nil => genMap(body, x)
        case xs       => genMapN(body, xs)
end Cont
