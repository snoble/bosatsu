// =============================================================================
// JIT Performance Benchmark Runner
// =============================================================================
// Compares Map-based binding traversal vs direct generated code
// to determine if runtime JIT optimization is worthwhile.
// =============================================================================

// -----------------------------------------------------------------------------
// Approach 1: Map Traversal (Current BosatsuUI approach)
// -----------------------------------------------------------------------------

class MapTraversalApproach {
  constructor(bindingCount) {
    this.bindingCount = bindingCount;
    this.bindings = {};  // path -> binding[]
    this.elementCache = new Map();
    this.state = {};
  }

  setup(container) {
    // Create DOM elements and bindings
    for (let i = 0; i < this.bindingCount; i++) {
      const el = document.createElement('span');
      el.id = `map-el-${i}`;
      el.textContent = '0';
      container.appendChild(el);

      // Register binding
      const path = `entity.field${i}`;
      this.bindings[path] = [{
        elementId: `map-el-${i}`,
        property: 'textContent',
        statePath: ['entity', `field${i}`]
      }];

      // Cache element
      this.elementCache.set(`map-el-${i}`, el);

      // Initialize state
      this.state[`field${i}`] = 0;
    }
  }

  // Simulates _applyBindingsForPath
  update(fieldIndex, value) {
    const path = `entity.field${fieldIndex}`;
    const bindings = this.bindings[path];

    if (!bindings) return;

    for (const binding of bindings) {
      const element = this.elementCache.get(binding.elementId);
      if (element) {
        element[binding.property] = String(value);
      }
    }
  }

  cleanup(container) {
    container.innerHTML = '';
    this.bindings = {};
    this.elementCache.clear();
    this.state = {};
  }
}

// -----------------------------------------------------------------------------
// Approach 2: Generated Code (Simulated JIT output)
// -----------------------------------------------------------------------------

class GeneratedCodeApproach {
  constructor(bindingCount) {
    this.bindingCount = bindingCount;
    this.elements = null;  // Will be frozen object
    this.updateFunctions = null;  // Pre-generated update functions
  }

  setup(container) {
    const elements = {};

    // Create DOM elements
    for (let i = 0; i < this.bindingCount; i++) {
      const el = document.createElement('span');
      el.id = `gen-el-${i}`;
      el.textContent = '0';
      container.appendChild(el);
      elements[`field${i}`] = el;
    }

    // Freeze for V8 optimization (consistent hidden class)
    this.elements = Object.freeze(elements);

    // Generate update functions (what the JIT Worker would produce)
    // Each function directly references its element
    this.updateFunctions = {};
    for (let i = 0; i < this.bindingCount; i++) {
      const el = elements[`field${i}`];
      // This closure captures the element directly - no lookup needed
      this.updateFunctions[i] = (value) => {
        el.textContent = String(value);
      };
    }
  }

  // Direct update - no Map traversal
  update(fieldIndex, value) {
    this.updateFunctions[fieldIndex](value);
  }

  cleanup(container) {
    container.innerHTML = '';
    this.elements = null;
    this.updateFunctions = null;
  }
}

// -----------------------------------------------------------------------------
// Approach 3: Generated Code with Batching Integration
// -----------------------------------------------------------------------------

class GeneratedCodeWithBatchingApproach {
  constructor(bindingCount) {
    this.bindingCount = bindingCount;
    this.elements = null;
    this.pendingUpdates = new Map();
    this.flushScheduled = false;
  }

  setup(container) {
    const elements = {};

    for (let i = 0; i < this.bindingCount; i++) {
      const el = document.createElement('span');
      el.id = `batch-el-${i}`;
      el.textContent = '0';
      container.appendChild(el);
      elements[`field${i}`] = el;
    }

    this.elements = Object.freeze(elements);
  }

  update(fieldIndex, value) {
    // Queue update (last-write-wins)
    this.pendingUpdates.set(fieldIndex, value);

    if (!this.flushScheduled) {
      this.flushScheduled = true;
      queueMicrotask(() => this.flush());
    }
  }

