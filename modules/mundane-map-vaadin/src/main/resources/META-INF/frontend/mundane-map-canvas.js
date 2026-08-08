const PROTOCOL_VERSION = 1;
const MAX_LAYERS = 64;
const MAX_FEATURES = 50000;
const MAX_PRIMITIVES = 200000;
const MAX_COORDINATE_PAIRS = 2000000;
const MAX_PATH_COMMANDS = 2000000;
const MAX_LOGICAL_BYTES = 64 * 1024 * 1024;
const MAX_BACKING_PIXELS = 67108864;
const MAX_BACKING_EDGE = 16384;
const MAX_DPR = 4;

export function validateViewport(candidate) {
  if (!candidate || !Number.isInteger(candidate.width) || candidate.width <= 0 ||
      !Number.isInteger(candidate.height) || candidate.height <= 0 ||
      !Number.isFinite(candidate.centerX) || !Number.isFinite(candidate.centerY) ||
      !Number.isFinite(candidate.worldUnitsPerPixel) || candidate.worldUnitsPerPixel <= 0) {
    throw new Error('NON_FINITE_VALUE');
  }
  if (candidate.width > MAX_BACKING_EDGE || candidate.height > MAX_BACKING_EDGE) {
    throw new Error('LIMIT_EXCEEDED');
  }
  const halfWidth = candidate.width * candidate.worldUnitsPerPixel / 2;
  const halfHeight = candidate.height * candidate.worldUnitsPerPixel / 2;
  if (![halfWidth, halfHeight, candidate.centerX - halfWidth,
    candidate.centerX + halfWidth, candidate.centerY - halfHeight,
    candidate.centerY + halfHeight].every(Number.isFinite)) {
    throw new Error('NON_FINITE_VALUE');
  }
  return Object.freeze({...candidate});
}

export function resizeViewport(viewport, width, height) {
  return validateViewport({...viewport, width, height});
}

export function panViewport(viewport, deltaX, deltaY) {
  if (!Number.isFinite(deltaX) || !Number.isFinite(deltaY)) {
    throw new Error('NON_FINITE_VALUE');
  }
  return validateViewport({
    ...viewport,
    centerX: viewport.centerX - deltaX * viewport.worldUnitsPerPixel,
    centerY: viewport.centerY + deltaY * viewport.worldUnitsPerPixel
  });
}

export function zoomViewport(viewport, screenX, screenY, factor) {
  if (!Number.isFinite(screenX) || !Number.isFinite(screenY) ||
      !Number.isFinite(factor) || factor <= 0) {
    throw new Error('NON_FINITE_VALUE');
  }
  const beforeX = viewport.centerX +
    (screenX - viewport.width / 2) * viewport.worldUnitsPerPixel;
  const beforeY = viewport.centerY -
    (screenY - viewport.height / 2) * viewport.worldUnitsPerPixel;
  const nextUnits = viewport.worldUnitsPerPixel / factor;
  return validateViewport({
    ...viewport,
    centerX: beforeX - (screenX - viewport.width / 2) * nextUnits,
    centerY: beforeY + (screenY - viewport.height / 2) * nextUnits,
    worldUnitsPerPixel: nextUnits
  });
}

export function collectDrawOrder(scene) {
  return scene.layers.flatMap(layer => layer.features.flatMap(feature =>
    feature.primitives.map((_primitive, index) => `${layer.id}/${feature.id}/${index}`)));
}

function rgba(channels, opacity = 1) {
  return `rgba(${channels[0]},${channels[1]},${channels[2]},${channels[3] / 255 * opacity})`;
}

function validateGeneration(value) {
  return Number.isSafeInteger(value) && value >= 0;
}

function validateColor(value) {
  if (!Array.isArray(value) || value.length !== 4 ||
      value.some(channel => typeof channel !== 'number')) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (!value.every(Number.isFinite)) {
    throw new Error('NON_FINITE_VALUE');
  }
  if (!value.every(channel => Number.isInteger(channel) && channel >= 0 && channel <= 255)) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  return true;
}

