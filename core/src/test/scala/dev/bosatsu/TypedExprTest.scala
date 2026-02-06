package dev.bosatsu

import cats.data.{NonEmptyList, State, Writer}
import cats.implicits._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop.forAll
import scala.collection.immutable.SortedSet

import Arbitrary.arbitrary
import Identifier.Bindable
import TestUtils.checkLast
import rankn.{Type, NTypeGen}

class TypedExprTest extends munit.ScalaCheckSuite {
  override def scalaCheckTestParameters =
    // PropertyCheckConfiguration(minSuccessful = 5000)
    super.scalaCheckTestParameters.withMinSuccessfulTests(500)

  def allVars[A](te: TypedExpr[A]): Set[Bindable] = {
    type W[B] = Writer[Set[Bindable], B]

    te.traverseUp[W] {
      case v @ TypedExpr.Local(ident, _, _) => Writer(Set(ident), v)
      case notVar                           => Writer(Set.empty, notVar)
    }.run
      ._1
  }

  /** Assert two bits of code normalize to the same thing
    */
  def normSame(s1: String, s2: String) =
    checkLast(s1) { t1 =>
      checkLast(s2) { t2 =>
        assertEquals(t1.void, t2.void)
      }
    }

  test("freeVarsSet is a subset of allVars") {
    def law[A](te: TypedExpr[A]) = {
      val frees = TypedExpr.freeVarsSet(te :: Nil).toSet
      val av = allVars(te)
      val missing = frees -- av
      assert(
        missing.isEmpty,
        s"expression:\n\n${te.repr}\n\nallVars: $av\n\nfrees: $frees"
      )
    }

    forAll(genTypedExpr)(law)

    checkLast("""
enum AB: A, B(x)
x = match B(100):
  case A: 10
  case B(b): b
""")(law)
  }

  test("freeVars on top level TypedExpr is Nil") {
    // all of the names are local to a TypedExpr, nothing can be free
    // id and x are fully resolved to top level
    checkLast("""#
x = 1
def id(x): x
y = id(x)
""")(te => assertEquals(TypedExpr.freeVars(te :: Nil), Nil))

    checkLast("""#
x = 1
""")(te => assertEquals(TypedExpr.freeVars(te :: Nil), Nil))

    checkLast("""#
struct Tup2(a, b)

x = Tup2(1, 2)
""")(te => assertEquals(TypedExpr.freeVars(te :: Nil), Nil))

    checkLast("""#
struct Tup2(a, b)

x = 23
x = Tup2(1, 2)
y = match x:
  case Tup2(a, _): a
""")(te => assertEquals(TypedExpr.freeVars(te :: Nil), Nil))
  }

  test("we can inline struct/destruct") {
    checkLast("""#
struct Tup2(a, b)

x = 23
x = Tup2(1, 2)
y = match x:
  case Tup2(a, _): a
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(1))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }

    normSame(
      """#
struct Tup2(a, b)

x = 23
x = Tup2(1, 2)
y = match x:
  case Tup2(a, _): a
""",
      """#
y = 1
"""
    )

    checkLast("""#
struct Tup2(a, b)

inner = (
  z = 1
  fn = Tup2
  x = fn(z, 2)
  match x:
    case Tup2(a, _): a
)
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(1))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }

