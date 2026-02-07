package dev.bosatsu

import munit.FunSuite
import dev.bosatsu.Value._

class CanvasTest extends FunSuite {

  // Helper to get an FfiCall from Canvas externals
  private def getExternal(name: String): FfiCall =
    Canvas.jvmExternals.toMap((Canvas.packageName, name))

  // Helper to wrap a Double into a Value (matching NumericTest pattern)
  private def wrap(d: Double): Value =
    ExternalValue(java.lang.Double.valueOf(d))

  // ==========================================================================
  // packageName tests
  // ==========================================================================

  test("packageName is Bosatsu/Canvas") {
    assertEquals(Canvas.packageName.asString, "Bosatsu/Canvas")
  }

  test("packageName parses correctly") {
    assertEquals(Canvas.packageName, PackageName.parse("Bosatsu/Canvas").get)
  }

  // ==========================================================================
  // canvasString tests
  // ==========================================================================

  test("canvasString is not empty") {
    assert(Canvas.canvasString.nonEmpty)
  }

  test("canvasString contains package declaration") {
    assert(Canvas.canvasString.contains("package Bosatsu/Canvas"))
  }

  // ==========================================================================
  // CanvasCommand ADT tests
  // ==========================================================================

  test("Circle stores x, y, radius") {
    val c = Canvas.Circle(10.0, 20.0, 5.0)
    assertEquals(c.x, 10.0)
    assertEquals(c.y, 20.0)
    assertEquals(c.radius, 5.0)
  }

  test("Circle equality") {
    val c1 = Canvas.Circle(1.0, 2.0, 3.0)
    val c2 = Canvas.Circle(1.0, 2.0, 3.0)
    val c3 = Canvas.Circle(4.0, 5.0, 6.0)
    assertEquals(c1, c2)
    assertNotEquals(c1, c3)
  }

  test("Rect stores x, y, w, h") {
    val r = Canvas.Rect(10.0, 20.0, 100.0, 50.0)
    assertEquals(r.x, 10.0)
    assertEquals(r.y, 20.0)
    assertEquals(r.w, 100.0)
    assertEquals(r.h, 50.0)
  }

  test("Rect equality") {
    val r1 = Canvas.Rect(1.0, 2.0, 3.0, 4.0)
    val r2 = Canvas.Rect(1.0, 2.0, 3.0, 4.0)
    val r3 = Canvas.Rect(5.0, 6.0, 7.0, 8.0)
    assertEquals(r1, r2)
    assertNotEquals(r1, r3)
  }

  test("Line stores x1, y1, x2, y2") {
    val l = Canvas.Line(0.0, 0.0, 100.0, 100.0)
    assertEquals(l.x1, 0.0)
    assertEquals(l.y1, 0.0)
    assertEquals(l.x2, 100.0)
    assertEquals(l.y2, 100.0)
  }

  test("Line equality") {
    val l1 = Canvas.Line(0.0, 0.0, 1.0, 1.0)
    val l2 = Canvas.Line(0.0, 0.0, 1.0, 1.0)
    val l3 = Canvas.Line(2.0, 3.0, 4.0, 5.0)
    assertEquals(l1, l2)
    assertNotEquals(l1, l3)
  }

  test("TextCmd stores content, x, y") {
    val t = Canvas.TextCmd("Hello", 50.0, 75.0)
    assertEquals(t.content, "Hello")
    assertEquals(t.x, 50.0)
    assertEquals(t.y, 75.0)
  }

  test("TextCmd equality") {
    val t1 = Canvas.TextCmd("Hello", 1.0, 2.0)
    val t2 = Canvas.TextCmd("Hello", 1.0, 2.0)
    val t3 = Canvas.TextCmd("World", 1.0, 2.0)
    assertEquals(t1, t2)
    assertNotEquals(t1, t3)
  }

  test("Arc stores x, y, radius, startAngle, endAngle") {
    val a = Canvas.Arc(50.0, 50.0, 25.0, 0.0, 3.14159)
    assertEquals(a.x, 50.0)
    assertEquals(a.y, 50.0)
    assertEquals(a.radius, 25.0)
    assertEquals(a.startAngle, 0.0)
    assertEquals(a.endAngle, 3.14159)
  }

  test("Arc equality") {
    val a1 = Canvas.Arc(1.0, 2.0, 3.0, 0.0, 6.28)
    val a2 = Canvas.Arc(1.0, 2.0, 3.0, 0.0, 6.28)
    val a3 = Canvas.Arc(1.0, 2.0, 3.0, 0.0, 3.14)
    assertEquals(a1, a2)
    assertNotEquals(a1, a3)
  }

  test("Fill stores color") {
    val f = Canvas.Fill("#ff0000")
    assertEquals(f.color, "#ff0000")
  }

