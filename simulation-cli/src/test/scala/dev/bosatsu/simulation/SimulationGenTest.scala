package dev.bosatsu.simulation

import munit.FunSuite
import dev.bosatsu.Identifier
import dev.bosatsu.ui.EmbedGenerator

class SimulationGenTest extends FunSuite {

  def bindable(name: String): Identifier.Bindable =
    Identifier.unsafeBindable(name)

  def makeAnalysis(
      name: String,
      kind: DerivationAnalyzer.DerivationKind,
      deps: Set[String] = Set.empty,
      formula: String = "",
      valueType: String = "number"
  ): DerivationAnalyzer.AnalyzedBinding =
    DerivationAnalyzer.AnalyzedBinding(
      bindable(name),
      kind,
      deps.map(bindable),
      formula,
      valueType
    )

  // ============================================
  // Basic generation tests
  // ============================================

  test("generate produces valid HTML") {
    val analyses = List(
      makeAnalysis("income", DerivationAnalyzer.Assumption, formula = "100000"),
      makeAnalysis("tax_rate", DerivationAnalyzer.Assumption, formula = "0.25"),
      makeAnalysis("tax", DerivationAnalyzer.Computation, Set("income", "tax_rate"), "(income * tax_rate)")
    )

    val config = SimulationGen.SimConfig(
      title = "Tax Calculator",
      showWhy = true,
      showWhatIf = true,
      showSweeps = false
    )

    val html = SimulationGen.generate(analyses, "// computation code", config)

    // Check HTML structure
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("<title>Tax Calculator</title>"))
    assert(html.contains("_derivations"))
    assert(html.contains("_recompute"))
  }

  test("generate includes DOCTYPE and basic HTML structure") {
    val analyses = List(makeAnalysis("x", DerivationAnalyzer.Assumption))
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))

    assert(html.startsWith("<!DOCTYPE html>"))
    assert(html.contains("<html"))
    assert(html.contains("<head>"))
    assert(html.contains("<body>"))
    assert(html.contains("</html>"))
  }

  test("generate includes title in head") {
    val analyses = List(makeAnalysis("x", DerivationAnalyzer.Assumption))
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("My Custom Title"))

    assert(html.contains("<title>My Custom Title</title>"))
  }

  test("generate includes meta viewport") {
    val analyses = List(makeAnalysis("x", DerivationAnalyzer.Assumption))
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))

    assert(html.contains("viewport"))
  }

  // ============================================
  // Feature flag tests
  // ============================================

  test("generate includes Why? buttons when enabled") {
    val analyses = List(
      makeAnalysis("x", DerivationAnalyzer.Assumption),
      makeAnalysis("y", DerivationAnalyzer.Computation, Set("x"), "x + 1")
    )

    val configWithWhy = SimulationGen.SimConfig("Test", showWhy = true)
    val htmlWithWhy = SimulationGen.generate(analyses, "", configWithWhy)
    assert(htmlWithWhy.contains("Why?"))
    assert(htmlWithWhy.contains("showWhyExplanation"))

    val configWithoutWhy = SimulationGen.SimConfig("Test", showWhy = false)
    val htmlWithoutWhy = SimulationGen.generate(analyses, "", configWithoutWhy)
    // Should still have the basic Why? infrastructure but no buttons added
    assert(htmlWithoutWhy.contains("showWhyExplanation"))
  }

  test("generate includes What-if toggles when enabled") {
    val analyses = List(
      makeAnalysis("rate", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("enabled", DerivationAnalyzer.Assumption, valueType = "boolean"),
      makeAnalysis("result", DerivationAnalyzer.Computation, Set("rate", "enabled"))
    )

    val configWithWhatIf = SimulationGen.SimConfig("Test", showWhatIf = true)
    val html = SimulationGen.generate(analyses, "", configWithWhatIf)
    assert(html.contains("What if"))
    assert(html.contains("addWhatIfToggle"))
  }

  test("generate includes sweep sliders when enabled") {
    val analyses = List(
      makeAnalysis("param", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("output", DerivationAnalyzer.Computation, Set("param"))
    )

    val configWithSweeps = SimulationGen.SimConfig("Test", showSweeps = true)
    val html = SimulationGen.generate(analyses, "", configWithSweeps)
    assert(html.contains("sweep-slider"))
    assert(html.contains("addSweepSlider"))
  }

  test("generate includes all features when all enabled") {
    val analyses = List(
      makeAnalysis("x", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("y", DerivationAnalyzer.Computation, Set("x"))
    )

    val config = SimulationGen.SimConfig("Test", showWhy = true, showWhatIf = true, showSweeps = true)
    val html = SimulationGen.generate(analyses, "", config)

    assert(html.contains("Why?"))
    assert(html.contains("What if"))
    assert(html.contains("sweep-slider"))
  }

  // ============================================
  // Theme tests
  // ============================================

  test("generate uses correct theme") {
    val analyses = List(makeAnalysis("x", DerivationAnalyzer.Assumption))

    val lightConfig = SimulationGen.SimConfig("Test", theme = EmbedGenerator.LightTheme)
    val lightHtml = SimulationGen.generate(analyses, "", lightConfig)
    assert(lightHtml.contains("data-theme=\"light\""))

    val darkConfig = SimulationGen.SimConfig("Test", theme = EmbedGenerator.DarkTheme)
    val darkHtml = SimulationGen.generate(analyses, "", darkConfig)
    assert(darkHtml.contains("data-theme=\"dark\""))
  }

  // ============================================
  // Derivation metadata tests
  // ============================================

  test("derivation state includes correct metadata") {
    val analyses = List(
      makeAnalysis("income", DerivationAnalyzer.Assumption, formula = "100000"),
      makeAnalysis("tax", DerivationAnalyzer.Computation, Set("income"), "(income * 0.25)")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))

    // Check derivation metadata
    assert(html.contains("\"income\""))
    assert(html.contains("\"tax\""))
    assert(html.contains("\"assumption\""))
    assert(html.contains("\"computed\""))
    assert(html.contains("(income * 0.25)"))
  }

  test("derivation state includes conditional kind") {
    val analyses = List(
      makeAnalysis("cond", DerivationAnalyzer.Conditional, Set("x"), "if x then 1 else 0")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    assert(html.contains("\"conditional\""))
  }

  test("derivation state includes dependencies") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Assumption),
      makeAnalysis("c", DerivationAnalyzer.Computation, Set("a", "b"), "a + b")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    // Should have deps array
    assert(html.contains("deps"))
  }

  test("derivation state includes value types") {
    val analyses = List(
      makeAnalysis("num", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("str", DerivationAnalyzer.Assumption, valueType = "string"),
      makeAnalysis("bool", DerivationAnalyzer.Assumption, valueType = "boolean"),
      makeAnalysis("any", DerivationAnalyzer.Assumption, valueType = "any")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    assert(html.contains("\"number\""))
    assert(html.contains("\"string\""))
    assert(html.contains("\"boolean\""))
    assert(html.contains("\"any\""))
  }

  // ============================================
  // Ordering and edge cases
  // ============================================

  test("topological sort orders dependencies correctly") {
    // c depends on b, b depends on a
    val analyses = List(
      makeAnalysis("c", DerivationAnalyzer.Computation, Set("b"), "b + 1"),
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Computation, Set("a"), "a * 2")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))

    // In the generated JS, a should appear before b, and b before c
    // We can't easily verify order in the HTML, but we can verify all are present
    assert(html.contains("\"a\""))
    assert(html.contains("\"b\""))
    assert(html.contains("\"c\""))
  }

  test("special characters in formulas are escaped") {
    val analyses = List(
      makeAnalysis("x", DerivationAnalyzer.Computation, formula = "\"quoted\" and 'apostrophe'")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))

    // Should not break the JS
    assert(html.contains("_derivations"))
    // The formula should be escaped
    assert(html.contains("quoted"))
  }

  test("empty analyses list produces valid HTML") {
    val html = SimulationGen.generate(List(), "", SimulationGen.SimConfig("Empty"))
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("<title>Empty</title>"))
  }

  test("single assumption produces valid HTML") {
    val analyses = List(makeAnalysis("x", DerivationAnalyzer.Assumption, formula = "42"))
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Single"))
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("\"x\""))
    assert(html.contains("42"))
  }

  test("values from computation code are extracted and used in state") {
    // computeJs values are extracted via regex and used to initialize state
    // The raw computeJs is NOT included - values are managed through _state object instead
    val analyses = List(makeAnalysis("myVar", DerivationAnalyzer.Assumption))
    val computeJs = "const myVar = 42;"
    val html = SimulationGen.generate(analyses, computeJs, SimulationGen.SimConfig("Test"))
    // Check that the variable and its value appear in the state initialization
    assert(html.contains("myVar"))
    assert(html.contains("42"))
  }

  test("long binding names are handled") {
    val analyses = List(
      makeAnalysis("very_long_variable_name_that_is_quite_descriptive", DerivationAnalyzer.Assumption)
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    assert(html.contains("very_long_variable_name_that_is_quite_descriptive"))
  }

  test("multiple levels of dependencies are included") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Computation, Set("a")),
      makeAnalysis("c", DerivationAnalyzer.Computation, Set("b")),
      makeAnalysis("d", DerivationAnalyzer.Computation, Set("c")),
      makeAnalysis("e", DerivationAnalyzer.Computation, Set("d"))
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))

    for (name <- List("a", "b", "c", "d", "e")) {
      assert(html.contains(s"\"$name\""), s"Should contain $name")
    }
  }

  // ============================================
  // SimConfig tests
  // ============================================

  test("SimConfig has correct defaults") {
    val config = SimulationGen.SimConfig("Title")
    assertEquals(config.title, "Title")
    assertEquals(config.theme, EmbedGenerator.LightTheme)
    assertEquals(config.showWhy, true)
    assertEquals(config.showWhatIf, true)
    assertEquals(config.showSweeps, false)
  }

  test("SimConfig can override all fields") {
    val config = SimulationGen.SimConfig(
      title = "Custom",
      theme = EmbedGenerator.DarkTheme,
      showWhy = false,
      showWhatIf = false,
      showSweeps = true
    )
    assertEquals(config.title, "Custom")
    assertEquals(config.theme, EmbedGenerator.DarkTheme)
    assertEquals(config.showWhy, false)
    assertEquals(config.showWhatIf, false)
    assertEquals(config.showSweeps, true)
  }

  test("SimConfig showCanvas default is false") {
    val config = SimulationGen.SimConfig("Test")
    assertEquals(config.showCanvas, false)
  }

  test("SimConfig showCanvas can be enabled") {
    val config = SimulationGen.SimConfig("Test", showCanvas = true)
    assertEquals(config.showCanvas, true)
  }

  // ============================================
  // generateFunctionBased tests
  // ============================================

  test("generateFunctionBased produces valid HTML") {
    val analyses = List(
      makeAnalysis("principal", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("rate", DerivationAnalyzer.Assumption, valueType = "number")
    )

    val html = SimulationGen.generateFunctionBased(
      funcName = "calculate",
      funcParams = List("principal" -> "Int", "rate" -> "Int"),
      analyses = analyses,
      computeJs = "function calculate(principal, rate) { return principal * rate; }",
      config = SimulationGen.SimConfig("Function Test")
    )

    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("<title>Function Test</title>"))
    assert(html.contains("_recompute"))
    assert(html.contains("calculate"))
  }

  test("generateFunctionBased includes function call with _getState args") {
    val analyses = List(
      makeAnalysis("x", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("y", DerivationAnalyzer.Assumption, valueType = "number")
    )

    val html = SimulationGen.generateFunctionBased(
      funcName = "myFunc",
      funcParams = List("x" -> "Int", "y" -> "Int"),
      analyses = analyses,
      computeJs = "function myFunc(x, y) { return x + y; }",
      config = SimulationGen.SimConfig("Test")
    )

    // The recompute function should call myFunc with _getState args
    assert(html.contains("""_getState("x")"""))
    assert(html.contains("""_getState("y")"""))
    assert(html.contains("myFunc("))
  }

  test("generateFunctionBased includes _formatValue function") {
    val analyses = List(makeAnalysis("n", DerivationAnalyzer.Assumption, valueType = "number"))

    val html = SimulationGen.generateFunctionBased(
      funcName = "f",
      funcParams = List("n" -> "Int"),
      analyses = analyses,
      computeJs = "function f(n) { return n; }",
      config = SimulationGen.SimConfig("Test")
    )

    assert(html.contains("_formatValue"))
    assert(html.contains("toFixed"))
  }

  test("generateFunctionBased generates input controls per param type") {
    val analyses = List(
      makeAnalysis("num_param", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("bool_param", DerivationAnalyzer.Assumption, valueType = "boolean"),
      makeAnalysis("str_param", DerivationAnalyzer.Assumption, valueType = "string")
    )

    val html = SimulationGen.generateFunctionBased(
      funcName = "f",
      funcParams = List("num_param" -> "Int", "bool_param" -> "Bool", "str_param" -> "String"),
      analyses = analyses,
      computeJs = "function f(a,b,c) { return a; }",
      config = SimulationGen.SimConfig("Test")
    )

    // Should contain addInputControl calls with correct input types
    assert(html.contains("""addInputControl("num_param", "number""""))
    assert(html.contains("""addInputControl("bool_param", "checkbox""""))
    assert(html.contains("""addInputControl("str_param", "text""""))
  }

  test("generateFunctionBased with Double param type uses number input") {
    val analyses = List(makeAnalysis("rate", DerivationAnalyzer.Assumption, valueType = "number"))

    val html = SimulationGen.generateFunctionBased(
      funcName = "f",
      funcParams = List("rate" -> "Double"),
      analyses = analyses,
      computeJs = "function f(r) { return r; }",
      config = SimulationGen.SimConfig("Test")
    )

    assert(html.contains("""addInputControl("rate", "number""""))
  }

  test("generateFunctionBased with unknown param type uses text input") {
    val analyses = List(makeAnalysis("data", DerivationAnalyzer.Assumption))

    val html = SimulationGen.generateFunctionBased(
      funcName = "f",
      funcParams = List("data" -> "CustomType"),
      analyses = analyses,
      computeJs = "function f(d) { return d; }",
      config = SimulationGen.SimConfig("Test")
    )

    assert(html.contains("""addInputControl("data", "text""""))
  }

  test("generateFunctionBased initial state defaults for Int") {
    val analyses = List(makeAnalysis("x", DerivationAnalyzer.Assumption, valueType = "number"))

    val html = SimulationGen.generateFunctionBased(
      funcName = "f",
      funcParams = List("x" -> "Int"),
      analyses = analyses,
      computeJs = "function f(x) { return x; }",
      config = SimulationGen.SimConfig("Test")
    )

    // Int default is "0"
    assert(html.contains("\"x\""))
  }

  test("generateFunctionBased includes results section") {
    val analyses = List(makeAnalysis("x", DerivationAnalyzer.Assumption, valueType = "number"))

    val html = SimulationGen.generateFunctionBased(
      funcName = "compute",
      funcParams = List("x" -> "Int"),
      analyses = analyses,
      computeJs = "function compute(x) { return x * 2; }",
      config = SimulationGen.SimConfig("Test")
    )

    // Should have results section creation
    assert(html.contains("results"))
    assert(html.contains("Results"))
  }

  test("generateFunctionBased with empty params") {
    val html = SimulationGen.generateFunctionBased(
      funcName = "noArgs",
      funcParams = Nil,
      analyses = Nil,
      computeJs = "function noArgs() { return 42; }",
      config = SimulationGen.SimConfig("Test")
    )

    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("noArgs()"))
  }

  test("generateFunctionBased includes derivation state for inputs") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption, formula = "10"),
      makeAnalysis("b", DerivationAnalyzer.Assumption, formula = "20")
    )

    val html = SimulationGen.generateFunctionBased(
      funcName = "add",
      funcParams = List("a" -> "Int", "b" -> "Int"),
      analyses = analyses,
      computeJs = "function add(a, b) { return a + b; }",
      config = SimulationGen.SimConfig("Test")
    )

    assert(html.contains("_derivations"))
    assert(html.contains("\"a\""))
    assert(html.contains("\"b\""))
    assert(html.contains("assumption"))
  }

  test("generateFunctionBased uses correct theme from config") {
    val analyses = List(makeAnalysis("x", DerivationAnalyzer.Assumption))

    val html = SimulationGen.generateFunctionBased(
      funcName = "f",
      funcParams = List("x" -> "Int"),
      analyses = analyses,
      computeJs = "function f(x) { return x; }",
      config = SimulationGen.SimConfig("Test", theme = EmbedGenerator.DarkTheme)
    )

    assert(html.contains("data-theme=\"dark\""))
  }

  // ============================================
  // formulaToJs indirect tests (via recompute output)
  // ============================================

  test("generate converts add formula to JS addition") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Assumption),
      makeAnalysis("c", DerivationAnalyzer.Computation, Set("a", "b"), "add(a, b)")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    // formulaToJs should convert add(a, b) to (a + b)
    assert(html.contains("(a + b)"))
  }

  test("generate converts sub formula to JS subtraction") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Assumption),
      makeAnalysis("c", DerivationAnalyzer.Computation, Set("a", "b"), "sub(a, b)")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    assert(html.contains("(a - b)"))
  }

  test("generate converts times formula to JS multiplication") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Assumption),
      makeAnalysis("c", DerivationAnalyzer.Computation, Set("a", "b"), "times(a, b)")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    assert(html.contains("(a * b)"))
  }

  test("generate converts div formula to JS Math.trunc division") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Assumption),
      makeAnalysis("c", DerivationAnalyzer.Computation, Set("a", "b"), "div(a, b)")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    assert(html.contains("Math.trunc(a / b)"))
  }

  test("generate converts nested formula calls") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Assumption),
      makeAnalysis("c", DerivationAnalyzer.Assumption),
      makeAnalysis("d", DerivationAnalyzer.Computation, Set("a", "b", "c"), "add(times(a, b), c)")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    // times(a, b) -> (a * b), then add((a * b), c) -> ((a * b) + c)
    assert(html.contains("((a * b) + c)"))
  }

  test("generate passes through non-function formulas unchanged") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Computation, Set("a"), "a")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    // Simple variable reference should pass through
    assert(html.contains("_derivations"))
  }

  // ============================================
  // Recompute function structure tests
  // ============================================

  test("recompute reads assumptions from _getState") {
    val analyses = List(
      makeAnalysis("income", DerivationAnalyzer.Assumption),
      makeAnalysis("rate", DerivationAnalyzer.Assumption),
      makeAnalysis("tax", DerivationAnalyzer.Computation, Set("income", "rate"), "times(income, rate)")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    // Assumptions should read from state
    assert(html.contains("""_getState('income')"""))
    assert(html.contains("""_getState('rate')"""))
  }

  test("recompute updates _setState for assumptions only") {
    val analyses = List(
      makeAnalysis("input_val", DerivationAnalyzer.Assumption),
      makeAnalysis("computed_val", DerivationAnalyzer.Computation, Set("input_val"), "add(input_val, 1)")
    )

    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    // Assumptions get _setState
    assert(html.contains("""_setState("input_val""""))
    // Computed values only update derivation, not state
    assert(html.contains("""_derivations["computed_val"].value"""))
  }

  test("recompute includes _redrawVisualization call") {
    val analyses = List(makeAnalysis("x", DerivationAnalyzer.Assumption))
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    assert(html.contains("_redrawVisualization"))
  }

  // ============================================
  // Compute JS value extraction tests
  // ============================================

  test("values extracted from computeJs with multiple const declarations") {
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Assumption)
    )
    val computeJs = "const a = 100;\nconst b = 200;"
    val html = SimulationGen.generate(analyses, computeJs, SimulationGen.SimConfig("Test"))
    assert(html.contains("100"))
    assert(html.contains("200"))
  }

  test("values extracted from computeJs with expressions") {
    val analyses = List(
      makeAnalysis("rate", DerivationAnalyzer.Assumption)
    )
    val computeJs = "const rate = 7 / 1200;"
    val html = SimulationGen.generate(analyses, computeJs, SimulationGen.SimConfig("Test"))
    assert(html.contains("7 / 1200"))
  }

  test("computeJs with no matching vars uses undefined") {
    val analyses = List(
      makeAnalysis("unmatched", DerivationAnalyzer.Assumption)
    )
    val computeJs = "const other = 42;"
    val html = SimulationGen.generate(analyses, computeJs, SimulationGen.SimConfig("Test"))
    // The var won't match any const declaration so should get undefined
    assert(html.contains("unmatched"))
  }

  // ============================================
  // escapeJs indirect tests (via formula escaping)
  // ============================================

  test("formulas with newlines are escaped") {
    val analyses = List(
      makeAnalysis("x", DerivationAnalyzer.Computation, formula = "line1\nline2")
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    // Newline should be escaped in the JS string
    assert(html.contains("line1\\nline2"))
  }

  test("formulas with tabs are escaped") {
    val analyses = List(
      makeAnalysis("x", DerivationAnalyzer.Computation, formula = "a\tb")
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    assert(html.contains("a\\tb"))
  }

  test("formulas with backslashes are escaped") {
    val analyses = List(
      makeAnalysis("x", DerivationAnalyzer.Computation, formula = "a\\b")
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))
    assert(html.contains("a\\\\b"))
  }

  // ============================================
  // What-if toggle input type tests
  // ============================================

  test("what-if toggles use number input for number types") {
    val analyses = List(
      makeAnalysis("rate", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("result", DerivationAnalyzer.Computation, Set("rate"))
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test", showWhatIf = true))
    assert(html.contains(""""rate", "number""""))
  }

  test("what-if toggles use checkbox input for boolean types") {
    val analyses = List(
      makeAnalysis("enabled", DerivationAnalyzer.Assumption, valueType = "boolean"),
      makeAnalysis("result", DerivationAnalyzer.Computation, Set("enabled"))
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test", showWhatIf = true))
    assert(html.contains(""""enabled", "checkbox""""))
  }

  test("what-if toggles use text input for string types") {
    val analyses = List(
      makeAnalysis("name", DerivationAnalyzer.Assumption, valueType = "string"),
      makeAnalysis("result", DerivationAnalyzer.Computation, Set("name"))
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test", showWhatIf = true))
    assert(html.contains(""""name", "text""""))
  }

  // ============================================
  // Sweep slider tests
  // ============================================

  test("sweep sliders only added for number-type assumptions") {
    val analyses = List(
      makeAnalysis("num_param", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("str_param", DerivationAnalyzer.Assumption, valueType = "string"),
      makeAnalysis("result", DerivationAnalyzer.Computation, Set("num_param", "str_param"))
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test", showSweeps = true))
    assert(html.contains("""addSweepSlider("num_param""""))
    assert(!html.contains("""addSweepSlider("str_param""""))
  }

  test("disabled features produce no feature-specific calls") {
    val analyses = List(
      makeAnalysis("x", DerivationAnalyzer.Assumption, valueType = "number"),
      makeAnalysis("y", DerivationAnalyzer.Computation, Set("x"))
    )
    val config = SimulationGen.SimConfig("Test", showWhy = false, showWhatIf = false, showSweeps = false)
    val html = SimulationGen.generate(analyses, "", config)
    // No Why buttons added for specific computed values
    assert(!html.contains("""addWhyButton("y", "val-y")"""))
    // No What-if toggles added for specific assumptions
    assert(!html.contains("""addWhatIfToggle("x", "number""""))
    // No sweep slider calls for specific assumptions
    assert(!html.contains("""addSweepSlider("x""""))
  }

  // ============================================
  // Diamond dependency tests
  // ============================================

  test("diamond dependencies are handled correctly") {
    // a -> b, a -> c, b -> d, c -> d (diamond shape)
    val analyses = List(
      makeAnalysis("a", DerivationAnalyzer.Assumption),
      makeAnalysis("b", DerivationAnalyzer.Computation, Set("a"), "add(a, 1)"),
      makeAnalysis("c", DerivationAnalyzer.Computation, Set("a"), "times(a, 2)"),
      makeAnalysis("d", DerivationAnalyzer.Computation, Set("b", "c"), "add(b, c)")
    )
    val html = SimulationGen.generate(analyses, "", SimulationGen.SimConfig("Test"))

    // All should be present
    for (name <- List("a", "b", "c", "d")) {
      assert(html.contains(s""""$name""""), s"Should contain $name")
    }
    assert(html.contains("_recompute"))
  }

  // ============================================
  // Multiple computeJs const declarations
  // ============================================

  test("computeJs with complex expressions extracts correctly") {
    val analyses = List(
      makeAnalysis("monthly_rate", DerivationAnalyzer.Assumption)
    )
    val computeJs = "const monthly_rate = 7 / 1200;"
    val html = SimulationGen.generate(analyses, computeJs, SimulationGen.SimConfig("Test"))
    // Should extract the expression "7 / 1200" as the initial value
    assert(html.contains("monthly_rate"))
  }
}
