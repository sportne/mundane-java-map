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
    'transform', 'clip', 'quadraticCurveTo', 'bezierCurveTo', 'putImageData', 'drawImage',
    'fillText']) {
    context[name] = (...arguments_) => operations.push([name, ...arguments_]);
  }
  context.createImageData = (width, height) =>
    ({width, height, data: new Uint8ClampedArray(width * height * 4)});
  context.measureText = text => ({
    width: text.length * 7,
    actualBoundingBoxLeft: 0,
    actualBoundingBoxAscent: 9,
    actualBoundingBoxRight: text.length * 7,
    actualBoundingBoxDescent: 3
  });
  Object.defineProperty(context, 'lineWidth', {
    set: value => operations.push(['lineWidth', value]),
    get: () => operations.filter(operation => operation[0] === 'lineWidth').at(-1)?.[1]
  });
  return {
    dataset: {},
    style: {},
    width: 0,
    height: 0,
    listeners,
    operations,
    setAttribute() {},
    getContext: () => context,
    addEventListener: (name, listener) => listeners.set(name, listener),
    removeEventListener: name => listeners.delete(name),
    focus() {},
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

function hatchMovePoints(operations) {
  const result = [];
  let afterClip = false;
  for (const operation of operations) {
    if (operation[0] === 'clip') {
      afterClip = true;
    } else if (afterClip && operation[0] === 'moveTo') {
      result.push(operation.slice(1));
      afterClip = false;
    }
  }
  return result;
}

function assertForwardHatchPhase(point, origin, spacing) {
  const projection = ((point[0] - origin[0]) + (point[1] - origin[1])) /
    Math.sqrt(2) / spacing;
  assert.ok(Math.abs(projection - Math.round(projection)) < 1e-8);
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
  labelCandidates: [],
  layers: [{id: 'layer', name: 'Layer', features: [
    {id: 'point', name: 'Point', primitives: [{
      kind: 'point', coordinate: [0, 0],
      path: {commands: ['MOVE_TO', 'LINE_TO', 'LINE_TO', 'CLOSE'],
        ordinates: [0, 0, 1, 0, 0, 1]},
      viewBox: [0, 0, 1, 1], size: [8, 6], unit: 'SCREEN_PIXEL', anchor: 'CENTER',
      offset: [0, 0], rotationDegrees: 0, rotationMode: 'SCREEN_RELATIVE',
      fill: [200, 10, 20, 255], stroke: {present: false},
      endpointBearing: {present: false}, opacity: 1
    }]},
    {id: 'line', name: 'Line', primitives: [{
      kind: 'line', coordinates: [0, 0, 2, 2],
      stroke: {color: [10, 20, 200, 255], width: 2, unit: 'SCREEN_PIXEL'}, opacity: 0.5
    }]},
    {id: 'polygon', name: 'Polygon', primitives: [{
      kind: 'polygon', rings: [[0, 0, 4, 0, 4, 4, 0, 0],
        [1, 1, 2, 1, 2, 2, 1, 1]], fill: [10, 200, 20, 255],
      opacity: 0.75
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
assert.equal(canvasModule.logicalSceneBytes(scene), 695);
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
const invalidStroke = structuredClone(scene);
invalidStroke.sceneGeneration = 4;
invalidStroke.layers[0].features[1].primitives[0].stroke.width = -1;
assert.throws(() => canvasModule.validateScene(invalidStroke, 2, 3), /SYMBOL_UNSUPPORTED/);
invalidStroke.layers[0].features[1].primitives[0].stroke.width = Number.NaN;
assert.throws(() => canvasModule.validateScene(invalidStroke, 2, 3), /NON_FINITE_VALUE/);
const nonFiniteColor = structuredClone(scene);
nonFiniteColor.sceneGeneration = 4;
nonFiniteColor.background[0] = Number.NaN;
assert.throws(() => canvasModule.validateScene(nonFiniteColor, 2, 3), /NON_FINITE_VALUE/);
const richScene = structuredClone(scene);
richScene.sceneGeneration = 4;
Object.assign(richScene.layers[0].features[0].primitives[0], {
  size: [12, 8], unit: 'MAP_UNIT', anchor: 'NORTH_EAST', offset: [2, -3],
  rotationDegrees: 30, rotationMode: 'MAP_RELATIVE',
  stroke: {present: true, value: {
    color: [0, 0, 0, 255], width: 1, unit: 'SCREEN_PIXEL'
  }}, endpointBearing: {present: true, value: 180}
});
richScene.layers[0].features[2].primitives.push({
  kind: 'hatch', rings: [[0, 0, 4, 0, 4, 4, 0, 0],
    [1, 1, 2, 1, 2, 2, 1, 1]], pattern: 'CROSS_DIAGONAL',
  stroke: {color: [30, 40, 50, 255], width: 1, unit: 'MAP_UNIT'},
  spacing: 1, spacingUnit: 'SCREEN_PIXEL', rotationMode: 'MAP_RELATIVE',
  maxSegments: 1000, opacity: 0.6
});
const screenRelativeHatch = structuredClone(
  richScene.layers[0].features[2].primitives.at(-1));
screenRelativeHatch.pattern = 'FORWARD_DIAGONAL';
screenRelativeHatch.stroke = {
  color: [60, 70, 80, 255], width: 3, unit: 'SCREEN_PIXEL'
};
screenRelativeHatch.spacing = 2;
screenRelativeHatch.spacingUnit = 'MAP_UNIT';
screenRelativeHatch.rotationMode = 'SCREEN_RELATIVE';
richScene.layers[0].features[2].primitives.push(screenRelativeHatch);
assert.deepEqual(canvasModule.validateScene(richScene, 2, 3), richScene);
const excessiveHatch = structuredClone(richScene);
excessiveHatch.layers[0].features[2].primitives.at(-2).maxSegments = 1;
const excessiveTotalHatch = structuredClone(richScene);
const denseHatch = excessiveTotalHatch.layers[0].features[2].primitives.at(-1);
denseHatch.spacing = 0.00005;
denseHatch.maxSegments = 200000;
excessiveTotalHatch.layers[0].features[2].primitives.push(structuredClone(denseHatch));
const invalidPathSequence = structuredClone(scene);
invalidPathSequence.sceneGeneration = 4;
invalidPathSequence.layers[0].features[0].primitives[0].path = {
  commands: ['LINE_TO'], ordinates: [0, 0]
};
assert.throws(() => canvasModule.validateScene(invalidPathSequence, 2, 3),
  /SYMBOL_UNSUPPORTED/);
const pathOutsideViewBox = structuredClone(scene);
pathOutsideViewBox.sceneGeneration = 4;
pathOutsideViewBox.layers[0].features[0].primitives[0].path.ordinates[2] = 2;
assert.throws(() => canvasModule.validateScene(pathOutsideViewBox, 2, 3),
  /SYMBOL_UNSUPPORTED/);
const openFilledPath = structuredClone(scene);
openFilledPath.sceneGeneration = 4;
openFilledPath.layers[0].features[0].primitives[0].path = {
  commands: ['MOVE_TO', 'LINE_TO'], ordinates: [0, 0, 1, 1]
};
assert.throws(() => canvasModule.validateScene(openFilledPath, 2, 3),
  /SYMBOL_UNSUPPORTED/);
const openStrokePath = structuredClone(openFilledPath);
const openStrokeMarker = openStrokePath.layers[0].features[0].primitives[0];
openStrokeMarker.fill = [0, 0, 0, 0];
openStrokeMarker.stroke = {present: true, value: {
  color: [0, 0, 0, 255], width: 1, unit: 'SCREEN_PIXEL'
}};
assert.deepEqual(canvasModule.validateScene(openStrokePath, 2, 3), openStrokePath);
const nonCanonicalRotation = structuredClone(richScene);
nonCanonicalRotation.layers[0].features[0].primitives[0].rotationDegrees = 360;
assert.throws(() => canvasModule.validateScene(nonCanonicalRotation, 2, 3),
  /SYMBOL_UNSUPPORTED/);
nonCanonicalRotation.layers[0].features[0].primitives[0].rotationDegrees = 0;
nonCanonicalRotation.layers[0].features[0].primitives[0].endpointBearing.value = -1;
assert.throws(() => canvasModule.validateScene(nonCanonicalRotation, 2, 3),
  /SYMBOL_UNSUPPORTED/);
const excessiveDeclaredHatchLimit = structuredClone(richScene);
excessiveDeclaredHatchLimit.layers[0].features[2].primitives.at(-1).maxSegments = 2147483648;
assert.throws(() => canvasModule.validateScene(excessiveDeclaredHatchLimit, 2, 3),
  /SYMBOL_UNSUPPORTED/);
const overflowingMarkerScene = structuredClone(richScene);
const overflowingMarker = overflowingMarkerScene.layers[0].features[0].primitives[0];
overflowingMarker.size = [Number.MAX_VALUE, Number.MAX_VALUE];
overflowingMarker.unit = 'SCREEN_PIXEL';
overflowingMarker.anchor = 'NORTH_WEST';
overflowingMarker.offset = [0, 0];
overflowingMarker.rotationDegrees = 45;
overflowingMarker.endpointBearing = {present: false};
assert.deepEqual(canvasModule.validateScene(overflowingMarkerScene, 2, 3),
  overflowingMarkerScene);
const overflowingViewBox = structuredClone(scene);
overflowingViewBox.sceneGeneration = 4;
overflowingViewBox.layers[0].features[0].primitives[0].viewBox =
  [-Number.MAX_VALUE, 0, Number.MAX_VALUE, 1];
assert.throws(() => canvasModule.validateScene(overflowingViewBox, 2, 3),
  /NON_FINITE_VALUE/);
overflowingViewBox.layers[0].features[0].primitives[0].viewBox =
  [0, -Number.MAX_VALUE, 1, Number.MAX_VALUE];
assert.throws(() => canvasModule.validateScene(overflowingViewBox, 2, 3),
  /NON_FINITE_VALUE/);
const invisibleScene = structuredClone(richScene);
invisibleScene.layers[0].features[0].primitives[0].opacity = 0;
invisibleScene.layers[0].features[0].primitives[0].stroke.value = {
  color: [0, 0, 0, 255], width: Number.MAX_VALUE, unit: 'MAP_UNIT'
};
invisibleScene.layers[0].features[1].primitives[0].opacity = 0;
invisibleScene.layers[0].features[1].primitives[0].stroke = {
  color: [0, 0, 0, 255], width: Number.MAX_VALUE, unit: 'MAP_UNIT'
};
for (const primitive of invisibleScene.layers[0].features[2].primitives
  .filter(value => value.kind === 'hatch')) {
  primitive.opacity = 0;
  primitive.spacing = Number.MIN_VALUE;
  primitive.spacingUnit = 'SCREEN_PIXEL';
}

const ElementClass = registeredElements.get('mundane-map-canvas');
assert.equal(ElementClass, canvasModule.MundaneMapCanvas);
const element = new ElementClass();
const settled = [];
const failures = [];
const emptyLabelAcks = [];
element.$server = {
  acceptSettledViewport: (...arguments_) => settled.push(arguments_),
  acceptPlacedLabels: (...arguments_) => emptyLabelAcks.push(arguments_),
  acceptClientFailure: (...arguments_) => failures.push(arguments_)
};
element.connectedCallback();
element.activateMap(1, 2, 3);
element.setScene(scene);
assert.deepEqual(emptyLabelAcks, []);
element.setMapViewport(1, 2, 3, 4, 800, 600, 10, 20, 2);
flushPaint();
assert.deepEqual(emptyLabelAcks, [[1, 2, 3, 4]]);
assert.equal(failures.length, 0);
assert.equal(element.canvas.width, 800);
assert.equal(element.canvas.height, 600);
assert.ok(element.canvas.operations.some(operation => operation[0] === 'fill'));
assert.ok(element.canvas.operations.some(operation => operation[0] === 'stroke'));
assert.ok(element.canvas.operations.some(operation => operation[0] === 'bezierCurveTo') === false);
const orderFailures = [];
const orderElement = new ElementClass();
orderElement.$server = {acceptClientFailure: (...arguments_) => orderFailures.push(arguments_)};
orderElement.connectedCallback();
orderElement.activateMap(1, 2, 3);
orderElement.setScene(structuredClone(scene));
const futureScene = structuredClone(scene);
futureScene.sceneGeneration = 4;
futureScene.viewportGeneration = 5;
futureScene.viewport = {...initial, centerX: 25};
orderElement.setMapViewport(1, 2, 4, 5, 800, 600, 25, 20, 2);
assert.equal(orderFailures.at(-1)[3], 'STALE_GENERATION');
const failureCountBeforeCorrectOrder = orderFailures.length;
orderElement.setScene(futureScene);
orderElement.setMapViewport(1, 2, 4, 5, 800, 600, 25, 20, 2);
assert.equal(orderFailures.length, failureCountBeforeCorrectOrder);
element.setScene(nonFiniteColor);
assert.deepEqual(failures.at(-1), [1, 2, 4, 'NON_FINITE_VALUE']);

const beforeDefaultDrag = element.viewport.centerX;
element.canvas.dispatch('pointerdown', {pointerId: 1, offsetX: 100, offsetY: 100,
  button: 0, buttons: 1});
element.canvas.dispatch('pointermove', {pointerId: 1, offsetX: 120, offsetY: 110,
  button: -1, buttons: 1});
element.canvas.dispatch('pointerup', {pointerId: 1, offsetX: 120, offsetY: 110,
  button: 0, buttons: 0});
await element.interactionChain;
assert.notEqual(element.viewport.centerX, beforeDefaultDrag);
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

const richElement = new ElementClass();
richElement.$server = {
  acceptSettledViewport: () => {},
  acceptClientFailure: (...arguments_) => failures.push(arguments_)
};
richElement.connectedCallback();
richElement.activateMap(1, 2, 3);
richElement.setScene(scene);
flushPaint();
const operationsBeforeFailure = richElement.canvas.operations.length;
richElement.setScene(excessiveHatch);
assert.equal(failures.at(-1)[3], 'SYMBOL_HATCH_SEGMENT_LIMIT_EXCEEDED');
assert.equal(richElement.sceneGeneration, 3);
assert.equal(richElement.canvas.operations.length, operationsBeforeFailure);
richElement.setScene(excessiveTotalHatch);
assert.equal(failures.at(-1)[3], 'SYMBOL_HATCH_SEGMENT_LIMIT_EXCEEDED');
assert.equal(richElement.sceneGeneration, 3);
richElement.setScene(overflowingMarkerScene);
assert.equal(failures.at(-1)[3], 'NON_FINITE_VALUE');
assert.equal(richElement.sceneGeneration, 3);
assert.equal(richElement.canvas.operations.length, operationsBeforeFailure);
const richPaintStart = richElement.canvas.operations.length;
richElement.setScene(richScene);
flushPaint();
assert.equal(richElement.sceneGeneration, 4);
assert.ok(richElement.canvas.operations.some(operation => operation[0] === 'transform'));
assert.ok(richElement.canvas.operations.some(operation => operation[0] === 'clip' &&
  operation[1] === 'evenodd'));
const markerMatrix = richElement.canvas.operations
  .filter(operation => operation[0] === 'transform').at(-1);
const expectedMatrix = [-3 * Math.sqrt(3), -3, 2, -2 * Math.sqrt(3),
  396 + 3 * Math.sqrt(3), 314.5];
markerMatrix.slice(1).forEach((value, index) =>
  assert.ok(Math.abs(value - expectedMatrix[index]) < 1e-9));
assert.deepEqual(richElement.canvas.operations.slice(richPaintStart)
  .filter(operation => operation[0] === 'lineWidth').map(operation => operation[1]),
[1, 2, 0.5, 3]);
const hatchPrimitives = richElement.scene.layers[0].features[2].primitives
  .filter(primitive => primitive.kind === 'hatch');
const initialMapHatch = richElement.hatchLayout(hatchPrimitives[0]);
const initialScreenHatch = richElement.hatchLayout(hatchPrimitives[1]);
assert.deepEqual(initialMapHatch.origin, [395, 310]);
assert.deepEqual(initialScreenHatch.origin, [0, 0]);
assert.equal(initialMapHatch.spacing, 1);
assert.equal(initialScreenHatch.spacing, 1);
const initialHatchMoves = hatchMovePoints(
  richElement.canvas.operations.slice(richPaintStart));
assert.equal(initialHatchMoves.length, 2);
assertForwardHatchPhase(initialHatchMoves[0], initialMapHatch.origin,
  initialMapHatch.spacing);
assertForwardHatchPhase(initialHatchMoves[1], initialScreenHatch.origin,
  initialScreenHatch.spacing);
const zoomPaintStart = richElement.canvas.operations.length;
richElement.setMapViewport(1, 2, 4, 5, 800, 600, 10, 20, 1);
flushPaint();
assert.deepEqual(richElement.canvas.operations.slice(zoomPaintStart)
  .filter(operation => operation[0] === 'lineWidth').map(operation => operation[1]),
[1, 2, 1, 3]);
const zoomMarkerMatrix = richElement.canvas.operations
  .filter(operation => operation[0] === 'transform').at(-1);
assert.ok(Math.abs(zoomMarkerMatrix[1] / markerMatrix[1] - 2) < 1e-9);
const zoomMapHatch = richElement.hatchLayout(hatchPrimitives[0]);
const zoomScreenHatch = richElement.hatchLayout(hatchPrimitives[1]);
assert.deepEqual(zoomMapHatch.origin, [390, 320]);
assert.deepEqual(zoomScreenHatch.origin, [0, 0]);
assert.equal(zoomMapHatch.spacing, 1);
assert.equal(zoomScreenHatch.spacing, 2);
const zoomHatchMoves = hatchMovePoints(
  richElement.canvas.operations.slice(zoomPaintStart));
assert.equal(zoomHatchMoves.length, 2);
assertForwardHatchPhase(zoomHatchMoves[0], zoomMapHatch.origin, zoomMapHatch.spacing);
assertForwardHatchPhase(zoomHatchMoves[1], zoomScreenHatch.origin,
  zoomScreenHatch.spacing);
richElement.disconnectedCallback();

const labelScene = structuredClone(scene);
labelScene.componentGeneration = 12;
labelScene.sceneGeneration = 13;
labelScene.labelCandidates = [{
  ordinal: 0,
  text: 'Alpha',
  fontFamily: 'SANS_SERIF',
  weight: 'BOLD',
  sizePixels: 14
}];
assert.equal(canvasModule.logicalSceneBytes(labelScene), 722);
assert.deepEqual(canvasModule.validateScene(labelScene, 12, 12), labelScene);
const hostileFontScene = structuredClone(labelScene);
hostileFontScene.labelCandidates[0].fontFamily = 'url(https://evil.example/font)';
assert.throws(() => canvasModule.validateScene(hostileFontScene, 12, 12),
  /SYMBOL_UNSUPPORTED/);
const multilineLabelScene = structuredClone(labelScene);
multilineLabelScene.labelCandidates[0].text = 'Alpha\nBeta';
assert.throws(() => canvasModule.validateScene(multilineLabelScene, 12, 12),
  /SYMBOL_UNSUPPORTED/);
const misorderedLabelScene = structuredClone(labelScene);
misorderedLabelScene.labelCandidates[0].ordinal = 1;
assert.throws(() => canvasModule.validateScene(misorderedLabelScene, 12, 12),
  /SYMBOL_UNSUPPORTED/);
const excessiveLabelsScene = structuredClone(labelScene);
excessiveLabelsScene.labelCandidates = Array.from({length: 4097}, (_, ordinal) => ({
  ...labelScene.labelCandidates[0], ordinal
}));
assert.throws(() => canvasModule.validateScene(excessiveLabelsScene, 12, 12),
  /LIMIT_EXCEEDED/);
const excessiveLabelTextScene = structuredClone(labelScene);
excessiveLabelTextScene.labelCandidates = Array.from({length: 1025}, (_, ordinal) => ({
  ...labelScene.labelCandidates[0], ordinal, text: 'x'.repeat(256)
}));
assert.throws(() => canvasModule.validateScene(excessiveLabelTextScene, 12, 12),
  /LIMIT_EXCEEDED/);

const labelMeasurements = [];
const placedAcks = [];
const labelFailures = [];
const labelElement = new ElementClass();
labelElement.$server = {
  acceptSettledViewport: () => {},
  acceptLabelMeasurements: (...arguments_) => labelMeasurements.push(arguments_),
  acceptPlacedLabels: (...arguments_) => placedAcks.push(arguments_),
  acceptClientFailure: (...arguments_) => labelFailures.push(arguments_)
};
labelElement.connectedCallback();
labelElement.activateMap(1, 12, 13);
labelElement.setScene(labelScene);
assert.deepEqual(labelMeasurements, [[1, 12, 13, 4, [35, -0, -9, 35, 3]]]);
assert.equal(labelElement.context.font, '700 14px sans-serif');
const labelPaintStart = labelElement.canvas.operations.length;
labelElement.setPlacedLabels(1, 12, 13, 4, [{
  text: 'Alpha', color: [20, 30, 40, 255], weight: 'BOLD', sizePixels: 14,
  baselineX: 401, baselineY: 299, advance: 35, ordinal: 0
}]);
assert.deepEqual(placedAcks, []);
flushPaint();
assert.deepEqual(placedAcks, [[1, 12, 13, 4]]);
const labelPaint = labelElement.canvas.operations.slice(labelPaintStart);
assert.deepEqual(labelPaint.filter(operation => operation[0] === 'fillText'),
  [['fillText', 'Alpha', 401, 299]]);
assert.ok(labelPaint.findIndex(operation => operation[0] === 'fillText') >
  labelPaint.findIndex(operation => operation[0] === 'stroke'));
assert.equal(labelElement.context.font, '700 14px sans-serif');

labelElement.setPlacedLabels(1, 12, 13, 4, [{
  text: 'url(https://evil.example)', color: [20, 30, 40, 255], weight: 'BOLD',
  sizePixels: 14, baselineX: 0, baselineY: 0, advance: 1, ordinal: 0
}]);
assert.equal(labelFailures.at(-1)[3], 'SYMBOL_UNSUPPORTED');
assert.equal(labelElement.placedLabels[0].text, 'Alpha');
labelElement.setPlacedLabels(1, 12, 13, 3, []);
assert.equal(labelFailures.at(-1)[3], 'STALE_GENERATION');
assert.equal(labelElement.placedLabels[0].text, 'Alpha');

labelElement.setMapEnabled(false);
assert.deepEqual(labelElement.placedLabels, []);
labelElement.setMapEnabled(true);
labelElement.remeasureLabels(1, 12, 13, 4);
assert.equal(labelMeasurements.length, 2);
labelElement.setPlacedLabels(1, 12, 13, 4, [{
  text: 'Alpha', color: [20, 30, 40, 255], weight: 'BOLD', sizePixels: 14,
  baselineX: 401, baselineY: 299, advance: 35, ordinal: 0
}]);
labelElement.setMapViewport(1, 12, 13, 5, 800, 600, 10, 20, 1);
assert.deepEqual(labelElement.placedLabels, []);
assert.equal(labelMeasurements.length, 3);
assert.deepEqual(labelMeasurements.at(-1).slice(0, 4), [1, 12, 13, 5]);
labelElement.deactivateMap(1, 12);
assert.equal(labelElement.scene, null);
assert.deepEqual(labelElement.placedLabels, []);
labelElement.disconnectedCallback();

for (const invalidMeasurement of [
  () => ({width: 1}),
  () => ({width: Number.NaN, actualBoundingBoxLeft: 0, actualBoundingBoxAscent: 1,
    actualBoundingBoxRight: 1, actualBoundingBoxDescent: 0}),
  () => ({width: 1, actualBoundingBoxLeft: -2, actualBoundingBoxAscent: 1,
    actualBoundingBoxRight: 1, actualBoundingBoxDescent: 0}),
  () => ({width: 1000001, actualBoundingBoxLeft: 0, actualBoundingBoxAscent: 1,
    actualBoundingBoxRight: 1000001, actualBoundingBoxDescent: 0})
]) {
  const metricFailures = [];
  const metricElement = new ElementClass();
  metricElement.context.measureText = invalidMeasurement;
  metricElement.$server = {
    acceptClientFailure: (...arguments_) => metricFailures.push(arguments_),
    acceptLabelMeasurements: () => assert.fail('invalid metrics must not be published')
  };
  metricElement.connectedCallback();
  metricElement.activateMap(1, 12, 13);
  metricElement.setScene(labelScene);
  assert.equal(metricFailures.at(-1)[3], 'BROWSER_CAPABILITY_UNSUPPORTED');
  metricElement.disconnectedCallback();
}

const fillFailures = [];
const fillAcks = [];
const fillElement = new ElementClass();
fillElement.$server = {
  acceptClientFailure: (...arguments_) => fillFailures.push(arguments_),
  acceptLabelMeasurements: () => {},
  acceptPlacedLabels: (...arguments_) => fillAcks.push(arguments_)
};
fillElement.connectedCallback();
fillElement.activateMap(1, 12, 13);
fillElement.setScene(labelScene);
fillElement.context.fillText = () => { throw new Error('deliberate label paint failure'); };
fillElement.setPlacedLabels(1, 12, 13, 4, [{
  text: 'Alpha', color: [20, 30, 40, 255], weight: 'BOLD', sizePixels: 14,
  baselineX: 401, baselineY: 299, advance: 35, ordinal: 0
}]);
flushPaint();
assert.deepEqual(fillAcks, []);
assert.equal(fillFailures.at(-1)[3], 'deliberate label paint failure');
fillElement.disconnectedCallback();

const invisibleElement = new ElementClass();
invisibleElement.$server = {
  acceptSettledViewport: () => {},
  acceptClientFailure: (...arguments_) => failures.push(arguments_)
};
invisibleElement.connectedCallback();
invisibleElement.activateMap(1, 2, 3);
const failuresBeforeInvisible = failures.length;
invisibleElement.setScene(invisibleScene);
flushPaint();
assert.equal(invisibleElement.sceneGeneration, 4);
assert.equal(failures.length, failuresBeforeInvisible);
invisibleElement.disconnectedCallback();

globalThis.location = {href: 'https://maps.example.test/app/'};
const iconBytes = new Uint8Array([77, 77, 82, 73, 1, 0, 0, 1, 0, 1, 0, 0,
  12, 34, 56, 255]);
const fetches = [];
globalThis.fetch = async (resource, options) => {
  fetches.push([resource, options]);
  return {ok: true, arrayBuffer: async () => iconBytes.buffer.slice(0)};
};
const iconScene = structuredClone(scene);
iconScene.componentGeneration = 9;
iconScene.sceneGeneration = 10;
iconScene.layers[0].features[0].primitives = [{
  kind: 'icon', coordinate: [10, 20], resource: './VAADIN/dynamic/resource/token/icon.mmri',
  intrinsicWidth: 1, intrinsicHeight: 1, size: [16, 16], unit: 'SCREEN_PIXEL',
  anchor: 'CENTER', offset: [0, 0], rotationDegrees: 0,
  rotationMode: 'SCREEN_RELATIVE', interpolation: 'NEAREST',
  endpointBearing: {present: false}, opacity: 1
}];
iconScene.layers[0].features.splice(1);
assert.equal(canvasModule.logicalSceneBytes(iconScene), 303);
const iconFailures = [];
const iconElement = new ElementClass();
iconElement.$server = {
  acceptSettledViewport: () => {},
  acceptClientFailure: (...arguments_) => iconFailures.push(arguments_)
};
iconElement.connectedCallback();
iconElement.activateMap(1, 9, 10);
iconElement.setScene(iconScene);
iconElement.setMapViewport(1, 9, 10, 5, 400, 300, 30, 40, 1);
await new Promise(resolve => setTimeout(resolve, 0));
flushPaint();
assert.equal(iconElement.sceneGeneration, 10);
assert.equal(iconElement.viewportGeneration, 5);
assert.deepEqual(iconElement.viewport,
  {width: 400, height: 300, centerX: 30, centerY: 40, worldUnitsPerPixel: 1});
assert.equal(iconFailures.length, 0);
assert.equal(fetches.length, 1);
assert.equal(fetches[0][0], './VAADIN/dynamic/resource/token/icon.mmri');
assert.equal(fetches[0][1].credentials, 'same-origin');
assert.equal(fetches[0][1].redirect, 'error');
assert.ok(iconElement.canvas.operations.some(operation => operation[0] === 'drawImage'));
let resolvePendingIconFetch;
globalThis.fetch = () => new Promise(resolve => { resolvePendingIconFetch = resolve; });
const pendingOverlayScene = structuredClone(iconScene);
pendingOverlayScene.sceneGeneration = 11;
iconElement.interactionLayers = structuredClone(scene.layers.slice(0, 1));
iconElement.setScene(pendingOverlayScene);
iconElement.setInteractionOverlay(1, 9, 11, pendingOverlayScene.viewportGeneration, []);
assert.equal(iconElement.pendingInteractionOverlay.layers.length, 0);
resolvePendingIconFetch({ok: true, arrayBuffer: async () => iconBytes.buffer.slice(0)});
await new Promise(resolve => setTimeout(resolve, 0));
assert.equal(iconElement.sceneGeneration, 11);
assert.equal(iconElement.interactionLayers.length, 0);
const hostileIconScene = structuredClone(iconScene);
hostileIconScene.sceneGeneration = 12;
hostileIconScene.layers[0].features[0].primitives[0].resource = 'https://evil.example/icon';
iconElement.setScene(hostileIconScene);
assert.equal(iconFailures.at(-1)[3], 'RESOURCE_UNAVAILABLE');
assert.equal(iconElement.sceneGeneration, 11);
globalThis.fetch = async () => { throw new TypeError('browser-specific network failure'); };
const expiredIconScene = structuredClone(iconScene);
expiredIconScene.sceneGeneration = 12;
iconElement.setScene(expiredIconScene);
await new Promise(resolve => setTimeout(resolve, 0));
assert.equal(iconFailures.at(-1)[3], 'RESOURCE_UNAVAILABLE');
assert.equal(iconElement.sceneGeneration, 11);
const iconReplacement = structuredClone(scene);
iconReplacement.componentGeneration = 9;
iconReplacement.sceneGeneration = 12;
iconElement.setScene(iconReplacement);
assert.equal(iconElement.sceneGeneration, 12);
assert.equal(iconElement.iconResources.size, 0);
iconElement.deactivateMap(1, 9);
assert.equal(iconElement.scene, null);

const routedCalls = [];
const resumeCalls = [];
const interactionElement = new ElementClass();
interactionElement.$server = {
  acceptClientFailure: (...arguments_) => assert.fail(`interaction failure ${arguments_}`),
  acceptLabelMeasurements: () => {},
  acceptPlacedLabels: () => {},
  acceptSettledViewport: () => {},
  acceptMapInteraction: async (...arguments_) => {
    routedCalls.push(arguments_);
    const type = arguments_[5];
    return {accepted: true, suppressDefault: type !== 'MOVE' && type !== 'CLICK',
      captured: type === 'PRESS' || type === 'DRAG', cursor: 'CROSSHAIR'};
  },
  acceptMapCommand: async (...arguments_) => {
    routedCalls.push(arguments_);
    return {accepted: true, suppressDefault: true, captured: false, cursor: 'HAND'};
  },
  acceptMapToolResume: async (...arguments_) => {
    resumeCalls.push(arguments_);
    return {accepted: true, suppressDefault: false, captured: false, cursor: 'HAND'};
  }
};
interactionElement.connectedCallback();
interactionElement.activateMap(1, 2, 3);
interactionElement.setScene(structuredClone(scene));
interactionElement.setInteractionOverlay(1, 2, 3, 4,
  structuredClone(scene.layers.slice(0, 1)));
flushPaint();
assert.equal(interactionElement.interactionLayers.length, 1);
assert.ok(interactionElement.canvas.operations.filter(operation => operation[0] === 'fill').length >= 2);
const hoverLayer = structuredClone(scene.layers[0]);
hoverLayer.id = '__hover';
const selectionLayer = structuredClone(scene.layers[0]);
selectionLayer.id = '__selection';
interactionElement.interactionLayers = [hoverLayer, selectionLayer];
interactionElement.afterLocalNavigation(false);
assert.deepEqual(interactionElement.interactionLayers.map(layer => layer.id), ['__selection']);

interactionElement.canvas.dispatch('pointermove', {offsetX: 20, offsetY: 30, buttons: 0,
  button: -1, detail: 0});
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'MOVE');
assert.deepEqual(routedCalls.at(-1).slice(6, 8), [20, 30]);
interactionElement.canvas.dispatch('click', {offsetX: 20, offsetY: 30, buttons: 0,
  button: 0, detail: 1});
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'CLICK');
let contextMenuPrevented = false;
interactionElement.canvas.dispatch('contextmenu', {offsetX: 20, offsetY: 30, buttons: 0,
  button: 2, detail: 1, preventDefault() { contextMenuPrevented = true; }});
await interactionElement.interactionChain;
assert.equal(contextMenuPrevented, true);
assert.equal(routedCalls.at(-1)[5], 'CLICK');
assert.equal(routedCalls.at(-1)[13], true);
let auxClickPrevented = false;
interactionElement.canvas.dispatch('auxclick', {offsetX: 20, offsetY: 30, buttons: 0,
  button: 1, detail: 1, preventDefault() { auxClickPrevented = true; }});
await interactionElement.interactionChain;
assert.equal(auxClickPrevented, true);
assert.equal(routedCalls.at(-1)[5], 'CLICK');
assert.equal(routedCalls.at(-1)[8], 2);
interactionElement.canvas.dispatch('pointerleave', {offsetX: 21, offsetY: 30,
  button: -1, buttons: 0, detail: 0});
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'CANCEL');
assert.equal(routedCalls.at(-1).at(-1), 'POINTER_EXITED');

interactionElement.setToolState(true, false, 'DEFAULT');
interactionElement.canvas.dispatch('pointerdown', {pointerId: 6, offsetX: 30, offsetY: 30,
  button: 0, buttons: 1, detail: 1});
interactionElement.canvas.dispatch('pointerleave', {pointerId: 6, offsetX: 31, offsetY: 30,
  button: -1, buttons: 1, detail: 0});
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'CANCEL');
assert.equal(routedCalls.at(-1).at(-1), 'POINTER_EXITED');
assert.equal(interactionElement.pointers.size, 0);

interactionElement.setToolState(true, false, 'DEFAULT');
interactionElement.canvas.dispatch('pointerdown', {pointerId: 9, offsetX: 30, offsetY: 30,
  button: 0, buttons: 1, detail: 1});
interactionElement.canvas.dispatch('pointerdown', {pointerId: 10, offsetX: 60, offsetY: 30,
  button: 0, buttons: 1, detail: 1});
interactionElement.canvas.dispatch('pointercancel', {pointerId: 9, offsetX: 30, offsetY: 30,
  button: -1, buttons: 0, detail: 0});
await interactionElement.interactionChain;
assert.equal(interactionElement.pointers.size, 0);
assert.equal(interactionElement.canvas.hasPointerCapture(9), false);
assert.equal(interactionElement.canvas.hasPointerCapture(10), false);

interactionElement.setToolState(true, false, 'DEFAULT');
interactionElement.canvas.dispatch('pointerdown', {pointerId: 11, offsetX: 30, offsetY: 30,
  button: 0, buttons: 1, detail: 1});
interactionElement.canvas.dispatch('pointermove', {pointerId: 11, offsetX: 31, offsetY: 30,
  button: -1, buttons: 0, detail: 0});
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'CANCEL');
assert.equal(routedCalls.at(-1).at(-1), 'POINTER_STATE_LOST');
assert.equal(interactionElement.pointers.size, 0);

interactionElement.setToolState(true, false, 'DEFAULT');
interactionElement.canvas.dispatch('pointerdown', {pointerId: 13, offsetX: 35, offsetY: 35,
  button: 0, buttons: 1, detail: 1});
interactionElement.canvas.dispatch('pointerdown', {pointerId: 13, offsetX: 35, offsetY: 35,
  button: 2, buttons: 3, detail: 1});
interactionElement.canvas.dispatch('pointerup', {pointerId: 13, offsetX: 35, offsetY: 35,
  button: 2, buttons: 1, detail: 1});
assert.equal(interactionElement.pointers.size, 1);
assert.equal(interactionElement.canvas.hasPointerCapture(13), true);
interactionElement.canvas.dispatch('pointermove', {pointerId: 13, offsetX: 36, offsetY: 35,
  button: -1, buttons: 1, detail: 0});
interactionElement.canvas.dispatch('pointerup', {pointerId: 13, offsetX: 36, offsetY: 35,
  button: 0, buttons: 0, detail: 1});
await interactionElement.interactionChain;
assert.deepEqual(routedCalls.slice(-5).map(call => call[5]),
  ['PRESS', 'PRESS', 'RELEASE', 'DRAG', 'RELEASE']);
assert.equal(interactionElement.pointers.size, 0);

interactionElement.canvas.dispatch('pointerdown', {pointerId: 12, offsetX: 30, offsetY: 30,
  button: 0, buttons: 1, detail: 1});
interactionElement.resetToolState(false, false, 'DEFAULT');
assert.equal(interactionElement.pointers.size, 0);
assert.equal(interactionElement.canvas.hasPointerCapture(12), false);
assert.equal(interactionElement.toolActive, false);

interactionElement.setToolState(true, false, 'MOVE');
assert.equal(interactionElement.canvas.style.cursor, 'move');
interactionElement.canvas.dispatch('pointerdown', {pointerId: 7, offsetX: 40, offsetY: 40,
  button: 0, buttons: 1, detail: 1});
interactionElement.canvas.dispatch('pointermove', {pointerId: 7, offsetX: 45, offsetY: 40,
  button: -1, buttons: 1, detail: 0});
interactionElement.canvas.dispatch('pointerup', {pointerId: 7, offsetX: 45, offsetY: 40,
  button: 0, buttons: 0, detail: 1});
await interactionElement.interactionChain;
assert.deepEqual(routedCalls.slice(-3).map(call => call[5]), ['PRESS', 'DRAG', 'RELEASE']);
assert.equal(interactionElement.toolCaptured, false);
interactionElement.canvas.dispatch('keydown', {key: 'Backspace', preventDefault() {}});
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'DELETE_BACKWARD');
interactionElement.canvas.dispatch('keydown', {key: 'z', ctrlKey: true, preventDefault() {}});
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'UNDO');
interactionElement.canvas.dispatch('keydown',
  {key: 'Z', metaKey: true, shiftKey: true, preventDefault() {}});
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'REDO');
interactionElement.canvas.dispatch('pointerdown', {pointerId: 14, offsetX: 42, offsetY: 43,
  button: 0, buttons: 1, detail: 1, shiftKey: true});
