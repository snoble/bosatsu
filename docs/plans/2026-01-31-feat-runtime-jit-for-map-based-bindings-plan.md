---
title: "feat: Runtime JIT for Map-Based UI Bindings"
type: feat
date: 2026-01-31
status: experimental
branch: experimental/runtime-jit
---

# feat: Runtime JIT for Map-Based UI Bindings

## Overview

Implement a background JIT compiler that observes hot paths through BosatsuUI's Map-based binding system and generates specialized JavaScript code via Blob URL modules, eliminating the "interpreter overhead" of Map traversal.

**Core insight**: BosatsuUI's Map structure (`Map<Id, Bindings>`) is a program encoded as data. V8's JIT can't optimize data traversal. By generating actual JavaScript for hot paths, we let V8 fully inline and optimize DOM updates.

**Experimental scope**: This is a benchmark-driven feature. Only adopt if we achieve 2x+ speedup at 100+ bindings with <10% memory overhead.

## Problem Statement / Motivation

### The Problem

Current binding update flow:

```javascript
function updateLikes(commentId, newLikes) {
  // 1. Map lookup (V8 can't inline)
  const bindings = _bindings[path.join('.')];

  // 2. Loop through bindings
  for (const binding of bindings) {
    // 3. Cached element lookup
    const element = _findElement(binding.elementId);

    // 4. Apply update
    element.textContent = String(newLikes);
  }
}
```

V8 sees: `data → data → data → DOM`. It can't prove the Maps don't change, so it can't specialize.

### The Solution

Generate direct code for hot paths:

```javascript
// Generated for entity 'c1' (Comment)
const elements = {
  likes: document.getElementById('c1-likes'),
  content: document.getElementById('c1-content')
};

export function updateC1(state) {
  if (state.likes !== undefined) elements.likes.textContent = String(state.likes);
  if (state.content !== undefined) elements.content.textContent = state.content;
}
```

V8 sees: `property access → DOM write`. This can be fully inlined.

### Why Not Static Generation?

Static generation works when binding structure is known at compile time. But some BosatsuUI apps have:
- User-generated content creating bindings dynamically
- Database-driven UI where the "program" comes from data
- Long-running apps where usage patterns emerge over time

## Proposed Solution

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ Main Thread                                                     │
│                                                                 │
│  ┌──────────────┐    ┌─────────────────────────────────────┐   │
│  │ BosatsuUI    │    │ JITIntegration                      │   │
│  │ Runtime      │───▶│ - statsCollector                    │   │
│  │              │    │ - optimizedHandlers: Map<id, fn>    │   │
│  │ setState()   │    │ - getHandler(id) → optimized|null   │   │
│  └──────────────┘    └─────────────────────────────────────┘   │
│         │                        │                ▲             │
│         │ stats                  │ import()       │ blobUrl     │
│         ▼                        ▼                │             │
└─────────┼────────────────────────┼────────────────┼─────────────┘
          │                        │                │
    postMessage                    │          postMessage
          │                        │                │
          ▼                        │                │
┌─────────────────────────────────────────────────────────────────┐
│ Worker Thread (jit-worker.js)                                   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ JITCompiler                                              │  │
│  │                                                          │  │
│  │  onmessage(stats):                                       │  │
│  │    candidate = findHottestUnoptimized(stats)             │  │
│  │    code = generateEntityRuntime(candidate)               │  │
│  │    blobUrl = URL.createObjectURL(new Blob([code]))       │  │
│  │    postMessage({ type: 'OPTIMIZED', blobUrl, entityId }) │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Code delivery** | Blob URL + `import()` | CSP-safe, V8 treats as first-class module |
| **Granularity** | Entity-oriented (state slice) | Matches type structure, single call for coherent updates |
| **Worker loop** | Message-driven (not polling) | Lower overhead, natural backpressure |
| **Invalidation** | Version + graceful fallback | Stale code catches errors, falls back to Map |
| **Sample rate** | 10% (configurable) | Balance overhead vs accuracy |

