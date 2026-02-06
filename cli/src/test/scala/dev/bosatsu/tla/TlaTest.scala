package dev.bosatsu.tla

import dev.bosatsu.service.{ServiceAnalysis, ServiceOperation, OperationKind, BatchGroup}
import io.circe.syntax._
import io.circe.parser.decode

class TlaTest extends munit.FunSuite {

  // ==========================================================================
  // TlaJson tests - encoder/decoder roundtrips
  // ==========================================================================
  import TlaJson.given

  test("TlaJson - TlaInt roundtrip") {
    val v: TlaValue = TlaValue.TlaInt(42)
    val json = v.asJson
    val decoded = decode[TlaValue](json.noSpaces)
    assertEquals(decoded, Right(v))
  }

  test("TlaJson - TlaString roundtrip") {
    val v: TlaValue = TlaValue.TlaString("hello world")
    val json = v.asJson
    val decoded = decode[TlaValue](json.noSpaces)
    assertEquals(decoded, Right(v))
  }

  test("TlaJson - TlaBool true roundtrip") {
    val v: TlaValue = TlaValue.TlaBool(true)
    val json = v.asJson
    val decoded = decode[TlaValue](json.noSpaces)
    assertEquals(decoded, Right(v))
  }

  test("TlaJson - TlaBool false roundtrip") {
    val v: TlaValue = TlaValue.TlaBool(false)
    val json = v.asJson
    val decoded = decode[TlaValue](json.noSpaces)
    assertEquals(decoded, Right(v))
  }

  test("TlaJson - TlaSeq roundtrip") {
    val v: TlaValue = TlaValue.TlaSeq(List(TlaValue.TlaInt(1), TlaValue.TlaInt(2), TlaValue.TlaInt(3)))
    val json = v.asJson
    val decoded = decode[TlaValue](json.noSpaces)
    assertEquals(decoded, Right(v))
  }

  test("TlaJson - TlaSet roundtrip") {
    val v: TlaValue = TlaValue.TlaSet(Set(TlaValue.TlaInt(1), TlaValue.TlaInt(2)))
    val json = v.asJson
    val decoded = decode[TlaValue](json.noSpaces)
    assertEquals(decoded, Right(v))
  }

  test("TlaJson - TlaRecord roundtrip") {
    val v: TlaValue = TlaValue.TlaRecord(Map("x" -> TlaValue.TlaInt(1), "y" -> TlaValue.TlaString("test")))
    val json = v.asJson
    val decoded = decode[TlaValue](json.noSpaces)
    assertEquals(decoded, Right(v))
  }

  test("TlaJson - TlaFunction roundtrip") {
    val v: TlaValue = TlaValue.TlaFunction("x \\in 1..10", "x * 2")
    val json = v.asJson
    val decoded = decode[TlaValue](json.noSpaces)
    assertEquals(decoded, Right(v))
  }

  test("TlaJson - nested TlaValue roundtrip") {
    val v: TlaValue = TlaValue.TlaRecord(Map(
      "list" -> TlaValue.TlaSeq(List(TlaValue.TlaInt(1), TlaValue.TlaInt(2))),
      "set" -> TlaValue.TlaSet(Set(TlaValue.TlaBool(true))),
      "nested" -> TlaValue.TlaRecord(Map("inner" -> TlaValue.TlaString("deep")))
    ))
    val json = v.asJson
    val decoded = decode[TlaValue](json.noSpaces)
    assertEquals(decoded, Right(v))
  }

  test("TlaJson - TlaValue decode error for unknown type") {
    val json = """{"type":"unknown","value":123}"""
    val decoded = decode[TlaValue](json)
    assert(decoded.isLeft)
  }

  test("TlaJson - TlaOptions roundtrip") {
    val opts = TlaOptions(
      initialState = Map("x" -> TlaValue.TlaInt(0), "y" -> TlaValue.TlaString("init")),
      invariant = Some("x >= 0"),
      stateVariable = "myState",
      checkDeadlock = false
    )
    val json = opts.asJson
    val decoded = decode[TlaOptions](json.noSpaces)
    assertEquals(decoded, Right(opts))
  }

  test("TlaJson - TlaOptions with defaults roundtrip") {
    val opts = TlaOptions()
    val json = opts.asJson
    val decoded = decode[TlaOptions](json.noSpaces)
    assertEquals(decoded.map(_.stateVariable), Right("state"))
    assertEquals(decoded.map(_.checkDeadlock), Right(true))
  }

  test("TlaJson - TlaAction roundtrip") {
    val action = TlaAction("DoSomething", "x > 0", "x' = x - 1", "running", "done")
    val json = action.asJson
    val decoded = decode[TlaAction](json.noSpaces)
    assertEquals(decoded, Right(action))
  }

  test("TlaJson - TlaSpec encode") {
    val spec = TlaSpec(
      moduleName = "TestSpec",
      extends_ = List("Integers", "Sequences"),
      variables = List("x", "y"),
      init = "Init == x = 0 /\\ y = 0",
      actions = List(TlaAction("Inc", "TRUE", "x' = x + 1", "start", "end")),
      next = "Next == Inc",
      spec = "Spec == Init /\\ [][Next]_vars",
      invariants = List("x >= 0")
    )
    val json = spec.asJson
    assert(json.hcursor.downField("moduleName").as[String] == Right("TestSpec"))
    assert(json.hcursor.downField("extends").as[List[String]] == Right(List("Integers", "Sequences")))
  }

  test("TlaJson - TlcTraceState roundtrip") {
    val state = TlcTraceState(1, "Init", "x = 0 /\\ y = 1")
    val json = state.asJson
    val decoded = decode[TlcTraceState](json.noSpaces)
    assertEquals(decoded, Right(state))
  }

  test("TlaJson - TlcResult roundtrip - success") {
    val result = TlcResult(
      success = true,
      statesGenerated = Some(100),
      distinctStates = Some(50)
    )
    val json = result.asJson
    val decoded = decode[TlcResult](json.noSpaces)
    assertEquals(decoded.map(_.success), Right(true))
    assertEquals(decoded.map(_.statesGenerated), Right(Some(100)))
  }

