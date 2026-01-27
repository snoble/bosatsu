// Runtime import (for standalone module use)
if (typeof require !== 'undefined') { require("./../../_runtime.js"); }

const fib = n => (() => {
  const _anon0 = _int_loop(n, ((_a0, _a1) => [_a0,
      _a1])(0, 1), (i, acc) => (() => {
      const a = acc[0];
      return (() => {
        const b = acc[1];
        return ((_a0, _a1) => [_a0, _a1])((-1) + i, ((_a0, _a1) => [_a0,
            _a1])(b, a + b));
      })();
    })());
  return _anon0[0];
})();
const main = (_a0 => [_a0])(Bosatsu_Prog$ignore_env((() => {
      const result = Demo_Fibonacci$fib(20);
      return Bosatsu_Prog$_await(Bosatsu_Prog$ignore_env(Bosatsu_Prog$println(_concat_String(((_a0, _a1) => [1,
                _a0,
                _a1])(_js_to_bosatsu_string("fib(20) = "), ((_a0, _a1) => [1,
                  _a0,
                  _a1])(_int_to_String(result), [0]))))))(a => Bosatsu_Prog$pure(0));
    })()));
export {fib};
export {main};