import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';

const registeredElements = new Map();
const animationFrames = new Map();
const windowListeners = new Map();
let nextAnimationFrame = 1;

class MockHTMLElement {
  constructor() {
    this.isConnected = true;
    this.clientWidth = 800;
    this.clientHeight = 600;
  }

  attachShadow() {
    this.shadowRoot = {children: [], append: (...values) => this.shadowRoot.children.push(...values)};
  }
}

function createCanvas() {
  const listeners = new Map();
  const captures = new Set();
  const operations = [];
  const context = {operations};
  for (const name of ['setTransform', 'clearRect', 'fillRect', 'save', 'restore', 'beginPath',
    'moveTo', 'lineTo', 'closePath', 'fill', 'stroke', 'translate', 'scale',
    'quadraticCurveTo', 'bezierCurveTo']) {
    context[name] = (...arguments_) => operations.push([name, ...arguments_]);
  }
  return {
    dataset: {},
    width: 0,
    height: 0,
    listeners,
    operations,
    setAttribute() {},
    getContext: () => context,
    addEventListener: (name, listener) => listeners.set(name, listener),
    removeEventListener: name => listeners.delete(name),
    setPointerCapture: id => captures.add(id),
    hasPointerCapture: id => captures.has(id),
    releasePointerCapture: id => captures.delete(id),
    dispatch(name, event) {
      listeners.get(name)?.(event);
    }
  };
}

globalThis.HTMLElement = MockHTMLElement;
globalThis.customElements = {
  get: name => registeredElements.get(name),
  define: (name, constructor) => registeredElements.set(name, constructor)
};
globalThis.document = {createElement: name => name === 'canvas' ? createCanvas() : {textContent: ''}};
globalThis.ResizeObserver = class {
  observe(target) {
    this.target = target;
  }

  disconnect() {
    this.target = null;
  }
};
globalThis.PointerEvent = class {};
globalThis.requestAnimationFrame = callback => {
  const id = nextAnimationFrame++;
  animationFrames.set(id, callback);
  return id;
};
globalThis.cancelAnimationFrame = id => animationFrames.delete(id);
globalThis.window = {
  devicePixelRatio: 1,
  addEventListener: (name, listener) => windowListeners.set(name, listener),
  removeEventListener: name => windowListeners.delete(name)
};

function flushPaint() {
  for (const [id, callback] of [...animationFrames]) {
    animationFrames.delete(id);
    callback();
  }
}

const canvasModule = await import(pathToFileURL(process.argv[2]));
const initial = {width: 800, height: 600, centerX: 10, centerY: 20,
  worldUnitsPerPixel: 2};

assert.deepEqual(canvasModule.validateViewport(initial), initial);
assert.deepEqual(canvasModule.resizeViewport(initial, 400, 300),
  {...initial, width: 400, height: 300});
assert.deepEqual(canvasModule.panViewport(initial, 5, -3),
  {...initial, centerX: 0, centerY: 14});
const zoomed = canvasModule.zoomViewport(initial, 200, 150, 2);
assert.equal(zoomed.worldUnitsPerPixel, 1);
assert.equal(zoomed.centerX, -190);
assert.equal(zoomed.centerY, 170);
assert.throws(() => canvasModule.validateViewport({...initial, centerX: NaN}),
  /NON_FINITE_VALUE/);
assert.throws(() => canvasModule.validateViewport({...initial, width: 16385}),
  /LIMIT_EXCEEDED/);
assert.throws(() => canvasModule.zoomViewport(initial, 0, 0, 0), /NON_FINITE_VALUE/);

const scene = {
  protocolVersion: 1,
  componentGeneration: 2,
  sceneGeneration: 3,
  viewportGeneration: 4,
  background: [255, 255, 255, 255],
  viewport: initial,
  layers: [{id: 'layer', name: 'Layer', features: [
    {id: 'point', name: 'Point', primitives: [{
      kind: 'point', coordinate: [0, 0],
      path: {commands: ['MOVE_TO', 'LINE_TO', 'LINE_TO', 'CLOSE'],
        ordinates: [0, 0, 1, 0, 0, 1]},
      viewBox: [0, 0, 1, 1], size: 8, fill: [200, 10, 20, 255], opacity: 1
    }]},
    {id: 'line', name: 'Line', primitives: [{
      kind: 'line', coordinates: [0, 0, 2, 2],
      stroke: {color: [10, 20, 200, 255], width: 2}, opacity: 0.5
    }]},
    {id: 'polygon', name: 'Polygon', primitives: [{
      kind: 'polygon', rings: [[0, 0, 4, 0, 4, 4, 0, 0],
        [1, 1, 2, 1, 2, 2, 1, 1]], fill: [10, 200, 20, 255],
      outline: {present: false}, opacity: 0.75
    }]}
  ]}]
};

const acceptedScene = canvasModule.validateScene(scene, 2, 2);
assert.deepEqual(acceptedScene, scene);
assert.notEqual(acceptedScene, scene);
assert.ok(Object.isFrozen(acceptedScene.layers[0].features[2].primitives[0].rings[0]));
scene.layers[0].features[2].primitives[0].rings[0][0] = 99;
assert.equal(acceptedScene.layers[0].features[2].primitives[0].rings[0][0], 0);
scene.layers[0].features[2].primitives[0].rings[0][0] = 0;
assert.deepEqual(canvasModule.collectDrawOrder(scene),
  ['layer/point/0', 'layer/line/0', 'layer/polygon/0']);
