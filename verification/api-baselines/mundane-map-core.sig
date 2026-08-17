public final class io.github.mundanej.map.core.BuiltInMarkers {
  public static io.github.mundanej.map.api.Envelope viewBox();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public static io.github.mundanej.map.api.VectorPath path(io.github.mundanej.map.api.BuiltInMarker);
    descriptor: (Lio/github/mundanej/map/api/BuiltInMarker;)Lio/github/mundanej/map/api/VectorPath;
  public static io.github.mundanej.map.api.VectorMarkerSymbol filledScreen(io.github.mundanej.map.api.BuiltInMarker, io.github.mundanej.map.api.Rgba, double, double);
    descriptor: (Lio/github/mundanej/map/api/BuiltInMarker;Lio/github/mundanej/map/api/Rgba;DD)Lio/github/mundanej/map/api/VectorMarkerSymbol;
}
public final class io.github.mundanej.map.core.CommonCrsCatalog {
  public static final java.lang.String SOURCE_SHA256 = "f91b37010154184f80b845f101839f71780d248311d112a27ae7fb5d8a38afe9";
    descriptor: Ljava/lang/String;
  public static final io.github.mundanej.map.api.CrsDefinition EPSG_3395;
    descriptor: Lio/github/mundanej/map/api/CrsDefinition;
  public static final io.github.mundanej.map.api.CrsDefinition EPSG_32618;
    descriptor: Lio/github/mundanej/map/api/CrsDefinition;
  public static final io.github.mundanej.map.api.CrsDefinition EPSG_32633;
    descriptor: Lio/github/mundanej/map/api/CrsDefinition;
  public static final io.github.mundanej.map.api.CrsDefinition EPSG_4269;
    descriptor: Lio/github/mundanej/map/api/CrsDefinition;
  public static final io.github.mundanej.map.api.CrsDefinition EPSG_26915;
    descriptor: Lio/github/mundanej/map/api/CrsDefinition;
  public static final io.github.mundanej.map.api.CrsDefinition EPSG_4277;
    descriptor: Lio/github/mundanej/map/api/CrsDefinition;
  public static final io.github.mundanej.map.api.CrsDefinition EPSG_27700;
    descriptor: Lio/github/mundanej/map/api/CrsDefinition;
  public static java.util.List<java.lang.String> identifiers();
    descriptor: ()Ljava/util/List;
  public static io.github.mundanej.map.api.WktCrsDefinition wktDefinition(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/WktCrsDefinition;
}
public final class io.github.mundanej.map.core.CommonTileMatrixSets {
  public static final int MAXIMUM_COMMON_QUAD_LEVEL = 24;
    descriptor: I
  public static final int MAXIMUM_LEGACY_XYZ_LEVEL = 22;
    descriptor: I
  public static io.github.mundanej.map.core.TileMatrixSet webMercatorQuad(int);
    descriptor: (I)Lio/github/mundanej/map/core/TileMatrixSet;
  public static io.github.mundanej.map.core.TileMatrixSet worldCrs84Quad(int);
    descriptor: (I)Lio/github/mundanej/map/core/TileMatrixSet;
  public static io.github.mundanej.map.core.TileMatrixSet legacyXyz();
    descriptor: ()Lio/github/mundanej/map/core/TileMatrixSet;
  public static io.github.mundanej.map.api.Envelope xyzEnvelope(int, long, long);
    descriptor: (IJJ)Lio/github/mundanej/map/api/Envelope;
}
public final class io.github.mundanej.map.core.CrsDefinitions {
  public static final io.github.mundanej.map.api.CrsDefinition EPSG_4326;
    descriptor: Lio/github/mundanej/map/api/CrsDefinition;
  public static final io.github.mundanej.map.api.CrsDefinition EPSG_3857;
    descriptor: Lio/github/mundanej/map/api/CrsDefinition;
}
public final class io.github.mundanej.map.core.CrsOperation {
  public io.github.mundanej.map.api.CrsDefinition sourceCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public io.github.mundanej.map.api.CrsDefinition targetCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public io.github.mundanej.map.api.Envelope sourceDomain();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.Envelope targetDomain();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.Coordinate transform(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.api.Envelope transformEnvelopeStrict(io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.core.QueryEnvelopeTransform transformQueryEnvelope(io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/core/QueryEnvelopeTransform;
}
public final class io.github.mundanej.map.core.CrsRegistry {
  public static io.github.mundanej.map.core.CrsRegistry$Builder builder();
    descriptor: ()Lio/github/mundanej/map/core/CrsRegistry$Builder;
  public static io.github.mundanej.map.core.CrsRegistry$Builder builderWithLevel1();
    descriptor: ()Lio/github/mundanej/map/core/CrsRegistry$Builder;
  public static io.github.mundanej.map.core.CrsRegistry$Builder builderWithCommon();
    descriptor: ()Lio/github/mundanej/map/core/CrsRegistry$Builder;
  public static io.github.mundanej.map.core.CrsRegistry level1();
    descriptor: ()Lio/github/mundanej/map/core/CrsRegistry;
  public static io.github.mundanej.map.core.CrsRegistry common();
    descriptor: ()Lio/github/mundanej/map/core/CrsRegistry;
  public io.github.mundanej.map.api.CrsDefinition resolve(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/CrsDefinition;
  public io.github.mundanej.map.core.CrsOperation operation(io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.CrsDefinition);
    descriptor: (Lio/github/mundanej/map/api/CrsDefinition;Lio/github/mundanej/map/api/CrsDefinition;)Lio/github/mundanej/map/core/CrsOperation;
  public io.github.mundanej.map.core.CrsOperation operationFromMetadata(java.util.Optional<io.github.mundanej.map.api.CrsMetadata>, io.github.mundanej.map.api.CrsDefinition);
    descriptor: (Ljava/util/Optional;Lio/github/mundanej/map/api/CrsDefinition;)Lio/github/mundanej/map/core/CrsOperation;
}
public final class io.github.mundanej.map.core.CrsRegistry$Builder {
  public io.github.mundanej.map.core.CrsRegistry$Builder registerDefinition(io.github.mundanej.map.api.CrsDefinition, java.util.List<java.lang.String>);
    descriptor: (Lio/github/mundanej/map/api/CrsDefinition;Ljava/util/List;)Lio/github/mundanej/map/core/CrsRegistry$Builder;
  public io.github.mundanej.map.core.CrsRegistry$Builder registerProjection(io.github.mundanej.map.api.Projection);
    descriptor: (Lio/github/mundanej/map/api/Projection;)Lio/github/mundanej/map/core/CrsRegistry$Builder;
  public io.github.mundanej.map.core.CrsRegistry build();
    descriptor: ()Lio/github/mundanej/map/core/CrsRegistry;
}
public final class io.github.mundanej.map.core.DistanceStrategies {
  public static final double GREAT_CIRCLE_RADIUS_METRES = 6371008.8d;
    descriptor: D
  public static io.github.mundanej.map.api.DistanceStrategy planarMetres(io.github.mundanej.map.api.CrsDefinition);
    descriptor: (Lio/github/mundanej/map/api/CrsDefinition;)Lio/github/mundanej/map/api/DistanceStrategy;
  public static io.github.mundanej.map.api.DistanceStrategy epsg4326GreatCircle(io.github.mundanej.map.api.CrsDefinition);
    descriptor: (Lio/github/mundanej/map/api/CrsDefinition;)Lio/github/mundanej/map/api/DistanceStrategy;
  public static void requireCoordinateCrs(io.github.mundanej.map.api.DistanceStrategy, io.github.mundanej.map.api.CrsDefinition);
    descriptor: (Lio/github/mundanej/map/api/DistanceStrategy;Lio/github/mundanej/map/api/CrsDefinition;)V
}
public final class io.github.mundanej.map.core.ElevationQueries {
  public static java.util.Optional<io.github.mundanej.map.api.ElevationValue> query(io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.api.ElevationQueryMode);
    descriptor: (Lio/github/mundanej/map/api/ElevationSource;Lio/github/mundanej/map/api/CrsDefinition;Lio/github/mundanej/map/api/Coordinate;Lio/github/mundanej/map/api/ElevationQueryMode;)Ljava/util/Optional;
}
public final class io.github.mundanej.map.core.ElevationRasterization {
  public static java.util.Optional<io.github.mundanej.map.core.ElevationRasterization$Plan> plan(io.github.mundanej.map.api.ElevationSourceMetadata, io.github.mundanej.map.api.Envelope, double, io.github.mundanej.map.api.RasterInterpolation, io.github.mundanej.map.api.RasterRequestLimits);
    descriptor: (Lio/github/mundanej/map/api/ElevationSourceMetadata;Lio/github/mundanej/map/api/Envelope;DLio/github/mundanej/map/api/RasterInterpolation;Lio/github/mundanej/map/api/RasterRequestLimits;)Ljava/util/Optional;
  public static io.github.mundanej.map.api.RasterRead rasterize(io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.core.ElevationRasterization$Plan, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/ElevationSource;Lio/github/mundanej/map/core/ElevationRasterization$Plan;Lio/github/mundanej/map/api/ElevationRasterStyle;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterRead;
}
public final class io.github.mundanej.map.core.ElevationRasterization$Plan {
  public io.github.mundanej.map.api.ElevationSourceMetadata metadata();
    descriptor: ()Lio/github/mundanej/map/api/ElevationSourceMetadata;
  public io.github.mundanej.map.api.RasterRequest request();
    descriptor: ()Lio/github/mundanej/map/api/RasterRequest;
  public io.github.mundanej.map.api.RasterRequestLimits effectiveLimits();
    descriptor: ()Lio/github/mundanej/map/api/RasterRequestLimits;
  public io.github.mundanej.map.api.Envelope imageMapBounds();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.Envelope clipMapBounds();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
}
public final class io.github.mundanej.map.core.FeatureEditSession {
  public static io.github.mundanej.map.core.FeatureEditSession open(io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditLimits);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditSnapshot;Lio/github/mundanej/map/api/FeatureEditLimits;)Lio/github/mundanej/map/core/FeatureEditSession;
  public static io.github.mundanej.map.core.FeatureEditSession open(io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditLimits, io.github.mundanej.map.api.FeatureEditHistoryLimits);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditSnapshot;Lio/github/mundanej/map/api/FeatureEditLimits;Lio/github/mundanej/map/api/FeatureEditHistoryLimits;)Lio/github/mundanej/map/core/FeatureEditSession;
  public static io.github.mundanej.map.core.FeatureEditSession open(io.github.mundanej.map.api.CrsDefinition, java.util.List<io.github.mundanej.map.api.FeatureRecord>);
    descriptor: (Lio/github/mundanej/map/api/CrsDefinition;Ljava/util/List;)Lio/github/mundanej/map/core/FeatureEditSession;
  public io.github.mundanej.map.api.FeatureEditSnapshot snapshot();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditSnapshot;
  public io.github.mundanej.map.api.FeatureEditLimits limits();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditLimits;
  public io.github.mundanej.map.api.FeatureEditHistoryLimits historyLimits();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditHistoryLimits;
  public boolean canUndo();
    descriptor: ()Z
  public boolean canRedo();
    descriptor: ()Z
  public java.util.Optional<java.lang.String> undoDescription();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<java.lang.String> redoDescription();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.FeatureEditResult apply(io.github.mundanej.map.api.FeatureEditTransaction);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditTransaction;)Lio/github/mundanej/map/api/FeatureEditResult;
  public io.github.mundanej.map.api.FeatureEditResult undo(long);
    descriptor: (J)Lio/github/mundanej/map/api/FeatureEditResult;
  public io.github.mundanej.map.api.FeatureEditResult redo(long);
    descriptor: (J)Lio/github/mundanej/map/api/FeatureEditResult;
  public void addFeatureEditListener(io.github.mundanej.map.api.FeatureEditListener);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditListener;)V
  public void removeFeatureEditListener(io.github.mundanej.map.api.FeatureEditListener);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditListener;)V
}
public final class io.github.mundanej.map.core.FeatureIndexLimits extends java.lang.Record {
  public static final io.github.mundanej.map.core.FeatureIndexLimits LEVEL_1;
    descriptor: Lio/github/mundanej/map/core/FeatureIndexLimits;
  public io.github.mundanej.map.core.FeatureIndexLimits(int, long, long, long);
    descriptor: (IJJJ)V
  public static io.github.mundanej.map.core.FeatureIndexLimits defaults();
    descriptor: ()Lio/github/mundanej/map/core/FeatureIndexLimits;
  public io.github.mundanej.map.core.FeatureIndexLimits withMaximumRecords(int);
    descriptor: (I)Lio/github/mundanej/map/core/FeatureIndexLimits;
  public io.github.mundanej.map.core.FeatureIndexLimits withMaximumRetainedBytes(long);
    descriptor: (J)Lio/github/mundanej/map/core/FeatureIndexLimits;
  public io.github.mundanej.map.core.FeatureIndexLimits withMaximumBuildBytes(long);
    descriptor: (J)Lio/github/mundanej/map/core/FeatureIndexLimits;
  public io.github.mundanej.map.core.FeatureIndexLimits withMaximumQueryBytes(long);
    descriptor: (J)Lio/github/mundanej/map/core/FeatureIndexLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumRecords();
    descriptor: ()I
  public long maximumRetainedBytes();
    descriptor: ()J
  public long maximumBuildBytes();
    descriptor: ()J
  public long maximumQueryBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.core.FeaturePortrayalResolver {
  public static io.github.mundanej.map.core.FeaturePortrayalResolver compile(io.github.mundanej.map.api.FeaturePortrayal);
    descriptor: (Lio/github/mundanej/map/api/FeaturePortrayal;)Lio/github/mundanej/map/core/FeaturePortrayalResolver;
  public io.github.mundanej.map.api.FeaturePortrayal portrayal();
    descriptor: ()Lio/github/mundanej/map/api/FeaturePortrayal;
  public java.util.List<java.lang.String> requiredSymbolAttributes();
    descriptor: ()Ljava/util/List;
  public java.util.List<io.github.mundanej.map.api.Symbol> reachableSymbols();
    descriptor: ()Ljava/util/List;
  public java.util.Optional<io.github.mundanej.map.api.PointLabelProfile> pointLabel();
    descriptor: ()Ljava/util/Optional;
  public boolean requiresScaleContext();
    descriptor: ()Z
  public boolean requiresZoomContext();
    descriptor: ()Z
  public java.util.List<java.lang.String> requiredConfigurationAttributes();
    descriptor: ()Ljava/util/List;
  public java.util.List<java.lang.String> requiredPaintAttributes(double);
    descriptor: (D)Ljava/util/List;
  public java.util.Optional<java.lang.String> resolveLabelText(java.lang.String, java.util.Map<java.lang.String, java.lang.Object>, double);
    descriptor: (Ljava/lang/String;Ljava/util/Map;D)Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> resolve(io.github.mundanej.map.api.SymbolRole, java.util.Map<java.lang.String, java.lang.Object>);
    descriptor: (Lio/github/mundanej/map/api/SymbolRole;Ljava/util/Map;)Ljava/util/Optional;
  public io.github.mundanej.map.api.ResolvedFeaturePortrayal resolveAll(java.util.Map<java.lang.String, java.lang.Object>, io.github.mundanej.map.api.PortrayalEvaluationContext);
    descriptor: (Ljava/util/Map;Lio/github/mundanej/map/api/PortrayalEvaluationContext;)Lio/github/mundanej/map/api/ResolvedFeaturePortrayal;
}
public final class io.github.mundanej.map.core.FeatureQueryAccounting {
  public io.github.mundanej.map.core.FeatureQueryAccounting(java.lang.String, io.github.mundanej.map.api.FeatureQueryLimits);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/FeatureQueryLimits;)V
  public void recordExamined();
    descriptor: ()V
  public void recordReturned(io.github.mundanej.map.api.FeatureRecord, int, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/FeatureRecord;ILio/github/mundanej/map/api/CancellationToken;)V
}
public final class io.github.mundanej.map.core.FeatureSnapper {
  public io.github.mundanej.map.core.FeatureSnapper();
    descriptor: ()V
  public io.github.mundanej.map.api.SnapQueryResult find(io.github.mundanej.map.core.SnapQuery);
    descriptor: (Lio/github/mundanej/map/core/SnapQuery;)Lio/github/mundanej/map/api/SnapQueryResult;
}
public final class io.github.mundanej.map.core.GeographicSeamSplitter {
  public static final int MAXIMUM_INSERTED_CROSSINGS = 4096;
    descriptor: I
  public static io.github.mundanej.map.core.GeographicSeamSplitter$Result split(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/core/GeographicSeamSplitter$Result;
}
public final class io.github.mundanej.map.core.GeographicSeamSplitter$Fragment extends java.lang.Record {
  public io.github.mundanej.map.core.GeographicSeamSplitter$Fragment(io.github.mundanej.map.api.Geometry, long, boolean, boolean);
    descriptor: (Lio/github/mundanej/map/api/Geometry;JZZ)V
  public io.github.mundanej.map.core.GeographicSeamSplitter$Fragment(io.github.mundanej.map.api.Geometry, long);
    descriptor: (Lio/github/mundanej/map/api/Geometry;J)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Geometry geometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
  public long worldOffset();
    descriptor: ()J
  public boolean retainsLogicalStart();
    descriptor: ()Z
  public boolean retainsLogicalEnd();
    descriptor: ()Z
}
public final class io.github.mundanej.map.core.GeographicSeamSplitter$GeographicSeamException extends java.lang.RuntimeException {
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.core.GeographicSeamSplitter$Result extends java.lang.Record {
  public io.github.mundanej.map.core.GeographicSeamSplitter$Result(java.util.List<io.github.mundanej.map.core.GeographicSeamSplitter$Fragment>, int);
    descriptor: (Ljava/util/List;I)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.List<io.github.mundanej.map.core.GeographicSeamSplitter$Fragment> fragments();
    descriptor: ()Ljava/util/List;
  public int insertedCrossings();
    descriptor: ()I
}
public final class io.github.mundanej.map.core.GeometryCanonicalRepair {
  public static io.github.mundanej.map.api.Geometry repair(io.github.mundanej.map.api.Geometry, java.util.Collection<io.github.mundanej.map.core.GeometryCanonicalRepair$Defect>);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Ljava/util/Collection;)Lio/github/mundanej/map/api/Geometry;
  public static io.github.mundanej.map.api.Geometry repair(io.github.mundanej.map.api.Geometry, java.util.Collection<io.github.mundanej.map.core.GeometryCanonicalRepair$Defect>, io.github.mundanej.map.core.GeometryTopologyLimits);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Ljava/util/Collection;Lio/github/mundanej/map/core/GeometryTopologyLimits;)Lio/github/mundanej/map/api/Geometry;
}
public final class io.github.mundanej.map.core.GeometryCanonicalRepair$Defect extends java.lang.Enum<io.github.mundanej.map.core.GeometryCanonicalRepair$Defect> {
  public static final io.github.mundanej.map.core.GeometryCanonicalRepair$Defect DUPLICATE_RING_POSITIONS;
    descriptor: Lio/github/mundanej/map/core/GeometryCanonicalRepair$Defect;
  public static final io.github.mundanej.map.core.GeometryCanonicalRepair$Defect RING_ORIENTATION;
    descriptor: Lio/github/mundanej/map/core/GeometryCanonicalRepair$Defect;
  public static io.github.mundanej.map.core.GeometryCanonicalRepair$Defect[] values();
    descriptor: ()[Lio/github/mundanej/map/core/GeometryCanonicalRepair$Defect;
  public static io.github.mundanej.map.core.GeometryCanonicalRepair$Defect valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/core/GeometryCanonicalRepair$Defect;
}
public final class io.github.mundanej.map.core.GeometryDownProjection {
  public static io.github.mundanej.map.api.Geometry toXy(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.OrdinateLossPolicy);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/api/OrdinateLossPolicy;)Lio/github/mundanej/map/api/Geometry;
}
public final class io.github.mundanej.map.core.GeometryEnvelopeClipper {
  public static io.github.mundanej.map.api.Geometry clip(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/api/Geometry;
  public static io.github.mundanej.map.api.Geometry clip(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.core.GeometryTopologyLimits);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/core/GeometryTopologyLimits;)Lio/github/mundanej/map/api/Geometry;
}
public final class io.github.mundanej.map.core.GeometryPredicates {
  public static boolean intersects(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Geometry);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/api/Geometry;)Z
  public static boolean intersects(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Geometry, io.github.mundanej.map.core.GeometryTopologyLimits);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/core/GeometryTopologyLimits;)Z
}
public final class io.github.mundanej.map.core.GeometryTopologyException extends java.lang.RuntimeException {
  public static final java.lang.String COORDINATE_LIMIT = "geometry.topology.coordinateLimit";
    descriptor: Ljava/lang/String;
  public static final java.lang.String COMPARISON_LIMIT = "geometry.topology.comparisonLimit";
    descriptor: Ljava/lang/String;
  public static final java.lang.String OUTPUT_LIMIT = "geometry.topology.outputLimit";
    descriptor: Ljava/lang/String;
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.core.GeometryTopologyLimits extends java.lang.Record {
  public static final io.github.mundanej.map.core.GeometryTopologyLimits DEFAULT;
    descriptor: Lio/github/mundanej/map/core/GeometryTopologyLimits;
  public io.github.mundanej.map.core.GeometryTopologyLimits(int, long, int);
    descriptor: (IJI)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maxCoordinates();
    descriptor: ()I
  public long maxSegmentComparisons();
    descriptor: ()J
  public int maxOutputCoordinates();
    descriptor: ()I
}
public final class io.github.mundanej.map.core.GeometryTransforms {
  public static io.github.mundanej.map.api.Geometry mapXy(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.core.GeometryTransforms$XyTransform);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/core/GeometryTransforms$XyTransform;)Lio/github/mundanej/map/api/Geometry;
  public static io.github.mundanej.map.api.Geometry mapXy(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.core.GeometryTransforms$XyTransform, io.github.mundanej.map.core.GeometryTopologyLimits);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/core/GeometryTransforms$XyTransform;Lio/github/mundanej/map/core/GeometryTopologyLimits;)Lio/github/mundanej/map/api/Geometry;
}
public interface io.github.mundanej.map.core.GeometryTransforms$XyTransform {
  public abstract io.github.mundanej.map.api.Coordinate transform(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
}
public final class io.github.mundanej.map.core.GeometryValidity {
  public static io.github.mundanej.map.core.GeometryValidity$Result check(io.github.mundanej.map.api.Geometry);
    descriptor: (Lio/github/mundanej/map/api/Geometry;)Lio/github/mundanej/map/core/GeometryValidity$Result;
  public static io.github.mundanej.map.core.GeometryValidity$Result check(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.core.GeometryTopologyLimits);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/core/GeometryTopologyLimits;)Lio/github/mundanej/map/core/GeometryValidity$Result;
}
public final class io.github.mundanej.map.core.GeometryValidity$Issue extends java.lang.Record {
  public io.github.mundanej.map.core.GeometryValidity$Issue(io.github.mundanej.map.core.GeometryValidity$Reason, java.lang.String, java.util.Optional<io.github.mundanej.map.api.Coordinate>);
    descriptor: (Lio/github/mundanej/map/core/GeometryValidity$Reason;Ljava/lang/String;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.core.GeometryValidity$Reason reason();
    descriptor: ()Lio/github/mundanej/map/core/GeometryValidity$Reason;
  public java.lang.String geometryPath();
    descriptor: ()Ljava/lang/String;
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> location();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.core.GeometryValidity$Reason extends java.lang.Enum<io.github.mundanej.map.core.GeometryValidity$Reason> {
  public static final io.github.mundanej.map.core.GeometryValidity$Reason TOO_FEW_DISTINCT_POSITIONS;
    descriptor: Lio/github/mundanej/map/core/GeometryValidity$Reason;
  public static final io.github.mundanej.map.core.GeometryValidity$Reason RING_NOT_CLOSED;
    descriptor: Lio/github/mundanej/map/core/GeometryValidity$Reason;
  public static final io.github.mundanej.map.core.GeometryValidity$Reason ZERO_AREA_RING;
    descriptor: Lio/github/mundanej/map/core/GeometryValidity$Reason;
  public static final io.github.mundanej.map.core.GeometryValidity$Reason RING_SELF_INTERSECTION;
    descriptor: Lio/github/mundanej/map/core/GeometryValidity$Reason;
  public static final io.github.mundanej.map.core.GeometryValidity$Reason HOLE_OUTSIDE_SHELL;
    descriptor: Lio/github/mundanej/map/core/GeometryValidity$Reason;
  public static final io.github.mundanej.map.core.GeometryValidity$Reason RING_INTERSECTION;
    descriptor: Lio/github/mundanej/map/core/GeometryValidity$Reason;
  public static final io.github.mundanej.map.core.GeometryValidity$Reason POLYGON_INTERIOR_OVERLAP;
    descriptor: Lio/github/mundanej/map/core/GeometryValidity$Reason;
  public static io.github.mundanej.map.core.GeometryValidity$Reason[] values();
    descriptor: ()[Lio/github/mundanej/map/core/GeometryValidity$Reason;
  public static io.github.mundanej.map.core.GeometryValidity$Reason valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/core/GeometryValidity$Reason;
}
public final class io.github.mundanej.map.core.GeometryValidity$Result extends java.lang.Record {
  public io.github.mundanej.map.core.GeometryValidity$Result(java.util.Optional<io.github.mundanej.map.core.GeometryValidity$Issue>);
    descriptor: (Ljava/util/Optional;)V
  public boolean isValid();
    descriptor: ()Z
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<io.github.mundanej.map.core.GeometryValidity$Issue> issue();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.core.GreedyPointLabelPlacement {
  public static final int MAXIMUM_REQUESTS = 4096;
    descriptor: I
  public static final int MAXIMUM_CANDIDATES = 32768;
    descriptor: I
  public static final long MAXIMUM_COLLISION_COMPARISONS = 10000000l;
    descriptor: J
  public static java.util.List<io.github.mundanej.map.api.PlacedPointLabel> place(io.github.mundanej.map.api.ScreenBox, java.util.List<io.github.mundanej.map.core.PointLabelPlacementRequest>);
    descriptor: (Lio/github/mundanej/map/api/ScreenBox;Ljava/util/List;)Ljava/util/List;
}
public final class io.github.mundanej.map.core.HatchLayouts {
  public static io.github.mundanej.map.core.HatchSegments cover(io.github.mundanej.map.api.HatchPattern, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.Coordinate, double, double, int, java.lang.String);
    descriptor: (Lio/github/mundanej/map/api/HatchPattern;Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/Coordinate;DDILjava/lang/String;)Lio/github/mundanej/map/core/HatchSegments;
  public static long candidateSegmentCount(io.github.mundanej.map.api.HatchPattern, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.Coordinate, double, double, java.lang.String);
    descriptor: (Lio/github/mundanej/map/api/HatchPattern;Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/Coordinate;DDLjava/lang/String;)J
}
public final class io.github.mundanej.map.core.HatchSegments {
  public int segmentCount();
    descriptor: ()I
  public double x1(int);
    descriptor: (I)D
  public double y1(int);
    descriptor: (I)D
  public double x2(int);
    descriptor: (I)D
  public double y2(int);
    descriptor: (I)D
  public double[] toArray();
    descriptor: ()[D
}
public final class io.github.mundanej.map.core.HorizontalInterval extends java.lang.Record {
  public io.github.mundanej.map.core.HorizontalInterval(double, double);
    descriptor: (DD)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double minimumX();
    descriptor: ()D
  public double maximumX();
    descriptor: ()D
}
public final class io.github.mundanej.map.core.HorizontalWrap extends java.lang.Record {
  public static final int VISIBLE_COPIES_HARD_MAXIMUM = 64;
    descriptor: I
  public static final long COPY_INDEX_HARD_MAXIMUM = 1048576l;
    descriptor: J
  public io.github.mundanej.map.core.HorizontalWrap(double, double, int, long);
    descriptor: (DDIJ)V
  public static io.github.mundanej.map.core.HorizontalWrap webMercator();
    descriptor: ()Lio/github/mundanej/map/core/HorizontalWrap;
  public double period();
    descriptor: ()D
  public io.github.mundanej.map.core.WrappedX canonicalize(double);
    descriptor: (D)Lio/github/mundanej/map/core/WrappedX;
  public double translate(double, long);
    descriptor: (DJ)D
  public double nearestEquivalent(double, double);
    descriptor: (DD)D
  public io.github.mundanej.map.core.HorizontalWrapPlan plan(double, double, double);
    descriptor: (DDD)Lio/github/mundanej/map/core/HorizontalWrapPlan;
  public long canonicalTileColumn(long, long);
    descriptor: (JJ)J
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double canonicalMinimumX();
    descriptor: ()D
  public double canonicalMaximumX();
    descriptor: ()D
  public int maximumVisibleCopies();
    descriptor: ()I
  public long maximumAbsoluteCopyIndex();
    descriptor: ()J
}
public final class io.github.mundanej.map.core.HorizontalWrapException extends java.lang.RuntimeException {
  public io.github.mundanej.map.core.HorizontalWrapException(io.github.mundanej.map.core.HorizontalWrapProblem);
    descriptor: (Lio/github/mundanej/map/core/HorizontalWrapProblem;)V
  public io.github.mundanej.map.core.HorizontalWrapProblem problem();
    descriptor: ()Lio/github/mundanej/map/core/HorizontalWrapProblem;
}
public final class io.github.mundanej.map.core.HorizontalWrapPlan extends java.lang.Record {
  public io.github.mundanej.map.core.HorizontalWrapPlan(java.util.List<io.github.mundanej.map.core.HorizontalInterval>, long, long, boolean);
    descriptor: (Ljava/util/List;JJZ)V
  public int visibleCopyCount();
    descriptor: ()I
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.List<io.github.mundanej.map.core.HorizontalInterval> canonicalIntervals();
    descriptor: ()Ljava/util/List;
  public long minimumVisibleCopyIndex();
    descriptor: ()J
  public long maximumVisibleCopyIndex();
    descriptor: ()J
  public boolean fullWorld();
    descriptor: ()Z
}
public final class io.github.mundanej.map.core.HorizontalWrapProblem extends java.lang.Record {
  public io.github.mundanej.map.core.HorizontalWrapProblem(java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/util/Map;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.core.InMemoryFeatureSource implements io.github.mundanej.map.api.FeatureSource {
  public static io.github.mundanej.map.core.InMemoryFeatureSource open(io.github.mundanej.map.api.SourceIdentity, java.util.List<io.github.mundanej.map.api.FeatureRecord>);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/util/List;)Lio/github/mundanej/map/core/InMemoryFeatureSource;
  public static io.github.mundanej.map.core.InMemoryFeatureSource open(io.github.mundanej.map.api.SourceIdentity, java.util.List<io.github.mundanej.map.api.FeatureRecord>, java.util.Optional<io.github.mundanej.map.api.AttributeSchema>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>, io.github.mundanej.map.api.FeatureSourceLimits);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/util/List;Ljava/util/Optional;Ljava/util/Optional;Lio/github/mundanej/map/api/FeatureSourceLimits;)Lio/github/mundanej/map/core/InMemoryFeatureSource;
  public static io.github.mundanej.map.core.InMemoryFeatureSource openIndexed(io.github.mundanej.map.api.SourceIdentity, java.util.List<io.github.mundanej.map.api.FeatureRecord>);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/util/List;)Lio/github/mundanej/map/core/InMemoryFeatureSource;
  public static io.github.mundanej.map.core.InMemoryFeatureSource openIndexed(io.github.mundanej.map.api.SourceIdentity, java.util.List<io.github.mundanej.map.api.FeatureRecord>, java.util.Optional<io.github.mundanej.map.api.AttributeSchema>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>, io.github.mundanej.map.api.FeatureSourceLimits, io.github.mundanej.map.core.FeatureIndexLimits);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/util/List;Ljava/util/Optional;Ljava/util/Optional;Lio/github/mundanej/map/api/FeatureSourceLimits;Lio/github/mundanej/map/core/FeatureIndexLimits;)Lio/github/mundanej/map/core/InMemoryFeatureSource;
  public io.github.mundanej.map.api.FeatureSourceMetadata metadata();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSourceMetadata;
  public io.github.mundanej.map.api.FeatureSourceLimits limits();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSourceLimits;
  public io.github.mundanej.map.api.DiagnosticReport openingDiagnostics();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
  public boolean isClosed();
    descriptor: ()Z
  public io.github.mundanej.map.api.FeatureCursor openCursor(io.github.mundanej.map.api.FeatureQuery, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/FeatureQuery;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/FeatureCursor;
  public void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.core.InMemoryLayer implements io.github.mundanej.map.api.Layer {
  public io.github.mundanej.map.core.InMemoryLayer(java.lang.String, java.lang.String, java.util.List<io.github.mundanej.map.api.Feature>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)V
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public java.util.List<io.github.mundanej.map.api.Feature> features();
    descriptor: ()Ljava/util/List;
  public java.util.Optional<io.github.mundanej.map.api.Envelope> envelope();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.core.LineEndpointBearings extends java.lang.Record {
  public io.github.mundanej.map.core.LineEndpointBearings(java.util.OptionalDouble, java.util.OptionalDouble);
    descriptor: (Ljava/util/OptionalDouble;Ljava/util/OptionalDouble;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.OptionalDouble startBearingDegrees();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.OptionalDouble endBearingDegrees();
    descriptor: ()Ljava/util/OptionalDouble;
}
public final class io.github.mundanej.map.core.LineTangents {
  public static io.github.mundanej.map.core.LineEndpointBearings outwardScreenBearings(io.github.mundanej.map.api.CoordinateSequence, java.lang.String, int);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;Ljava/lang/String;I)Lio/github/mundanej/map/core/LineEndpointBearings;
  public static io.github.mundanej.map.core.LineEndpointBearings outwardScreenBearings(io.github.mundanej.map.api.CoordinateSequence, int, int, java.lang.String, int);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;IILjava/lang/String;I)Lio/github/mundanej/map/core/LineEndpointBearings;
}
public final class io.github.mundanej.map.core.MapScreenBasis {
  public static io.github.mundanej.map.core.MapScreenBasis of(io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/core/MapScreenBasis;
  public io.github.mundanej.map.api.Coordinate xUnitScreenDelta();
    descriptor: ()Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.api.Coordinate yUnitScreenDelta();
    descriptor: ()Lio/github/mundanej/map/api/Coordinate;
  public double determinant();
    descriptor: ()D
  public double uniformScale();
    descriptor: ()D
  public double xAxisScreenBearingDegrees();
    descriptor: ()D
}
public final class io.github.mundanej.map.core.MapToolRouter {
  public io.github.mundanej.map.core.MapToolRouter();
    descriptor: ()V
  public java.util.Optional<io.github.mundanej.map.api.MapTool> activeTool();
    descriptor: ()Ljava/util/Optional;
  public boolean captured();
    descriptor: ()Z
  public io.github.mundanej.map.api.MapCursorIntent currentCursorIntent();
    descriptor: ()Lio/github/mundanej/map/api/MapCursorIntent;
  public io.github.mundanej.map.core.RouteOutcome setActiveTool(io.github.mundanej.map.api.MapTool, io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapTool;Lio/github/mundanej/map/api/MapToolEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/core/RouteOutcome;
  public io.github.mundanej.map.core.RouteOutcome clearActiveTool(io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/core/RouteOutcome;
  public io.github.mundanej.map.core.RouteOutcome route(io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/core/RouteOutcome;
  public io.github.mundanej.map.core.RouteOutcome routeCommand(io.github.mundanej.map.api.MapToolCommandEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolCommandEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/core/RouteOutcome;
  public io.github.mundanej.map.core.RouteOutcome cancelInteraction(io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/core/RouteOutcome;
  public io.github.mundanej.map.core.RouteOutcome resume();
    descriptor: ()Lio/github/mundanej/map/core/RouteOutcome;
}
public final class io.github.mundanej.map.core.MapViewport extends java.lang.Record {
  public io.github.mundanej.map.core.MapViewport(int, int, double, double, double);
    descriptor: (IIDDD)V
  public static io.github.mundanej.map.core.MapViewport initial(int, int);
    descriptor: (II)Lio/github/mundanej/map/core/MapViewport;
  public io.github.mundanej.map.core.MapViewport resized(int, int);
    descriptor: (II)Lio/github/mundanej/map/core/MapViewport;
  public io.github.mundanej.map.api.Coordinate worldToScreen(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.api.Coordinate screenToWorld(double, double);
    descriptor: (DD)Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.core.MapViewport panByPixels(double, double);
    descriptor: (DD)Lio/github/mundanej/map/core/MapViewport;
  public io.github.mundanej.map.core.MapViewport zoomAt(double, double, double);
    descriptor: (DDD)Lio/github/mundanej/map/core/MapViewport;
  public static io.github.mundanej.map.core.MapViewport fit(int, int, io.github.mundanej.map.api.Envelope, double);
    descriptor: (IILio/github/mundanej/map/api/Envelope;D)Lio/github/mundanej/map/core/MapViewport;
  public io.github.mundanej.map.api.Envelope visibleWorldEnvelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int width();
    descriptor: ()I
  public int height();
    descriptor: ()I
  public double centerX();
    descriptor: ()D
  public double centerY();
    descriptor: ()D
  public double worldUnitsPerPixel();
    descriptor: ()D
}
public final class io.github.mundanej.map.core.MarkerTransform {
  public double m00();
    descriptor: ()D
  public double m10();
    descriptor: ()D
  public double m01();
    descriptor: ()D
  public double m11();
    descriptor: ()D
  public double m02();
    descriptor: ()D
  public double m12();
    descriptor: ()D
  public io.github.mundanej.map.api.Envelope nominalScreenBounds();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.Coordinate screenToLocal(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
}
public final class io.github.mundanej.map.core.PackedElevationGrid implements io.github.mundanej.map.api.ElevationSource {
  public static io.github.mundanej.map.core.PackedElevationGrid copyOf(io.github.mundanej.map.api.ElevationSourceMetadata, double[], java.util.BitSet);
    descriptor: (Lio/github/mundanej/map/api/ElevationSourceMetadata;[DLjava/util/BitSet;)Lio/github/mundanej/map/core/PackedElevationGrid;
  public static io.github.mundanej.map.core.PackedElevationGrid copyOf(io.github.mundanej.map.api.ElevationSourceMetadata, double[], java.util.BitSet, io.github.mundanej.map.api.ElevationSourceLimits, io.github.mundanej.map.api.DiagnosticReport);
    descriptor: (Lio/github/mundanej/map/api/ElevationSourceMetadata;[DLjava/util/BitSet;Lio/github/mundanej/map/api/ElevationSourceLimits;Lio/github/mundanej/map/api/DiagnosticReport;)Lio/github/mundanej/map/core/PackedElevationGrid;
  public io.github.mundanej.map.api.ElevationSourceMetadata metadata();
    descriptor: ()Lio/github/mundanej/map/api/ElevationSourceMetadata;
  public io.github.mundanej.map.api.ElevationSourceLimits limits();
    descriptor: ()Lio/github/mundanej/map/api/ElevationSourceLimits;
  public io.github.mundanej.map.api.DiagnosticReport openingDiagnostics();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
  public java.util.OptionalDouble sample(int, int);
    descriptor: (II)Ljava/util/OptionalDouble;
  public boolean isClosed();
    descriptor: ()Z
  public void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.core.PointLabelLayouts {
  public static io.github.mundanej.map.api.PlacedPointLabel place(java.lang.String, java.lang.String, java.lang.String, io.github.mundanej.map.api.LabelTextStyle, io.github.mundanej.map.api.ScreenBox, io.github.mundanej.map.api.ScreenBox, double, io.github.mundanej.map.api.PointLabelProfile, io.github.mundanej.map.api.PointLabelPosition, int, int, int);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/LabelTextStyle;Lio/github/mundanej/map/api/ScreenBox;Lio/github/mundanej/map/api/ScreenBox;DLio/github/mundanej/map/api/PointLabelProfile;Lio/github/mundanej/map/api/PointLabelPosition;III)Lio/github/mundanej/map/api/PlacedPointLabel;
}
public final class io.github.mundanej.map.core.PointLabelPlacementRequest extends java.lang.Record {
  public io.github.mundanej.map.core.PointLabelPlacementRequest(java.lang.String, java.lang.String, java.lang.String, io.github.mundanej.map.api.LabelTextStyle, io.github.mundanej.map.api.ScreenBox, io.github.mundanej.map.api.ScreenBox, double, io.github.mundanej.map.api.PointLabelProfile, int, int, int);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/LabelTextStyle;Lio/github/mundanej/map/api/ScreenBox;Lio/github/mundanej/map/api/ScreenBox;DLio/github/mundanej/map/api/PointLabelProfile;III)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String layerId();
    descriptor: ()Ljava/lang/String;
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
  public java.lang.String text();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.LabelTextStyle style();
    descriptor: ()Lio/github/mundanej/map/api/LabelTextStyle;
  public io.github.mundanej.map.api.ScreenBox markerBounds();
    descriptor: ()Lio/github/mundanej/map/api/ScreenBox;
  public io.github.mundanej.map.api.ScreenBox relativeVisualBounds();
    descriptor: ()Lio/github/mundanej/map/api/ScreenBox;
  public double advance();
    descriptor: ()D
  public io.github.mundanej.map.api.PointLabelProfile profile();
    descriptor: ()Lio/github/mundanej/map/api/PointLabelProfile;
  public int layerIndex();
    descriptor: ()I
  public int featureIndex();
    descriptor: ()I
  public int ordinaryPaintOrdinal();
    descriptor: ()I
}
public final class io.github.mundanej.map.core.PortrayalExpressions {
  public static final java.lang.String INPUT_MISSING = "PORTRAYAL_EXPRESSION_INPUT_MISSING";
    descriptor: Ljava/lang/String;
  public static final java.lang.String TYPE_MISMATCH = "PORTRAYAL_EXPRESSION_TYPE_MISMATCH";
    descriptor: Ljava/lang/String;
  public static final java.lang.String NON_FINITE = "PORTRAYAL_EXPRESSION_NON_FINITE";
    descriptor: Ljava/lang/String;
  public static io.github.mundanej.map.api.PortrayalEvaluationResult evaluate(io.github.mundanej.map.api.PortrayalExpression, io.github.mundanej.map.api.FeatureRecord, io.github.mundanej.map.api.PortrayalEvaluationContext);
    descriptor: (Lio/github/mundanej/map/api/PortrayalExpression;Lio/github/mundanej/map/api/FeatureRecord;Lio/github/mundanej/map/api/PortrayalEvaluationContext;)Lio/github/mundanej/map/api/PortrayalEvaluationResult;
}
public final class io.github.mundanej.map.core.QueryEnvelopeStatus extends java.lang.Enum<io.github.mundanej.map.core.QueryEnvelopeStatus> {
  public static final io.github.mundanej.map.core.QueryEnvelopeStatus COMPLETE;
    descriptor: Lio/github/mundanej/map/core/QueryEnvelopeStatus;
  public static final io.github.mundanej.map.core.QueryEnvelopeStatus CLIPPED;
    descriptor: Lio/github/mundanej/map/core/QueryEnvelopeStatus;
  public static final io.github.mundanej.map.core.QueryEnvelopeStatus OUTSIDE;
    descriptor: Lio/github/mundanej/map/core/QueryEnvelopeStatus;
  public static io.github.mundanej.map.core.QueryEnvelopeStatus[] values();
    descriptor: ()[Lio/github/mundanej/map/core/QueryEnvelopeStatus;
  public static io.github.mundanej.map.core.QueryEnvelopeStatus valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/core/QueryEnvelopeStatus;
}
public final class io.github.mundanej.map.core.QueryEnvelopeTransform extends java.lang.Record {
  public io.github.mundanej.map.core.QueryEnvelopeTransform(io.github.mundanej.map.core.QueryEnvelopeStatus, java.util.Optional<io.github.mundanej.map.api.Envelope>);
    descriptor: (Lio/github/mundanej/map/core/QueryEnvelopeStatus;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.core.QueryEnvelopeStatus status();
    descriptor: ()Lio/github/mundanej/map/core/QueryEnvelopeStatus;
  public java.util.Optional<io.github.mundanej.map.api.Envelope> transformedEnvelope();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.core.RasterGridWindows {
  public static java.util.Optional<io.github.mundanej.map.api.RasterWindow> visibleWindow(io.github.mundanej.map.api.RasterSourceMetadata, io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/RasterSourceMetadata;Lio/github/mundanej/map/api/Envelope;)Ljava/util/Optional;
  public static io.github.mundanej.map.api.Envelope mapBounds(io.github.mundanej.map.api.RasterSourceMetadata, io.github.mundanej.map.api.RasterWindow);
    descriptor: (Lio/github/mundanej/map/api/RasterSourceMetadata;Lio/github/mundanej/map/api/RasterWindow;)Lio/github/mundanej/map/api/Envelope;
  public static io.github.mundanej.map.core.RasterGridWindows$OutputSize outputSize(io.github.mundanej.map.api.RasterSourceMetadata, io.github.mundanej.map.api.RasterWindow, io.github.mundanej.map.core.MapViewport);
    descriptor: (Lio/github/mundanej/map/api/RasterSourceMetadata;Lio/github/mundanej/map/api/RasterWindow;Lio/github/mundanej/map/core/MapViewport;)Lio/github/mundanej/map/core/RasterGridWindows$OutputSize;
}
public final class io.github.mundanej.map.core.RasterGridWindows$OutputSize extends java.lang.Record {
  public io.github.mundanej.map.core.RasterGridWindows$OutputSize(int, int);
    descriptor: (II)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int width();
    descriptor: ()I
  public int height();
    descriptor: ()I
}
public final class io.github.mundanej.map.core.RasterRequestAccounting {
  public io.github.mundanej.map.core.RasterRequestAccounting(java.lang.String, io.github.mundanej.map.api.RasterRequestLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/RasterRequestLimits;Lio/github/mundanej/map/api/CancellationToken;)V
  public void validateWindow(io.github.mundanej.map.api.RasterSourceMetadata, io.github.mundanej.map.api.RasterWindow);
    descriptor: (Lio/github/mundanej/map/api/RasterSourceMetadata;Lio/github/mundanej/map/api/RasterWindow;)V
  public void validateWindow(int, int, io.github.mundanej.map.api.RasterWindow);
    descriptor: (IILio/github/mundanej/map/api/RasterWindow;)V
  public void chargeSourcePixels(long);
    descriptor: (J)V
  public long validateOutput(int, int);
    descriptor: (II)J
  public void chargeIntermediateBytes(long);
    descriptor: (J)V
  public void chargePublishedBytes(long);
    descriptor: (J)V
  public void checkpoint();
    descriptor: ()V
}
public final class io.github.mundanej.map.core.RasterResampling {
  public static void validatePlan(int, int, int, int, io.github.mundanej.map.api.RasterInterpolation);
    descriptor: (IIIILio/github/mundanej/map/api/RasterInterpolation;)V
  public static int nearestIndex(int, int, int);
    descriptor: (III)I
  public static io.github.mundanej.map.core.RasterResampling$AxisWeights bilinearAxis(int, int, int);
    descriptor: (III)Lio/github/mundanej/map/core/RasterResampling$AxisWeights;
  public static int bilinearRgba(int, int, int, int, io.github.mundanej.map.core.RasterResampling$AxisWeights, io.github.mundanej.map.core.RasterResampling$AxisWeights);
    descriptor: (IIIILio/github/mundanej/map/core/RasterResampling$AxisWeights;Lio/github/mundanej/map/core/RasterResampling$AxisWeights;)I
}
public final class io.github.mundanej.map.core.RasterResampling$AxisWeights extends java.lang.Record {
  public io.github.mundanej.map.core.RasterResampling$AxisWeights(int, int, long, long, long);
    descriptor: (IIJJJ)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int lowerIndex();
    descriptor: ()I
  public int upperIndex();
    descriptor: ()I
  public long lowerWeight();
    descriptor: ()J
  public long upperWeight();
    descriptor: ()J
  public long denominator();
    descriptor: ()J
}
public final class io.github.mundanej.map.core.RouteOutcome extends java.lang.Record {
  public io.github.mundanej.map.core.RouteOutcome(boolean, boolean, io.github.mundanej.map.api.MapCursorIntent);
    descriptor: (ZZLio/github/mundanej/map/api/MapCursorIntent;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public boolean suppressDefault();
    descriptor: ()Z
  public boolean captured();
    descriptor: ()Z
  public io.github.mundanej.map.api.MapCursorIntent cursorIntent();
    descriptor: ()Lio/github/mundanej/map/api/MapCursorIntent;
}
public final class io.github.mundanej.map.core.ScreenGeometryHits {
  public static boolean pointWithin(double, double, double, double, double);
    descriptor: (DDDDD)Z
  public static boolean polylineWithin(io.github.mundanej.map.api.CoordinateSequence, boolean, double, double, double);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;ZDDD)Z
  public static boolean filledPolygonWithin(io.github.mundanej.map.api.CoordinateSequence, java.util.List<io.github.mundanej.map.api.CoordinateSequence>, double, double, double);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;Ljava/util/List;DDD)Z
  public static boolean convexQuadWithin(double[], double, double, double);
    descriptor: ([DDDD)Z
}
public final class io.github.mundanej.map.core.ScreenGeometryOptimization {
  public io.github.mundanej.map.api.Geometry authoritativeGeometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
  public java.util.Optional<io.github.mundanej.map.api.Geometry> renderingGeometry();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome outcome();
    descriptor: ()Lio/github/mundanej/map/core/ScreenGeometryOptimizationOutcome;
  public int sourceComponentCount();
    descriptor: ()I
  public int renderComponentCount();
    descriptor: ()I
  public int renderComponentOffset(int);
    descriptor: (I)I
  public int[] renderComponentOffsets();
    descriptor: ()[I
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.core.ScreenGeometryOptimizationLimits extends java.lang.Record {
  public static final io.github.mundanej.map.core.ScreenGeometryOptimizationLimits LEVEL_1;
    descriptor: Lio/github/mundanej/map/core/ScreenGeometryOptimizationLimits;
  public io.github.mundanej.map.core.ScreenGeometryOptimizationLimits(int, long, long);
    descriptor: (IJJ)V
  public static io.github.mundanej.map.core.ScreenGeometryOptimizationLimits defaults();
    descriptor: ()Lio/github/mundanej/map/core/ScreenGeometryOptimizationLimits;
  public io.github.mundanej.map.core.ScreenGeometryOptimizationLimits withMaximumOutputCoordinates(int);
    descriptor: (I)Lio/github/mundanej/map/core/ScreenGeometryOptimizationLimits;
  public io.github.mundanej.map.core.ScreenGeometryOptimizationLimits withMaximumBuildBytes(long);
    descriptor: (J)Lio/github/mundanej/map/core/ScreenGeometryOptimizationLimits;
  public io.github.mundanej.map.core.ScreenGeometryOptimizationLimits withMaximumTopologyComparisons(long);
    descriptor: (J)Lio/github/mundanej/map/core/ScreenGeometryOptimizationLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumOutputCoordinates();
    descriptor: ()I
  public long maximumBuildBytes();
    descriptor: ()J
  public long maximumTopologyComparisons();
    descriptor: ()J
}
public final class io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome extends java.lang.Enum<io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome> {
  public static final io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome UNCHANGED;
    descriptor: Lio/github/mundanej/map/core/ScreenGeometryOptimizationOutcome;
  public static final io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome OPTIMIZED;
    descriptor: Lio/github/mundanej/map/core/ScreenGeometryOptimizationOutcome;
  public static final io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome PATH_CULLED;
    descriptor: Lio/github/mundanej/map/core/ScreenGeometryOptimizationOutcome;
  public static final io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome FALLBACK;
    descriptor: Lio/github/mundanej/map/core/ScreenGeometryOptimizationOutcome;
  public static io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome[] values();
    descriptor: ()[Lio/github/mundanej/map/core/ScreenGeometryOptimizationOutcome;
  public static io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/core/ScreenGeometryOptimizationOutcome;
}
public final class io.github.mundanej.map.core.ScreenGeometryOptimizer {
  public static io.github.mundanej.map.core.ScreenGeometryOptimization optimize(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Envelope, double, io.github.mundanej.map.core.ScreenGeometryOptimizationLimits);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/api/Envelope;DLio/github/mundanej/map/core/ScreenGeometryOptimizationLimits;)Lio/github/mundanej/map/core/ScreenGeometryOptimization;
}
public final class io.github.mundanej.map.core.SnapQuery {
  public io.github.mundanej.map.core.SnapQuery(double, double, double, io.github.mundanej.map.core.CrsOperation, io.github.mundanej.map.core.CrsOperation, io.github.mundanej.map.core.MapViewport, io.github.mundanej.map.api.SnapReferenceSet, java.util.Set<io.github.mundanej.map.api.FeatureSelection>, io.github.mundanej.map.api.SnapLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (DDDLio/github/mundanej/map/core/CrsOperation;Lio/github/mundanej/map/core/CrsOperation;Lio/github/mundanej/map/core/MapViewport;Lio/github/mundanej/map/api/SnapReferenceSet;Ljava/util/Set;Lio/github/mundanej/map/api/SnapLimits;Lio/github/mundanej/map/api/CancellationToken;)V
  public io.github.mundanej.map.core.SnapQuery(double, double, double, io.github.mundanej.map.core.CrsOperation, io.github.mundanej.map.core.CrsOperation, io.github.mundanej.map.core.MapViewport, java.util.Optional<io.github.mundanej.map.core.HorizontalWrap>, java.util.Set<java.lang.String>, io.github.mundanej.map.api.SnapReferenceSet, java.util.Set<io.github.mundanej.map.api.FeatureSelection>, io.github.mundanej.map.api.SnapLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (DDDLio/github/mundanej/map/core/CrsOperation;Lio/github/mundanej/map/core/CrsOperation;Lio/github/mundanej/map/core/MapViewport;Ljava/util/Optional;Ljava/util/Set;Lio/github/mundanej/map/api/SnapReferenceSet;Ljava/util/Set;Lio/github/mundanej/map/api/SnapLimits;Lio/github/mundanej/map/api/CancellationToken;)V
  public double screenX();
    descriptor: ()D
  public double screenY();
    descriptor: ()D
  public double tolerancePixels();
    descriptor: ()D
  public io.github.mundanej.map.core.CrsOperation coordinatesToDisplay();
    descriptor: ()Lio/github/mundanej/map/core/CrsOperation;
  public io.github.mundanej.map.core.CrsOperation displayToCoordinates();
    descriptor: ()Lio/github/mundanej/map/core/CrsOperation;
  public io.github.mundanej.map.core.MapViewport viewport();
    descriptor: ()Lio/github/mundanej/map/core/MapViewport;
  public java.util.Optional<io.github.mundanej.map.core.HorizontalWrap> horizontalWrap();
    descriptor: ()Ljava/util/Optional;
  public boolean repeatsLayer(java.lang.String);
    descriptor: (Ljava/lang/String;)Z
  public java.util.Set<java.lang.String> repeatingLayerIds();
    descriptor: ()Ljava/util/Set;
  public io.github.mundanej.map.api.SnapReferenceSet references();
    descriptor: ()Lio/github/mundanej/map/api/SnapReferenceSet;
  public java.util.Set<io.github.mundanej.map.api.FeatureSelection> exclusions();
    descriptor: ()Ljava/util/Set;
  public io.github.mundanej.map.api.SnapLimits limits();
    descriptor: ()Lio/github/mundanej/map/api/SnapLimits;
  public io.github.mundanej.map.api.CancellationToken cancellation();
    descriptor: ()Lio/github/mundanej/map/api/CancellationToken;
}
public final class io.github.mundanej.map.core.SymbolTransforms {
  public static io.github.mundanej.map.core.MarkerTransform marker(io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.core.MapScreenBasis);
    descriptor: (Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/api/Coordinate;Lio/github/mundanej/map/core/MapScreenBasis;)Lio/github/mundanej/map/core/MarkerTransform;
  public static io.github.mundanej.map.core.MarkerTransform markerAtScreenBearing(io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.core.MapScreenBasis, double);
    descriptor: (Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/api/Coordinate;Lio/github/mundanej/map/core/MapScreenBasis;D)Lio/github/mundanej/map/core/MarkerTransform;
  public static double screenLength(io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.core.MapScreenBasis);
    descriptor: (Lio/github/mundanej/map/api/SymbolLength;Lio/github/mundanej/map/core/MapScreenBasis;)D
}
public final class io.github.mundanej.map.core.SyntheticRasterSource implements io.github.mundanej.map.api.RasterSource {
  public static io.github.mundanej.map.core.SyntheticRasterSource open(io.github.mundanej.map.api.SourceIdentity, int, int, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>, io.github.mundanej.map.api.RasterSourceLimits);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;IILjava/util/Optional;Ljava/util/Optional;Lio/github/mundanej/map/api/RasterSourceLimits;)Lio/github/mundanej/map/core/SyntheticRasterSource;
  public static io.github.mundanej.map.core.SyntheticRasterSource open(io.github.mundanej.map.api.SourceIdentity, int, int, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.CrsMetadata);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;IILio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/CrsMetadata;)Lio/github/mundanej/map/core/SyntheticRasterSource;
  public io.github.mundanej.map.api.RasterSourceMetadata metadata();
    descriptor: ()Lio/github/mundanej/map/api/RasterSourceMetadata;
  public io.github.mundanej.map.api.RasterSourceLimits limits();
    descriptor: ()Lio/github/mundanej/map/api/RasterSourceLimits;
  public io.github.mundanej.map.api.DiagnosticReport openingDiagnostics();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
  public io.github.mundanej.map.api.RasterRead read(io.github.mundanej.map.api.RasterRequest, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/RasterRequest;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterRead;
  public boolean isClosed();
    descriptor: ()Z
  public void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.core.TileCoverage extends java.lang.Record {
  public io.github.mundanej.map.core.TileCoverage(io.github.mundanej.map.core.TileCoverageStatus, java.util.List<io.github.mundanej.map.api.Envelope>, java.util.List<io.github.mundanej.map.core.TileMatrixIndex>);
    descriptor: (Lio/github/mundanej/map/core/TileCoverageStatus;Ljava/util/List;Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.core.TileCoverageStatus status();
    descriptor: ()Lio/github/mundanej/map/core/TileCoverageStatus;
  public java.util.List<io.github.mundanej.map.api.Envelope> intersections();
    descriptor: ()Ljava/util/List;
  public java.util.List<io.github.mundanej.map.core.TileMatrixIndex> tiles();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.core.TileCoverageLimits extends java.lang.Record {
  public static final int HARD_MAXIMUM_TILES = 1000000;
    descriptor: I
  public io.github.mundanej.map.core.TileCoverageLimits(int);
    descriptor: (I)V
  public static io.github.mundanej.map.core.TileCoverageLimits defaults();
    descriptor: ()Lio/github/mundanej/map/core/TileCoverageLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumTiles();
    descriptor: ()I
}
public final class io.github.mundanej.map.core.TileCoverageStatus extends java.lang.Enum<io.github.mundanej.map.core.TileCoverageStatus> {
  public static final io.github.mundanej.map.core.TileCoverageStatus OUTSIDE;
    descriptor: Lio/github/mundanej/map/core/TileCoverageStatus;
  public static final io.github.mundanej.map.core.TileCoverageStatus COMPLETE;
    descriptor: Lio/github/mundanej/map/core/TileCoverageStatus;
  public static final io.github.mundanej.map.core.TileCoverageStatus CLIPPED;
    descriptor: Lio/github/mundanej/map/core/TileCoverageStatus;
  public static io.github.mundanej.map.core.TileCoverageStatus[] values();
    descriptor: ()[Lio/github/mundanej/map/core/TileCoverageStatus;
  public static io.github.mundanej.map.core.TileCoverageStatus valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/core/TileCoverageStatus;
}
public final class io.github.mundanej.map.core.TileMatrix extends java.lang.Record {
  public static final int MAXIMUM_TILE_SIZE = 65536;
    descriptor: I
  public static final long MAXIMUM_MATRIX_DIMENSION = 4294967296l;
    descriptor: J
  public static final int MAXIMUM_VARIABLE_WIDTHS = 1024;
    descriptor: I
  public io.github.mundanej.map.core.TileMatrix(java.lang.String, double, double, io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.core.TileMatrixCorner, int, int, long, long, java.util.List<io.github.mundanej.map.core.VariableMatrixWidth>);
    descriptor: (Ljava/lang/String;DDLio/github/mundanej/map/api/Coordinate;Lio/github/mundanej/map/core/TileMatrixCorner;IIJJLjava/util/List;)V
  public int coalesce(long);
    descriptor: (J)I
  public long columnCount(long);
    descriptor: (J)J
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String identifier();
    descriptor: ()Ljava/lang/String;
  public double scaleDenominator();
    descriptor: ()D
  public double cellSize();
    descriptor: ()D
  public io.github.mundanej.map.api.Coordinate pointOfOrigin();
    descriptor: ()Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.core.TileMatrixCorner cornerOfOrigin();
    descriptor: ()Lio/github/mundanej/map/core/TileMatrixCorner;
  public int tileWidth();
    descriptor: ()I
  public int tileHeight();
    descriptor: ()I
  public long matrixWidth();
    descriptor: ()J
  public long matrixHeight();
    descriptor: ()J
  public java.util.List<io.github.mundanej.map.core.VariableMatrixWidth> variableMatrixWidths();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.core.TileMatrixAlgorithms {
  public static io.github.mundanej.map.api.Envelope matrixEnvelope(io.github.mundanej.map.core.TileMatrixSet, java.lang.String);
    descriptor: (Lio/github/mundanej/map/core/TileMatrixSet;Ljava/lang/String;)Lio/github/mundanej/map/api/Envelope;
  public static io.github.mundanej.map.core.TileMatrixIndex tileAt(io.github.mundanej.map.core.TileMatrixSet, java.lang.String, io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/core/TileMatrixSet;Ljava/lang/String;Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/core/TileMatrixIndex;
  public static io.github.mundanej.map.api.Envelope tileEnvelope(io.github.mundanej.map.core.TileMatrixSet, io.github.mundanej.map.core.TileMatrixIndex);
    descriptor: (Lio/github/mundanej/map/core/TileMatrixSet;Lio/github/mundanej/map/core/TileMatrixIndex;)Lio/github/mundanej/map/api/Envelope;
  public static io.github.mundanej.map.core.TileCoverage coverage(io.github.mundanej.map.core.TileMatrixSet, java.lang.String, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.core.TileCoverageLimits);
    descriptor: (Lio/github/mundanej/map/core/TileMatrixSet;Ljava/lang/String;Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/core/TileCoverageLimits;)Lio/github/mundanej/map/core/TileCoverage;
  public static io.github.mundanej.map.core.TileCoverage coverageAcrossHorizontalSeam(io.github.mundanej.map.core.TileMatrixSet, java.lang.String, double, double, double, double, io.github.mundanej.map.core.TileCoverageLimits);
    descriptor: (Lio/github/mundanej/map/core/TileMatrixSet;Ljava/lang/String;DDDDLio/github/mundanej/map/core/TileCoverageLimits;)Lio/github/mundanej/map/core/TileCoverage;
}
public final class io.github.mundanej.map.core.TileMatrixAxisOrder extends java.lang.Enum<io.github.mundanej.map.core.TileMatrixAxisOrder> {
  public static final io.github.mundanej.map.core.TileMatrixAxisOrder XY;
    descriptor: Lio/github/mundanej/map/core/TileMatrixAxisOrder;
  public static final io.github.mundanej.map.core.TileMatrixAxisOrder YX;
    descriptor: Lio/github/mundanej/map/core/TileMatrixAxisOrder;
  public static io.github.mundanej.map.core.TileMatrixAxisOrder[] values();
    descriptor: ()[Lio/github/mundanej/map/core/TileMatrixAxisOrder;
  public static io.github.mundanej.map.core.TileMatrixAxisOrder valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/core/TileMatrixAxisOrder;
}
public final class io.github.mundanej.map.core.TileMatrixCorner extends java.lang.Enum<io.github.mundanej.map.core.TileMatrixCorner> {
  public static final io.github.mundanej.map.core.TileMatrixCorner TOP_LEFT;
    descriptor: Lio/github/mundanej/map/core/TileMatrixCorner;
  public static final io.github.mundanej.map.core.TileMatrixCorner BOTTOM_LEFT;
    descriptor: Lio/github/mundanej/map/core/TileMatrixCorner;
  public static io.github.mundanej.map.core.TileMatrixCorner[] values();
    descriptor: ()[Lio/github/mundanej/map/core/TileMatrixCorner;
  public static io.github.mundanej.map.core.TileMatrixCorner valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/core/TileMatrixCorner;
}
public final class io.github.mundanej.map.core.TileMatrixException extends java.lang.RuntimeException {
  public io.github.mundanej.map.core.TileMatrixException(io.github.mundanej.map.core.TileMatrixProblem);
    descriptor: (Lio/github/mundanej/map/core/TileMatrixProblem;)V
  public io.github.mundanej.map.core.TileMatrixProblem problem();
    descriptor: ()Lio/github/mundanej/map/core/TileMatrixProblem;
}
public final class io.github.mundanej.map.core.TileMatrixIndex extends java.lang.Record {
  public io.github.mundanej.map.core.TileMatrixIndex(java.lang.String, long, long);
    descriptor: (Ljava/lang/String;JJ)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String matrixIdentifier();
    descriptor: ()Ljava/lang/String;
  public long row();
    descriptor: ()J
  public long column();
    descriptor: ()J
}
public final class io.github.mundanej.map.core.TileMatrixProblem extends java.lang.Record {
  public io.github.mundanej.map.core.TileMatrixProblem(java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/util/Map;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.core.TileMatrixSelectionPolicy extends java.lang.Enum<io.github.mundanej.map.core.TileMatrixSelectionPolicy> {
  public static final io.github.mundanej.map.core.TileMatrixSelectionPolicy NEAREST;
    descriptor: Lio/github/mundanej/map/core/TileMatrixSelectionPolicy;
  public static final io.github.mundanej.map.core.TileMatrixSelectionPolicy COARSER_OR_EQUAL;
    descriptor: Lio/github/mundanej/map/core/TileMatrixSelectionPolicy;
  public static final io.github.mundanej.map.core.TileMatrixSelectionPolicy FINER_OR_EQUAL;
    descriptor: Lio/github/mundanej/map/core/TileMatrixSelectionPolicy;
  public static io.github.mundanej.map.core.TileMatrixSelectionPolicy[] values();
    descriptor: ()[Lio/github/mundanej/map/core/TileMatrixSelectionPolicy;
  public static io.github.mundanej.map.core.TileMatrixSelectionPolicy valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/core/TileMatrixSelectionPolicy;
}
public final class io.github.mundanej.map.core.TileMatrixSet extends java.lang.Record {
  public static final int MAXIMUM_MATRICES = 64;
    descriptor: I
  public io.github.mundanej.map.core.TileMatrixSet(java.lang.String, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.core.TileMatrixAxisOrder, io.github.mundanej.map.api.Envelope, java.util.List<io.github.mundanej.map.core.TileMatrix>);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/CrsDefinition;Lio/github/mundanej/map/core/TileMatrixAxisOrder;Lio/github/mundanej/map/api/Envelope;Ljava/util/List;)V
  public io.github.mundanej.map.core.TileMatrix matrix(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/core/TileMatrix;
  public io.github.mundanej.map.core.TileMatrix select(double, io.github.mundanej.map.core.TileMatrixSelectionPolicy);
    descriptor: (DLio/github/mundanej/map/core/TileMatrixSelectionPolicy;)Lio/github/mundanej/map/core/TileMatrix;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String identifier();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.CrsDefinition crs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public io.github.mundanej.map.core.TileMatrixAxisOrder orderedAxes();
    descriptor: ()Lio/github/mundanej/map/core/TileMatrixAxisOrder;
  public io.github.mundanej.map.api.Envelope boundingBox();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public java.util.List<io.github.mundanej.map.core.TileMatrix> tileMatrices();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.core.VariableMatrixWidth extends java.lang.Record {
  public static final int MAXIMUM_COALESCE = 1048576;
    descriptor: I
  public io.github.mundanej.map.core.VariableMatrixWidth(int, long, long);
    descriptor: (IJJ)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int coalesce();
    descriptor: ()I
  public long minimumTileRow();
    descriptor: ()J
  public long maximumTileRow();
    descriptor: ()J
}
public final class io.github.mundanej.map.core.WebMercatorProjection implements io.github.mundanej.map.api.Projection {
  public static final double MAX_LATITUDE = 85.0511287798066d;
    descriptor: D
  public static final double WORLD_LIMIT = 2.0037508342789244E7d;
    descriptor: D
  public io.github.mundanej.map.core.WebMercatorProjection();
    descriptor: ()V
  public io.github.mundanej.map.api.CrsDefinition sourceCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public io.github.mundanej.map.api.CrsDefinition targetCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public io.github.mundanej.map.api.Envelope sourceDomain();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.Envelope targetDomain();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.Coordinate project(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.api.Coordinate unproject(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.api.Envelope projectEnvelope(io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.Envelope unprojectEnvelope(io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/api/Envelope;
}
public final class io.github.mundanej.map.core.Wkt2 {
  public static final int MAXIMUM_CHARACTERS = 16384;
    descriptor: I
  public static final int MAXIMUM_DEPTH = 32;
    descriptor: I
  public static final int MAXIMUM_VALUES = 4096;
    descriptor: I
  public static io.github.mundanej.map.api.WktCrsDefinition parse(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/WktCrsDefinition;
  public static java.lang.String write(io.github.mundanej.map.api.WktCrsDefinition);
    descriptor: (Lio/github/mundanej/map/api/WktCrsDefinition;)Ljava/lang/String;
}
public final class io.github.mundanej.map.core.WktCoordinateOperation {
  public static final java.lang.String MERCATOR_VARIANT_A = "Mercator (variant A)";
    descriptor: Ljava/lang/String;
  public static final java.lang.String TRANSVERSE_MERCATOR = "Transverse Mercator";
    descriptor: Ljava/lang/String;
  public static final int MAXIMUM_BATCH_COORDINATES = 1000000;
    descriptor: I
  public static io.github.mundanej.map.core.WktCoordinateOperation between(io.github.mundanej.map.api.WktCrsDefinition, io.github.mundanej.map.api.WktCrsDefinition);
    descriptor: (Lio/github/mundanej/map/api/WktCrsDefinition;Lio/github/mundanej/map/api/WktCrsDefinition;)Lio/github/mundanej/map/core/WktCoordinateOperation;
  public io.github.mundanej.map.api.WktCrsDefinition source();
    descriptor: ()Lio/github/mundanej/map/api/WktCrsDefinition;
  public io.github.mundanej.map.api.WktCrsDefinition target();
    descriptor: ()Lio/github/mundanej/map/api/WktCrsDefinition;
  public io.github.mundanej.map.api.Coordinate transform(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public java.util.List<io.github.mundanej.map.api.Coordinate> transformAll(java.util.List<io.github.mundanej.map.api.Coordinate>);
    descriptor: (Ljava/util/List;)Ljava/util/List;
}
public final class io.github.mundanej.map.core.WrappedX extends java.lang.Record {
  public io.github.mundanej.map.core.WrappedX(double, long);
    descriptor: (DJ)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double canonicalX();
    descriptor: ()D
  public long copyIndex();
    descriptor: ()J
}
SHAPE io.github.mundanej.map.core.BuiltInMarkers sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:filledScreen[io.github.mundanej.map.api.BuiltInMarker, io.github.mundanej.map.api.Rgba, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:path[io.github.mundanej.map.api.BuiltInMarker] throws=[] annotations=[] parameterAnnotations=[[]], method:viewBox[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.CommonCrsCatalog sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:EPSG_26915[], field:EPSG_27700[], field:EPSG_32618[], field:EPSG_32633[], field:EPSG_3395[], field:EPSG_4269[], field:EPSG_4277[], field:SOURCE_SHA256[], method:identifiers[] throws=[] annotations=[] parameterAnnotations=[], method:wktDefinition[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.CommonTileMatrixSets sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:MAXIMUM_COMMON_QUAD_LEVEL[], field:MAXIMUM_LEGACY_XYZ_LEVEL[], method:legacyXyz[] throws=[] annotations=[] parameterAnnotations=[], method:webMercatorQuad[int] throws=[] annotations=[] parameterAnnotations=[[]], method:worldCrs84Quad[int] throws=[] annotations=[] parameterAnnotations=[[]], method:xyzEnvelope[int, long, long] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.core.CrsDefinitions sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:EPSG_3857[], field:EPSG_4326[]]
SHAPE io.github.mundanej.map.core.CrsOperation sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:sourceCrs[] throws=[] annotations=[] parameterAnnotations=[], method:sourceDomain[] throws=[] annotations=[] parameterAnnotations=[], method:targetCrs[] throws=[] annotations=[] parameterAnnotations=[], method:targetDomain[] throws=[] annotations=[] parameterAnnotations=[], method:transformEnvelopeStrict[io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[]], method:transformQueryEnvelope[io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[]], method:transform[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.CrsRegistry sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:builderWithCommon[] throws=[] annotations=[] parameterAnnotations=[], method:builderWithLevel1[] throws=[] annotations=[] parameterAnnotations=[], method:builder[] throws=[] annotations=[] parameterAnnotations=[], method:common[] throws=[] annotations=[] parameterAnnotations=[], method:level1[] throws=[] annotations=[] parameterAnnotations=[], method:operationFromMetadata[java.util.Optional<io.github.mundanej.map.api.CrsMetadata>, io.github.mundanej.map.api.CrsDefinition] throws=[] annotations=[] parameterAnnotations=[[], []], method:operation[io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.CrsDefinition] throws=[] annotations=[] parameterAnnotations=[[], []], method:resolve[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.CrsRegistry$Builder sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:build[] throws=[] annotations=[] parameterAnnotations=[], method:registerDefinition[io.github.mundanej.map.api.CrsDefinition, java.util.List<java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], []], method:registerProjection[io.github.mundanej.map.api.Projection] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.DistanceStrategies sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:GREAT_CIRCLE_RADIUS_METRES[], method:epsg4326GreatCircle[io.github.mundanej.map.api.CrsDefinition] throws=[] annotations=[] parameterAnnotations=[[]], method:planarMetres[io.github.mundanej.map.api.CrsDefinition] throws=[] annotations=[] parameterAnnotations=[[]], method:requireCoordinateCrs[io.github.mundanej.map.api.DistanceStrategy, io.github.mundanej.map.api.CrsDefinition] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.ElevationQueries sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:query[io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.api.ElevationQueryMode] throws=[] annotations=[] parameterAnnotations=[[], [], [], []]]
SHAPE io.github.mundanej.map.core.ElevationRasterization sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:plan[io.github.mundanej.map.api.ElevationSourceMetadata, io.github.mundanej.map.api.Envelope, double, io.github.mundanej.map.api.RasterInterpolation, io.github.mundanej.map.api.RasterRequestLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:rasterize[io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.core.ElevationRasterization$Plan, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []]]
SHAPE io.github.mundanej.map.core.ElevationRasterization$Plan sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:clipMapBounds[] throws=[] annotations=[] parameterAnnotations=[], method:effectiveLimits[] throws=[] annotations=[] parameterAnnotations=[], method:imageMapBounds[] throws=[] annotations=[] parameterAnnotations=[], method:metadata[] throws=[] annotations=[] parameterAnnotations=[], method:request[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.FeatureEditSession sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:addFeatureEditListener[io.github.mundanej.map.api.FeatureEditListener] throws=[] annotations=[] parameterAnnotations=[[]], method:apply[io.github.mundanej.map.api.FeatureEditTransaction] throws=[] annotations=[] parameterAnnotations=[[]], method:canRedo[] throws=[] annotations=[] parameterAnnotations=[], method:canUndo[] throws=[] annotations=[] parameterAnnotations=[], method:historyLimits[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:open[io.github.mundanej.map.api.CrsDefinition, java.util.List<io.github.mundanej.map.api.FeatureRecord>] throws=[] annotations=[] parameterAnnotations=[[], []], method:open[io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditLimits, io.github.mundanej.map.api.FeatureEditHistoryLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:open[io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:redoDescription[] throws=[] annotations=[] parameterAnnotations=[], method:redo[long] throws=[] annotations=[] parameterAnnotations=[[]], method:removeFeatureEditListener[io.github.mundanej.map.api.FeatureEditListener] throws=[] annotations=[] parameterAnnotations=[[]], method:snapshot[] throws=[] annotations=[] parameterAnnotations=[], method:undoDescription[] throws=[] annotations=[] parameterAnnotations=[], method:undo[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.FeatureIndexLimits sealed=false permits=[] record=[maximumRecords:int[], maximumRetainedBytes:long[], maximumBuildBytes:long[], maximumQueryBytes:long[]] enum=[] annotations=[] members=[constructor:[int, long, long, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], field:LEVEL_1[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumBuildBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumQueryBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumRecords[] throws=[] annotations=[] parameterAnnotations=[], method:maximumRetainedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumBuildBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumQueryBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumRecords[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumRetainedBytes[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.FeaturePortrayalResolver sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:compile[io.github.mundanej.map.api.FeaturePortrayal] throws=[] annotations=[] parameterAnnotations=[[]], method:pointLabel[] throws=[] annotations=[] parameterAnnotations=[], method:portrayal[] throws=[] annotations=[] parameterAnnotations=[], method:reachableSymbols[] throws=[] annotations=[] parameterAnnotations=[], method:requiredConfigurationAttributes[] throws=[] annotations=[] parameterAnnotations=[], method:requiredPaintAttributes[double] throws=[] annotations=[] parameterAnnotations=[[]], method:requiredSymbolAttributes[] throws=[] annotations=[] parameterAnnotations=[], method:requiresScaleContext[] throws=[] annotations=[] parameterAnnotations=[], method:requiresZoomContext[] throws=[] annotations=[] parameterAnnotations=[], method:resolveAll[java.util.Map<java.lang.String, java.lang.Object>, io.github.mundanej.map.api.PortrayalEvaluationContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:resolveLabelText[java.lang.String, java.util.Map<java.lang.String, java.lang.Object>, double] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:resolve[io.github.mundanej.map.api.SymbolRole, java.util.Map<java.lang.String, java.lang.Object>] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.FeatureQueryAccounting sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.FeatureQueryLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:recordExamined[] throws=[] annotations=[] parameterAnnotations=[], method:recordReturned[io.github.mundanej.map.api.FeatureRecord, int, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.core.FeatureSnapper sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[] throws=[] annotations=[] parameterAnnotations=[], method:find[io.github.mundanej.map.core.SnapQuery] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.GeographicSeamSplitter sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:MAXIMUM_INSERTED_CROSSINGS[], method:split[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.GeographicSeamSplitter$Fragment sealed=false permits=[] record=[geometry:io.github.mundanej.map.api.Geometry[], worldOffset:long[], retainsLogicalStart:boolean[], retainsLogicalEnd:boolean[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Geometry, long, boolean, boolean] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], constructor:[io.github.mundanej.map.api.Geometry, long] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:geometry[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:retainsLogicalEnd[] throws=[] annotations=[] parameterAnnotations=[], method:retainsLogicalStart[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:worldOffset[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.GeographicSeamSplitter$GeographicSeamException sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.GeographicSeamSplitter$Result sealed=false permits=[] record=[fragments:java.util.List<io.github.mundanej.map.core.GeographicSeamSplitter$Fragment>[], insertedCrossings:int[]] enum=[] annotations=[] members=[constructor:[java.util.List<io.github.mundanej.map.core.GeographicSeamSplitter$Fragment>, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fragments[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:insertedCrossings[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.GeometryCanonicalRepair sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:repair[io.github.mundanej.map.api.Geometry, java.util.Collection<io.github.mundanej.map.core.GeometryCanonicalRepair$Defect>, io.github.mundanej.map.core.GeometryTopologyLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:repair[io.github.mundanej.map.api.Geometry, java.util.Collection<io.github.mundanej.map.core.GeometryCanonicalRepair$Defect>] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.GeometryCanonicalRepair$Defect sealed=false permits=[] record=[] enum=[DUPLICATE_RING_POSITIONS, RING_ORIENTATION] annotations=[] members=[field:DUPLICATE_RING_POSITIONS[], field:RING_ORIENTATION[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.GeometryDownProjection sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:toXy[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.OrdinateLossPolicy] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.GeometryEnvelopeClipper sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:clip[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.core.GeometryTopologyLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:clip[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.GeometryPredicates sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:intersects[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Geometry, io.github.mundanej.map.core.GeometryTopologyLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:intersects[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Geometry] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.GeometryTopologyException sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:COMPARISON_LIMIT[], field:COORDINATE_LIMIT[], field:OUTPUT_LIMIT[], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.GeometryTopologyLimits sealed=false permits=[] record=[maxCoordinates:int[], maxSegmentComparisons:long[], maxOutputCoordinates:int[]] enum=[] annotations=[] members=[constructor:[int, long, int] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:DEFAULT[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maxCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maxOutputCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maxSegmentComparisons[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.GeometryTransforms sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:mapXy[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.core.GeometryTransforms$XyTransform, io.github.mundanej.map.core.GeometryTopologyLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:mapXy[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.core.GeometryTransforms$XyTransform] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.GeometryTransforms$XyTransform sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:transform[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.GeometryValidity sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:check[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.core.GeometryTopologyLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:check[io.github.mundanej.map.api.Geometry] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.GeometryValidity$Issue sealed=false permits=[] record=[reason:io.github.mundanej.map.core.GeometryValidity$Reason[], geometryPath:java.lang.String[], location:java.util.Optional<io.github.mundanej.map.api.Coordinate>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.core.GeometryValidity$Reason, java.lang.String, java.util.Optional<io.github.mundanej.map.api.Coordinate>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:geometryPath[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:location[] throws=[] annotations=[] parameterAnnotations=[], method:reason[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.GeometryValidity$Reason sealed=false permits=[] record=[] enum=[TOO_FEW_DISTINCT_POSITIONS, RING_NOT_CLOSED, ZERO_AREA_RING, RING_SELF_INTERSECTION, HOLE_OUTSIDE_SHELL, RING_INTERSECTION, POLYGON_INTERIOR_OVERLAP] annotations=[] members=[field:HOLE_OUTSIDE_SHELL[], field:POLYGON_INTERIOR_OVERLAP[], field:RING_INTERSECTION[], field:RING_NOT_CLOSED[], field:RING_SELF_INTERSECTION[], field:TOO_FEW_DISTINCT_POSITIONS[], field:ZERO_AREA_RING[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.GeometryValidity$Result sealed=false permits=[] record=[issue:java.util.Optional<io.github.mundanej.map.core.GeometryValidity$Issue>[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<io.github.mundanej.map.core.GeometryValidity$Issue>] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:isValid[] throws=[] annotations=[] parameterAnnotations=[], method:issue[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.GreedyPointLabelPlacement sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:MAXIMUM_CANDIDATES[], field:MAXIMUM_COLLISION_COMPARISONS[], field:MAXIMUM_REQUESTS[], method:place[io.github.mundanej.map.api.ScreenBox, java.util.List<io.github.mundanej.map.core.PointLabelPlacementRequest>] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.HatchLayouts sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:candidateSegmentCount[io.github.mundanej.map.api.HatchPattern, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.Coordinate, double, double, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:cover[io.github.mundanej.map.api.HatchPattern, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.Coordinate, double, double, int, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], []]]
SHAPE io.github.mundanej.map.core.HatchSegments sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:segmentCount[] throws=[] annotations=[] parameterAnnotations=[], method:toArray[] throws=[] annotations=[] parameterAnnotations=[], method:x1[int] throws=[] annotations=[] parameterAnnotations=[[]], method:x2[int] throws=[] annotations=[] parameterAnnotations=[[]], method:y1[int] throws=[] annotations=[] parameterAnnotations=[[]], method:y2[int] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.HorizontalInterval sealed=false permits=[] record=[minimumX:double[], maximumX:double[]] enum=[] annotations=[] members=[constructor:[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumX[] throws=[] annotations=[] parameterAnnotations=[], method:minimumX[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.HorizontalWrap sealed=false permits=[] record=[canonicalMinimumX:double[], canonicalMaximumX:double[], maximumVisibleCopies:int[], maximumAbsoluteCopyIndex:long[]] enum=[] annotations=[] members=[constructor:[double, double, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], field:COPY_INDEX_HARD_MAXIMUM[], field:VISIBLE_COPIES_HARD_MAXIMUM[], method:canonicalMaximumX[] throws=[] annotations=[] parameterAnnotations=[], method:canonicalMinimumX[] throws=[] annotations=[] parameterAnnotations=[], method:canonicalTileColumn[long, long] throws=[] annotations=[] parameterAnnotations=[[], []], method:canonicalize[double] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAbsoluteCopyIndex[] throws=[] annotations=[] parameterAnnotations=[], method:maximumVisibleCopies[] throws=[] annotations=[] parameterAnnotations=[], method:nearestEquivalent[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:period[] throws=[] annotations=[] parameterAnnotations=[], method:plan[double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:translate[double, long] throws=[] annotations=[] parameterAnnotations=[[], []], method:webMercator[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.HorizontalWrapException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.core.HorizontalWrapProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.HorizontalWrapPlan sealed=false permits=[] record=[canonicalIntervals:java.util.List<io.github.mundanej.map.core.HorizontalInterval>[], minimumVisibleCopyIndex:long[], maximumVisibleCopyIndex:long[], fullWorld:boolean[]] enum=[] annotations=[] members=[constructor:[java.util.List<io.github.mundanej.map.core.HorizontalInterval>, long, long, boolean] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:canonicalIntervals[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fullWorld[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumVisibleCopyIndex[] throws=[] annotations=[] parameterAnnotations=[], method:minimumVisibleCopyIndex[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:visibleCopyCount[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.HorizontalWrapProblem sealed=false permits=[] record=[code:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.InMemoryFeatureSource sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:close[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:metadata[] throws=[] annotations=[] parameterAnnotations=[], method:openCursor[io.github.mundanej.map.api.FeatureQuery, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []], method:openIndexed[io.github.mundanej.map.api.SourceIdentity, java.util.List<io.github.mundanej.map.api.FeatureRecord>, java.util.Optional<io.github.mundanej.map.api.AttributeSchema>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>, io.github.mundanej.map.api.FeatureSourceLimits, io.github.mundanej.map.core.FeatureIndexLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:openIndexed[io.github.mundanej.map.api.SourceIdentity, java.util.List<io.github.mundanej.map.api.FeatureRecord>] throws=[] annotations=[] parameterAnnotations=[[], []], method:open[io.github.mundanej.map.api.SourceIdentity, java.util.List<io.github.mundanej.map.api.FeatureRecord>, java.util.Optional<io.github.mundanej.map.api.AttributeSchema>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>, io.github.mundanej.map.api.FeatureSourceLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:open[io.github.mundanej.map.api.SourceIdentity, java.util.List<io.github.mundanej.map.api.FeatureRecord>] throws=[] annotations=[] parameterAnnotations=[[], []], method:openingDiagnostics[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.InMemoryLayer sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.List<io.github.mundanej.map.api.Feature>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:features[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.LineEndpointBearings sealed=false permits=[] record=[startBearingDegrees:java.util.OptionalDouble[], endBearingDegrees:java.util.OptionalDouble[]] enum=[] annotations=[] members=[constructor:[java.util.OptionalDouble, java.util.OptionalDouble] throws=[] annotations=[] parameterAnnotations=[[], []], method:endBearingDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:startBearingDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.LineTangents sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:outwardScreenBearings[io.github.mundanej.map.api.CoordinateSequence, int, int, java.lang.String, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:outwardScreenBearings[io.github.mundanej.map.api.CoordinateSequence, java.lang.String, int] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.core.MapScreenBasis sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:determinant[] throws=[] annotations=[] parameterAnnotations=[], method:of[io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[], []], method:uniformScale[] throws=[] annotations=[] parameterAnnotations=[], method:xAxisScreenBearingDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:xUnitScreenDelta[] throws=[] annotations=[] parameterAnnotations=[], method:yUnitScreenDelta[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.MapToolRouter sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[] throws=[] annotations=[] parameterAnnotations=[], method:activeTool[] throws=[] annotations=[] parameterAnnotations=[], method:cancelInteraction[io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:captured[] throws=[] annotations=[] parameterAnnotations=[], method:clearActiveTool[io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:currentCursorIntent[] throws=[] annotations=[] parameterAnnotations=[], method:resume[] throws=[] annotations=[] parameterAnnotations=[], method:routeCommand[io.github.mundanej.map.api.MapToolCommandEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:route[io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:setActiveTool[io.github.mundanej.map.api.MapTool, io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.core.MapViewport sealed=false permits=[] record=[width:int[], height:int[], centerX:double[], centerY:double[], worldUnitsPerPixel:double[]] enum=[] annotations=[] members=[constructor:[int, int, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:centerX[] throws=[] annotations=[] parameterAnnotations=[], method:centerY[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fit[int, int, io.github.mundanej.map.api.Envelope, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:height[] throws=[] annotations=[] parameterAnnotations=[], method:initial[int, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:panByPixels[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:resized[int, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:screenToWorld[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:visibleWorldEnvelope[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[], method:worldToScreen[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:worldUnitsPerPixel[] throws=[] annotations=[] parameterAnnotations=[], method:zoomAt[double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.core.MarkerTransform sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:m00[] throws=[] annotations=[] parameterAnnotations=[], method:m01[] throws=[] annotations=[] parameterAnnotations=[], method:m02[] throws=[] annotations=[] parameterAnnotations=[], method:m10[] throws=[] annotations=[] parameterAnnotations=[], method:m11[] throws=[] annotations=[] parameterAnnotations=[], method:m12[] throws=[] annotations=[] parameterAnnotations=[], method:nominalScreenBounds[] throws=[] annotations=[] parameterAnnotations=[], method:screenToLocal[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.PackedElevationGrid sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:close[] throws=[] annotations=[] parameterAnnotations=[], method:copyOf[io.github.mundanej.map.api.ElevationSourceMetadata, double[], java.util.BitSet, io.github.mundanej.map.api.ElevationSourceLimits, io.github.mundanej.map.api.DiagnosticReport] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:copyOf[io.github.mundanej.map.api.ElevationSourceMetadata, double[], java.util.BitSet] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:metadata[] throws=[] annotations=[] parameterAnnotations=[], method:openingDiagnostics[] throws=[] annotations=[] parameterAnnotations=[], method:sample[int, int] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.PointLabelLayouts sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:place[java.lang.String, java.lang.String, java.lang.String, io.github.mundanej.map.api.LabelTextStyle, io.github.mundanej.map.api.ScreenBox, io.github.mundanej.map.api.ScreenBox, double, io.github.mundanej.map.api.PointLabelProfile, io.github.mundanej.map.api.PointLabelPosition, int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], []]]
SHAPE io.github.mundanej.map.core.PointLabelPlacementRequest sealed=false permits=[] record=[layerId:java.lang.String[], featureId:java.lang.String[], text:java.lang.String[], style:io.github.mundanej.map.api.LabelTextStyle[], markerBounds:io.github.mundanej.map.api.ScreenBox[], relativeVisualBounds:io.github.mundanej.map.api.ScreenBox[], advance:double[], profile:io.github.mundanej.map.api.PointLabelProfile[], layerIndex:int[], featureIndex:int[], ordinaryPaintOrdinal:int[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.lang.String, io.github.mundanej.map.api.LabelTextStyle, io.github.mundanej.map.api.ScreenBox, io.github.mundanej.map.api.ScreenBox, double, io.github.mundanej.map.api.PointLabelProfile, int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], []], method:advance[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:featureIndex[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layerId[] throws=[] annotations=[] parameterAnnotations=[], method:layerIndex[] throws=[] annotations=[] parameterAnnotations=[], method:markerBounds[] throws=[] annotations=[] parameterAnnotations=[], method:ordinaryPaintOrdinal[] throws=[] annotations=[] parameterAnnotations=[], method:profile[] throws=[] annotations=[] parameterAnnotations=[], method:relativeVisualBounds[] throws=[] annotations=[] parameterAnnotations=[], method:style[] throws=[] annotations=[] parameterAnnotations=[], method:text[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.PortrayalExpressions sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:INPUT_MISSING[], field:NON_FINITE[], field:TYPE_MISMATCH[], method:evaluate[io.github.mundanej.map.api.PortrayalExpression, io.github.mundanej.map.api.FeatureRecord, io.github.mundanej.map.api.PortrayalEvaluationContext] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.core.QueryEnvelopeStatus sealed=false permits=[] record=[] enum=[COMPLETE, CLIPPED, OUTSIDE] annotations=[] members=[field:CLIPPED[], field:COMPLETE[], field:OUTSIDE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.QueryEnvelopeTransform sealed=false permits=[] record=[status:io.github.mundanej.map.core.QueryEnvelopeStatus[], transformedEnvelope:java.util.Optional<io.github.mundanej.map.api.Envelope>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.core.QueryEnvelopeStatus, java.util.Optional<io.github.mundanej.map.api.Envelope>] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:status[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:transformedEnvelope[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.RasterGridWindows sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:mapBounds[io.github.mundanej.map.api.RasterSourceMetadata, io.github.mundanej.map.api.RasterWindow] throws=[] annotations=[] parameterAnnotations=[[], []], method:outputSize[io.github.mundanej.map.api.RasterSourceMetadata, io.github.mundanej.map.api.RasterWindow, io.github.mundanej.map.core.MapViewport] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:visibleWindow[io.github.mundanej.map.api.RasterSourceMetadata, io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.RasterGridWindows$OutputSize sealed=false permits=[] record=[width:int[], height:int[]] enum=[] annotations=[] members=[constructor:[int, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:height[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.RasterRequestAccounting sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.RasterRequestLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:chargeIntermediateBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:chargePublishedBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:chargeSourcePixels[long] throws=[] annotations=[] parameterAnnotations=[[]], method:checkpoint[] throws=[] annotations=[] parameterAnnotations=[], method:validateOutput[int, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:validateWindow[int, int, io.github.mundanej.map.api.RasterWindow] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:validateWindow[io.github.mundanej.map.api.RasterSourceMetadata, io.github.mundanej.map.api.RasterWindow] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.RasterResampling sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:bilinearAxis[int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:bilinearRgba[int, int, int, int, io.github.mundanej.map.core.RasterResampling$AxisWeights, io.github.mundanej.map.core.RasterResampling$AxisWeights] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:nearestIndex[int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:validatePlan[int, int, int, int, io.github.mundanej.map.api.RasterInterpolation] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []]]
SHAPE io.github.mundanej.map.core.RasterResampling$AxisWeights sealed=false permits=[] record=[lowerIndex:int[], upperIndex:int[], lowerWeight:long[], upperWeight:long[], denominator:long[]] enum=[] annotations=[] members=[constructor:[int, int, long, long, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:denominator[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:lowerIndex[] throws=[] annotations=[] parameterAnnotations=[], method:lowerWeight[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:upperIndex[] throws=[] annotations=[] parameterAnnotations=[], method:upperWeight[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.RouteOutcome sealed=false permits=[] record=[suppressDefault:boolean[], captured:boolean[], cursorIntent:io.github.mundanej.map.api.MapCursorIntent[]] enum=[] annotations=[] members=[constructor:[boolean, boolean, io.github.mundanej.map.api.MapCursorIntent] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:captured[] throws=[] annotations=[] parameterAnnotations=[], method:cursorIntent[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:suppressDefault[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.ScreenGeometryHits sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:convexQuadWithin[double[], double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:filledPolygonWithin[io.github.mundanej.map.api.CoordinateSequence, java.util.List<io.github.mundanej.map.api.CoordinateSequence>, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:pointWithin[double, double, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:polylineWithin[io.github.mundanej.map.api.CoordinateSequence, boolean, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []]]
SHAPE io.github.mundanej.map.core.ScreenGeometryOptimization sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:authoritativeGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:outcome[] throws=[] annotations=[] parameterAnnotations=[], method:renderComponentCount[] throws=[] annotations=[] parameterAnnotations=[], method:renderComponentOffset[int] throws=[] annotations=[] parameterAnnotations=[[]], method:renderComponentOffsets[] throws=[] annotations=[] parameterAnnotations=[], method:renderingGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:sourceComponentCount[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.ScreenGeometryOptimizationLimits sealed=false permits=[] record=[maximumOutputCoordinates:int[], maximumBuildBytes:long[], maximumTopologyComparisons:long[]] enum=[] annotations=[] members=[constructor:[int, long, long] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:LEVEL_1[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumBuildBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOutputCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTopologyComparisons[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumBuildBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumOutputCoordinates[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumTopologyComparisons[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome sealed=false permits=[] record=[] enum=[UNCHANGED, OPTIMIZED, PATH_CULLED, FALLBACK] annotations=[] members=[field:FALLBACK[], field:OPTIMIZED[], field:PATH_CULLED[], field:UNCHANGED[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.ScreenGeometryOptimizer sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:optimize[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Envelope, double, io.github.mundanej.map.core.ScreenGeometryOptimizationLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], []]]
SHAPE io.github.mundanej.map.core.SnapQuery sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[double, double, double, io.github.mundanej.map.core.CrsOperation, io.github.mundanej.map.core.CrsOperation, io.github.mundanej.map.core.MapViewport, io.github.mundanej.map.api.SnapReferenceSet, java.util.Set<io.github.mundanej.map.api.FeatureSelection>, io.github.mundanej.map.api.SnapLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], []], constructor:[double, double, double, io.github.mundanej.map.core.CrsOperation, io.github.mundanej.map.core.CrsOperation, io.github.mundanej.map.core.MapViewport, java.util.Optional<io.github.mundanej.map.core.HorizontalWrap>, java.util.Set<java.lang.String>, io.github.mundanej.map.api.SnapReferenceSet, java.util.Set<io.github.mundanej.map.api.FeatureSelection>, io.github.mundanej.map.api.SnapLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], []], method:cancellation[] throws=[] annotations=[] parameterAnnotations=[], method:coordinatesToDisplay[] throws=[] annotations=[] parameterAnnotations=[], method:displayToCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:exclusions[] throws=[] annotations=[] parameterAnnotations=[], method:horizontalWrap[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:references[] throws=[] annotations=[] parameterAnnotations=[], method:repeatingLayerIds[] throws=[] annotations=[] parameterAnnotations=[], method:repeatsLayer[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:screenX[] throws=[] annotations=[] parameterAnnotations=[], method:screenY[] throws=[] annotations=[] parameterAnnotations=[], method:tolerancePixels[] throws=[] annotations=[] parameterAnnotations=[], method:viewport[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.SymbolTransforms sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:markerAtScreenBearing[io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.core.MapScreenBasis, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:marker[io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.core.MapScreenBasis] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:screenLength[io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.core.MapScreenBasis] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.SyntheticRasterSource sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:close[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:metadata[] throws=[] annotations=[] parameterAnnotations=[], method:open[io.github.mundanej.map.api.SourceIdentity, int, int, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.CrsMetadata] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:open[io.github.mundanej.map.api.SourceIdentity, int, int, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>, io.github.mundanej.map.api.RasterSourceLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:openingDiagnostics[] throws=[] annotations=[] parameterAnnotations=[], method:read[io.github.mundanej.map.api.RasterRequest, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.TileCoverage sealed=false permits=[] record=[status:io.github.mundanej.map.core.TileCoverageStatus[], intersections:java.util.List<io.github.mundanej.map.api.Envelope>[], tiles:java.util.List<io.github.mundanej.map.core.TileMatrixIndex>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.core.TileCoverageStatus, java.util.List<io.github.mundanej.map.api.Envelope>, java.util.List<io.github.mundanej.map.core.TileMatrixIndex>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:intersections[] throws=[] annotations=[] parameterAnnotations=[], method:status[] throws=[] annotations=[] parameterAnnotations=[], method:tiles[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileCoverageLimits sealed=false permits=[] record=[maximumTiles:int[]] enum=[] annotations=[] members=[constructor:[int] throws=[] annotations=[] parameterAnnotations=[[]], field:HARD_MAXIMUM_TILES[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTiles[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileCoverageStatus sealed=false permits=[] record=[] enum=[OUTSIDE, COMPLETE, CLIPPED] annotations=[] members=[field:CLIPPED[], field:COMPLETE[], field:OUTSIDE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileMatrix sealed=false permits=[] record=[identifier:java.lang.String[], scaleDenominator:double[], cellSize:double[], pointOfOrigin:io.github.mundanej.map.api.Coordinate[], cornerOfOrigin:io.github.mundanej.map.core.TileMatrixCorner[], tileWidth:int[], tileHeight:int[], matrixWidth:long[], matrixHeight:long[], variableMatrixWidths:java.util.List<io.github.mundanej.map.core.VariableMatrixWidth>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, double, double, io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.core.TileMatrixCorner, int, int, long, long, java.util.List<io.github.mundanej.map.core.VariableMatrixWidth>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], []], field:MAXIMUM_MATRIX_DIMENSION[], field:MAXIMUM_TILE_SIZE[], field:MAXIMUM_VARIABLE_WIDTHS[], method:cellSize[] throws=[] annotations=[] parameterAnnotations=[], method:coalesce[long] throws=[] annotations=[] parameterAnnotations=[[]], method:columnCount[long] throws=[] annotations=[] parameterAnnotations=[[]], method:cornerOfOrigin[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:identifier[] throws=[] annotations=[] parameterAnnotations=[], method:matrixHeight[] throws=[] annotations=[] parameterAnnotations=[], method:matrixWidth[] throws=[] annotations=[] parameterAnnotations=[], method:pointOfOrigin[] throws=[] annotations=[] parameterAnnotations=[], method:scaleDenominator[] throws=[] annotations=[] parameterAnnotations=[], method:tileHeight[] throws=[] annotations=[] parameterAnnotations=[], method:tileWidth[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:variableMatrixWidths[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileMatrixAlgorithms sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:coverageAcrossHorizontalSeam[io.github.mundanej.map.core.TileMatrixSet, java.lang.String, double, double, double, double, io.github.mundanej.map.core.TileCoverageLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], []], method:coverage[io.github.mundanej.map.core.TileMatrixSet, java.lang.String, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.core.TileCoverageLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:matrixEnvelope[io.github.mundanej.map.core.TileMatrixSet, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], []], method:tileAt[io.github.mundanej.map.core.TileMatrixSet, java.lang.String, io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:tileEnvelope[io.github.mundanej.map.core.TileMatrixSet, io.github.mundanej.map.core.TileMatrixIndex] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.core.TileMatrixAxisOrder sealed=false permits=[] record=[] enum=[XY, YX] annotations=[] members=[field:XY[], field:YX[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileMatrixCorner sealed=false permits=[] record=[] enum=[TOP_LEFT, BOTTOM_LEFT] annotations=[] members=[field:BOTTOM_LEFT[], field:TOP_LEFT[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileMatrixException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.core.TileMatrixProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileMatrixIndex sealed=false permits=[] record=[matrixIdentifier:java.lang.String[], row:long[], column:long[]] enum=[] annotations=[] members=[constructor:[java.lang.String, long, long] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:column[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:matrixIdentifier[] throws=[] annotations=[] parameterAnnotations=[], method:row[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileMatrixProblem sealed=false permits=[] record=[code:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileMatrixSelectionPolicy sealed=false permits=[] record=[] enum=[NEAREST, COARSER_OR_EQUAL, FINER_OR_EQUAL] annotations=[] members=[field:COARSER_OR_EQUAL[], field:FINER_OR_EQUAL[], field:NEAREST[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.TileMatrixSet sealed=false permits=[] record=[identifier:java.lang.String[], crs:io.github.mundanej.map.api.CrsDefinition[], orderedAxes:io.github.mundanej.map.core.TileMatrixAxisOrder[], boundingBox:io.github.mundanej.map.api.Envelope[], tileMatrices:java.util.List<io.github.mundanej.map.core.TileMatrix>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.core.TileMatrixAxisOrder, io.github.mundanej.map.api.Envelope, java.util.List<io.github.mundanej.map.core.TileMatrix>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], field:MAXIMUM_MATRICES[], method:boundingBox[] throws=[] annotations=[] parameterAnnotations=[], method:crs[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:identifier[] throws=[] annotations=[] parameterAnnotations=[], method:matrix[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:orderedAxes[] throws=[] annotations=[] parameterAnnotations=[], method:select[double, io.github.mundanej.map.core.TileMatrixSelectionPolicy] throws=[] annotations=[] parameterAnnotations=[[], []], method:tileMatrices[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.VariableMatrixWidth sealed=false permits=[] record=[coalesce:int[], minimumTileRow:long[], maximumTileRow:long[]] enum=[] annotations=[] members=[constructor:[int, long, long] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:MAXIMUM_COALESCE[], method:coalesce[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTileRow[] throws=[] annotations=[] parameterAnnotations=[], method:minimumTileRow[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.core.WebMercatorProjection sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[] throws=[] annotations=[] parameterAnnotations=[], field:MAX_LATITUDE[], field:WORLD_LIMIT[], method:projectEnvelope[io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[]], method:project[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:sourceCrs[] throws=[] annotations=[] parameterAnnotations=[], method:sourceDomain[] throws=[] annotations=[] parameterAnnotations=[], method:targetCrs[] throws=[] annotations=[] parameterAnnotations=[], method:targetDomain[] throws=[] annotations=[] parameterAnnotations=[], method:unprojectEnvelope[io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[]], method:unproject[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.Wkt2 sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:MAXIMUM_CHARACTERS[], field:MAXIMUM_DEPTH[], field:MAXIMUM_VALUES[], method:parse[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:write[io.github.mundanej.map.api.WktCrsDefinition] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.WktCoordinateOperation sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:MAXIMUM_BATCH_COORDINATES[], field:MERCATOR_VARIANT_A[], field:TRANSVERSE_MERCATOR[], method:between[io.github.mundanej.map.api.WktCrsDefinition, io.github.mundanej.map.api.WktCrsDefinition] throws=[] annotations=[] parameterAnnotations=[[], []], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:target[] throws=[] annotations=[] parameterAnnotations=[], method:transformAll[java.util.List<io.github.mundanej.map.api.Coordinate>] throws=[] annotations=[] parameterAnnotations=[[]], method:transform[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.core.WrappedX sealed=false permits=[] record=[canonicalX:double[], copyIndex:long[]] enum=[] annotations=[] members=[constructor:[double, long] throws=[] annotations=[] parameterAnnotations=[[], []], method:canonicalX[] throws=[] annotations=[] parameterAnnotations=[], method:copyIndex[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