  flush() {
    for (const [fieldIndex, value] of this.pendingUpdates) {
      this.elements[`field${fieldIndex}`].textContent = String(value);
    }
    this.pendingUpdates.clear();
    this.flushScheduled = false;
  }

  cleanup(container) {
    container.innerHTML = '';
    this.elements = null;
    this.pendingUpdates.clear();
  }
}

// -----------------------------------------------------------------------------
// Approach 4: Direct Array (No function call overhead)
// -----------------------------------------------------------------------------
// Hypothesis: Function closure overhead might be the problem.
// What if we just use direct element references in an array?

class DirectArrayApproach {
  constructor(bindingCount) {
    this.bindingCount = bindingCount;
    this.elements = null;  // Will be a plain array
  }

  setup(container) {
    this.elements = new Array(this.bindingCount);

    for (let i = 0; i < this.bindingCount; i++) {
      const el = document.createElement('span');
      el.id = `arr-el-${i}`;
      el.textContent = '0';
      container.appendChild(el);
      this.elements[i] = el;  // Direct array storage
    }
  }

  update(fieldIndex, value) {
    // Direct array access + property write - no function call, no Map lookup
    this.elements[fieldIndex].textContent = String(value);
  }

  cleanup(container) {
    container.innerHTML = '';
    this.elements = null;
  }
}

// -----------------------------------------------------------------------------
// Approach 5: Frozen Object Direct (Like GeneratedCode but no closures)
// -----------------------------------------------------------------------------
// Test if the closure itself is the problem

class FrozenObjectDirectApproach {
  constructor(bindingCount) {
    this.bindingCount = bindingCount;
    this.elements = null;
  }

  setup(container) {
    const elements = {};

    for (let i = 0; i < this.bindingCount; i++) {
      const el = document.createElement('span');
      el.id = `fod-el-${i}`;
      el.textContent = '0';
      container.appendChild(el);
      elements[i] = el;  // Numeric keys
    }

    this.elements = Object.freeze(elements);
  }

  update(fieldIndex, value) {
    // Direct property access on frozen object - V8 should optimize
    this.elements[fieldIndex].textContent = String(value);
  }

  cleanup(container) {
    container.innerHTML = '';
    this.elements = null;
  }
}

// -----------------------------------------------------------------------------
// Hot Spot Benchmark Runner
// -----------------------------------------------------------------------------
// Real apps often have hot spots: 90% of updates hit 10% of bindings

async function runHotSpotBenchmark(name, approach, container, bindingCount, durationMs, hotSpotRatio = 0.1) {
  approach.setup(container);

  const hotSpotCount = Math.max(1, Math.floor(bindingCount * hotSpotRatio));

  // Warmup
  for (let i = 0; i < 1000; i++) {
    // 90% of updates hit hot spots
    const fieldIndex = Math.random() < 0.9
      ? i % hotSpotCount
      : Math.floor(Math.random() * bindingCount);
    approach.update(fieldIndex, i);
  }

  await new Promise(r => setTimeout(r, 50));

  let operations = 0;
  const startTime = performance.now();

  while (performance.now() - startTime < durationMs) {
    // 90% hit hot spots, 10% hit random
    const fieldIndex = Math.random() < 0.9
      ? operations % hotSpotCount
      : Math.floor(Math.random() * bindingCount);
    approach.update(fieldIndex, operations);
    operations++;
  }

  const elapsed = performance.now() - startTime;
  approach.cleanup(container);

  return {
    name,
    bindingCount,
    operations,
    elapsed,
    opsPerSec: (operations / elapsed) * 1000,
    avgMicroseconds: (elapsed / operations) * 1000,
    pattern: 'hot-spot'
  };
}

// -----------------------------------------------------------------------------
// Entity Batch Benchmark Runner
// -----------------------------------------------------------------------------
// DB-driven pages update whole entities at once (all fields of one row)

class MapTraversalEntityBatch {
  constructor(bindingCount, fieldsPerEntity = 10) {
    this.bindingCount = bindingCount;
    this.fieldsPerEntity = fieldsPerEntity;
    this.entityCount = Math.floor(bindingCount / fieldsPerEntity);
    this.bindings = {};
    this.elementCache = new Map();
  }