interactionElement.canvas.dispatch('blur', {});
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'CANCEL');
assert.equal(routedCalls.at(-1).at(-1), 'FOCUS_LOST');
assert.deepEqual(routedCalls.at(-1).slice(6, 8), [42, 43]);
assert.equal(routedCalls.at(-1)[9], 1);
interactionElement.canvas.dispatch('focus', {});
await interactionElement.interactionChain;
assert.equal(resumeCalls.length, 1);
assert.equal(interactionElement.canvas.style.cursor, 'pointer');

interactionElement.canvas.dispatch('pointerdown', {pointerId: 8, offsetX: 40, offsetY: 40,
  button: 0, buttons: 1, detail: 1});
interactionElement.clientWidth = 801;
interactionElement.resizeCanvas();
await interactionElement.interactionChain;
assert.equal(routedCalls.at(-1)[5], 'CANCEL');
assert.equal(routedCalls.at(-1).at(-1), 'POINTER_STATE_LOST');
assert.equal(routedCalls.at(-1)[9], 1);

interactionElement.setToolState(false, false, 'DEFAULT');
const keyboardCenter = interactionElement.viewport.centerX;
interactionElement.canvas.dispatch('keydown', {key: 'ArrowLeft', altKey: false, ctrlKey: false,
  metaKey: false, shiftKey: false, preventDefault() {}});
