public final class io.github.mundanej.map.io.http.tiles.HttpSchemePolicy extends java.lang.Enum<io.github.mundanej.map.io.http.tiles.HttpSchemePolicy> {
  public static final io.github.mundanej.map.io.http.tiles.HttpSchemePolicy HTTPS_ONLY;
    descriptor: Lio/github/mundanej/map/io/http/tiles/HttpSchemePolicy;
  public static final io.github.mundanej.map.io.http.tiles.HttpSchemePolicy HTTPS_OR_HTTP;
    descriptor: Lio/github/mundanej/map/io/http/tiles/HttpSchemePolicy;
  public static io.github.mundanej.map.io.http.tiles.HttpSchemePolicy[] values();
    descriptor: ()[Lio/github/mundanej/map/io/http/tiles/HttpSchemePolicy;
  public static io.github.mundanej.map.io.http.tiles.HttpSchemePolicy valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/io/http/tiles/HttpSchemePolicy;
}
public final class io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy extends java.lang.Enum<io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy> {
  public static final io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy DISABLED;
    descriptor: Lio/github/mundanej/map/io/http/tiles/HttpTileCachePolicy;
  public static final io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy MEMORY;
    descriptor: Lio/github/mundanej/map/io/http/tiles/HttpTileCachePolicy;
  public static io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy[] values();
    descriptor: ()[Lio/github/mundanej/map/io/http/tiles/HttpTileCachePolicy;
  public static io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/io/http/tiles/HttpTileCachePolicy;
}
public final class io.github.mundanej.map.io.http.tiles.HttpXyzClientOptions extends java.lang.Record {
  public io.github.mundanej.map.io.http.tiles.HttpXyzClientOptions(io.github.mundanej.map.io.http.tiles.HttpSchemePolicy, io.github.mundanej.map.io.http.tiles.HttpXyzLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.image.EncodedRasterDecodeOptions, io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy, java.time.Duration, java.time.Duration, java.time.Duration, java.time.Duration);
    descriptor: (Lio/github/mundanej/map/io/http/tiles/HttpSchemePolicy;Lio/github/mundanej/map/io/http/tiles/HttpXyzLimits;Lio/github/mundanej/map/api/RasterSourceLimits;Lio/github/mundanej/map/io/image/EncodedRasterDecodeOptions;Lio/github/mundanej/map/io/http/tiles/HttpTileCachePolicy;Ljava/time/Duration;Ljava/time/Duration;Ljava/time/Duration;Ljava/time/Duration;)V
  public static io.github.mundanej.map.io.http.tiles.HttpXyzClientOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/http/tiles/HttpXyzClientOptions;
  public io.github.mundanej.map.io.http.tiles.HttpXyzClientOptions allowingHttp();
    descriptor: ()Lio/github/mundanej/map/io/http/tiles/HttpXyzClientOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.http.tiles.HttpSchemePolicy schemePolicy();
    descriptor: ()Lio/github/mundanej/map/io/http/tiles/HttpSchemePolicy;
  public io.github.mundanej.map.io.http.tiles.HttpXyzLimits limits();
    descriptor: ()Lio/github/mundanej/map/io/http/tiles/HttpXyzLimits;
  public io.github.mundanej.map.api.RasterSourceLimits snapshotLimits();
    descriptor: ()Lio/github/mundanej/map/api/RasterSourceLimits;
  public io.github.mundanej.map.io.image.EncodedRasterDecodeOptions decodeOptions();
    descriptor: ()Lio/github/mundanej/map/io/image/EncodedRasterDecodeOptions;
  public io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy cachePolicy();
    descriptor: ()Lio/github/mundanej/map/io/http/tiles/HttpTileCachePolicy;
  public java.time.Duration connectTimeout();
    descriptor: ()Ljava/time/Duration;
  public java.time.Duration requestTimeout();
    descriptor: ()Ljava/time/Duration;
  public java.time.Duration operationTimeout();
    descriptor: ()Ljava/time/Duration;
  public java.time.Duration closeTimeout();
    descriptor: ()Ljava/time/Duration;
}
public final class io.github.mundanej.map.io.http.tiles.HttpXyzLimits extends java.lang.Record {
  public io.github.mundanej.map.io.http.tiles.HttpXyzLimits(int, int, int, int, int, int, int, int, int, long, long, int, int, long);
    descriptor: (IIIIIIIIIJJIIJ)V
  public static io.github.mundanej.map.io.http.tiles.HttpXyzLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/http/tiles/HttpXyzLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int templateCharacters();
    descriptor: ()I
  public int zoom();
    descriptor: ()I
  public int tilesPerRequest();
    descriptor: ()I
  public int regionAxisTiles();
    descriptor: ()I
  public int concurrency();
    descriptor: ()I
  public int responseHeaders();
    descriptor: ()I
  public int headerNameOrValueCharacters();
    descriptor: ()I
  public int aggregateHeaderCharacters();
    descriptor: ()I
  public int responseBodyBytes();
    descriptor: ()I
  public long cumulativeResponseBytes();
    descriptor: ()J
  public long ownedBytes();
    descriptor: ()J
  public int warnings();
    descriptor: ()I
  public int cacheEntries();
    descriptor: ()I
  public long cacheBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.io.http.tiles.HttpXyzTemplate {
  public static io.github.mundanej.map.io.http.tiles.HttpXyzTemplate parse(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/io/http/tiles/HttpXyzTemplate;
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public interface io.github.mundanej.map.io.http.tiles.HttpXyzTileClient extends java.lang.AutoCloseable {
  public abstract io.github.mundanej.map.api.RasterSource fetch(io.github.mundanej.map.io.http.tiles.XyzTileRegion, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/io/http/tiles/XyzTileRegion;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterSource;
  public abstract boolean isClosed();
    descriptor: ()Z
  public abstract void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.io.http.tiles.HttpXyzTiles {
  public static io.github.mundanej.map.io.http.tiles.HttpXyzTileClient open(io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.http.tiles.HttpXyzTemplate, io.github.mundanej.map.io.http.tiles.HttpXyzClientOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/io/http/tiles/HttpXyzTemplate;Lio/github/mundanej/map/io/http/tiles/HttpXyzClientOptions;Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry;)Lio/github/mundanej/map/io/http/tiles/HttpXyzTileClient;
}
public final class io.github.mundanej.map.io.http.tiles.XyzTileRegion extends java.lang.Record {
  public io.github.mundanej.map.io.http.tiles.XyzTileRegion(int, int, int, int, int);
    descriptor: (IIIII)V
  public static io.github.mundanej.map.io.http.tiles.XyzTileRegion single(int, int, int);
    descriptor: (III)Lio/github/mundanej/map/io/http/tiles/XyzTileRegion;
  public static io.github.mundanej.map.io.http.tiles.XyzTileRegion covering(io.github.mundanej.map.api.Envelope, int);
    descriptor: (Lio/github/mundanej/map/api/Envelope;I)Lio/github/mundanej/map/io/http/tiles/XyzTileRegion;
  public long tileCount();
    descriptor: ()J
  public boolean isSingleTile();
    descriptor: ()Z
  public int widthInTiles();
    descriptor: ()I
  public int heightInTiles();
    descriptor: ()I
  public io.github.mundanej.map.api.Envelope bounds();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int zoom();
    descriptor: ()I
  public int minimumX();
    descriptor: ()I
  public int minimumY();
    descriptor: ()I
  public int maximumX();
    descriptor: ()I
  public int maximumY();
    descriptor: ()I
}
SHAPE io.github.mundanej.map.io.http.tiles.HttpSchemePolicy sealed=false permits=[] record=[] enum=[HTTPS_ONLY, HTTPS_OR_HTTP] annotations=[] members=[field:HTTPS_ONLY[], field:HTTPS_OR_HTTP[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy sealed=false permits=[] record=[] enum=[DISABLED, MEMORY] annotations=[] members=[field:DISABLED[], field:MEMORY[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.http.tiles.HttpXyzClientOptions sealed=false permits=[] record=[schemePolicy:io.github.mundanej.map.io.http.tiles.HttpSchemePolicy[], limits:io.github.mundanej.map.io.http.tiles.HttpXyzLimits[], snapshotLimits:io.github.mundanej.map.api.RasterSourceLimits[], decodeOptions:io.github.mundanej.map.io.image.EncodedRasterDecodeOptions[], cachePolicy:io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy[], connectTimeout:java.time.Duration[], requestTimeout:java.time.Duration[], operationTimeout:java.time.Duration[], closeTimeout:java.time.Duration[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.http.tiles.HttpSchemePolicy, io.github.mundanej.map.io.http.tiles.HttpXyzLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.image.EncodedRasterDecodeOptions, io.github.mundanej.map.io.http.tiles.HttpTileCachePolicy, java.time.Duration, java.time.Duration, java.time.Duration, java.time.Duration] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], []], method:allowingHttp[] throws=[] annotations=[] parameterAnnotations=[], method:cachePolicy[] throws=[] annotations=[] parameterAnnotations=[], method:closeTimeout[] throws=[] annotations=[] parameterAnnotations=[], method:connectTimeout[] throws=[] annotations=[] parameterAnnotations=[], method:decodeOptions[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:operationTimeout[] throws=[] annotations=[] parameterAnnotations=[], method:requestTimeout[] throws=[] annotations=[] parameterAnnotations=[], method:schemePolicy[] throws=[] annotations=[] parameterAnnotations=[], method:snapshotLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.http.tiles.HttpXyzLimits sealed=false permits=[] record=[templateCharacters:int[], zoom:int[], tilesPerRequest:int[], regionAxisTiles:int[], concurrency:int[], responseHeaders:int[], headerNameOrValueCharacters:int[], aggregateHeaderCharacters:int[], responseBodyBytes:int[], cumulativeResponseBytes:long[], ownedBytes:long[], warnings:int[], cacheEntries:int[], cacheBytes:long[]] enum=[] annotations=[] members=[constructor:[int, int, int, int, int, int, int, int, int, long, long, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], [], [], []], method:aggregateHeaderCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:cacheBytes[] throws=[] annotations=[] parameterAnnotations=[], method:cacheEntries[] throws=[] annotations=[] parameterAnnotations=[], method:concurrency[] throws=[] annotations=[] parameterAnnotations=[], method:cumulativeResponseBytes[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:headerNameOrValueCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:ownedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:regionAxisTiles[] throws=[] annotations=[] parameterAnnotations=[], method:responseBodyBytes[] throws=[] annotations=[] parameterAnnotations=[], method:responseHeaders[] throws=[] annotations=[] parameterAnnotations=[], method:templateCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:tilesPerRequest[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:warnings[] throws=[] annotations=[] parameterAnnotations=[], method:zoom[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.http.tiles.HttpXyzTemplate sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:parse[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.http.tiles.HttpXyzTileClient sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:close[] throws=[] annotations=[] parameterAnnotations=[], method:fetch[io.github.mundanej.map.io.http.tiles.XyzTileRegion, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.http.tiles.HttpXyzTiles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:open[io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.http.tiles.HttpXyzTemplate, io.github.mundanej.map.io.http.tiles.HttpXyzClientOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry] throws=[] annotations=[] parameterAnnotations=[[], [], [], []]]
SHAPE io.github.mundanej.map.io.http.tiles.XyzTileRegion sealed=false permits=[] record=[zoom:int[], minimumX:int[], minimumY:int[], maximumX:int[], maximumY:int[]] enum=[] annotations=[] members=[constructor:[int, int, int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:bounds[] throws=[] annotations=[] parameterAnnotations=[], method:covering[io.github.mundanej.map.api.Envelope, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:heightInTiles[] throws=[] annotations=[] parameterAnnotations=[], method:isSingleTile[] throws=[] annotations=[] parameterAnnotations=[], method:maximumX[] throws=[] annotations=[] parameterAnnotations=[], method:maximumY[] throws=[] annotations=[] parameterAnnotations=[], method:minimumX[] throws=[] annotations=[] parameterAnnotations=[], method:minimumY[] throws=[] annotations=[] parameterAnnotations=[], method:single[int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:tileCount[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:widthInTiles[] throws=[] annotations=[] parameterAnnotations=[], method:zoom[] throws=[] annotations=[] parameterAnnotations=[]]