## Technical Considerations

### Integration with Existing Batching

Generated code MUST integrate with `_queuePathUpdate`, not bypass batching:

```javascript
// Generated code calls internal batch API
export function updateC1(state) {
  // Queue updates through batching system
  if (state.likes !== undefined) {
    _queueDirectUpdate('c1-likes', 'textContent', String(state.likes));
  }
}
```

### Conditional Binding Support (Deferred)

Initial implementation skips entities with `when` clauses. These fall back to Map traversal. Future work can generate switch statements for sum type variants.

### Element Invalidation Strategy

Generated code uses **version tracking with graceful fallback**:

```javascript
// Generated with version token
const _version = 42;
const elements = { likes: document.getElementById('c1-likes') };

export function updateC1(state) {
  if (!elements.likes || !document.contains(elements.likes)) {
    // Element removed - signal invalidation
    throw new InvalidationError('c1', _version);
  }
  elements.likes.textContent = String(state.likes);
}
```

Integration layer catches `InvalidationError`, removes handler, falls back to Map.

### V8 Optimization Patterns

Generated code follows V8 best practices:

1. **Consistent object shapes** - Destructure state first
2. **Cached element refs** - `Object.freeze({ ... })`
3. **No dynamic property access** - All field names static
4. **Small, focused functions** - One per entity

## Acceptance Criteria

### Functional Requirements

- [ ] Worker spawns on BosatsuUI init (lazy, after first binding access)
- [ ] Stats collector records binding access counts (10% sample rate)
- [ ] Stats sent to Worker every 1000ms
- [ ] Worker generates code for entities exceeding threshold (100 accesses)
- [ ] Generated modules exported via Blob URL
- [ ] Main thread imports and hot-swaps handlers
- [ ] Stale handlers detected and removed on element removal
- [ ] Fallback to Map traversal works correctly

### Non-Functional Requirements

- [ ] **Performance**: 2x+ speedup for hot paths at 100+ bindings
- [ ] **Memory**: <10% overhead from generated modules
- [ ] **Cold path**: No regression for unoptimized paths
- [ ] **Degradation**: Graceful fallback if Worker fails or CSP blocks

### Quality Gates

- [ ] Benchmark suite at 10, 100, 1000 bindings
- [ ] Tests for Worker failure scenarios
- [ ] Tests for element removal/invalidation
- [ ] CSP compatibility tests (`blob:` in various policies)

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Hot path speedup | ≥2x | Time per update: Map vs generated |
| Cold path regression | 0% | Must not slow down unoptimized paths |
| Memory overhead | <10% | Blob URLs + modules vs baseline |
| Time to first optimization | <5s | From page load to first hot-swap |

## Dependencies & Risks

### Dependencies

- Web Worker support (all modern browsers)
- Blob URL support (all modern browsers)
- Dynamic `import()` support (all modern browsers)
- CSP must allow `blob:` in `script-src`

### Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| No measurable speedup | Medium | High | Benchmark early; abandon if <1.5x |
| CSP blocks Blob URLs | Low | Medium | Graceful fallback; document CSP requirements |
| Element invalidation race | Medium | Medium | Version tracking; error boundaries |
| Memory leak from Blob URLs | Low | Medium | Revoke on invalidation; cleanup on unmount |

## Implementation Phases

### Phase 1: Infrastructure (Benchmark Harness)

**Goal**: Measure baseline, establish methodology

- [ ] Create benchmark harness (`demos/benchmarks/jit-performance/`)
  - [ ] `baseline-map-traversal.html` - current runtime performance
  - [ ] `direct-code-simulation.html` - simulated generated code
  - [ ] `benchmark-runner.js` - automated comparison
- [ ] Measure at 10, 100, 1000, 10000 bindings
- [ ] Document baseline numbers
- [ ] **Exit criterion**: If simulated speedup <1.5x at 100 bindings, reconsider approach

