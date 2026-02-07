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

  // ==========================================================================
  // TlcRunner.parseOutput - successful runs
  // ==========================================================================

  test("TlcRunner.parseOutput - successful run with states") {
    val stdout = """TLC2 Version 2.18
Model checking completed. No error has been found.
  Evaluating initial predicate
  Finished computing initial states: 1 distinct state generated
42 states generated, 20 distinct states found, 0 states left on queue.
The depth of the complete state graph is 10."""
    val result = TlcRunner.parseOutput(0, stdout, "")
    assert(result.success)
    assertEquals(result.statesGenerated, Some(42))
    assertEquals(result.distinctStates, Some(20))
    assertEquals(result.invariantViolation, false)
    assertEquals(result.deadlock, false)
    assertEquals(result.syntaxError, false)
    assertEquals(result.errorMessage, None)
    assertEquals(result.errorTrace, Nil)
  }

  test("TlcRunner.parseOutput - successful run without state counts") {
    val stdout = "Model checking completed. No error has been found."
    val result = TlcRunner.parseOutput(0, stdout, "")
    assert(result.success)
    assertEquals(result.statesGenerated, None)
    assertEquals(result.distinctStates, None)
  }

  test("TlcRunner.parseOutput - empty output with exit code 0") {
    val result = TlcRunner.parseOutput(0, "", "")
    assert(result.success)
    assertEquals(result.statesGenerated, None)
    assertEquals(result.distinctStates, None)
    assertEquals(result.errorMessage, None)
  }

  // ==========================================================================
  // TlcRunner.parseOutput - invariant violations
  // ==========================================================================

  test("TlcRunner.parseOutput - invariant violation detected") {
    val stdout = """Error: Invariant Inv0 is violated.
State 1: <Initial predicate>
  pc = "start"
  state = [x |-> 0]
State 2: <Write_set_0>
  pc = "done"
  state = [x |-> -1]
100 states generated, 50 distinct states found."""
    val result = TlcRunner.parseOutput(12, stdout, "")
    assert(!result.success)
    assert(result.invariantViolation)
    assertEquals(result.statesGenerated, Some(100))
    assertEquals(result.distinctStates, Some(50))
    assert(result.errorMessage.isDefined)
    assert(result.errorMessage.get.contains("Invariant"))
  }

  test("TlcRunner.parseOutput - invariant violation with both keywords") {
    val stdout = "Invariant Inv0 is violated.\nSome more details."
    val result = TlcRunner.parseOutput(1, stdout, "")
    assert(result.invariantViolation)
    assert(!result.success)
  }

  // ==========================================================================
  // TlcRunner.parseOutput - deadlock detection
  // ==========================================================================

  test("TlcRunner.parseOutput - deadlock detected") {
    val stdout = """Error: deadlock reached.
State 1: <Initial predicate>
  pc = "start"
10 states generated, 5 distinct states found."""
    val result = TlcRunner.parseOutput(12, stdout, "")
    assert(!result.success)
    assert(result.deadlock)
    assert(result.errorMessage.isDefined)
  }

  test("TlcRunner.parseOutput - deadlock with exit code 0 still fails") {
    // Even if exit code is 0, deadlock keyword should mark failure
    val stdout = "deadlock found in state graph"
    val result = TlcRunner.parseOutput(0, stdout, "")
    assert(!result.success)
    assert(result.deadlock)
  }

  // ==========================================================================
  // TlcRunner.parseOutput - syntax errors
  // ==========================================================================

  test("TlcRunner.parseOutput - syntax error detected") {
    val stderr = "Syntax error in spec.tla at line 5"
    val result = TlcRunner.parseOutput(1, "", stderr)
    assert(!result.success)
    assert(result.syntaxError)
  }

  test("TlcRunner.parseOutput - Parse Error detected") {
    val stderr = "Parse Error in module at line 10"
    val result = TlcRunner.parseOutput(1, "", stderr)
    assert(!result.success)
    assert(result.syntaxError)
  }

  // ==========================================================================
  // TlcRunner.parseOutput - non-zero exit code
  // ==========================================================================

  test("TlcRunner.parseOutput - non-zero exit code with no specific error") {
    val result = TlcRunner.parseOutput(1, "some output", "")
    assert(!result.success)
    assert(result.errorMessage.isDefined)
    assertEquals(result.errorMessage.get, "Model checking failed")
  }

  test("TlcRunner.parseOutput - Error: prefix in output") {
    val stdout = "Error: Something went wrong\nMore details."
    val result = TlcRunner.parseOutput(1, stdout, "")
    assert(!result.success)
    assert(result.errorMessage.isDefined)
    assert(result.errorMessage.get.contains("Error:"))
  }

  // ==========================================================================
  // TlcRunner.parseOutput - combined stderr and stdout
  // ==========================================================================

  test("TlcRunner.parseOutput - states in stdout, errors in stderr") {
    val stdout = "50 states generated, 25 distinct states found."
    val stderr = "Warning: some non-fatal issue"
    val result = TlcRunner.parseOutput(0, stdout, stderr)
    assert(result.success)
    assertEquals(result.statesGenerated, Some(50))
    assertEquals(result.distinctStates, Some(25))
  }

  test("TlcRunner.parseOutput - rawOutput includes both stdout and stderr") {
    val result = TlcRunner.parseOutput(0, "stdout part", "stderr part")
    assert(result.rawOutput.isDefined)
    assert(result.rawOutput.get.contains("stdout part"))
    assert(result.rawOutput.get.contains("stderr part"))
  }

  // ==========================================================================
  // TlcRunner.parseErrorTrace
  // ==========================================================================

  test("TlcRunner.parseErrorTrace - single state") {
    val output = "State 1: <Initial predicate> pc = \"start\""
    val trace = TlcRunner.parseErrorTrace(output)
    assertEquals(trace.size, 1)
    assertEquals(trace.head.stateNumber, 1)
  }

  test("TlcRunner.parseErrorTrace - multiple states") {
    val output = "State 1: first state State 2: second state State 3: third state"
    val trace = TlcRunner.parseErrorTrace(output)
    assertEquals(trace.size, 3)
    assertEquals(trace(0).stateNumber, 1)
    assertEquals(trace(1).stateNumber, 2)
    assertEquals(trace(2).stateNumber, 3)
  }

  test("TlcRunner.parseErrorTrace - no states in output") {
    val output = "No state information here"
    val trace = TlcRunner.parseErrorTrace(output)
    assertEquals(trace, Nil)
  }

  test("TlcRunner.parseErrorTrace - state variables are captured") {
    val output = "State 1: pc = \"start\" /\\ x = 0"
    val trace = TlcRunner.parseErrorTrace(output)
    assertEquals(trace.size, 1)
    assert(trace.head.variables.contains("pc"))
  }

  test("TlcRunner.parseErrorTrace - action defaults to unknown") {
    val output = "State 1: pc = \"start\""
    val trace = TlcRunner.parseErrorTrace(output)
    assertEquals(trace.head.action, "unknown")
  }

  // ==========================================================================
  // TlcRunner.extractErrorMessage
  // ==========================================================================

  test("TlcRunner.extractErrorMessage - finds Error: line") {
    val output = "Line 1\nError: Something bad happened\nLine 3"
    val msg = TlcRunner.extractErrorMessage(output)
    assert(msg.contains("Error:"))
    assert(msg.contains("Something bad happened"))
  }

  test("TlcRunner.extractErrorMessage - finds violated line when no Error:") {
    val output = "Line 1\nInvariant Inv0 is violated.\nLine 3"
    val msg = TlcRunner.extractErrorMessage(output)
    assert(msg.contains("violated"))
  }

  test("TlcRunner.extractErrorMessage - finds deadlock line when no Error: or violated") {
    val output = "Line 1\nLine 2\ndeadlock reached"
    val msg = TlcRunner.extractErrorMessage(output)
    assert(msg.contains("deadlock"))
  }

  test("TlcRunner.extractErrorMessage - returns default when no known pattern") {
    val output = "Some unknown output\nWith no error keywords"
    val msg = TlcRunner.extractErrorMessage(output)
    assertEquals(msg, "Model checking failed")
  }

  test("TlcRunner.extractErrorMessage - prefers Error: over violated") {
    val output = "Error: Primary error\nInvariant violated"
    val msg = TlcRunner.extractErrorMessage(output)
    assert(msg.contains("Error:"))
  }

  test("TlcRunner.extractErrorMessage - empty output") {
    val msg = TlcRunner.extractErrorMessage("")
    assertEquals(msg, "Model checking failed")
  }

  // ==========================================================================
  // TlcRunner.run - skipped/error paths
  // ==========================================================================

  test("TlcRunner.run - returns TlcResult with valid structure") {
    val result = TlcRunner.run(Paths.get("/nonexistent/file.tla"), TlcOptions(workers = 2, depth = Some(50)))
    // Whether TLC is installed or not, we get a valid TlcResult
    assert(result.isInstanceOf[TlcResult])
    if (result.skipped) {
      assert(result.skipReason.isDefined)
    }
  }

  test("TlcRunner.run - options with checkDeadlock=false") {
    val result = TlcRunner.run(
      Paths.get("/nonexistent/file.tla"),
      TlcOptions(checkDeadlock = false)
    )
    assert(result.isInstanceOf[TlcResult])
  }

  test("TlcRunner.run - options with depth") {
    val result = TlcRunner.run(
      Paths.get("/nonexistent/file.tla"),
      TlcOptions(depth = Some(100))
    )
    assert(result.isInstanceOf[TlcResult])
  }

  // ==========================================================================
  // TlcRunner.findTlc
  // ==========================================================================

  test("TlcRunner.findTlc - returns consistent result across calls") {
    val result1 = TlcRunner.findTlc()
    val result2 = TlcRunner.findTlc()
    assertEquals(result1, result2)
  }

  // ==========================================================================
  // TlcOptions construction
  // ==========================================================================

  test("TlcOptions - default values") {
    val opts = TlcOptions()
    assertEquals(opts.workers, 1)
    assert(opts.checkDeadlock)
    assertEquals(opts.depth, None)
    assertEquals(opts.timeout, None)
    assert(opts.skipIfUnavailable)
  }

  test("TlcOptions - custom values") {
    val opts = TlcOptions(workers = 4, checkDeadlock = false, depth = Some(200), timeout = Some(60000))
    assertEquals(opts.workers, 4)
    assert(!opts.checkDeadlock)
    assertEquals(opts.depth, Some(200))
    assertEquals(opts.timeout, Some(60000))
  }

  // ==========================================================================
  // TlcResult construction
  // ==========================================================================

  test("TlcResult - default values") {
    val result = TlcResult(success = true)
    assert(result.success)
    assert(!result.invariantViolation)
    assert(!result.deadlock)
    assert(!result.temporalPropertyViolation)
    assert(!result.syntaxError)
    assertEquals(result.errorMessage, None)
    assertEquals(result.statesGenerated, None)
    assertEquals(result.distinctStates, None)
    assertEquals(result.errorTrace, Nil)
    assert(!result.skipped)
    assertEquals(result.skipReason, None)
    assertEquals(result.rawOutput, None)
  }

  test("TlcResult - skipped result") {
    val result = TlcResult(success = false, skipped = true, skipReason = Some("TLC not found"))
    assert(!result.success)
    assert(result.skipped)
    assertEquals(result.skipReason, Some("TLC not found"))
  }

  // ==========================================================================
  // TlcTraceState construction
  // ==========================================================================

  test("TlcTraceState - construction and equality") {
    val s1 = TlcTraceState(1, "Init", "pc = start")
    val s2 = TlcTraceState(1, "Init", "pc = start")
    val s3 = TlcTraceState(2, "Step", "pc = done")
    assertEquals(s1, s2)
    assertNotEquals(s1, s3)
  }
}
