const PROTOCOL_VERSION = 1;
const MAX_LAYERS = 64;
const MAX_FEATURES = 50000;
const MAX_PRIMITIVES = 200000;
const MAX_COORDINATE_PAIRS = 2000000;
const MAX_PATH_COMMANDS = 2000000;
const MAX_LOGICAL_BYTES = 64 * 1024 * 1024;
const MAX_ICON_RESOURCES = 4096;
const MAX_ICON_RESOURCE_BYTES = 64 * 1024 * 1024;
const MAX_HATCH_SEGMENTS = 200000;
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
  let active = false;
  let closed = false;
  let subpathHasSegment = false;
  let hasSegment = false;
  let allSubpathsClosed = true;
  for (const command of path.commands) {
    if (command === 'MOVE_TO') {
      if (active && !subpathHasSegment) {
        throw new Error('SYMBOL_UNSUPPORTED');
      }
      if (active && !closed) {
        allSubpathsClosed = false;
      }
      active = true;
      closed = false;
      subpathHasSegment = false;
    } else if (command === 'CLOSE') {
      if (!active || closed || !subpathHasSegment) {
        throw new Error('SYMBOL_UNSUPPORTED');
      }
      closed = true;
    } else {
      if (!active || closed) {
        throw new Error('SYMBOL_UNSUPPORTED');
      }
      subpathHasSegment = true;
      hasSegment = true;
    }
  }
  if (!subpathHasSegment || !hasSegment) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  return allSubpathsClosed && closed;
}

function validateStroke(stroke) {
  if (!stroke || !validateColor(stroke.color) || typeof stroke.width !== 'number' ||
      !['SCREEN_PIXEL', 'MAP_UNIT'].includes(stroke.unit)) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (!Number.isFinite(stroke.width)) {
    throw new Error('NON_FINITE_VALUE');
  }
  if (stroke.width <= 0) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
}

