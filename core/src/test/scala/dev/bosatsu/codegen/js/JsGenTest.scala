package dev.bosatsu.codegen.js

import munit.ScalaCheckSuite
import org.scalacheck.{Gen, Prop}
import org.scalacheck.Prop.forAll
import dev.bosatsu.{Identifier, Lit, Matchless, PackageName}
import dev.bosatsu.Identifier.Name
import cats.data.NonEmptyList

class JsGenTest extends ScalaCheckSuite {

  import Matchless._

  // ==================
  // Helper Functions
  // ==================

  def renderAndCheck(expr: Matchless.Expr[Unit], check: String => Boolean): Boolean = {
    val result = JsGen.renderExpr(expr)
    check(result)
  }

  def assertRenders(expr: Matchless.Expr[Unit], expected: String)(implicit loc: munit.Location): Unit = {
    val result = JsGen.renderExpr(expr)
    assertEquals(result, expected)
  }

  def bindable(name: String): Identifier.Bindable = Name(name)

  // ==================
  // Literal Tests
  // ==================

  test("Literal integer renders correctly") {
    assertRenders(Literal(Lit.Integer(42)), "42")
    assertRenders(Literal(Lit.Integer(-5)), "(-5)")
    assertRenders(Literal(Lit.Integer(0)), "0")
  }

  test("Literal string renders as Bosatsu string") {
    // Strings are converted to Bosatsu's internal string representation
    val helloResult = JsGen.renderExpr(Literal(Lit.Str("hello")))
    assert(helloResult.contains("_js_to_bosatsu_string"), s"Expected Bosatsu string conversion, got: $helloResult")
    assert(helloResult.contains("\"hello\""), s"Expected hello in string, got: $helloResult")

    val emptyResult = JsGen.renderExpr(Literal(Lit.Str("")))
    assert(emptyResult.contains("_js_to_bosatsu_string"), s"Expected Bosatsu string conversion, got: $emptyResult")
  }

  test("Literal char renders as Bosatsu char (single-element string)") {
    // Chars are represented as [1, char, [0]] (single-element linked list)
    val aResult = JsGen.renderExpr(Literal(Lit.Chr("a")))
    assert(aResult.contains("[1,"), s"Expected array with cons tag, got: $aResult")
    assert(aResult.contains("\"a\""), s"Expected char 'a' in array, got: $aResult")
    assert(aResult.contains("[0]"), s"Expected nil terminator, got: $aResult")

    val newlineResult = JsGen.renderExpr(Literal(Lit.Chr("\n")))
    assert(newlineResult.contains("[1,"), s"Expected array with cons tag, got: $newlineResult")
  }

  // ==================
  // Variable Tests
  // ==================

  test("Local variable renders correctly") {
    assertRenders(Local(bindable("x")), "x")
    assertRenders(Local(bindable("myVar")), "myVar")
  }

  test("Reserved word escaping works") {
    // "class" is a reserved word
    val ident = JsGen.escape(bindable("class"))
    assertEquals(ident.name, "_class")
  }

  test("Operator escaping works") {
    val op = Identifier.Operator("+")
    val ident = JsGen.escape(op)
    assert(ident.name.startsWith("op_"))
  }

  // ==================
  // Enum/Struct Tests
  // ==================

  test("MakeEnum with zero arity renders as array") {
    assertRenders(MakeEnum(0, 0, List(0)), "[0]")
    assertRenders(MakeEnum(1, 0, List(0, 0)), "[1]")
  }

  test("MakeEnum with arity renders as function") {
    val result = JsGen.renderExpr(MakeEnum(1, 2, List(0, 2)))
    assert(result.contains("=>"), s"Expected arrow function, got: $result")
    assert(result.contains("[1,"), s"Expected array with variant tag, got: $result")
  }

  test("MakeStruct with zero arity renders as empty array") {
    assertRenders(MakeStruct(0), "[]")
  }

  test("MakeStruct with arity renders as function") {
    val result = JsGen.renderExpr(MakeStruct(2))
    assert(result.contains("=>"), s"Expected arrow function, got: $result")
  }

  // ==================
  // Nat Tests
  // ==================

  test("ZeroNat renders as 0") {
    assertRenders(ZeroNat, "0")
  }

  test("SuccNat renders as increment function") {
    val result = JsGen.renderExpr(SuccNat)
    assert(result.contains("=>"), s"Expected arrow function, got: $result")
    assert(result.contains("n + 1") || result.contains("n+1"), s"Expected increment, got: $result")
  }

  test("PrevNat renders as decrement") {
    val result = JsGen.renderExpr(PrevNat(Local(bindable("n"))))
    assert(result.contains("n - 1") || result.contains("n-1"), s"Expected decrement, got: $result")
  }

  // ==================
  // Application Tests
  // ==================

  test("Simple function application renders") {
    val app = App(Local(bindable("f")), NonEmptyList.of(Literal(Lit.Integer(1))))
    assertRenders(app, "f(1)")
  }

  test("Multiple argument application renders") {
    val app = App(
      Local(bindable("add")),
      NonEmptyList.of(Literal(Lit.Integer(1)), Literal(Lit.Integer(2)))
    )
    assertRenders(app, "add(1, 2)")
  }

  // ==================
  // Lambda Tests
  // ==================

  test("Simple lambda renders") {
    val lam = Lambda(
      Nil,
      None,
      NonEmptyList.of(bindable("x")),
      Local(bindable("x"))
    )
    val result = JsGen.renderExpr(lam)
    assert(result.contains("=>"), s"Expected arrow function, got: $result")
  }

  test("Multi-arg lambda renders") {
    val lam = Lambda(
      Nil,
      None,
      NonEmptyList.of(bindable("x"), bindable("y")),
      App(Local(bindable("add")), NonEmptyList.of(Local(bindable("x")), Local(bindable("y"))))
    )
    val result = JsGen.renderExpr(lam)
    assert(result.contains("=>"), s"Expected arrow function, got: $result")
    assert(result.contains("add(x, y)"), s"Expected application in body, got: $result")
  }

  // ==================
  // If Tests
  // ==================

