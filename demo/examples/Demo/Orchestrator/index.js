// Runtime import (for standalone module use)
if (typeof require !== 'undefined') { require("./../../_runtime.js"); }

const compute_fib_sequence = n => Bosatsu_Predef$map_List(range(1 + n), i => ((_a0, _a1) => [_a0,
    _a1])(i, Demo_Compute$fib(i)));
const compute_factorial_table = n => Bosatsu_Predef$map_List(range(1 + n), i => ((_a0, _a1) => [_a0,
    _a1])(i, Demo_Compute$factorial(i)));
export {compute_fib_sequence};
export {compute_factorial_table};