assert.notEqual(interactionElement.viewport.centerX, keyboardCenter);
interactionElement.disconnectedCallback();

let resolveDelayedInteraction;
const delayedElement = new ElementClass();
delayedElement.$server = {
  acceptClientFailure: (...arguments_) => assert.fail(`delayed failure ${arguments_}`),
  acceptSettledViewport: () => {},
  acceptMapInteraction: () => new Promise(resolve => { resolveDelayedInteraction = resolve; })
};
delayedElement.connectedCallback();
delayedElement.activateMap(1, 2, 3);
delayedElement.setScene(structuredClone(scene));
delayedElement.setToolState(true, false, 'DEFAULT');
const delayedScale = delayedElement.viewport.worldUnitsPerPixel;
delayedElement.canvas.dispatch('wheel', {offsetX: 20, offsetY: 30, deltaY: -100,
  button: -1, buttons: 0, preventDefault() {}});
await new Promise(resolve => setTimeout(resolve, 0));
delayedElement.setMapEnabled(false);
resolveDelayedInteraction({accepted: true, suppressDefault: false, captured: false,
  cursor: 'HAND'});
await delayedElement.interactionChain;
assert.equal(delayedElement.viewport.worldUnitsPerPixel, delayedScale);
assert.notEqual(delayedElement.canvas.style.cursor, 'pointer');

