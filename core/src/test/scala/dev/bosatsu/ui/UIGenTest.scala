package dev.bosatsu.ui

import munit.FunSuite
import dev.bosatsu.{TypedExpr, Lit}
import dev.bosatsu.rankn.Type

class UIGenTest extends FunSuite {

  // Helper to create mock expressions
  private def makeLiteral(i: Int): TypedExpr[Unit] =
    TypedExpr.Literal(Lit.Integer(i), Type.IntType, ())

  // ==========================================================================
  // UIConfig tests
  // ==========================================================================

  test("UIConfig has sensible defaults") {
    val config = UIGen.UIConfig(title = "Test")
    assertEquals(config.title, "Test")
    assertEquals(config.theme, "light")
    assertEquals(config.includeSourceMap, false)
  }

  test("UIConfig accepts custom theme") {
    val config = UIGen.UIConfig(title = "Test", theme = "dark")
    assertEquals(config.theme, "dark")
  }

  test("UIConfig accepts includeSourceMap option") {
    val config = UIGen.UIConfig(title = "Test", includeSourceMap = true)
    assert(config.includeSourceMap)
  }

  // ==========================================================================
  // generate tests
  // ==========================================================================

  test("generate produces valid HTML structure") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test App")
    val vnodeJs = "const main = { type: 'element', tag: 'div' };"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("<html lang=\"en\">"))
    assert(html.contains("<head>"))
    assert(html.contains("</head>"))
    assert(html.contains("<body>"))
    assert(html.contains("</body>"))
    assert(html.contains("</html>"))
  }

  test("generate includes title in head") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "My Test Title")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("<title>My Test Title</title>"))
  }

  test("generate escapes HTML in title") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "<script>alert('xss')</script>")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(!html.contains("<script>alert('xss')</script>"))
    assert(html.contains("&lt;script&gt;"))
  }

  test("generate includes style block") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("<style>"))
    assert(html.contains("</style>"))
  }

  test("generate includes app container div") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("<div id=\"app\"></div>"))
  }

  test("generate includes script block") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("<script>"))
    assert(html.contains("</script>"))
  }

  test("generate includes bindings map") {
    val expr = makeLiteral(42)
    val binding = UIAnalyzer.DOMBinding[Unit](
      elementId = "count-display",
      property = UIAnalyzer.DOMProperty.TextContent,
      statePath = List("count"),
      when = None,
      transform = None,
      sourceExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("count")),
      bindings = List(binding),
      eventHandlers = Nil
    )
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("const _bindings ="))
    assert(html.contains("count"))
    assert(html.contains("count-display"))
    assert(html.contains("textContent"))
  }

  test("generate includes runtime code") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Runtime functions
    assert(html.contains("function read"))
    assert(html.contains("function _runIO"))
    assert(html.contains("function _updateBindings"))
    assert(html.contains("function _renderVNode"))
  }

  test("generate includes event handler registration") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("_ui_register_handler"))
    assert(html.contains("_initEventHandlers"))
  }

  test("generate includes DOMContentLoaded listener") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("DOMContentLoaded"))
    assert(html.contains("init"))
  }

  test("generate includes vnode execution") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const myVNode = { type: 'text', text: 'hello' };"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("const _vnode = (function()"))
    assert(html.contains("return main"))
  }

  // ==========================================================================
  // generateCounterDemo tests
  // ==========================================================================

  test("generateCounterDemo produces valid HTML") {
    val config = UIGen.UIConfig(title = "Counter")

    val html = UIGen.generateCounterDemo(config)

    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("<title>Counter</title>"))
  }

  test("generateCounterDemo includes counter elements") {
    val config = UIGen.UIConfig(title = "Counter")

    val html = UIGen.generateCounterDemo(config)

    assert(html.contains("id=\"count-display\""))
    assert(html.contains("id=\"increment-btn\""))
    assert(html.contains("id=\"decrement-btn\""))
  }

  test("generateCounterDemo includes state management") {
    val config = UIGen.UIConfig(title = "Counter")

    val html = UIGen.generateCounterDemo(config)

    assert(html.contains("const _state = { count: 0 }"))
    assert(html.contains("function read(path)"))
    assert(html.contains("function write(path, value)"))
  }

  test("generateCounterDemo includes bindings") {
    val config = UIGen.UIConfig(title = "Counter")

    val html = UIGen.generateCounterDemo(config)

    assert(html.contains("const _bindings"))
    assert(html.contains("\"count\""))
    assert(html.contains("elementId"))
    assert(html.contains("textContent"))
  }

  test("generateCounterDemo includes increment and decrement functions") {
    val config = UIGen.UIConfig(title = "Counter")

    val html = UIGen.generateCounterDemo(config)

    assert(html.contains("function increment()"))
    assert(html.contains("function decrement()"))
    assert(html.contains("read(\"count\") + 1"))
    assert(html.contains("read(\"count\") - 1"))
  }

  // ==========================================================================
  // Theme tests
  // ==========================================================================

  test("generate applies light theme styles") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test", theme = "light")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Light theme colors
    assert(html.contains("#f0f4f8")) // light background
    assert(html.contains("#ffffff")) // white card
  }

  test("generate applies dark theme styles") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test", theme = "dark")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Dark theme colors
    assert(html.contains("#1a1a2e")) // dark background
    assert(html.contains("#16213e")) // dark card
  }

  test("generateCounterDemo applies light theme") {
    val config = UIGen.UIConfig(title = "Counter", theme = "light")

    val html = UIGen.generateCounterDemo(config)

    assert(html.contains("#f0f4f8"))
  }

  test("generateCounterDemo applies dark theme") {
    val config = UIGen.UIConfig(title = "Counter", theme = "dark")

    val html = UIGen.generateCounterDemo(config)

    assert(html.contains("#1a1a2e"))
  }

  // ==========================================================================
  // Event handler generation tests
  // ==========================================================================

  test("generate creates event handler for click events") {
    val expr = makeLiteral(42)
    val handler = UIAnalyzer.EventBinding[Unit](
      elementId = "my-button",
      eventType = "click",
      handler = expr,
      preventDefault = true,
      stopPropagation = false
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = Nil,
      bindings = Nil,
      eventHandlers = List(handler)
    )
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Handler registration infrastructure is included (per-handler code generated by JsGen)
    assert(html.contains("_ui_register_handler"))
    assert(html.contains("data-onclick"))
    assert(html.contains("addEventListener"))
  }

  test("generate creates event handler for input events") {
    val expr = makeLiteral(42)
    val handler = UIAnalyzer.EventBinding[Unit](
      elementId = "my-input",
      eventType = "input",
      handler = expr,
      preventDefault = false,
      stopPropagation = false
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = Nil,
      bindings = Nil,
      eventHandlers = List(handler)
    )
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Handler infrastructure includes input event handling and IO execution
    assert(html.contains("data-oninput"))
    assert(html.contains("_runIO"))
  }

  // ==========================================================================
  // Runtime VNode rendering tests
  // ==========================================================================

  test("generate includes _renderVNode function for text nodes") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("case \"text\""))
    assert(html.contains("createTextNode"))
  }

  test("generate includes _renderVNode function for element nodes") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("case \"element\""))
    assert(html.contains("createElement"))
  }

  test("generate includes _renderVNode function for fragment nodes") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("case \"fragment\""))
    assert(html.contains("createDocumentFragment"))
  }

  // ==========================================================================
  // HTML escaping tests
  // ==========================================================================

  test("HTML escaping handles ampersand") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test & Demo")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("Test &amp; Demo"))
  }

  test("HTML escaping handles quotes") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test \"Demo\"")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("Test &quot;Demo&quot;"))
  }

  test("HTML escaping handles apostrophe") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "It's a test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("It&#39;s a test"))
  }

  // ==========================================================================
  // Multiple bindings tests
  // ==========================================================================

  test("generate handles multiple bindings for same state") {
    val expr = makeLiteral(42)
    val binding1 = UIAnalyzer.DOMBinding[Unit](
      elementId = "display1",
      property = UIAnalyzer.DOMProperty.TextContent,
      statePath = List("count"),
      when = None,
      transform = None,
      sourceExpr = expr
    )
    val binding2 = UIAnalyzer.DOMBinding[Unit](
      elementId = "display2",
      property = UIAnalyzer.DOMProperty.ClassName,
      statePath = List("count"),
      when = None,
      transform = None,
      sourceExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("count")),
      bindings = List(binding1, binding2),
      eventHandlers = Nil
    )
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("display1"))
    assert(html.contains("display2"))
    assert(html.contains("textContent"))
    assert(html.contains("className"))
  }

  // ==========================================================================
  // CSS tests
  // ==========================================================================

  test("generate includes CSS reset") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("margin: 0"))
    assert(html.contains("padding: 0"))
    assert(html.contains("box-sizing: border-box"))
  }

  test("generate includes responsive styles") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("max-width"))
    assert(html.contains("display: flex"))
  }

  test("generate includes button styles") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("button"))
    assert(html.contains("cursor: pointer"))
    assert(html.contains("button:hover"))
    assert(html.contains("button:active"))
  }

  // ==========================================================================
  // Canvas bindings in generate
  // ==========================================================================

  test("generate with empty canvasBindings produces minimal canvas section") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Empty canvas bindings produce just the constant declaration
    assert(html.contains("const _canvasBindings = {};"))
    // Should NOT contain the registration function when empty
    assert(!html.contains("_ui_register_canvas_render"))
  }

  test("generate with canvasBindings includes canvas registration function") {
    val expr = makeLiteral(42)
    val canvasBinding = UIAnalyzer.CanvasBinding[Unit](
      elementId = "my-canvas",
      statePath = List("gameState"),
      renderFn = expr,
      stateExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("gameState")),
      bindings = Nil,
      eventHandlers = Nil,
      canvasBindings = List(canvasBinding),
      frameCallbacks = Nil
    )
    val config = UIGen.UIConfig(title = "Canvas App")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Non-empty canvas bindings include the registration function
    assert(html.contains("_ui_register_canvas_render"))
    assert(html.contains("const _canvasBindings = {};"))
    assert(html.contains("_canvas_"))
  }

  test("generate with multiple canvasBindings includes canvas infrastructure") {
    val expr = makeLiteral(0)
    val cb1 = UIAnalyzer.CanvasBinding[Unit](
      elementId = "canvas-1",
      statePath = List("state1"),
      renderFn = expr,
      stateExpr = expr
    )
    val cb2 = UIAnalyzer.CanvasBinding[Unit](
      elementId = "canvas-2",
      statePath = List("state2"),
      renderFn = expr,
      stateExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("state1"), List("state2")),
      bindings = Nil,
      eventHandlers = Nil,
      canvasBindings = List(cb1, cb2),
      frameCallbacks = Nil
    )
    val config = UIGen.UIConfig(title = "Multi Canvas")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("_ui_register_canvas_render"))
    // Canvas runtime should always be present
    assert(html.contains("_initCanvasBindings"))
    assert(html.contains("_updateCanvasBindings"))
    assert(html.contains("_executeCanvas"))
  }

  // ==========================================================================
  // Frame callbacks in generate
  // ==========================================================================

  test("generate with empty frameCallbacks produces minimal frame section") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Empty frame callbacks produce just the array declaration
    assert(html.contains("const _frameCallbacks = [];"))
    // Should NOT contain the registration function when empty
    assert(!html.contains("_ui_register_frame_callback"))
  }

  test("generate with frameCallbacks includes frame registration function") {
    val expr = makeLiteral(0)
    val frameCallback = UIAnalyzer.FrameCallback[Unit](handler = expr)
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = Nil,
      bindings = Nil,
      eventHandlers = Nil,
      canvasBindings = Nil,
      frameCallbacks = List(frameCallback)
    )
    val config = UIGen.UIConfig(title = "Animated App")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Non-empty frame callbacks include the registration function
    assert(html.contains("_ui_register_frame_callback"))
    assert(html.contains("const _frameCallbacks = [];"))
    assert(html.contains("Return Unit"))
  }

  test("generate with frameCallbacks includes animation loop runtime") {
    val expr = makeLiteral(0)
    val frameCallback = UIAnalyzer.FrameCallback[Unit](handler = expr)
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = Nil,
      bindings = Nil,
      eventHandlers = Nil,
      canvasBindings = Nil,
      frameCallbacks = List(frameCallback)
    )
    val config = UIGen.UIConfig(title = "Animated App")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Animation loop infrastructure is always in canvas runtime
    assert(html.contains("_animationLoop"))
    assert(html.contains("_startAnimationLoop"))
    assert(html.contains("requestAnimationFrame"))
    assert(html.contains("MAX_DT"))
    assert(html.contains("_lastFrameTime"))
  }

  test("generate with multiple frameCallbacks includes registration function") {
    val expr1 = makeLiteral(1)
    val expr2 = makeLiteral(2)
    val fc1 = UIAnalyzer.FrameCallback[Unit](handler = expr1)
    val fc2 = UIAnalyzer.FrameCallback[Unit](handler = expr2)
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = Nil,
      bindings = Nil,
      eventHandlers = Nil,
      canvasBindings = Nil,
      frameCallbacks = List(fc1, fc2)
    )
    val config = UIGen.UIConfig(title = "Multi Frame")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("_ui_register_frame_callback"))
    assert(html.contains("_frameCallbacks.push"))
  }

  // ==========================================================================
  // Multiple event handler types
  // ==========================================================================

  test("generate with multiple different event handler types") {
    val expr = makeLiteral(42)
    val clickHandler = UIAnalyzer.EventBinding[Unit](
      elementId = "btn",
      eventType = "click",
      handler = expr,
      preventDefault = true,
      stopPropagation = false
    )
    val inputHandler = UIAnalyzer.EventBinding[Unit](
      elementId = "text-field",
      eventType = "input",
      handler = expr,
      preventDefault = false,
      stopPropagation = false
    )
    val changeHandler = UIAnalyzer.EventBinding[Unit](
      elementId = "select-box",
      eventType = "change",
      handler = expr,
      preventDefault = false,
      stopPropagation = false
    )
    val keydownHandler = UIAnalyzer.EventBinding[Unit](
      elementId = "text-field",
      eventType = "keydown",
      handler = expr,
      preventDefault = false,
      stopPropagation = false
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = Nil,
      bindings = Nil,
      eventHandlers = List(clickHandler, inputHandler, changeHandler, keydownHandler)
    )
    val config = UIGen.UIConfig(title = "Multi Events")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Event handler infrastructure handles all event types
    assert(html.contains("data-onclick"))
    assert(html.contains("data-oninput"))
    assert(html.contains("data-onchange"))
    assert(html.contains("data-onkeydown"))
    assert(html.contains("addEventListener"))
    assert(html.contains("_ui_register_handler"))
  }

  test("generate event handler infrastructure includes keyboard event handling") {
    val expr = makeLiteral(42)
    val keyupHandler = UIAnalyzer.EventBinding[Unit](
      elementId = "input-field",
      eventType = "keyup",
      handler = expr,
      preventDefault = false,
      stopPropagation = false
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = Nil,
      bindings = Nil,
      eventHandlers = List(keyupHandler)
    )
    val config = UIGen.UIConfig(title = "Keyboard Events")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("data-onkeyup"))
    // Keyboard events pass e.key to the handler
    assert(html.contains("e.key"))
  }

  test("generate event handler infrastructure includes drag event handling") {
    val expr = makeLiteral(42)
    val dragStartHandler = UIAnalyzer.EventBinding[Unit](
      elementId = "draggable",
      eventType = "dragstart",
      handler = expr,
      preventDefault = false,
      stopPropagation = false
    )
    val dragOverHandler = UIAnalyzer.EventBinding[Unit](
      elementId = "drop-zone",
      eventType = "dragover",
      handler = expr,
      preventDefault = false,
      stopPropagation = false
    )
    val dropHandler = UIAnalyzer.EventBinding[Unit](
      elementId = "drop-zone",
      eventType = "drop",
      handler = expr,
      preventDefault = false,
      stopPropagation = false
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = Nil,
      bindings = Nil,
      eventHandlers = List(dragStartHandler, dragOverHandler, dropHandler)
    )
    val config = UIGen.UIConfig(title = "Drag Events")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("data-ondragstart"))
    assert(html.contains("data-ondragover"))
    assert(html.contains("data-ondrop"))
  }

  // ==========================================================================
  // Style bindings
  // ==========================================================================

  test("generate with style binding produces style property in bindings map") {
    val expr = makeLiteral(42)
    val styleBinding = UIAnalyzer.DOMBinding[Unit](
      elementId = "animated-el",
      property = UIAnalyzer.DOMProperty.Style("transform"),
      statePath = List("position"),
      when = None,
      transform = None,
      sourceExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("position")),
      bindings = List(styleBinding),
      eventHandlers = Nil
    )
    val config = UIGen.UIConfig(title = "Style Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("const _bindings ="))
    assert(html.contains("animated-el"))
    assert(html.contains("style.transform"))
    assert(html.contains("position"))
  }

  test("generate with style.opacity binding") {
    val expr = makeLiteral(0)
    val styleBinding = UIAnalyzer.DOMBinding[Unit](
      elementId = "fade-el",
      property = UIAnalyzer.DOMProperty.Style("opacity"),
      statePath = List("alpha"),
      when = None,
      transform = None,
      sourceExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("alpha")),
      bindings = List(styleBinding),
      eventHandlers = Nil
    )
    val config = UIGen.UIConfig(title = "Opacity Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("style.opacity"))
    assert(html.contains("fade-el"))
    assert(html.contains("alpha"))
  }

  test("generate with multiple style bindings on same element") {
    val expr = makeLiteral(0)
    val transformBinding = UIAnalyzer.DOMBinding[Unit](
      elementId = "sprite",
      property = UIAnalyzer.DOMProperty.Style("transform"),
      statePath = List("x_pos"),
      when = None,
      transform = None,
      sourceExpr = expr
    )
    val opacityBinding = UIAnalyzer.DOMBinding[Unit](
      elementId = "sprite",
      property = UIAnalyzer.DOMProperty.Style("opacity"),
      statePath = List("visibility"),
      when = None,
      transform = None,
      sourceExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("x_pos"), List("visibility")),
      bindings = List(transformBinding, opacityBinding),
      eventHandlers = Nil
    )
    val config = UIGen.UIConfig(title = "Multi Style")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("style.transform"))
    assert(html.contains("style.opacity"))
    assert(html.contains("x_pos"))
    assert(html.contains("visibility"))
  }

  test("DOMProperty.Style serialization via toJsProperty") {
    assertEquals(
      UIAnalyzer.DOMProperty.toJsProperty(UIAnalyzer.DOMProperty.Style("transform")),
      "style.transform"
    )
    assertEquals(
      UIAnalyzer.DOMProperty.toJsProperty(UIAnalyzer.DOMProperty.Style("opacity")),
      "style.opacity"
    )
    assertEquals(
      UIAnalyzer.DOMProperty.toJsProperty(UIAnalyzer.DOMProperty.Style("background-color")),
      "style.background-color"
    )
  }

  test("DOMProperty.fromString round-trips style properties") {
    val prop = UIAnalyzer.DOMProperty.fromString("style.transform")
    assertEquals(prop, Some(UIAnalyzer.DOMProperty.Style("transform")))

    val prop2 = UIAnalyzer.DOMProperty.fromString("style.opacity")
    assertEquals(prop2, Some(UIAnalyzer.DOMProperty.Style("opacity")))
  }

  // ==========================================================================
  // Generate with all analysis fields populated
  // ==========================================================================

  test("generate with all analysis fields populated") {
    val expr = makeLiteral(42)

    // DOM binding
    val binding = UIAnalyzer.DOMBinding[Unit](
      elementId = "score-display",
      property = UIAnalyzer.DOMProperty.TextContent,
      statePath = List("score"),
      when = None,
      transform = None,
      sourceExpr = expr
    )

    // Style binding
    val styleBinding = UIAnalyzer.DOMBinding[Unit](
      elementId = "player",
      property = UIAnalyzer.DOMProperty.Style("transform"),
      statePath = List("playerPos"),
      when = None,
      transform = None,
      sourceExpr = expr
    )

    // Event handler
    val clickHandler = UIAnalyzer.EventBinding[Unit](
      elementId = "start-btn",
      eventType = "click",
      handler = expr,
      preventDefault = true,
      stopPropagation = false
    )

    // Canvas binding
    val canvasBinding = UIAnalyzer.CanvasBinding[Unit](
      elementId = "game-canvas",
      statePath = List("gameState"),
      renderFn = expr,
      stateExpr = expr
    )

    // Frame callback
    val frameCallback = UIAnalyzer.FrameCallback[Unit](handler = expr)

    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("score"), List("playerPos"), List("gameState")),
      bindings = List(binding, styleBinding),
      eventHandlers = List(clickHandler),
      canvasBindings = List(canvasBinding),
      frameCallbacks = List(frameCallback)
    )
    val config = UIGen.UIConfig(title = "Full Game")
    val vnodeJs = "const main = { type: 'element', tag: 'div' };"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // HTML structure
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("<title>Full Game</title>"))
    assert(html.contains("<div id=\"app\"></div>"))

    // Bindings section
    assert(html.contains("const _bindings ="))
    assert(html.contains("score-display"))
    assert(html.contains("textContent"))
    assert(html.contains("style.transform"))
    assert(html.contains("player"))

    // Event handler infrastructure
    assert(html.contains("_ui_register_handler"))
    assert(html.contains("_initEventHandlers"))

    // Canvas bindings with registration function (non-empty)
    assert(html.contains("_ui_register_canvas_render"))
    assert(html.contains("_initCanvasBindings"))

    // Frame callbacks with registration function (non-empty)
    assert(html.contains("_ui_register_frame_callback"))
    assert(html.contains("_startAnimationLoop"))

    // Runtime code
    assert(html.contains("function _runIO"))
    assert(html.contains("function _renderVNode"))
    assert(html.contains("function _updateBindings"))
    assert(html.contains("DOMContentLoaded"))

    // Canvas runtime
    assert(html.contains("_executeCanvas"))
    assert(html.contains("_animationLoop"))

    // VNode execution
    assert(html.contains("const _vnode = (function()"))
    assert(html.contains("return main"))
  }

  // ==========================================================================
  // HTML structure edge cases
  // ==========================================================================

  test("generate with empty vnodeJs string still produces valid HTML") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Empty")
    val vnodeJs = ""

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("<title>Empty</title>"))
    assert(html.contains("const _vnode = (function()"))
    assert(html.contains("return main"))
    assert(html.contains("</html>"))
  }

  test("generate with empty analysis produces all infrastructure but no custom bindings") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Minimal")
    val vnodeJs = "const main = null;"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Empty bindings map
    assert(html.contains("const _bindings = {};"))
    // Empty canvas bindings (minimal form)
    assert(html.contains("const _canvasBindings = {};"))
    // Empty frame callbacks (minimal form)
    assert(html.contains("const _frameCallbacks = [];"))
    // Runtime code is always present
    assert(html.contains("function _runIO"))
    assert(html.contains("function _renderVNode"))
  }

  test("generate with special characters in vnodeJs embeds them literally") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = """const main = { type: "text", text: "hello <world> & 'friends'" };"""

    val html = UIGen.generate(vnodeJs, analysis, config)

    // VNode JS is embedded literally inside the script tag (not escaped)
    assert(html.contains("""text: "hello <world> & 'friends'" """))
  }

  test("generate with empty title") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("<title></title>"))
  }

  test("generate includes canvas style rule") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Canvas")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Canvas styles are in the generated CSS
    assert(html.contains("canvas {"))
    assert(html.contains("border-radius: 8px"))
  }

  test("generate with conditional DOM binding") {
    val expr = makeLiteral(42)
    val condition = UIAnalyzer.BranchCondition(
      discriminant = List("status"),
      tag = "Success",
      isTotal = false
    )
    val binding = UIAnalyzer.DOMBinding[Unit](
      elementId = "success-msg",
      property = UIAnalyzer.DOMProperty.TextContent,
      statePath = List("status"),
      when = Some(condition),
      transform = None,
      sourceExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("status")),
      bindings = List(binding),
      eventHandlers = Nil
    )
    val config = UIGen.UIConfig(title = "Conditional")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // Conditional binding includes the when clause in the bindings map
    assert(html.contains("const _bindings ="))
    assert(html.contains("success-msg"))
    assert(html.contains("discriminant"))
    assert(html.contains("Success"))
  }

  test("generate with Value property binding for input elements") {
    val expr = makeLiteral(42)
    val binding = UIAnalyzer.DOMBinding[Unit](
      elementId = "my-input",
      property = UIAnalyzer.DOMProperty.Value,
      statePath = List("inputText"),
      when = None,
      transform = None,
      sourceExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("inputText")),
      bindings = List(binding),
      eventHandlers = Nil
    )
    val config = UIGen.UIConfig(title = "Input Binding")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("my-input"))
    assert(html.contains("\"value\""))
  }

  test("generate with transform in DOM binding") {
    val expr = makeLiteral(42)
    val binding = UIAnalyzer.DOMBinding[Unit](
      elementId = "display",
      property = UIAnalyzer.DOMProperty.TextContent,
      statePath = List("count"),
      when = None,
      transform = Some("_int_to_String"),
      sourceExpr = expr
    )
    val analysis = UIAnalyzer.UIAnalysis[Unit](
      stateReads = List(List("count")),
      bindings = List(binding),
      eventHandlers = Nil
    )
    val config = UIGen.UIConfig(title = "Transform")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    assert(html.contains("_int_to_String"))
    assert(html.contains("transform"))
  }

  test("generate init function calls all initialization functions") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // The init function should call all initialization steps
    assert(html.contains("function init()"))
    assert(html.contains("_renderVNode(_vnode)"))
    assert(html.contains("_initEventHandlers()"))
    assert(html.contains("_initCanvasBindings()"))
    assert(html.contains("_startAnimationLoop()"))
  }

  test("generate includes IO runtime operations") {
    val analysis = UIAnalyzer.UIAnalysis.empty[Unit]
    val config = UIGen.UIConfig(title = "IO Test")
    val vnodeJs = "const main = {};"

    val html = UIGen.generate(vnodeJs, analysis, config)

    // IO runtime handles various IO tags
    assert(html.contains("case 'Pure'"))
    assert(html.contains("case 'Write'"))
    assert(html.contains("case 'FlatMap'"))
    assert(html.contains("case 'Sequence'"))
    assert(html.contains("case 'RandomInt'"))
    assert(html.contains("case 'RegisterFrameCallback'"))
  }
}
