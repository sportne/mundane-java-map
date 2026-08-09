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
const hostileIconScene = structuredClone(iconScene);
hostileIconScene.sceneGeneration = 11;
hostileIconScene.layers[0].features[0].primitives[0].resource = 'https://evil.example/icon';
iconElement.setScene(hostileIconScene);
assert.equal(iconFailures.at(-1)[3], 'RESOURCE_UNAVAILABLE');
assert.equal(iconElement.sceneGeneration, 10);
globalThis.fetch = async () => { throw new TypeError('browser-specific network failure'); };
const expiredIconScene = structuredClone(iconScene);
expiredIconScene.sceneGeneration = 11;
iconElement.setScene(expiredIconScene);
await new Promise(resolve => setTimeout(resolve, 0));
assert.equal(iconFailures.at(-1)[3], 'RESOURCE_UNAVAILABLE');
assert.equal(iconElement.sceneGeneration, 10);
const iconReplacement = structuredClone(scene);
iconReplacement.componentGeneration = 9;
iconReplacement.sceneGeneration = 11;
iconElement.setScene(iconReplacement);
assert.equal(iconElement.sceneGeneration, 11);
assert.equal(iconElement.iconResources.size, 0);
iconElement.deactivateMap(1, 9);
assert.equal(iconElement.scene, null);

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
