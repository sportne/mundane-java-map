package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.Envelope;
import java.util.Optional;

record ShpHeader(int shapeType, Optional<Envelope> extent) {}