  test("TlaJson - TlcResult roundtrip - failure with trace") {
    val trace = List(
      TlcTraceState(1, "Init", "x = 0"),
      TlcTraceState(2, "Inc", "x = 1"),
      TlcTraceState(3, "Inc", "x = 2")
    )
    val result = TlcResult(
      success = false,
      invariantViolation = true,
      errorMessage = Some("Invariant violated"),
      errorTrace = trace
    )
    val json = result.asJson
    val decoded = decode[TlcResult](json.noSpaces)
    assertEquals(decoded.map(_.success), Right(false))
    assertEquals(decoded.map(_.invariantViolation), Right(true))
    assertEquals(decoded.map(_.errorTrace.length), Right(3))
  }

  test("TlaJson - TlcResult roundtrip - deadlock") {
    val result = TlcResult(success = false, deadlock = true)
    val json = result.asJson
    val decoded = decode[TlcResult](json.noSpaces)
    assertEquals(decoded.map(_.deadlock), Right(true))
  }

  test("TlaJson - TlcResult roundtrip - skipped") {
    val result = TlcResult(success = true, skipped = true, skipReason = Some("TLC not available"))
    val json = result.asJson
    val decoded = decode[TlcResult](json.noSpaces)
    assertEquals(decoded.map(_.skipped), Right(true))
    assertEquals(decoded.map(_.skipReason), Right(Some("TLC not available")))
  }

  test("TlaJson - TlcOptions roundtrip") {
    val opts = TlcOptions(
      workers = 4,
      checkDeadlock = false,
      depth = Some(100),
      timeout = Some(60),
      skipIfUnavailable = false
    )
    val json = opts.asJson
    val decoded = decode[TlcOptions](json.noSpaces)
    assertEquals(decoded.map(_.workers), Right(4))
    assertEquals(decoded.map(_.checkDeadlock), Right(false))
    assertEquals(decoded.map(_.depth), Right(Some(100)))
  }

  test("TlaJson - TlcOptions with defaults roundtrip") {
    val opts = TlcOptions()
    val json = opts.asJson
    val decoded = decode[TlcOptions](json.noSpaces)
    assertEquals(decoded.map(_.workers), Right(1))
    assertEquals(decoded.map(_.skipIfUnavailable), Right(true))
  }

  test("TlaJson - RaceViolation encode") {
    val violation = RaceViolation(
      trace = List("Init: x = 0", "Step: x = -1"),
      finalState = Map("x" -> TlaValue.TlaInt(-1))
    )
    val json = violation.asJson
    assert(json.hcursor.downField("trace").as[List[String]].isRight)
    assert(json.hcursor.downField("finalState").as[Map[String, TlaValue]].isRight)
  }

  test("TlaJson - TlaGenResult encode") {
    val result = TlaGenResult(
      file = "test.bosatsu",
      tlaSpec = "---- MODULE test ----\n====",
      moduleName = "test",
      instances = 2,
      invariant = Some("x >= 0"),
      tlcResult = Some(TlcResult(success = true))
    )
    val json = result.asJson
    assert(json.hcursor.downField("file").as[String] == Right("test.bosatsu"))
    assert(json.hcursor.downField("instances").as[Int] == Right(2))
  }

  test("TlaJson - RaceAnalysisResult encode") {
    val result = RaceAnalysisResult(
      file = "handler.bosatsu",
      handlerName = "update",
      instances = 3,
      totalInterleavings = 100,
      violatingInterleavings = 5,
      invariant = "balance >= 0",
      exampleViolation = None
    )
    val json = result.asJson
    assert(json.hcursor.downField("handlerName").as[String] == Right("update"))
    assert(json.hcursor.downField("violatingInterleavings").as[Int] == Right(5))
  }

  // ==========================================================================
  // TlaValue tests
  // ==========================================================================

  test("TlaValue.TlaInt - render") {
    val int = TlaValue.TlaInt(42)
    assertEquals(int.render, "42")
  }

  test("TlaValue.TlaString - render") {
    val str = TlaValue.TlaString("hello")
    assertEquals(str.render, "\"hello\"")
  }

  test("TlaValue.TlaBool - render TRUE") {
    val bool = TlaValue.TlaBool(true)
    assertEquals(bool.render, "TRUE")
  }

  test("TlaValue.TlaBool - render FALSE") {
    val bool = TlaValue.TlaBool(false)
    assertEquals(bool.render, "FALSE")
  }

  test("TlaValue.TlaSeq - render") {
    val seq = TlaValue.TlaSeq(List(TlaValue.TlaInt(1), TlaValue.TlaInt(2)))
    assertEquals(seq.render, "<<1, 2>>")
  }

  test("TlaValue.TlaSet - render") {
    val set = TlaValue.TlaSet(Set(TlaValue.TlaInt(1), TlaValue.TlaInt(2)))
    assert(set.render.startsWith("{"))
    assert(set.render.endsWith("}"))
    assert(set.render.contains("1"))
    assert(set.render.contains("2"))
  }

  test("TlaValue.TlaRecord - render") {
    val record = TlaValue.TlaRecord(Map("x" -> TlaValue.TlaInt(1)))
    assertEquals(record.render, "[x |-> 1]")
  }

  test("TlaValue.TlaFunction - render") {
    val fn = TlaValue.TlaFunction("x \\in 1..5", "x * 2")
    assertEquals(fn.render, "[x \\in 1..5 |-> x * 2]")
  }

  test("TlaValue.fromAny - Int") {
    val result = TlaValue.fromAny(42)
    assertEquals(result.render, "42")
  }

  test("TlaValue.fromAny - Long") {
    val result = TlaValue.fromAny(100L)
    assertEquals(result.render, "100")
  }

  test("TlaValue.fromAny - String") {
    val result = TlaValue.fromAny("test")
    assertEquals(result.render, "\"test\"")
  }

  test("TlaValue.fromAny - Boolean") {
    val result = TlaValue.fromAny(true)
    assertEquals(result.render, "TRUE")
  }

  test("TlaValue.fromAny - Seq") {
    val result = TlaValue.fromAny(Seq(1, 2, 3))
    assertEquals(result.render, "<<1, 2, 3>>")
  }

  test("TlaValue.fromAny - Set") {
    val result = TlaValue.fromAny(Set(1))
    assertEquals(result.render, "{1}")
  }

  test("TlaValue.fromAny - Map") {
    val result = TlaValue.fromAny(Map("x" -> 1, "y" -> 2))
    assert(result.isInstanceOf[TlaValue.TlaRecord])
    val record = result.asInstanceOf[TlaValue.TlaRecord]
    assertEquals(record.fields.size, 2)
  }