  test("If expression renders as ternary") {
    val ifExpr = If(
      TrueConst,
      Literal(Lit.Integer(1)),
      Literal(Lit.Integer(2))
    )
    val result = JsGen.renderExpr(ifExpr)
    assert(result.contains("?") && result.contains(":"), s"Expected ternary, got: $result")
  }

  // ==================
  // Let Tests
  // ==================

  test("Let binding renders with IIFE") {
    val letExpr = Let(
      Right(bindable("x")),
      Literal(Lit.Integer(42)),
      Local(bindable("x"))
    )
    val result = JsGen.renderExpr(letExpr)
    assert(result.contains("const x"), s"Expected const binding, got: $result")
    assert(result.contains("return"), s"Expected return, got: $result")
  }

  // ==================
  // GetElement Tests
  // ==================

  test("GetEnumElement renders as array access") {
    val get = GetEnumElement(Local(bindable("e")), 0, 1, 2)
    val result = JsGen.renderExpr(get)
    assert(result.contains("[2]"), s"Expected index 2 (1+1), got: $result")
  }

  test("GetStructElement renders as array access") {
    val get = GetStructElement(Local(bindable("s")), 0, 2)
    val result = JsGen.renderExpr(get)
    assert(result.contains("[0]"), s"Expected index 0, got: $result")
  }

  // ==================
  // Boolean Expression Tests
  // ==================

  test("TrueConst renders as true") {
    val ifExpr = If(TrueConst, Literal(Lit.Integer(1)), Literal(Lit.Integer(2)))
    val result = JsGen.renderExpr(ifExpr)
    assert(result.contains("true"), s"Expected true, got: $result")
  }

  test("And expression renders") {
    val andExpr = And(TrueConst, TrueConst)
    val ifExpr = If(andExpr, Literal(Lit.Integer(1)), Literal(Lit.Integer(2)))
    val result = JsGen.renderExpr(ifExpr)
    assert(result.contains("&&"), s"Expected &&, got: $result")
  }

  test("CheckVariant renders as array index comparison") {
    val check = CheckVariant(Local(bindable("x")), 1, 0, List(0, 0))
    val ifExpr = If(check, Literal(Lit.Integer(1)), Literal(Lit.Integer(2)))
    val result = JsGen.renderExpr(ifExpr)
    assert(result.contains("[0]"), s"Expected index access, got: $result")
    assert(result.contains("=== 1") || result.contains("===1"), s"Expected comparison with 1, got: $result")
  }

  // ==================
  // Module Rendering Tests
  // ==================

  test("renderModule produces valid code") {
    val bindings = List(
      (bindable("x"), Literal(Lit.Integer(42))),
      (bindable("y"), Literal(Lit.Str("hello")))
    )
    val result = JsGen.renderModule(bindings)
    assert(result.contains("const x = 42"), s"Expected x binding, got: $result")
    // Strings are converted to Bosatsu string representation
    assert(result.contains("const y = _js_to_bosatsu_string(\"hello\")"), s"Expected y binding with Bosatsu string, got: $result")
  }

  // ==================
  // Property Tests
  // ==================

  val genSimpleLit: Gen[Lit] = Gen.oneOf(
    Gen.choose(-1000L, 1000L).map(Lit.Integer(_)),
    Gen.alphaNumStr.map(Lit.Str(_)),
    Gen.alphaChar.map(c => Lit.Chr(c.toString))
  )

  property("literal rendering produces non-empty output") {
    forAll(genSimpleLit) { lit =>
      val result = JsGen.renderExpr(Literal(lit))
      result.nonEmpty
    }
  }

  property("variable names don't contain JS reserved words as standalone") {
    forAll(Gen.alphaStr.filter(_.nonEmpty)) { name =>
      val ident = JsGen.escape(Name(name))
      !JsGen.jsReservedWords.contains(ident.name)
    }
  }

  property("escaped names are valid JS identifiers") {
    forAll(Gen.alphaNumStr.filter(_.nonEmpty)) { name =>
      val ident = JsGen.escape(Name(name))
      // Check first char is letter or underscore
      val first = ident.name.head
      (first.isLetter || first == '_' || first == '$') &&
        ident.name.forall(c => c.isLetterOrDigit || c == '_' || c == '$')
    }
  }

  // ==================
  // Additional Coverage Tests
  // ==================

  test("escape handles Backticked identifiers") {
    val backticked = Identifier.Backticked("myVar")
    val ident = JsGen.escape(backticked)
    assertEquals(ident.name, "myVar")
  }

  test("escape handles names starting with digit") {
    val name = Name("123abc")
    val ident = JsGen.escape(name)
    // Should prefix with underscore
    assert(ident.name.startsWith("_"), s"Expected underscore prefix, got: ${ident.name}")
  }

  test("escape handles empty base name") {
    // A backticked empty string would result in empty escaped name
    val backticked = Identifier.Backticked("")
    val ident = JsGen.escape(backticked)
    // Should have underscore prefix
    assertEquals(ident.name, "_")
  }

  test("escapePackage creates valid module name") {
    val pack = PackageName.parse("Bosatsu/Predef").get
    val result = JsGen.escapePackage(pack)
    assertEquals(result, "Bosatsu_Predef")
  }

  test("qualifiedName creates unique identifier") {
    val pack = PackageName.parse("Bosatsu/Nat").get
    val name = bindable("times2")
    val ident = JsGen.qualifiedName(pack, name)
    assert(ident.name.contains("Bosatsu_Nat"), s"Expected package prefix, got: ${ident.name}")
    assert(ident.name.contains("times2"), s"Expected name, got: ${ident.name}")
  }

  // ==================
  // Env Monad Tests (using map/flatMap directly)
  // ==================

  test("Env.bind creates unique identifier") {
    val env = JsGen.Env.bind(bindable("x"))
    val (state, ident) = JsGen.Env.run(env)
    assertEquals(ident.name, "x")
    assert(state.bindings.contains(bindable("x")))
  }