let resolveBoundedInteraction;
const boundedCalls = [];
const boundedElement = new ElementClass();
boundedElement.$server = {
  acceptClientFailure: (...arguments_) => assert.fail(`bounded failure ${arguments_}`),
  acceptSettledViewport: () => {},
  acceptMapInteraction: (...arguments_) => {
    boundedCalls.push(arguments_);
    if (boundedCalls.length === 1) {
      return new Promise(resolve => { resolveBoundedInteraction = resolve; });
    }
    return Promise.resolve({accepted: true, suppressDefault: true,
      captured: false, cursor: 'DEFAULT'});
  }
};
boundedElement.connectedCallback();
boundedElement.activateMap(1, 2, 3);
boundedElement.setScene(structuredClone(scene));
boundedElement.setToolState(true, false, 'DEFAULT');
boundedElement.canvas.dispatch('pointerdown', {pointerId: 20, offsetX: 10, offsetY: 10,
  button: 0, buttons: 1, detail: 1});
await new Promise(resolve => setTimeout(resolve, 0));
for (let index = 0; index < 40; index++) {
  boundedElement.canvas.dispatch('pointermove', {pointerId: 20,
    offsetX: 11 + index, offsetY: 10, button: -1, buttons: 1, detail: 0});
}
for (let index = 0; index < 40; index++) {
  boundedElement.canvas.dispatch('blur', {offsetX: 50, offsetY: 50, buttons: 1});
}
assert.ok(boundedElement.pendingToolEvents <= 33);
await new Promise(resolve => setTimeout(resolve, 0));
resolveBoundedInteraction({accepted: true, suppressDefault: true,
  captured: true, cursor: 'CROSSHAIR'});
