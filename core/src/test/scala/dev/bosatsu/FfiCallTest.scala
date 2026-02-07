package dev.bosatsu

import munit.FunSuite
import cats.data.NonEmptyList
import Value._

class FfiCallTest extends FunSuite {

  // A dummy type to pass to FfiCall.call - we just need any rankn.Type
  private val dummyType: rankn.Type = rankn.Type.IntType

  // =========================================================================
  // Fn0 tests
  // =========================================================================

  test("Fn0 arity is 0") {
    val fn0 = FfiCall.Fn0(() => Value.UnitValue)
    assertEquals(fn0.arity, 0)
  }

  test("Fn0 call returns the result directly") {
    val fn0 = FfiCall.Fn0(() => ExternalValue("hello"))
    val result = fn0.call(dummyType)
    assertEquals(result, ExternalValue("hello"))
  }

  test("Fn0 call invokes the function each time") {
    var counter = 0
    val fn0 = FfiCall.Fn0(() => { counter += 1; ExternalValue(counter) })
    fn0.call(dummyType)
    fn0.call(dummyType)
    assertEquals(counter, 2)
  }

  // =========================================================================
  // Fn1 tests
  // =========================================================================

  test("Fn1 arity is 1") {
    val fn1 = FfiCall.Fn1(v => v)
    assertEquals(fn1.arity, 1)
  }

  test("Fn1 call returns an FnValue that applies to one arg") {
    val fn1 = FfiCall.Fn1(v => ExternalValue(v.asExternal.toAny.toString + "!"))
    val fnValue = fn1.call(dummyType)
    // fnValue should be an FnValue; apply it to one argument
    val result = fnValue.asFn(NonEmptyList.one(ExternalValue("hi")))
    assertEquals(result, ExternalValue("hi!"))
  }

  test("Fn1 call works with extra arguments (ignores extras)") {
    // Fn1 uses NonEmptyList(a, _) which matches any NonEmptyList, taking head
    val fn1 = FfiCall.Fn1(v => v)
    val fnValue = fn1.call(dummyType)
    val result = fnValue.asFn(NonEmptyList.of(ExternalValue("first"), ExternalValue("second")))
    assertEquals(result, ExternalValue("first"))
  }

  // =========================================================================
  // Fn2 tests
  // =========================================================================

  test("Fn2 arity is 2") {
    val fn2 = FfiCall.Fn2((a, b) => Value.Tuple(a, b))
    assertEquals(fn2.arity, 2)
  }

  test("Fn2 call returns an FnValue that applies to two args") {
    val fn2 = FfiCall.Fn2((a, b) => Value.Tuple(a, b))
    val fnValue = fn2.call(dummyType)
    val result = fnValue.asFn(NonEmptyList.of(ExternalValue(1), ExternalValue(2)))
    val expected = Value.Tuple(ExternalValue(1), ExternalValue(2))
    assertEquals(result, expected)
  }

  test("Fn2 call with only one arg throws RuntimeException") {
    val fn2 = FfiCall.Fn2((a, b) => Value.Tuple(a, b))
    val fnValue = fn2.call(dummyType)
    intercept[RuntimeException] {
      fnValue.asFn(NonEmptyList.one(ExternalValue(1)))
    }
  }

  // =========================================================================
  // Fn3 tests
  // =========================================================================

  test("Fn3 arity is 3") {
    val fn3 = FfiCall.Fn3((a, b, c) => Value.Tuple(a, b, c))
    assertEquals(fn3.arity, 3)
  }

  test("Fn3 call returns an FnValue that applies to three args") {
    val fn3 = FfiCall.Fn3((a, b, c) => Value.Tuple(a, b, c))
    val fnValue = fn3.call(dummyType)
    val result = fnValue.asFn(
      NonEmptyList.of(ExternalValue(1), ExternalValue(2), ExternalValue(3))
    )
    val expected = Value.Tuple(ExternalValue(1), ExternalValue(2), ExternalValue(3))
    assertEquals(result, expected)
  }

  test("Fn3 call with only one arg throws RuntimeException") {
    val fn3 = FfiCall.Fn3((a, b, c) => Value.Tuple(a, b, c))
    val fnValue = fn3.call(dummyType)
    intercept[RuntimeException] {
      fnValue.asFn(NonEmptyList.one(ExternalValue(1)))
    }
  }

  test("Fn3 call with two args throws RuntimeException") {
    val fn3 = FfiCall.Fn3((a, b, c) => Value.Tuple(a, b, c))
    val fnValue = fn3.call(dummyType)
    intercept[RuntimeException] {
      fnValue.asFn(NonEmptyList.of(ExternalValue(1), ExternalValue(2)))
    }
  }

  // =========================================================================
  // Fn4 tests
  // =========================================================================

  test("Fn4 arity is 4") {
    val fn4 = FfiCall.Fn4((a, b, c, d) => Value.Tuple(a, b, c, d))
    assertEquals(fn4.arity, 4)
  }

  test("Fn4 call returns an FnValue that applies to four args") {
    val fn4 = FfiCall.Fn4((a, b, c, d) => Value.Tuple(a, b, c, d))
    val fnValue = fn4.call(dummyType)
    val result = fnValue.asFn(
      NonEmptyList.of(
        ExternalValue(1),
        ExternalValue(2),
        ExternalValue(3),
        ExternalValue(4)
      )
    )
    val expected =
      Value.Tuple(ExternalValue(1), ExternalValue(2), ExternalValue(3), ExternalValue(4))
    assertEquals(result, expected)
  }