function validateCoordinates(coordinates, minimumPairs) {
  if (!Array.isArray(coordinates) || coordinates.length < minimumPairs * 2 ||
      coordinates.length % 2 !== 0) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (!coordinates.every(Number.isFinite)) {
    throw new Error('NON_FINITE_VALUE');
  }
  return coordinates.length / 2;
}

function validatePath(path) {
  const arities = {MOVE_TO: 2, LINE_TO: 2, QUADRATIC_TO: 4, CUBIC_TO: 6, CLOSE: 0};
  if (!path || !Array.isArray(path.commands) || !path.commands.length ||
      !Array.isArray(path.ordinates) ||
      path.commands.some(command => !(command in arities)) ||
      path.commands.reduce((sum, command) => sum + arities[command], 0) !==
        path.ordinates.length) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (!path.ordinates.every(Number.isFinite)) {
    throw new Error('NON_FINITE_VALUE');
  }
}

function validateStroke(stroke) {
  if (!stroke || !validateColor(stroke.color) || typeof stroke.width !== 'number') {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (!Number.isFinite(stroke.width)) {
    throw new Error('NON_FINITE_VALUE');
  }
  if (stroke.width <= 0) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
}

function validateOpacity(value) {
  if (!Number.isFinite(value)) {
    throw new Error('NON_FINITE_VALUE');
  }
  if (value < 0 || value > 1) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
}

function logicalStringBytes(value) {
  return 4 + new TextEncoder().encode(value).length;
}

function logicalNumberArrayBytes(value) {
  return 4 + value.length * 8;
}

function detachPrimitive(primitive) {
  if (primitive.kind === 'point') {
    return {
      kind: 'point',
      coordinate: [...primitive.coordinate],
      path: {commands: [...primitive.path.commands], ordinates: [...primitive.path.ordinates]},
      viewBox: [...primitive.viewBox],
      size: primitive.size,
      fill: [...primitive.fill],
      opacity: primitive.opacity
    };
  }
  if (primitive.kind === 'line') {
    return {
      kind: 'line',
      coordinates: [...primitive.coordinates],
      stroke: {color: [...primitive.stroke.color], width: primitive.stroke.width},
      opacity: primitive.opacity
    };
  }
  const outline = primitive.outline.present ? {
    present: true,
    stroke: {color: [...primitive.outline.stroke.color], width: primitive.outline.stroke.width},
    opacity: primitive.outline.opacity
  } : {present: false};
  return {
    kind: 'polygon',
    rings: primitive.rings.map(ring => [...ring]),
    fill: [...primitive.fill],
    outline,
    opacity: primitive.opacity
  };
}

function detachScene(scene) {
  return {
    protocolVersion: scene.protocolVersion,
    componentGeneration: scene.componentGeneration,
    sceneGeneration: scene.sceneGeneration,
    viewportGeneration: scene.viewportGeneration,
    background: [...scene.background],
    viewport: {...scene.viewport},
    layers: scene.layers.map(layer => ({
      id: layer.id,
      name: layer.name,
      features: layer.features.map(feature => ({
        id: feature.id,
        name: feature.name,
        primitives: feature.primitives.map(detachPrimitive)
      }))
    }))
  };
}

function deepFreeze(value) {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    for (const nested of Object.values(value)) {
      deepFreeze(nested);
    }
    Object.freeze(value);
  }
  return value;
}