await boundedElement.interactionChain;
await new Promise(resolve => setTimeout(resolve, 0));
await boundedElement.interactionChain;
assert.equal(boundedCalls.at(-1)[5], 'CANCEL');
assert.equal(boundedCalls.at(-1).at(-1), 'POINTER_STATE_LOST');
assert.equal(boundedElement.toolCaptured, false);
assert.equal(boundedElement.pointers.size, 0);
assert.equal(boundedElement.pendingToolEvents, 0);

let resolveBoundedCommand;
const commandCalls = [];
const boundedCommandElement = new ElementClass();
boundedCommandElement.$server = {
  acceptClientFailure: (...arguments_) => assert.fail(`command failure ${arguments_}`),
  acceptSettledViewport: () => {},
  acceptMapCommand: (...arguments_) => {
    commandCalls.push(arguments_);
    if (commandCalls.length === 1) {
      return new Promise(resolve => { resolveBoundedCommand = resolve; });
    }
    return Promise.resolve({accepted: true, suppressDefault: true,
      captured: false, cursor: 'DEFAULT'});
  },
  acceptMapInteraction: (...arguments_) => {
    commandCalls.push(arguments_);
    return Promise.resolve({accepted: true, suppressDefault: true,
      captured: false, cursor: 'DEFAULT'});
  }
};
boundedCommandElement.connectedCallback();
boundedCommandElement.activateMap(1, 2, 3);
boundedCommandElement.setScene(structuredClone(scene));
boundedCommandElement.setToolState(true, false, 'DEFAULT');
boundedCommandElement.canvas.dispatch('keydown',
  {key: 'Backspace', preventDefault() {}});