    checkLast("""#
struct Tup2(a, b)

inner = (
  x = Tup2(1, 2)
  match x:
    case Tup2(a, _): a
)

y = inner
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(1))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }

    checkLast("""#
struct Tup2(a, b)

x = 23
cons = Tup2
x = cons(1, 2)
y = match x:
  case Tup2(a, _): a
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(1))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }

    checkLast("""#
struct Tup2(a, b)

x = 23
cons = Tup2
x = cons(1, 2)
y = match x:
  case Tup2(_, _) as t:
    Tup2(a, _) = t
    a
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(1))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }

    checkLast("""#
struct Tup2(a, b)

x = 23
x = Tup2(Tup2(1, 3), 2)
y = match x:
  case Tup2(Tup2(a, _), _): a
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(1))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }

    checkLast("""#
struct Tup2(a, b)

x = 23
x = Tup2(0, Tup2(1, 2))
y = match x:
  case Tup2(_, Tup2(a, _)): a
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(1))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }

    checkLast("""#
enum E: Left(l), Right(r)

x = 23
x = Left(1)
y = match x:
  case Left(l): l
  case Right(r): r
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(1))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }

    checkLast("""#
enum E: Left(l), Right(r)

inner = (
  x = Left(1)
  z = 1
  match x:
    case Left(_): z
    case Right(r):
      match z:
        case 1: 1
        case _: r
)
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(1))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }

    checkLast("""#
x = 23
y = match x:
  case 42: 0
  case 23 as y: y
  case _: -1
""") {
      case TypedExpr.Literal(lit, _, _) => assertEquals(lit, Lit.fromInt(23))
      case notLit => fail(s"expected Literal got: ${notLit.repr}")
    }
  }

  test("we can lift a match above a lambda") {
    normSame(
      """#
struct Tup2(a, b)

y = Tup2(1, 2)

def inner_match(x):
  match y:
    case Tup2(a, _): Tup2(a, x)
""",
      """#
struct Tup2(a, b)
inner_match = x -> Tup2(1, x)
"""
    )

    normSame(
      """#
struct Tup2(a, b)
enum Eith: L(left), R(right)

def run(y):
  def inner_match(x):
    match y:
      case L(a): Tup2(a, x)
      case R(b): Tup2(x, b)

  inner_match
""",
      """#
struct Tup2(a, b)
enum Eith: L(left), R(right)

def run(y):
  match y:
    case L(a): x -> Tup2(a, x)
    case R(b): x -> Tup2(x, b)
"""
    )
  }

  test("we can push lets into match") {
    normSame(
      """#
struct Tup2(a, b)
enum Eith: L(left), R(right)

def run(y, x):
  z = y
  match x:
    case L(_): z
    case R(r): r
""",
      """#
struct Tup2(a, b)
enum Eith: L(left), R(right)

def run(y, x):
  match x:
    case L(_): y
    case R(r): r
"""
    )
  }

  test("we can evaluate constant matches") {
    normSame(
      """#
x = match 1:
  case (1 | 2) as x: x
  case _: -1
""",
      """#
x = 1
"""
    )

    normSame(
      """#
x = match 1:
  case _: -1
""",
      """#
x = -1
"""
    )

    normSame(
      """#
y = 21

def foo(_):
  match y:
    case 42: 0
    case x: x
""",
      """#
foo = _ -> 21
"""
    )

    /*
     * This does not yet work
    normSame("""#
struct Tup2(a, b)

def foo(_):
  y = 1
  z = Tup2(y, y)
  y = 0
  match z:
    case Tup2(0, 0): y
    case Tup2(1, 1): 1
    case _: 2
""", """#
foo = _ -> 1
""")
     */
  }

  val intTpe = Type.IntType

  def lit(l: Lit): TypedExpr[Unit] =
    TypedExpr.Literal(l, Type.getTypeOf(l), ())

  def int(i: Int): TypedExpr[Unit] =
    lit(Lit.fromInt(i))

  def varTE(n: String, tpe: Type): TypedExpr[Unit] =
    TypedExpr.Local(Identifier.Name(n), tpe, ())

  val PredefAdd: TypedExpr[Unit] =
    TypedExpr.Global(
      PackageName.PredefName,
      Identifier.Name("add"),
      Type.Fun(NonEmptyList.of(Type.IntType, Type.IntType), Type.IntType),
      ()
    )

  val PredefTimes: TypedExpr[Unit] =
    TypedExpr.Global(
      PackageName.PredefName,
      Identifier.Name("times"),
      Type.Fun(NonEmptyList.of(Type.IntType, Type.IntType), Type.IntType),
      ()
    )

  def let(
      n: String,
      ex1: TypedExpr[Unit],
      ex2: TypedExpr[Unit]
  ): TypedExpr[Unit] =
    TypedExpr.Let(Identifier.Name(n), ex1, ex2, RecursionKind.NonRecursive, ())

  def letrec(
      n: String,
      ex1: TypedExpr[Unit],
      ex2: TypedExpr[Unit]
  ): TypedExpr[Unit] =
    TypedExpr.Let(Identifier.Name(n), ex1, ex2, RecursionKind.Recursive, ())

  def app(
      fn: TypedExpr[Unit],
      arg: TypedExpr[Unit],
      tpe: Type
  ): TypedExpr[Unit] =
    TypedExpr.App(fn, NonEmptyList.one(arg), tpe, ())

  def lam(n: String, nt: Type, res: TypedExpr[Unit]): TypedExpr[Unit] =
    TypedExpr.AnnotatedLambda(
      NonEmptyList.one((Identifier.Name(n), nt)),
      res,
      ()
    )

  test("test let substitution") {
    {
      // substitution in let
      val let1 = let("y", varTE("x", intTpe), varTE("y", intTpe))
      assertEquals(TypedExpr.substitute(Identifier.Name("x"), int(2), let1), Some(let("y", int(2), varTE("y", intTpe))))
    }

    {
      // substitution in let with a masking
      val let1 = let("y", varTE("x", intTpe), varTE("y", intTpe))
      assertEquals(TypedExpr
          .substitute(Identifier.Name("x"), varTE("y", intTpe), let1)
          .map(_.reprString), Some(
            "(let y0 (var y Bosatsu/Predef::Int) (var y0 Bosatsu/Predef::Int))"
          ))
    }

    {
      // substitution in let with a shadowing in result
      val let1 = let("y", varTE("y", intTpe), varTE("y", intTpe))
      assertEquals(TypedExpr.substitute(Identifier.Name("y"), int(42), let1), Some(let("y", int(42), varTE("y", intTpe))))
    }

    {
      // substitution in letrec with a shadowing in bind and result
      val let1 = letrec("y", varTE("y", intTpe), varTE("y", intTpe))
      assertEquals(TypedExpr.substitute(Identifier.Name("y"), int(42), let1), Some(let1))
    }
  }

  lazy val genNonFree: Gen[TypedExpr[Unit]] =
    genTypedExpr.flatMap { te =>
      if (TypedExpr.freeVars(te :: Nil).isEmpty) Gen.const(te)
      else genNonFree
    }

  test("after substitution, a variable is no longer free") {
    forAll(genTypedExpr, genNonFree) { (te0, te1) =>
      TypedExpr.freeVars(te0 :: Nil) match {
        case Nil    => ()
        case b :: _ =>
          TypedExpr.substitute(b, te1, te0) match {
            case None =>
              // te1 has no free variables, this shouldn't fail
              assert(false)

            case Some(te0sub) =>
              assert(!TypedExpr.freeVarsSet(te0sub :: Nil)(b))
          }
      }
    }
  }

  test("substituting a non-free variable is identity") {
    def genNotFree[A](te: TypedExpr[A]): Gen[Bindable] = {
      val frees = TypedExpr.freeVarsSet(te :: Nil).toSet
      lazy val nf: Gen[Bindable] =
        Generators.bindIdentGen.flatMap {
          case isfree if frees(isfree) => nf
          case notfree                 => Gen.const(notfree)
        }

      nf
    }

    val pair = for {
      te <- genTypedExpr
      nf <- genNotFree(te)
    } yield (nf, te)

    forAll(pair, genNonFree) { case ((b, te0), te1) =>
      TypedExpr.substitute(b, te1, te0) match {
        case None =>
          // te1 has no free variables, this shouldn't fail
          assert(false)

        case Some(te0sub) => assertEquals(te0sub, te0)
      }
    }
  }

  test("let x = y in x == y") {
    // inline lets of vars
    assertEquals(TypedExprNormalization.normalize(
        let("x", varTE("y", intTpe), varTE("x", intTpe))
      ), Some(varTE("y", intTpe)))
  }

  test("we can normalize addition") {
    val three = TypedExpr.App(
      PredefAdd,
      NonEmptyList.of(int(1), int(2)),
      Type.IntType,
      ()
    )
    assertEquals(TypedExprNormalization.normalize(three), Some(int(3)))
  }

  test("we can normalize multiplication") {
    val six = TypedExpr.App(
      PredefTimes,
      NonEmptyList.of(int(2), int(3)),
      Type.IntType,
      ()
    )
    assertEquals(TypedExprNormalization.normalize(six), Some(int(6)))

    val zeroR = TypedExpr.App(
      PredefTimes,
      NonEmptyList.of(varTE("x", intTpe), int(0)),
      Type.IntType,
      ()
    )
    assertEquals(TypedExprNormalization.normalize(zeroR), Some(int(0)))

    val zeroL = TypedExpr.App(
      PredefTimes,
      NonEmptyList.of(int(0), varTE("x", intTpe)),
      Type.IntType,
      ()
    )
    assertEquals(TypedExprNormalization.normalize(zeroL), Some(int(0)))
  }

  val normalLet =
    let(
      "x",
      varTE("y", intTpe),
      let(
        "y",
        app(varTE("z", intTpe), int(43), intTpe),
        app(
          app(varTE("x", intTpe), varTE("y", intTpe), intTpe),
          varTE("y", intTpe),
          intTpe
        )
      )
    )

  test("we can inline using a shadow: let x = y in let y = z(43) in x(y)(y)") {
    // we can inline a shadow by unshadowing y to be y1
    // x = y
    // y = z(43)
    // x(y, y)
    val normed = TypedExprNormalization.normalize(normalLet)
    assertEquals(normed.map(_.repr.render(80)), Some("""(let
    y0
    (ap
        (var z Bosatsu/Predef::Int)
        (lit 43 Bosatsu/Predef::Int)
        Bosatsu/Predef::Int)
    (ap
        (ap
            (var y Bosatsu/Predef::Int)
            (var y0 Bosatsu/Predef::Int)
            Bosatsu/Predef::Int)
        (var y0 Bosatsu/Predef::Int)
        Bosatsu/Predef::Int))"""))
  }

  test("if w doesn't have x free: (app (let x y z) w) == let x y (app z w)") {
    assertEquals(TypedExprNormalization
        .normalize(
          app(normalLet, varTE("w", intTpe), intTpe)
        )
        .map(_.reprString), Some(
          let(
            "y0",
            app(varTE("z", intTpe), int(43), intTpe),
            app(
              app(
                app(varTE("y", intTpe), varTE("y0", intTpe), intTpe),
                varTE("y0", intTpe),
                intTpe
              ),
              varTE("w", intTpe),
              intTpe
            )
          ).reprString
        ))

  }

  test("x -> f(x) == f") {
    val f = varTE("f", Type.Fun(intTpe, intTpe))
    val left = lam("x", intTpe, app(f, varTE("x", intTpe), intTpe))

    assertEquals(TypedExprNormalization.normalize(left), Some(f))

    checkLast("""
struct Foo(a)
x = (y) -> Foo(y)
g = a -> x(a)
    """) { te1 =>
      checkLast("""
struct Foo(a)
x = Foo
      """) { te2 =>
        assertEquals(te1.void, te2.void, s"${te1.repr} != ${te2.repr}")
      }
    }

    checkLast("""
struct Foo(a)
x = Foo
    """) {
      case TypedExpr.Global(_, Identifier.Constructor("Foo"), _, _) =>
        assert(true)
      case notNorm =>
        fail(notNorm.repr.render(80))
    }
  }

  test("(x -> f(x, z))(y) == f(y, z)") {
    val int2int = Type.Fun(intTpe, intTpe)
    val f = varTE("f", Type.Fun(intTpe, int2int))
    val z = varTE("z", intTpe)
    val lamf =
      lam("x", intTpe, app(app(f, varTE("x", intTpe), int2int), z, intTpe))
    val y = varTE("y", intTpe)
    val left = app(lamf, y, intTpe)
    val right = app(app(f, y, int2int), z, intTpe)
    val res = TypedExprNormalization.normalize(left)

    assertEquals(res, Some(right), s"${res.map(_.repr)} != Some(${right.repr}")

    checkLast("""
res = (
  f = (_, y) -> y
  z = 1
  y -> (x -> f(x, z))(y)
)
""") { te1 =>
      checkLast("""
res = _ -> 1
      """) { te2 =>
        assertEquals(te1.void, te2.void, s"${te1.repr.render(80)} != ${te2.repr.render(80)}")
      }
    }

    checkLast("""
f = (_, y) -> y
z = 1
res = y -> (x -> f(x, z))(y)
""") { te1 =>
      checkLast("""
res = _ -> 1
      """) { te2 =>
        assertEquals(te1.void, te2.void, s"${te1.repr} != ${te2.repr}")
      }
    }
  }

  test("let de-nesting") {
    checkLast("""
struct Tup(a, b)
def f(x): x
res = (
   x = (
    y = f(1)
    Tup(y, y)
   ) 
   Tup(x, x)
)
""") { te1 =>
      checkLast("""
struct Tup(a, b)
def f(x): x
res = (
   y = f(1)
   x = Tup(y, y)
   Tup(x, x)
)
      """) { te2 =>
        assertEquals(te1.void, te2.void, s"${te1.repr} != ${te2.repr}")
      }
    }
  }

  test("lift let above lambda") {
    checkLast("""
enum FooBar: Foo, Bar

z = Foo
fn = (
  x -> (
    y = z
    match y:
      case Foo: x
      case Bar: y
  )
)
""") { te1 =>
      checkLast("""
enum FooBar: Foo, Bar

fn = (x: FooBar) -> x
    """) { te2 =>
        assertEquals(te1.void, te2.void, s"${te1.repr} != ${te2.repr}")
      }
    }
  }

  test("we destructure known structs") {
    checkLast("""
struct Data(a, b, c)
enum FooBar: Foo, Bar

x = (
  Data(_, _, c) = Data(Foo, Bar, Foo)
  c
)
""") { te1 =>
      checkLast("""
enum FooBar: Foo, Bar

x = Foo
    """) { te2 =>
        assertEquals(te1.void, te2.void, s"${te1.repr} != ${te2.repr}")
      }
    }
  }

  def gen[A](g: Gen[A]): Gen[TypedExpr[A]] =
    Generators.genTypedExpr(g, 3, NTypeGen.genDepth03)

  val genTypedExpr: Gen[TypedExpr[Unit]] =
    gen(Gen.const(()))

  val genTypedExprInt: Gen[TypedExpr[Int]] =
    gen(Gen.choose(Int.MinValue, Int.MaxValue))

  val genTypedExprChar: Gen[TypedExpr[Char]] =
    gen(Gen.choose('a', 'z'))

  test("TypedExpr.substituteTypeVar of identity is identity") {
    forAll(genTypedExpr, Gen.listOf(NTypeGen.genBound)) { (te, bounds) =>
      val identMap: Map[Type.Var, Type] = bounds.map { b =>
        (b, Type.TyVar(b))
      }.toMap
      assertEquals(TypedExpr.substituteTypeVar(te, identMap), te)
    }
  }

  test("TypedExpr.substituteTypeVar is idempotent") {
    forAll(genTypedExpr, Gen.listOf(NTypeGen.genBound)) { (te, bounds) =>
      val tpes = te.allTypes
      val avoid = tpes.toSet | bounds.map(Type.TyVar(_)).toSet
      val replacements = Type.allBinders.iterator.filterNot { t =>
        avoid(Type.TyVar(t))
      }
      val identMap: Map[Type.Var, Type] =
        bounds.iterator
          .zip(replacements)
          .map { case (b, v) => (b, Type.TyVar(v)) }
          .toMap
      val te1 = TypedExpr.substituteTypeVar(te, identMap)
      val te2 = TypedExpr.substituteTypeVar(te1, identMap)
      assertEquals(te2, te1)
    }
  }

  test("TypedExpr.substituteTypeVar is not an identity function") {
    // if we replace all the current types with some bound types, things won't be the same
    forAll(genTypedExpr) { te =>
      val tpes: Set[Type.Var] = te.allTypes.iterator.collect {
        case Type.TyVar(b) => b
      }.toSet

      implicit def setM[A: Ordering]: cats.Monoid[SortedSet[A]] =
        new cats.Monoid[SortedSet[A]] {
          def empty = SortedSet.empty
          def combine(a: SortedSet[A], b: SortedSet[A]) = a ++ b
        }

      // All the vars that are used in bounds
      val bounds: Set[Type.Var] = te
        .traverseType { (t: Type) =>
          t match {
            case q: Type.Quantified =>
              Writer(SortedSet[Type.Var](q.vars.toList.map(_._1)*), t)
            case _ => Writer(SortedSet[Type.Var](), t)
          }
        }
        .run
        ._1
        .toSet[Type.Var]

      val replacements = Type.allBinders.iterator.filterNot(tpes)
      val identMap: Map[Type.Var, Type] =
        tpes
          .filterNot(bounds)
          .iterator
          .zip(replacements)
          .map { case (b, v) => (b, Type.TyVar(v)) }
          .toMap

      if (identMap.nonEmpty) {
        val te1 = TypedExpr.substituteTypeVar(te, identMap)
        assert(te1 =!= te, s"mapping: $identMap, $bounds")
      }
    }
  }

  test("TypedExpr.allTypes contains the type") {
    forAll(genTypedExpr) { te =>
      assert(te.allTypes.contains(te.getType))
    }
  }

  def count[A](
      te: TypedExpr[A]
  )(fn: PartialFunction[TypedExpr[A], Boolean]): Int = {
    type W[B] = Writer[Int, B]
    val (count, _) =
      te.traverseUp[W] { inner =>
        val c = if (fn.isDefinedAt(inner) && fn(inner)) 1 else 0
        Writer(c, inner)
      }.run

    count
  }

  def countMatch[A](te: TypedExpr[A]) = count(te) {
    case TypedExpr.Match(_, _, _) => true
  }
  def countLet[A](te: TypedExpr[A]) = count(te) {
    case TypedExpr.Let(_, _, _, _, _) => true
  }

  test("test match removed from some examples") {
    checkLast("""
x = _ -> 1
""")(te => assertEquals(countMatch(te), 0))

    checkLast("""
x = 10
y = match x:
  case z: z
""")(te => assertEquals(countMatch(te), 0))

    checkLast("""
x = 10
y = match x:
  case _: 20
""")(te => assertEquals(countMatch(te), 0))
  }

  test("test let removed from some examples") {
    // this should turn into `y = 20` as the last expression
    checkLast("""
x = 10
y = match x:
  case _: 20
""")(te => assertEquals(countLet(te), 0))

    checkLast("""
foo = (
  x = 1
  _ = x
  42
)
""")(te => assertEquals(countLet(te), 0))
  }

  test("test normalization let shadowing bug in lambda") {
    checkLast("""
enum L[a]: E, NE(head: a, tail: L[a])

x = (
  def go(y, z):
    def loop(z):
      recur z:
        case E: y
        case NE(_, t): loop(t)

    loop(z)

  fn1 = z -> go(1, z)
  fn1(NE(1, NE(2, E)))
)
""") { te1 =>
      checkLast("""
enum L[a]: E, NE(head: a, tail: L[a])

x = (
  def go(y, z):
    def loop(z1):
      recur z1:
        case E: y
        case NE(_, t): loop(t)

    loop(z)

  fn1 = z -> go(1, z)
  fn1(NE(1, NE(2, E)))
)
    """) { te2 =>
        assertEquals(te1.void, te2.void, s"\n${te1.reprString}\n\n!=\n\n${te2.reprString}")
      }
    }
  }

  test("toArgsBody always terminates") {
    forAll(Gen.choose(0, 10), genTypedExpr) { (arity, te) =>
      // this is a pretty weak test.
      assert(TypedExpr.toArgsBody(arity, te) ne null)
    }
  }

  test("TypedExpr.fold matches traverse") {
    def law[A, B](init: A, te: TypedExpr[B])(fn: (A, B) => A) = {
      type StateA[X] = State[A, X]
      val viaFold = te.foldLeft(init)(fn)
      val viaTraverse = te.traverse_[StateA, Unit] { b =>
        for {
          i <- State.get[A]
          i1 = fn(i, b)
          _ <- State.set(i1)
        } yield ()
      }

      assertEquals(viaFold, viaTraverse.runS(init).value, s"${te.repr}")
    }

    def lawR[A, B](te: TypedExpr[B], a: A)(fn: (B, A) => A) = {
      type StateA[X] = State[A, X]
      val viaFold = te
        .foldRight(cats.Eval.now(a))((b, r) => r.map(j => fn(b, j)))
        .value
      val viaTraverse: State[A, Unit] = te.traverse_[StateA, Unit] { b =>
        for {
          i <- State.get[A]
          i1 = fn(b, i)
          _ <- State.set(i1)
        } yield ()
      }

      assertEquals(viaFold, viaTraverse.runS(a).value, s"${te.repr}")
    }

    forAll(genTypedExprInt, Gen.choose(0, 1000)) { (te, init) =>
      // make a commutative int function
      law(init, te)((a, b) => (a + 1) * b)
      lawR(te, init)((a, b) => (a + 1) + b)
      law(init, te)((a, b) => a + b)
    }
  }

  test("foldMap from traverse matches foldMap") {
    import cats.data.Const
    import cats.Monoid

    def law[A, B: Monoid](te: TypedExpr[A])(fn: A => B) = {
      type ConstB[X] = Const[B, X]
      val viaFold = te.foldMap(fn)
      val viaTraverse: Const[B, Unit] = te
        .traverse[ConstB, Unit] { b =>
          Const[B, Unit](fn(b))
        }
        .void

      assertEquals(viaFold, viaTraverse.getConst, s"${te.repr}")
    }

    val propInt = forAll(genTypedExprChar, arbitrary[Char => Int])(law(_)(_))
    // non-commutative
    val propString =
      forAll(genTypedExprChar, arbitrary[Char => String])(law(_)(_))

    val lamconst: TypedExpr[String] =
      TypedExpr.AnnotatedLambda(
        NonEmptyList.one((Identifier.Name("x"), intTpe)),
        int(1).as("a"),
        "b"
      )

    assertEquals(lamconst.foldMap(identity), "ab")
    assertEquals(lamconst.traverse(a => Const[String, Unit](a)).getConst, "ab")
    org.scalacheck.Prop.all(propInt, propString)
  }

  test("TypedExpr.traverse.void matches traverse_") {
    import cats.data.Const
    forAll(genTypedExprInt, arbitrary[Int => String]) { (te, fn) =>
      assertEquals(te.traverse(i => Const[String, Unit](fn(i))).void, te.traverse_(i => Const[String, Unit](fn(i))))
    }
  }

  test("TypedExpr.foldRight matches foldRight for commutative funs") {
    forAll(genTypedExprInt, Gen.choose(0, 1000)) { (te, init) =>
      val right =
        te.foldRight(cats.Eval.now(init))((i, ej) => ej.map(_ + i)).value
      val left = te.foldLeft(init)(_ + _)
      assertEquals(right, left)
    }
  }

  test("TypedExpr.foldRight matches foldRight for non-commutative funs") {
    forAll(genTypedExprInt) { te =>
      val right = te
        .foldRight(cats.Eval.now("")) { (i, ej) =>
          ej.map(j => i.toString + j)
        }
        .value
      val left = te.foldLeft("")((i, j) => i + j.toString)
      assertEquals(right, left)
    }
  }

  test("TypedExpr.map matches traverse with Id") {
    forAll(genTypedExprInt, arbitrary[Int => Int]) { (te, fn) =>
      assertEquals(te.map(fn), te.traverse[cats.Id, Int](fn))
    }
  }

  test("freeTyVars is a superset of the frees in the outer type") {
    forAll(genTypedExpr) { te =>
      assert(
        Type
          .freeTyVars(te.getType :: Nil)
          .toSet
          .subsetOf(
            te.freeTyVars.toSet
          )
      )
    }
  }

  test("TypedExpr.liftQuantification makes all args Rho types") {

    forAll(
      Generators.smallNonEmptyList(genTypedExpr, 10),
      Gen.containerOf[Set, Type.Var.Bound](NTypeGen.genBound)
    ) { (tes, avoid) =>
      val (optQuant, args) = TypedExpr.liftQuantification(tes, avoid)
      args.toList.foreach { te =>
        te.getType match {
          case _: Type.Rho => ()
          case notRho => fail(s"expected: $te to have rho type, got: $notRho")
        }
      }
      val allRhos = tes.forall(_.getType.isInstanceOf[Type.Rho])
      assertEquals(allRhos, optQuant.isEmpty)
    }
  }

  // =========================================================================
  // Tests for mayHaveSideEffects / side-effect-aware normalization
  // =========================================================================

  // Helper: a package name for our test externals
  private val testPack = PackageName.parts("Test", "Pkg")

  // Helper: create a TypeEnv with an external value registered
  private def typeEnvWithExternal(
      pack: PackageName,
      name: String,
      tpe: rankn.Type
  ): rankn.TypeEnv[Nothing] =
    rankn.TypeEnv.empty.addExternalValue(pack, Identifier.Name(name), tpe)

  // Helper: create a Global reference to an external function
  private def extGlobal(
      pack: PackageName,
      name: String,
      tpe: rankn.Type
  ): TypedExpr[Unit] =
    TypedExpr.Global(pack, Identifier.Name(name), tpe, ())

  // Helper: normalize with a specific TypeEnv via normalizeAll
  private def normalizeWithEnv(
      te: TypedExpr[Unit],
      typeEnv: rankn.TypeEnv[Nothing]
  ): TypedExpr[Unit] = {
    val bindName = Identifier.Name("__result")
    val lets = List((bindName, RecursionKind.NonRecursive, te))
    val result = TypedExprNormalization.normalizeAll(testPack, lets, typeEnv)
    result.head._3
  }

  test("let dead-code elimination: pure expression is eliminated when unused") {
    // let x = 42 in 10  =>  10  (pure, x unused)
    val expr = let("x", int(42), int(10))
    val result = TypedExprNormalization.normalize(expr)
    assertEquals(result, Some(int(10)))
  }

  test("let dead-code elimination: external call preserved when unused") {
    // let x = write(state) in 10
    // write is external => don't eliminate, keep the let
    val writeTpe = Type.Fun(NonEmptyList.one(intTpe), intTpe)
    val envWithWrite = typeEnvWithExternal(testPack, "write", writeTpe)
    val writeRef = extGlobal(testPack, "write", writeTpe)
    val writeCall = TypedExpr.App(writeRef, NonEmptyList.one(int(1)), intTpe, ())
    // let x = write(1) in 10
    val expr = TypedExpr.Let(
      Identifier.Name("x"),
      writeCall,
      int(10),
      RecursionKind.NonRecursive,
      ()
    )
    val result = normalizeWithEnv(expr, envWithWrite)
    // The result should still contain a Let with the write call
    result match {
      case TypedExpr.Let(_, ex, in, _, _) =>
        // The let should be preserved because write is external
        assert(true)
      case other =>
        // It should NOT be just int(10); the let should be kept
        fail(
          s"expected Let to be preserved for external call, got: ${other.repr.render(80)}"
        )
    }
  }

  test("let dead-code elimination: non-external call is eliminated when unused") {
    // let x = f(1) in 10 where f is NOT external => eliminate
    // Using a local variable as function (locals are always pure in Bosatsu)
    val fTpe = Type.Fun(NonEmptyList.one(intTpe), intTpe)
    val fRef = varTE("f", fTpe)
    val fCall = app(fRef, int(1), intTpe)
    val expr = TypedExpr.Let(
      Identifier.Name("x"),
      fCall,
      int(10),
      RecursionKind.NonRecursive,
      ()
    )
    // With empty TypeEnv (no externals), f is not external => pure
    val result = TypedExprNormalization.normalize(expr)
    // The Let should be eliminated since x is unused and f is not external
    assertEquals(result, Some(int(10)))
  }

  test("match single-branch: pure arg is eliminated") {
    // match 42: case _: 10  =>  10
    val expr = TypedExpr.Match(
      int(42),
      NonEmptyList.one((Pattern.WildCard, int(10))),
      ()
    )
    val result = TypedExprNormalization.normalize(expr)
    assertEquals(result, Some(int(10)))
  }

  test("match single-branch: external call arg is preserved") {
    // match write(1): case _: 10
    // write is external => keep the match
    val writeTpe = Type.Fun(NonEmptyList.one(intTpe), intTpe)
    val envWithWrite = typeEnvWithExternal(testPack, "write", writeTpe)
    val writeRef = extGlobal(testPack, "write", writeTpe)
    val writeCall = TypedExpr.App(writeRef, NonEmptyList.one(int(1)), intTpe, ())
    val expr = TypedExpr.Match(
      writeCall,
      NonEmptyList.one((Pattern.WildCard, int(10))),
      ()
    )
    val result = normalizeWithEnv(expr, envWithWrite)
    // The result should NOT just be int(10); the match should be preserved
    result match {
      case TypedExpr.Literal(_, _, _) =>
        fail(
          "match with external call arg should not be eliminated to a literal"
        )
      case _ =>
        // Good - the match or let structure is preserved
        assert(true)
    }
  }

  test("lambda let-lifting: pure let is lifted above lambda") {
    // x -> (y = 42; f(y))  =>  y = 42; x -> f(y)
    // where f is not external (pure)
    // This tests that pure lets ARE still lifted
    normSame(
      """#
def fn(x):
  y = 42
  _ = x
  y
""",
      """#
fn = _ -> 42
"""
    )
  }

  test("lambda match-lifting: pure match is lifted above lambda") {
    // This verifies that pure match lifting still works
    normSame(
      """#
struct Tup2(a, b)

y = Tup2(1, 2)

def inner_match(x):
  match y:
    case Tup2(a, _): Tup2(a, x)
""",
      """#
struct Tup2(a, b)
inner_match = x -> Tup2(1, x)
"""
    )
  }

  test("mayHaveSideEffects: non-App expression returns false") {
    // A literal is not an App, so mayHaveSideEffects should be false
    // We test this indirectly: let x = 42 in 10 should be optimized away
    val expr = let("x", int(42), int(10))
    val result = TypedExprNormalization.normalize(expr)
    assertEquals(result, Some(int(10)))
  }

  test("mayHaveSideEffects: App with local function returns false") {
    // App with a local function reference is always pure
    // let x = f(1) in 10 where f is local => eliminate
    val fTpe = Type.Fun(NonEmptyList.one(intTpe), intTpe)
    val expr = let(
      "x",
      app(varTE("f", fTpe), int(1), intTpe),
      int(10)
    )
    val result = TypedExprNormalization.normalize(expr)
    assertEquals(result, Some(int(10)))
  }

  test("mayHaveSideEffects: App with Constructor returns false") {
    // Constructor applications are always pure
    // This is tested indirectly via normSame - Tup2(1, 2) should be inlinable
    normSame(
      """#
struct Tup2(a, b)
x = Tup2(1, 2)
y = match x:
  case Tup2(a, _): a
""",
      """#
y = 1
"""
    )
  }

  test("normalizeAll with external: unused external call preserved in let") {
    // Create a TypeEnv with an external "sideEffect" function
    val seTpe = Type.Fun(NonEmptyList.one(intTpe), intTpe)
    val env = typeEnvWithExternal(testPack, "side_effect", seTpe)
    val seRef = extGlobal(testPack, "side_effect", seTpe)
    val seCall = TypedExpr.App(seRef, NonEmptyList.one(int(1)), intTpe, ())

    // let unused = side_effect(1) in 99
    val bindName = Identifier.Name("unused_val")
    val body = int(99)
    val expr = TypedExpr.Let(bindName, seCall, body, RecursionKind.NonRecursive, ())

    val resultName = Identifier.Name("result")
    val lets =
      List((resultName, RecursionKind.NonRecursive, expr))
    val normalized = TypedExprNormalization.normalizeAll(testPack, lets, env)
    val resultExpr = normalized.head._3

    // The let should NOT be eliminated because side_effect is external
    resultExpr match {
      case TypedExpr.Literal(l, _, _) =>
        fail(
          s"external call let should not be eliminated, but got literal: $l"
        )
      case TypedExpr.Let(_, _, _, _, _) =>
        // Good - the let is preserved
        assert(true)
      case other =>
        // Any non-literal result that preserves structure is acceptable
        assert(
          true,
          s"got: ${other.repr.render(80)}"
        )
    }
  }

  test("normalizeAll without external: unused pure call is eliminated in let") {
    // With empty TypeEnv, a Global reference is not considered external
    // let unused = g(1) in 99 where g is not in typeEnv => pure => eliminate
    val gTpe = Type.Fun(NonEmptyList.one(intTpe), intTpe)
    val gRef = TypedExpr.Global(testPack, Identifier.Name("g"), gTpe, ())
    val gCall = TypedExpr.App(gRef, NonEmptyList.one(int(1)), intTpe, ())

    val bindName = Identifier.Name("unused_val")
    val body = int(99)
    val expr = TypedExpr.Let(bindName, gCall, body, RecursionKind.NonRecursive, ())

    // Use empty TypeEnv - g is NOT registered as external
    val resultName = Identifier.Name("result")
    val lets =
      List((resultName, RecursionKind.NonRecursive, expr))
    val normalized =
      TypedExprNormalization.normalizeAll(testPack, lets, rankn.TypeEnv.empty)
    val resultExpr = normalized.head._3

    // The let should be eliminated because g is not external
    resultExpr match {
      case TypedExpr.Literal(l, _, _) =>
        assertEquals(l, Lit.fromInt(99))
      case other =>
        fail(
          s"expected literal 99 after eliminating pure unused let, got: ${other.repr.render(80)}"
        )
    }
  }

  test("normalizeAll: curried external call is detected as having side effects") {
    // write(state)(value) - a curried external call
    // The functionHead should recurse through App to find the Global
    val writeTpe = Type.Fun(
      NonEmptyList.one(intTpe),
      Type.Fun(NonEmptyList.one(intTpe), intTpe)
    )
    val env = typeEnvWithExternal(testPack, "write", writeTpe)
    val writeRef = extGlobal(testPack, "write", writeTpe)
    // write(1) returns a function
    val partialApp = TypedExpr.App(
      writeRef,
      NonEmptyList.one(int(1)),
      Type.Fun(NonEmptyList.one(intTpe), intTpe),
      ()
    )
    // write(1)(2) - full application
    val fullApp = TypedExpr.App(partialApp, NonEmptyList.one(int(2)), intTpe, ())

    // let unused = write(1)(2) in 99
    val expr = TypedExpr.Let(
      Identifier.Name("unused_val"),
      fullApp,
      int(99),
      RecursionKind.NonRecursive,
      ()
    )

    val resultName = Identifier.Name("result")
    val lets =
      List((resultName, RecursionKind.NonRecursive, expr))
    val normalized = TypedExprNormalization.normalizeAll(testPack, lets, env)
    val resultExpr = normalized.head._3

    // The let should NOT be eliminated because write is external
    resultExpr match {
      case TypedExpr.Literal(l, _, _) =>
        fail(
          s"curried external call let should not be eliminated, got literal: $l"
        )
      case _ =>
        // Good - the let is preserved
        assert(true)
    }
  }
}