  setup(container) {
    let fieldIndex = 0;
    for (let e = 0; e < this.entityCount; e++) {
      for (let f = 0; f < this.fieldsPerEntity; f++) {
        const el = document.createElement('span');
        el.id = `batch-map-el-${fieldIndex}`;
        el.textContent = '0';
        container.appendChild(el);

        const path = `entity${e}.field${f}`;
        this.bindings[path] = [{
          elementId: `batch-map-el-${fieldIndex}`,
          property: 'textContent'
        }];
        this.elementCache.set(`batch-map-el-${fieldIndex}`, el);
        fieldIndex++;
      }
    }
  }

  // Update all fields of one entity (simulates DB row update)
  updateEntity(entityIndex, values) {
    for (let f = 0; f < this.fieldsPerEntity; f++) {
      const path = `entity${entityIndex}.field${f}`;
      const bindings = this.bindings[path];
      if (!bindings) continue;
      for (const binding of bindings) {
        const element = this.elementCache.get(binding.elementId);
        if (element) {
          element[binding.property] = String(values[f]);
        }
      }
    }
  }

  cleanup(container) {
    container.innerHTML = '';
    this.bindings = {};
    this.elementCache.clear();
  }
}

class DirectArrayEntityBatch {
  constructor(bindingCount, fieldsPerEntity = 10) {
    this.bindingCount = bindingCount;
    this.fieldsPerEntity = fieldsPerEntity;
    this.entityCount = Math.floor(bindingCount / fieldsPerEntity);
    this.entities = null;  // Array of arrays
  }

  setup(container) {
    this.entities = new Array(this.entityCount);
    let fieldIndex = 0;

    for (let e = 0; e < this.entityCount; e++) {
      this.entities[e] = new Array(this.fieldsPerEntity);
      for (let f = 0; f < this.fieldsPerEntity; f++) {
        const el = document.createElement('span');
        el.id = `batch-arr-el-${fieldIndex}`;
        el.textContent = '0';
        container.appendChild(el);
        this.entities[e][f] = el;
        fieldIndex++;
      }
    }
  }

  // Update all fields of one entity - direct array access
  updateEntity(entityIndex, values) {
    const entity = this.entities[entityIndex];
    for (let f = 0; f < this.fieldsPerEntity; f++) {
      entity[f].textContent = String(values[f]);
    }
  }

  cleanup(container) {
    container.innerHTML = '';
    this.entities = null;
  }
}

async function runEntityBatchBenchmark(name, approach, container, durationMs) {
  approach.setup(container);

  const entityCount = approach.entityCount;
  const fieldsPerEntity = approach.fieldsPerEntity;
  const sampleValues = Array.from({length: fieldsPerEntity}, (_, i) => i);

  // Warmup
  for (let i = 0; i < 100; i++) {
    approach.updateEntity(i % entityCount, sampleValues);
  }

  await new Promise(r => setTimeout(r, 50));

  let operations = 0;
  const startTime = performance.now();

  while (performance.now() - startTime < durationMs) {
    approach.updateEntity(operations % entityCount, sampleValues);
    operations++;
  }

  const elapsed = performance.now() - startTime;
  approach.cleanup(container);

  return {
    name,
    entityCount,
    fieldsPerEntity,
    operations,
    elapsed,
    opsPerSec: (operations / elapsed) * 1000,
    avgMicroseconds: (elapsed / operations) * 1000,
    pattern: 'entity-batch'
  };
}

// -----------------------------------------------------------------------------
// Extended Experiments Runner
// -----------------------------------------------------------------------------