await new Promise(resolve => setTimeout(resolve, 0));
for (let index = 1; index < 40; index++) {
  boundedCommandElement.canvas.dispatch('keydown',
    {key: 'Backspace', preventDefault() {}});
}
assert.ok(boundedCommandElement.pendingToolEvents <= 33);
await new Promise(resolve => setTimeout(resolve, 0));
resolveBoundedCommand({accepted: true, suppressDefault: true,
  captured: false, cursor: 'DEFAULT'});
await boundedCommandElement.interactionChain;
await new Promise(resolve => setTimeout(resolve, 0));
await boundedCommandElement.interactionChain;
assert.equal(boundedCommandElement.pendingToolEvents, 0);
assert.ok(commandCalls.length <= 2);

const rejectedCalls = [];
const rejectedFailures = [];
const rejectedElement = new ElementClass();
rejectedElement.$server = {
  acceptClientFailure: (...arguments_) => rejectedFailures.push(arguments_),
  acceptSettledViewport: () => {},
  acceptMapInteraction: (...arguments_) => {
    rejectedCalls.push(arguments_);
    if (arguments_[5] === 'PRESS') return Promise.reject(new Error('TOOL_CALLBACK_FAILED'));
    return Promise.resolve({accepted: true, suppressDefault: true,
      captured: false, cursor: 'DEFAULT'});
  }
};
rejectedElement.connectedCallback();
rejectedElement.activateMap(1, 2, 3);
rejectedElement.setScene(structuredClone(scene));
rejectedElement.setToolState(true, true, 'CROSSHAIR');
rejectedElement.canvas.dispatch('pointerdown', {pointerId: 21, offsetX: 10, offsetY: 10,
  button: 0, buttons: 1, detail: 1});
