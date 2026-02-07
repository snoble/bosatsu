package dev.bosatsu

import munit.FunSuite
import Value._

class NumericTest extends FunSuite {

  private def wrap(d: Double): Value =
    ExternalValue(java.lang.Double.valueOf(d))

  // =========================================================================
  // Basic arithmetic operations
  // =========================================================================

  test("NumericImpl.add adds two doubles") {
    val result = NumericImpl.add(wrap(3.5), wrap(2.5))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 6.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.sub subtracts two doubles") {
    val result = NumericImpl.sub(wrap(5.0), wrap(2.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 3.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.times multiplies two doubles") {
    val result = NumericImpl.times(wrap(3.0), wrap(4.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 12.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.div divides two doubles") {
    val result = NumericImpl.div(wrap(10.0), wrap(4.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 2.5)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.div by zero produces Infinity") {
    val result = NumericImpl.div(wrap(1.0), wrap(0.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assert(d.doubleValue.isInfinite, "1.0 / 0.0 should be Infinity")
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.div 0/0 produces NaN") {
    val result = NumericImpl.div(wrap(0.0), wrap(0.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assert(d.doubleValue.isNaN, "0.0 / 0.0 should be NaN")
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  // =========================================================================
  // Conversion operations
  // =========================================================================

  test("NumericImpl.fromInt converts Int to Double") {
    val result = NumericImpl.fromInt(VInt(java.math.BigInteger.valueOf(42)))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 42.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.toInt converts Double to Int (truncates)") {
    val result = NumericImpl.toInt(wrap(3.9))
    result match {
      case VInt(bi) =>
        assertEquals(bi.intValue, 3)
      case _ => fail("Expected VInt")
    }
  }

  test("NumericImpl.toInt converts negative Double to Int") {
    val result = NumericImpl.toInt(wrap(-3.9))
    result match {
      case VInt(bi) =>
        assertEquals(bi.intValue, -3)
      case _ => fail("Expected VInt")
    }
  }

  // =========================================================================
  // Comparison operations
  // =========================================================================

  test("NumericImpl.cmp returns LT for less than") {
    val result = NumericImpl.cmp(wrap(1.0), wrap(2.0))
    assertEquals(result, Comparison.LT)
  }

  test("NumericImpl.cmp returns GT for greater than") {
    val result = NumericImpl.cmp(wrap(3.0), wrap(2.0))
    assertEquals(result, Comparison.GT)
  }

  test("NumericImpl.cmp returns EQ for equal") {
    val result = NumericImpl.cmp(wrap(2.0), wrap(2.0))
    assertEquals(result, Comparison.EQ)
  }

  test("NumericImpl.cmp NaN > any non-NaN (antisymmetric total ordering)") {
    // NaN is greater than all other values for total ordering
    val result = NumericImpl.cmp(wrap(Double.NaN), wrap(1.0))
    assertEquals(result, Comparison.GT)
  }

  test("NumericImpl.cmp any non-NaN < NaN (antisymmetric)") {
    // For antisymmetry: if cmp(NaN, x) = GT then cmp(x, NaN) = LT
    val result = NumericImpl.cmp(wrap(1.0), wrap(Double.NaN))
    assertEquals(result, Comparison.LT)
  }

  test("NumericImpl.cmp NaN == NaN for ordering purposes") {
    val result = NumericImpl.cmp(wrap(Double.NaN), wrap(Double.NaN))
    assertEquals(result, Comparison.EQ)
  }

  test("NumericImpl.cmp NaN > positive infinity") {
    val result = NumericImpl.cmp(wrap(Double.NaN), wrap(Double.PositiveInfinity))
    assertEquals(result, Comparison.GT)
  }

  test("NumericImpl.cmp positive infinity < NaN") {
    val result = NumericImpl.cmp(wrap(Double.PositiveInfinity), wrap(Double.NaN))
    assertEquals(result, Comparison.LT)
  }

  test("NumericImpl.eq returns True for equal doubles") {
    val result = NumericImpl.eq(wrap(2.5), wrap(2.5))
    assertEquals(result, True)
  }

  test("NumericImpl.eq returns False for unequal doubles") {
    val result = NumericImpl.eq(wrap(2.5), wrap(3.5))
    assertEquals(result, False)
  }

  // =========================================================================
  // Unary operations
  // =========================================================================

  test("NumericImpl.neg negates a double") {
    val result = NumericImpl.neg(wrap(5.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, -5.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.neg of negative is positive") {
    val result = NumericImpl.neg(wrap(-3.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 3.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.abs of positive is unchanged") {
    val result = NumericImpl.abs(wrap(5.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 5.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.abs of negative is positive") {
    val result = NumericImpl.abs(wrap(-5.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 5.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  // =========================================================================
  // String conversion operations
  // =========================================================================

  test("NumericImpl.doubleToString converts double to string") {
    val result = NumericImpl.doubleToString(wrap(3.14))
    result match {
      case Str(s) =>
        assertEquals(s, "3.14")
      case _ => fail("Expected Str value")
    }
  }

  test("NumericImpl.doubleToString converts integer-valued double") {
    val result = NumericImpl.doubleToString(wrap(42.0))
    result match {
      case Str(s) =>
        // JS returns "42", JVM returns "42.0"
        assert(s == "42.0" || s == "42", s"unexpected: $s")
      case _ => fail("Expected Str value")
    }
  }

  test("NumericImpl.doubleToString converts negative double") {
    val result = NumericImpl.doubleToString(wrap(-1.5))
    result match {
      case Str(s) =>
        assertEquals(s, "-1.5")
      case _ => fail("Expected Str value")
    }
  }

  test("NumericImpl.doubleToString converts Infinity") {
    val result = NumericImpl.doubleToString(wrap(Double.PositiveInfinity))
    result match {
      case Str(s) =>
        assertEquals(s, "Infinity")
      case _ => fail("Expected Str value")
    }
  }

  test("NumericImpl.doubleToString converts NaN") {
    val result = NumericImpl.doubleToString(wrap(Double.NaN))
    result match {
      case Str(s) =>
        assertEquals(s, "NaN")
      case _ => fail("Expected Str value")
    }
  }

  test("NumericImpl.stringToDouble parses valid double string") {
    val result = NumericImpl.stringToDouble(Str("3.14"))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 3.14)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.stringToDouble parses negative string") {
    val result = NumericImpl.stringToDouble(Str("-2.5"))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, -2.5)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.stringToDouble parses integer string") {
    val result = NumericImpl.stringToDouble(Str("42"))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 42.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.stringToDouble returns 0.0 for invalid string") {
    val result = NumericImpl.stringToDouble(Str("not_a_number"))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 0.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.stringToDouble throws on non-String value") {
    intercept[RuntimeException] {
      NumericImpl.stringToDouble(wrap(1.0))
    }
  }

  // =========================================================================
  // Trigonometric functions
  // =========================================================================

  test("NumericImpl.sin of 0 is 0") {
    val result = NumericImpl.sin(wrap(0.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 0.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.sin of pi/2 is 1") {
    val result = NumericImpl.sin(wrap(math.Pi / 2))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, 1.0, 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.sin of pi is approximately 0") {
    val result = NumericImpl.sin(wrap(math.Pi))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, 0.0, 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.cos of 0 is 1") {
    val result = NumericImpl.cos(wrap(0.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 1.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.cos of pi is -1") {
    val result = NumericImpl.cos(wrap(math.Pi))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, -1.0, 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.cos of pi/2 is approximately 0") {
    val result = NumericImpl.cos(wrap(math.Pi / 2))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, 0.0, 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.tan of 0 is 0") {
    val result = NumericImpl.tan(wrap(0.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 0.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.tan of pi/4 is approximately 1") {
    val result = NumericImpl.tan(wrap(math.Pi / 4))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, 1.0, 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  // =========================================================================
  // Power and exponential functions
  // =========================================================================

  test("NumericImpl.sqrt of 4 is 2") {
    val result = NumericImpl.sqrt(wrap(4.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 2.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.sqrt of 2 is approximately 1.414") {
    val result = NumericImpl.sqrt(wrap(2.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, math.sqrt(2.0), 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.sqrt of 0 is 0") {
    val result = NumericImpl.sqrt(wrap(0.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 0.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.sqrt of negative is NaN") {
    val result = NumericImpl.sqrt(wrap(-1.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assert(d.doubleValue.isNaN, "sqrt(-1) should be NaN")
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.pow computes base^exp") {
    val result = NumericImpl.pow(wrap(2.0), wrap(10.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 1024.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.pow with fractional exponent") {
    val result = NumericImpl.pow(wrap(9.0), wrap(0.5))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, 3.0, 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.pow with zero exponent is 1") {
    val result = NumericImpl.pow(wrap(5.0), wrap(0.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 1.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.pow with negative exponent") {
    val result = NumericImpl.pow(wrap(2.0), wrap(-1.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 0.5)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.exp of 0 is 1") {
    val result = NumericImpl.exp(wrap(0.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 1.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.exp of 1 is e") {
    val result = NumericImpl.exp(wrap(1.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, math.E, 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.log of 1 is 0") {
    val result = NumericImpl.log(wrap(1.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 0.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.log of e is 1") {
    val result = NumericImpl.log(wrap(math.E))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, 1.0, 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.log of negative is NaN") {
    val result = NumericImpl.log(wrap(-1.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assert(d.doubleValue.isNaN, "log(-1) should be NaN")
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.exp and log are inverses") {
    val original = 3.7
    val result = NumericImpl.log(NumericImpl.exp(wrap(original)))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEqualsDouble(d.doubleValue, original, 1e-10)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  // =========================================================================
  // Rounding functions
  // =========================================================================

  test("NumericImpl.floor rounds down positive") {
    val result = NumericImpl.floor(wrap(3.7))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 3.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.floor rounds down negative") {
    val result = NumericImpl.floor(wrap(-3.2))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, -4.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.floor of integer-valued double is unchanged") {
    val result = NumericImpl.floor(wrap(5.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 5.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.ceil rounds up positive") {
    val result = NumericImpl.ceil(wrap(3.2))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 4.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.ceil rounds up negative") {
    val result = NumericImpl.ceil(wrap(-3.7))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, -3.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.ceil of integer-valued double is unchanged") {
    val result = NumericImpl.ceil(wrap(5.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 5.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.round rounds to nearest integer (half up)") {
    val result = NumericImpl.round(wrap(3.5))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 4.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.round rounds down when below half") {
    val result = NumericImpl.round(wrap(3.4))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 3.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.round of negative") {
    val result = NumericImpl.round(wrap(-3.5))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, -3.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.round of integer-valued double is unchanged") {
    val result = NumericImpl.round(wrap(7.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 7.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  // =========================================================================
  // Random number generation
  // =========================================================================

  test("NumericImpl.random returns value in [0, 1)") {
    // Call several times to increase confidence
    for (_ <- 1 to 100) {
      val result = NumericImpl.random(Value.UnitValue)
      result match {
        case ExternalValue(d: java.lang.Double) =>
          val v = d.doubleValue
          assert(v >= 0.0 && v < 1.0, s"random() returned $v, expected [0, 1)")
        case _ => fail("Expected ExternalValue with Double")
      }
    }
  }

  test("NumericImpl.randomRange returns value in [min, max)") {
    val min = 10.0
    val max = 20.0
    for (_ <- 1 to 100) {
      val result = NumericImpl.randomRange(wrap(min), wrap(max))
      result match {
        case ExternalValue(d: java.lang.Double) =>
          val v = d.doubleValue
          assert(v >= min && v < max, s"randomRange returned $v, expected [$min, $max)")
        case _ => fail("Expected ExternalValue with Double")
      }
    }
  }

  test("NumericImpl.randomRange with negative range") {
    val min = -5.0
    val max = -1.0
    for (_ <- 1 to 50) {
      val result = NumericImpl.randomRange(wrap(min), wrap(max))
      result match {
        case ExternalValue(d: java.lang.Double) =>
          val v = d.doubleValue
          assert(v >= min && v < max, s"randomRange returned $v, expected [$min, $max)")
        case _ => fail("Expected ExternalValue with Double")
      }
    }
  }

  // =========================================================================
  // Min/max operations
  // =========================================================================

  test("NumericImpl.minDouble returns the smaller value") {
    val result = NumericImpl.minDouble(wrap(3.0), wrap(7.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 3.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.minDouble with equal values") {
    val result = NumericImpl.minDouble(wrap(5.0), wrap(5.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 5.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.minDouble with negative values") {
    val result = NumericImpl.minDouble(wrap(-3.0), wrap(-7.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, -7.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.maxDouble returns the larger value") {
    val result = NumericImpl.maxDouble(wrap(3.0), wrap(7.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 7.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.maxDouble with equal values") {
    val result = NumericImpl.maxDouble(wrap(5.0), wrap(5.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, 5.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.maxDouble with negative values") {
    val result = NumericImpl.maxDouble(wrap(-3.0), wrap(-7.0))
    result match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, -3.0)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  // =========================================================================
  // Constants
  // =========================================================================

  test("NumericImpl.pi is math.Pi") {
    NumericImpl.pi match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, math.Pi)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  test("NumericImpl.eConst is math.E") {
    NumericImpl.eConst match {
      case ExternalValue(d: java.lang.Double) =>
        assertEquals(d.doubleValue, math.E)
      case _ => fail("Expected ExternalValue with Double")
    }
  }

  // =========================================================================
  // Error handling
  // =========================================================================

  test("NumericImpl operations throw on invalid types") {
    intercept[RuntimeException] {
      NumericImpl.add(VInt(java.math.BigInteger.valueOf(1)), wrap(2.0))
    }
  }

  test("NumericImpl.fromInt throws on non-Int") {
    intercept[RuntimeException] {
      NumericImpl.fromInt(wrap(1.0))
    }
  }

  // =========================================================================
  // Numeric module constants
  // =========================================================================

  test("Numeric.packageName is correct") {
    assertEquals(Numeric.packageName.asString, "Bosatsu/Numeric")
  }

  test("Numeric.numericString contains Double definition") {
    assert(Numeric.numericString.contains("external struct Double"))
  }

  test("Numeric.jvmExternals is not empty") {
    // jvmExternals adds 10 functions
    assert(!Numeric.jvmExternals.equals(Externals.empty))
  }
}