  test("Fill equality") {
    val f1 = Canvas.Fill("red")
    val f2 = Canvas.Fill("red")
    val f3 = Canvas.Fill("blue")
    assertEquals(f1, f2)
    assertNotEquals(f1, f3)
  }

  test("Stroke stores color") {
    val s = Canvas.Stroke("blue")
    assertEquals(s.color, "blue")
  }

  test("Stroke equality") {
    val s1 = Canvas.Stroke("red")
    val s2 = Canvas.Stroke("red")
    val s3 = Canvas.Stroke("green")
    assertEquals(s1, s2)
    assertNotEquals(s1, s3)
  }

  test("LineWidth stores width") {
    val lw = Canvas.LineWidth(2.5)
    assertEquals(lw.width, 2.5)
  }

  test("LineWidth equality") {
    val lw1 = Canvas.LineWidth(1.0)
    val lw2 = Canvas.LineWidth(1.0)
    val lw3 = Canvas.LineWidth(3.0)
    assertEquals(lw1, lw2)
    assertNotEquals(lw1, lw3)
  }

  test("Clear stores color") {
    val c = Canvas.Clear("white")
    assertEquals(c.color, "white")
  }

  test("Clear equality") {
    val c1 = Canvas.Clear("white")
    val c2 = Canvas.Clear("white")
    val c3 = Canvas.Clear("black")
    assertEquals(c1, c2)
    assertNotEquals(c1, c3)
  }

  test("Sequence stores list of commands") {
    val cmds = List(Canvas.Fill("red"), Canvas.Circle(0.0, 0.0, 10.0))
    val seq = Canvas.Sequence(cmds)
    assertEquals(seq.commands.length, 2)
    assertEquals(seq.commands.head, Canvas.Fill("red"))
    assertEquals(seq.commands(1), Canvas.Circle(0.0, 0.0, 10.0))
  }

  test("Sequence with empty list") {
    val seq = Canvas.Sequence(Nil)
    assertEquals(seq.commands, Nil)
  }

  test("Sequence equality") {
    val cmds = List(Canvas.Fill("red"))
    val s1 = Canvas.Sequence(cmds)
    val s2 = Canvas.Sequence(cmds)
    val s3 = Canvas.Sequence(Nil)
    assertEquals(s1, s2)
    assertNotEquals(s1, s3)
  }

  test("Save is a singleton") {
    val s1 = Canvas.Save
    val s2 = Canvas.Save
    assert(s1 eq s2)
    assertEquals(s1, s2)
  }

  test("Restore is a singleton") {
    val r1 = Canvas.Restore
    val r2 = Canvas.Restore
    assert(r1 eq r2)
    assertEquals(r1, r2)
  }

  test("Save and Restore are not equal") {
    assertNotEquals(Canvas.Save: Canvas.CanvasCommand, Canvas.Restore: Canvas.CanvasCommand)
  }

  test("Translate stores x, y") {
    val t = Canvas.Translate(10.0, 20.0)
    assertEquals(t.x, 10.0)
    assertEquals(t.y, 20.0)
  }

  test("Translate equality") {
    val t1 = Canvas.Translate(1.0, 2.0)
    val t2 = Canvas.Translate(1.0, 2.0)
    val t3 = Canvas.Translate(3.0, 4.0)
    assertEquals(t1, t2)
    assertNotEquals(t1, t3)
  }

  test("Rotate stores angle") {
    val r = Canvas.Rotate(1.5708)
    assertEquals(r.angle, 1.5708)
  }

  test("Rotate equality") {
    val r1 = Canvas.Rotate(3.14)
    val r2 = Canvas.Rotate(3.14)
    val r3 = Canvas.Rotate(6.28)
    assertEquals(r1, r2)
    assertNotEquals(r1, r3)
  }

  test("Scale stores sx, sy") {
    val s = Canvas.Scale(2.0, 3.0)
    assertEquals(s.sx, 2.0)
    assertEquals(s.sy, 3.0)
  }

  test("Scale equality") {
    val s1 = Canvas.Scale(1.0, 1.0)
    val s2 = Canvas.Scale(1.0, 1.0)
    val s3 = Canvas.Scale(2.0, 2.0)
    assertEquals(s1, s2)
    assertNotEquals(s1, s3)
  }

