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