export function logicalSceneBytes(scene) {
  let size = 4 * 8 + logicalNumberArrayBytes(scene.background) + 5 * 8 + 4;
  for (const layer of scene.layers) {
    size += logicalStringBytes(layer.id) + logicalStringBytes(layer.name) + 4;
    for (const feature of layer.features) {
      size += logicalStringBytes(feature.id) + logicalStringBytes(feature.name) + 4;
      for (const primitive of feature.primitives) {
        size += 1;
        if (primitive.kind === 'point') {
          size += logicalNumberArrayBytes(primitive.coordinate) + 4 +
            primitive.path.commands.length + logicalNumberArrayBytes(primitive.path.ordinates) +
            logicalNumberArrayBytes(primitive.viewBox) + 2 * 8 +
            logicalNumberArrayBytes(primitive.fill);
        } else if (primitive.kind === 'line') {
          size += logicalNumberArrayBytes(primitive.coordinates) +
            logicalNumberArrayBytes(primitive.stroke.color) + 2 * 8;
        } else {
          size += 4 + primitive.rings.reduce((sum, ring) =>
            sum + logicalNumberArrayBytes(ring), 0) + logicalNumberArrayBytes(primitive.fill) +
            1 + 8;
          if (primitive.outline.present) {
            size += logicalNumberArrayBytes(primitive.outline.stroke.color) + 2 * 8;
          }
        }
      }
    }
  }
  return size;
}

function validateSceneIdentity(candidate, currentComponentGeneration, currentSceneGeneration) {
  if (!candidate || candidate.protocolVersion !== PROTOCOL_VERSION) {
    throw new Error('PROTOCOL_VERSION_UNSUPPORTED');
  }
  if (!validateGeneration(candidate.componentGeneration) ||
      !validateGeneration(candidate.sceneGeneration) ||
      !validateGeneration(candidate.viewportGeneration) ||
      candidate.componentGeneration !== currentComponentGeneration ||
      candidate.sceneGeneration <= currentSceneGeneration) {
    throw new Error('STALE_GENERATION');
  }
}