await rejectedElement.interactionChain;
await new Promise(resolve => setTimeout(resolve, 0));
await rejectedElement.interactionChain;
assert.equal(rejectedCalls.at(-1)[5], 'CANCEL');
assert.equal(rejectedElement.toolCaptured, false);
assert.equal(rejectedElement.pointers.size, 0);
assert.equal(rejectedFailures.at(-1)[3], 'TOOL_CALLBACK_FAILED');

const rejectedCancelFailures = [];
const rejectedCancelElement = new ElementClass();
rejectedCancelElement.$server = {
  acceptClientFailure: (...arguments_) => rejectedCancelFailures.push(arguments_),
  acceptSettledViewport: () => {},
  acceptMapInteraction: async (...arguments_) => {
    if (arguments_[5] === 'CANCEL') throw new Error('CANCEL_CALLBACK_FAILED');
    return {accepted: true, suppressDefault: true, captured: true, cursor: 'CROSSHAIR'};
  }
};
rejectedCancelElement.connectedCallback();
rejectedCancelElement.activateMap(1, 2, 3);
rejectedCancelElement.setScene(structuredClone(scene));
rejectedCancelElement.setToolState(true, false, 'DEFAULT');
rejectedCancelElement.canvas.dispatch('pointerdown', {pointerId: 23, offsetX: 10, offsetY: 10,
  button: 0, buttons: 1, detail: 1});
