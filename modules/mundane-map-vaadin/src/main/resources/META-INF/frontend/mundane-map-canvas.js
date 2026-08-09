const PROTOCOL_VERSION = 1;
const MAX_PENDING_TOOL_EVENTS = 32;
const MAX_LAYERS = 64;
const MAX_FEATURES = 50000;
const MAX_PRIMITIVES = 200000;
const MAX_COORDINATE_PAIRS = 2000000;
const MAX_PATH_COMMANDS = 2000000;
const MAX_LOGICAL_BYTES = 64 * 1024 * 1024;
const MAX_ICON_RESOURCES = 4096;
const MAX_ICON_RESOURCE_BYTES = 64 * 1024 * 1024;
const MAX_RASTER_WINDOWS = 4096;
const MAX_RASTER_EDGE = 16384;
const MAX_RASTER_PIXELS = 16777216;
const MAX_RASTER_WINDOW_BYTES = 64 * 1024 * 1024;
const MAX_SCENE_RESOURCE_BYTES = 128 * 1024 * 1024;
const MAX_LABELS = 4096;
const MAX_LABEL_CODE_POINTS = 262144;
const MAX_LABEL_METRIC_MAGNITUDE = 1000000;
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

function sameViewport(first, second) {
  return first.width === second.width && first.height === second.height &&
    first.centerX === second.centerX && first.centerY === second.centerY &&
    first.worldUnitsPerPixel === second.worldUnitsPerPixel;
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

function validateRelativeResource(resource) {
  const base = globalThis.location?.href;
  let resolved;
  try {
    if (!base || typeof resource !== 'string' || !resource || resource.length > 4096 ||
        !(resource.startsWith('/') || resource.startsWith('./')) ||
        resource.startsWith('//') || resource.includes('\\')) {
      throw new Error('RESOURCE_UNAVAILABLE');
    }
    resolved = new URL(resource, base);
  } catch (_error) {
    throw new Error('RESOURCE_UNAVAILABLE');
  }
  if (resolved.origin !== new URL(base).origin || resolved.hash ||
      !['http:', 'https:'].includes(resolved.protocol)) {
    throw new Error('RESOURCE_UNAVAILABLE');
  }
}

async function readExactResponse(response, expectedBytes) {
  const contentLength = response.headers?.get?.('content-length');
  if (contentLength !== null && contentLength !== undefined &&
      (!/^(0|[1-9][0-9]*)$/.test(contentLength) ||
       !Number.isSafeInteger(Number(contentLength)) || Number(contentLength) !== expectedBytes)) {
    throw new Error('RESOURCE_UNAVAILABLE');
  }
  const reader = response.body?.getReader?.();
  if (!reader) throw new Error('BROWSER_CAPABILITY_UNSUPPORTED');
  const result = new Uint8Array(expectedBytes);
  let offset = 0;
  let chunks = 0;
  try {
    while (true) {
      const next = await reader.read();
      if (next.done) break;
      if (!(next.value instanceof Uint8Array) || next.value.length === 0 ||
          ++chunks > 65_536 || next.value.length > expectedBytes - offset) {
        reader.cancel().catch(() => {});
        throw new Error('RESOURCE_UNAVAILABLE');
      }
      result.set(next.value, offset);
      offset += next.value.length;
    }
  } finally {
    reader.releaseLock?.();
  }
  if (offset !== expectedBytes) throw new Error('RESOURCE_UNAVAILABLE');
  return result;
}

function validateBounds(value) {
  if (!Array.isArray(value) || value.length !== 4 || !value.every(Number.isFinite) ||
      !(value[0] < value[2]) || !(value[1] < value[3])) {
    throw new Error('NON_FINITE_VALUE');
  }
}

function validateRaster(raster) {
  if (!raster || typeof raster.id !== 'string' || !raster.id || raster.id.length > 256 ||
      typeof raster.name !== 'string' || raster.name.length > 4096 ||
      !Number.isInteger(raster.width) || raster.width <= 0 || raster.width > MAX_RASTER_EDGE ||
      !Number.isInteger(raster.height) || raster.height <= 0 || raster.height > MAX_RASTER_EDGE ||
      raster.width * raster.height > MAX_RASTER_PIXELS ||
      !Number.isFinite(raster.opacity) || raster.opacity < 0 || raster.opacity > 1 ||
      !['NEAREST', 'BILINEAR'].includes(raster.interpolation) ||
      !Array.isArray(raster.sourceWindow) || raster.sourceWindow.length !== 4 ||
      !raster.sourceWindow.every(Number.isSafeInteger) ||
      raster.sourceWindow.some(value => value < 0) ||
      raster.sourceWindow[2] <= 0 || raster.sourceWindow[3] <= 0 || !raster.placement) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  validateRelativeResource(raster.resource);
  validateBounds(raster.imageMapBounds);
  validateBounds(raster.clipMapBounds);
  if (raster.clipMapBounds[0] < raster.imageMapBounds[0] ||
      raster.clipMapBounds[1] < raster.imageMapBounds[1] ||
      raster.clipMapBounds[2] > raster.imageMapBounds[2] ||
      raster.clipMapBounds[3] > raster.imageMapBounds[3]) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (raster.placement.kind === 'AXIS_ALIGNED') {
    validateBounds(raster.placement.bounds);
    if (!raster.placement.bounds.every((value, index) => value === raster.imageMapBounds[index])) {
      throw new Error('SYMBOL_UNSUPPORTED');
    }
  } else if (raster.placement.kind === 'AFFINE') {
    const transform = raster.placement.transform;
    if (!Array.isArray(transform) || transform.length !== 6 ||
        !transform.every(Number.isFinite)) {
      throw new Error('NON_FINITE_VALUE');
    }
    const determinant = transform[0] * transform[3] - transform[2] * transform[1];
    if (!Number.isFinite(determinant) || determinant === 0) {
      throw new Error('NON_FINITE_VALUE');
    }
  } else {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
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
    ...(scene.rasters ? {rasters: scene.rasters.map(raster => ({
      ...raster,
      sourceWindow: [...raster.sourceWindow],
      imageMapBounds: [...raster.imageMapBounds],
      clipMapBounds: [...raster.clipMapBounds],
      placement: raster.placement.kind === 'AFFINE' ?
        {...raster.placement, transform: [...raster.placement.transform]} :
        {...raster.placement, bounds: [...raster.placement.bounds]}
    }))} : {}),
    labelCandidates: scene.labelCandidates.map(candidate => ({...candidate})),
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
  let size = 4 * 8 + logicalNumberArrayBytes(scene.background) + 5 * 8 + 4 + 4 + 4;
  for (const raster of scene.rasters || []) {
    size += logicalStringBytes(raster.id) + logicalStringBytes(raster.name) +
      logicalStringBytes(raster.resource) + 4 * 4 + 19 * 8;
  }
  for (const candidate of scene.labelCandidates) {
    size += 2 * 8 + logicalStringBytes(candidate.text) + 2;
  }
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
  if (!validateColor(candidate.background) || !Array.isArray(candidate.layers) ||
      (candidate.rasters !== undefined && !Array.isArray(candidate.rasters)) ||
      !Array.isArray(candidate.labelCandidates)) {
    throw new Error('SYMBOL_UNSUPPORTED');
  }
  if (candidate.labelCandidates.length > MAX_LABELS) {
    throw new Error('LIMIT_EXCEEDED');
  }
  let labelCodePoints = 0;
  for (let index = 0; index < candidate.labelCandidates.length; index++) {
    const label = candidate.labelCandidates[index];
    const codePoints = typeof label?.text === 'string' ? [...label.text].length : 0;
    if (!label || label.ordinal !== index || typeof label.text !== 'string' ||
        !label.text || label.text.length > 512 || codePoints > 256 ||
        /[\r\n\u2028\u2029]/u.test(label.text) || label.fontFamily !== 'SANS_SERIF' ||
        !['NORMAL', 'BOLD'].includes(label.weight) ||
        !Number.isFinite(label.sizePixels)) {
      throw new Error('SYMBOL_UNSUPPORTED');
    }
    if (label.sizePixels < 1 || label.sizePixels > 512) {
      throw new Error('LIMIT_EXCEEDED');
    }
    labelCodePoints += codePoints;
    if (labelCodePoints > MAX_LABEL_CODE_POINTS) throw new Error('LIMIT_EXCEEDED');
  }
  const rasters = candidate.rasters || [];
  if (rasters.length > MAX_RASTER_WINDOWS || candidate.layers.length + rasters.length > MAX_LAYERS) {
    throw new Error('LIMIT_EXCEEDED');
  }
  const layerIds = new Set();
  for (const raster of rasters) {
    validateRaster(raster);
    if (layerIds.has(raster.id)) throw new Error('DUPLICATE_ID');
    layerIds.add(raster.id);
  }
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
          validateRelativeResource(primitive.resource);
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
    this.canvas.setAttribute('role', 'application');
    this.canvas.setAttribute('aria-label', 'Interactive map');
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
      ['measureText', typeof this.context?.measureText === 'function'],
      ['fillText', typeof this.context?.fillText === 'function'],
      ['TextEncoder', typeof TextEncoder === 'function'],
      ['Uint8Array', typeof Uint8Array === 'function']
    ].find(entry => !entry[1])?.[0] || null;
    this.scene = null;
    this.interactionLayers = [];
    this.pendingInteractionOverlay = null;
    this.iconResources = new Map();
    this.rasterResources = new Map();
    this.sceneLoadAbort = null;
    this.sceneLoadSequence = 0;
    this.pendingSceneGeneration = -1;
    this.pendingViewport = null;
    this.placedLabels = [];
    this.lastLabelMeasurementKey = null;
    this.pendingLabelAcknowledgement = null;
    this.viewport = validateViewport({width: 800, height: 600, centerX: 0,
      centerY: 0, worldUnitsPerPixel: 100000});
    this.componentGeneration = 0;
    this.sceneGeneration = -1;
    this.viewportGeneration = 0;
    this.eventSequence = 0;
    this.toolActive = false;
    this.toolCaptured = false;
    this.interactionEpoch = 0;
    this.interactionChain = Promise.resolve();
    this.pendingToolEvents = 0;
    this.hoverTimer = 0;
    this.pendingHoverEvent = null;
    this.lastHoverMilliseconds = -Infinity;
    this.enabled = true;
    this.closed = false;
    this.active = false;
    this.paintFrame = 0;
    this.settleTimer = 0;
    this.settledRateTimer = 0;
    this.settledTokens = 10;
    this.settledRefillMilliseconds = performance.now();
    this.toolPointerTokens = 120;
    this.toolPointerRefillMilliseconds = this.settledRefillMilliseconds;
    this.toolPointerRateLimited = false;
    this.toolPointerRateCancellationQueued = false;
    this.pendingCancellationCount = 0;
    this.requiredCancellationPending = false;
    this.viewportDirty = false;
    this.pointers = new Map();
    this.lastPointerSnapshot = null;
    this.lastPinch = null;
    this.boundPointerDown = event => this.onPointerDown(event);
    this.boundPointerMove = event => this.onPointerMove(event);
    this.boundPointerUp = event => this.onPointerUp(event);
    this.boundPointerCancel = event => this.onPointerCancel(event);
    this.boundClick = event => this.onClick(event);
    this.boundAuxClick = event => this.onAuxClick(event);
    this.boundContextMenu = event => this.onContextMenu(event);
    this.boundWheel = event => this.onWheel(event);
    this.boundKeyDown = event => this.onKeyDown(event);
    this.boundBlur = event => this.onBlur(event);
    this.boundFocus = () => this.onFocus();
    this.boundPointerLeave = event => this.onPointerLeave(event);
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
    this.interactionEpoch++;
    this.cancelSceneLoad();
    this.pendingSceneGeneration = -1;
    this.pendingViewport = null;
    this.placedLabels = [];
    this.lastLabelMeasurementKey = null;
    this.pendingLabelAcknowledgement = null;
    this.iconResources.clear();
    this.rasterResources.clear();
    this.scene = null;
    this.interactionLayers = [];
    this.pendingInteractionOverlay = null;
    this.componentGeneration = componentGeneration;
    this.sceneGeneration = sceneGeneration - 1;
    this.expectedSceneGeneration = sceneGeneration;
    this.eventSequence = 0;
    this.settledTokens = 10;
    this.settledRefillMilliseconds = performance.now();
    this.toolPointerTokens = 120;
    this.toolPointerRefillMilliseconds = this.settledRefillMilliseconds;
    this.toolPointerRateLimited = false;
    this.toolPointerRateCancellationQueued = false;
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
      this.interactionEpoch++;
      this.cancelSceneLoad();
      this.pendingSceneGeneration = -1;
      this.pendingViewport = null;
      this.placedLabels = [];
      this.lastLabelMeasurementKey = null;
      this.pendingLabelAcknowledgement = null;
      this.iconResources.clear();
      this.rasterResources.clear();
      this.scene = null;
      this.interactionLayers = [];
      this.pendingInteractionOverlay = null;
      this.teardown();
    }
  }

  closeMap(version, componentGeneration) {
    if (version !== PROTOCOL_VERSION || !validateGeneration(componentGeneration)) {
      return;
    }
    this.componentGeneration = componentGeneration;
    this.interactionEpoch++;
    this.closed = true;
    this.scene = null;
    this.interactionLayers = [];
    this.pendingInteractionOverlay = null;
    this.cancelSceneLoad();
    this.pendingSceneGeneration = -1;
    this.pendingViewport = null;
    this.placedLabels = [];
    this.lastLabelMeasurementKey = null;
    this.pendingLabelAcknowledgement = null;
    this.iconResources.clear();
    this.rasterResources.clear();
    this.teardown();
    if (this.context) {
      this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
    }
  }

  setMapEnabled(enabled) {
    const next = Boolean(enabled);
    if (next !== this.enabled) this.interactionEpoch++;
    this.enabled = next;
    if (!this.enabled) {
      this.placedLabels = [];
      this.lastLabelMeasurementKey = null;
      this.pendingLabelAcknowledgement = null;
      this.teardown();
    } else if (this.isConnected && !this.closed) {
      this.setup();
    }
  }

  setToolState(active, captured, cursor) {
    this.toolActive = Boolean(active);
    this.toolCaptured = Boolean(captured);
    const cursors = {DEFAULT: 'default', CROSSHAIR: 'crosshair', HAND: 'pointer', MOVE: 'move'};
    this.canvas.style.cursor = cursors[cursor] || 'default';
  }

  resetToolState(active, captured, cursor) {
    this.settleTerminatedGesture();
    this.interactionEpoch++;
    this.releaseClientPointers();
    this.setToolState(active, captured, cursor);
  }

  requestMapPaint() {
    this.schedulePaint();
  }

  setInteractionOverlay(version, componentGeneration, sceneGeneration,
      viewportGeneration, layers) {
    if (version !== PROTOCOL_VERSION || componentGeneration !== this.componentGeneration ||
        !Array.isArray(layers)) {
      return;
    }
    if (sceneGeneration === this.pendingSceneGeneration) {
      this.pendingInteractionOverlay = {version, componentGeneration, sceneGeneration,
        viewportGeneration, layers};
      return;
    }
    if (sceneGeneration !== this.sceneGeneration ||
        viewportGeneration !== this.viewportGeneration) return;
    try {
      const candidate = validateScene({
        protocolVersion: PROTOCOL_VERSION,
        componentGeneration,
        sceneGeneration,
        viewportGeneration,
        background: [0, 0, 0, 0],
        viewport: {...this.viewport},
        labelCandidates: [],
        layers
      }, componentGeneration, sceneGeneration - 1);
      this.interactionLayers = deepFreeze(candidate.layers);
      this.pendingInteractionOverlay = null;
      this.preflightLayers(this.interactionLayers, this.iconResources);
      this.schedulePaint();
    } catch (error) {
      this.reportFailure(error.message);
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
      const icons = this.sceneIconMetadata(accepted);
      resources = {icons, rasters: this.sceneRasterMetadata(accepted, icons)};
    } catch (error) {
      this.reportFailure(error.message, candidate.sceneGeneration);
      return;
    }
    this.interactionEpoch++;
    this.toolPointerRateLimited = false;
    this.toolPointerRateCancellationQueued = false;
    this.viewportDirty = false;
    this.setToolState(this.toolActive, false, 'DEFAULT');
    this.releaseClientPointers();
    this.cancelSceneLoad();
    this.pendingSceneGeneration = accepted.sceneGeneration;
    this.pendingViewport = null;
    this.pendingInteractionOverlay = null;
    const sequence = ++this.sceneLoadSequence;
    if (!resources.icons.size && !resources.rasters.size) {
      this.acceptLoadedScene(accepted, acceptedViewport, new Map(), new Map(), sequence);
      return;
    }
    const controller = new AbortController();
    this.sceneLoadAbort = controller;
    Promise.all([
      this.loadSceneIcons(resources.icons, controller.signal),
      this.loadSceneRasters(resources.rasters, accepted, controller.signal)
    ]).then(([icons, rasters]) =>
      this.acceptLoadedScene(accepted, acceptedViewport, icons, rasters, sequence))
      .catch(error => {
        if (!controller.signal.aborted && sequence === this.sceneLoadSequence) {
          controller.abort();
          if (this.sceneLoadAbort === controller) this.sceneLoadAbort = null;
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

  sceneRasterMetadata(scene, icons) {
    const resources = new Map();
    let total = [...icons.values()].reduce((sum, metadata) => sum + metadata.bytes, 0);
    for (const raster of scene.rasters || []) {
      if (resources.has(raster.resource)) throw new Error('RESOURCE_UNAVAILABLE');
      const bytes = 32 + raster.width * raster.height * 4;
      if (bytes - 32 > MAX_RASTER_WINDOW_BYTES) throw new Error('LIMIT_EXCEEDED');
      total += bytes;
      if (resources.size >= MAX_RASTER_WINDOWS || total > MAX_SCENE_RESOURCE_BYTES) {
        throw new Error('LIMIT_EXCEEDED');
      }
      resources.set(raster.resource, {width: raster.width, height: raster.height, bytes});
    }
    return resources;
  }

  async loadSceneIcons(resources, signal) {
    const loaded = new Map();
    for (const [resource, metadata] of resources) {
      const response = await fetch(resource,
        {credentials: 'same-origin', cache: 'no-store', redirect: 'error', signal});
      if (!response.ok) throw new Error('RESOURCE_UNAVAILABLE');
      const bytes = await readExactResponse(response, metadata.bytes);
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

  async loadSceneRasters(resources, scene, signal) {
    const loaded = new Map();
    for (const [resource, metadata] of resources) {
      const response = await fetch(resource,
        {credentials: 'same-origin', cache: 'no-store', redirect: 'error', signal});
      if (!response.ok) throw new Error('RESOURCE_UNAVAILABLE');
      const contentType = response.headers?.get?.('content-type')?.split(';')[0]?.trim();
      if (contentType !== 'application/vnd.mundane-map.rgba-window') {
        throw new Error('RESOURCE_UNAVAILABLE');
      }
      const bytes = await readExactResponse(response, metadata.bytes);
      const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
      const componentGeneration = view.getUint32(16) * 4294967296 + view.getUint32(20);
      const sceneGeneration = view.getUint32(24) * 4294967296 + view.getUint32(28);
      if (bytes[0] !== 77 || bytes[1] !== 77 || bytes[2] !== 82 || bytes[3] !== 87 ||
          bytes[4] !== 1 || bytes[5] !== 0 || view.getUint16(6) !== 32 ||
          view.getUint32(8) !== metadata.width || view.getUint32(12) !== metadata.height ||
          !Number.isSafeInteger(componentGeneration) || !Number.isSafeInteger(sceneGeneration) ||
          componentGeneration !== scene.componentGeneration ||
          sceneGeneration !== scene.sceneGeneration) {
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
      pixels.data.set(bytes.subarray(32));
      context.putImageData(pixels, 0, 0);
      loaded.set(resource, image);
    }
    return loaded;
  }

  acceptLoadedScene(accepted, acceptedViewport, icons, rasters, sequence) {
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
    const previousInteractionLayers = this.interactionLayers;
    this.viewport = finalViewport;
    this.interactionLayers = [];
    try {
      this.preflightPaint(accepted, icons, rasters);
    } catch (error) {
      this.viewport = previousViewport;
      this.interactionLayers = previousInteractionLayers;
      this.pendingSceneGeneration = -1;
      this.pendingViewport = null;
      this.reportFailure(error.message, accepted.sceneGeneration);
      return;
    }
    this.sceneLoadAbort = null;
    this.scene = accepted;
    this.iconResources = icons;
    this.rasterResources = rasters;
    this.placedLabels = [];
    this.lastLabelMeasurementKey = null;
    this.pendingLabelAcknowledgement = null;
    this.sceneGeneration = accepted.sceneGeneration;
    this.viewportGeneration = finalViewportGeneration;
    this.viewport = finalViewport;
    this.viewportDirty = false;
    this.pendingSceneGeneration = -1;
    this.pendingViewport = null;
    const pendingOverlay = this.pendingInteractionOverlay;
    this.pendingInteractionOverlay = null;
    this.schedulePaint();
    this.requestLabelMeasurements();
    if (pendingOverlay && pendingOverlay.sceneGeneration === this.sceneGeneration &&
        pendingOverlay.viewportGeneration === this.viewportGeneration) {
      this.setInteractionOverlay(pendingOverlay.version, pendingOverlay.componentGeneration,
        pendingOverlay.sceneGeneration, pendingOverlay.viewportGeneration, pendingOverlay.layers);
    }
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
      if (viewportGeneration === this.viewportGeneration &&
          !sameViewport(candidate, this.viewport)) {
        this.reportFailure('STALE_GENERATION', sceneGeneration);
        return;
      }
      const changed = viewportGeneration !== this.viewportGeneration;
      this.viewport = candidate;
      this.viewportGeneration = viewportGeneration;
      this.viewportDirty = false;
      if (changed) {
        this.placedLabels = [];
        this.pendingLabelAcknowledgement = null;
      }
      this.schedulePaint();
      this.requestLabelMeasurements();
    } catch (error) {
      this.reportFailure(error.message, sceneGeneration);
    }
  }

  remeasureLabels(version, componentGeneration, sceneGeneration, viewportGeneration) {
    if (version !== PROTOCOL_VERSION || componentGeneration !== this.componentGeneration ||
        sceneGeneration !== this.sceneGeneration ||
        viewportGeneration !== this.viewportGeneration) {
      this.reportFailure('STALE_GENERATION', sceneGeneration);
      return;
    }
    this.placedLabels = [];
    this.lastLabelMeasurementKey = null;
    this.pendingLabelAcknowledgement = null;
    this.schedulePaint();
    this.requestLabelMeasurements();
  }

  requestLabelMeasurements() {
    if (!this.scene || !this.$server || !this.enabled || !this.active) return;
    const key = `${this.componentGeneration}/${this.sceneGeneration}/${this.viewportGeneration}`;
    if (key === this.lastLabelMeasurementKey) return;
    this.lastLabelMeasurementKey = key;
    const candidates = this.scene.labelCandidates;
    if (!candidates.length) {
      this.pendingLabelAcknowledgement = {
        componentGeneration: this.componentGeneration,
        sceneGeneration: this.sceneGeneration,
        viewportGeneration: this.viewportGeneration
      };
      this.schedulePaint();
      return;
    }
    const metrics = [];
    try {
      for (const candidate of candidates) {
        this.context.font = `${candidate.weight === 'BOLD' ? '700' : '400'} ` +
          `${candidate.sizePixels}px sans-serif`;
        this.context.textBaseline = 'alphabetic';
        const measured = this.context.measureText(candidate.text);
        const values = [measured.width, -measured.actualBoundingBoxLeft,
          -measured.actualBoundingBoxAscent, measured.actualBoundingBoxRight,
          measured.actualBoundingBoxDescent];
        if (!values.every(Number.isFinite) || values[0] < 0 ||
            values.some(value => Math.abs(value) > MAX_LABEL_METRIC_MAGNITUDE) ||
            values[3] < values[1] || values[4] < values[2]) {
          throw new Error('BROWSER_CAPABILITY_UNSUPPORTED');
        }
        metrics.push(...values);
      }
      this.$server.acceptLabelMeasurements?.(PROTOCOL_VERSION, this.componentGeneration,
        this.sceneGeneration, this.viewportGeneration, metrics);
    } catch (_error) {
      this.reportFailure('BROWSER_CAPABILITY_UNSUPPORTED', this.sceneGeneration);
    }
  }

  setPlacedLabels(version, componentGeneration, sceneGeneration, viewportGeneration, labels) {
    if (version !== PROTOCOL_VERSION || componentGeneration !== this.componentGeneration ||
        sceneGeneration !== this.sceneGeneration ||
        viewportGeneration !== this.viewportGeneration || !Array.isArray(labels)) {
      this.reportFailure('STALE_GENERATION', sceneGeneration);
      return;
    }
    try {
      if (labels.length > MAX_LABELS) throw new Error('LIMIT_EXCEEDED');
      let priorOrdinal = -1;
      const accepted = labels.map(label => {
        if (!label || typeof label.text !== 'string' || !label.text ||
            label.text.length > 512 || [...label.text].length > 256 ||
            /[\r\n\u2028\u2029]/u.test(label.text) || !validateColor(label.color) ||
            !['NORMAL', 'BOLD'].includes(label.weight) ||
            ![label.sizePixels, label.baselineX, label.baselineY, label.advance]
              .every(Number.isFinite) || label.sizePixels < 1 || label.sizePixels > 512 ||
            label.advance < 0 || !Number.isSafeInteger(label.ordinal) ||
            label.ordinal <= priorOrdinal || label.ordinal >= this.scene.labelCandidates.length ||
            [label.baselineX, label.baselineY, label.advance]
              .some(value => Math.abs(value) > MAX_LABEL_METRIC_MAGNITUDE)) {
          throw new Error('SYMBOL_UNSUPPORTED');
        }
        const candidate = this.scene.labelCandidates[label.ordinal];
        if (label.text !== candidate.text || label.weight !== candidate.weight ||
            label.sizePixels !== candidate.sizePixels) {
          throw new Error('SYMBOL_UNSUPPORTED');
        }
        priorOrdinal = label.ordinal;
        return {...label, color: [...label.color]};
      });
      this.placedLabels = deepFreeze(accepted);
      this.pendingLabelAcknowledgement = {
        componentGeneration: this.componentGeneration,
        sceneGeneration: this.sceneGeneration,
        viewportGeneration: this.viewportGeneration
      };
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
    this.canvas.addEventListener('pointercancel', this.boundPointerCancel);
    this.canvas.addEventListener('pointerleave', this.boundPointerLeave);
    this.canvas.addEventListener('click', this.boundClick);
    this.canvas.addEventListener('auxclick', this.boundAuxClick);
    this.canvas.addEventListener('contextmenu', this.boundContextMenu);
    this.canvas.addEventListener('wheel', this.boundWheel, {passive: false});
    this.canvas.addEventListener('keydown', this.boundKeyDown);
    this.canvas.addEventListener('blur', this.boundBlur);
    this.canvas.addEventListener('focus', this.boundFocus);
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
    this.canvas.removeEventListener('pointercancel', this.boundPointerCancel);
    this.canvas.removeEventListener('pointerleave', this.boundPointerLeave);
    this.canvas.removeEventListener('click', this.boundClick);
    this.canvas.removeEventListener('auxclick', this.boundAuxClick);
    this.canvas.removeEventListener('contextmenu', this.boundContextMenu);
    this.canvas.removeEventListener('wheel', this.boundWheel);
    this.canvas.removeEventListener('keydown', this.boundKeyDown);
    this.canvas.removeEventListener('blur', this.boundBlur);
    this.canvas.removeEventListener('focus', this.boundFocus);
    for (const pointerId of this.pointers.keys()) {
      if (this.canvas.hasPointerCapture(pointerId)) {
        this.canvas.releasePointerCapture(pointerId);
      }
    }
    this.pointers.clear();
    this.lastPinch = null;
    if (this.hoverTimer) {
      clearTimeout(this.hoverTimer);
      this.hoverTimer = 0;
    }
    this.pendingHoverEvent = null;
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
    if (changed && (this.pointers.size || this.toolCaptured)) {
      this.settleTerminatedGesture();
      this.sendCancellation(this.lifecycleSnapshot({}), 'POINTER_STATE_LOST');
      this.releaseClientPointers();
    }
    this.viewport = resizeViewport(this.viewport, width, height);
    if (changed) {
      this.viewportDirty = true;
      this.placedLabels = [];
      this.pendingLabelAcknowledgement = null;
    }
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
      const dpr = Number(this.canvas.dataset.devicePixelRatio || 1);
      this.context.setTransform(dpr, 0, 0, dpr, 0, 0);
      this.context.clearRect(0, 0, this.viewport.width, this.viewport.height);
      if (!this.scene) {
        return;
      }
      this.context.fillStyle = rgba(this.scene.background);
      this.context.fillRect(0, 0, this.viewport.width, this.viewport.height);
      for (const raster of this.scene.rasters || []) {
        this.drawRaster(raster);
      }
      for (const layer of this.scene.layers) {
        for (const feature of layer.features) {
          for (const primitive of feature.primitives) {
            this.drawPrimitive(primitive);
          }
        }
      }
      for (const label of this.placedLabels) {
        this.context.save();
        try {
          this.context.font = `${label.weight === 'BOLD' ? '700' : '400'} ` +
            `${label.sizePixels}px sans-serif`;
          this.context.textBaseline = 'alphabetic';
          this.context.fillStyle = rgba(label.color);
          this.context.fillText(label.text, label.baselineX, label.baselineY);
        } finally {
          this.context.restore();
        }
      }
      for (const layer of this.interactionLayers) {
        for (const feature of layer.features) {
          for (const primitive of feature.primitives) {
            this.drawPrimitive(primitive);
          }
        }
      }
    } catch (error) {
      this.pendingLabelAcknowledgement = null;
      this.reportFailure(error.message);
      return;
    }
    const acknowledgement = this.pendingLabelAcknowledgement;
    if (acknowledgement &&
        acknowledgement.componentGeneration === this.componentGeneration &&
        acknowledgement.sceneGeneration === this.sceneGeneration &&
        acknowledgement.viewportGeneration === this.viewportGeneration) {
      this.pendingLabelAcknowledgement = null;
      this.$server?.acceptPlacedLabels?.(PROTOCOL_VERSION, this.componentGeneration,
        this.sceneGeneration, this.viewportGeneration);
    }
  }

  drawPrimitive(primitive) {
    this.context.save();
    try {
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
    } finally {
      this.context.restore();
    }
  }

  drawRaster(raster) {
    if (raster.opacity === 0) return;
    const image = this.rasterResources.get(raster.resource);
    if (!image) throw new Error('RESOURCE_UNAVAILABLE');
    const matrix = this.rasterTransform(raster);
    const clip = raster.clipMapBounds;
    const northWest = this.screen([clip[0], clip[3]], 0);
    const southEast = this.screen([clip[2], clip[1]], 0);
    this.context.save();
    try {
      this.context.beginPath();
      this.context.rect(northWest[0], northWest[1],
        southEast[0] - northWest[0], southEast[1] - northWest[1]);
      this.context.clip();
      this.context.imageSmoothingEnabled = raster.interpolation === 'BILINEAR';
      this.context.globalAlpha = raster.opacity;
      this.context.transform(matrix.m00, matrix.m10, matrix.m01, matrix.m11,
        matrix.m02, matrix.m12);
      this.context.drawImage(image, 0, 0);
    } finally {
      this.context.restore();
    }
  }

  rasterTransform(raster) {
    let mapOriginX;
    let mapOriginY;
    let mapColumnX;
    let mapColumnY;
    let mapRowX;
    let mapRowY;
    if (raster.placement.kind === 'AXIS_ALIGNED') {
      const bounds = raster.placement.bounds;
      mapOriginX = bounds[0];
      mapOriginY = bounds[3];
      mapColumnX = (bounds[2] - bounds[0]) / raster.width;
      mapColumnY = 0;
      mapRowX = 0;
      mapRowY = (bounds[1] - bounds[3]) / raster.height;
    } else {
      const [a, d, b, e, c, f] = raster.placement.transform;
      const [column, row, width, height] = raster.sourceWindow;
      const firstColumnEdge = column - 0.5;
      const firstRowEdge = row - 0.5;
      mapOriginX = a * firstColumnEdge + b * firstRowEdge + c;
      mapOriginY = d * firstColumnEdge + e * firstRowEdge + f;
      mapColumnX = a * width / raster.width;
      mapColumnY = d * width / raster.width;
      mapRowX = b * height / raster.height;
      mapRowY = e * height / raster.height;
    }
    const m00 = mapColumnX / this.viewport.worldUnitsPerPixel;
    const m10 = -mapColumnY / this.viewport.worldUnitsPerPixel;
    const m01 = mapRowX / this.viewport.worldUnitsPerPixel;
    const m11 = -mapRowY / this.viewport.worldUnitsPerPixel;
    const m02 = this.viewport.width / 2 +
      (mapOriginX - this.viewport.centerX) / this.viewport.worldUnitsPerPixel;
    const m12 = this.viewport.height / 2 -
      (mapOriginY - this.viewport.centerY) / this.viewport.worldUnitsPerPixel;
    const values = [mapOriginX, mapOriginY, mapColumnX, mapColumnY, mapRowX, mapRowY,
      m00, m10, m01, m11, m02, m12];
    if (!values.every(Number.isFinite)) throw new Error('NON_FINITE_VALUE');
    for (const x of [0, raster.width]) for (const y of [0, raster.height]) {
      const cornerX = m00 * x + m01 * y + m02;
      const cornerY = m10 * x + m11 * y + m12;
      if (!Number.isFinite(cornerX) || !Number.isFinite(cornerY)) {
        throw new Error('NON_FINITE_VALUE');
      }
    }
    return {m00, m10, m01, m11, m02, m12};
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

  preflightPaint(scene = this.scene, iconResources = this.iconResources,
      rasterResources = this.rasterResources) {
    if (!scene) {
      return;
    }
    for (const raster of scene.rasters || []) {
      if (!rasterResources.has(raster.resource)) throw new Error('RESOURCE_UNAVAILABLE');
      this.rasterTransform(raster);
      const clip = raster.clipMapBounds;
      this.preflightCoordinates([clip[0], clip[1], clip[2], clip[3]]);
    }
    this.preflightLayers([...scene.layers, ...this.interactionLayers], iconResources);
  }

  preflightLayers(layers, iconResources) {
    let hatchSegments = 0;
    for (const layer of layers) {
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
    this.rememberPointerEvent(event);
    this.canvas.focus?.({preventScroll: true});
    this.canvas.setPointerCapture(event.pointerId);
    this.pointers.set(event.pointerId, {x: event.offsetX, y: event.offsetY,
      button: this.pointerButton(event.button), moved: false});
    this.updatePinch();
    if (this.toolActive) {
      this.sendInteraction(event, 'PRESS', '').then(outcome => {
        this.applyToolOutcome(outcome, event.pointerId);
      });
    } else {
      this.sendInteraction(event, 'PRESS', '').then(outcome => this.applyToolOutcome(outcome));
    }
  }

  onPointerMove(event) {
    this.rememberPointerEvent(event);
    const previous = this.pointers.get(event.pointerId);
    if (!previous) {
      if (event.buttons === 0) {
        this.queueHover(event);
      } else if (this.toolActive) {
        this.sendCancellation(event, 'POINTER_STATE_LOST');
      }
      return;
    }
    if (event.buttons === 0) {
      this.settleTerminatedGesture();
      this.sendCancellation(event, 'POINTER_STATE_LOST');
      this.releaseClientPointers();
      return;
    }
    const current = {x: event.offsetX, y: event.offsetY,
      button: previous.button, moved: true};
    this.pointers.set(event.pointerId, current);
    const navigate = () => {
      try {
        let next = this.viewport;
        if (this.pointers.size === 1 && event.buttons === 1) {
          next = panViewport(next,
            current.x - previous.x, current.y - previous.y);
        } else if (this.pointers.size === 2 && this.lastPinch) {
          const points = [...this.pointers.values()];
          const distance = Math.hypot(points[1].x - points[0].x, points[1].y - points[0].y);
          const centerX = (points[0].x + points[1].x) / 2;
          const centerY = (points[0].y + points[1].y) / 2;
          if (this.lastPinch.distance > 0 && distance > 0) {
            next = panViewport(next,
              centerX - this.lastPinch.centerX, centerY - this.lastPinch.centerY);
            next = zoomViewport(next, centerX, centerY,
              distance / this.lastPinch.distance);
          }
          this.lastPinch = {distance, centerX, centerY};
        }
        this.viewport = next;
        this.afterLocalNavigation(false);
        return true;
      } catch (error) {
        this.reportFailure(error.message);
        return false;
      }
    };
    if (this.toolActive) {
      this.applyRoutedInteraction(this.sendInteraction(event, 'DRAG', ''), outcome => {
        this.applyToolOutcome(outcome, event.pointerId);
        return Boolean(outcome?.accepted && !outcome.suppressDefault && navigate());
      });
      return;
    }
    navigate();
  }

  onPointerUp(event) {
    if (!this.pointers.has(event.pointerId)) {
      return;
    }
    this.rememberPointerEvent(event);
    const retained = event.buttons !== 0;
    if (retained) {
      const previous = this.pointers.get(event.pointerId);
      this.pointers.set(event.pointerId, {
        x: event.offsetX, y: event.offsetY, button: previous.button, moved: previous.moved
      });
    } else {
      this.pointers.delete(event.pointerId);
    }
    if (this.toolActive) {
      this.sendInteraction(event, 'RELEASE', '').then(outcome => {
        this.applyToolOutcome(outcome, event.pointerId);
        if (!retained && !outcome?.captured && this.canvas.hasPointerCapture(event.pointerId)) {
          this.canvas.releasePointerCapture(event.pointerId);
        }
      });
    } else {
      this.sendInteraction(event, 'RELEASE', '').then(outcome => this.applyToolOutcome(outcome));
      if (!retained && this.canvas.hasPointerCapture(event.pointerId)) {
        this.canvas.releasePointerCapture(event.pointerId);
      }
    }
    this.updatePinch();
    if (!this.pointers.size) {
      this.emitSettled();
    }
  }

  onPointerCancel(event) {
    if (!this.pointers.has(event.pointerId)) return;
    this.rememberPointerEvent(event);
    this.settleTerminatedGesture();
    this.sendCancellation(event, 'POINTER_STATE_LOST');
    this.releaseClientPointers();
  }

  onPointerLeave(event) {
    this.rememberPointerEvent(event);
    if (!this.toolCaptured) {
      this.settleTerminatedGesture();
      this.sendCancellation(event, 'POINTER_EXITED');
      this.releaseClientPointers();
    }
    this.pendingHoverEvent = null;
  }

  onClick(event) {
    if (!this.enabled || this.closed || this.pointers.size) return;
    this.rememberPointerEvent(event);
    this.sendInteraction(event, 'CLICK', '').then(outcome => this.applyToolOutcome(outcome));
  }

  onAuxClick(event) {
    if (!this.enabled || this.closed || this.pointers.size || event.button !== 1) return;
    this.rememberPointerEvent(event);
    event.preventDefault();
    this.sendInteraction(event, 'CLICK', '').then(outcome => this.applyToolOutcome(outcome));
  }

  onContextMenu(event) {
    if (!this.enabled || this.closed || this.pointers.size) return;
    this.rememberPointerEvent(event);
    event.preventDefault();
    this.sendInteraction(event, 'CLICK', '', true)
      .then(outcome => this.applyToolOutcome(outcome));
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
    this.rememberPointerEvent(event);
    event.preventDefault();
    const navigate = () => {
      const factor = Math.exp(-Math.max(-100, Math.min(100, event.deltaY)) * 0.002);
      try {
        this.viewport = zoomViewport(this.viewport, event.offsetX, event.offsetY, factor);
        this.afterLocalNavigation(true);
        return true;
      } catch (error) {
        this.reportFailure(error.message);
        return false;
      }
    };
    if (this.toolActive) {
      this.applyRoutedInteraction(this.sendInteraction(event, 'WHEEL', ''), outcome => {
        this.applyToolOutcome(outcome);
        return Boolean(outcome?.accepted && !outcome.suppressDefault && navigate());
      });
    } else {
      navigate();
    }
  }

  onKeyDown(event) {
    if (!this.enabled || this.closed || event.altKey) return;
    const commandModifier = Boolean(event.ctrlKey) !== Boolean(event.metaKey);
    let command = null;
    if (this.toolActive && commandModifier && event.key.toLowerCase() === 'z') {
      command = event.shiftKey ? 'REDO' : 'UNDO';
    } else if (this.toolActive && commandModifier && !event.shiftKey &&
        event.key.toLowerCase() === 'y') {
      command = 'REDO';
    } else if (this.toolActive && !event.ctrlKey && !event.metaKey && !event.shiftKey &&
        event.key === 'Backspace') {
      command = 'DELETE_BACKWARD';
    }
    if (command) {
      event.preventDefault();
      this.sendCommand(command).then(outcome => this.applyToolOutcome(outcome));
      return;
    }
    if (event.ctrlKey || event.metaKey || event.shiftKey) return;
    if (event.key === 'Escape' && (this.toolActive || this.pointers.size)) {
      event.preventDefault();
      this.settleTerminatedGesture();
      this.sendCancellation(this.lifecycleSnapshot(event), 'USER_CANCEL');
      this.releaseClientPointers();
      return;
    }
    const pans = {ArrowLeft: [40, 0], ArrowRight: [-40, 0],
      ArrowUp: [0, 40], ArrowDown: [0, -40]};
    if (pans[event.key]) {
      event.preventDefault();
      try {
        this.viewport = panViewport(this.viewport, ...pans[event.key]);
        this.afterLocalNavigation(true);
      } catch (error) {
        this.reportFailure(error.message);
      }
      return;
    }
    if (['+', '=', '-', '_'].includes(event.key)) {
      event.preventDefault();
      const factor = ['+', '='].includes(event.key) ? 2 : 0.5;
      try {
        this.viewport = zoomViewport(this.viewport,
          this.viewport.width / 2, this.viewport.height / 2, factor);
        this.afterLocalNavigation(true);
      } catch (error) {
        this.reportFailure(error.message);
      }
    }
  }

  onBlur(event) {
    if (this.toolActive || this.pointers.size) {
      this.settleTerminatedGesture();
      this.sendCancellation(this.lifecycleSnapshot(event), 'FOCUS_LOST');
      this.releaseClientPointers();
    }
  }

  onFocus() {
    if (!this.enabled || this.closed || !this.toolActive) return;
    this.sendResume().then(outcome => this.applyToolOutcome(outcome));
  }

  queueHover(event) {
    const snapshot = this.eventSnapshot(event);
    const elapsed = performance.now() - this.lastHoverMilliseconds;
    if (elapsed >= 50 && !this.hoverTimer) {
      this.lastHoverMilliseconds = performance.now();
      this.sendInteraction(snapshot, 'MOVE', '').then(outcome => this.applyToolOutcome(outcome));
      return;
    }
    this.pendingHoverEvent = snapshot;
    if (!this.hoverTimer) {
      this.hoverTimer = setTimeout(() => {
        this.hoverTimer = 0;
        const pending = this.pendingHoverEvent;
        this.pendingHoverEvent = null;
        if (pending) {
          this.lastHoverMilliseconds = performance.now();
          this.sendInteraction(pending, 'MOVE', '')
            .then(outcome => this.applyToolOutcome(outcome));
        }
      }, Math.max(1, Math.ceil(50 - elapsed)));
    }
  }

  afterLocalNavigation(settle) {
    this.viewportDirty = true;
    this.placedLabels = [];
    this.pendingLabelAcknowledgement = null;
    this.interactionLayers = this.interactionLayers.filter(layer => layer.id !== '__hover');
    this.schedulePaint();
    if (!settle) return;
    if (this.settleTimer) clearTimeout(this.settleTimer);
    this.settleTimer = setTimeout(() => {
      this.settleTimer = 0;
      this.emitSettled();
    }, 80);
  }

  pointerButton(button) {
    return Number.isInteger(button) && button >= 0 && button <= 2 ? button + 1 : 0;
  }

  modifierMask(event) {
    return (event.shiftKey ? 1 : 0) | (event.ctrlKey ? 2 : 0) |
      (event.altKey ? 4 : 0) | (event.metaKey ? 8 : 0) |
      (event.getModifierState?.('AltGraph') ? 16 : 0);
  }

  eventSnapshot(event) {
    return {
      offsetX: Number(event.offsetX ?? 0), offsetY: Number(event.offsetY ?? 0),
      button: Number(event.button ?? -1), buttons: Number(event.buttons ?? 0),
      detail: Number(event.detail ?? 0), deltaY: Number(event.deltaY ?? 0),
      shiftKey: Boolean(event.shiftKey), ctrlKey: Boolean(event.ctrlKey),
      altKey: Boolean(event.altKey), metaKey: Boolean(event.metaKey),
      getModifierState: name => name === 'AltGraph' && Boolean(event.getModifierState?.(name))
    };
  }

  rememberPointerEvent(event) {
    if (Number.isFinite(event.offsetX) && Number.isFinite(event.offsetY)) {
      this.lastPointerSnapshot = this.eventSnapshot(event);
    }
  }

  lifecycleSnapshot(event) {
    const retained = this.lastPointerSnapshot || {
      offsetX: this.viewport.width / 2, offsetY: this.viewport.height / 2,
      button: -1, buttons: 0, detail: 0, deltaY: 0,
      shiftKey: false, ctrlKey: false, altKey: false, metaKey: false,
      getModifierState: () => false
    };
    return {
      ...retained,
      shiftKey: typeof event.shiftKey === 'boolean' ? event.shiftKey : retained.shiftKey,
      ctrlKey: typeof event.ctrlKey === 'boolean' ? event.ctrlKey : retained.ctrlKey,
      altKey: typeof event.altKey === 'boolean' ? event.altKey : retained.altKey,
      metaKey: typeof event.metaKey === 'boolean' ? event.metaKey : retained.metaKey,
      getModifierState: name => typeof event.getModifierState === 'function' ?
        event.getModifierState(name) : retained.getModifierState?.(name)
    };
  }

  sendInteraction(event, type, cancelReason, popupTrigger = false,
    requiredCancellation = false) {
    const snapshot = this.eventSnapshot(event);
    if (type === 'CANCEL' && !requiredCancellation &&
        this.pendingToolEvents >= MAX_PENDING_TOOL_EVENTS + 2) {
      return Promise.resolve(
        {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'});
    }
    if (type !== 'CANCEL') {
      const now = performance.now();
      const elapsed = Math.max(0, now - this.toolPointerRefillMilliseconds);
      this.toolPointerRefillMilliseconds = now;
      this.toolPointerTokens = Math.min(120,
        this.toolPointerTokens + elapsed * 0.12);
      if (this.toolPointerRateLimited) {
        return Promise.resolve(
          {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'});
      }
      if (this.pendingToolEvents >= MAX_PENDING_TOOL_EVENTS) {
        this.beginClientRateCancellation(snapshot);
        return Promise.resolve(
          {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'});
      }
      if (this.toolPointerTokens < 1) {
        this.toolPointerRateLimited = true;
      } else {
        this.toolPointerTokens -= 1;
      }
    }
    const result = this.enqueueInteraction(snapshot, type, cancelReason, popupTrigger);
    if (type !== 'CANCEL' && this.toolPointerRateLimited) {
      this.beginClientRateCancellation(snapshot);
    }
    return result;
  }

  enqueueInteraction(snapshot, type, cancelReason, popupTrigger) {
    const componentGeneration = this.componentGeneration;
    const sceneGeneration = this.sceneGeneration;
    const interactionEpoch = this.interactionEpoch;
    const operation = async () => {
      if (!this.$server?.acceptMapInteraction || !this.active || !this.scene ||
          interactionEpoch !== this.interactionEpoch) {
        return {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'};
      }
      const changedButton = ['PRESS', 'RELEASE', 'CLICK'].includes(type) ?
        this.pointerButton(snapshot.button) : 0;
      const clickCount = ['PRESS', 'RELEASE'].includes(type) ?
        Math.max(0, Math.trunc(snapshot.detail)) :
        (type === 'CLICK' ? Math.max(1, Math.trunc(snapshot.detail || 1)) : 0);
      const wheel = type === 'WHEEL' ? snapshot.deltaY : 0;
      const viewportGeneration = this.viewportGeneration;
      let outcome;
      try {
        outcome = await this.$server.acceptMapInteraction(PROTOCOL_VERSION,
          componentGeneration, sceneGeneration, viewportGeneration,
          this.eventSequence++, type, snapshot.offsetX, snapshot.offsetY,
          changedButton, snapshot.buttons, this.modifierMask(snapshot), clickCount,
          wheel, popupTrigger, cancelReason);
      } catch (error) {
        if (type !== 'CANCEL') {
          this.beginClientFailureCancellation(snapshot);
        } else {
          this.toolPointerRateLimited = false;
          this.toolPointerRateCancellationQueued = false;
          this.setToolState(this.toolActive, false, 'DEFAULT');
          this.releaseClientPointers();
        }
        this.reportFailure(error?.message || 'CLIENT_FAILURE');
        return {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'};
      }
      if (type === 'CANCEL') {
        this.toolPointerRateLimited = false;
        this.toolPointerRateCancellationQueued = false;
      }
      if (interactionEpoch !== this.interactionEpoch || !this.active || !this.enabled ||
          this.closed || componentGeneration !== this.componentGeneration ||
          sceneGeneration !== this.sceneGeneration ||
          viewportGeneration !== this.viewportGeneration) {
        return {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'};
      }
      return outcome;
    };
    this.pendingToolEvents++;
    const result = this.interactionChain.then(operation, operation);
    const tracked = result.finally(() => this.pendingToolEvents--);
    this.interactionChain = tracked.catch(
      error => this.reportFailure(error?.message || 'CLIENT_FAILURE'));
    return tracked;
  }

  sendCommand(command) {
    const snapshot = {offsetX: this.viewport.width / 2, offsetY: this.viewport.height / 2,
      button: -1, buttons: 0};
    const now = performance.now();
    const elapsed = Math.max(0, now - this.toolPointerRefillMilliseconds);
    this.toolPointerRefillMilliseconds = now;
    this.toolPointerTokens = Math.min(120, this.toolPointerTokens + elapsed * 0.12);
    if (this.toolPointerRateLimited) {
      return Promise.resolve(
        {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'});
    }
    if (this.pendingToolEvents >= MAX_PENDING_TOOL_EVENTS) {
      this.beginClientRateCancellation(snapshot);
      return Promise.resolve(
        {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'});
    }
    if (this.toolPointerTokens < 1) {
      this.toolPointerRateLimited = true;
    } else {
      this.toolPointerTokens -= 1;
    }
    const componentGeneration = this.componentGeneration;
    const sceneGeneration = this.sceneGeneration;
    const interactionEpoch = this.interactionEpoch;
    const operation = async () => {
      if (!this.$server?.acceptMapCommand || !this.active || !this.scene ||
          interactionEpoch !== this.interactionEpoch) {
        return {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'};
      }
      const viewportGeneration = this.viewportGeneration;
      let outcome;
      try {
        outcome = await this.$server.acceptMapCommand(PROTOCOL_VERSION,
          componentGeneration, sceneGeneration, viewportGeneration,
          this.eventSequence++, command);
      } catch (error) {
        this.beginClientFailureCancellation(snapshot);
        this.reportFailure(error?.message || 'CLIENT_FAILURE');
        return {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'};
      }
      if (interactionEpoch !== this.interactionEpoch || !this.active || !this.enabled ||
          this.closed || componentGeneration !== this.componentGeneration ||
          sceneGeneration !== this.sceneGeneration ||
          viewportGeneration !== this.viewportGeneration) {
        return {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'};
      }
      return outcome;
    };
    this.pendingToolEvents++;
    const result = this.interactionChain.then(operation, operation);
    const tracked = result.finally(() => this.pendingToolEvents--);
    this.interactionChain = tracked.catch(
      error => this.reportFailure(error?.message || 'CLIENT_FAILURE'));
    if (this.toolPointerRateLimited) this.beginClientRateCancellation(snapshot);
    return tracked;
  }

  sendResume() {
    const snapshot = this.lifecycleSnapshot({});
    const now = performance.now();
    const elapsed = Math.max(0, now - this.toolPointerRefillMilliseconds);
    this.toolPointerRefillMilliseconds = now;
    this.toolPointerTokens = Math.min(120, this.toolPointerTokens + elapsed * 0.12);
    if (this.toolPointerRateLimited || this.pendingToolEvents >= MAX_PENDING_TOOL_EVENTS) {
      if (!this.toolPointerRateLimited) this.beginClientRateCancellation(snapshot);
      return Promise.resolve(
        {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'});
    }
    if (this.toolPointerTokens < 1) {
      this.toolPointerRateLimited = true;
    } else {
      this.toolPointerTokens -= 1;
    }
    const componentGeneration = this.componentGeneration;
    const sceneGeneration = this.sceneGeneration;
    const interactionEpoch = this.interactionEpoch;
    const operation = async () => {
      if (!this.$server?.acceptMapToolResume || !this.active || !this.scene ||
          interactionEpoch !== this.interactionEpoch) {
        return {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'};
      }
      const viewportGeneration = this.viewportGeneration;
      let outcome;
      try {
        outcome = await this.$server.acceptMapToolResume(PROTOCOL_VERSION,
          componentGeneration, sceneGeneration, viewportGeneration, this.eventSequence++);
      } catch (error) {
        this.beginClientFailureCancellation(snapshot);
        this.reportFailure(error?.message || 'CLIENT_FAILURE');
        return {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'};
      }
      if (interactionEpoch !== this.interactionEpoch || !this.active || !this.enabled ||
          this.closed || componentGeneration !== this.componentGeneration ||
          sceneGeneration !== this.sceneGeneration ||
          viewportGeneration !== this.viewportGeneration) {
        return {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'};
      }
      return outcome;
    };
    this.pendingToolEvents++;
    const result = this.interactionChain.then(operation, operation);
    const tracked = result.finally(() => this.pendingToolEvents--);
    this.interactionChain = tracked.catch(
      error => this.reportFailure(error?.message || 'CLIENT_FAILURE'));
    if (this.toolPointerRateLimited) this.beginClientRateCancellation(snapshot);
    return tracked;
  }

  applyRoutedInteraction(result, action) {
    const applied = result.then(async outcome => {
      if (action(outcome)) await this.syncToolViewport();
      return outcome;
    });
    this.interactionChain = applied.catch(
      error => this.reportFailure(error?.message || 'CLIENT_FAILURE'));
    return applied;
  }

  async syncToolViewport() {
    if (!this.$server?.acceptTransientViewport || !this.active || !this.scene) return;
    const reportedGeneration = this.viewportGeneration;
    const settledViewport = {...this.viewport};
    this.viewportGeneration++;
    await this.$server.acceptTransientViewport(PROTOCOL_VERSION, this.componentGeneration,
      this.sceneGeneration, reportedGeneration, this.eventSequence++,
      settledViewport.width, settledViewport.height,
      settledViewport.centerX, settledViewport.centerY, settledViewport.worldUnitsPerPixel);
  }

  sendCancellation(event, reason, required = false) {
    if ((required && this.requiredCancellationPending) ||
        (!required && this.pendingCancellationCount > 0)) {
      return Promise.resolve(
        {accepted: false, suppressDefault: true, captured: false, cursor: 'DEFAULT'});
    }
    this.pendingCancellationCount++;
    if (required) this.requiredCancellationPending = true;
    const completed = this.sendInteraction(event, 'CANCEL', reason, false, required)
      .then(outcome => this.applyToolOutcome(outcome))
      .finally(() => {
        this.pendingCancellationCount--;
        if (required) this.requiredCancellationPending = false;
      });
    this.interactionChain = completed.catch(
      error => this.reportFailure(error?.message || 'CLIENT_FAILURE'));
    return completed;
  }

  beginClientRateCancellation(event) {
    if (this.toolPointerRateCancellationQueued) return;
    this.interactionEpoch++;
    this.toolPointerRateLimited = true;
    this.toolPointerRateCancellationQueued = true;
    const snapshot = this.eventSnapshot(event);
    this.settleTerminatedGesture();
    this.setToolState(this.toolActive, false, 'DEFAULT');
    this.releaseClientPointers();
    this.sendCancellation({...snapshot, button: -1}, 'POINTER_STATE_LOST', true);
  }

  beginClientFailureCancellation(event) {
    if (this.toolPointerRateCancellationQueued) return;
    this.interactionEpoch++;
    this.toolPointerRateCancellationQueued = true;
    const snapshot = this.eventSnapshot(event);
    this.settleTerminatedGesture();
    this.setToolState(this.toolActive, false, 'DEFAULT');
    this.releaseClientPointers();
    this.sendCancellation({...snapshot, button: -1}, 'POINTER_STATE_LOST', true);
  }

  applyToolOutcome(outcome, pointerId) {
    if (outcome?.rateExceeded) {
      const pointer = this.pointers.values().next().value ||
        {x: this.viewport.width / 2, y: this.viewport.height / 2};
      this.beginClientRateCancellation(
        {offsetX: pointer.x, offsetY: pointer.y, button: -1, buttons: 0});
      return;
    }
    if (!outcome || !outcome.accepted) return;
    this.setToolState(this.toolActive, outcome.captured, outcome.cursor);
    if (pointerId !== undefined && !outcome.captured &&
        this.canvas.hasPointerCapture(pointerId) &&
        (outcome.suppressDefault || !this.pointers.has(pointerId))) {
      this.canvas.releasePointerCapture(pointerId);
    }
  }

  releaseClientPointers() {
    for (const pointerId of this.pointers.keys()) {
      if (this.canvas.hasPointerCapture(pointerId)) this.canvas.releasePointerCapture(pointerId);
    }
    this.pointers.clear();
    this.lastPinch = null;
  }

  settleTerminatedGesture() {
    if (this.viewportDirty) this.emitSettled(true);
  }

  emitSettled(force = false) {
    if (!this.scene || !this.$server || !this.active) {
      return;
    }
    if (!force && this.pendingToolEvents >= MAX_PENDING_TOOL_EVENTS) {
      if (!this.settledRateTimer) {
        this.settledRateTimer = setTimeout(() => {
          this.settledRateTimer = 0;
          this.emitSettled();
        }, 100);
      }
      return;
    }
    const now = performance.now();
    const elapsed = Math.max(0, now - this.settledRefillMilliseconds);
    this.settledRefillMilliseconds = now;
    this.settledTokens = Math.min(10, this.settledTokens + elapsed / 100);
    if (!force && this.settledTokens < 1) {
      if (!this.settledRateTimer) {
        this.settledRateTimer = setTimeout(() => {
          this.settledRateTimer = 0;
          this.emitSettled();
        }, Math.ceil((1 - this.settledTokens) * 100));
      }
      return;
    }
    if (!force) this.settledTokens -= 1;
    const reportedGeneration = this.viewportGeneration;
    const componentGeneration = this.componentGeneration;
    const sceneGeneration = this.sceneGeneration;
    const settledViewport = {...this.viewport};
    this.viewportDirty = false;
    this.viewportGeneration += 1;
    const operation = async () => {
      if (!this.$server?.acceptSettledViewport || !this.active || !this.scene) return;
      await this.$server.acceptSettledViewport(PROTOCOL_VERSION, componentGeneration,
        sceneGeneration, reportedGeneration, this.eventSequence++,
        settledViewport.width, settledViewport.height,
        settledViewport.centerX, settledViewport.centerY, settledViewport.worldUnitsPerPixel);
    };
    this.pendingToolEvents++;
    const result = this.interactionChain.then(operation, operation);
    const tracked = result.finally(() => this.pendingToolEvents--);
    this.interactionChain = tracked.catch(error =>
      this.reportFailure(error?.message || 'CLIENT_FAILURE'));
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