  test("Fn4 call with too few args throws RuntimeException") {
    val fn4 = FfiCall.Fn4((a, b, c, d) => Value.Tuple(a, b, c, d))
    val fnValue = fn4.call(dummyType)
    intercept[RuntimeException] {
      fnValue.asFn(NonEmptyList.of(ExternalValue(1), ExternalValue(2)))
    }
  }

  // =========================================================================
  // Fn5 tests
  // =========================================================================

  test("Fn5 arity is 5") {
    val fn5 = FfiCall.Fn5((a, b, c, d, e) => Value.Tuple(a, b, c, d, e))
    assertEquals(fn5.arity, 5)
  }

  test("Fn5 call returns an FnValue that applies to five args") {
    val fn5 = FfiCall.Fn5((a, b, c, d, e) => Value.Tuple(a, b, c, d, e))
    val fnValue = fn5.call(dummyType)
    val result = fnValue.asFn(
      NonEmptyList.of(
        ExternalValue(1),
        ExternalValue(2),
        ExternalValue(3),
        ExternalValue(4),
        ExternalValue(5)
      )
    )
    val expected = Value.Tuple(
      ExternalValue(1),
      ExternalValue(2),
      ExternalValue(3),
      ExternalValue(4),
      ExternalValue(5)
    )
    assertEquals(result, expected)
  }

  test("Fn5 call with too few args throws RuntimeException") {
    val fn5 = FfiCall.Fn5((a, b, c, d, e) => Value.Tuple(a, b, c, d, e))
    val fnValue = fn5.call(dummyType)
    intercept[RuntimeException] {
      fnValue.asFn(
        NonEmptyList.of(ExternalValue(1), ExternalValue(2), ExternalValue(3))
      )
    }
  }

  // =========================================================================
  // Identity / value preservation tests
  // =========================================================================

  test("Fn1 identity function preserves value") {
    val identity = FfiCall.Fn1(v => v)
    val fnValue = identity.call(dummyType)
    val input = Value.SumValue(3, ProductValue.fromList(List(ExternalValue("data"))))
    val result = fnValue.asFn(NonEmptyList.one(input))
    assertEquals(result, input)
  }

  test("Fn2 receives correct argument order") {
    // verify first arg and second arg are correctly routed
    val fn2 = FfiCall.Fn2((a, _) => a)
    val fnValue = fn2.call(dummyType)
    val result = fnValue.asFn(NonEmptyList.of(ExternalValue("first"), ExternalValue("second")))
    assertEquals(result, ExternalValue("first"))

    val fn2b = FfiCall.Fn2((_, b) => b)
    val fnValue2 = fn2b.call(dummyType)
    val result2 = fnValue2.asFn(NonEmptyList.of(ExternalValue("first"), ExternalValue("second")))
    assertEquals(result2, ExternalValue("second"))
  }

  test("FfiCall.call returns the same FnValue instance on repeated calls for FnN") {
    // Fn1's evalFn is a val, so call(t) should always return the same FnValue
    val fn1 = FfiCall.Fn1(v => v)
    val v1 = fn1.call(dummyType)
    val v2 = fn1.call(rankn.Type.StrType)
    assert(v1 eq v2, "Fn1.call should return the same cached FnValue regardless of type arg")
  }

  test("FfiCall.call on Fn0 returns fresh value each time") {
    var count = 0
    val fn0 = FfiCall.Fn0(() => { count += 1; ExternalValue(count) })
    val v1 = fn0.call(dummyType)
    val v2 = fn0.call(dummyType)
    // Fn0 actually evaluates fn() each call
    assert(v1 != v2, "Fn0.call evaluates the thunk on each call")
  }

  // =========================================================================
  // Various Value types as arguments
  // =========================================================================

  test("FfiCall works with SumValue arguments") {
    val fn1 = FfiCall.Fn1 { v =>
      val sum = v.asSum
      ExternalValue(sum.variant)
    }
    val fnValue = fn1.call(dummyType)
    val input = Value.SumValue(42, Value.UnitValue)
    val result = fnValue.asFn(NonEmptyList.one(input))
    assertEquals(result, ExternalValue(42))
  }

  test("FfiCall works with ProductValue arguments") {
    val fn1 = FfiCall.Fn1 { v =>
      val prod = v.asProduct
      prod.get(0)
    }
    val fnValue = fn1.call(dummyType)
    val input = ProductValue.fromList(List(ExternalValue("inner"), ExternalValue("other")))
    val result = fnValue.asFn(NonEmptyList.one(input))
    assertEquals(result, ExternalValue("inner"))
  }

  test("FfiCall works with FnValue arguments") {
    // Pass an FnValue as an argument to Fn1
    val fn1 = FfiCall.Fn1 { v =>
      // apply the received function to a fixed argument
      v.asFn(NonEmptyList.one(ExternalValue("applied")))
    }
    val fnValue = fn1.call(dummyType)
    val inputFn = FnValue(args => ExternalValue(args.head.asExternal.toAny.toString.toUpperCase))
    val result = fnValue.asFn(NonEmptyList.one(inputFn))
    assertEquals(result, ExternalValue("APPLIED"))
  }
}