**Files**:
- `demos/benchmarks/jit-performance/index.html`
- `demos/benchmarks/jit-performance/baseline-map-traversal.html`
- `demos/benchmarks/jit-performance/direct-code-simulation.html`
- `demos/benchmarks/jit-performance/benchmark-runner.js`

### Phase 2: Stats Collection

**Goal**: Collect binding access patterns without affecting performance

- [ ] Create `StatsCollector` class
  - [ ] `recordAccess(entityId)` with 10% sampling
  - [ ] `getStats()` returns access counts
  - [ ] `reset()` clears counters
- [ ] Integrate into `_applyBindingsForPath`
- [ ] Add config option `BosatsuUI.config.jit.sampleRate`
- [ ] Benchmark overhead of stats collection

**Files**:
- `core/src/main/resources/bosatsu-jit-stats.js`
- Edit `core/src/main/resources/bosatsu-ui-runtime.js`

### Phase 3: Worker JIT Compiler

**Goal**: Generate optimized code in background thread

- [ ] Create `jit-worker.js`
  - [ ] Message-driven event loop
  - [ ] `findHottestUnoptimized(stats)` selection
  - [ ] `generateEntityRuntime(candidate)` code generation
  - [ ] Blob URL creation and postMessage
- [ ] Handle Worker errors gracefully
- [ ] Add backpressure (buffer latest stats only)

**Files**:
- `core/src/main/resources/bosatsu-jit-worker.js`

### Phase 4: Integration Layer

**Goal**: Hot-swap handlers, manage Blob URLs

- [ ] Create `JITIntegration` class
  - [ ] Worker lifecycle management
  - [ ] `optimizedHandlers: Map<entityId, function>`
  - [ ] `getHandler(entityId)` → optimized | null
  - [ ] `loadOptimizedCode(blobUrl, entityId)`
  - [ ] Blob URL lifecycle (revoke on invalidation)
- [ ] Modify `_applyBindingsForPath` to check for optimized handler
- [ ] Integrate with batching system (`_queueDirectUpdate`)

**Files**:
- `core/src/main/resources/bosatsu-jit-integration.js`
- Edit `core/src/main/resources/bosatsu-ui-runtime.js`

### Phase 5: Invalidation & Error Handling

**Goal**: Handle element removal, stale handlers

- [ ] Add version tracking to generated code
- [ ] Create `InvalidationError` class
- [ ] Error boundary in integration layer
- [ ] Remove handler on invalidation
- [ ] Cleanup on `BosatsuUI.unmount()`

**Files**:
- Edit `core/src/main/resources/bosatsu-jit-worker.js`
- Edit `core/src/main/resources/bosatsu-jit-integration.js`

### Phase 6: Validation & Documentation

**Goal**: Prove value, document findings

- [ ] Run full benchmark suite
- [ ] Compare baseline vs JIT-optimized
- [ ] Document results in brainstorm/plan
- [ ] If 2x+ speedup: prepare for merge consideration
- [ ] If <2x speedup: document learnings, archive branch

**Files**:
- Update `docs/brainstorms/2026-01-31-runtime-jit-for-map-based-ui-brainstorm.md`
- `docs/performance/jit-benchmark-results.md`

### Phase 7: Compositional Generator Correctness Proof

**Goal**: Structure the generator as composable pieces, each trivially provable, with composition preserving type safety.

#### The Compositional Structure

Instead of one monolithic generator, decompose into small generators with clear contracts:

