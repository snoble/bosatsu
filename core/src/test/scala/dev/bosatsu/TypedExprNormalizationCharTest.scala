package dev.bosatsu

import cats.data.Ior

class TypedExprNormalizationCharTest extends munit.FunSuite {
  private val testPackage = TestUtils.testPackage

  private def normalizedLets(source: String) = {
    val stmts = Parser.unsafeParse(Statement.parser, source)
    val (fullTypeEnv, unoptimized) =
      Package.inferBodyUnopt(testPackage, Nil, stmts) match {
        case Ior.Right(res)      => res
        case Ior.Both(errs, _)   =>
          fail(s"inference failure:\n${errs.toList.mkString("\n")}")
        case Ior.Left(errs)      =>
          fail(s"inference failure:\n${errs.toList.mkString("\n")}")
      }

    TypedExprNormalization
      .normalizeProgram(testPackage, fullTypeEnv, unoptimized)
      .lets
  }

  private def renderNormalized(source: String): String =
    normalizedLets(source).map { case (name, recursion, te) =>
      s"${name.asString} [$recursion] = ${te.repr.render(100)}"
    }.mkString("\n")

  test("characterization: simple let chain normalization") {
    val source =
      """x = 1
        |y = x
        |z = y
        |""".stripMargin

    val expected =
      """x [NonRecursive] = (lit 1 Bosatsu/Predef::Int)
        |y [NonRecursive] = (lit 1 Bosatsu/Predef::Int)
        |z [NonRecursive] = (lit 1 Bosatsu/Predef::Int)
        |""".stripMargin.trim

    assertNoDiff(renderNormalized(source), expected)
  }

  test("characterization: beta reduction through function call") {
    val source =
      """def id(x): x
        |out = id(2)
        |""".stripMargin

    val expected =
      """id [NonRecursive] = (generic forall a: *. a -> a (lambda [x a] (var x a)))
        |out [NonRecursive] = (lit 2 Bosatsu/Predef::Int)
        |""".stripMargin.trim

    assertNoDiff(renderNormalized(source), expected)
  }

  test("characterization: simplifiable match branches") {
    val source =
      """enum E: A, B(i)
        |v = A
        |out = match v:
        |  case A: 1
        |  case B(i): i
        |""".stripMargin

    val expected =
      """v [NonRecursive] = (var Test::A forall a: *. Test::E[a])
        |out [NonRecursive] = (lit 1 Bosatsu/Predef::Int)
        |""".stripMargin.trim

    assertNoDiff(renderNormalized(source), expected)
  }

  test("characterization: recursive function is not inlined") {
    val source =
      """enum L[a]: E, NE(head: a, tail: L[a])
        |def count(z):
        |  def loop(lst):
        |    recur lst:
        |      case E: 0
        |      case NE(_, t): loop(t)
        |  loop(z)
        |out = count(NE(1, E))
        |""".stripMargin

    val expected =
      """count [NonRecursive] = (generic
        |    forall a: *. Test::L[a] -> Bosatsu/Predef::Int
        |    (lambda [z Test::L[a]] (loop [a (var z Test::L[a])] (match (var a Test::L[a])
        |                    [E, (lit 0 Bosatsu/Predef::Int)]
        |                    [NE(_, t: Test::L[a]), (recur (var t Test::L[a]) Bosatsu/Predef::Int)]))))
        |out [NonRecursive] = (let
        |    z
        |    (ap
        |        (var Test::NE forall a: *. (a, Test::L[a]) -> Test::L[a])
        |        (lit 1 Bosatsu/Predef::Int)
        |            (ann Test::L[Bosatsu/Predef::Int] (var Test::E forall a: *. Test::L[a]))
        |        Test::L[Bosatsu/Predef::Int])
        |    (loop [a (var z Test::L[Bosatsu/Predef::Int])] (match (var a Test::L[Bosatsu/Predef::Int])
        |                [E, (lit 0 Bosatsu/Predef::Int)]
        |                [NE(_, t: Test::L[Bosatsu/Predef::Int]), (recur (var t Test::L[Bosatsu/Predef::Int]) Bosatsu/Predef::Int)])))
        |""".stripMargin.trim

    assertNoDiff(renderNormalized(source), expected)
  }

  test("characterization: nested lambdas") {
    val source =
      """def keep_left(x):
        |  (y) ->
        |    match y:
        |      case 0: x
        |      case _: x
        |add2 = keep_left(2)
        |out = add2(3)
        |""".stripMargin

    val expected =
      """keep_left [NonRecursive] = (generic
        |    forall a: *. a -> Bosatsu/Predef::Int -> a
        |    (lambda [x a] (lambda [y Bosatsu/Predef::Int] (match (var y Bosatsu/Predef::Int)
        |                    [0, (var x a)]
        |                    [_, (var x a)]))))
        |add2 [NonRecursive] = (lambda [y Bosatsu/Predef::Int] (match (var y Bosatsu/Predef::Int)
        |            [0, (lit 2 Bosatsu/Predef::Int)]
        |            [_, (lit 2 Bosatsu/Predef::Int)]))
        |out [NonRecursive] = (lit 2 Bosatsu/Predef::Int)
        |""".stripMargin.trim

    assertNoDiff(renderNormalized(source), expected)
  }
}