  test("Env handles shadowing correctly") {
    import cats.syntax.all._
    given cats.Monad[JsGen.Env] = JsGen.Env.envMonad

    val env = JsGen.Env.bind(bindable("x")).flatMap { id1 =>
      JsGen.Env.bind(bindable("x")).flatMap { id2 =>
        JsGen.Env.deref(bindable("x")).map { result =>
          (id1, id2, result)
        }
      }
    }
    val (_, tuple) = JsGen.Env.run(env)
    // Shadowed binding should have different name
    assertNotEquals(tuple._1.name, tuple._2.name)
    assertEquals(tuple._3.name, tuple._2.name)
  }

  test("Env.unbind restores shadowed binding") {
    import cats.syntax.all._
    given cats.Monad[JsGen.Env] = JsGen.Env.envMonad

    val env = JsGen.Env.bind(bindable("x")).flatMap { id1 =>
      JsGen.Env.bind(bindable("x")).flatMap { id2 =>
        JsGen.Env.unbind(bindable("x")).flatMap { _ =>
          JsGen.Env.deref(bindable("x")).map { result =>
            (id1, id2, result)
          }
        }
      }
    }
    val (_, tuple) = JsGen.Env.run(env)
    // After unbind, should get original binding
    assertEquals(tuple._3.name, tuple._1.name)
  }

  test("Env.newTmp generates unique temporary names") {
    import cats.syntax.all._
    given cats.Monad[JsGen.Env] = JsGen.Env.envMonad

    val env = JsGen.Env.newTmp.flatMap { t1 =>
      JsGen.Env.newTmp.flatMap { t2 =>
        JsGen.Env.newTmp.map { t3 =>
          (t1, t2, t3)
        }
      }
    }
    val (_, tuple) = JsGen.Env.run(env)
    assertNotEquals(tuple._1.name, tuple._2.name)
    assertNotEquals(tuple._2.name, tuple._3.name)
    assert(tuple._1.name.startsWith("_tmp"))
  }

  test("Env.anonName returns consistent name for same id") {
    import cats.syntax.all._
    given cats.Monad[JsGen.Env] = JsGen.Env.envMonad

    val env = JsGen.Env.anonName(42L).flatMap { a1 =>
      JsGen.Env.anonName(42L).map { a2 =>
        (a1, a2)
      }
    }
    val (_, tuple) = JsGen.Env.run(env)
    assertEquals(tuple._1.name, tuple._2.name) // Same ID -> same name
  }

  test("Env.anonName creates different names for different ids") {
    import cats.syntax.all._
    given cats.Monad[JsGen.Env] = JsGen.Env.envMonad

    val env = JsGen.Env.anonName(42L).flatMap { a1 =>
      JsGen.Env.anonName(43L).map { a2 =>
        (a1, a2)
      }
    }
    val (_, tuple) = JsGen.Env.run(env)
    assertNotEquals(tuple._1.name, tuple._2.name)
  }

  test("Env.deref returns escaped name for unbound variable") {
    val env = JsGen.Env.deref(bindable("unbound"))
    val (_, ident) = JsGen.Env.run(env)
    assertEquals(ident.name, "unbound")
  }

  // ==================
  // EqualsLit Test
  // ==================

  test("EqualsLit comparison renders") {
    val eq = EqualsLit(Local(bindable("x")), Lit.Integer(42))
    val ifExpr = If(eq, Literal(Lit.Integer(1)), Literal(Lit.Integer(2)))
    val result = JsGen.renderExpr(ifExpr)
    assert(result.contains("42"), s"Expected literal comparison, got: $result")
    assert(result.contains("===") || result.contains("=="), s"Expected equality operator, got: $result")
  }

  // ==================
  // Additional Reserved Word Tests
  // ==================

  test("All common JS reserved words are escaped") {
    val reservedWords = List("class", "function", "var", "let", "const", "this", "super", "return", "if", "else")
    reservedWords.foreach { word =>
      val ident = JsGen.escape(Name(word))
      assertNotEquals(ident.name, word, s"Reserved word '$word' should be escaped")
    }
  }

  test("Common browser globals are escaped") {
    val globals = List("window", "document", "console")
    globals.foreach { word =>
      val ident = JsGen.escape(Name(word))
      assertNotEquals(ident.name, word, s"Browser global '$word' should be escaped")
    }
  }

  test("Standard library names are escaped") {
    val stdLibNames = List("Array", "Object", "String", "Number", "Boolean", "Function", "Math", "JSON")
    stdLibNames.foreach { word =>
      val ident = JsGen.escape(Name(word))
      assertNotEquals(ident.name, word, s"Standard library name '$word' should be escaped")
    }
  }

  // ==================
  // IO Intrinsic Tests
  // ==================

  val IOPackage: PackageName = PackageName.parse("Bosatsu/IO").get
  val UIPackage: PackageName = PackageName.parse("Bosatsu/UI").get

  def ioGlobal(name: String): Matchless.Expr[Unit] =
    Matchless.Global((), IOPackage, Name(name))

  def uiGlobal(name: String): Matchless.Expr[Unit] =
    Matchless.Global((), UIPackage, Name(name))