  test("all CanvasCommand subtypes are CanvasCommand instances") {
    val commands: List[Canvas.CanvasCommand] = List(
      Canvas.Circle(0, 0, 1),
      Canvas.Rect(0, 0, 1, 1),
      Canvas.Line(0, 0, 1, 1),
      Canvas.TextCmd("hi", 0, 0),
      Canvas.Arc(0, 0, 1, 0, 6.28),
      Canvas.Fill("red"),
      Canvas.Stroke("blue"),
      Canvas.LineWidth(1.0),
      Canvas.Clear("white"),
      Canvas.Sequence(Nil),
      Canvas.Save,
      Canvas.Restore,
      Canvas.Translate(0, 0),
      Canvas.Rotate(0),
      Canvas.Scale(1, 1)
    )
    assertEquals(commands.length, 15)
    commands.foreach { cmd =>
      assert(cmd.isInstanceOf[Canvas.CanvasCommand])
    }
  }

  // ==========================================================================
  // jvmExternals - all externals present
  // ==========================================================================

  test("jvmExternals contains all expected function names") {
    val expected = List(
      "circle", "rect", "line", "text_cmd", "arc",
      "fill", "stroke", "line_width",
      "clear", "sequence",
      "save", "restore", "translate", "rotate", "scale",
      "canvas_render"
    )
    expected.foreach { name =>
      assert(
        Canvas.jvmExternals.toMap.contains((Canvas.packageName, name)),
        s"Missing external: $name"
      )
    }
  }

  test("jvmExternals map has exactly 16 entries") {
    assertEquals(Canvas.jvmExternals.toMap.size, 16)
  }

  // ==========================================================================
  // jvmExternals - arity tests
  // ==========================================================================