await rejectedCancelElement.interactionChain;
rejectedCancelElement.canvas.dispatch('blur', {offsetX: 10, offsetY: 10, buttons: 1});
await rejectedCancelElement.interactionChain;
assert.equal(rejectedCancelElement.toolCaptured, false);
assert.equal(rejectedCancelElement.canvas.style.cursor, 'default');
assert.equal(rejectedCancelFailures.at(-1)[3], 'CANCEL_CALLBACK_FAILED');

let resolveFirstDrag;
const passDragCalls = [];
const transientCalls = [];
const passDragElement = new ElementClass();
passDragElement.$server = {
  acceptClientFailure: (...arguments_) => assert.fail(`pass drag failure ${arguments_}`),
  acceptSettledViewport: () => {},
  acceptTransientViewport: async (...arguments_) => transientCalls.push(arguments_),
  acceptMapInteraction: async (...arguments_) => {
    passDragCalls.push(arguments_);
    if (arguments_[5] === 'PRESS') {
      return {accepted: true, suppressDefault: true, captured: true, cursor: 'CROSSHAIR'};
    }
    if (arguments_[5] === 'DRAG' && !resolveFirstDrag) {
      return new Promise(resolve => { resolveFirstDrag = resolve; });
    }
    return {accepted: true, suppressDefault: false, captured: false, cursor: 'DEFAULT'};
  }
};
passDragElement.connectedCallback();
passDragElement.activateMap(1, 2, 3);
passDragElement.setScene(structuredClone(scene));
passDragElement.setToolState(true, false, 'DEFAULT');
passDragElement.canvas.dispatch('pointerdown', {pointerId: 22, offsetX: 10, offsetY: 10,
  button: 0, buttons: 1, detail: 1});
await passDragElement.interactionChain;
passDragElement.canvas.dispatch('pointermove', {pointerId: 22, offsetX: 20, offsetY: 10,
  button: -1, buttons: 1, detail: 0});
await new Promise(resolve => setTimeout(resolve, 0));
passDragElement.canvas.dispatch('pointermove', {pointerId: 22, offsetX: 30, offsetY: 10,
  button: -1, buttons: 1, detail: 0});
resolveFirstDrag({accepted: true, suppressDefault: false,
  captured: false, cursor: 'DEFAULT'});
await passDragElement.interactionChain;
assert.deepEqual(passDragCalls.map(call => call[5]), ['PRESS', 'DRAG', 'DRAG']);
assert.equal(transientCalls.length, 2);
assert.deepEqual(passDragCalls.slice(1).map(call => call[3]), [4, 5]);

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
