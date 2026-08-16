public final class io.github.mundanej.map.io.mbtiles.MbTiles {
  public static io.github.mundanej.map.io.mbtiles.MbTilesMetadata inspect(java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.mbtiles.MbTilesInspectOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/io/mbtiles/MbTilesInspectOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/io/mbtiles/MbTilesMetadata;
  public static io.github.mundanej.map.api.RasterSource open(java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, int, io.github.mundanej.map.io.mbtiles.MbTilesOpenOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/SourceIdentity;ILio/github/mundanej/map/io/mbtiles/MbTilesOpenOptions;Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterSource;
}
public final class io.github.mundanej.map.io.mbtiles.MbTilesCenter extends java.lang.Record {
  public io.github.mundanej.map.io.mbtiles.MbTilesCenter(double, double, int);
    descriptor: (DDI)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double longitude();
    descriptor: ()D
  public double latitude();
    descriptor: ()D
  public int zoom();
    descriptor: ()I
}
public final class io.github.mundanej.map.io.mbtiles.MbTilesInspectOptions extends java.lang.Record {
  public io.github.mundanej.map.io.mbtiles.MbTilesInspectOptions(io.github.mundanej.map.io.mbtiles.MbTilesLimits);
    descriptor: (Lio/github/mundanej/map/io/mbtiles/MbTilesLimits;)V
  public static io.github.mundanej.map.io.mbtiles.MbTilesInspectOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/mbtiles/MbTilesInspectOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.mbtiles.MbTilesLimits limits();
    descriptor: ()Lio/github/mundanej/map/io/mbtiles/MbTilesLimits;
}
public final class io.github.mundanej.map.io.mbtiles.MbTilesLimits extends java.lang.Record {
  public static final io.github.mundanej.map.io.mbtiles.MbTilesLimits DEFAULTS;
    descriptor: Lio/github/mundanej/map/io/mbtiles/MbTilesLimits;
  public io.github.mundanej.map.io.mbtiles.MbTilesLimits(long, int, int, int, long, int, long, int, long, long, long, int, int, int, int, int, int, long);
    descriptor: (JIIIJIJIJJJIIIIIIJ)V
  public io.github.mundanej.map.io.mbtiles.MbTilesLimits withMaximumSchemaObjects(int);
    descriptor: (I)Lio/github/mundanej/map/io/mbtiles/MbTilesLimits;
  public io.github.mundanej.map.io.mbtiles.MbTilesLimits withMaximumVmOpcodes(long);
    descriptor: (J)Lio/github/mundanej/map/io/mbtiles/MbTilesLimits;
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
public final class io.github.mundanej.map.io.mbtiles.MbTilesMetadata extends java.lang.Record {
  public io.github.mundanej.map.io.mbtiles.MbTilesMetadata(java.lang.String, io.github.mundanej.map.api.EncodedRasterFormat, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.Optional<io.github.mundanej.map.io.mbtiles.MbTilesCenter>, java.util.OptionalInt, java.util.OptionalInt, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>, java.util.List<java.lang.Integer>, io.github.mundanej.map.api.DiagnosticReport);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/EncodedRasterFormat;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/OptionalInt;Ljava/util/OptionalInt;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/List;Lio/github/mundanej/map/api/DiagnosticReport;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.EncodedRasterFormat format();
    descriptor: ()Lio/github/mundanej/map/api/EncodedRasterFormat;
  public java.util.Optional<io.github.mundanej.map.api.Envelope> bounds();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.io.mbtiles.MbTilesCenter> center();
    descriptor: ()Ljava/util/Optional;
  public java.util.OptionalInt minimumZoom();
    descriptor: ()Ljava/util/OptionalInt;
  public java.util.OptionalInt maximumZoom();
    descriptor: ()Ljava/util/OptionalInt;
  public java.util.Optional<java.lang.String> type();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<java.lang.String> revision();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<java.lang.String> description();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<java.lang.String> attribution();
    descriptor: ()Ljava/util/Optional;
  public java.util.List<java.lang.Integer> zoomLevels();
    descriptor: ()Ljava/util/List;
  public io.github.mundanej.map.api.DiagnosticReport openingDiagnostics();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
}
public final class io.github.mundanej.map.io.mbtiles.MbTilesOpenOptions extends java.lang.Record {
  public io.github.mundanej.map.io.mbtiles.MbTilesOpenOptions(io.github.mundanej.map.io.mbtiles.MbTilesLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.mbtiles.MbTilesTileCachePolicy);
    descriptor: (Lio/github/mundanej/map/io/mbtiles/MbTilesLimits;Lio/github/mundanej/map/api/RasterSourceLimits;Lio/github/mundanej/map/io/mbtiles/MbTilesTileCachePolicy;)V
  public static io.github.mundanej.map.io.mbtiles.MbTilesOpenOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/mbtiles/MbTilesOpenOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.mbtiles.MbTilesLimits limits();
    descriptor: ()Lio/github/mundanej/map/io/mbtiles/MbTilesLimits;
  public io.github.mundanej.map.api.RasterSourceLimits rasterSourceLimits();
    descriptor: ()Lio/github/mundanej/map/api/RasterSourceLimits;
  public io.github.mundanej.map.io.mbtiles.MbTilesTileCachePolicy cachePolicy();
    descriptor: ()Lio/github/mundanej/map/io/mbtiles/MbTilesTileCachePolicy;
}
public final class io.github.mundanej.map.io.mbtiles.MbTilesTileCachePolicy {
  public static io.github.mundanej.map.io.mbtiles.MbTilesTileCachePolicy disabled();
    descriptor: ()Lio/github/mundanej/map/io/mbtiles/MbTilesTileCachePolicy;
  public static io.github.mundanej.map.io.mbtiles.MbTilesTileCachePolicy bounded(int, long);
    descriptor: (IJ)Lio/github/mundanej/map/io/mbtiles/MbTilesTileCachePolicy;
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
SHAPE io.github.mundanej.map.io.mbtiles.MbTiles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:inspect[java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.mbtiles.MbTilesInspectOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:open[java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, int, io.github.mundanej.map.io.mbtiles.MbTilesOpenOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []]]
SHAPE io.github.mundanej.map.io.mbtiles.MbTilesCenter sealed=false permits=[] record=[longitude:double[], latitude:double[], zoom:int[]] enum=[] annotations=[] members=[constructor:[double, double, int] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:latitude[] throws=[] annotations=[] parameterAnnotations=[], method:longitude[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:zoom[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.mbtiles.MbTilesInspectOptions sealed=false permits=[] record=[limits:io.github.mundanej.map.io.mbtiles.MbTilesLimits[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.mbtiles.MbTilesLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.mbtiles.MbTilesLimits sealed=false permits=[] record=[maximumInputBytes:long[], maximumSchemaObjects:int[], maximumColumns:int[], maximumIdentifierCharacters:int[], maximumMetadataRows:long[], maximumTextValueCharacters:int[], maximumTextCharacters:long[], maximumBlobBytes:int[], maximumRows:long[], maximumVmOpcodes:long[], maximumOwnedBytes:long[], maximumZoomLevels:int[], maximumZoom:int[], maximumMatrixAxis:int[], maximumCoordinates:int[], maximumParts:int[], maximumCacheEntries:int[], maximumCacheBytes:long[]] enum=[] annotations=[] members=[constructor:[long, int, int, int, long, int, long, int, long, long, long, int, int, int, int, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], [], [], [], [], [], [], []], field:DEFAULTS[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumBlobBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCacheBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCacheEntries[] throws=[] annotations=[] parameterAnnotations=[], method:maximumColumns[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maximumIdentifierCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumInputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumMatrixAxis[] throws=[] annotations=[] parameterAnnotations=[], method:maximumMetadataRows[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumParts[] throws=[] annotations=[] parameterAnnotations=[], method:maximumRows[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSchemaObjects[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTextCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTextValueCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumVmOpcodes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumZoomLevels[] throws=[] annotations=[] parameterAnnotations=[], method:maximumZoom[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumSchemaObjects[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumVmOpcodes[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.mbtiles.MbTilesMetadata sealed=false permits=[] record=[name:java.lang.String[], format:io.github.mundanej.map.api.EncodedRasterFormat[], bounds:java.util.Optional<io.github.mundanej.map.api.Envelope>[], center:java.util.Optional<io.github.mundanej.map.io.mbtiles.MbTilesCenter>[], minimumZoom:java.util.OptionalInt[], maximumZoom:java.util.OptionalInt[], type:java.util.Optional<java.lang.String>[], revision:java.util.Optional<java.lang.String>[], description:java.util.Optional<java.lang.String>[], attribution:java.util.Optional<java.lang.String>[], zoomLevels:java.util.List<java.lang.Integer>[], openingDiagnostics:io.github.mundanej.map.api.DiagnosticReport[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.EncodedRasterFormat, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.Optional<io.github.mundanej.map.io.mbtiles.MbTilesCenter>, java.util.OptionalInt, java.util.OptionalInt, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>, java.util.List<java.lang.Integer>, io.github.mundanej.map.api.DiagnosticReport] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], []], method:attribution[] throws=[] annotations=[] parameterAnnotations=[], method:bounds[] throws=[] annotations=[] parameterAnnotations=[], method:center[] throws=[] annotations=[] parameterAnnotations=[], method:description[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:format[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumZoom[] throws=[] annotations=[] parameterAnnotations=[], method:minimumZoom[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:openingDiagnostics[] throws=[] annotations=[] parameterAnnotations=[], method:revision[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:type[] throws=[] annotations=[] parameterAnnotations=[], method:zoomLevels[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.mbtiles.MbTilesOpenOptions sealed=false permits=[] record=[limits:io.github.mundanej.map.io.mbtiles.MbTilesLimits[], rasterSourceLimits:io.github.mundanej.map.api.RasterSourceLimits[], cachePolicy:io.github.mundanej.map.io.mbtiles.MbTilesTileCachePolicy[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.mbtiles.MbTilesLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.mbtiles.MbTilesTileCachePolicy] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:cachePolicy[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:rasterSourceLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.mbtiles.MbTilesTileCachePolicy sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:bounded[int, long] throws=[] annotations=[] parameterAnnotations=[[], []], method:disabled[] throws=[] annotations=[] parameterAnnotations=[], method:enabled[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumEntries[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPixelBytes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