async function runExperiments() {
  const bindingCount = parseInt(document.getElementById('binding-count').value);
  const duration = parseInt(document.getElementById('duration').value);
  const container = document.getElementById('benchmark-area');

  let html = '<h3>Extended Experiments</h3>';
  html += '<p>Testing alternative dimensions where JIT might be viable...</p>';

  // Experiment 1: Direct Array vs Map (eliminate function call overhead)
  setProgress('Experiment 1: Testing direct array vs closure overhead...');

  const mapApproach = new MapTraversalApproach(bindingCount);
  const mapResult = await runThroughputBenchmark('Map Traversal', mapApproach, container, bindingCount, duration, 1);

  await new Promise(r => setTimeout(r, 100));

  const genApproach = new GeneratedCodeApproach(bindingCount);
  const genResult = await runThroughputBenchmark('Generated (closures)', genApproach, container, bindingCount, duration, 1);

  await new Promise(r => setTimeout(r, 100));

  const arrApproach = new DirectArrayApproach(bindingCount);
  const arrResult = await runThroughputBenchmark('Direct Array', arrApproach, container, bindingCount, duration, 1);

  await new Promise(r => setTimeout(r, 100));

  const fodApproach = new FrozenObjectDirectApproach(bindingCount);
  const fodResult = await runThroughputBenchmark('Frozen Object', fodApproach, container, bindingCount, duration, 1);

  html += '<h4>Experiment 1: Closure Overhead Test</h4>';
  html += '<p><em>Is the function closure call the bottleneck?</em></p>';
  html += '<table>';
  html += '<tr><th>Approach</th><th>Ops/sec</th><th>vs Map</th></tr>';

  const approaches1 = [
    { name: 'Map Traversal (baseline)', result: mapResult },
    { name: 'Generated (closures)', result: genResult },
    { name: 'Direct Array', result: arrResult },
    { name: 'Frozen Object', result: fodResult }
  ];

  for (const a of approaches1) {
    const speedup = mapResult.avgMicroseconds / a.result.avgMicroseconds;
    const cls = speedup > 1.2 ? 'faster' : speedup < 0.8 ? 'slower' : '';
    html += `<tr class="${cls}">
      <td>${a.name}</td>
      <td>${Math.round(a.result.opsPerSec).toLocaleString()}</td>
      <td>${speedup.toFixed(2)}x</td>
    </tr>`;
  }
  html += '</table>';

  // Experiment 2: Hot Spot Pattern
  setProgress('Experiment 2: Testing hot spot pattern (90% hit 10% of bindings)...');

  await new Promise(r => setTimeout(r, 100));

  const mapHotSpot = await runHotSpotBenchmark('Map (hot spot)', new MapTraversalApproach(bindingCount), container, bindingCount, duration);

  await new Promise(r => setTimeout(r, 100));

  const arrHotSpot = await runHotSpotBenchmark('Direct Array (hot spot)', new DirectArrayApproach(bindingCount), container, bindingCount, duration);

  html += '<h4>Experiment 2: Hot Spot Pattern</h4>';
  html += '<p><em>What if 90% of updates hit 10% of bindings? (V8 IC optimization opportunity)</em></p>';
  html += '<table>';
  html += '<tr><th>Approach</th><th>Ops/sec</th><th>Speedup</th></tr>';

  const hotSpotSpeedup = mapHotSpot.avgMicroseconds / arrHotSpot.avgMicroseconds;
  html += `<tr><td>Map Traversal</td><td>${Math.round(mapHotSpot.opsPerSec).toLocaleString()}</td><td>1.00x</td></tr>`;
  html += `<tr class="${hotSpotSpeedup > 1.2 ? 'faster' : ''}"><td>Direct Array</td><td>${Math.round(arrHotSpot.opsPerSec).toLocaleString()}</td><td>${hotSpotSpeedup.toFixed(2)}x</td></tr>`;
  html += '</table>';

  // Experiment 3: Entity Batch (DB-driven pages)
  setProgress('Experiment 3: Testing entity batch updates (like DB row updates)...');

  await new Promise(r => setTimeout(r, 100));

  const mapBatch = new MapTraversalEntityBatch(bindingCount, 10);
  const mapBatchResult = await runEntityBatchBenchmark('Map (entity batch)', mapBatch, container, duration);

  await new Promise(r => setTimeout(r, 100));

  const arrBatch = new DirectArrayEntityBatch(bindingCount, 10);
  const arrBatchResult = await runEntityBatchBenchmark('Direct Array (entity batch)', arrBatch, container, duration);

  html += '<h4>Experiment 3: Entity Batch Updates</h4>';
  html += '<p><em>DB-driven pages update whole rows (10 fields per entity)</em></p>';
  html += '<table>';
  html += '<tr><th>Approach</th><th>Entities/sec</th><th>Speedup</th></tr>';

  const batchSpeedup = mapBatchResult.avgMicroseconds / arrBatchResult.avgMicroseconds;
  html += `<tr><td>Map Traversal</td><td>${Math.round(mapBatchResult.opsPerSec).toLocaleString()}</td><td>1.00x</td></tr>`;
  html += `<tr class="${batchSpeedup > 1.2 ? 'faster' : ''}"><td>Direct Array</td><td>${Math.round(arrBatchResult.opsPerSec).toLocaleString()}</td><td>${batchSpeedup.toFixed(2)}x</td></tr>`;
  html += '</table>';

  // Summary
  html += '<h4>Summary</h4>';
  html += '<div class="verdict ' + (Math.max(hotSpotSpeedup, batchSpeedup) > 1.5 ? 'go' : 'nogo') + '">';
  html += '<p><strong>Key findings:</strong></p>';
  html += '<ul>';
  html += `<li>Closure overhead: Direct array is ${(mapResult.avgMicroseconds / arrResult.avgMicroseconds).toFixed(2)}x vs Map</li>`;
  html += `<li>Hot spot pattern: ${hotSpotSpeedup.toFixed(2)}x speedup</li>`;
  html += `<li>Entity batch: ${batchSpeedup.toFixed(2)}x speedup</li>`;
  html += '</ul>';

  const bestScenario = Math.max(
    mapResult.avgMicroseconds / arrResult.avgMicroseconds,
    hotSpotSpeedup,
    batchSpeedup
  );

  if (bestScenario >= 1.5) {
    html += `<p>✅ Found viable scenario with ${bestScenario.toFixed(2)}x speedup!</p>`;
  } else {
    html += `<p>❌ No scenario exceeds 1.5x threshold. Best: ${bestScenario.toFixed(2)}x</p>`;
  }
  html += '</div>';

  setProgress('');
  document.getElementById('results').innerHTML = html;
  document.getElementById('results-card').style.display = 'block';
}

