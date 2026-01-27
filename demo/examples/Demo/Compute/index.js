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
const factorial = n => _int_loop(n, 1, (i, acc) => ((_a0, _a1) => [_a0,
    _a1])((-1) + i, acc * i));
const main = (_a0 => [_a0])(Bosatsu_Prog$ignore_env(Bosatsu_Prog$pure(0)));
export {fib};
export {factorial};
export {main};