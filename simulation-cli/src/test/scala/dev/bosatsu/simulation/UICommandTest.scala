package dev.bosatsu.simulation

import munit.FunSuite
import cats.effect.unsafe.implicits.global
import java.nio.file.{Files, Path, Paths}

class UICommandTest extends FunSuite {

  // ==========================================================================
  // CLI Parsing Tests
  // ==========================================================================

  test("UICommand.parse succeeds with minimal args") {
    val result = UICommand.parse(List("input.bosatsu"))
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.input, Paths.get("input.bosatsu"))
    assertEquals(cmd.output, Paths.get("output.html"))
    assertEquals(cmd.title, None)
    assertEquals(cmd.theme, "light")
    assertEquals(cmd.includeSourceMap, false)
  }

  test("UICommand.parse handles output option") {
    val result = UICommand.parse(List("input.bosatsu", "-o", "out.html"))
    assert(result.isRight)
    assertEquals(result.toOption.get.output, Paths.get("out.html"))
  }

  test("UICommand.parse handles long output option") {
    val result = UICommand.parse(List("input.bosatsu", "--output", "out.html"))
    assert(result.isRight)
    assertEquals(result.toOption.get.output, Paths.get("out.html"))
  }

  test("UICommand.parse handles title option") {
    val result = UICommand.parse(List("input.bosatsu", "-t", "My Title"))
    assert(result.isRight)
    assertEquals(result.toOption.get.title, Some("My Title"))
  }

  test("UICommand.parse handles long title option") {
    val result = UICommand.parse(List("input.bosatsu", "--title", "My Title"))
    assert(result.isRight)
    assertEquals(result.toOption.get.title, Some("My Title"))
  }

  test("UICommand.parse handles theme option") {
    val result = UICommand.parse(List("input.bosatsu", "--theme", "dark"))
    assert(result.isRight)
    assertEquals(result.toOption.get.theme, "dark")
  }

  test("UICommand.parse handles source-map flag") {
    val result = UICommand.parse(List("input.bosatsu", "--source-map"))
    assert(result.isRight)
    assert(result.toOption.get.includeSourceMap)
  }

  test("UICommand.parse handles all options combined") {
    val result = UICommand.parse(List(
      "input.bosatsu",
      "-o", "out.html",
      "-t", "Title",
      "--theme", "dark",
      "--source-map"
    ))
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.input, Paths.get("input.bosatsu"))
    assertEquals(cmd.output, Paths.get("out.html"))
    assertEquals(cmd.title, Some("Title"))
    assertEquals(cmd.theme, "dark")
    assert(cmd.includeSourceMap)
  }

  test("UICommand.parse fails with no arguments") {
    val result = UICommand.parse(List())
    assert(result.isLeft)
  }

  test("UICommand.parse fails with unknown option") {
    val result = UICommand.parse(List("--unknown"))
    assert(result.isLeft)
  }

  test("UICommand.command has correct name") {
    assertEquals(UICommand.command.name, "bosatsu-ui")
  }

  // ==========================================================================
  // Integration Tests - Full Pipeline
  // ==========================================================================

  private def withTempOutput(test: Path => Unit): Unit = {
    val tmpOutput = Files.createTempFile("uicmd_test", ".html")
    try {
      test(tmpOutput)
    } finally {
      Files.deleteIfExists(tmpOutput)
    }
  }

  // Helper to get path to test fixture files
  private def fixturePath(name: String): Path =
    Paths.get("demos/test", name)

  test("run generates HTML from io_single_write fixture") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = Some("Single Write Test"),
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      // HTML structure
      assert(html.contains("<!DOCTYPE html>"), "Should have DOCTYPE")
      assert(html.contains("<title>Single Write Test</title>"), "Should have title")
      assert(html.contains("<div id=\"app\"></div>"), "Should have app container")

      // Runtime functions
      assert(html.contains("function _runIO"), "Should have IO interpreter")
      assert(html.contains("function _renderVNode"), "Should have VNode renderer")
      assert(html.contains("function _updateBinding"), "Should have binding updater")
      assert(html.contains("function _bosatsuStringToJs"), "Should have string converter")
      assert(html.contains("function _bosatsuListToArray"), "Should have list converter")

      // State management
      assert(html.contains("function _ui_create_state"), "Should have state creator")
      assert(html.contains("function _ui_read"), "Should have state reader")
      assert(html.contains("const _state = {}"), "Should have state storage")

      // Event handler infrastructure
      assert(html.contains("function _ui_register_handler"), "Should have handler registration")
      assert(html.contains("function _initBindingCache"), "Should have binding cache init")

      // Initialization
      assert(html.contains("DOMContentLoaded"), "Should have DOMContentLoaded listener")
      assert(html.contains("function init()"), "Should have init function")

      // VNode rendering
      assert(html.contains("_assignVNodeIds"), "Should have VNode ID assignment")
      assert(html.contains("_renderVNode(vnode)"), "Should render VNode")
    }
  }

  test("run generates HTML from io_flatmap_sequence fixture") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_flatmap_sequence.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("<!DOCTYPE html>"))
      // FlatMap IO handling in runtime
      assert(html.contains("case 'FlatMap'"))
      assert(html.contains("case 'Write'"))
      assert(html.contains("case 'Pure'"))
    }
  }

  test("run generates HTML from io_event_handler fixture") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_event_handler.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("<!DOCTYPE html>"))
      // Input event handling
      assert(html.contains("_js_to_bosatsu_string"))
    }
  }

  test("run generates HTML from io_frame_callback fixture") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_frame_callback.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("<!DOCTYPE html>"))
      // Frame callback infrastructure
      assert(html.contains("_frameCallbacks"))
      assert(html.contains("requestAnimationFrame"))
      assert(html.contains("_animationLoop"))
      // IO execution of frame callbacks
      assert(html.contains("case 'RegisterFrameCallback'"))
    }
  }

  test("run generates HTML from io_read_then_write fixture") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_read_then_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("<!DOCTYPE html>"))
    }
  }

  // ==========================================================================
  // Theme Tests
  // ==========================================================================

  test("run applies light theme") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("#f0f4f8"), "Light theme background")
      assert(html.contains("#ffffff"), "Light theme card")
    }
  }

  test("run applies dark theme") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "dark",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("#1a1a2e"), "Dark theme background")
      assert(html.contains("#16213e"), "Dark theme card")
    }
  }

  // ==========================================================================
  // Generated HTML Content Tests
  // ==========================================================================

  test("generated HTML includes CSS reset") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("margin: 0"))
      assert(html.contains("padding: 0"))
      assert(html.contains("box-sizing: border-box"))
    }
  }

  test("generated HTML includes button styles") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("cursor: pointer"))
      assert(html.contains("button:hover"))
      assert(html.contains("button:active"))
    }
  }

  test("generated HTML includes canvas runtime") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("function _executeCanvas"), "Should have canvas executor")
      assert(html.contains("function _initCanvasBindings"), "Should have canvas init")
      assert(html.contains("function _updateCanvasBindings"), "Should have canvas updater")
      assert(html.contains("function _startAnimationLoop"), "Should have animation loop start")
    }
  }

  test("generated HTML includes list state management") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("function _ui_create_list_state"), "Should have list state creator")
      assert(html.contains("function _ui_list_read"), "Should have list reader")
      assert(html.contains("function _ui_list_append"), "Should have list append")
      assert(html.contains("function _ui_list_remove_at"), "Should have list remove")
      assert(html.contains("function _ui_list_update_at"), "Should have list update")
    }
  }

  test("generated HTML includes IO monad all cases") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("case 'Pure'"))
      assert(html.contains("case 'Write'"))
      assert(html.contains("case 'FlatMap'"))
      assert(html.contains("case 'Sequence'"))
      assert(html.contains("case 'Capture'"))
      assert(html.contains("case 'CaptureFormula'"))
      assert(html.contains("case 'Trace'"))
      assert(html.contains("case 'RandomInt'"))
      assert(html.contains("case 'RegisterFrameCallback'"))
    }
  }

  test("generated HTML includes binding map") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("const _bindings ="), "Should have bindings map")
    }
  }

  test("generated HTML includes VNode ID assignment") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("function _assignVNodeIds"), "Should have VNode ID assignment")
      assert(html.contains("function _vnodeHasExplicitId"), "Should have explicit ID check")
      assert(html.contains("data-bosatsu-id"))
    }
  }

  test("generated HTML escapes title properly") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = Some("<script>alert('xss')</script>"),
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(!html.contains("<script>alert('xss')</script>"), "Should escape XSS in title")
      assert(html.contains("&lt;script&gt;"), "Should have escaped script tag")
    }
  }

  test("generated HTML escapes ampersand in title") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = Some("A & B"),
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("A &amp; B"))
    }
  }

  test("generated HTML escapes quotes in title") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = Some("Test \"Demo\""),
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("Test &quot;Demo&quot;"))
    }
  }

  // ==========================================================================
  // Source Map Tests
  // ==========================================================================

  test("source map is not included when disabled") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(!html.contains("sourceMappingURL"), "Should not include source map")
    }
  }

  test("source map is included when enabled") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = true
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("sourceMappingURL"), "Should include source map")
    }
  }

  // ==========================================================================
  // Error Handling Tests
  // ==========================================================================

  test("run fails for nonexistent input file") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = Paths.get("nonexistent.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      interceptMessage[Exception]("nonexistent.bosatsu") {
        cmd.run.unsafeRunSync()
      }
    }
  }

  test("run fails for invalid bosatsu content") {
    val tmpInput = Files.createTempFile("invalid", ".bosatsu")
    try {
      Files.writeString(tmpInput, "this is not valid bosatsu")
      withTempOutput { tmpOutput =>
        val cmd = UICommand(
          input = tmpInput,
          output = tmpOutput,
          title = None,
          theme = "light",
          includeSourceMap = false
        )
        intercept[RuntimeException] {
          cmd.run.unsafeRunSync()
        }
      }
    } finally {
      Files.deleteIfExists(tmpInput)
    }
  }

  test("run fails for bosatsu file without main or view binding") {
    val tmpInput = Files.createTempFile("nomain", ".bosatsu")
    try {
      Files.writeString(tmpInput, """package Test/NoMain
from Bosatsu/Predef import add

x = add(1, 2)
""")
      withTempOutput { tmpOutput =>
        val cmd = UICommand(
          input = tmpInput,
          output = tmpOutput,
          title = None,
          theme = "light",
          includeSourceMap = false
        )
        val ex = intercept[RuntimeException] {
          cmd.run.unsafeRunSync()
        }
        assert(ex.getMessage.contains("main") || ex.getMessage.contains("view"))
      }
    } finally {
      Files.deleteIfExists(tmpInput)
    }
  }

  // ==========================================================================
  // Canvas Runtime Content Tests
  // ==========================================================================

  test("canvas runtime handles all command types") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      // Canvas command types in _executeCanvas
      assert(html.contains("case 'clear'"))
      assert(html.contains("case 'fill'"))
      assert(html.contains("case 'stroke'"))
      assert(html.contains("case 'lineWidth'"))
      assert(html.contains("case 'circle'"))
      assert(html.contains("case 'rect'"))
      assert(html.contains("case 'line'"))
      assert(html.contains("case 'text'"))
      assert(html.contains("case 'arc'"))
      assert(html.contains("case 'save'"))
      assert(html.contains("case 'restore'"))
      assert(html.contains("case 'translate'"))
      assert(html.contains("case 'rotate'"))
      assert(html.contains("case 'scale'"))
      assert(html.contains("case 'sequence'"))
    }
  }

  // ==========================================================================
  // Event Handler Type Coverage
  // ==========================================================================

  test("runtime handles all event handler types") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      // Event types handled in _initBindingCache
      assert(html.contains("'click'"))
      assert(html.contains("'input'"))
      assert(html.contains("'change'"))
      assert(html.contains("'keydown'"))
      assert(html.contains("'keyup'"))
      assert(html.contains("'dragstart'"))
      assert(html.contains("'dragover'"))
      assert(html.contains("'drop'"))
    }
  }

  // ==========================================================================
  // Binding Update Property Coverage
  // ==========================================================================

  test("_updateBinding handles all property types") {
    withTempOutput { tmpOutput =>
      val cmd = UICommand(
        input = fixturePath("io_single_write.bosatsu"),
        output = tmpOutput,
        title = None,
        theme = "light",
        includeSourceMap = false
      )
      cmd.run.unsafeRunSync()

      val html = Files.readString(tmpOutput)
      assert(html.contains("case 'textContent'"))
      assert(html.contains("case 'className'"))
      assert(html.contains("case 'value'"))
      assert(html.contains("case 'checked'"))
      assert(html.contains("case 'disabled'"))
      assert(html.contains("style."))
    }
  }

  // ==========================================================================
  // PathArgument Tests
  // ==========================================================================

  test("PathArgument parses valid path") {
    val result = UICommand.parse(List("/tmp/test.bosatsu"))
    assert(result.isRight)
    assertEquals(result.toOption.get.input, Paths.get("/tmp/test.bosatsu"))
  }

  test("PathArgument parses relative path") {
    val result = UICommand.parse(List("relative/path.bosatsu"))
    assert(result.isRight)
    assertEquals(result.toOption.get.input, Paths.get("relative/path.bosatsu"))
  }
}