const multipart = structuredClone(scene);
multipart.sceneGeneration = 4;
multipart.layers[0].features[0].primitives.push({
  ...structuredClone(multipart.layers[0].features[0].primitives[0]),
  coordinate: [1, 1]
});
canvasModule.validateScene(multipart, 2, 3);
assert.deepEqual(canvasModule.collectDrawOrder(multipart),
  ['layer/point/0', 'layer/point/1', 'layer/line/0', 'layer/polygon/0']);
assert.equal(canvasModule.logicalSceneBytes(scene), 646);
assert.throws(() => canvasModule.validateScene({...scene, protocolVersion: 2}, 2, 2),
  /PROTOCOL_VERSION_UNSUPPORTED/);
assert.throws(() => canvasModule.validateScene({...scene, sceneGeneration: 2}, 2, 2),
  /STALE_GENERATION/);
assert.throws(() => canvasModule.validateScene({...scene,
  layers: [scene.layers[0], scene.layers[0]]}, 2, 2), /DUPLICATE_ID/);
const overlongIdentity = structuredClone(scene);
overlongIdentity.sceneGeneration = 4;
overlongIdentity.layers[0].id = 'x'.repeat(257);
assert.throws(() => canvasModule.validateScene(overlongIdentity, 2, 3), /LIMIT_EXCEEDED/);
const outlined = structuredClone(scene);
outlined.sceneGeneration = 4;
outlined.layers[0].features[2].primitives[0].outline = {
  present: true,
  stroke: {color: [0, 0, 0, 255], width: 1},
  opacity: 2
};
assert.throws(() => canvasModule.validateScene(outlined, 2, 3), /SYMBOL_UNSUPPORTED/);
outlined.layers[0].features[2].primitives[0].outline.opacity = Number.NaN;
assert.throws(() => canvasModule.validateScene(outlined, 2, 3), /NON_FINITE_VALUE/);
const nonFiniteColor = structuredClone(scene);
nonFiniteColor.sceneGeneration = 4;
nonFiniteColor.background[0] = Number.NaN;
assert.throws(() => canvasModule.validateScene(nonFiniteColor, 2, 3), /NON_FINITE_VALUE/);

const ElementClass = registeredElements.get('mundane-map-canvas');
assert.equal(ElementClass, canvasModule.MundaneMapCanvas);
const element = new ElementClass();
const settled = [];
const failures = [];
element.$server = {
  acceptSettledViewport: (...arguments_) => settled.push(arguments_),
  acceptClientFailure: (...arguments_) => failures.push(arguments_)
};
element.connectedCallback();
element.activateMap(1, 2, 3);
element.setScene(scene);
flushPaint();
assert.equal(failures.length, 0);
assert.equal(element.canvas.width, 800);
assert.equal(element.canvas.height, 600);
assert.ok(element.canvas.operations.some(operation => operation[0] === 'fill'));
assert.ok(element.canvas.operations.some(operation => operation[0] === 'stroke'));
assert.ok(element.canvas.operations.some(operation => operation[0] === 'bezierCurveTo') === false);
element.setScene(nonFiniteColor);
assert.deepEqual(failures.at(-1), [1, 2, 4, 'NON_FINITE_VALUE']);

element.canvas.dispatch('pointerdown', {pointerId: 1, offsetX: 100, offsetY: 100});
element.canvas.dispatch('pointermove', {pointerId: 1, offsetX: 120, offsetY: 110});
element.canvas.dispatch('pointerup', {pointerId: 1, offsetX: 120, offsetY: 110});
assert.equal(settled.length, 1);
assert.equal(settled[0][1], 2);
assert.equal(settled[0][2], 3);
assert.equal(settled[0][3], 4);

element.canvas.dispatch('pointerdown', {pointerId: 2, offsetX: 100, offsetY: 100});
element.canvas.dispatch('pointerdown', {pointerId: 3, offsetX: 200, offsetY: 100});
const beforePinchCenter = element.viewport.centerX;
element.canvas.dispatch('pointermove', {pointerId: 2, offsetX: 120, offsetY: 100});
assert.notEqual(element.viewport.centerX, beforePinchCenter);
element.canvas.dispatch('pointerup', {pointerId: 2, offsetX: 120, offsetY: 100});
element.canvas.dispatch('pointerup', {pointerId: 3, offsetX: 200, offsetY: 100});

element.disconnectedCallback();
assert.equal(element.canvas.listeners.size, 0);
assert.equal(windowListeners.size, 0);

const resizeObserver = globalThis.ResizeObserver;
globalThis.ResizeObserver = undefined;
const unsupported = new ElementClass();
globalThis.ResizeObserver = resizeObserver;
unsupported.$server = {acceptClientFailure: (...arguments_) => failures.push(arguments_)};
unsupported.activateMap(1, 5, 7);
assert.deepEqual(failures.at(-1).slice(0, 3), [1, 5, 7]);
assert.equal(failures.at(-1)[3], 'BROWSER_CAPABILITY_UNSUPPORTED');
unsupported.setScene({...scene, protocolVersion: 2, componentGeneration: 5, sceneGeneration: 7});
assert.equal(failures.at(-1)[3], 'PROTOCOL_VERSION_UNSUPPORTED');