function validateOptionalStroke(stroke) {
  if (!stroke || typeof stroke.present !== 'boolean') {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (stroke.present) {
    validateStroke(stroke.value);
  }
}

function validateOptionalNumber(value) {
  if (!value || typeof value.present !== 'boolean') {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (value.present && !Number.isFinite(value.value)) {
    throw new Error('NON_FINITE_VALUE');
  }
}

function validateRings(rings) {
  if (!Array.isArray(rings) || !rings.length) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  let pairs = 0;
  for (const ring of rings) {
    pairs += validateCoordinates(ring, 4);
    if (ring[0] !== ring[ring.length - 2] || ring[1] !== ring[ring.length - 1]) {
      throw new Error('SYMBOL_UNSUPPORTED');
    }
  }
  return pairs;
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
  if (primitive.kind === 'icon') {
    return {
      kind: 'icon',
      coordinate: [...primitive.coordinate],
      resource: primitive.resource,
      intrinsicWidth: primitive.intrinsicWidth,
      intrinsicHeight: primitive.intrinsicHeight,
      size: [...primitive.size],
      unit: primitive.unit,
      anchor: primitive.anchor,
      offset: [...primitive.offset],
      rotationDegrees: primitive.rotationDegrees,
      rotationMode: primitive.rotationMode,
      interpolation: primitive.interpolation,
      endpointBearing: primitive.endpointBearing.present ?
        {present: true, value: primitive.endpointBearing.value} : {present: false},
      opacity: primitive.opacity
    };
  }
  if (primitive.kind === 'point') {
    return {
      kind: 'point',
      coordinate: [...primitive.coordinate],
      path: {commands: [...primitive.path.commands], ordinates: [...primitive.path.ordinates]},
      viewBox: [...primitive.viewBox],
      size: [...primitive.size],
      unit: primitive.unit,
      anchor: primitive.anchor,
      offset: [...primitive.offset],
      rotationDegrees: primitive.rotationDegrees,
      rotationMode: primitive.rotationMode,
      fill: [...primitive.fill],
      stroke: primitive.stroke.present ? {present: true, value: {
        color: [...primitive.stroke.value.color], width: primitive.stroke.value.width,
        unit: primitive.stroke.value.unit
      }} : {present: false},
      endpointBearing: primitive.endpointBearing.present ?
        {present: true, value: primitive.endpointBearing.value} : {present: false},
      opacity: primitive.opacity
    };
  }
  if (primitive.kind === 'line') {
    return {
      kind: 'line',
      coordinates: [...primitive.coordinates],
      stroke: {color: [...primitive.stroke.color], width: primitive.stroke.width,
        unit: primitive.stroke.unit},
      opacity: primitive.opacity
    };
  }
  if (primitive.kind === 'hatch') {
    return {
      kind: 'hatch',
      rings: primitive.rings.map(ring => [...ring]),
      pattern: primitive.pattern,
      stroke: {color: [...primitive.stroke.color], width: primitive.stroke.width,
        unit: primitive.stroke.unit},
      spacing: primitive.spacing,
      spacingUnit: primitive.spacingUnit,
      rotationMode: primitive.rotationMode,
      maxSegments: primitive.maxSegments,
      opacity: primitive.opacity
    };
  }
  return {
    kind: 'polygon',
    rings: primitive.rings.map(ring => [...ring]),
    fill: [...primitive.fill],
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
            logicalNumberArrayBytes(primitive.viewBox) +
            logicalNumberArrayBytes(primitive.size) + logicalNumberArrayBytes(primitive.offset) +
            2 * 8 + 3 + logicalNumberArrayBytes(primitive.fill) + 1 + 1;
          if (primitive.stroke.present) {
            size += logicalNumberArrayBytes(primitive.stroke.value.color) + 8 + 1;
          }
          if (primitive.endpointBearing.present) {
            size += 8;
          }
        } else if (primitive.kind === 'icon') {
          size += logicalNumberArrayBytes(primitive.coordinate) +
            logicalStringBytes(primitive.resource) + 2 * 8 +
            logicalNumberArrayBytes(primitive.size) + logicalNumberArrayBytes(primitive.offset) +
            2 * 8 + 4 + 1;
          if (primitive.endpointBearing.present) size += 8;
        } else if (primitive.kind === 'line') {
          size += logicalNumberArrayBytes(primitive.coordinates) +
            logicalNumberArrayBytes(primitive.stroke.color) + 2 * 8 + 1;
        } else if (primitive.kind === 'hatch') {
          size += 4 + primitive.rings.reduce((sum, ring) =>
            sum + logicalNumberArrayBytes(ring), 0) +
            logicalNumberArrayBytes(primitive.stroke.color) + 4 * 8 + 3;
        } else {
          size += 4 + primitive.rings.reduce((sum, ring) =>
            sum + logicalNumberArrayBytes(ring), 0) + logicalNumberArrayBytes(primitive.fill) + 8;
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
        if (primitive.kind === 'icon') {
          coordinatePairs += validateCoordinates(primitive.coordinate, 1);
          if (primitive.coordinate.length !== 2 ||
              typeof primitive.resource !== 'string' || !primitive.resource ||
              primitive.resource.length > 4096 ||
              !Number.isInteger(primitive.intrinsicWidth) || primitive.intrinsicWidth <= 0 ||
              primitive.intrinsicWidth > 4096 ||
              !Number.isInteger(primitive.intrinsicHeight) || primitive.intrinsicHeight <= 0 ||
              primitive.intrinsicHeight > 4096 ||
              primitive.intrinsicWidth * primitive.intrinsicHeight > 4194304 ||
              !Array.isArray(primitive.size) || primitive.size.length !== 2 ||
              !Array.isArray(primitive.offset) || primitive.offset.length !== 2 ||
              !['SCREEN_PIXEL', 'MAP_UNIT'].includes(primitive.unit) ||
              !['NORTH_WEST', 'NORTH', 'NORTH_EAST', 'WEST', 'CENTER', 'EAST',
                'SOUTH_WEST', 'SOUTH', 'SOUTH_EAST'].includes(primitive.anchor) ||
              !['SCREEN_RELATIVE', 'MAP_RELATIVE'].includes(primitive.rotationMode) ||
              !['NEAREST', 'BILINEAR'].includes(primitive.interpolation)) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          const base = globalThis.location?.href;
          let resolved;
          try {
            if (!base) throw new Error('RESOURCE_UNAVAILABLE');
            if (!(primitive.resource.startsWith('/') || primitive.resource.startsWith('./')) ||
                primitive.resource.startsWith('//') || primitive.resource.includes('\\')) {
              throw new Error('RESOURCE_UNAVAILABLE');
            }
            resolved = new URL(primitive.resource, base);
          } catch (_error) {
            throw new Error('RESOURCE_UNAVAILABLE');
          }
          if (resolved.origin !== new URL(base).origin || resolved.hash ||
              !['http:', 'https:'].includes(resolved.protocol)) {
            throw new Error('RESOURCE_UNAVAILABLE');
          }
          if (!primitive.size.every(Number.isFinite) ||
              !primitive.offset.every(Number.isFinite) ||
              !Number.isFinite(primitive.rotationDegrees)) {
            throw new Error('NON_FINITE_VALUE');
          }
          if (primitive.size.some(value => value <= 0) || primitive.rotationDegrees < 0 ||
              primitive.rotationDegrees >= 360) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          validateOptionalNumber(primitive.endpointBearing);
          if (primitive.endpointBearing.present &&
              (primitive.endpointBearing.value < 0 || primitive.endpointBearing.value >= 360)) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
        } else if (primitive.kind === 'point') {
          coordinatePairs += validateCoordinates(primitive.coordinate, 1);
          const pathClosed = validatePath(primitive.path);
          if (primitive.coordinate.length !== 2 ||
              !Array.isArray(primitive.viewBox) || primitive.viewBox.length !== 4 ||
              !Array.isArray(primitive.size) || primitive.size.length !== 2 ||
              !Array.isArray(primitive.offset) || primitive.offset.length !== 2 ||
              !validateColor(primitive.fill) ||
              !['SCREEN_PIXEL', 'MAP_UNIT'].includes(primitive.unit) ||
              !['NORTH_WEST', 'NORTH', 'NORTH_EAST', 'WEST', 'CENTER', 'EAST',
                'SOUTH_WEST', 'SOUTH', 'SOUTH_EAST'].includes(primitive.anchor) ||
              !['SCREEN_RELATIVE', 'MAP_RELATIVE'].includes(primitive.rotationMode)) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          if (!primitive.viewBox.every(Number.isFinite) ||
              !primitive.size.every(Number.isFinite) ||
              !primitive.offset.every(Number.isFinite) ||
              !Number.isFinite(primitive.rotationDegrees)) {
            throw new Error('NON_FINITE_VALUE');
          }
          const viewBoxWidth = primitive.viewBox[2] - primitive.viewBox[0];
          const viewBoxHeight = primitive.viewBox[3] - primitive.viewBox[1];
          if (!Number.isFinite(viewBoxWidth) || !Number.isFinite(viewBoxHeight)) {
            throw new Error('NON_FINITE_VALUE');
          }
          if (viewBoxWidth <= 0 || viewBoxHeight <= 0 ||
              primitive.size.some(value => value <= 0) || primitive.rotationDegrees < 0 ||
              primitive.rotationDegrees >= 360) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          validateOptionalStroke(primitive.stroke);
          validateOptionalNumber(primitive.endpointBearing);
          if (primitive.endpointBearing.present &&
              (primitive.endpointBearing.value < 0 || primitive.endpointBearing.value >= 360)) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          for (let index = 0; index < primitive.path.ordinates.length; index += 2) {
            if (primitive.path.ordinates[index] < primitive.viewBox[0] ||
                primitive.path.ordinates[index] > primitive.viewBox[2] ||
                primitive.path.ordinates[index + 1] < primitive.viewBox[1] ||
                primitive.path.ordinates[index + 1] > primitive.viewBox[3]) {
              throw new Error('SYMBOL_UNSUPPORTED');
            }
          }
          if ((primitive.fill[3] !== 0 || !primitive.stroke.present) && !pathClosed) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
          pathCommands += primitive.path.commands.length;
        } else if (primitive.kind === 'line') {
          coordinatePairs += validateCoordinates(primitive.coordinates, 2);
          validateStroke(primitive.stroke);
        } else if (primitive.kind === 'polygon') {
          coordinatePairs += validateRings(primitive.rings);
          if (!validateColor(primitive.fill)) {
            throw new Error('SYMBOL_UNSUPPORTED');
          }
        } else if (primitive.kind === 'hatch') {
          coordinatePairs += validateRings(primitive.rings);
          validateStroke(primitive.stroke);
          if (!['FORWARD_DIAGONAL', 'BACKWARD_DIAGONAL', 'CROSS_DIAGONAL']
            .includes(primitive.pattern) ||
              !['SCREEN_PIXEL', 'MAP_UNIT'].includes(primitive.spacingUnit) ||
              !['SCREEN_RELATIVE', 'MAP_RELATIVE'].includes(primitive.rotationMode) ||
              !Number.isFinite(primitive.spacing) || primitive.spacing <= 0 ||
              !Number.isSafeInteger(primitive.maxSegments) || primitive.maxSegments <= 0 ||
              primitive.maxSegments > 2147483647) {
            throw new Error('SYMBOL_UNSUPPORTED');
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
    this.iconResources = new Map();
    this.sceneLoadAbort = null;
    this.sceneLoadSequence = 0;
    this.pendingSceneGeneration = -1;
    this.pendingViewport = null;
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
    this.cancelSceneLoad();
    this.pendingSceneGeneration = -1;
    this.pendingViewport = null;
    this.iconResources.clear();
    this.scene = null;
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
      this.cancelSceneLoad();
      this.pendingSceneGeneration = -1;
      this.pendingViewport = null;
      this.iconResources.clear();
      this.scene = null;
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
    this.cancelSceneLoad();
    this.pendingSceneGeneration = -1;
    this.pendingViewport = null;
    this.iconResources.clear();
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
    let accepted;
    let acceptedViewport;
    try {
      accepted = validateScene(candidate, this.componentGeneration, this.sceneGeneration);
      acceptedViewport = validateViewport(accepted.viewport);
    } catch (error) {
      this.reportFailure(error.message, candidate.sceneGeneration);
      return;
    }
    let resources;
    try {
      resources = this.sceneIconMetadata(accepted);
    } catch (error) {
      this.reportFailure(error.message, candidate.sceneGeneration);
      return;
    }
    this.cancelSceneLoad();
    this.pendingSceneGeneration = accepted.sceneGeneration;
    this.pendingViewport = null;
    const sequence = ++this.sceneLoadSequence;
    if (!resources.size) {
      this.acceptLoadedScene(accepted, acceptedViewport, new Map(), sequence);
      return;
    }
    const controller = new AbortController();
    this.sceneLoadAbort = controller;
    this.loadSceneIcons(resources, controller.signal)
      .then(icons => this.acceptLoadedScene(accepted, acceptedViewport, icons, sequence))
      .catch(error => {
        if (!controller.signal.aborted && sequence === this.sceneLoadSequence) {
          this.pendingSceneGeneration = -1;
          this.pendingViewport = null;
          const code = error?.message === 'BROWSER_CAPABILITY_UNSUPPORTED' ?
            error.message : 'RESOURCE_UNAVAILABLE';
          this.reportFailure(code, candidate.sceneGeneration);
        }
      });
  }

  cancelSceneLoad() {
    this.sceneLoadSequence++;
    if (this.sceneLoadAbort) {
      this.sceneLoadAbort.abort();
      this.sceneLoadAbort = null;
    }
  }

  sceneIconMetadata(scene) {
    const resources = new Map();
    let total = 0;
    for (const layer of scene.layers) for (const feature of layer.features) {
      for (const primitive of feature.primitives) if (primitive.kind === 'icon') {
        const prior = resources.get(primitive.resource);
        if (prior && (prior.width !== primitive.intrinsicWidth ||
            prior.height !== primitive.intrinsicHeight)) {
          throw new Error('RESOURCE_UNAVAILABLE');
        }
        if (!prior) {
          const bytes = 12 + primitive.intrinsicWidth * primitive.intrinsicHeight * 4;
          total += bytes;
          if (resources.size >= MAX_ICON_RESOURCES || total > MAX_ICON_RESOURCE_BYTES) {
            throw new Error('LIMIT_EXCEEDED');
          }
          resources.set(primitive.resource,
            {width: primitive.intrinsicWidth, height: primitive.intrinsicHeight, bytes});
        }
      }
    }
    return resources;
  }

  async loadSceneIcons(resources, signal) {
    const loaded = new Map();
    for (const [resource, metadata] of resources) {
      const response = await fetch(resource,
        {credentials: 'same-origin', cache: 'no-store', redirect: 'error', signal});
      if (!response.ok) throw new Error('RESOURCE_UNAVAILABLE');
      const buffer = await response.arrayBuffer();
      if (buffer.byteLength !== metadata.bytes) throw new Error('RESOURCE_UNAVAILABLE');
      const bytes = new Uint8Array(buffer);
      if (bytes[0] !== 77 || bytes[1] !== 77 || bytes[2] !== 82 || bytes[3] !== 73 ||
          bytes[4] !== 1 || bytes[5] !== 0 || bytes[10] !== 0 || bytes[11] !== 0 ||
          (bytes[6] << 8 | bytes[7]) !== metadata.width ||
          (bytes[8] << 8 | bytes[9]) !== metadata.height) {
        throw new Error('RESOURCE_UNAVAILABLE');
      }
      const image = document.createElement('canvas');
      image.width = metadata.width;
      image.height = metadata.height;
      const context = image.getContext('2d');
      if (!context || typeof context.createImageData !== 'function' ||
          typeof context.putImageData !== 'function') {
        throw new Error('BROWSER_CAPABILITY_UNSUPPORTED');
      }
      const pixels = context.createImageData(metadata.width, metadata.height);
      pixels.data.set(bytes.subarray(12));
      context.putImageData(pixels, 0, 0);
      loaded.set(resource, image);
    }
    return loaded;
  }

  acceptLoadedScene(accepted, acceptedViewport, icons, sequence) {
    if (sequence !== this.sceneLoadSequence || accepted.componentGeneration !==
        this.componentGeneration || accepted.sceneGeneration <= this.sceneGeneration) return;
    const queued = this.pendingSceneGeneration === accepted.sceneGeneration ?
      this.pendingViewport : null;
    const finalViewport = queued && queued.viewportGeneration >= accepted.viewportGeneration ?
      queued.viewport : acceptedViewport;
    const finalViewportGeneration = queued &&
      queued.viewportGeneration >= accepted.viewportGeneration ?
      queued.viewportGeneration : accepted.viewportGeneration;
    const previousViewport = this.viewport;
    this.viewport = finalViewport;
    try {
      this.preflightPaint(accepted, icons);
    } catch (error) {
      this.viewport = previousViewport;
      this.pendingSceneGeneration = -1;
      this.pendingViewport = null;
      this.reportFailure(error.message, accepted.sceneGeneration);
      return;
    }
    this.sceneLoadAbort = null;
    this.scene = accepted;
    this.iconResources = icons;
    this.sceneGeneration = accepted.sceneGeneration;
    this.viewportGeneration = finalViewportGeneration;
    this.viewport = finalViewport;
    this.pendingSceneGeneration = -1;
    this.pendingViewport = null;
    this.schedulePaint();
  }

  setMapViewport(version, componentGeneration, sceneGeneration, viewportGeneration,
    width, height, centerX, centerY, worldUnitsPerPixel) {
    if (version !== PROTOCOL_VERSION) {
      this.reportFailure('PROTOCOL_VERSION_UNSUPPORTED', sceneGeneration);
      return;
    }
    if (componentGeneration !== this.componentGeneration ||
        !validateGeneration(viewportGeneration)) {
      this.reportFailure('STALE_GENERATION', sceneGeneration);
      return;
    }
    if (this.missingCapability) {
      this.reportFailure('BROWSER_CAPABILITY_UNSUPPORTED', sceneGeneration);
      return;
    }
    try {
      const candidate = validateViewport(
        {width, height, centerX, centerY, worldUnitsPerPixel});
      if (sceneGeneration === this.pendingSceneGeneration &&
          sceneGeneration > this.sceneGeneration) {
        if (this.pendingViewport &&
            viewportGeneration < this.pendingViewport.viewportGeneration) {
          this.reportFailure('STALE_GENERATION', sceneGeneration);
          return;
        }
        this.pendingViewport = {viewportGeneration, viewport: candidate};
        return;
      }
      if (sceneGeneration !== this.sceneGeneration ||
          viewportGeneration < this.viewportGeneration) {
        this.reportFailure('STALE_GENERATION', sceneGeneration);
        return;
      }
      this.viewport = candidate;
      this.viewportGeneration = viewportGeneration;
      this.schedulePaint();
    } catch (error) {
      this.reportFailure(error.message, sceneGeneration);
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
    try {
      this.preflightPaint();
    } catch (error) {
      this.reportFailure(error.message);
      return;
    }
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
    } else if (primitive.kind === 'icon') {
      this.drawIcon(primitive);
    } else if (primitive.kind === 'line') {
      this.drawLine(primitive.coordinates, primitive.stroke, primitive.opacity);
    } else if (primitive.kind === 'hatch') {
      this.drawHatch(primitive);
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
    if (opacity === 0 || stroke.color[3] === 0) {
      return;
    }
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
    this.context.lineWidth = this.screenLength(stroke.width, stroke.unit);
    this.context.lineCap = 'round';
    this.context.lineJoin = 'round';
    this.context.stroke();
  }

  drawPolygon(primitive) {
    this.polygonPath(primitive.rings);
    this.context.fillStyle = rgba(primitive.fill, primitive.opacity);
    this.context.fill('evenodd');
  }

  drawPoint(primitive) {
    const matrix = this.markerTransform(primitive);
    const strokeVisible = primitive.stroke.present && primitive.stroke.value.color[3] > 0;
    if (primitive.opacity === 0 || (primitive.fill[3] === 0 && !strokeVisible)) {
      return;
    }
    this.context.save();
    this.context.transform(matrix.m00, matrix.m10, matrix.m01, matrix.m11,
      matrix.m02, matrix.m12);
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
    this.context.restore();
    if (primitive.fill[3] > 0) {
      this.context.fillStyle = rgba(primitive.fill, primitive.opacity);
      this.context.fill('evenodd');
    }
    if (strokeVisible) {
      const stroke = primitive.stroke.value;
      this.context.strokeStyle = rgba(stroke.color, primitive.opacity);
      this.context.lineWidth = this.screenLength(stroke.width, stroke.unit);
      this.context.lineCap = 'round';
      this.context.lineJoin = 'round';
      this.context.stroke();
    }
  }

  drawIcon(primitive) {
    if (primitive.opacity === 0) return;
    const image = this.iconResources.get(primitive.resource);
    if (!image) throw new Error('RESOURCE_UNAVAILABLE');
    const matrix = this.markerTransform(primitive);
    this.context.transform(matrix.m00, matrix.m10, matrix.m01, matrix.m11,
      matrix.m02, matrix.m12);
    this.context.imageSmoothingEnabled = primitive.interpolation === 'BILINEAR';
    this.context.globalAlpha = primitive.opacity;
    this.context.drawImage(image, 0, 0, primitive.intrinsicWidth, primitive.intrinsicHeight);
  }

  markerTransform(primitive) {
    const point = this.screen(primitive.coordinate, 0);
    const viewBox = primitive.kind === 'icon' ?
      [0, 0, primitive.intrinsicWidth, primitive.intrinsicHeight] : primitive.viewBox;
    const unitScale = primitive.unit === 'SCREEN_PIXEL' ? 1 :
      1 / this.viewport.worldUnitsPerPixel;
    const width = primitive.size[0] * unitScale;
    const height = primitive.size[1] * unitScale;
    const offsetX = primitive.unit === 'SCREEN_PIXEL' ? primitive.offset[0] :
      primitive.offset[0] / this.viewport.worldUnitsPerPixel;
    const offsetY = primitive.unit === 'SCREEN_PIXEL' ? primitive.offset[1] :
      -primitive.offset[1] / this.viewport.worldUnitsPerPixel;
    const anchors = {
      NORTH_WEST: [0, 0], NORTH: [0.5, 0], NORTH_EAST: [1, 0],
      WEST: [0, 0.5], CENTER: [0.5, 0.5], EAST: [1, 0.5],
      SOUTH_WEST: [0, 1], SOUTH: [0.5, 1], SOUTH_EAST: [1, 1]
    };
    const anchor = anchors[primitive.anchor];
    const bearing = (primitive.endpointBearing.present ? primitive.endpointBearing.value :
      (primitive.rotationMode === 'MAP_RELATIVE' ? 0 : 0)) + primitive.rotationDegrees;
    const radians = bearing * Math.PI / 180;
    const cosine = Math.cos(radians);
    const sine = Math.sin(radians);
    const scaleX = width / (viewBox[2] - viewBox[0]);
    const scaleY = height / (viewBox[3] - viewBox[1]);
    const localX = -viewBox[0] * scaleX - anchor[0] * width;
    const localY = -viewBox[1] * scaleY - anchor[1] * height;
    const m00 = cosine * scaleX;
    const m01 = -sine * scaleY;
    const m10 = sine * scaleX;
    const m11 = cosine * scaleY;
    const m02 = point[0] + offsetX + cosine * localX - sine * localY;
    const m12 = point[1] + offsetY + sine * localX + cosine * localY;
    const values = [width, height, offsetX, offsetY, scaleX, scaleY,
      m00, m01, m10, m11, m02, m12];
    if (!values.every(Number.isFinite) || width <= 0 || height <= 0) {
      throw new Error('NON_FINITE_VALUE');
    }
    for (const x of [viewBox[0], viewBox[2]]) {
      for (const y of [viewBox[1], viewBox[3]]) {
        const xProductX = m00 * x;
        const yProductX = m01 * y;
        const xProductY = m10 * x;
        const yProductY = m11 * y;
        const cornerX = xProductX + yProductX + m02;
        const cornerY = xProductY + yProductY + m12;
        if (![xProductX, yProductX, xProductY, yProductY, cornerX, cornerY]
          .every(Number.isFinite)) {
          throw new Error('NON_FINITE_VALUE');
        }
      }
    }
    return {m00, m01, m10, m11, m02, m12};
  }

  screenLength(value, unit) {
    const result = unit === 'SCREEN_PIXEL' ? value :
      value / this.viewport.worldUnitsPerPixel;
    if (!Number.isFinite(result) || result <= 0) {
      throw new Error('NON_FINITE_VALUE');
    }
    return result;
  }

  polygonPath(rings) {
    this.context.beginPath();
    for (const ring of rings) {
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
  }

  hatchLayout(primitive) {
    const bounds = [Infinity, Infinity, -Infinity, -Infinity];
    for (const ring of primitive.rings) {
      for (let index = 0; index < ring.length; index += 2) {
        const point = this.screen(ring, index);
        bounds[0] = Math.min(bounds[0], point[0]);
        bounds[1] = Math.min(bounds[1], point[1]);
        bounds[2] = Math.max(bounds[2], point[0]);
        bounds[3] = Math.max(bounds[3], point[1]);
      }
    }
    bounds[0] = Math.max(0, bounds[0]);
    bounds[1] = Math.max(0, bounds[1]);
    bounds[2] = Math.min(this.viewport.width, bounds[2]);
    bounds[3] = Math.min(this.viewport.height, bounds[3]);
    if (!(bounds[0] < bounds[2] && bounds[1] < bounds[3])) {
      return {bounds, orientations: [], required: 0};
    }
    const origin = primitive.rotationMode === 'MAP_RELATIVE' ? this.screen([0, 0], 0) : [0, 0];
    const spacing = this.screenLength(primitive.spacing, primitive.spacingUnit);
    if (!Number.isFinite(spacing) || spacing <= 0) {
      throw new Error('NON_FINITE_VALUE');
    }
    const bearings = primitive.pattern === 'FORWARD_DIAGONAL' ? [315] :
      primitive.pattern === 'BACKWARD_DIAGONAL' ? [45] : [315, 45];
    const orientations = [];
    let required = 0;
    for (const bearing of bearings) {
      const radians = bearing * Math.PI / 180;
      const directionX = Math.cos(radians);
      const directionY = Math.sin(radians);
      const normalX = -directionY;
      const normalY = directionX;
      const projections = [
        [bounds[0], bounds[1]], [bounds[0], bounds[3]],
        [bounds[2], bounds[1]], [bounds[2], bounds[3]]
      ].map(point => (point[0] - origin[0]) * normalX +
        (point[1] - origin[1]) * normalY);
      const first = Math.ceil(Math.min(...projections) / spacing);
      const last = Math.floor(Math.max(...projections) / spacing);
      const count = Math.max(0, last - first + 1);
      if (![first, last, count].every(Number.isSafeInteger)) {
        throw new Error('SYMBOL_HATCH_SEGMENT_LIMIT_EXCEEDED');
      }
      required += count;
      orientations.push({directionX, directionY, normalX, normalY, first, last});
    }
    if (!Number.isSafeInteger(required) || required > primitive.maxSegments ||
        required > MAX_HATCH_SEGMENTS) {
      throw new Error('SYMBOL_HATCH_SEGMENT_LIMIT_EXCEEDED');
    }
    return {bounds, origin, spacing, orientations, required};
  }

  preflightPaint(scene = this.scene, iconResources = this.iconResources) {
    if (!scene) {
      return;
    }
    let hatchSegments = 0;
    for (const layer of scene.layers) {
      for (const feature of layer.features) {
        for (const primitive of feature.primitives) {
          if (primitive.kind === 'icon') {
            this.markerTransform(primitive);
            if (!iconResources.has(primitive.resource)) throw new Error('RESOURCE_UNAVAILABLE');
          } else if (primitive.kind === 'hatch') {
            this.preflightRings(primitive.rings);
            if (primitive.opacity === 0 || primitive.stroke.color[3] === 0) {
              continue;
            }
            hatchSegments += this.hatchLayout(primitive).required;
            if (!Number.isSafeInteger(hatchSegments) || hatchSegments > MAX_HATCH_SEGMENTS) {
              throw new Error('SYMBOL_HATCH_SEGMENT_LIMIT_EXCEEDED');
            }
            this.screenLength(primitive.stroke.width, primitive.stroke.unit);
          } else if (primitive.kind === 'point') {
            this.markerTransform(primitive);
            if (primitive.opacity !== 0 && primitive.stroke.present &&
                primitive.stroke.value.color[3] !== 0) {
              this.screenLength(primitive.stroke.value.width, primitive.stroke.value.unit);
            }
          } else if (primitive.kind === 'line') {
            this.preflightCoordinates(primitive.coordinates);
            if (primitive.opacity !== 0 && primitive.stroke.color[3] !== 0) {
              this.screenLength(primitive.stroke.width, primitive.stroke.unit);
            }
          } else {
            this.preflightRings(primitive.rings);
          }
        }
      }
    }
  }

  preflightCoordinates(coordinates) {
    for (let index = 0; index < coordinates.length; index += 2) {
      if (!this.screen(coordinates, index).every(Number.isFinite)) {
        throw new Error('NON_FINITE_VALUE');
      }
    }
  }

  preflightRings(rings) {
    for (const ring of rings) {
      this.preflightCoordinates(ring);
    }
  }

  drawHatch(primitive) {
    if (primitive.opacity === 0 || primitive.stroke.color[3] === 0) {
      return;
    }
    const layout = this.hatchLayout(primitive);
    if (!layout.required) {
      return;
    }
    this.polygonPath(primitive.rings);
    this.context.clip('evenodd');
    this.context.beginPath();
    const bounds = layout.bounds;
    for (const orientation of layout.orientations) {
      for (let index = orientation.first; index <= orientation.last; index++) {
        const offset = index * layout.spacing;
        const lineX = layout.origin[0] + orientation.normalX * offset;
        const lineY = layout.origin[1] + orientation.normalY * offset;
        let minimum = -Infinity;
        let maximum = Infinity;
        if (orientation.directionX === 0) {
          if (lineX < bounds[0] || lineX > bounds[2]) continue;
        } else {
          const first = (bounds[0] - lineX) / orientation.directionX;
          const second = (bounds[2] - lineX) / orientation.directionX;
          minimum = Math.max(minimum, Math.min(first, second));
          maximum = Math.min(maximum, Math.max(first, second));
        }
        if (orientation.directionY === 0) {
          if (lineY < bounds[1] || lineY > bounds[3]) continue;
        } else {
          const first = (bounds[1] - lineY) / orientation.directionY;
          const second = (bounds[3] - lineY) / orientation.directionY;
          minimum = Math.max(minimum, Math.min(first, second));
          maximum = Math.min(maximum, Math.max(first, second));
        }
        if (minimum < maximum) {
          this.context.moveTo(lineX + orientation.directionX * minimum,
            lineY + orientation.directionY * minimum);
          this.context.lineTo(lineX + orientation.directionX * maximum,
            lineY + orientation.directionY * maximum);
        }
      }
    }
    this.context.strokeStyle = rgba(primitive.stroke.color, primitive.opacity);
    this.context.lineWidth = this.screenLength(primitive.stroke.width, primitive.stroke.unit);
    this.context.lineCap = 'round';
    this.context.lineJoin = 'round';
    this.context.stroke();
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