// -----------------------------------------------------------------------------
// Benchmark Runner
// -----------------------------------------------------------------------------

async function runThroughputBenchmark(name, approach, container, bindingCount, durationMs, updatesPerTick) {
  approach.setup(container);

  // Warmup
  for (let i = 0; i < 1000; i++) {
    const fieldIndex = i % bindingCount;
    approach.update(fieldIndex, i);
  }

  // Wait for any pending microtasks
  await new Promise(r => setTimeout(r, 50));

  let operations = 0;
  const startTime = performance.now();

  while (performance.now() - startTime < durationMs) {
    for (let j = 0; j < updatesPerTick; j++) {
      const fieldIndex = (operations + j) % bindingCount;
      approach.update(fieldIndex, operations);
    }
    operations += updatesPerTick;
  }

  const elapsed = performance.now() - startTime;

  // Wait for any pending batched updates
  await new Promise(r => setTimeout(r, 50));

  approach.cleanup(container);

  return {
    name,
    bindingCount,
    operations,
    elapsed,
    opsPerSec: (operations / elapsed) * 1000,
    avgMicroseconds: (elapsed / operations) * 1000
  };
}

async function runBenchmark() {
  const bindingCount = parseInt(document.getElementById('binding-count').value);
  const duration = parseInt(document.getElementById('duration').value);
  const updatesPerTick = parseInt(document.getElementById('updates-per-tick').value);

  setProgress(`Running benchmark with ${bindingCount} bindings...`);

  const container = document.getElementById('benchmark-area');
  const results = [];

  // Run Map Traversal
  setProgress(`Running Map Traversal (${bindingCount} bindings)...`);
  const mapApproach = new MapTraversalApproach(bindingCount);
  results.push(await runThroughputBenchmark(
    'Map Traversal',
    mapApproach,
    container,
    bindingCount,
    duration,
    updatesPerTick
  ));

  await new Promise(r => setTimeout(r, 100));

  // Run Generated Code
  setProgress(`Running Generated Code (${bindingCount} bindings)...`);
  const genApproach = new GeneratedCodeApproach(bindingCount);
  results.push(await runThroughputBenchmark(
    'Generated Code',
    genApproach,
    container,
    bindingCount,
    duration,
    updatesPerTick
  ));

  await new Promise(r => setTimeout(r, 100));

  // Run Generated Code with Batching
  setProgress(`Running Generated Code + Batching (${bindingCount} bindings)...`);
  const batchApproach = new GeneratedCodeWithBatchingApproach(bindingCount);
  results.push(await runThroughputBenchmark(
    'Generated + Batching',
    batchApproach,
    container,
    bindingCount,
    duration,
    updatesPerTick
  ));

  setProgress('');
  displayResults(results, bindingCount);
}