  test("IOExternal pure generates Pure tagged object") {
    val expr = App(ioGlobal("pure"), NonEmptyList.one(Literal(Lit.Integer(42))))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"Pure\""), s"Expected Pure tag, got: $result")
    assert(result.contains("42"), s"Expected value 42, got: $result")
    assert(result.contains("tag"), s"Expected 'tag' key, got: $result")
    assert(result.contains("value"), s"Expected 'value' key, got: $result")
  }

  test("IOExternal flatMap generates FlatMap tagged object") {
    val io = App(ioGlobal("pure"), NonEmptyList.one(Literal(Lit.Integer(1))))
    val fn = Local(bindable("f"))
    val expr = App(ioGlobal("flatMap"), NonEmptyList.of(io, fn))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"FlatMap\""), s"Expected FlatMap tag, got: $result")
    assert(result.contains("io"), s"Expected 'io' key, got: $result")
    assert(result.contains("fn"), s"Expected 'fn' key, got: $result")
  }

  test("IOExternal sequence generates Sequence tagged object") {
    val ios = Local(bindable("myList"))
    val expr = App(ioGlobal("sequence"), NonEmptyList.one(ios))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"Sequence\""), s"Expected Sequence tag, got: $result")
    assert(result.contains("ios"), s"Expected 'ios' key, got: $result")
  }

  test("IOExternal trace generates Trace tagged object") {
    val unit = MakeStruct(0)
    val expr = App(ioGlobal("trace"), NonEmptyList.one(unit))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"Trace\""), s"Expected Trace tag, got: $result")
  }

  test("IOExternal random_Int generates RandomInt tagged object") {
    val min = Literal(Lit.Integer(1))
    val max = Literal(Lit.Integer(10))
    val expr = App(ioGlobal("random_Int"), NonEmptyList.of(min, max))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"RandomInt\""), s"Expected RandomInt tag, got: $result")
    assert(result.contains("min"), s"Expected 'min' key, got: $result")
    assert(result.contains("max"), s"Expected 'max' key, got: $result")
  }

  test("UIExternal write generates Write tagged object") {
    val stateObj = Local(bindable("myState"))
    val value = Literal(Lit.Integer(99))
    val expr = App(uiGlobal("write"), NonEmptyList.of(stateObj, value))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"Write\""), s"Expected Write tag, got: $result")
    assert(result.contains("state"), s"Expected 'state' key, got: $result")
    assert(result.contains("value"), s"Expected 'value' key, got: $result")
  }

  test("UIExternal on_frame generates RegisterFrameCallback tagged object") {
    val updateFn = Local(bindable("myCallback"))
    val expr = App(uiGlobal("on_frame"), NonEmptyList.one(updateFn))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"RegisterFrameCallback\""), s"Expected RegisterFrameCallback tag, got: $result")
    assert(result.contains("updateFn"), s"Expected 'updateFn' key, got: $result")
  }

  test("UIExternal on_click generates handler registration") {
    val handler = Local(bindable("myHandler"))
    val expr = App(uiGlobal("on_click"), NonEmptyList.one(handler))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("data-onclick"), s"Expected data-onclick attribute, got: $result")
    assert(result.contains("_ui_register_handler"), s"Expected handler registration call, got: $result")
    assert(result.contains("\"click\""), s"Expected click event type, got: $result")
  }

  test("UIExternal on_input generates handler registration") {
    val handler = Local(bindable("myHandler"))
    val expr = App(uiGlobal("on_input"), NonEmptyList.one(handler))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("data-oninput"), s"Expected data-oninput attribute, got: $result")
    assert(result.contains("_ui_register_handler"), s"Expected handler registration call, got: $result")
    assert(result.contains("\"input\""), s"Expected input event type, got: $result")
  }

  test("UIExternal read generates property access") {
    val stateObj = Local(bindable("myState"))
    val expr = App(uiGlobal("read"), NonEmptyList.one(stateObj))
    val result = JsGen.renderExpr(expr)
    assert(result.contains(".value"), s"Expected .value property access, got: $result")
  }

  test("IO intrinsics never generate function/thunk wrappers") {
    // Verify IO data structures are plain objects, not wrapped in () => { ... }
    val pureExpr = App(ioGlobal("pure"), NonEmptyList.one(Literal(Lit.Integer(1))))
    val pureResult = JsGen.renderExpr(pureExpr)
    assert(!pureResult.contains("() =>"), s"pure should not generate thunk, got: $pureResult")

    val stateObj = Local(bindable("s"))
    val writeExpr = App(uiGlobal("write"), NonEmptyList.of(stateObj, Literal(Lit.Integer(1))))
    val writeResult = JsGen.renderExpr(writeExpr)
    assert(!writeResult.contains("() =>"), s"write should not generate thunk, got: $writeResult")
  }

  // ==================
  // IO Intrinsic Tests (additional)
  // ==================

  test("IOExternal capture generates Capture tagged object") {
    val name = Literal(Lit.Str("myCapture"))
    val value = Literal(Lit.Integer(100))
    val expr = App(ioGlobal("capture"), NonEmptyList.of(name, value))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"Capture\""), s"Expected Capture tag, got: $result")
    assert(result.contains("name"), s"Expected 'name' key, got: $result")
    assert(result.contains("value"), s"Expected 'value' key, got: $result")
  }

  test("IOExternal captureFormula generates CaptureFormula tagged object") {
    val name = Literal(Lit.Str("myFormula"))
    val formula = Literal(Lit.Str("x + 1"))
    val value = Literal(Lit.Integer(42))
    val expr = App(ioGlobal("captureFormula"), NonEmptyList.of(name, formula, value))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"CaptureFormula\""), s"Expected CaptureFormula tag, got: $result")
    assert(result.contains("name"), s"Expected 'name' key, got: $result")
    assert(result.contains("formula"), s"Expected 'formula' key, got: $result")
    assert(result.contains("value"), s"Expected 'value' key, got: $result")
  }

  // ==================
  // UI Intrinsic Tests (additional)
  // ==================

  test("UIExternal list_state generates _ui_create_list_state call") {
    val initial = Local(bindable("myInitial"))
    val expr = App(uiGlobal("list_state"), NonEmptyList.one(initial))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("_ui_create_list_state"), s"Expected _ui_create_list_state call, got: $result")
    assert(result.contains("myInitial"), s"Expected initial value argument, got: $result")
  }

  test("UIExternal list_read generates _ui_list_read call") {
    val listState = Local(bindable("myListState"))
    val expr = App(uiGlobal("list_read"), NonEmptyList.one(listState))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("_ui_list_read"), s"Expected _ui_list_read call, got: $result")
    assert(result.contains("myListState"), s"Expected list state argument, got: $result")
  }

  test("UIExternal list_append generates _ui_list_append call") {
    val listState = Local(bindable("myListState"))
    val item = Literal(Lit.Integer(7))
    val expr = App(uiGlobal("list_append"), NonEmptyList.of(listState, item))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("_ui_list_append"), s"Expected _ui_list_append call, got: $result")
    assert(result.contains("myListState"), s"Expected list state argument, got: $result")
    assert(result.contains("7"), s"Expected item argument, got: $result")
  }

  test("UIExternal on_keydown generates handler registration with keydown") {
    val handler = Local(bindable("myHandler"))
    val expr = App(uiGlobal("on_keydown"), NonEmptyList.one(handler))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("data-onkeydown"), s"Expected data-onkeydown attribute, got: $result")
    assert(result.contains("_ui_register_handler"), s"Expected handler registration call, got: $result")
    assert(result.contains("\"keydown\""), s"Expected keydown event type, got: $result")
  }

  test("UIExternal on_dragstart generates handler registration with dragstart") {
    val handler = Local(bindable("myHandler"))
    val expr = App(uiGlobal("on_dragstart"), NonEmptyList.one(handler))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("data-ondragstart"), s"Expected data-ondragstart attribute, got: $result")
    assert(result.contains("_ui_register_handler"), s"Expected handler registration call, got: $result")
    assert(result.contains("\"dragstart\""), s"Expected dragstart event type, got: $result")
  }

  // ==================
  // Canvas Intrinsic Tests
  // ==================

  val CanvasPackage: PackageName = PackageName.parse("Bosatsu/Canvas").get

  def canvasGlobal(name: String): Matchless.Expr[Unit] =
    Matchless.Global((), CanvasPackage, Name(name))

  test("CanvasExternal circle generates tagged object with type circle") {
    val expr = App(canvasGlobal("circle"), NonEmptyList.of(
      Literal(Lit.Integer(10)),
      Literal(Lit.Integer(20)),
      Literal(Lit.Integer(5))
    ))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"circle\""), s"Expected type 'circle', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("x"), s"Expected 'x' key, got: $result")
    assert(result.contains("y"), s"Expected 'y' key, got: $result")
    assert(result.contains("r"), s"Expected 'r' key, got: $result")
    assert(result.contains("10"), s"Expected x value 10, got: $result")
    assert(result.contains("20"), s"Expected y value 20, got: $result")
    assert(result.contains("5"), s"Expected r value 5, got: $result")
  }

  test("CanvasExternal rect generates tagged object with type rect") {
    val expr = App(canvasGlobal("rect"), NonEmptyList.of(
      Literal(Lit.Integer(0)),
      Literal(Lit.Integer(0)),
      Literal(Lit.Integer(100)),
      Literal(Lit.Integer(50))
    ))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"rect\""), s"Expected type 'rect', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("x"), s"Expected 'x' key, got: $result")
    assert(result.contains("y"), s"Expected 'y' key, got: $result")
    assert(result.contains("w"), s"Expected 'w' key, got: $result")
    assert(result.contains("h"), s"Expected 'h' key, got: $result")
    assert(result.contains("100"), s"Expected w value 100, got: $result")
    assert(result.contains("50"), s"Expected h value 50, got: $result")
  }

  test("CanvasExternal line generates tagged object with type line") {
    val expr = App(canvasGlobal("line"), NonEmptyList.of(
      Literal(Lit.Integer(0)),
      Literal(Lit.Integer(0)),
      Literal(Lit.Integer(100)),
      Literal(Lit.Integer(100))
    ))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"line\""), s"Expected type 'line', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("x1"), s"Expected 'x1' key, got: $result")
    assert(result.contains("y1"), s"Expected 'y1' key, got: $result")
    assert(result.contains("x2"), s"Expected 'x2' key, got: $result")
    assert(result.contains("y2"), s"Expected 'y2' key, got: $result")
  }

  test("CanvasExternal fill generates tagged object with type fill") {
    val color = Literal(Lit.Str("red"))
    val expr = App(canvasGlobal("fill"), NonEmptyList.one(color))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"fill\""), s"Expected type 'fill', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("color"), s"Expected 'color' key, got: $result")
    assert(result.contains("_bosatsu_to_js_string"), s"Expected string conversion for color, got: $result")
  }

  test("CanvasExternal stroke generates tagged object with type stroke") {
    val color = Literal(Lit.Str("blue"))
    val expr = App(canvasGlobal("stroke"), NonEmptyList.one(color))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"stroke\""), s"Expected type 'stroke', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("color"), s"Expected 'color' key, got: $result")
    assert(result.contains("_bosatsu_to_js_string"), s"Expected string conversion for color, got: $result")
  }

  test("CanvasExternal clear generates tagged object with type clear") {
    val color = Literal(Lit.Str("white"))
    val expr = App(canvasGlobal("clear"), NonEmptyList.one(color))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"clear\""), s"Expected type 'clear', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("color"), s"Expected 'color' key, got: $result")
    assert(result.contains("_bosatsu_to_js_string"), s"Expected string conversion for color, got: $result")
  }

  test("CanvasExternal save generates tagged object with type save") {
    val unit = MakeStruct(0)
    val expr = App(canvasGlobal("save"), NonEmptyList.one(unit))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"save\""), s"Expected type 'save', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
  }

  test("CanvasExternal restore generates tagged object with type restore") {
    val unit = MakeStruct(0)
    val expr = App(canvasGlobal("restore"), NonEmptyList.one(unit))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"restore\""), s"Expected type 'restore', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
  }

  test("CanvasExternal translate generates tagged object with type translate") {
    val expr = App(canvasGlobal("translate"), NonEmptyList.of(
      Literal(Lit.Integer(50)),
      Literal(Lit.Integer(75))
    ))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"translate\""), s"Expected type 'translate', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("x"), s"Expected 'x' key, got: $result")
    assert(result.contains("y"), s"Expected 'y' key, got: $result")
    assert(result.contains("50"), s"Expected x value 50, got: $result")
    assert(result.contains("75"), s"Expected y value 75, got: $result")
  }

  test("CanvasExternal rotate generates tagged object with type rotate") {
    val expr = App(canvasGlobal("rotate"), NonEmptyList.one(Literal(Lit.Integer(45))))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"rotate\""), s"Expected type 'rotate', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("angle"), s"Expected 'angle' key, got: $result")
    assert(result.contains("45"), s"Expected angle value 45, got: $result")
  }

  test("CanvasExternal scale generates tagged object with type scale") {
    val expr = App(canvasGlobal("scale"), NonEmptyList.of(
      Literal(Lit.Integer(2)),
      Literal(Lit.Integer(3))
    ))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"scale\""), s"Expected type 'scale', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("sx"), s"Expected 'sx' key, got: $result")
    assert(result.contains("sy"), s"Expected 'sy' key, got: $result")
    assert(result.contains("2"), s"Expected sx value 2, got: $result")
    assert(result.contains("3"), s"Expected sy value 3, got: $result")
  }

  test("CanvasExternal canvas_render generates _ui_register_canvas_render call") {
    val state = Local(bindable("myState"))
    val renderFn = Local(bindable("myRenderFn"))
    val expr = App(canvasGlobal("canvas_render"), NonEmptyList.of(state, renderFn))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("data-canvas-render"), s"Expected data-canvas-render attribute, got: $result")
    assert(result.contains("_ui_register_canvas_render"), s"Expected _ui_register_canvas_render call, got: $result")
    assert(result.contains("myState"), s"Expected state argument, got: $result")
    assert(result.contains("myRenderFn"), s"Expected render function argument, got: $result")
  }

  // ==================
  // NumericExternal Arity Consistency Tests
  // ==================

  val NumericPackage: PackageName = PackageName.parse("Bosatsu/Numeric").get

  def numericGlobal(name: String): Matchless.Expr[Unit] =
    Matchless.Global((), NumericPackage, Name(name))

  test("NumericExternal random has arity 1 (takes Unit argument)") {
    // Bosatsu declares: external def random(u: Unit) -> Double
    // JsGen must accept 1 argument (even though JS Math.random() takes 0)
    val unitArg = MakeStruct(0)  // Unit value
    val expr = App(numericGlobal("random"), NonEmptyList.one(unitArg))
    val result = JsGen.renderExpr(expr)
    // Should call Math.random() - the Unit arg is ignored but arity must match
    assert(result.contains("Math.random()"), s"Expected Math.random() call, got: $result")
  }

  test("NumericExternal arities match Bosatsu declarations") {
    // This test catches the class of bugs where JsGen declares a different arity
    // than the Bosatsu source file. All external functions must have matching arity.
    // Uses Identifier types matching JsGen.NumericExternal.results keys.
    val expectedArities: Map[Identifier.Bindable, Int] = Map(
      // Symbolic operators
      Identifier.Operator("+.") -> 2, Identifier.Operator("-.") -> 2,
      Identifier.Operator("*.") -> 2, Identifier.Operator("/.") -> 2,
      // Conversion
      Name("from_Int") -> 1, Name("to_Int") -> 1,
      Name("double_to_String") -> 1, Name("string_to_Double") -> 1,
      // Comparison
      Name("cmp_Double") -> 2, Name("eq_Double") -> 2,
      // Unary
      Name("neg_Double") -> 1, Name("abs_Double") -> 1,
      // Trig
      Name("sin") -> 1, Name("cos") -> 1, Name("tan") -> 1,
      // Power/exponential
      Name("sqrt") -> 1, Name("pow") -> 2, Name("exp") -> 1, Name("log") -> 1,
      // Rounding
      Name("floor") -> 1, Name("ceil") -> 1, Name("round") -> 1,
      // Random - random takes Unit arg, so arity must be 1
      Name("random") -> 1, Name("random_range") -> 2,
      // Min/max
      Name("min_Double") -> 2, Name("max_Double") -> 2
    )

    expectedArities.foreach { case (id, expectedArity) =>
      val actual = JsGen.NumericExternal.results.get(id).map(_._2)
      assert(actual.isDefined, s"NumericExternal missing function: ${id.asString}")
      assertEquals(actual.get, expectedArity, s"Arity mismatch for ${id.asString}")
    }
  }

  test("CanvasExternal sequence generates tagged object with type sequence") {
    val commands = Local(bindable("myCommands"))
    val expr = App(canvasGlobal("sequence"), NonEmptyList.one(commands))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"sequence\""), s"Expected type 'sequence', got: $result")
    assert(result.contains("type"), s"Expected 'type' key, got: $result")
    assert(result.contains("commands"), s"Expected 'commands' key, got: $result")
    assert(result.contains("_bosatsu_list_to_array"), s"Expected list-to-array conversion, got: $result")
  }

  // ==================
  // Missing UIExternal Tests
  // ==================

  test("UIExternal state generates _ui_create_state call") {
    val initial = Literal(Lit.Integer(0))
    val expr = App(uiGlobal("state"), NonEmptyList.one(initial))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("_ui_create_state"), s"Expected _ui_create_state call, got: $result")
  }

  test("UIExternal h generates element VNode object") {
    val tag = Literal(Lit.Str("div"))
    val props = Local(bindable("myProps"))
    val children = Local(bindable("myChildren"))
    val expr = App(uiGlobal("h"), NonEmptyList.of(tag, props, children))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"element\""), s"Expected element type, got: $result")
    assert(result.contains("tag"), s"Expected 'tag' key, got: $result")
    assert(result.contains("props"), s"Expected 'props' key, got: $result")
    assert(result.contains("children"), s"Expected 'children' key, got: $result")
  }

  test("UIExternal text generates text VNode object") {
    val content = Literal(Lit.Str("hello"))
    val expr = App(uiGlobal("text"), NonEmptyList.one(content))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"text\""), s"Expected text type, got: $result")
    assert(result.contains("_bosatsu_to_js_string"), s"Expected string conversion, got: $result")
  }

  test("UIExternal fragment generates fragment VNode object") {
    val children = Local(bindable("myChildren"))
    val expr = App(uiGlobal("fragment"), NonEmptyList.one(children))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"fragment\""), s"Expected fragment type, got: $result")
    assert(result.contains("children"), s"Expected 'children' key, got: $result")
  }

  test("UIExternal on_change generates handler registration with change") {
    val handler = Local(bindable("myHandler"))
    val expr = App(uiGlobal("on_change"), NonEmptyList.one(handler))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("data-onchange"), s"Expected data-onchange attribute, got: $result")
    assert(result.contains("_ui_register_handler"), s"Expected handler registration call, got: $result")
    assert(result.contains("\"change\""), s"Expected change event type, got: $result")
  }

  test("UIExternal on_keyup generates handler registration with keyup") {
    val handler = Local(bindable("myHandler"))
    val expr = App(uiGlobal("on_keyup"), NonEmptyList.one(handler))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("data-onkeyup"), s"Expected data-onkeyup attribute, got: $result")
    assert(result.contains("_ui_register_handler"), s"Expected handler registration call, got: $result")
    assert(result.contains("\"keyup\""), s"Expected keyup event type, got: $result")
  }

  test("UIExternal on_dragover generates handler registration with dragover") {
    val handler = Local(bindable("myHandler"))
    val expr = App(uiGlobal("on_dragover"), NonEmptyList.one(handler))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("data-ondragover"), s"Expected data-ondragover attribute, got: $result")
    assert(result.contains("_ui_register_handler"), s"Expected handler registration call, got: $result")
    assert(result.contains("\"dragover\""), s"Expected dragover event type, got: $result")
  }

  test("UIExternal on_drop generates handler registration with drop") {
    val handler = Local(bindable("myHandler"))
    val expr = App(uiGlobal("on_drop"), NonEmptyList.one(handler))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("data-ondrop"), s"Expected data-ondrop attribute, got: $result")
    assert(result.contains("_ui_register_handler"), s"Expected handler registration call, got: $result")
    assert(result.contains("\"drop\""), s"Expected drop event type, got: $result")
  }

  test("UIExternal list_remove_at generates _ui_list_remove_at call") {
    val listState = Local(bindable("myListState"))
    val index = Literal(Lit.Integer(2))
    val expr = App(uiGlobal("list_remove_at"), NonEmptyList.of(listState, index))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("_ui_list_remove_at"), s"Expected _ui_list_remove_at call, got: $result")
    assert(result.contains("myListState"), s"Expected list state argument, got: $result")
  }

  test("UIExternal list_update_at generates _ui_list_update_at call") {
    val listState = Local(bindable("myListState"))
    val index = Literal(Lit.Integer(1))
    val item = Literal(Lit.Integer(99))
    val expr = App(uiGlobal("list_update_at"), NonEmptyList.of(listState, index, item))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("_ui_list_update_at"), s"Expected _ui_list_update_at call, got: $result")
    assert(result.contains("myListState"), s"Expected list state argument, got: $result")
  }

  test("UIExternal list_length generates property access on items.length") {
    val listState = Local(bindable("myListState"))
    val expr = App(uiGlobal("list_length"), NonEmptyList.one(listState))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("items"), s"Expected items property access, got: $result")
    assert(result.contains("length"), s"Expected length property access, got: $result")
  }

  // ==================
  // Missing CanvasExternal Tests
  // ==================

  test("CanvasExternal text_cmd generates tagged object with type text") {
    val content = Literal(Lit.Str("Hello"))
    val x = Literal(Lit.Integer(10))
    val y = Literal(Lit.Integer(20))
    val expr = App(canvasGlobal("text_cmd"), NonEmptyList.of(content, x, y))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"text\""), s"Expected type 'text', got: $result")
    assert(result.contains("_bosatsu_to_js_string"), s"Expected string conversion, got: $result")
  }

  test("CanvasExternal arc generates tagged object with type arc") {
    val x = Literal(Lit.Integer(50))
    val y = Literal(Lit.Integer(50))
    val r = Literal(Lit.Integer(25))
    val start = Literal(Lit.Integer(0))
    val end = Literal(Lit.Integer(3))
    val expr = App(canvasGlobal("arc"), NonEmptyList.of(x, y, r, start, end))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"arc\""), s"Expected type 'arc', got: $result")
    assert(result.contains("start"), s"Expected 'start' key, got: $result")
    assert(result.contains("end"), s"Expected 'end' key, got: $result")
  }

  test("CanvasExternal line_width generates tagged object with type lineWidth") {
    val width = Literal(Lit.Integer(3))
    val expr = App(canvasGlobal("line_width"), NonEmptyList.one(width))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("\"lineWidth\""), s"Expected type 'lineWidth', got: $result")
    assert(result.contains("width"), s"Expected 'width' key, got: $result")
  }

  // ==================
  // Missing NumericExternal Function Tests
  // ==================

  test("NumericExternal double_to_String generates _js_to_bosatsu_string call") {
    val arg = Local(bindable("myDouble"))
    val expr = App(numericGlobal("double_to_String"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("_js_to_bosatsu_string"), s"Expected _js_to_bosatsu_string call, got: $result")
    assert(result.contains("String"), s"Expected String conversion, got: $result")
  }

  test("NumericExternal string_to_Double generates parseFloat call") {
    val arg = Local(bindable("myStr"))
    val expr = App(numericGlobal("string_to_Double"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("parseFloat"), s"Expected parseFloat call, got: $result")
    assert(result.contains("_bosatsu_to_js_string"), s"Expected _bosatsu_to_js_string call, got: $result")
  }

  test("NumericExternal sin generates Math.sin call") {
    val arg = Local(bindable("angle"))
    val expr = App(numericGlobal("sin"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.sin"), s"Expected Math.sin call, got: $result")
  }

  test("NumericExternal cos generates Math.cos call") {
    val arg = Local(bindable("angle"))
    val expr = App(numericGlobal("cos"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.cos"), s"Expected Math.cos call, got: $result")
  }

  test("NumericExternal tan generates Math.tan call") {
    val arg = Local(bindable("angle"))
    val expr = App(numericGlobal("tan"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.tan"), s"Expected Math.tan call, got: $result")
  }

  test("NumericExternal sqrt generates Math.sqrt call") {
    val arg = Local(bindable("n"))
    val expr = App(numericGlobal("sqrt"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.sqrt"), s"Expected Math.sqrt call, got: $result")
  }

  test("NumericExternal pow generates Math.pow call") {
    val base = Local(bindable("base"))
    val exp = Local(bindable("exp"))
    val expr = App(numericGlobal("pow"), NonEmptyList.of(base, exp))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.pow"), s"Expected Math.pow call, got: $result")
  }

  test("NumericExternal exp generates Math.exp call") {
    val arg = Local(bindable("x"))
    val expr = App(numericGlobal("exp"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.exp"), s"Expected Math.exp call, got: $result")
  }

  test("NumericExternal log generates Math.log call") {
    val arg = Local(bindable("x"))
    val expr = App(numericGlobal("log"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.log"), s"Expected Math.log call, got: $result")
  }

  test("NumericExternal floor generates Math.floor call") {
    val arg = Local(bindable("x"))
    val expr = App(numericGlobal("floor"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.floor"), s"Expected Math.floor call, got: $result")
  }

  test("NumericExternal ceil generates Math.ceil call") {
    val arg = Local(bindable("x"))
    val expr = App(numericGlobal("ceil"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.ceil"), s"Expected Math.ceil call, got: $result")
  }

  test("NumericExternal round generates Math.round call") {
    val arg = Local(bindable("x"))
    val expr = App(numericGlobal("round"), NonEmptyList.one(arg))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.round"), s"Expected Math.round call, got: $result")
  }

  test("NumericExternal random_range generates Math.random with range") {
    val min = Local(bindable("minVal"))
    val max = Local(bindable("maxVal"))
    val expr = App(numericGlobal("random_range"), NonEmptyList.of(min, max))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.random()"), s"Expected Math.random() call, got: $result")
  }

  test("NumericExternal min_Double generates Math.min call") {
    val a = Local(bindable("a"))
    val b = Local(bindable("b"))
    val expr = App(numericGlobal("min_Double"), NonEmptyList.of(a, b))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.min"), s"Expected Math.min call, got: $result")
  }

  test("NumericExternal max_Double generates Math.max call") {
    val a = Local(bindable("a"))
    val b = Local(bindable("b"))
    val expr = App(numericGlobal("max_Double"), NonEmptyList.of(a, b))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.max"), s"Expected Math.max call, got: $result")
  }

  // ==================
  // Numeric Constants Tests
  // ==================

  test("NumericExternal pi constant renders as Math.PI") {
    val expr: Matchless.Expr[Unit] = Matchless.Global((), NumericPackage, Name("pi"))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.PI"), s"Expected Math.PI, got: $result")
  }

  test("NumericExternal e_const constant renders as Math.E") {
    val expr: Matchless.Expr[Unit] = Matchless.Global((), NumericPackage, Name("e_const"))
    val result = JsGen.renderExpr(expr)
    assert(result.contains("Math.E"), s"Expected Math.E, got: $result")
  }

  // ==================
  // Standalone Reference Tests (intrinsic used as value, not called)
  // ==================

  test("IOExternal standalone reference wraps in lambda") {
    // When pure is used as a value (not applied), it should become a lambda
    val expr: Matchless.Expr[Unit] = ioGlobal("pure")
    val result = JsGen.renderExpr(expr)
    assert(result.contains("=>"), s"Expected arrow function for standalone IO ref, got: $result")
  }

  test("UIExternal standalone reference wraps in lambda") {
    val expr: Matchless.Expr[Unit] = uiGlobal("on_click")
    val result = JsGen.renderExpr(expr)
    assert(result.contains("=>"), s"Expected arrow function for standalone UI ref, got: $result")
  }

  test("CanvasExternal standalone reference wraps in lambda") {
    val expr: Matchless.Expr[Unit] = canvasGlobal("circle")
    val result = JsGen.renderExpr(expr)
    assert(result.contains("=>"), s"Expected arrow function for standalone Canvas ref, got: $result")
  }

  // ==================
  // intrinsicValues Tests
  // ==================

  test("intrinsicValues includes all package externals") {
    val values = JsGen.intrinsicValues
    assert(values.contains(PackageName.PredefName), "Missing Predef package")
    assert(values.contains(JsGen.NumericExternal.NumericPackage), "Missing Numeric package")
    assert(values.contains(JsGen.IOExternal.IOPackage), "Missing IO package")
    assert(values.contains(JsGen.UIExternal.UIPackage), "Missing UI package")
    assert(values.contains(JsGen.CanvasExternal.CanvasPackage), "Missing Canvas package")
  }

  test("intrinsicValues Numeric includes constants") {
    val values = JsGen.intrinsicValues
    val numericNames = values(JsGen.NumericExternal.NumericPackage)
    assert(numericNames.contains(Name("pi")), "Missing pi constant")
    assert(numericNames.contains(Name("e_const")), "Missing e_const constant")
  }

  test("intrinsicValues UI includes all event handlers") {
    val values = JsGen.intrinsicValues
    val uiNames = values(JsGen.UIExternal.UIPackage)
    assert(uiNames.contains(Name("on_click")), "Missing on_click")
    assert(uiNames.contains(Name("on_keydown")), "Missing on_keydown")
    assert(uiNames.contains(Name("on_keyup")), "Missing on_keyup")
    assert(uiNames.contains(Name("on_dragstart")), "Missing on_dragstart")
    assert(uiNames.contains(Name("on_dragover")), "Missing on_dragover")
    assert(uiNames.contains(Name("on_drop")), "Missing on_drop")
    assert(uiNames.contains(Name("list_state")), "Missing list_state")
    assert(uiNames.contains(Name("on_frame")), "Missing on_frame")
  }

  test("intrinsicValues Canvas includes all drawing commands") {
    val values = JsGen.intrinsicValues
    val canvasNames = values(JsGen.CanvasExternal.CanvasPackage)
    assert(canvasNames.contains(Name("circle")), "Missing circle")
    assert(canvasNames.contains(Name("rect")), "Missing rect")
    assert(canvasNames.contains(Name("line")), "Missing line")
    assert(canvasNames.contains(Name("text_cmd")), "Missing text_cmd")
    assert(canvasNames.contains(Name("arc")), "Missing arc")
    assert(canvasNames.contains(Name("fill")), "Missing fill")
    assert(canvasNames.contains(Name("stroke")), "Missing stroke")
    assert(canvasNames.contains(Name("canvas_render")), "Missing canvas_render")
  }
}