```
                    ┌─────────────────────────────────────────────┐
                    │ EntitySchema                                │
                    │ { name: "Comment", fields: [                │
                    │   {name: "likes", type: Int, binding: ...}, │
                    │   {name: "content", type: String, ...}      │
                    │ ]}                                          │
                    └─────────────────┬───────────────────────────┘
                                      │
                    ┌─────────────────┼───────────────────────────┐
                    │                 │                           │
                    ▼                 ▼                           ▼
         ┌──────────────┐  ┌──────────────┐            ┌──────────────┐
         │ genFieldRef  │  │ genFieldRef  │    ...     │ genFieldRef  │
         │ Int→ElementRef│  │ String→...  │            │ T→ElementRef │
         └──────┬───────┘  └──────┬───────┘            └──────┬───────┘
                │                 │                           │
                ▼                 ▼                           ▼
         ┌──────────────┐  ┌──────────────┐            ┌──────────────┐
         │genFieldUpdate│  │genFieldUpdate│    ...     │genFieldUpdate│
         │ Int→Update   │  │ String→...  │            │ T→Update     │
         └──────┬───────┘  └──────┬───────┘            └──────┬───────┘
                │                 │                           │
                └────────────────┬┴───────────────────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────────────┐
                    │ combineUpdates                  │
                    │ List[FieldUpdate] → UpdateFn   │
                    └─────────────────┬───────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────┐
                    │ wrapModule                      │
                    │ (ElementRefs, UpdateFn) → Module│
                    └─────────────────────────────────┘
```

#### Generator Components

Each component has a **contract** (precondition → postcondition):

```scala
// Component 1: Field Reference Generator
// Contract: Given valid field binding, produces valid element lookup
def genFieldRef(field: FieldSchema): Code.Statement
// Pre:  field.elementId exists in DOM schema
// Post: produces `field: document.getElementById('${field.elementId}')`

// Component 2: Coercion Generator
// Contract: Given Bosatsu type, produces JS coercion that preserves semantics
def genCoercion(typ: Type): Code.Expression => Code.Expression
// Pre:  typ ∈ {Int, String, Bool, Double}
// Post: Int → String(_), String → identity, Bool → identity, Double → String(_)

// Component 3: Field Update Generator
// Contract: Given field schema, produces update statement that type-checks
def genFieldUpdate(field: FieldSchema): Code.Statement
// Pre:  field has valid type and binding
// Post: produces `if (state.${field.name} !== undefined)
//                   elements.${field.name}.${field.property} = ${coerce}(state.${field.name})`
// where coerce = genCoercion(field.type)

// Component 4: Update Function Combiner
// Contract: Combining valid updates produces valid function
def combineUpdates(entityId: String, updates: List[Code.Statement]): Code.Function
// Pre:  all updates are valid FieldUpdate statements
// Post: produces `export function update${entityId}(state) { ${updates} }`

// Component 5: Module Wrapper
// Contract: Wrapping valid parts produces valid module
def wrapModule(refs: List[Code.Statement], fn: Code.Function): Code.Module
// Pre:  refs are valid FieldRefs, fn is valid UpdateFunction
// Post: produces complete ES module with exports
```

#### Composition Theorem

**Theorem**: If each component satisfies its contract, the composition produces type-safe code.

```
∀ entity: EntitySchema.
  (∀ f ∈ entity.fields. validFieldSchema(f)) →
  validModule(generate(entity))

where generate(entity) =
  let refs = entity.fields.map(genFieldRef)
  let updates = entity.fields.map(genFieldUpdate)
  let fn = combineUpdates(entity.name, updates)
  wrapModule(refs, fn)