async function runAllScales() {
  const scales = [10, 100, 1000, 10000];
  const duration = parseInt(document.getElementById('duration').value);
  const updatesPerTick = parseInt(document.getElementById('updates-per-tick').value);
  const allResults = [];

  const container = document.getElementById('benchmark-area');

  for (const bindingCount of scales) {
    setProgress(`Running benchmarks for ${bindingCount} bindings...`);

    // Map Traversal
    const mapApproach = new MapTraversalApproach(bindingCount);
    allResults.push(await runThroughputBenchmark(
      'Map Traversal',
      mapApproach,
      container,
      bindingCount,
      duration,
      updatesPerTick
    ));

    await new Promise(r => setTimeout(r, 100));

    // Generated Code
    const genApproach = new GeneratedCodeApproach(bindingCount);
    allResults.push(await runThroughputBenchmark(
      'Generated Code',
      genApproach,
      container,
      bindingCount,
      duration,
      updatesPerTick
    ));

    await new Promise(r => setTimeout(r, 100));
  }

  setProgress('');
  displayAllScalesResults(allResults, scales);
}

function displayResults(results, bindingCount) {
  const mapResult = results.find(r => r.name === 'Map Traversal');
  const genResult = results.find(r => r.name === 'Generated Code');
  const batchResult = results.find(r => r.name === 'Generated + Batching');

  const speedup = mapResult.avgMicroseconds / genResult.avgMicroseconds;
  const batchSpeedup = mapResult.avgMicroseconds / batchResult.avgMicroseconds;

  let html = `<h3>Results for ${bindingCount} Bindings</h3>`;

  html += '<table>';
  html += '<tr><th>Approach</th><th>Ops/sec</th><th>Avg (μs)</th><th>Speedup</th></tr>';

  for (const r of results) {
    const su = mapResult.avgMicroseconds / r.avgMicroseconds;
    const speedupText = r.name === 'Map Traversal'
      ? '1.00x (baseline)'
      : `${su.toFixed(2)}x`;
    const cls = su > 1.5 ? 'faster' : su < 1 ? 'slower' : '';

    html += `<tr class="${cls}">
      <td>${r.name}</td>
      <td>${Math.round(r.opsPerSec).toLocaleString()}</td>
      <td>${r.avgMicroseconds.toFixed(3)}</td>
      <td>${speedupText}</td>
    </tr>`;
  }
  html += '</table>';

  // Visual bar chart
  const maxOps = Math.max(...results.map(r => r.opsPerSec));
  html += '<div class="chart-container"><div class="bar-chart">';
  for (const r of results) {
    const width = (r.opsPerSec / maxOps) * 100;
    const barClass = r.name === 'Map Traversal' ? 'map' : 'generated';
    html += `
      <div class="bar-row">
        <span class="bar-label">${r.name}</span>
        <div class="bar-container">
          <div class="bar ${barClass}" style="width: ${width}%"></div>
        </div>
        <span class="bar-value">${Math.round(r.opsPerSec).toLocaleString()} ops/s</span>
      </div>
    `;
  }
  html += '</div></div>';

  // Verdict
  const meetsThreshold = speedup >= 1.5;
  html += `
    <div class="verdict ${meetsThreshold ? 'go' : 'nogo'}">
      <h3>${meetsThreshold ? '✅ GO: JIT optimization is worthwhile' : '❌ NO-GO: Speedup below threshold'}</h3>
      <p>
        <strong>Generated Code speedup:</strong> ${speedup.toFixed(2)}x
        ${meetsThreshold ? '(exceeds 1.5x threshold)' : '(below 1.5x threshold)'}
      </p>
      <p>
        <strong>With Batching:</strong> ${batchSpeedup.toFixed(2)}x
        (batching may reduce gains due to queueMicrotask overhead)
      </p>
      <p style="margin-top: 12px; font-size: 14px; color: #666;">
        ${meetsThreshold
          ? 'The JIT approach shows meaningful speedup. Proceed with implementation.'
          : 'The speedup is marginal. Consider whether the complexity is justified.'}
      </p>
    </div>
  `;

  document.getElementById('results').innerHTML = html;
  document.getElementById('results-card').style.display = 'block';
}

