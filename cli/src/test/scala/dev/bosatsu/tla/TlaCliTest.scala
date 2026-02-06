package dev.bosatsu.tla

import java.nio.file.{Path, Paths}

class TlaCliTest extends munit.FunSuite {

  // ==========================================================================
  // Helper: parse and extract the action
  // ==========================================================================

  private def parseOk(args: String*): TlaCli.TlaAction =
    TlaCli.parse(args.toList) match {
      case Right(action) => action
      case Left(help)    => fail(s"Expected successful parse but got help:\n$help")
    }

  private def parseErr(args: String*): Unit =
    TlaCli.parse(args.toList) match {
      case Right(action) => fail(s"Expected parse failure but got: $action")
      case Left(_)       => () // expected
    }

  // ==========================================================================
  // Generate command - basic parsing
  // ==========================================================================

  test("TlaCli.parse - generate with file argument only") {
    val action = parseOk("generate", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.file, Paths.get("handler.bosatsu"))
        assertEquals(g.output, None)
        assertEquals(g.instances, 1)
        assertEquals(g.invariant, None)
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate with --output option") {
    val action = parseOk("generate", "--output", "spec.tla", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.output, Some(Paths.get("spec.tla")))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate with -o short flag") {
    val action = parseOk("generate", "-o", "spec.tla", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.output, Some(Paths.get("spec.tla")))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate with --instances option") {
    val action = parseOk("generate", "--instances", "3", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.instances, 3)
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate with -n short flag for instances") {
    val action = parseOk("generate", "-n", "5", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.instances, 5)
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate with --invariant option") {
    val action = parseOk("generate", "--invariant", "x >= 0", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.invariant, Some("x >= 0"))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate with -i short flag for invariant") {
    val action = parseOk("generate", "-i", "count > 0", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.invariant, Some("count > 0"))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate with all options") {
    val action = parseOk(
      "generate",
      "--output", "/tmp/out.tla",
      "--instances", "4",
      "--invariant", "balance >= 0",
      "handler.bosatsu"
    )
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.file, Paths.get("handler.bosatsu"))
        assertEquals(g.output, Some(Paths.get("/tmp/out.tla")))
        assertEquals(g.instances, 4)
        assertEquals(g.invariant, Some("balance >= 0"))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate with all short flags") {
    val action = parseOk(
      "generate",
      "-o", "out.tla",
      "-n", "2",
      "-i", "x > 0",
      "src.bosatsu"
    )
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.file, Paths.get("src.bosatsu"))
        assertEquals(g.output, Some(Paths.get("out.tla")))
        assertEquals(g.instances, 2)
        assertEquals(g.invariant, Some("x > 0"))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate file arg at different positions") {
    // file arg before options should also work with decline
    val action = parseOk("generate", "handler.bosatsu", "-n", "2")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.file, Paths.get("handler.bosatsu"))
        assertEquals(g.instances, 2)
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  // ==========================================================================
  // Generate command - default values
  // ==========================================================================

  test("TlaCli.parse - generate defaults: output is None") {
    val action = parseOk("generate", "file.bosatsu")
    assert(action.isInstanceOf[TlaCli.Generate])
    assertEquals(action.asInstanceOf[TlaCli.Generate].output, None)
  }

  test("TlaCli.parse - generate defaults: instances is 1") {
    val action = parseOk("generate", "file.bosatsu")
    assertEquals(action.asInstanceOf[TlaCli.Generate].instances, 1)
  }

  test("TlaCli.parse - generate defaults: invariant is None") {
    val action = parseOk("generate", "file.bosatsu")
    assertEquals(action.asInstanceOf[TlaCli.Generate].invariant, None)
  }

  // ==========================================================================
  // Check command - basic parsing
  // ==========================================================================

  test("TlaCli.parse - check with file argument only") {
    val action = parseOk("check", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.file, Paths.get("spec.tla"))
        assertEquals(c.workers, 1)
        assertEquals(c.depth, None)
        assertEquals(c.timeout, None)
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - check with --workers option") {
    val action = parseOk("check", "--workers", "4", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.workers, 4)
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - check with -w short flag for workers") {
    val action = parseOk("check", "-w", "8", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.workers, 8)
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - check with --depth option") {
    val action = parseOk("check", "--depth", "100", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.depth, Some(100))
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - check with --timeout option") {
    val action = parseOk("check", "--timeout", "60000", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.timeout, Some(60000))
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - check with all options") {
    val action = parseOk(
      "check",
      "--workers", "4",
      "--depth", "200",
      "--timeout", "30000",
      "model.tla"
    )
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.file, Paths.get("model.tla"))
        assertEquals(c.workers, 4)
        assertEquals(c.depth, Some(200))
        assertEquals(c.timeout, Some(30000))
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - check with -w short flag and other options") {
    val action = parseOk("check", "-w", "2", "--depth", "50", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.workers, 2)
        assertEquals(c.depth, Some(50))
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  // ==========================================================================
  // Check command - default values
  // ==========================================================================

  test("TlaCli.parse - check defaults: workers is 1") {
    val action = parseOk("check", "spec.tla")
    assertEquals(action.asInstanceOf[TlaCli.Check].workers, 1)
  }

  test("TlaCli.parse - check defaults: depth is None") {
    val action = parseOk("check", "spec.tla")
    assertEquals(action.asInstanceOf[TlaCli.Check].depth, None)
  }

  test("TlaCli.parse - check defaults: timeout is None") {
    val action = parseOk("check", "spec.tla")
    assertEquals(action.asInstanceOf[TlaCli.Check].timeout, None)
  }

  // ==========================================================================
  // Race command - basic parsing
  // ==========================================================================

  test("TlaCli.parse - race with file argument only") {
    val action = parseOk("race", "handler.bosatsu")
    action match {
      case r: TlaCli.Race =>
        assertEquals(r.file, Paths.get("handler.bosatsu"))
        assertEquals(r.instances, 1)
        assertEquals(r.invariant, None)
      case other =>
        fail(s"Expected Race, got $other")
    }
  }

  test("TlaCli.parse - race with --instances option") {
    val action = parseOk("race", "--instances", "3", "handler.bosatsu")
    action match {
      case r: TlaCli.Race =>
        assertEquals(r.instances, 3)
      case other =>
        fail(s"Expected Race, got $other")
    }
  }

  test("TlaCli.parse - race with -n short flag for instances") {
    val action = parseOk("race", "-n", "5", "handler.bosatsu")
    action match {
      case r: TlaCli.Race =>
        assertEquals(r.instances, 5)
      case other =>
        fail(s"Expected Race, got $other")
    }
  }

  test("TlaCli.parse - race with --invariant option") {
    val action = parseOk("race", "--invariant", "balance >= 0", "handler.bosatsu")
    action match {
      case r: TlaCli.Race =>
        assertEquals(r.invariant, Some("balance >= 0"))
      case other =>
        fail(s"Expected Race, got $other")
    }
  }

  test("TlaCli.parse - race with -i short flag for invariant") {
    val action = parseOk("race", "-i", "x + y == total", "handler.bosatsu")
    action match {
      case r: TlaCli.Race =>
        assertEquals(r.invariant, Some("x + y == total"))
      case other =>
        fail(s"Expected Race, got $other")
    }
  }

  test("TlaCli.parse - race with all options") {
    val action = parseOk(
      "race",
      "--instances", "4",
      "--invariant", "state[\"count\"] >= 0",
      "handler.bosatsu"
    )
    action match {
      case r: TlaCli.Race =>
        assertEquals(r.file, Paths.get("handler.bosatsu"))
        assertEquals(r.instances, 4)
        assertEquals(r.invariant, Some("state[\"count\"] >= 0"))
      case other =>
        fail(s"Expected Race, got $other")
    }
  }

  test("TlaCli.parse - race with all short flags") {
    val action = parseOk("race", "-n", "3", "-i", "inv", "src.bosatsu")
    action match {
      case r: TlaCli.Race =>
        assertEquals(r.file, Paths.get("src.bosatsu"))
        assertEquals(r.instances, 3)
        assertEquals(r.invariant, Some("inv"))
      case other =>
        fail(s"Expected Race, got $other")
    }
  }

  // ==========================================================================
  // Race command - default values
  // ==========================================================================

  test("TlaCli.parse - race defaults: instances is 1") {
    val action = parseOk("race", "handler.bosatsu")
    assertEquals(action.asInstanceOf[TlaCli.Race].instances, 1)
  }

  test("TlaCli.parse - race defaults: invariant is None") {
    val action = parseOk("race", "handler.bosatsu")
    assertEquals(action.asInstanceOf[TlaCli.Race].invariant, None)
  }

  // ==========================================================================
  // Error cases
  // ==========================================================================

  test("TlaCli.parse - no arguments returns Left") {
    parseErr()
  }

  test("TlaCli.parse - unknown command returns Left") {
    parseErr("unknown")
  }

  test("TlaCli.parse - generate without file returns Left") {
    parseErr("generate")
  }

  test("TlaCli.parse - check without file returns Left") {
    parseErr("check")
  }

  test("TlaCli.parse - race without file returns Left") {
    parseErr("race")
  }

  test("TlaCli.parse - generate with invalid instances (non-integer) returns Left") {
    parseErr("generate", "--instances", "abc", "handler.bosatsu")
  }

  test("TlaCli.parse - check with invalid workers (non-integer) returns Left") {
    parseErr("check", "--workers", "abc", "spec.tla")
  }

  test("TlaCli.parse - check with invalid depth (non-integer) returns Left") {
    parseErr("check", "--depth", "xyz", "spec.tla")
  }

  test("TlaCli.parse - check with invalid timeout (non-integer) returns Left") {
    parseErr("check", "--timeout", "never", "spec.tla")
  }

  test("TlaCli.parse - race with invalid instances (non-integer) returns Left") {
    parseErr("race", "--instances", "many", "handler.bosatsu")
  }

  test("TlaCli.parse - generate with unknown option returns Left") {
    parseErr("generate", "--unknown-option", "value", "handler.bosatsu")
  }

  test("TlaCli.parse - check with unknown option returns Left") {
    parseErr("check", "--bogus", "99", "spec.tla")
  }

  test("TlaCli.parse - race with unknown option returns Left") {
    parseErr("race", "--fake", "thing", "handler.bosatsu")
  }

  // ==========================================================================
  // Command discrimination - correct subtype returned
  // ==========================================================================

  test("TlaCli.parse - generate returns Generate instance") {
    val action = parseOk("generate", "f.bosatsu")
    assert(action.isInstanceOf[TlaCli.Generate])
  }

  test("TlaCli.parse - check returns Check instance") {
    val action = parseOk("check", "f.tla")
    assert(action.isInstanceOf[TlaCli.Check])
  }

  test("TlaCli.parse - race returns Race instance") {
    val action = parseOk("race", "f.bosatsu")
    assert(action.isInstanceOf[TlaCli.Race])
  }

  // ==========================================================================
  // File path handling
  // ==========================================================================

  test("TlaCli.parse - generate with absolute path") {
    val action = parseOk("generate", "/home/user/project/handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.file, Paths.get("/home/user/project/handler.bosatsu"))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - check with absolute path") {
    val action = parseOk("check", "/tmp/spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.file, Paths.get("/tmp/spec.tla"))
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - generate with relative path containing directories") {
    val action = parseOk("generate", "src/main/handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.file, Paths.get("src/main/handler.bosatsu"))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - generate output path with directories") {
    val action = parseOk("generate", "-o", "output/dir/spec.tla", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.output, Some(Paths.get("output/dir/spec.tla")))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  // ==========================================================================
  // TlaCli.Generate case class
  // ==========================================================================

  test("TlaCli.Generate - is a TlaAction") {
    val gen = TlaCli.Generate(
      file = Paths.get("test.bosatsu"),
      output = None,
      instances = 1,
      invariant = None
    )
    assert(gen.isInstanceOf[TlaCli.TlaAction])
  }

  test("TlaCli.Generate - equality") {
    val g1 = TlaCli.Generate(Paths.get("a.bosatsu"), None, 1, None)
    val g2 = TlaCli.Generate(Paths.get("a.bosatsu"), None, 1, None)
    val g3 = TlaCli.Generate(Paths.get("b.bosatsu"), None, 1, None)

    assertEquals(g1, g2)
    assertNotEquals(g1, g3)
  }

  // ==========================================================================
  // TlaCli.Check case class
  // ==========================================================================

  test("TlaCli.Check - is a TlaAction") {
    val chk = TlaCli.Check(
      file = Paths.get("spec.tla"),
      workers = 1,
      depth = None,
      timeout = None
    )
    assert(chk.isInstanceOf[TlaCli.TlaAction])
  }

  test("TlaCli.Check - equality") {
    val c1 = TlaCli.Check(Paths.get("a.tla"), 2, Some(100), None)
    val c2 = TlaCli.Check(Paths.get("a.tla"), 2, Some(100), None)
    val c3 = TlaCli.Check(Paths.get("a.tla"), 4, Some(100), None)

    assertEquals(c1, c2)
    assertNotEquals(c1, c3)
  }

  // ==========================================================================
  // TlaCli.Race case class
  // ==========================================================================

  test("TlaCli.Race - is a TlaAction") {
    val race = TlaCli.Race(
      file = Paths.get("handler.bosatsu"),
      instances = 2,
      invariant = None
    )
    assert(race.isInstanceOf[TlaCli.TlaAction])
  }

  test("TlaCli.Race - equality") {
    val r1 = TlaCli.Race(Paths.get("a.bosatsu"), 3, Some("inv"))
    val r2 = TlaCli.Race(Paths.get("a.bosatsu"), 3, Some("inv"))
    val r3 = TlaCli.Race(Paths.get("a.bosatsu"), 3, None)

    assertEquals(r1, r2)
    assertNotEquals(r1, r3)
  }

  // ==========================================================================
  // TlaCli.command metadata
  // ==========================================================================

  test("TlaCli.command - has correct name") {
    assertEquals(TlaCli.command.name, "tla")
  }

  test("TlaCli.command - has correct header") {
    assertEquals(TlaCli.command.header, "TLA+ formal verification tools")
  }

  // ==========================================================================
  // Options with boundary values
  // ==========================================================================

  test("TlaCli.parse - generate with instances = 0") {
    val action = parseOk("generate", "--instances", "0", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.instances, 0)
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - check with workers = 1") {
    val action = parseOk("check", "--workers", "1", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.workers, 1)
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - check with large depth value") {
    val action = parseOk("check", "--depth", "999999", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.depth, Some(999999))
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - check with large timeout value") {
    val action = parseOk("check", "--timeout", "3600000", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.timeout, Some(3600000))
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - generate with invariant containing special characters") {
    val inv = "state[\"x\"] >= 0 /\\ state[\"y\"] # state[\"z\"]"
    val action = parseOk("generate", "--invariant", inv, "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.invariant, Some(inv))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  // ==========================================================================
  // TlcRunner.findTlc - basic invocation
  // ==========================================================================

  test("TlcRunner.findTlc - returns Option[String]") {
    // We cannot guarantee TLC is installed, but we can verify the method
    // runs without error and returns the correct type.
    val result: Option[String] = TlcRunner.findTlc()
    // result is either Some(path) or None - both are valid
    assert(result.isInstanceOf[Option[String]])
  }

  // ==========================================================================
  // TlcRunner.run - with non-existent spec file
  // ==========================================================================

  test("TlcRunner.run - with nonexistent file returns skipped or error") {
    val bogusPath = Paths.get("/tmp/nonexistent_bosatsu_test_spec_12345.tla")
    val result = TlcRunner.run(bogusPath, TlcOptions())

    // If TLC is not installed, we get skipped.
    // If TLC is installed, we get an error.
    assert(result.skipped || !result.success)
  }

  // ==========================================================================
  // Mixed option ordering
  // ==========================================================================

  test("TlaCli.parse - generate options in different order") {
    val action = parseOk(
      "generate",
      "--invariant", "inv",
      "--instances", "2",
      "--output", "out.tla",
      "handler.bosatsu"
    )
    action match {
      case g: TlaCli.Generate =>
        assertEquals(g.file, Paths.get("handler.bosatsu"))
        assertEquals(g.output, Some(Paths.get("out.tla")))
        assertEquals(g.instances, 2)
        assertEquals(g.invariant, Some("inv"))
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - check options in different order") {
    val action = parseOk(
      "check",
      "--timeout", "5000",
      "--depth", "50",
      "--workers", "2",
      "spec.tla"
    )
    action match {
      case c: TlaCli.Check =>
        assertEquals(c.file, Paths.get("spec.tla"))
        assertEquals(c.workers, 2)
        assertEquals(c.depth, Some(50))
        assertEquals(c.timeout, Some(5000))
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  // ==========================================================================
  // Help message content
  // ==========================================================================

  test("TlaCli.parse - empty args produces help mentioning subcommands") {
    TlaCli.parse(Nil) match {
      case Left(help) =>
        val helpText = help.toString
        assert(helpText.contains("generate"), s"Help should mention generate:\n$helpText")
        assert(helpText.contains("check"), s"Help should mention check:\n$helpText")
        assert(helpText.contains("race"), s"Help should mention race:\n$helpText")
      case Right(_) =>
        fail("Expected Left(Help)")
    }
  }

  test("TlaCli.parse - help text contains command name") {
    TlaCli.parse(Nil) match {
      case Left(help) =>
        val helpText = help.toString
        assert(helpText.contains("tla"), s"Help should contain command name 'tla':\n$helpText")
      case Right(_) =>
        fail("Expected Left(Help)")
    }
  }

  // ==========================================================================
  // Generate produces the correct TlaCli.Generate fields (not TlaCommand)
  // ==========================================================================

  test("TlaCli.parse - generate produces TlaCli.Generate with Path file (not String)") {
    val action = parseOk("generate", "handler.bosatsu")
    action match {
      case g: TlaCli.Generate =>
        // TlaCli.Generate.file is a java.nio.file.Path
        assert(g.file.isInstanceOf[Path])
      case other =>
        fail(s"Expected Generate, got $other")
    }
  }

  test("TlaCli.parse - check produces TlaCli.Check with Path file (not String)") {
    val action = parseOk("check", "spec.tla")
    action match {
      case c: TlaCli.Check =>
        assert(c.file.isInstanceOf[Path])
      case other =>
        fail(s"Expected Check, got $other")
    }
  }

  test("TlaCli.parse - race produces TlaCli.Race with Path file (not String)") {
    val action = parseOk("race", "handler.bosatsu")
    action match {
      case r: TlaCli.Race =>
        assert(r.file.isInstanceOf[Path])
      case other =>
        fail(s"Expected Race, got $other")
    }
  }

  // ==========================================================================
  // TlcRunner.run - verify TlcOptions are threaded through
  // ==========================================================================

  test("TlcRunner.run - skipped result has expected structure") {
    // If TLC is not available, run() should return a skipped result
    val result = TlcRunner.run(Paths.get("/tmp/noexist.tla"), TlcOptions())
    if (result.skipped) {
      assert(result.skipReason.isDefined, "Skipped result should have a skipReason")
      assertEquals(result.invariantViolation, false)
      assertEquals(result.deadlock, false)
    }
    // If TLC happens to be available, we just check the result has valid structure
    assert(result.isInstanceOf[TlcResult])
  }

  // ==========================================================================
  // Ensure no cross-contamination between subcommands
  // ==========================================================================

  test("TlaCli.parse - check does not accept --instances") {
    parseErr("check", "--instances", "3", "spec.tla")
  }

  test("TlaCli.parse - check does not accept --output") {
    parseErr("check", "--output", "out.tla", "spec.tla")
  }

  test("TlaCli.parse - check does not accept --invariant") {
    parseErr("check", "--invariant", "x > 0", "spec.tla")
  }

  test("TlaCli.parse - generate does not accept --workers") {
    parseErr("generate", "--workers", "4", "handler.bosatsu")
  }

  test("TlaCli.parse - generate does not accept --depth") {
    parseErr("generate", "--depth", "100", "handler.bosatsu")
  }

  test("TlaCli.parse - generate does not accept --timeout") {
    parseErr("generate", "--timeout", "5000", "handler.bosatsu")
  }

  test("TlaCli.parse - race does not accept --workers") {
    parseErr("race", "--workers", "4", "handler.bosatsu")
  }

  test("TlaCli.parse - race does not accept --depth") {
    parseErr("race", "--depth", "100", "handler.bosatsu")
  }

  test("TlaCli.parse - race does not accept --timeout") {
    parseErr("race", "--timeout", "5000", "handler.bosatsu")
  }

  test("TlaCli.parse - race does not accept --output") {
    parseErr("race", "--output", "out.tla", "handler.bosatsu")
  }
}