  test("TlaValue.fromAny - unknown type falls back to String") {
    case class Custom(x: Int)
    val result = TlaValue.fromAny(Custom(42))
    assertEquals(result.render, "\"Custom(42)\"")
  }

  // ==========================================================================
  // TlaCommand tests
  // ==========================================================================

  test("TlaCommand.Generate - default values") {
    val cmd = TlaCommand.Generate("test.bosatsu")
    assertEquals(cmd.file, "test.bosatsu")
    assertEquals(cmd.output, None)
    assertEquals(cmd.instances, 1)
    assertEquals(cmd.invariant, None)
  }

  test("TlaCommand.Generate - with all options") {
    val cmd = TlaCommand.Generate("test.bosatsu", Some("out.tla"), 3, Some("x >= 0"))
    assertEquals(cmd.file, "test.bosatsu")
    assertEquals(cmd.output, Some("out.tla"))
    assertEquals(cmd.instances, 3)
    assertEquals(cmd.invariant, Some("x >= 0"))
  }

  test("TlaCommand.Check - default values") {
    val cmd = TlaCommand.Check("test.tla")
    assertEquals(cmd.file, "test.tla")
    assertEquals(cmd.workers, 1)
    assertEquals(cmd.depth, None)
    assertEquals(cmd.timeout, None)
  }

  test("TlaCommand.Check - with all options") {
    val cmd = TlaCommand.Check("test.tla", 4, Some(100), Some(30000))
    assertEquals(cmd.workers, 4)
    assertEquals(cmd.depth, Some(100))
    assertEquals(cmd.timeout, Some(30000))
  }

  test("TlaCommand.Race - default values") {
    val cmd = TlaCommand.Race("handler.bosatsu")
    assertEquals(cmd.file, "handler.bosatsu")
    assertEquals(cmd.instances, 2)
    assertEquals(cmd.invariant, None)
  }

  test("TlaCommand.Race - with all options") {
    val cmd = TlaCommand.Race("handler.bosatsu", 5, Some("balance >= 0"))
    assertEquals(cmd.instances, 5)
    assertEquals(cmd.invariant, Some("balance >= 0"))
  }

  test("TlaCommand - equality") {
    val cmd1: TlaCommand = TlaCommand.Generate("a.bosatsu")
    val cmd2: TlaCommand = TlaCommand.Generate("a.bosatsu")
    val cmd3: TlaCommand = TlaCommand.Check("a.bosatsu")

    assertEquals(cmd1, cmd2)
    assertNotEquals(cmd1, cmd3)
  }

  test("TlaGenResult - construction") {
    val result = TlaGenResult(
      file = "test.bosatsu",
      tlaSpec = "---- MODULE Test ----\n====",
      moduleName = "Test",
      instances = 2,
      invariant = Some("x >= 0"),
      tlcResult = Some(TlcResult(success = true))
    )
    assertEquals(result.file, "test.bosatsu")
    assertEquals(result.moduleName, "Test")
    assertEquals(result.instances, 2)
    assert(result.tlcResult.isDefined)
  }

  test("RaceAnalysisResult - construction") {
    val result = RaceAnalysisResult(
      file = "handler.bosatsu",
      handlerName = "update",
      instances = 3,
      totalInterleavings = 100,
      violatingInterleavings = 5,
      invariant = "balance >= 0",
      exampleViolation = Some(RaceViolation(List("step1", "step2"), Map("x" -> TlaValue.TlaInt(-1))))
    )
    assertEquals(result.handlerName, "update")
    assertEquals(result.violatingInterleavings, 5)
    assert(result.exampleViolation.isDefined)
  }

  test("TlcResult - with all error types") {
    val syntaxErr = TlcResult(success = false, syntaxError = true, errorMessage = Some("parse error"))
    assertEquals(syntaxErr.syntaxError, true)
    assertEquals(syntaxErr.errorMessage, Some("parse error"))

    val invariantErr = TlcResult(success = false, invariantViolation = true)
    assertEquals(invariantErr.invariantViolation, true)

    val deadlockErr = TlcResult(success = false, deadlock = true)
    assertEquals(deadlockErr.deadlock, true)

    val temporalErr = TlcResult(success = false, temporalPropertyViolation = true)
    assertEquals(temporalErr.temporalPropertyViolation, true)

    val skipped = TlcResult(success = true, skipped = true, skipReason = Some("TLC not available"))
    assertEquals(skipped.skipped, true)
    assertEquals(skipped.skipReason, Some("TLC not available"))
  }

  test("TlcResult - with raw output") {
    val result = TlcResult(
      success = true,
      statesGenerated = Some(1000),
      distinctStates = Some(500),
      rawOutput = Some("TLC output here...")
    )
    assertEquals(result.rawOutput, Some("TLC output here..."))
    assertEquals(result.statesGenerated, Some(1000))
    assertEquals(result.distinctStates, Some(500))
  }

  test("TlaOptions - default values") {
    val opts = TlaOptions()
    assertEquals(opts.stateVariable, "state")
    assertEquals(opts.checkDeadlock, true)
    assertEquals(opts.invariant, None)
    assertEquals(opts.initialState, Map.empty)
  }

  test("TlaAction - default multiInstance is false") {
    val action = TlaAction("Test", "TRUE", "UNCHANGED state", "start", "done")
    assertEquals(action.multiInstance, false)
  }

  test("TlaAction - with multiInstance true") {
    val action = TlaAction("Test", "TRUE", "UNCHANGED state", "start", "done", multiInstance = true)
    assertEquals(action.multiInstance, true)
  }

