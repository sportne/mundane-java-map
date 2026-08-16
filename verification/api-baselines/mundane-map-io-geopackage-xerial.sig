public final class io.github.mundanej.map.io.geopackage.GeoPackageCatalog extends java.lang.Record {
  public io.github.mundanej.map.io.geopackage.GeoPackageCatalog(java.util.List<io.github.mundanej.map.io.geopackage.GeoPackageFeatureTable>, java.util.List<io.github.mundanej.map.io.geopackage.GeoPackageTileTable>, io.github.mundanej.map.api.DiagnosticReport);
    descriptor: (Ljava/util/List;Ljava/util/List;Lio/github/mundanej/map/api/DiagnosticReport;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.List<io.github.mundanej.map.io.geopackage.GeoPackageFeatureTable> featureTables();
    descriptor: ()Ljava/util/List;
  public java.util.List<io.github.mundanej.map.io.geopackage.GeoPackageTileTable> tileTables();
    descriptor: ()Ljava/util/List;
  public io.github.mundanej.map.api.DiagnosticReport report();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
}
public final class io.github.mundanej.map.io.geopackage.GeoPackageFeatureOptions extends java.lang.Record {
  public io.github.mundanej.map.io.geopackage.GeoPackageFeatureOptions(io.github.mundanej.map.io.geopackage.GeoPackageLimits, io.github.mundanej.map.api.FeatureSourceLimits);
    descriptor: (Lio/github/mundanej/map/io/geopackage/GeoPackageLimits;Lio/github/mundanej/map/api/FeatureSourceLimits;)V
  public static io.github.mundanej.map.io.geopackage.GeoPackageFeatureOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/geopackage/GeoPackageFeatureOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.geopackage.GeoPackageLimits limits();
    descriptor: ()Lio/github/mundanej/map/io/geopackage/GeoPackageLimits;
  public io.github.mundanej.map.api.FeatureSourceLimits featureSourceLimits();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSourceLimits;
}
public final class io.github.mundanej.map.io.geopackage.GeoPackageFeatureTable extends java.lang.Record {
  public io.github.mundanej.map.io.geopackage.GeoPackageFeatureTable(java.lang.String, java.lang.String, io.github.mundanej.map.io.geopackage.GeoPackageGeometryType, java.lang.String, io.github.mundanej.map.api.AttributeSchema, int, io.github.mundanej.map.api.CrsMetadata, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.OptionalLong);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;Ljava/lang/String;Lio/github/mundanej/map/api/AttributeSchema;ILio/github/mundanej/map/api/CrsMetadata;Ljava/util/Optional;Ljava/util/OptionalLong;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String tableName();
    descriptor: ()Ljava/lang/String;
  public java.lang.String geometryColumnName();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.io.geopackage.GeoPackageGeometryType geometryType();
    descriptor: ()Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
  public java.lang.String primaryKey();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.AttributeSchema attributeSchema();
    descriptor: ()Lio/github/mundanej/map/api/AttributeSchema;
  public int srsId();
    descriptor: ()I
  public io.github.mundanej.map.api.CrsMetadata crs();
    descriptor: ()Lio/github/mundanej/map/api/CrsMetadata;
  public java.util.Optional<io.github.mundanej.map.api.Envelope> bounds();
    descriptor: ()Ljava/util/Optional;
  public java.util.OptionalLong featureCount();
    descriptor: ()Ljava/util/OptionalLong;
}
public final class io.github.mundanej.map.io.geopackage.GeoPackageGeometryType extends java.lang.Enum<io.github.mundanej.map.io.geopackage.GeoPackageGeometryType> {
  public static final io.github.mundanej.map.io.geopackage.GeoPackageGeometryType GEOMETRY;
    descriptor: Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
  public static final io.github.mundanej.map.io.geopackage.GeoPackageGeometryType POINT;
    descriptor: Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
  public static final io.github.mundanej.map.io.geopackage.GeoPackageGeometryType MULTI_POINT;
    descriptor: Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
  public static final io.github.mundanej.map.io.geopackage.GeoPackageGeometryType LINE_STRING;
    descriptor: Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
  public static final io.github.mundanej.map.io.geopackage.GeoPackageGeometryType MULTI_LINE_STRING;
    descriptor: Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
  public static final io.github.mundanej.map.io.geopackage.GeoPackageGeometryType POLYGON;
    descriptor: Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
  public static final io.github.mundanej.map.io.geopackage.GeoPackageGeometryType MULTI_POLYGON;
    descriptor: Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
  public static io.github.mundanej.map.io.geopackage.GeoPackageGeometryType[] values();
    descriptor: ()[Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
  public static io.github.mundanej.map.io.geopackage.GeoPackageGeometryType valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/io/geopackage/GeoPackageGeometryType;
}
public final class io.github.mundanej.map.io.geopackage.GeoPackageInspectOptions extends java.lang.Record {
  public io.github.mundanej.map.io.geopackage.GeoPackageInspectOptions(io.github.mundanej.map.io.geopackage.GeoPackageLimits);
    descriptor: (Lio/github/mundanej/map/io/geopackage/GeoPackageLimits;)V
  public static io.github.mundanej.map.io.geopackage.GeoPackageInspectOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/geopackage/GeoPackageInspectOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.geopackage.GeoPackageLimits limits();
    descriptor: ()Lio/github/mundanej/map/io/geopackage/GeoPackageLimits;
}
public final class io.github.mundanej.map.io.geopackage.GeoPackageLimits extends java.lang.Record {
  public static final io.github.mundanej.map.io.geopackage.GeoPackageLimits DEFAULTS;
    descriptor: Lio/github/mundanej/map/io/geopackage/GeoPackageLimits;
  public io.github.mundanej.map.io.geopackage.GeoPackageLimits(long, int, int, int, long, int, long, int, long, long, long, int, int, int, int, int, int, long);
    descriptor: (JIIIJIJIJJJIIIIIIJ)V
  public io.github.mundanej.map.io.geopackage.GeoPackageLimits withMaximumSchemaObjects(int);
    descriptor: (I)Lio/github/mundanej/map/io/geopackage/GeoPackageLimits;
  public io.github.mundanej.map.io.geopackage.GeoPackageLimits withMaximumVmOpcodes(long);
    descriptor: (J)Lio/github/mundanej/map/io/geopackage/GeoPackageLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long maximumInputBytes();
    descriptor: ()J
  public int maximumSchemaObjects();
    descriptor: ()I
  public int maximumColumns();
    descriptor: ()I
  public int maximumIdentifierCharacters();
    descriptor: ()I
  public long maximumMetadataRows();
    descriptor: ()J
  public int maximumTextValueCharacters();
    descriptor: ()I
  public long maximumTextCharacters();
    descriptor: ()J
  public int maximumBlobBytes();
    descriptor: ()I
  public long maximumRows();
    descriptor: ()J
  public long maximumVmOpcodes();
    descriptor: ()J
  public long maximumOwnedBytes();
    descriptor: ()J
  public int maximumZoomLevels();
    descriptor: ()I
  public int maximumZoom();
    descriptor: ()I
  public int maximumMatrixAxis();
    descriptor: ()I
  public int maximumCoordinates();
    descriptor: ()I
  public int maximumParts();
    descriptor: ()I
  public int maximumCacheEntries();
    descriptor: ()I
  public long maximumCacheBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.io.geopackage.GeoPackageTileCachePolicy {
  public static io.github.mundanej.map.io.geopackage.GeoPackageTileCachePolicy disabled();
    descriptor: ()Lio/github/mundanej/map/io/geopackage/GeoPackageTileCachePolicy;
  public static io.github.mundanej.map.io.geopackage.GeoPackageTileCachePolicy bounded(int, long);
    descriptor: (IJ)Lio/github/mundanej/map/io/geopackage/GeoPackageTileCachePolicy;
  public boolean enabled();
    descriptor: ()Z
  public java.util.OptionalInt maximumEntries();
    descriptor: ()Ljava/util/OptionalInt;
  public java.util.OptionalLong maximumPixelBytes();
    descriptor: ()Ljava/util/OptionalLong;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.io.geopackage.GeoPackageTileOptions extends java.lang.Record {
  public io.github.mundanej.map.io.geopackage.GeoPackageTileOptions(io.github.mundanej.map.io.geopackage.GeoPackageLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.geopackage.GeoPackageTileCachePolicy);
    descriptor: (Lio/github/mundanej/map/io/geopackage/GeoPackageLimits;Lio/github/mundanej/map/api/RasterSourceLimits;Lio/github/mundanej/map/io/geopackage/GeoPackageTileCachePolicy;)V
  public static io.github.mundanej.map.io.geopackage.GeoPackageTileOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/geopackage/GeoPackageTileOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.geopackage.GeoPackageLimits limits();
    descriptor: ()Lio/github/mundanej/map/io/geopackage/GeoPackageLimits;
  public io.github.mundanej.map.api.RasterSourceLimits rasterSourceLimits();
    descriptor: ()Lio/github/mundanej/map/api/RasterSourceLimits;
  public io.github.mundanej.map.io.geopackage.GeoPackageTileCachePolicy cachePolicy();
    descriptor: ()Lio/github/mundanej/map/io/geopackage/GeoPackageTileCachePolicy;
}
public final class io.github.mundanej.map.io.geopackage.GeoPackageTileTable extends java.lang.Record {
  public io.github.mundanej.map.io.geopackage.GeoPackageTileTable(java.lang.String, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.CrsMetadata, java.util.List<java.lang.Integer>);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/CrsMetadata;Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String tableName();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.Envelope bounds();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.CrsMetadata crs();
    descriptor: ()Lio/github/mundanej/map/api/CrsMetadata;
  public java.util.List<java.lang.Integer> zoomLevels();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.io.geopackage.GeoPackages {
  public static io.github.mundanej.map.io.geopackage.GeoPackageCatalog inspect(java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.geopackage.GeoPackageInspectOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/io/geopackage/GeoPackageInspectOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/io/geopackage/GeoPackageCatalog;
  public static io.github.mundanej.map.api.FeatureSource openFeatures(java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, java.lang.String, io.github.mundanej.map.io.geopackage.GeoPackageFeatureOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/SourceIdentity;Ljava/lang/String;Lio/github/mundanej/map/io/geopackage/GeoPackageFeatureOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/FeatureSource;
  public static io.github.mundanej.map.api.RasterSource openTiles(java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, java.lang.String, int, io.github.mundanej.map.io.geopackage.GeoPackageTileOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/SourceIdentity;Ljava/lang/String;ILio/github/mundanej/map/io/geopackage/GeoPackageTileOptions;Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterSource;
}
SHAPE io.github.mundanej.map.io.geopackage.GeoPackageCatalog sealed=false permits=[] record=[featureTables:java.util.List<io.github.mundanej.map.io.geopackage.GeoPackageFeatureTable>[], tileTables:java.util.List<io.github.mundanej.map.io.geopackage.GeoPackageTileTable>[], report:io.github.mundanej.map.api.DiagnosticReport[]] enum=[] annotations=[] members=[constructor:[java.util.List<io.github.mundanej.map.io.geopackage.GeoPackageFeatureTable>, java.util.List<io.github.mundanej.map.io.geopackage.GeoPackageTileTable>, io.github.mundanej.map.api.DiagnosticReport] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureTables[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:report[] throws=[] annotations=[] parameterAnnotations=[], method:tileTables[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geopackage.GeoPackageFeatureOptions sealed=false permits=[] record=[limits:io.github.mundanej.map.io.geopackage.GeoPackageLimits[], featureSourceLimits:io.github.mundanej.map.api.FeatureSourceLimits[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.geopackage.GeoPackageLimits, io.github.mundanej.map.api.FeatureSourceLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureSourceLimits[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geopackage.GeoPackageFeatureTable sealed=false permits=[] record=[tableName:java.lang.String[], geometryColumnName:java.lang.String[], geometryType:io.github.mundanej.map.io.geopackage.GeoPackageGeometryType[], primaryKey:java.lang.String[], attributeSchema:io.github.mundanej.map.api.AttributeSchema[], srsId:int[], crs:io.github.mundanej.map.api.CrsMetadata[], bounds:java.util.Optional<io.github.mundanej.map.api.Envelope>[], featureCount:java.util.OptionalLong[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, io.github.mundanej.map.io.geopackage.GeoPackageGeometryType, java.lang.String, io.github.mundanej.map.api.AttributeSchema, int, io.github.mundanej.map.api.CrsMetadata, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.OptionalLong] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], []], method:attributeSchema[] throws=[] annotations=[] parameterAnnotations=[], method:bounds[] throws=[] annotations=[] parameterAnnotations=[], method:crs[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureCount[] throws=[] annotations=[] parameterAnnotations=[], method:geometryColumnName[] throws=[] annotations=[] parameterAnnotations=[], method:geometryType[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:primaryKey[] throws=[] annotations=[] parameterAnnotations=[], method:srsId[] throws=[] annotations=[] parameterAnnotations=[], method:tableName[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geopackage.GeoPackageGeometryType sealed=false permits=[] record=[] enum=[GEOMETRY, POINT, MULTI_POINT, LINE_STRING, MULTI_LINE_STRING, POLYGON, MULTI_POLYGON] annotations=[] members=[field:GEOMETRY[], field:LINE_STRING[], field:MULTI_LINE_STRING[], field:MULTI_POINT[], field:MULTI_POLYGON[], field:POINT[], field:POLYGON[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geopackage.GeoPackageInspectOptions sealed=false permits=[] record=[limits:io.github.mundanej.map.io.geopackage.GeoPackageLimits[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.geopackage.GeoPackageLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geopackage.GeoPackageLimits sealed=false permits=[] record=[maximumInputBytes:long[], maximumSchemaObjects:int[], maximumColumns:int[], maximumIdentifierCharacters:int[], maximumMetadataRows:long[], maximumTextValueCharacters:int[], maximumTextCharacters:long[], maximumBlobBytes:int[], maximumRows:long[], maximumVmOpcodes:long[], maximumOwnedBytes:long[], maximumZoomLevels:int[], maximumZoom:int[], maximumMatrixAxis:int[], maximumCoordinates:int[], maximumParts:int[], maximumCacheEntries:int[], maximumCacheBytes:long[]] enum=[] annotations=[] members=[constructor:[long, int, int, int, long, int, long, int, long, long, long, int, int, int, int, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], [], [], [], [], [], [], []], field:DEFAULTS[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumBlobBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCacheBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCacheEntries[] throws=[] annotations=[] parameterAnnotations=[], method:maximumColumns[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maximumIdentifierCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumInputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumMatrixAxis[] throws=[] annotations=[] parameterAnnotations=[], method:maximumMetadataRows[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumParts[] throws=[] annotations=[] parameterAnnotations=[], method:maximumRows[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSchemaObjects[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTextCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTextValueCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumVmOpcodes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumZoomLevels[] throws=[] annotations=[] parameterAnnotations=[], method:maximumZoom[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumSchemaObjects[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumVmOpcodes[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.geopackage.GeoPackageTileCachePolicy sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:bounded[int, long] throws=[] annotations=[] parameterAnnotations=[[], []], method:disabled[] throws=[] annotations=[] parameterAnnotations=[], method:enabled[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumEntries[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPixelBytes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geopackage.GeoPackageTileOptions sealed=false permits=[] record=[limits:io.github.mundanej.map.io.geopackage.GeoPackageLimits[], rasterSourceLimits:io.github.mundanej.map.api.RasterSourceLimits[], cachePolicy:io.github.mundanej.map.io.geopackage.GeoPackageTileCachePolicy[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.geopackage.GeoPackageLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.geopackage.GeoPackageTileCachePolicy] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:cachePolicy[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:rasterSourceLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geopackage.GeoPackageTileTable sealed=false permits=[] record=[tableName:java.lang.String[], bounds:io.github.mundanej.map.api.Envelope[], crs:io.github.mundanej.map.api.CrsMetadata[], zoomLevels:java.util.List<java.lang.Integer>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.CrsMetadata, java.util.List<java.lang.Integer>] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:bounds[] throws=[] annotations=[] parameterAnnotations=[], method:crs[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:tableName[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:zoomLevels[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geopackage.GeoPackages sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:inspect[java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.geopackage.GeoPackageInspectOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:openFeatures[java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, java.lang.String, io.github.mundanej.map.io.geopackage.GeoPackageFeatureOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:openTiles[java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, java.lang.String, int, io.github.mundanej.map.io.geopackage.GeoPackageTileOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], []]]