export function validateScene(candidate, currentComponentGeneration, currentSceneGeneration) {
  validateSceneIdentity(candidate, currentComponentGeneration, currentSceneGeneration);
  validateViewport(candidate.viewport);
  if (!validateColor(candidate.background) || !Array.isArray(candidate.layers)) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (candidate.layers.length > MAX_LAYERS) {
    throw new Error('LIMIT_EXCEEDED');
  }
  const layerIds = new Set();
  let featureCount = 0;
  let primitiveCount = 0;
  let coordinatePairs = 0;
  let pathCommands = 0;
  for (const layer of candidate.layers) {
    if (!layer || typeof layer.id !== 'string' || !layer.id ||
        typeof layer.name !== 'string' || !Array.isArray(layer.features)) {
      throw new Error('SYMBOL_UNSUPPORTED');
    }
    if (layer.id.length > 256 || layer.name.length > 4096) {
      throw new Error('LIMIT_EXCEEDED');
    }
    if (layerIds.has(layer.id)) {
      throw new Error('DUPLICATE_ID');
    }
    layerIds.add(layer.id);
    const featureIds = new Set();
    featureCount += layer.features.length;
    if (featureCount > MAX_FEATURES) {
      throw new Error('LIMIT_EXCEEDED');
    }
    for (const feature of layer.features) {
      if (!feature || typeof feature.id !== 'string' || !feature.id ||
          typeof feature.name !== 'string' || !Array.isArray(feature.primitives)) {
        throw new Error('SYMBOL_UNSUPPORTED');
      }
      if (feature.id.length > 256 || feature.name.length > 4096) {
        throw new Error('LIMIT_EXCEEDED');
      }
      if (featureIds.has(feature.id)) {
        throw new Error('DUPLICATE_ID');
      }
      featureIds.add(feature.id);
      primitiveCount += feature.primitives.length;
      if (primitiveCount > MAX_PRIMITIVES) {
        throw new Error('LIMIT_EXCEEDED');
      }
      for (const primitive of feature.primitives) {
        if (!primitive || typeof primitive.kind !== 'string') {
          throw new Error('SYMBOL_UNSUPPORTED');
        }
        if (primitive.kind === 'point') {
          coordinatePairs += validateCoordinates(primitive.coordinate, 1);
          validatePath(primitive.path);
          if (primitive.coordinate.length !== 2 ||
              !Array.isArray(primitive.viewBox) || primitive.viewBox.length !== 4 ||
              !validateColor(primitive.fill) || typeof primitive.size !== 'number') {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          if (!primitive.viewBox.every(Number.isFinite) || !Number.isFinite(primitive.size)) {
            throw new Error('NON_FINITE_VALUE');
          }
          if (primitive.viewBox[2] <= primitive.viewBox[0] ||
              primitive.viewBox[3] <= primitive.viewBox[1] || primitive.size <= 0) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          pathCommands += primitive.path.commands.length;
        } else if (primitive.kind === 'line') {
          coordinatePairs += validateCoordinates(primitive.coordinates, 2);
          validateStroke(primitive.stroke);
        } else if (primitive.kind === 'polygon') {
          if (!Array.isArray(primitive.rings) || !primitive.rings.length ||
              !validateColor(primitive.fill)) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          for (const ring of primitive.rings) {
            coordinatePairs += validateCoordinates(ring, 4);
            if (ring[0] !== ring[ring.length - 2] || ring[1] !== ring[ring.length - 1]) {
              throw new Error('SYMBOL_UNSUPPORTED');
            }
          }
          if (!primitive.outline || typeof primitive.outline.present !== 'boolean') {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          if (primitive.outline.present) {
            validateStroke(primitive.outline.stroke);
            validateOpacity(primitive.outline.opacity);
          }
        } else {
          throw new Error('SYMBOL_UNSUPPORTED');
        }
        validateOpacity(primitive.opacity);
      }
    }
  }
  if (featureCount > MAX_FEATURES || primitiveCount > MAX_PRIMITIVES ||
      coordinatePairs > MAX_COORDINATE_PAIRS || pathCommands > MAX_PATH_COMMANDS) {
    throw new Error('LIMIT_EXCEEDED');
  }
  if (logicalSceneBytes(candidate) > MAX_LOGICAL_BYTES) {
    throw new Error('LIMIT_EXCEEDED');
  }
  return deepFreeze(detachScene(candidate));
}

export class MundaneMapCanvas extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({mode: 'open'});
    this.canvas = document.createElement('canvas');
    this.canvas.setAttribute('part', 'canvas');
    this.canvas.setAttribute('aria-label', 'Interactive map');
    this.canvas.setAttribute('role', 'img');
    const style = document.createElement('style');
    style.textContent = ':host{display:block;min-width:1px;min-height:1px;overflow:hidden}' +
      'canvas{display:block;width:100%;height:100%;touch-action:none;outline:none}';
    this.shadowRoot.append(style, this.canvas);
    this.context = this.canvas.getContext('2d');
    this.missingCapability = [
      ['CanvasRenderingContext2D', this.context],
      ['ResizeObserver', typeof ResizeObserver === 'function'],
      ['PointerEvent', typeof PointerEvent === 'function'],
      ['requestAnimationFrame', typeof requestAnimationFrame === 'function'],
      ['AbortController', typeof AbortController === 'function'],
      ['fetch', typeof fetch === 'function'],
      ['TextEncoder', typeof TextEncoder === 'function'],
      ['Uint8Array', typeof Uint8Array === 'function']
    ].find(entry => !entry[1])?.[0] || null;
    this.scene = null;
    this.viewport = validateViewport({width: 800, height: 600, centerX: 0,
      centerY: 0, worldUnitsPerPixel: 100000});
    this.componentGeneration = 0;
    this.sceneGeneration = -1;
    this.viewportGeneration = 0;
    this.eventSequence = 0;
    this.enabled = true;
    this.closed = false;
    this.active = false;
    this.paintFrame = 0;
    this.settleTimer = 0;
    this.settledRateTimer = 0;
    this.settledTokens = 10;
    this.settledRefillMilliseconds = performance.now();
    this.pointers = new Map();
    this.lastPinch = null;
    this.boundPointerDown = event => this.onPointerDown(event);
    this.boundPointerMove = event => this.onPointerMove(event);
    this.boundPointerUp = event => this.onPointerUp(event);
    this.boundWheel = event => this.onWheel(event);
    this.boundWindowResize = () => this.resizeCanvas();
    this.resizeObserver = this.missingCapability ? null :
      new ResizeObserver(() => this.resizeCanvas());
  }

  connectedCallback() {
    if (!this.closed && this.enabled) {
      this.setup();
    }
  }

  disconnectedCallback() {
    this.teardown();
  }

  activateMap(version, componentGeneration, sceneGeneration) {
    if (version !== PROTOCOL_VERSION || !validateGeneration(componentGeneration) ||
        !validateGeneration(sceneGeneration) || this.closed) {
      this.reportFailure('PROTOCOL_VERSION_UNSUPPORTED');
      return;
    }
    this.componentGeneration = componentGeneration;
    this.sceneGeneration = sceneGeneration - 1;
    this.expectedSceneGeneration = sceneGeneration;
    this.eventSequence = 0;
    this.settledTokens = 10;
    this.settledRefillMilliseconds = performance.now();
    if (this.missingCapability) {
      this.reportFailure('BROWSER_CAPABILITY_UNSUPPORTED', sceneGeneration);
      return;
    }
    if (this.isConnected && this.enabled) {
      this.setup();
    }
  }

  deactivateMap(version, componentGeneration) {
    if (version === PROTOCOL_VERSION && componentGeneration === this.componentGeneration) {
      this.teardown();
    }
  }

  closeMap(version, componentGeneration) {
    if (version !== PROTOCOL_VERSION || !validateGeneration(componentGeneration)) {
      return;
    }
    this.componentGeneration = componentGeneration;
    this.closed = true;
    this.scene = null;
    this.teardown();
    if (this.context) {
      this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
    }
  }

  setMapEnabled(enabled) {
    this.enabled = Boolean(enabled);
    if (!this.enabled) {
      this.teardown();
    } else if (this.isConnected && !this.closed) {
      this.setup();
    }
  }

  setScene(candidate) {
    try {
      validateSceneIdentity(candidate, this.componentGeneration, this.sceneGeneration);
    } catch (error) {
      this.reportFailure(error.message, validateGeneration(candidate?.sceneGeneration) ?
        candidate.sceneGeneration : Math.max(0, this.sceneGeneration));
      return;
    }
    if (this.missingCapability) {
      const generation = validateGeneration(candidate?.sceneGeneration) ?
        candidate.sceneGeneration : this.expectedSceneGeneration;
      this.reportFailure('BROWSER_CAPABILITY_UNSUPPORTED', generation);
      return;
    }
    try {
      const accepted = validateScene(candidate, this.componentGeneration, this.sceneGeneration);
      this.scene = accepted;
      this.sceneGeneration = accepted.sceneGeneration;
      this.viewportGeneration = accepted.viewportGeneration;
      this.viewport = validateViewport(accepted.viewport);
      this.schedulePaint();
    } catch (error) {
      this.reportFailure(error.message, candidate.sceneGeneration);
    }
  }

  setMapViewport(version, componentGeneration, sceneGeneration, viewportGeneration,
    width, height, centerX, centerY, worldUnitsPerPixel) {
    if (version !== PROTOCOL_VERSION) {
      this.reportFailure('PROTOCOL_VERSION_UNSUPPORTED', sceneGeneration);
      return;
    }
    if (componentGeneration !== this.componentGeneration ||
        sceneGeneration !== this.sceneGeneration || !validateGeneration(viewportGeneration) ||
        viewportGeneration < this.viewportGeneration) {
      this.reportFailure('STALE_GENERATION', sceneGeneration);
      return;
    }
    if (this.missingCapability) {
      this.reportFailure('BROWSER_CAPABILITY_UNSUPPORTED', sceneGeneration);
      return;
    }
    try {
      this.viewport = validateViewport({width, height, centerX, centerY, worldUnitsPerPixel});
      this.viewportGeneration = viewportGeneration;
      this.schedulePaint();
    } catch (error) {
      this.reportFailure(error.message);
    }
  }

  setup() {
    if (this.active || this.missingCapability) {
      return;
    }
    this.active = true;
    this.canvas.tabIndex = 0;
    this.canvas.addEventListener('pointerdown', this.boundPointerDown);
    this.canvas.addEventListener('pointermove', this.boundPointerMove);
    this.canvas.addEventListener('pointerup', this.boundPointerUp);
    this.canvas.addEventListener('pointercancel', this.boundPointerUp);
    this.canvas.addEventListener('wheel', this.boundWheel, {passive: false});
    this.resizeObserver.observe(this);
    window.addEventListener('resize', this.boundWindowResize);
    this.resizeCanvas();
  }

  teardown() {
    if (this.settledRateTimer) {
      clearTimeout(this.settledRateTimer);
      this.settledRateTimer = 0;
    }
    if (!this.active) {
      return;
    }
    this.active = false;
    this.resizeObserver.disconnect();
    window.removeEventListener('resize', this.boundWindowResize);
    this.canvas.removeEventListener('pointerdown', this.boundPointerDown);
    this.canvas.removeEventListener('pointermove', this.boundPointerMove);
    this.canvas.removeEventListener('pointerup', this.boundPointerUp);
    this.canvas.removeEventListener('pointercancel', this.boundPointerUp);
    this.canvas.removeEventListener('wheel', this.boundWheel);
    for (const pointerId of this.pointers.keys()) {
      if (this.canvas.hasPointerCapture(pointerId)) {
        this.canvas.releasePointerCapture(pointerId);
      }
    }
    this.pointers.clear();
    this.lastPinch = null;
    if (this.paintFrame) {
      cancelAnimationFrame(this.paintFrame);
      this.paintFrame = 0;
    }
    if (this.settleTimer) {
      clearTimeout(this.settleTimer);
      this.settleTimer = 0;
    }
  }

  resizeCanvas() {
    if (!this.active) {
      return;
    }
    const width = Math.max(1, Math.round(this.clientWidth || this.viewport.width));
    const height = Math.max(1, Math.round(this.clientHeight || this.viewport.height));
    if (width > MAX_BACKING_EDGE || height > MAX_BACKING_EDGE) {
      this.reportFailure('LIMIT_EXCEEDED');
      return;
    }
    let dpr = Math.min(MAX_DPR, Math.max(1, window.devicePixelRatio || 1));
    dpr = Math.min(dpr, MAX_BACKING_EDGE / width, MAX_BACKING_EDGE / height,
      Math.sqrt(MAX_BACKING_PIXELS / (width * height)));
    dpr = Math.max(Number.EPSILON, dpr);
    const backingWidth = Math.max(1, Math.floor(width * dpr));
    const backingHeight = Math.max(1, Math.floor(height * dpr));
    const changed = width !== this.viewport.width || height !== this.viewport.height;
    this.canvas.width = backingWidth;
    this.canvas.height = backingHeight;
    this.canvas.dataset.devicePixelRatio = String(dpr);
    this.viewport = resizeViewport(this.viewport, width, height);
    this.schedulePaint();
    if (changed && this.scene) {
      this.emitSettled();
    }
  }

  schedulePaint() {
    if (!this.active || this.paintFrame) {
      return;
    }
    this.paintFrame = requestAnimationFrame(() => {
      this.paintFrame = 0;
      this.paint();
    });
  }

  paint() {
    const dpr = Number(this.canvas.dataset.devicePixelRatio || 1);
    this.context.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.context.clearRect(0, 0, this.viewport.width, this.viewport.height);
    if (!this.scene) {
      return;
    }
    this.context.fillStyle = rgba(this.scene.background);
    this.context.fillRect(0, 0, this.viewport.width, this.viewport.height);
    for (const layer of this.scene.layers) {
      for (const feature of layer.features) {
        for (const primitive of feature.primitives) {
          this.drawPrimitive(primitive);
        }
      }
    }
  }

  drawPrimitive(primitive) {
    this.context.save();
    if (primitive.kind === 'point') {
      this.drawPoint(primitive);
    } else if (primitive.kind === 'line') {
      this.drawLine(primitive.coordinates, primitive.stroke, primitive.opacity);
    } else {
      this.drawPolygon(primitive);
    }
    this.context.restore();
  }

  screen(coordinates, index) {
    return [this.viewport.width / 2 +
      (coordinates[index] - this.viewport.centerX) / this.viewport.worldUnitsPerPixel,
    this.viewport.height / 2 -
      (coordinates[index + 1] - this.viewport.centerY) / this.viewport.worldUnitsPerPixel];
  }

  drawLine(coordinates, stroke, opacity) {
    this.context.beginPath();
    for (let index = 0; index < coordinates.length; index += 2) {
      const point = this.screen(coordinates, index);
      if (index === 0) {
        this.context.moveTo(point[0], point[1]);
      } else {
        this.context.lineTo(point[0], point[1]);
      }
    }
    this.context.strokeStyle = rgba(stroke.color, opacity);
    this.context.lineWidth = stroke.width;
    this.context.lineCap = 'round';
    this.context.lineJoin = 'round';
    this.context.stroke();
  }

  drawPolygon(primitive) {
    this.context.beginPath();
    for (const ring of primitive.rings) {
      for (let index = 0; index < ring.length; index += 2) {
        const point = this.screen(ring, index);
        if (index === 0) {
          this.context.moveTo(point[0], point[1]);
        } else {
          this.context.lineTo(point[0], point[1]);
        }
      }
      this.context.closePath();
    }
    this.context.fillStyle = rgba(primitive.fill, primitive.opacity);
    this.context.fill('evenodd');
    if (primitive.outline.present) {
      this.context.strokeStyle = rgba(primitive.outline.stroke.color,
        primitive.opacity * primitive.outline.opacity);
      this.context.lineWidth = primitive.outline.stroke.width;
      this.context.lineCap = 'round';
      this.context.lineJoin = 'round';
      this.context.stroke();
    }
  }

  drawPoint(primitive) {
    const point = this.screen(primitive.coordinate, 0);
    const viewBox = primitive.viewBox;
    const scaleX = primitive.size / (viewBox[2] - viewBox[0]);
    const scaleY = primitive.size / (viewBox[3] - viewBox[1]);
    this.context.translate(point[0] - primitive.size / 2, point[1] - primitive.size / 2);
    this.context.scale(scaleX, scaleY);
    this.context.translate(-viewBox[0], -viewBox[1]);
    this.context.beginPath();
    let ordinate = 0;
    for (const command of primitive.path.commands) {
      if (command === 'MOVE_TO') {
        this.context.moveTo(primitive.path.ordinates[ordinate],
          primitive.path.ordinates[ordinate + 1]);
        ordinate += 2;
      } else if (command === 'LINE_TO') {
        this.context.lineTo(primitive.path.ordinates[ordinate],
          primitive.path.ordinates[ordinate + 1]);
        ordinate += 2;
      } else if (command === 'QUADRATIC_TO') {
        this.context.quadraticCurveTo(...primitive.path.ordinates.slice(ordinate, ordinate + 4));
        ordinate += 4;
      } else if (command === 'CUBIC_TO') {
        this.context.bezierCurveTo(...primitive.path.ordinates.slice(ordinate, ordinate + 6));
        ordinate += 6;
      } else {
        this.context.closePath();
      }
    }
    this.context.fillStyle = rgba(primitive.fill, primitive.opacity);
    this.context.fill('evenodd');
  }

  onPointerDown(event) {
    if (!this.enabled || this.closed) {
      return;
    }
    this.canvas.setPointerCapture(event.pointerId);
    this.pointers.set(event.pointerId, {x: event.offsetX, y: event.offsetY});
    this.updatePinch();
  }

  onPointerMove(event) {
    const previous = this.pointers.get(event.pointerId);
    if (!previous) {
      return;
    }
    const current = {x: event.offsetX, y: event.offsetY};
    this.pointers.set(event.pointerId, current);
    if (this.pointers.size === 1) {
      this.viewport = panViewport(this.viewport,
        current.x - previous.x, current.y - previous.y);
    } else if (this.pointers.size === 2 && this.lastPinch) {
      const points = [...this.pointers.values()];
      const distance = Math.hypot(points[1].x - points[0].x, points[1].y - points[0].y);
      const centerX = (points[0].x + points[1].x) / 2;
      const centerY = (points[0].y + points[1].y) / 2;
      if (this.lastPinch.distance > 0 && distance > 0) {
        this.viewport = panViewport(this.viewport,
          centerX - this.lastPinch.centerX, centerY - this.lastPinch.centerY);
        this.viewport = zoomViewport(this.viewport, centerX, centerY,
          distance / this.lastPinch.distance);
      }
      this.lastPinch = {distance, centerX, centerY};
    }
    this.schedulePaint();
  }

  onPointerUp(event) {
    if (!this.pointers.has(event.pointerId)) {
      return;
    }
    this.pointers.delete(event.pointerId);
    if (this.canvas.hasPointerCapture(event.pointerId)) {
      this.canvas.releasePointerCapture(event.pointerId);
    }
    this.updatePinch();
    if (!this.pointers.size) {
      this.emitSettled();
    }
  }

  updatePinch() {
    if (this.pointers.size !== 2) {
      this.lastPinch = null;
      return;
    }
    const points = [...this.pointers.values()];
    this.lastPinch = {
      distance: Math.hypot(points[1].x - points[0].x, points[1].y - points[0].y),
      centerX: (points[0].x + points[1].x) / 2,
      centerY: (points[0].y + points[1].y) / 2
    };
  }

  onWheel(event) {
    if (!this.enabled || this.closed) {
      return;
    }
    event.preventDefault();
    const factor = Math.exp(-Math.max(-100, Math.min(100, event.deltaY)) * 0.002);
    this.viewport = zoomViewport(this.viewport, event.offsetX, event.offsetY, factor);
    this.schedulePaint();
    if (this.settleTimer) {
      clearTimeout(this.settleTimer);
    }
    this.settleTimer = setTimeout(() => {
      this.settleTimer = 0;
      this.emitSettled();
    }, 80);
  }

  emitSettled() {
    if (!this.scene || !this.$server || !this.active) {
      return;
    }
    const now = performance.now();
    const elapsed = Math.max(0, now - this.settledRefillMilliseconds);
    this.settledRefillMilliseconds = now;
    this.settledTokens = Math.min(10, this.settledTokens + elapsed / 100);
    if (this.settledTokens < 1) {
      if (!this.settledRateTimer) {
        this.settledRateTimer = setTimeout(() => {
          this.settledRateTimer = 0;
          this.emitSettled();
        }, Math.ceil((1 - this.settledTokens) * 100));
      }
      return;
    }
    this.settledTokens -= 1;
    const reportedGeneration = this.viewportGeneration;
    this.viewportGeneration += 1;
    this.$server.acceptSettledViewport(PROTOCOL_VERSION, this.componentGeneration,
      this.sceneGeneration, reportedGeneration, this.eventSequence++,
      this.viewport.width, this.viewport.height,
      this.viewport.centerX, this.viewport.centerY, this.viewport.worldUnitsPerPixel);
  }

  reportFailure(message, sceneGeneration = Math.max(0, this.sceneGeneration)) {
    if (this.$server) {
      this.$server.acceptClientFailure(PROTOCOL_VERSION, this.componentGeneration,
        sceneGeneration, String(message).slice(0, 4096));
    }
  }
}

if (!customElements.get('mundane-map-canvas')) {
  customElements.define('mundane-map-canvas', MundaneMapCanvas);
}