function displayAllScalesResults(results, scales) {
  let html = '<h3>Results Across All Scales</h3>';

  html += '<table>';
  html += '<tr><th>Bindings</th><th>Map (ops/s)</th><th>Generated (ops/s)</th><th>Speedup</th><th>Verdict</th></tr>';

  const summaryData = [];

  for (const scale of scales) {
    const mapResult = results.find(r => r.name === 'Map Traversal' && r.bindingCount === scale);
    const genResult = results.find(r => r.name === 'Generated Code' && r.bindingCount === scale);

    if (!mapResult || !genResult) continue;

    const speedup = mapResult.avgMicroseconds / genResult.avgMicroseconds;
    const meetsThreshold = speedup >= 1.5;

    summaryData.push({ scale, speedup, meetsThreshold });

    html += `<tr class="${meetsThreshold ? 'faster' : ''}">
      <td>${scale.toLocaleString()}</td>
      <td>${Math.round(mapResult.opsPerSec).toLocaleString()}</td>
      <td>${Math.round(genResult.opsPerSec).toLocaleString()}</td>
      <td>${speedup.toFixed(2)}x</td>
      <td>${meetsThreshold ? '✅ GO' : '❌ NO-GO'}</td>
    </tr>`;
  }
  html += '</table>';

  // Summary verdict
  const at100 = summaryData.find(d => d.scale === 100);
  if (at100) {
    html += `
      <div class="verdict ${at100.meetsThreshold ? 'go' : 'nogo'}">
        <h3>Exit Criterion Check (100 bindings)</h3>
        <p>
          Speedup at 100 bindings: <strong>${at100.speedup.toFixed(2)}x</strong>
          ${at100.meetsThreshold
            ? '- Exceeds 1.5x threshold. Proceed with JIT implementation.'
            : '- Below 1.5x threshold. Reconsider JIT approach.'}
        </p>
      </div>
    `;
  }

  // Chart showing speedup trend
  html += '<div class="chart-container"><h4>Speedup by Scale</h4><div class="bar-chart">';
  const maxSpeedup = Math.max(...summaryData.map(d => d.speedup));
  for (const d of summaryData) {
    const width = (d.speedup / maxSpeedup) * 100;
    html += `
      <div class="bar-row">
        <span class="bar-label">${d.scale.toLocaleString()} bindings</span>
        <div class="bar-container">
          <div class="bar generated" style="width: ${width}%"></div>
        </div>
        <span class="bar-value">${d.speedup.toFixed(2)}x</span>
      </div>
    `;
  }
  html += '</div></div>';

  document.getElementById('results').innerHTML = html;
  document.getElementById('results-card').style.display = 'block';
}

function setProgress(msg) {
  document.getElementById('progress').textContent = msg;
}