  test("TlaGen.generate - empty operations") {
    val analysis = ServiceAnalysis(
      handlerName = "test_handler",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions()
    val spec = TlaGen.generate(analysis, options, 1)

    assertEquals(spec.moduleName, "test_handler")
    assert(spec.actions.nonEmpty)
    assertEquals(spec.actions.head.name, "Complete")
  }

  test("TlaGen.generate - with read operations") {
    val analysis = ServiceAnalysis(
      handlerName = "reader",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions()
    val spec = TlaGen.generate(analysis, options, 1)

    // Should have read action + Complete action
    assert(spec.actions.exists(_.name.startsWith("Read_")))
    assert(spec.actions.exists(_.name == "Complete"))
  }

  test("TlaGen.generate - with write operations") {
    val analysis = ServiceAnalysis(
      handlerName = "writer",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions()
    val spec = TlaGen.generate(analysis, options, 1)

    assert(spec.actions.exists(_.name.startsWith("Write_")))
    // Last write transitions to done
    val writeAction = spec.actions.find(_.name.startsWith("Write_")).get
    assertEquals(writeAction.pcTo, "done")
  }

  test("TlaGen.generate - multi-instance") {
    val analysis = ServiceAnalysis(
      handlerName = "concurrent_handler",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions()
    val spec = TlaGen.generate(analysis, options, instances = 2)

    // Actions should have multiInstance=true
    assert(spec.actions.forall(_.multiInstance == true))
    assert(spec.variables.contains("instance"))
  }

  test("TlaGen.generateRaceSpec") {
    val analysis = ServiceAnalysis(
      handlerName = "race_test",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 2,
      batchedQueries = 0,
      queriesSaved = 0
    )

    val spec = TlaGen.generateRaceSpec(analysis, instances = 2, invariant = "state[\"x\"] >= 0")

    assertEquals(spec.moduleName, "race_test")
    assert(spec.invariants.nonEmpty)
    assertEquals(spec.invariants.head, "state[\"x\"] >= 0")
  }

  test("TlaSpec.render - single instance") {
    val spec = TlaSpec(
      moduleName = "TestModule",
      extends_ = List("Integers"),
      variables = List("state", "pc"),
      init = "Init == state = 0 /\\ pc = \"start\"",
      actions = List(
        TlaAction("Step", "TRUE", "state' = state + 1", "start", "done")
      ),
      next = "Next == Step \\/ Done",
      spec = "Spec == Init /\\ [][Next]_vars"
    )

    val rendered = spec.render
    assert(rendered.contains("---- MODULE TestModule ----"))
    assert(rendered.contains("EXTENDS Integers"))
    assert(rendered.contains("VARIABLES state, pc"))
    assert(rendered.contains("Step =="))
    assert(rendered.contains("pc = \"start\""))
    assert(rendered.contains("pc' = \"done\""))
    assert(rendered.contains("Done =="))
    assert(rendered.contains("===="))
  }

  test("TlaSpec.render - multi-instance with parameterized actions") {
    val spec = TlaSpec(
      moduleName = "MultiTest",
      extends_ = List("Integers"),
      variables = List("state", "pc", "instance"),
      init = "Init == state = 0 /\\ pc = [i \\in 1..2 |-> \"start\"]",
      actions = List(
        TlaAction("Step", "TRUE", "state' = state + 1", "start", "done", multiInstance = true)
      ),
      next = "Next == \\E self \\in 1..2: Step(self) \\/ Done",
      spec = "Spec == Init /\\ [][Next]_vars"
    )

    val rendered = spec.render
    assert(rendered.contains("Step(self) =="))
    assert(rendered.contains("pc[self] = \"start\""))
    assert(rendered.contains("pc' = [pc EXCEPT ![self] = \"done\"]"))
  }

  test("TlaGen.generateConfig") {
    val spec = TlaSpec(
      moduleName = "TestModule",
      extends_ = List("Integers"),
      variables = List("state", "pc"),
      init = "Init == TRUE",
      actions = Nil,
      next = "Next == TRUE",
      spec = "Spec == Init /\\ [][Next]_vars",
      invariants = List("state >= 0", "pc \\in {\"start\", \"done\"}")
    )

    val options = TlcOptions(checkDeadlock = true)
    val config = TlaGen.generateConfig(spec, options)

    assert(config.contains("SPECIFICATION Spec"))
    assert(config.contains("INVARIANT Inv0"))
    assert(config.contains("INVARIANT Inv1"))
    assert(!config.contains("CHECK_DEADLOCK FALSE"))
  }

  test("TlaGen.generateConfig - no deadlock check") {
    val spec = TlaSpec(
      moduleName = "TestModule",
      extends_ = Nil,
      variables = Nil,
      init = "",
      actions = Nil,
      next = "",
      spec = "",
      invariants = Nil
    )

    val options = TlcOptions(checkDeadlock = false)
    val config = TlaGen.generateConfig(spec, options)

    assert(config.contains("CHECK_DEADLOCK FALSE"))
  }

  test("TlcResult - default values") {
    val result = TlcResult(success = true)
    assertEquals(result.invariantViolation, false)
    assertEquals(result.deadlock, false)
    assertEquals(result.skipped, false)
  }

  test("TlcOptions - default values") {
    val options = TlcOptions()
    assertEquals(options.workers, 1)
    assertEquals(options.checkDeadlock, true)
    assertEquals(options.depth, None)
    assertEquals(options.skipIfUnavailable, true)
  }

  test("TlcTraceState") {
    val state = TlcTraceState(1, "Init", "x = 0")
    assertEquals(state.stateNumber, 1)
    assertEquals(state.action, "Init")
    assertEquals(state.variables, "x = 0")
  }

  // ==========================================================================
  // TlaGen additional tests
  // ==========================================================================

  test("TlaGen.generate - with unknown operations") {
    val analysis = ServiceAnalysis(
      handlerName = "unknown_ops",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("API", "call", OperationKind.Unknown, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions()
    val spec = TlaGen.generate(analysis, options, 1)

    // Unknown operations should still generate an action
    assert(spec.actions.nonEmpty)
  }

  test("TlaGen.generate - with mixed read/write operations") {
    val analysis = ServiceAnalysis(
      handlerName = "mixed",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 3,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions()
    val spec = TlaGen.generate(analysis, options, 1)

    // Should have multiple actions
    assert(spec.actions.size >= 3)
  }

  test("TlaGen.generate - with custom invariant") {
    val analysis = ServiceAnalysis(
      handlerName = "inv_test",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions(invariant = Some("state[\"count\"] >= 0"))
    val spec = TlaGen.generate(analysis, options, 1)

    assert(spec.invariants.contains("state[\"count\"] >= 0"))
  }

  test("TlaGen.generate - with initial state") {
    val analysis = ServiceAnalysis(
      handlerName = "init_test",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions(initialState = Map(
      "x" -> TlaValue.TlaInt(0),
      "y" -> TlaValue.TlaString("init")
    ))
    val spec = TlaGen.generate(analysis, options, 1)

    assert(spec.init.contains("x"))
  }

  test("TlaGen.sanitizeName") {
    // Test via generate - names with special chars should be sanitized
    val analysis = ServiceAnalysis(
      handlerName = "test-handler_v2",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions()
    val spec = TlaGen.generate(analysis, options, 1)
    // Module name should be sanitized (hyphens replaced)
    assert(!spec.moduleName.contains("-"))
  }

  test("TlaSpec.render - with invariants") {
    val spec = TlaSpec(
      moduleName = "InvTest",
      extends_ = List("Integers"),
      variables = List("x"),
      init = "Init == x = 0",
      actions = Nil,
      next = "Next == TRUE",
      spec = "Spec == Init /\\ [][Next]_vars",
      invariants = List("x >= 0", "x < 100")
    )

    val rendered = spec.render
    assert(rendered.contains("Inv0 == x >= 0"))
    assert(rendered.contains("Inv1 == x < 100"))
  }

  test("TlaSpec.render - empty extends") {
    val spec = TlaSpec(
      moduleName = "NoExtends",
      extends_ = Nil,
      variables = List("x"),
      init = "Init == x = 0",
      actions = Nil,
      next = "Next == TRUE",
      spec = "Spec == Init"
    )

    val rendered = spec.render
    // Should not have EXTENDS line when empty
    assert(!rendered.contains("EXTENDS"))
  }

  // ==========================================================================
  // TlaProtocol additional tests
  // ==========================================================================

  test("TlaGenResult - equality") {
    val r1 = TlaGenResult("file.bosatsu", "spec", "mod", 1, None, None)
    val r2 = TlaGenResult("file.bosatsu", "spec", "mod", 1, None, None)
    val r3 = TlaGenResult("other.bosatsu", "spec", "mod", 1, None, None)

    assertEquals(r1, r2)
    assertNotEquals(r1, r3)
  }

  test("RaceAnalysisResult - equality") {
    val r1 = RaceAnalysisResult("f", "h", 2, 10, 0, "inv", None)
    val r2 = RaceAnalysisResult("f", "h", 2, 10, 0, "inv", None)
    val r3 = RaceAnalysisResult("f", "h", 2, 10, 1, "inv", None)

    assertEquals(r1, r2)
    assertNotEquals(r1, r3)
  }

  test("RaceViolation - equality") {
    val v1 = RaceViolation(List("Init: x=0"), Map("x" -> TlaValue.TlaInt(-1)))
    val v2 = RaceViolation(List("Init: x=0"), Map("x" -> TlaValue.TlaInt(-1)))
    val v3 = RaceViolation(List("Init: x=0"), Map("x" -> TlaValue.TlaInt(0)))

    assertEquals(v1, v2)
    assertNotEquals(v1, v3)
  }

  test("TlcResult - with all fields") {
    val result = TlcResult(
      success = false,
      invariantViolation = true,
      deadlock = false,
      temporalPropertyViolation = false,
      syntaxError = false,
      errorMessage = Some("Invariant Inv0 violated"),
      statesGenerated = Some(1000),
      distinctStates = Some(500),
      errorTrace = List(TlcTraceState(1, "Init", "x=0"), TlcTraceState(2, "Bad", "x=-1")),
      skipped = false,
      skipReason = None
    )

    assertEquals(result.success, false)
    assertEquals(result.invariantViolation, true)
    assertEquals(result.errorTrace.length, 2)
  }

  test("TlcOptions - with depth and timeout") {
    val opts = TlcOptions(
      workers = 8,
      checkDeadlock = true,
      depth = Some(50),
      timeout = Some(120),
      skipIfUnavailable = false
    )

    assertEquals(opts.workers, 8)
    assertEquals(opts.depth, Some(50))
    assertEquals(opts.timeout, Some(120))
    assertEquals(opts.skipIfUnavailable, false)
  }

  test("TlaValue.fromAny - unknown type returns string") {
    case class Custom(x: Int)
    val result = TlaValue.fromAny(Custom(42))
    assert(result.isInstanceOf[TlaValue.TlaString])
  }

  test("TlaValue.TlaRecord - empty") {
    val record = TlaValue.TlaRecord(Map.empty)
    assertEquals(record.render, "[]")
  }

  test("TlaValue.TlaSeq - empty") {
    val seq = TlaValue.TlaSeq(Nil)
    assertEquals(seq.render, "<<>>")
  }

  test("TlaValue.TlaSet - empty") {
    val set = TlaValue.TlaSet(Set.empty)
    assertEquals(set.render, "{}")
  }

  // ==========================================================================
  // TlaGen.generateConfig - additional coverage
  // ==========================================================================

  test("TlaGen.generateConfig - no invariants produces no INVARIANT lines") {
    val spec = TlaSpec(
      moduleName = "NoInv",
      extends_ = Nil,
      variables = List("state", "pc"),
      init = "Init == TRUE",
      actions = Nil,
      next = "Next == TRUE",
      spec = "Spec == Init /\\ [][Next]_vars",
      invariants = Nil
    )
    val config = TlaGen.generateConfig(spec, TlcOptions(checkDeadlock = true))
    assert(config.contains("SPECIFICATION Spec"))
    assert(!config.contains("INVARIANT"))
  }

  test("TlaGen.generateConfig - multiple invariants produce indexed INVARIANT lines") {
    val spec = TlaSpec(
      moduleName = "MultiInv",
      extends_ = Nil,
      variables = Nil,
      init = "",
      actions = Nil,
      next = "",
      spec = "",
      invariants = List("x >= 0", "y < 100", "x + y < 200")
    )
    val config = TlaGen.generateConfig(spec, TlcOptions())
    assert(config.contains("INVARIANT Inv0"))
    assert(config.contains("INVARIANT Inv1"))
    assert(config.contains("INVARIANT Inv2"))
  }

  test("TlaGen.generateConfig - checkDeadlock true omits CHECK_DEADLOCK FALSE") {
    val spec = TlaSpec("M", Nil, Nil, "", Nil, "", "", Nil)
    val config = TlaGen.generateConfig(spec, TlcOptions(checkDeadlock = true))
    assert(!config.contains("CHECK_DEADLOCK FALSE"))
  }

  test("TlaGen.generateConfig - checkDeadlock false includes CHECK_DEADLOCK FALSE") {
    val spec = TlaSpec("M", Nil, Nil, "", Nil, "", "", Nil)
    val config = TlaGen.generateConfig(spec, TlcOptions(checkDeadlock = false))
    assert(config.contains("CHECK_DEADLOCK FALSE"))
  }

  test("TlaGen.generateConfig - always starts with SPECIFICATION Spec") {
    val spec = TlaSpec("M", Nil, Nil, "", Nil, "", "", Nil)
    val config = TlaGen.generateConfig(spec, TlcOptions())
    assert(config.startsWith("SPECIFICATION Spec"))
  }

  test("TlaGen.generateConfig - TlcOptions workers/depth/timeout do not affect config output") {
    // generateConfig only uses checkDeadlock from TlcOptions and invariants from spec
    val spec = TlaSpec("M", Nil, Nil, "", Nil, "", "", List("inv"))
    val config1 = TlaGen.generateConfig(spec, TlcOptions(workers = 1))
    val config2 = TlaGen.generateConfig(spec, TlcOptions(workers = 8, depth = Some(100), timeout = Some(60)))
    assertEquals(config1, config2)
  }

  // ==========================================================================
  // TlaGen.generate - mixed operations
  // ==========================================================================

  test("TlaGen.generate - read then write chain has correct pc transitions") {
    val analysis = ServiceAnalysis(
      handlerName = "readwrite",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 2,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)

    val readAction = spec.actions.find(_.name.startsWith("Read_")).get
    val writeAction = spec.actions.find(_.name.startsWith("Write_")).get

    // Read starts at "start", write follows after read
    assertEquals(readAction.pcFrom, "start")
    assertEquals(readAction.pcTo, "read_0")
    assertEquals(writeAction.pcFrom, "read_0")
    assertEquals(writeAction.pcTo, "done")
  }

  test("TlaGen.generate - multiple reads then multiple writes") {
    val analysis = ServiceAnalysis(
      handlerName = "multi_rw",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("Cache", "fetch", OperationKind.Read, false, None),
        ServiceOperation("DB", "set", OperationKind.Write, false, None),
        ServiceOperation("Cache", "put", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 4,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)

    val readActions = spec.actions.filter(_.name.startsWith("Read_"))
    val writeActions = spec.actions.filter(_.name.startsWith("Write_"))

    assertEquals(readActions.size, 2)
    assertEquals(writeActions.size, 2)

    // First read starts at "start"
    assertEquals(readActions(0).pcFrom, "start")
    assertEquals(readActions(0).pcTo, "read_0")
    // Second read follows first
    assertEquals(readActions(1).pcFrom, "read_0")
    assertEquals(readActions(1).pcTo, "read_1")
    // First write follows last read
    assertEquals(writeActions(0).pcFrom, "read_1")
    assertEquals(writeActions(0).pcTo, "write_0")
    // Last write goes to done
    assertEquals(writeActions(1).pcFrom, "write_0")
    assertEquals(writeActions(1).pcTo, "done")
  }

  test("TlaGen.generate - read+write+unknown together") {
    val analysis = ServiceAnalysis(
      handlerName = "mixed_all",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("API", "call", OperationKind.Unknown, false, None),
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 3,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)

    // Unknown operations are neither read nor write, so they are skipped
    // Only read and write ops generate actions
    val readActions = spec.actions.filter(_.name.startsWith("Read_"))
    val writeActions = spec.actions.filter(_.name.startsWith("Write_"))
    assert(readActions.nonEmpty)
    assert(writeActions.nonEmpty)
  }

  test("TlaGen.generate - only unknown operations produces Complete action") {
    val analysis = ServiceAnalysis(
      handlerName = "unknown_only",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("API", "call", OperationKind.Unknown, false, None),
        ServiceOperation("API", "notify", OperationKind.Unknown, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 2,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)

    // No read or write operations means empty read/write lists
    // so we get the Complete action
    assertEquals(spec.actions.head.name, "Complete")
    assertEquals(spec.actions.head.pcFrom, "start")
    assertEquals(spec.actions.head.pcTo, "done")
  }

  test("TlaGen.generate - read-only handler gets Complete action after last read") {
    val analysis = ServiceAnalysis(
      handlerName = "readonly",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("Cache", "find", OperationKind.Read, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 2,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)

    val completeAction = spec.actions.find(_.name == "Complete").get
    assertEquals(completeAction.pcFrom, "read_1")
    assertEquals(completeAction.pcTo, "done")
  }

  // ==========================================================================
  // TlaGen.generate - custom state variable and options
  // ==========================================================================

  test("TlaGen.generate - custom stateVariable name") {
    val analysis = ServiceAnalysis(
      handlerName = "custom_state",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions(stateVariable = "db_state")
    val spec = TlaGen.generate(analysis, options, 1)

    assert(spec.variables.contains("db_state"))
    assert(!spec.variables.contains("state"))
    // Actions should reference the custom state variable
    val writeAction = spec.actions.find(_.name.startsWith("Write_")).get
    assert(writeAction.effect.contains("db_state"))
  }

  test("TlaGen.generate - combined initial state and invariant") {
    val analysis = ServiceAnalysis(
      handlerName = "combo",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions(
      initialState = Map("count" -> TlaValue.TlaInt(0), "active" -> TlaValue.TlaBool(true)),
      invariant = Some("state[\"count\"] >= 0")
    )
    val spec = TlaGen.generate(analysis, options, 1)

    assert(spec.init.contains("count"))
    assert(spec.init.contains("active"))
    assert(spec.invariants.contains("state[\"count\"] >= 0"))
  }

  test("TlaGen.generate - empty initial state uses default") {
    val analysis = ServiceAnalysis(
      handlerName = "empty_init",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions(initialState = Map.empty)
    val spec = TlaGen.generate(analysis, options, 1)

    // With empty initial state, should use the empty record form
    assert(spec.init.contains("x \\in {} |-> 0"))
  }

  // ==========================================================================
  // sanitizeName / sanitizeModuleName edge cases
  // ==========================================================================

  test("TlaGen.sanitizeName - name starting with digit gets prefix") {
    val analysis = ServiceAnalysis(
      handlerName = "123handler",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)
    // Module name starting with digit should get M_ prefix
    assert(spec.moduleName.startsWith("M_"))
    assert(spec.moduleName.contains("123handler"))
  }

  test("TlaGen.sanitizeName - all special characters replaced") {
    val analysis = ServiceAnalysis(
      handlerName = "my-handler.v2@prod",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)
    // Hyphens, dots, @ signs should be replaced with underscores
    assert(!spec.moduleName.contains("-"))
    assert(!spec.moduleName.contains("."))
    assert(!spec.moduleName.contains("@"))
    assert(spec.moduleName.contains("_"))
  }

  test("TlaGen.sanitizeName - underscores preserved") {
    val analysis = ServiceAnalysis(
      handlerName = "my_handler_v2",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)
    assertEquals(spec.moduleName, "my_handler_v2")
  }

  test("TlaGen.sanitizeName - alphanumeric preserved") {
    val analysis = ServiceAnalysis(
      handlerName = "SimpleHandler42",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)
    assertEquals(spec.moduleName, "SimpleHandler42")
  }

  test("TlaGen.sanitizeName - operation method names sanitized in action names") {
    val analysis = ServiceAnalysis(
      handlerName = "test",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get-item", OperationKind.Read, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)
    val readAction = spec.actions.find(_.name.startsWith("Read_")).get
    // Method "get-item" should have hyphen replaced
    assert(!readAction.name.contains("-"))
    assert(readAction.name.contains("get_item"))
  }

  // ==========================================================================
  // TlaSpec.render - additional output format tests
  // ==========================================================================

  test("TlaSpec.render - multiple EXTENDS") {
    val spec = TlaSpec(
      moduleName = "MultiExtends",
      extends_ = List("Integers", "Sequences", "TLC", "FiniteSets"),
      variables = List("x"),
      init = "Init == x = 0",
      actions = Nil,
      next = "Next == TRUE",
      spec = "Spec == Init"
    )
    val rendered = spec.render
    assert(rendered.contains("EXTENDS Integers, Sequences, TLC, FiniteSets"))
  }

  test("TlaSpec.render - vars definition") {
    val spec = TlaSpec(
      moduleName = "VarsTest",
      extends_ = Nil,
      variables = List("state", "pc", "counter"),
      init = "Init == TRUE",
      actions = Nil,
      next = "Next == TRUE",
      spec = "Spec == Init"
    )
    val rendered = spec.render
    assert(rendered.contains("vars == <<state, pc, counter>>"))
    assert(rendered.contains("VARIABLES state, pc, counter"))
  }

  test("TlaSpec.render - Done formula always present") {
    val spec = TlaSpec(
      moduleName = "DoneTest",
      extends_ = Nil,
      variables = List("state", "pc"),
      init = "Init == TRUE",
      actions = Nil,
      next = "Next == Done",
      spec = "Spec == Init"
    )
    val rendered = spec.render
    assert(rendered.contains("Done =="))
    assert(rendered.contains("""pc = "done""""))
    assert(rendered.contains("UNCHANGED vars"))
  }

  test("TlaSpec.render - Next formula present") {
    val spec = TlaSpec(
      moduleName = "NextTest",
      extends_ = Nil,
      variables = List("x"),
      init = "Init == x = 0",
      actions = List(TlaAction("Step", "TRUE", "x' = x + 1", "start", "end")),
      next = "Next == Step \\/ Done",
      spec = "Spec == Init /\\ [][Next]_vars"
    )
    val rendered = spec.render
    assert(rendered.contains("Next == Step \\/ Done"))
  }

  test("TlaSpec.render - Spec formula present") {
    val spec = TlaSpec(
      moduleName = "SpecTest",
      extends_ = Nil,
      variables = List("x"),
      init = "Init == x = 0",
      actions = Nil,
      next = "Next == TRUE",
      spec = "Spec == Init /\\ [][Next]_vars"
    )
    val rendered = spec.render
    assert(rendered.contains("Spec == Init /\\ [][Next]_vars"))
  }

  test("TlaSpec.render - action guard and effect in single instance") {
    val spec = TlaSpec(
      moduleName = "ActionTest",
      extends_ = Nil,
      variables = List("state", "pc"),
      init = "Init == TRUE",
      actions = List(
        TlaAction("Increment", "state > 0", "state' = state + 1", "start", "done")
      ),
      next = "Next == Increment \\/ Done",
      spec = "Spec == Init"
    )
    val rendered = spec.render
    assert(rendered.contains("Increment =="))
    assert(rendered.contains("/\\ state > 0"))
    assert(rendered.contains("/\\ state' = state + 1"))
    assert(rendered.contains("pc = \"start\""))
    assert(rendered.contains("pc' = \"done\""))
  }

  test("TlaSpec.render - multiple actions") {
    val spec = TlaSpec(
      moduleName = "MultiAction",
      extends_ = Nil,
      variables = List("state", "pc"),
      init = "Init == TRUE",
      actions = List(
        TlaAction("Read", "TRUE", "UNCHANGED state", "start", "reading"),
        TlaAction("Write", "TRUE", "state' = state", "reading", "done")
      ),
      next = "Next == Read \\/ Write \\/ Done",
      spec = "Spec == Init"
    )
    val rendered = spec.render
    assert(rendered.contains("Read =="))
    assert(rendered.contains("Write =="))
    assert(rendered.contains("pc = \"start\""))
    assert(rendered.contains("pc' = \"reading\""))
    assert(rendered.contains("pc = \"reading\""))
    assert(rendered.contains("pc' = \"done\""))
  }

  test("TlaSpec.render - starts with MODULE header and ends with ====") {
    val spec = TlaSpec(
      moduleName = "Boundaries",
      extends_ = Nil,
      variables = List("x"),
      init = "Init == x = 0",
      actions = Nil,
      next = "Next == TRUE",
      spec = "Spec == Init"
    )
    val rendered = spec.render
    assert(rendered.contains("---- MODULE Boundaries ----"))
    assert(rendered.trim.endsWith("===="))
  }

  // ==========================================================================
  // TlaGen.generateNext tests
  // ==========================================================================

  test("TlaGen.generate - Next formula includes all action names plus Done") {
    val analysis = ServiceAnalysis(
      handlerName = "next_test",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 2,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)

    assert(spec.next.startsWith("Next =="))
    assert(spec.next.contains("Done"))
    // All action names should be in the Next disjunction
    for (action <- spec.actions) {
      assert(spec.next.contains(action.name), s"Next should contain ${action.name}")
    }
    assert(spec.next.contains("\\/"))
  }

  // ==========================================================================
  // TlaGen.generate - multi-instance details
  // ==========================================================================

  test("TlaGen.generate - multi-instance Init uses function for pc") {
    val analysis = ServiceAnalysis(
      handlerName = "multi",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), instances = 3)

    // pc should be a function for multi-instance
    assert(spec.init.contains("""pc = [i \in 1..3 |-> "start"]"""))
    // instance variable should be set
    assert(spec.init.contains("instance = 1..3"))
  }

  test("TlaGen.generate - single instance Init uses simple pc string") {
    val analysis = ServiceAnalysis(
      handlerName = "single",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), instances = 1)

    assert(spec.init.contains("""pc = "start""""))
    assert(!spec.init.contains("instance"))
  }

  test("TlaGen.generate - multi-instance variables include instance") {
    val analysis = ServiceAnalysis(
      handlerName = "vars_test",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val specSingle = TlaGen.generate(analysis, TlaOptions(), instances = 1)
    val specMulti = TlaGen.generate(analysis, TlaOptions(), instances = 2)

    assert(!specSingle.variables.contains("instance"))
    assert(specMulti.variables.contains("instance"))
    assert(specSingle.variables.contains("pc"))
    assert(specMulti.variables.contains("pc"))
  }

  // ==========================================================================
  // TlaGen.generate - Spec formula
  // ==========================================================================

  test("TlaGen.generate - Spec formula always set") {
    val analysis = ServiceAnalysis(
      handlerName = "spec_test",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)
    assertEquals(spec.spec, "Spec == Init /\\ [][Next]_vars")
  }

  test("TlaGen.generate - extends always includes Integers, Sequences, TLC") {
    val analysis = ServiceAnalysis(
      handlerName = "extends_test",
      sourceFile = "test.bosatsu",
      operations = Nil,
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 0,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)
    assertEquals(spec.extends_, List("Integers", "Sequences", "TLC"))
  }

  // ==========================================================================
  // TlaGen.generate - write action effect
  // ==========================================================================

  test("TlaGen.generate - write action effect references state variable") {
    val analysis = ServiceAnalysis(
      handlerName = "write_effect",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(stateVariable = "mydb"), 1)
    val writeAction = spec.actions.find(_.name.startsWith("Write_")).get
    assert(writeAction.effect.contains("mydb"))
  }

  test("TlaGen.generate - read action effect is UNCHANGED state") {
    val analysis = ServiceAnalysis(
      handlerName = "read_effect",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 1,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(), 1)
    val readAction = spec.actions.find(_.name.startsWith("Read_")).get
    assert(readAction.effect.contains("UNCHANGED state"))
  }

  // ==========================================================================
  // TlaSpec.render - round-trip with TlaGen.generate
  // ==========================================================================

  test("TlaSpec.render - generated spec renders valid TLA+") {
    val analysis = ServiceAnalysis(
      handlerName = "full_roundtrip",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 2,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val options = TlaOptions(
      initialState = Map("count" -> TlaValue.TlaInt(0)),
      invariant = Some("state[\"count\"] >= 0")
    )
    val spec = TlaGen.generate(analysis, options, 1)
    val rendered = spec.render

    // Verify structure
    assert(rendered.contains("---- MODULE full_roundtrip ----"))
    assert(rendered.contains("EXTENDS Integers, Sequences, TLC"))
    assert(rendered.contains("VARIABLES"))
    assert(rendered.contains("Init =="))
    assert(rendered.contains("Read_"))
    assert(rendered.contains("Write_"))
    assert(rendered.contains("Done =="))
    assert(rendered.contains("Next =="))
    assert(rendered.contains("Spec =="))
    assert(rendered.contains("Inv0 == state[\"count\"] >= 0"))
    assert(rendered.contains("===="))
  }

  test("TlaSpec.render - generated multi-instance spec renders valid TLA+") {
    val analysis = ServiceAnalysis(
      handlerName = "concurrent",
      sourceFile = "test.bosatsu",
      operations = List(
        ServiceOperation("DB", "get", OperationKind.Read, false, None),
        ServiceOperation("DB", "set", OperationKind.Write, false, None)
      ),
      batchGroups = Nil,
      canBatch = false,
      totalQueries = 2,
      batchedQueries = 0,
      queriesSaved = 0
    )
    val spec = TlaGen.generate(analysis, TlaOptions(invariant = Some("TRUE")), instances = 2)
    val rendered = spec.render

    assert(rendered.contains("---- MODULE concurrent ----"))
    assert(rendered.contains("VARIABLES state, pc, instance"))
    // Multi-instance actions use self parameter
    assert(rendered.contains("(self) =="))
    assert(rendered.contains("pc[self]"))
    assert(rendered.contains("EXCEPT ![self]"))
  }

  // ==========================================================================
  // TlaValue.TlaRecord - multi-field render
  // ==========================================================================

  test("TlaValue.TlaRecord - multiple fields render") {
    val record = TlaValue.TlaRecord(Map(
      "a" -> TlaValue.TlaInt(1),
      "b" -> TlaValue.TlaString("hello")
    ))
    val rendered = record.render
    assert(rendered.startsWith("["))
    assert(rendered.endsWith("]"))
    assert(rendered.contains("a |-> 1"))
    assert(rendered.contains("b |-> \"hello\""))
  }

  test("TlaValue.TlaSeq - single element") {
    val seq = TlaValue.TlaSeq(List(TlaValue.TlaInt(42)))
    assertEquals(seq.render, "<<42>>")
  }

  test("TlaValue.TlaSet - single element") {
    val set = TlaValue.TlaSet(Set(TlaValue.TlaBool(true)))
    assertEquals(set.render, "{TRUE}")
  }

  // ==========================================================================
  // TlaValue.fromAny - edge cases
  // ==========================================================================

  test("TlaValue.fromAny - negative Int") {
    val result = TlaValue.fromAny(-5)
    assertEquals(result.render, "-5")
  }

  test("TlaValue.fromAny - zero Int") {
    val result = TlaValue.fromAny(0)
    assertEquals(result.render, "0")
  }

  test("TlaValue.fromAny - empty String") {
    val result = TlaValue.fromAny("")
    assertEquals(result.render, "\"\"")
  }

  test("TlaValue.fromAny - empty Seq") {
    val result = TlaValue.fromAny(Seq.empty)
    assertEquals(result.render, "<<>>")
  }

  test("TlaValue.fromAny - empty Map") {
    val result = TlaValue.fromAny(Map.empty[String, Int])
    assertEquals(result.render, "[]")
  }

  test("TlaValue.fromAny - nested Seq of Seq") {
    val result = TlaValue.fromAny(Seq(Seq(1, 2), Seq(3)))
    assertEquals(result.render, "<<<<1, 2>>, <<3>>>>")
  }
}