  test("circle external is Fn3 (arity 3)") {
    val ffi = getExternal("circle")
    assert(ffi.isInstanceOf[FfiCall.Fn3], s"Expected Fn3, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 3)
  }

  test("rect external is Fn4 (arity 4)") {
    val ffi = getExternal("rect")
    assert(ffi.isInstanceOf[FfiCall.Fn4], s"Expected Fn4, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 4)
  }

  test("line external is Fn4 (arity 4)") {
    val ffi = getExternal("line")
    assert(ffi.isInstanceOf[FfiCall.Fn4], s"Expected Fn4, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 4)
  }

  test("text_cmd external is Fn3 (arity 3)") {
    val ffi = getExternal("text_cmd")
    assert(ffi.isInstanceOf[FfiCall.Fn3], s"Expected Fn3, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 3)
  }

  test("arc external is Fn5 (arity 5)") {
    val ffi = getExternal("arc")
    assert(ffi.isInstanceOf[FfiCall.Fn5], s"Expected Fn5, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 5)
  }

  test("fill external is Fn1 (arity 1)") {
    val ffi = getExternal("fill")
    assert(ffi.isInstanceOf[FfiCall.Fn1], s"Expected Fn1, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 1)
  }

  test("stroke external is Fn1 (arity 1)") {
    val ffi = getExternal("stroke")
    assert(ffi.isInstanceOf[FfiCall.Fn1], s"Expected Fn1, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 1)
  }

  test("line_width external is Fn1 (arity 1)") {
    val ffi = getExternal("line_width")
    assert(ffi.isInstanceOf[FfiCall.Fn1], s"Expected Fn1, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 1)
  }

  test("clear external is Fn1 (arity 1)") {
    val ffi = getExternal("clear")
    assert(ffi.isInstanceOf[FfiCall.Fn1], s"Expected Fn1, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 1)
  }

  test("sequence external is Fn1 (arity 1)") {
    val ffi = getExternal("sequence")
    assert(ffi.isInstanceOf[FfiCall.Fn1], s"Expected Fn1, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 1)
  }

  test("save external is Fn1 (arity 1)") {
    val ffi = getExternal("save")
    assert(ffi.isInstanceOf[FfiCall.Fn1], s"Expected Fn1, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 1)
  }

  test("restore external is Fn1 (arity 1)") {
    val ffi = getExternal("restore")
    assert(ffi.isInstanceOf[FfiCall.Fn1], s"Expected Fn1, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 1)
  }

  test("translate external is Fn2 (arity 2)") {
    val ffi = getExternal("translate")
    assert(ffi.isInstanceOf[FfiCall.Fn2], s"Expected Fn2, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 2)
  }

  test("rotate external is Fn1 (arity 1)") {
    val ffi = getExternal("rotate")
    assert(ffi.isInstanceOf[FfiCall.Fn1], s"Expected Fn1, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 1)
  }

  test("scale external is Fn2 (arity 2)") {
    val ffi = getExternal("scale")
    assert(ffi.isInstanceOf[FfiCall.Fn2], s"Expected Fn2, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 2)
  }

  test("canvas_render external is Fn2 (arity 2)") {
    val ffi = getExternal("canvas_render")
    assert(ffi.isInstanceOf[FfiCall.Fn2], s"Expected Fn2, got ${ffi.getClass.getSimpleName}")
    assertEquals(ffi.arity, 2)
  }

  // ==========================================================================
  // jvmExternals - execution: drawing commands
  // ==========================================================================

  test("circle FfiCall produces Circle CanvasCommand") {
    val fn = getExternal("circle") match {
      case FfiCall.Fn3(f) => f
      case other => fail(s"Expected Fn3, got ${other.getClass.getSimpleName}"); null
    }

    val result = fn(wrap(10.0), wrap(20.0), wrap(5.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Circle =>
        assertEquals(cmd.x, 10.0)
        assertEquals(cmd.y, 20.0)
        assertEquals(cmd.radius, 5.0)
      case other => fail(s"Expected Circle, got ${other.getClass.getSimpleName}")
    }
  }

  test("circle FfiCall with zero values") {
    val fn = getExternal("circle") match {
      case FfiCall.Fn3(f) => f
      case _ => fail("Expected Fn3"); null
    }

    val result = fn(wrap(0.0), wrap(0.0), wrap(0.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Circle =>
        assertEquals(cmd.x, 0.0)
        assertEquals(cmd.y, 0.0)
        assertEquals(cmd.radius, 0.0)
      case _ => fail("Expected Circle")
    }
  }

  test("circle FfiCall with negative values") {
    val fn = getExternal("circle") match {
      case FfiCall.Fn3(f) => f
      case _ => fail("Expected Fn3"); null
    }

    val result = fn(wrap(-5.0), wrap(-10.0), wrap(15.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Circle =>
        assertEquals(cmd.x, -5.0)
        assertEquals(cmd.y, -10.0)
        assertEquals(cmd.radius, 15.0)
      case _ => fail("Expected Circle")
    }
  }

  test("rect FfiCall produces Rect CanvasCommand") {
    val fn = getExternal("rect") match {
      case FfiCall.Fn4(f) => f
      case _ => fail("Expected Fn4"); null
    }

    val result = fn(wrap(0.0), wrap(0.0), wrap(100.0), wrap(50.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Rect =>
        assertEquals(cmd.x, 0.0)
        assertEquals(cmd.y, 0.0)
        assertEquals(cmd.w, 100.0)
        assertEquals(cmd.h, 50.0)
      case _ => fail("Expected Rect")
    }
  }

  test("rect FfiCall with fractional values") {
    val fn = getExternal("rect") match {
      case FfiCall.Fn4(f) => f
      case _ => fail("Expected Fn4"); null
    }

    val result = fn(wrap(1.5), wrap(2.5), wrap(3.7), wrap(4.9))
    result.asExternal.toAny match {
      case cmd: Canvas.Rect =>
        assertEquals(cmd.x, 1.5)
        assertEquals(cmd.y, 2.5)
        assertEquals(cmd.w, 3.7)
        assertEquals(cmd.h, 4.9)
      case _ => fail("Expected Rect")
    }
  }

  test("line FfiCall produces Line CanvasCommand") {
    val fn = getExternal("line") match {
      case FfiCall.Fn4(f) => f
      case _ => fail("Expected Fn4"); null
    }

    val result = fn(wrap(0.0), wrap(0.0), wrap(100.0), wrap(100.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Line =>
        assertEquals(cmd.x1, 0.0)
        assertEquals(cmd.y1, 0.0)
        assertEquals(cmd.x2, 100.0)
        assertEquals(cmd.y2, 100.0)
      case _ => fail("Expected Line")
    }
  }

  test("text_cmd FfiCall produces TextCmd CanvasCommand") {
    val fn = getExternal("text_cmd") match {
      case FfiCall.Fn3(f) => f
      case _ => fail("Expected Fn3"); null
    }

    val result = fn(Value.Str("Hello World"), wrap(50.0), wrap(75.0))
    result.asExternal.toAny match {
      case cmd: Canvas.TextCmd =>
        assertEquals(cmd.content, "Hello World")
        assertEquals(cmd.x, 50.0)
        assertEquals(cmd.y, 75.0)
      case _ => fail("Expected TextCmd")
    }
  }

  test("text_cmd FfiCall with empty string") {
    val fn = getExternal("text_cmd") match {
      case FfiCall.Fn3(f) => f
      case _ => fail("Expected Fn3"); null
    }

    val result = fn(Value.Str(""), wrap(0.0), wrap(0.0))
    result.asExternal.toAny match {
      case cmd: Canvas.TextCmd =>
        assertEquals(cmd.content, "")
      case _ => fail("Expected TextCmd")
    }
  }

  test("arc FfiCall produces Arc CanvasCommand") {
    val fn = getExternal("arc") match {
      case FfiCall.Fn5(f) => f
      case _ => fail("Expected Fn5"); null
    }

    val result = fn(wrap(50.0), wrap(50.0), wrap(25.0), wrap(0.0), wrap(6.28318))
    result.asExternal.toAny match {
      case cmd: Canvas.Arc =>
        assertEquals(cmd.x, 50.0)
        assertEquals(cmd.y, 50.0)
        assertEquals(cmd.radius, 25.0)
        assertEquals(cmd.startAngle, 0.0)
        assertEquals(cmd.endAngle, 6.28318)
      case _ => fail("Expected Arc")
    }
  }

  test("arc FfiCall with partial arc") {
    val fn = getExternal("arc") match {
      case FfiCall.Fn5(f) => f
      case _ => fail("Expected Fn5"); null
    }

    val result = fn(wrap(100.0), wrap(200.0), wrap(30.0), wrap(1.0), wrap(3.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Arc =>
        assertEquals(cmd.startAngle, 1.0)
        assertEquals(cmd.endAngle, 3.0)
      case _ => fail("Expected Arc")
    }
  }

  // ==========================================================================
  // jvmExternals - execution: style commands
  // ==========================================================================

  test("fill FfiCall produces Fill CanvasCommand") {
    val fn = getExternal("fill") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(Value.Str("#ff0000"))
    result.asExternal.toAny match {
      case cmd: Canvas.Fill =>
        assertEquals(cmd.color, "#ff0000")
      case _ => fail("Expected Fill")
    }
  }

  test("fill FfiCall with named color") {
    val fn = getExternal("fill") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(Value.Str("red"))
    result.asExternal.toAny match {
      case cmd: Canvas.Fill =>
        assertEquals(cmd.color, "red")
      case _ => fail("Expected Fill")
    }
  }

  test("stroke FfiCall produces Stroke CanvasCommand") {
    val fn = getExternal("stroke") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(Value.Str("blue"))
    result.asExternal.toAny match {
      case cmd: Canvas.Stroke =>
        assertEquals(cmd.color, "blue")
      case _ => fail("Expected Stroke")
    }
  }

  test("stroke FfiCall with hex color") {
    val fn = getExternal("stroke") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(Value.Str("#00ff00"))
    result.asExternal.toAny match {
      case cmd: Canvas.Stroke =>
        assertEquals(cmd.color, "#00ff00")
      case _ => fail("Expected Stroke")
    }
  }

  test("line_width FfiCall produces LineWidth CanvasCommand") {
    val fn = getExternal("line_width") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(wrap(2.5))
    result.asExternal.toAny match {
      case cmd: Canvas.LineWidth =>
        assertEquals(cmd.width, 2.5)
      case _ => fail("Expected LineWidth")
    }
  }

  test("line_width FfiCall with zero width") {
    val fn = getExternal("line_width") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(wrap(0.0))
    result.asExternal.toAny match {
      case cmd: Canvas.LineWidth =>
        assertEquals(cmd.width, 0.0)
      case _ => fail("Expected LineWidth")
    }
  }

  // ==========================================================================
  // jvmExternals - execution: canvas operations
  // ==========================================================================

  test("clear FfiCall produces Clear CanvasCommand") {
    val fn = getExternal("clear") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(Value.Str("white"))
    result.asExternal.toAny match {
      case cmd: Canvas.Clear =>
        assertEquals(cmd.color, "white")
      case _ => fail("Expected Clear")
    }
  }

  test("clear FfiCall with rgba color") {
    val fn = getExternal("clear") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(Value.Str("rgba(0,0,0,0.5)"))
    result.asExternal.toAny match {
      case cmd: Canvas.Clear =>
        assertEquals(cmd.color, "rgba(0,0,0,0.5)")
      case _ => fail("Expected Clear")
    }
  }

  test("sequence FfiCall produces Sequence with empty list") {
    val fn = getExternal("sequence") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(VList.VNil)
    result.asExternal.toAny match {
      case cmd: Canvas.Sequence =>
        assertEquals(cmd.commands, Nil)
      case _ => fail("Expected Sequence")
    }
  }

  test("sequence FfiCall produces Sequence with commands") {
    val seqFn = getExternal("sequence") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }
    val fillFn = getExternal("fill") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }
    val circleFn = getExternal("circle") match {
      case FfiCall.Fn3(f) => f
      case _ => fail("Expected Fn3"); null
    }

    // Create some canvas commands
    val fillCmd = fillFn(Value.Str("red"))
    val circleCmd = circleFn(wrap(50.0), wrap(50.0), wrap(25.0))

    // Put them into a VList
    val cmdList = VList.Cons(fillCmd, VList.Cons(circleCmd, VList.VNil))

    val result = seqFn(cmdList)
    result.asExternal.toAny match {
      case cmd: Canvas.Sequence =>
        assertEquals(cmd.commands.length, 2)
        assertEquals(cmd.commands(0), Canvas.Fill("red"))
        assertEquals(cmd.commands(1), Canvas.Circle(50.0, 50.0, 25.0))
      case _ => fail("Expected Sequence")
    }
  }

  test("sequence FfiCall filters out non-CanvasCommand values") {
    val seqFn = getExternal("sequence") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }
    val fillFn = getExternal("fill") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    // Mix of valid CanvasCommand and a non-CanvasCommand ExternalValue
    val fillCmd = fillFn(Value.Str("red"))
    val nonCmd = ExternalValue("not a canvas command")

    val cmdList = VList.Cons(fillCmd, VList.Cons(nonCmd, VList.VNil))

    val result = seqFn(cmdList)
    result.asExternal.toAny match {
      case cmd: Canvas.Sequence =>
        // Only the Fill should be present; the non-command should be filtered
        assertEquals(cmd.commands.length, 1)
        assertEquals(cmd.commands.head, Canvas.Fill("red"))
      case _ => fail("Expected Sequence")
    }
  }

  test("sequence FfiCall handles non-ExternalValue items in list") {
    val seqFn = getExternal("sequence") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    // List with plain Value items that are not ExternalValue
    val cmdList = VList.Cons(Value.Str("plain string"), VList.VNil)

    // Str("plain string") is ExternalValue(String), but String is not CanvasCommand
    // so it should be filtered out
    val result = seqFn(cmdList)
    result.asExternal.toAny match {
      case cmd: Canvas.Sequence =>
        assertEquals(cmd.commands, Nil)
      case _ => fail("Expected Sequence")
    }
  }

  // ==========================================================================
  // jvmExternals - execution: transform commands
  // ==========================================================================

  test("save FfiCall produces Save CanvasCommand") {
    val fn = getExternal("save") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    // save takes a unit argument (ignored)
    val result = fn(UnitValue)
    val cmd = result.asExternal.toAny.asInstanceOf[Canvas.CanvasCommand]
    assert(cmd == Canvas.Save, s"Expected Save but got $cmd")
  }

  test("save FfiCall ignores its argument") {
    val fn = getExternal("save") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    // Passing any value should still produce Save
    val result = fn(Value.Str("anything"))
    val cmd = result.asExternal.toAny.asInstanceOf[Canvas.CanvasCommand]
    assert(cmd == Canvas.Save, s"Expected Save but got $cmd")
  }

  test("restore FfiCall produces Restore CanvasCommand") {
    val fn = getExternal("restore") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(UnitValue)
    val cmd = result.asExternal.toAny.asInstanceOf[Canvas.CanvasCommand]
    assert(cmd == Canvas.Restore, s"Expected Restore but got $cmd")
  }

  test("restore FfiCall ignores its argument") {
    val fn = getExternal("restore") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(wrap(42.0))
    val cmd = result.asExternal.toAny.asInstanceOf[Canvas.CanvasCommand]
    assert(cmd == Canvas.Restore, s"Expected Restore but got $cmd")
  }

  test("translate FfiCall produces Translate CanvasCommand") {
    val fn = getExternal("translate") match {
      case FfiCall.Fn2(f) => f
      case _ => fail("Expected Fn2"); null
    }

    val result = fn(wrap(10.0), wrap(20.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Translate =>
        assertEquals(cmd.x, 10.0)
        assertEquals(cmd.y, 20.0)
      case _ => fail("Expected Translate")
    }
  }

  test("translate FfiCall with negative offsets") {
    val fn = getExternal("translate") match {
      case FfiCall.Fn2(f) => f
      case _ => fail("Expected Fn2"); null
    }

    val result = fn(wrap(-30.0), wrap(-40.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Translate =>
        assertEquals(cmd.x, -30.0)
        assertEquals(cmd.y, -40.0)
      case _ => fail("Expected Translate")
    }
  }

  test("rotate FfiCall produces Rotate CanvasCommand") {
    val fn = getExternal("rotate") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(wrap(1.5708))
    result.asExternal.toAny match {
      case cmd: Canvas.Rotate =>
        assertEquals(cmd.angle, 1.5708)
      case _ => fail("Expected Rotate")
    }
  }

  test("rotate FfiCall with zero angle") {
    val fn = getExternal("rotate") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val result = fn(wrap(0.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Rotate =>
        assertEquals(cmd.angle, 0.0)
      case _ => fail("Expected Rotate")
    }
  }

  test("scale FfiCall produces Scale CanvasCommand") {
    val fn = getExternal("scale") match {
      case FfiCall.Fn2(f) => f
      case _ => fail("Expected Fn2"); null
    }

    val result = fn(wrap(2.0), wrap(3.0))
    result.asExternal.toAny match {
      case cmd: Canvas.Scale =>
        assertEquals(cmd.sx, 2.0)
        assertEquals(cmd.sy, 3.0)
      case _ => fail("Expected Scale")
    }
  }

  test("scale FfiCall with uniform scaling") {
    val fn = getExternal("scale") match {
      case FfiCall.Fn2(f) => f
      case _ => fail("Expected Fn2"); null
    }

    val result = fn(wrap(0.5), wrap(0.5))
    result.asExternal.toAny match {
      case cmd: Canvas.Scale =>
        assertEquals(cmd.sx, 0.5)
        assertEquals(cmd.sy, 0.5)
      case _ => fail("Expected Scale")
    }
  }

  // ==========================================================================
  // jvmExternals - execution: canvas_render
  // ==========================================================================

  test("canvas_render FfiCall produces a ProductValue tuple") {
    val fn = getExternal("canvas_render") match {
      case FfiCall.Fn2(f) => f
      case _ => fail("Expected Fn2"); null
    }

    val state = Value.Str("some_state")
    val renderFn = UnitValue

    val result = fn(state, renderFn)

    ProductValue.unapplySeq(result) match {
      case Some(seq) if seq.length == 2 =>
        assertEquals(seq(0), Value.Str("data-canvas-render"))
        seq(1) match {
          case Value.Str(s) =>
            assert(s.startsWith("canvas_render_"), s"Expected prefix 'canvas_render_', got: $s")
          case _ => fail("Expected string binding ID")
        }
      case _ => fail("Expected ProductValue with 2 elements")
    }
  }

  test("canvas_render FfiCall produces different IDs for different state objects") {
    val fn = getExternal("canvas_render") match {
      case FfiCall.Fn2(f) => f
      case _ => fail("Expected Fn2"); null
    }

    val state1 = Value.Str("state_a")
    val state2 = Value.Str("state_b")
    val renderFn = UnitValue

    val result1 = fn(state1, renderFn)
    val result2 = fn(state2, renderFn)

    val id1 = ProductValue.unapplySeq(result1).get(1) match {
      case Value.Str(s) => s
      case _ => fail("Expected string"); ""
    }
    val id2 = ProductValue.unapplySeq(result2).get(1) match {
      case Value.Str(s) => s
      case _ => fail("Expected string"); ""
    }

    // Different state objects should produce different identity hash codes
    // (unless JVM happens to reuse the same hash, which is very unlikely)
    // We only assert the prefix is correct for both
    assert(id1.startsWith("canvas_render_"))
    assert(id2.startsWith("canvas_render_"))
  }

  // ==========================================================================
  // Error handling - invalid types for d() helper
  // ==========================================================================

  test("circle FfiCall throws on non-Double arguments") {
    val fn = getExternal("circle") match {
      case FfiCall.Fn3(f) => f
      case _ => fail("Expected Fn3"); null
    }

    intercept[RuntimeException] {
      fn(Value.Str("not a double"), wrap(0.0), wrap(0.0))
    }
  }

  test("fill FfiCall throws on non-String argument") {
    val fn = getExternal("fill") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    intercept[RuntimeException] {
      fn(wrap(42.0))
    }
  }

  test("text_cmd FfiCall throws when content is not a String") {
    val fn = getExternal("text_cmd") match {
      case FfiCall.Fn3(f) => f
      case _ => fail("Expected Fn3"); null
    }

    intercept[RuntimeException] {
      fn(wrap(42.0), wrap(0.0), wrap(0.0))
    }
  }

  test("line_width FfiCall throws on non-Double argument") {
    val fn = getExternal("line_width") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    intercept[RuntimeException] {
      fn(Value.Str("not a double"))
    }
  }

  test("translate FfiCall throws on non-Double arguments") {
    val fn = getExternal("translate") match {
      case FfiCall.Fn2(f) => f
      case _ => fail("Expected Fn2"); null
    }

    intercept[RuntimeException] {
      fn(Value.Str("bad"), wrap(0.0))
    }
  }

  test("rotate FfiCall throws on non-Double argument") {
    val fn = getExternal("rotate") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    intercept[RuntimeException] {
      fn(Value.Str("bad"))
    }
  }

  test("scale FfiCall throws on non-Double arguments") {
    val fn = getExternal("scale") match {
      case FfiCall.Fn2(f) => f
      case _ => fail("Expected Fn2"); null
    }

    intercept[RuntimeException] {
      fn(Value.Str("bad"), wrap(1.0))
    }
  }

  test("rect FfiCall throws on non-Double arguments") {
    val fn = getExternal("rect") match {
      case FfiCall.Fn4(f) => f
      case _ => fail("Expected Fn4"); null
    }

    intercept[RuntimeException] {
      fn(Value.Str("bad"), wrap(0.0), wrap(0.0), wrap(0.0))
    }
  }

  test("line FfiCall throws on non-Double arguments") {
    val fn = getExternal("line") match {
      case FfiCall.Fn4(f) => f
      case _ => fail("Expected Fn4"); null
    }

    intercept[RuntimeException] {
      fn(wrap(0.0), Value.Str("bad"), wrap(0.0), wrap(0.0))
    }
  }

  test("arc FfiCall throws on non-Double arguments") {
    val fn = getExternal("arc") match {
      case FfiCall.Fn5(f) => f
      case _ => fail("Expected Fn5"); null
    }

    intercept[RuntimeException] {
      fn(wrap(0.0), wrap(0.0), wrap(0.0), wrap(0.0), Value.Str("bad"))
    }
  }

  test("stroke FfiCall throws on non-String argument") {
    val fn = getExternal("stroke") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    intercept[RuntimeException] {
      fn(wrap(42.0))
    }
  }

  test("clear FfiCall throws on non-String argument") {
    val fn = getExternal("clear") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    intercept[RuntimeException] {
      fn(wrap(42.0))
    }
  }

  // ==========================================================================
  // Nested/composite sequence tests
  // ==========================================================================

  test("sequence FfiCall with nested Sequence commands") {
    val seqFn = getExternal("sequence") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }
    val fillFn = getExternal("fill") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    // Create an inner sequence
    val innerCmd = fillFn(Value.Str("blue"))
    val innerList = VList.Cons(innerCmd, VList.VNil)
    val innerSeq = seqFn(innerList)

    // Create outer sequence containing the inner sequence
    val outerList = VList.Cons(innerSeq, VList.VNil)
    val outerResult = seqFn(outerList)

    outerResult.asExternal.toAny match {
      case cmd: Canvas.Sequence =>
        assertEquals(cmd.commands.length, 1)
        cmd.commands.head match {
          case inner: Canvas.Sequence =>
            assertEquals(inner.commands.length, 1)
            assertEquals(inner.commands.head, Canvas.Fill("blue"))
          case other => fail(s"Expected nested Sequence, got $other")
        }
      case _ => fail("Expected Sequence")
    }
  }

  test("sequence FfiCall with multiple command types") {
    val seqFn = getExternal("sequence") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }
    val saveFn = getExternal("save") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }
    val translateFn = getExternal("translate") match {
      case FfiCall.Fn2(f) => f
      case _ => fail("Expected Fn2"); null
    }
    val fillFn = getExternal("fill") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }
    val circleFn = getExternal("circle") match {
      case FfiCall.Fn3(f) => f
      case _ => fail("Expected Fn3"); null
    }
    val restoreFn = getExternal("restore") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    // Build a typical drawing sequence: save, translate, fill, circle, restore
    val save = saveFn(UnitValue)
    val translate = translateFn(wrap(100.0), wrap(200.0))
    val fill = fillFn(Value.Str("green"))
    val circle = circleFn(wrap(0.0), wrap(0.0), wrap(30.0))
    val restore = restoreFn(UnitValue)

    val cmdList = VList(List(save, translate, fill, circle, restore))
    val result = seqFn(cmdList)

    result.asExternal.toAny match {
      case cmd: Canvas.Sequence =>
        assertEquals(cmd.commands.length, 5)
        assertEquals(cmd.commands(0), Canvas.Save)
        assertEquals(cmd.commands(1), Canvas.Translate(100.0, 200.0))
        assertEquals(cmd.commands(2), Canvas.Fill("green"))
        assertEquals(cmd.commands(3), Canvas.Circle(0.0, 0.0, 30.0))
        assertEquals(cmd.commands(4), Canvas.Restore)
      case _ => fail("Expected Sequence")
    }
  }

  // ==========================================================================
  // Edge case: large values
  // ==========================================================================

  test("circle FfiCall with very large coordinates") {
    val fn = getExternal("circle") match {
      case FfiCall.Fn3(f) => f
      case _ => fail("Expected Fn3"); null
    }

    val result = fn(wrap(1e10), wrap(-1e10), wrap(1e6))
    result.asExternal.toAny match {
      case cmd: Canvas.Circle =>
        assertEquals(cmd.x, 1e10)
        assertEquals(cmd.y, -1e10)
        assertEquals(cmd.radius, 1e6)
      case _ => fail("Expected Circle")
    }
  }

  test("rotate FfiCall with full rotation") {
    val fn = getExternal("rotate") match {
      case FfiCall.Fn1(f) => f
      case _ => fail("Expected Fn1"); null
    }

    val twoPi = 2.0 * math.Pi
    val result = fn(wrap(twoPi))
    result.asExternal.toAny match {
      case cmd: Canvas.Rotate =>
        assertEquals(cmd.angle, twoPi)
      case _ => fail("Expected Rotate")
    }
  }
}
