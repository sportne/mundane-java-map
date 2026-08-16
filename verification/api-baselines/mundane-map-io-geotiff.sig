public final class io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions extends java.lang.Record {
  public io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions(io.github.mundanej.map.api.ElevationUnit, io.github.mundanej.map.io.geotiff.GeoTiffLimits, io.github.mundanej.map.api.ElevationSourceLimits);
    descriptor: (Lio/github/mundanej/map/api/ElevationUnit;Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;Lio/github/mundanej/map/api/ElevationSourceLimits;)V
  public static io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions of(io.github.mundanej.map.api.ElevationUnit);
    descriptor: (Lio/github/mundanej/map/api/ElevationUnit;)Lio/github/mundanej/map/io/geotiff/GeoTiffElevationOptions;
  public io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions withFormatLimits(io.github.mundanej.map.io.geotiff.GeoTiffLimits);
    descriptor: (Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;)Lio/github/mundanej/map/io/geotiff/GeoTiffElevationOptions;
  public io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions withSourceLimits(io.github.mundanej.map.api.ElevationSourceLimits);
    descriptor: (Lio/github/mundanej/map/api/ElevationSourceLimits;)Lio/github/mundanej/map/io/geotiff/GeoTiffElevationOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.ElevationUnit elevationUnit();
    descriptor: ()Lio/github/mundanej/map/api/ElevationUnit;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits formatLimits();
    descriptor: ()Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.api.ElevationSourceLimits sourceLimits();
    descriptor: ()Lio/github/mundanej/map/api/ElevationSourceLimits;
}
public final class io.github.mundanej.map.io.geotiff.GeoTiffFiles {
  public static io.github.mundanej.map.api.RasterSource openRaster(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/io/geotiff/GeoTiffRasterOptions;)Lio/github/mundanej/map/api/RasterSource;
  public static io.github.mundanej.map.api.RasterSource openRaster(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/io/geotiff/GeoTiffRasterOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterSource;
  public static io.github.mundanej.map.api.RasterSource openRaster(io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;[BLio/github/mundanej/map/io/geotiff/GeoTiffRasterOptions;)Lio/github/mundanej/map/api/RasterSource;
  public static io.github.mundanej.map.api.RasterSource openRaster(io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;[BLio/github/mundanej/map/io/geotiff/GeoTiffRasterOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterSource;
  public static io.github.mundanej.map.api.ElevationSource openElevation(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/io/geotiff/GeoTiffElevationOptions;)Lio/github/mundanej/map/api/ElevationSource;
  public static io.github.mundanej.map.api.ElevationSource openElevation(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/io/geotiff/GeoTiffElevationOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/ElevationSource;
  public static io.github.mundanej.map.api.ElevationSource openElevation(io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;[BLio/github/mundanej/map/io/geotiff/GeoTiffElevationOptions;)Lio/github/mundanej/map/api/ElevationSource;
  public static io.github.mundanej.map.api.ElevationSource openElevation(io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;[BLio/github/mundanej/map/io/geotiff/GeoTiffElevationOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/ElevationSource;
}
public final class io.github.mundanej.map.io.geotiff.GeoTiffLimits extends java.lang.Record {
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits(long, int, long, int, int, int, long, long, long, int, int, long);
    descriptor: (JIJIIIJJJIIJ)V
  public static io.github.mundanej.map.io.geotiff.GeoTiffLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumInputBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumDimension(int);
    descriptor: (I)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumPixels(long);
    descriptor: (J)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumIfdEntries(int);
    descriptor: (I)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumGeoKeys(int);
    descriptor: (I)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumSegments(int);
    descriptor: (I)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumEncodedSegmentBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumDecodedSegmentBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumTagPayloadBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumGeoAsciiBytes(int);
    descriptor: (I)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumNoDataBytes(int);
    descriptor: (I)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits withMaximumWorkingBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long maximumInputBytes();
    descriptor: ()J
  public int maximumDimension();
    descriptor: ()I
  public long maximumPixels();
    descriptor: ()J
  public int maximumIfdEntries();
    descriptor: ()I
  public int maximumGeoKeys();
    descriptor: ()I
  public int maximumSegments();
    descriptor: ()I
  public long maximumEncodedSegmentBytes();
    descriptor: ()J
  public long maximumDecodedSegmentBytes();
    descriptor: ()J
  public long maximumTagPayloadBytes();
    descriptor: ()J
  public int maximumGeoAsciiBytes();
    descriptor: ()I
  public int maximumNoDataBytes();
    descriptor: ()I
  public long maximumWorkingBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions extends java.lang.Record {
  public io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions(io.github.mundanej.map.io.geotiff.GeoTiffLimits, io.github.mundanej.map.api.RasterSourceLimits);
    descriptor: (Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;Lio/github/mundanej/map/api/RasterSourceLimits;)V
  public static io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/geotiff/GeoTiffRasterOptions;
  public io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions withFormatLimits(io.github.mundanej.map.io.geotiff.GeoTiffLimits);
    descriptor: (Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;)Lio/github/mundanej/map/io/geotiff/GeoTiffRasterOptions;
  public io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions withRequestLimits(io.github.mundanej.map.api.RasterSourceLimits);
    descriptor: (Lio/github/mundanej/map/api/RasterSourceLimits;)Lio/github/mundanej/map/io/geotiff/GeoTiffRasterOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.geotiff.GeoTiffLimits formatLimits();
    descriptor: ()Lio/github/mundanej/map/io/geotiff/GeoTiffLimits;
  public io.github.mundanej.map.api.RasterSourceLimits requestLimits();
    descriptor: ()Lio/github/mundanej/map/api/RasterSourceLimits;
}
SHAPE io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions sealed=false permits=[] record=[elevationUnit:io.github.mundanej.map.api.ElevationUnit[], formatLimits:io.github.mundanej.map.io.geotiff.GeoTiffLimits[], sourceLimits:io.github.mundanej.map.api.ElevationSourceLimits[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.ElevationUnit, io.github.mundanej.map.io.geotiff.GeoTiffLimits, io.github.mundanej.map.api.ElevationSourceLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:elevationUnit[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:formatLimits[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:of[io.github.mundanej.map.api.ElevationUnit] throws=[] annotations=[] parameterAnnotations=[[]], method:sourceLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withFormatLimits[io.github.mundanej.map.io.geotiff.GeoTiffLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:withSourceLimits[io.github.mundanej.map.api.ElevationSourceLimits] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.geotiff.GeoTiffFiles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:openElevation[io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:openElevation[io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:openElevation[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:openElevation[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:openRaster[io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:openRaster[io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:openRaster[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:openRaster[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.io.geotiff.GeoTiffLimits sealed=false permits=[] record=[maximumInputBytes:long[], maximumDimension:int[], maximumPixels:long[], maximumIfdEntries:int[], maximumGeoKeys:int[], maximumSegments:int[], maximumEncodedSegmentBytes:long[], maximumDecodedSegmentBytes:long[], maximumTagPayloadBytes:long[], maximumGeoAsciiBytes:int[], maximumNoDataBytes:int[], maximumWorkingBytes:long[]] enum=[] annotations=[] members=[constructor:[long, int, long, int, int, int, long, long, long, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumDecodedSegmentBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumDimension[] throws=[] annotations=[] parameterAnnotations=[], method:maximumEncodedSegmentBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumGeoAsciiBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumGeoKeys[] throws=[] annotations=[] parameterAnnotations=[], method:maximumIfdEntries[] throws=[] annotations=[] parameterAnnotations=[], method:maximumInputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumNoDataBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPixels[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSegments[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTagPayloadBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumWorkingBytes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumDecodedSegmentBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumDimension[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumEncodedSegmentBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumGeoAsciiBytes[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumGeoKeys[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumIfdEntries[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumInputBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumNoDataBytes[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumPixels[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumSegments[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumTagPayloadBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumWorkingBytes[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions sealed=false permits=[] record=[formatLimits:io.github.mundanej.map.io.geotiff.GeoTiffLimits[], requestLimits:io.github.mundanej.map.api.RasterSourceLimits[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.geotiff.GeoTiffLimits, io.github.mundanej.map.api.RasterSourceLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:formatLimits[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:requestLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withFormatLimits[io.github.mundanej.map.io.geotiff.GeoTiffLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:withRequestLimits[io.github.mundanej.map.api.RasterSourceLimits] throws=[] annotations=[] parameterAnnotations=[[]]]
