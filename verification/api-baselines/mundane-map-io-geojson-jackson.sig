public final class io.github.mundanej.map.io.geojson.GeoJsonFiles {
  public static io.github.mundanej.map.api.FeatureSource open(java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.geojson.GeoJsonOpenOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/io/geojson/GeoJsonOpenOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/FeatureSource;
  public static void write(java.nio.file.Path, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.io.geojson.GeoJsonWriteLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/io/geojson/GeoJsonWriteLimits;Lio/github/mundanej/map/api/CancellationToken;)V
  public static io.github.mundanej.map.api.FeatureSource open(byte[], io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.geojson.GeoJsonOpenOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: ([BLio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/io/geojson/GeoJsonOpenOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/FeatureSource;
}
public final class io.github.mundanej.map.io.geojson.GeoJsonLimits extends java.lang.Record {
  public io.github.mundanej.map.io.geojson.GeoJsonLimits(int, int, long, int, int, int, int, int, int, int, int, int, int, int, long, int);
    descriptor: (IIJIIIIIIIIIIIJI)V
  public static io.github.mundanej.map.io.geojson.GeoJsonLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/geojson/GeoJsonLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumInputBytes();
    descriptor: ()I
  public int maximumNestingDepth();
    descriptor: ()I
  public long maximumTokens();
    descriptor: ()J
  public int maximumObjectMembers();
    descriptor: ()I
  public int maximumPhysicalFeatures();
    descriptor: ()I
  public int maximumTotalPositions();
    descriptor: ()I
  public int maximumPositionsPerGeometry();
    descriptor: ()I
  public int maximumParts();
    descriptor: ()I
  public int maximumPropertiesPerFeature();
    descriptor: ()I
  public int maximumTotalProperties();
    descriptor: ()I
  public int maximumMemberNameCharacters();
    descriptor: ()I
  public int maximumScalarCharacters();
    descriptor: ()I
  public int maximumAggregateCharacters();
    descriptor: ()I
  public int maximumNumberCharacters();
    descriptor: ()I
  public long maximumOwnedBytes();
    descriptor: ()J
  public int retainedWarnings();
    descriptor: ()I
}
public final class io.github.mundanej.map.io.geojson.GeoJsonOpenOptions extends java.lang.Record {
  public io.github.mundanej.map.io.geojson.GeoJsonOpenOptions(io.github.mundanej.map.io.geojson.GeoJsonLimits, io.github.mundanej.map.api.FeatureSourceLimits);
    descriptor: (Lio/github/mundanej/map/io/geojson/GeoJsonLimits;Lio/github/mundanej/map/api/FeatureSourceLimits;)V
  public static io.github.mundanej.map.io.geojson.GeoJsonOpenOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/geojson/GeoJsonOpenOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.geojson.GeoJsonLimits formatLimits();
    descriptor: ()Lio/github/mundanej/map/io/geojson/GeoJsonLimits;
  public io.github.mundanej.map.api.FeatureSourceLimits sourceLimits();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSourceLimits;
}
public final class io.github.mundanej.map.io.geojson.GeoJsonWriteException extends java.lang.RuntimeException {
  public io.github.mundanej.map.io.geojson.GeoJsonWriteException(io.github.mundanej.map.io.geojson.GeoJsonWriteProblem, java.util.Optional<io.github.mundanej.map.api.DiagnosticReport>, java.lang.Throwable);
    descriptor: (Lio/github/mundanej/map/io/geojson/GeoJsonWriteProblem;Ljava/util/Optional;Ljava/lang/Throwable;)V
  public io.github.mundanej.map.io.geojson.GeoJsonWriteProblem problem();
    descriptor: ()Lio/github/mundanej/map/io/geojson/GeoJsonWriteProblem;
  public java.util.Optional<io.github.mundanej.map.api.DiagnosticReport> sourceReport();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.io.geojson.GeoJsonWriteLimits extends java.lang.Record {
  public io.github.mundanej.map.io.geojson.GeoJsonWriteLimits(long, long, int, int, int, int, int, int, int, int, int, int);
    descriptor: (JJIIIIIIIIII)V
  public static io.github.mundanej.map.io.geojson.GeoJsonWriteLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/geojson/GeoJsonWriteLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long maximumOutputBytes();
    descriptor: ()J
  public long maximumOwnedBytes();
    descriptor: ()J
  public int maximumNestingDepth();
    descriptor: ()I
  public int maximumFeatures();
    descriptor: ()I
  public int maximumTotalCoordinates();
    descriptor: ()I
  public int maximumCoordinatesPerGeometry();
    descriptor: ()I
  public int maximumParts();
    descriptor: ()I
  public int maximumPropertiesPerFeature();
    descriptor: ()I
  public int maximumTotalProperties();
    descriptor: ()I
  public int maximumScalarCharacters();
    descriptor: ()I
  public int maximumAggregateCharacters();
    descriptor: ()I
  public int maximumNumberCharacters();
    descriptor: ()I
}
public final class io.github.mundanej.map.io.geojson.GeoJsonWriteProblem extends java.lang.Record {
  public io.github.mundanej.map.io.geojson.GeoJsonWriteProblem(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.lang.String message();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
SHAPE io.github.mundanej.map.io.geojson.GeoJsonFiles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:open[byte[], io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.geojson.GeoJsonOpenOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:open[java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.geojson.GeoJsonOpenOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:write[java.nio.file.Path, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.io.geojson.GeoJsonWriteLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []]]
SHAPE io.github.mundanej.map.io.geojson.GeoJsonLimits sealed=false permits=[] record=[maximumInputBytes:int[], maximumNestingDepth:int[], maximumTokens:long[], maximumObjectMembers:int[], maximumPhysicalFeatures:int[], maximumTotalPositions:int[], maximumPositionsPerGeometry:int[], maximumParts:int[], maximumPropertiesPerFeature:int[], maximumTotalProperties:int[], maximumMemberNameCharacters:int[], maximumScalarCharacters:int[], maximumAggregateCharacters:int[], maximumNumberCharacters:int[], maximumOwnedBytes:long[], retainedWarnings:int[]] enum=[] annotations=[] members=[constructor:[int, int, long, int, int, int, int, int, int, int, int, int, int, int, long, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], [], [], [], [], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAggregateCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumInputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumMemberNameCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumNestingDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumNumberCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumObjectMembers[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumParts[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPhysicalFeatures[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPositionsPerGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPropertiesPerFeature[] throws=[] annotations=[] parameterAnnotations=[], method:maximumScalarCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTokens[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTotalPositions[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTotalProperties[] throws=[] annotations=[] parameterAnnotations=[], method:retainedWarnings[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geojson.GeoJsonOpenOptions sealed=false permits=[] record=[formatLimits:io.github.mundanej.map.io.geojson.GeoJsonLimits[], sourceLimits:io.github.mundanej.map.api.FeatureSourceLimits[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.geojson.GeoJsonLimits, io.github.mundanej.map.api.FeatureSourceLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:formatLimits[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:sourceLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geojson.GeoJsonWriteException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.geojson.GeoJsonWriteProblem, java.util.Optional<io.github.mundanej.map.api.DiagnosticReport>, java.lang.Throwable] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:problem[] throws=[] annotations=[] parameterAnnotations=[], method:sourceReport[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geojson.GeoJsonWriteLimits sealed=false permits=[] record=[maximumOutputBytes:long[], maximumOwnedBytes:long[], maximumNestingDepth:int[], maximumFeatures:int[], maximumTotalCoordinates:int[], maximumCoordinatesPerGeometry:int[], maximumParts:int[], maximumPropertiesPerFeature:int[], maximumTotalProperties:int[], maximumScalarCharacters:int[], maximumAggregateCharacters:int[], maximumNumberCharacters:int[]] enum=[] annotations=[] members=[constructor:[long, long, int, int, int, int, int, int, int, int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAggregateCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCoordinatesPerGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:maximumFeatures[] throws=[] annotations=[] parameterAnnotations=[], method:maximumNestingDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumNumberCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOutputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumParts[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPropertiesPerFeature[] throws=[] annotations=[] parameterAnnotations=[], method:maximumScalarCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTotalCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTotalProperties[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.geojson.GeoJsonWriteProblem sealed=false permits=[] record=[code:java.lang.String[], message:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:message[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