```

**Proof structure** (compositional):
1. Prove `genFieldRef` satisfies its contract (by case analysis on field types)
2. Prove `genCoercion` satisfies its contract (by case analysis on Bosatsu types)
3. Prove `genFieldUpdate` satisfies its contract (uses 1 and 2)
4. Prove `combineUpdates` preserves validity (structural - just wraps in function)
5. Prove `wrapModule` preserves validity (structural - just wraps in module)
6. Compose: 1-5 together imply the theorem

#### Implementation Approach

- [ ] **Step 1**: Define `Code` ADT for generated code fragments
  - `Code.Statement`, `Code.Expression`, `Code.Function`, `Code.Module`
  - Each has a `render: String` method

- [ ] **Step 2**: Implement each generator component as a pure function
  - Input: schema fragment
  - Output: `Code` fragment
  - No side effects, no string concatenation

- [ ] **Step 3**: Define validity predicates for each `Code` type
  - `validElementRef: Code.Statement => Boolean`
  - `validFieldUpdate: Code.Statement => Boolean`
  - `validUpdateFunction: Code.Function => Boolean`
  - `validModule: Code.Module => Boolean`

- [ ] **Step 4**: Prove each component (choose one):
  - **Option A**: Exhaustive property tests (ScalaCheck)
    - Generate all possible inputs (finite type space)
    - Verify output satisfies predicate
  - **Option B**: Lean 4 proof
    - Encode `Code` ADT and generators in Lean
    - Prove contracts as theorems
  - **Option C**: Hybrid - property tests now, formal proof later

- [ ] **Step 5**: Prove composition preserves validity
  - Show that `wrapModule(refs.map(genFieldRef), combineUpdates(...))` is valid
  - If using property tests: test the full pipeline
  - If using Lean: prove the composition theorem

#### Why This Works

The key insight: **each component is tiny and obviously correct**.

- `genCoercion(Int)` returns `String(_)` - trivially correct
- `genFieldRef` is just string interpolation of known-valid IDs
- `combineUpdates` is structural wrapping
- The "proof" is mostly "look at it, it's obviously right"

But by structuring it compositionally, we get:
1. **Local reasoning** - each piece can be verified independently
2. **Reusable proofs** - if we add new types, just prove `genCoercion` for that type
3. **Clear contracts** - documentation doubles as specification
4. **Testability** - each component can be unit tested in isolation

#### Files

- `core/src/main/scala/dev/bosatsu/jit/Code.scala` - Code ADT
- `core/src/main/scala/dev/bosatsu/jit/Generator.scala` - Compositional generators
- `core/src/main/scala/dev/bosatsu/jit/Validator.scala` - Validity predicates
- `core/src/test/scala/dev/bosatsu/jit/GeneratorPropertiesTest.scala` - Property tests
- `docs/proofs/jit-generator-composition.md` - Proof sketch
- `proofs/lean/JITGenerator.lean` - Formal proof (if Option B chosen)

## Open Questions (Deferred to Implementation)

1. **Stats persistence**: Should we persist stats to localStorage for returning users?
   - Default: No persistence initially; evaluate based on benchmarks

2. **Tiered optimization**: Should we have baseline vs aggressive tiers?
   - Default: Single tier initially; add tiers if benchmarks justify

3. **Conditional binding support**: How to handle `when` clauses?
   - Default: Skip for Phase 1; fall back to Map traversal

4. **Debug tooling**: `BosatsuUI.getJITStats()` API?
   - Default: Add basic stats API; expand based on debugging needs

5. **Generate Bosatsu instead of JS?**: Could generate Bosatsu → type check → transpile
   - Decided: No - adds complexity (need compiler in Worker), grammar is simple enough to prove directly
   - Future: If generator becomes more complex, revisit this approach

## References & Research

### Internal References

- Brainstorm: `docs/brainstorms/2026-01-31-runtime-jit-for-map-based-ui-brainstorm.md`
- Current runtime: `core/src/main/resources/bosatsu-ui-runtime.js`
- Existing benchmarks: `demos/benchmarks/ui-performance/`
- Map-based demos: `demos/ui/shopping-cart-maps.html`, `demos/ui/nested-comments.html`

### External References

- [V8 Inline Caching](https://medium.com/@braineanear/the-v8-engine-series-iii-inline-caching-unlocking-javascript-performance-51cf09a64cc3)
- [MDN Web Workers](https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API)
- [MDN Blob URLs](https://developer.mozilla.org/en-US/docs/Web/API/URL/createObjectURL)
- [MDN Dynamic Import](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/import)

### Institutional Learnings Applied

- **State monad for analysis**: Use functional state threading for stats accumulation
- **AST reference pattern**: Store references to binding metadata, don't copy
- **JsGen pipeline**: Don't bypass with string manipulation; use proper code generation
