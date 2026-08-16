public final class io.github.mundanej.map.io.image.EncodedRasterDecodeOptions extends java.lang.Record {
  public io.github.mundanej.map.io.image.EncodedRasterDecodeOptions(java.util.Optional<io.github.mundanej.map.api.EncodedRasterFormat>, java.util.OptionalInt, java.util.OptionalInt, io.github.mundanej.map.io.image.ImageSourceLimits, io.github.mundanej.map.api.RasterRequestLimits);
    descriptor: (Ljava/util/Optional;Ljava/util/OptionalInt;Ljava/util/OptionalInt;Lio/github/mundanej/map/io/image/ImageSourceLimits;Lio/github/mundanej/map/api/RasterRequestLimits;)V
  public static io.github.mundanej.map.io.image.EncodedRasterDecodeOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/image/EncodedRasterDecodeOptions;
  public io.github.mundanej.map.io.image.EncodedRasterDecodeOptions expecting(io.github.mundanej.map.api.EncodedRasterFormat);
    descriptor: (Lio/github/mundanej/map/api/EncodedRasterFormat;)Lio/github/mundanej/map/io/image/EncodedRasterDecodeOptions;
  public io.github.mundanej.map.io.image.EncodedRasterDecodeOptions expectingDimensions(int, int);
    descriptor: (II)Lio/github/mundanej/map/io/image/EncodedRasterDecodeOptions;
  public io.github.mundanej.map.io.image.EncodedRasterDecodeOptions withImageLimits(io.github.mundanej.map.io.image.ImageSourceLimits);
    descriptor: (Lio/github/mundanej/map/io/image/ImageSourceLimits;)Lio/github/mundanej/map/io/image/EncodedRasterDecodeOptions;
  public io.github.mundanej.map.io.image.EncodedRasterDecodeOptions withDecodeLimits(io.github.mundanej.map.api.RasterRequestLimits);
    descriptor: (Lio/github/mundanej/map/api/RasterRequestLimits;)Lio/github/mundanej/map/io/image/EncodedRasterDecodeOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<io.github.mundanej.map.api.EncodedRasterFormat> expectedFormat();
    descriptor: ()Ljava/util/Optional;
  public java.util.OptionalInt expectedWidth();
    descriptor: ()Ljava/util/OptionalInt;
  public java.util.OptionalInt expectedHeight();
    descriptor: ()Ljava/util/OptionalInt;
  public io.github.mundanej.map.io.image.ImageSourceLimits imageLimits();
    descriptor: ()Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.api.RasterRequestLimits decodeLimits();
    descriptor: ()Lio/github/mundanej/map/api/RasterRequestLimits;
}
public final class io.github.mundanej.map.io.image.ImageCachePolicy {
  public static io.github.mundanej.map.io.image.ImageCachePolicy disabled();
    descriptor: ()Lio/github/mundanej/map/io/image/ImageCachePolicy;
  public static io.github.mundanej.map.io.image.ImageCachePolicy bounded(int, long);
    descriptor: (IJ)Lio/github/mundanej/map/io/image/ImageCachePolicy;
  public static io.github.mundanej.map.io.image.ImageCachePolicy defaults();
    descriptor: ()Lio/github/mundanej/map/io/image/ImageCachePolicy;
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
public interface io.github.mundanej.map.io.image.ImageChannel$Opener {
  public abstract io.github.mundanej.map.io.image.ImageChannel open(java.nio.file.Path) throws java.io.IOException;
    descriptor: (Ljava/nio/file/Path;)Lio/github/mundanej/map/io/image/ImageChannel;
}
public final class io.github.mundanej.map.io.image.ImageOpenOptions extends java.lang.Record {
  public io.github.mundanej.map.io.image.ImageOpenOptions(io.github.mundanej.map.io.image.ImageSourceLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.image.ImagePlacement);
    descriptor: (Lio/github/mundanej/map/io/image/ImageSourceLimits;Lio/github/mundanej/map/api/RasterSourceLimits;Lio/github/mundanej/map/io/image/ImagePlacement;)V
  public io.github.mundanej.map.io.image.ImageOpenOptions(io.github.mundanej.map.io.image.ImageSourceLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.image.ImagePlacement, io.github.mundanej.map.io.image.ImageCachePolicy);
    descriptor: (Lio/github/mundanej/map/io/image/ImageSourceLimits;Lio/github/mundanej/map/api/RasterSourceLimits;Lio/github/mundanej/map/io/image/ImagePlacement;Lio/github/mundanej/map/io/image/ImageCachePolicy;)V
  public static io.github.mundanej.map.io.image.ImageOpenOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/image/ImageOpenOptions;
  public io.github.mundanej.map.io.image.ImageOpenOptions withImageLimits(io.github.mundanej.map.io.image.ImageSourceLimits);
    descriptor: (Lio/github/mundanej/map/io/image/ImageSourceLimits;)Lio/github/mundanej/map/io/image/ImageOpenOptions;
  public io.github.mundanej.map.io.image.ImageOpenOptions withRequestLimits(io.github.mundanej.map.api.RasterSourceLimits);
    descriptor: (Lio/github/mundanej/map/api/RasterSourceLimits;)Lio/github/mundanej/map/io/image/ImageOpenOptions;
  public io.github.mundanej.map.io.image.ImageOpenOptions withPlacement(io.github.mundanej.map.io.image.ImagePlacement);
    descriptor: (Lio/github/mundanej/map/io/image/ImagePlacement;)Lio/github/mundanej/map/io/image/ImageOpenOptions;
  public io.github.mundanej.map.io.image.ImageOpenOptions withCachePolicy(io.github.mundanej.map.io.image.ImageCachePolicy);
    descriptor: (Lio/github/mundanej/map/io/image/ImageCachePolicy;)Lio/github/mundanej/map/io/image/ImageOpenOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.image.ImageSourceLimits imageLimits();
    descriptor: ()Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.api.RasterSourceLimits requestLimits();
    descriptor: ()Lio/github/mundanej/map/api/RasterSourceLimits;
  public io.github.mundanej.map.io.image.ImagePlacement placement();
    descriptor: ()Lio/github/mundanej/map/io/image/ImagePlacement;
  public io.github.mundanej.map.io.image.ImageCachePolicy cachePolicy();
    descriptor: ()Lio/github/mundanej/map/io/image/ImageCachePolicy;
}
public final class io.github.mundanej.map.io.image.ImagePlacement {
  public io.github.mundanej.map.io.image.ImagePlacement(java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>);
    descriptor: (Ljava/util/Optional;Ljava/util/Optional;)V
  public static io.github.mundanej.map.io.image.ImagePlacement unplaced();
    descriptor: ()Lio/github/mundanej/map/io/image/ImagePlacement;
  public static io.github.mundanej.map.io.image.ImagePlacement axisAligned(io.github.mundanej.map.api.Envelope, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>);
    descriptor: (Lio/github/mundanej/map/api/Envelope;Ljava/util/Optional;)Lio/github/mundanej/map/io/image/ImagePlacement;
  public static io.github.mundanej.map.io.image.ImagePlacement axisAligned(io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.CrsMetadata);
    descriptor: (Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/CrsMetadata;)Lio/github/mundanej/map/io/image/ImagePlacement;
  public static io.github.mundanej.map.io.image.ImagePlacement worldFile();
    descriptor: ()Lio/github/mundanej/map/io/image/ImagePlacement;
  public static io.github.mundanej.map.io.image.ImagePlacement worldFile(java.util.Optional<io.github.mundanej.map.api.CrsMetadata>);
    descriptor: (Ljava/util/Optional;)Lio/github/mundanej/map/io/image/ImagePlacement;
  public static io.github.mundanej.map.io.image.ImagePlacement worldFile(io.github.mundanej.map.api.CrsMetadata);
    descriptor: (Lio/github/mundanej/map/api/CrsMetadata;)Lio/github/mundanej/map/io/image/ImagePlacement;
  public io.github.mundanej.map.io.image.ImagePlacement$Kind kind();
    descriptor: ()Lio/github/mundanej/map/io/image/ImagePlacement$Kind;
  public java.util.Optional<io.github.mundanej.map.api.Envelope> mapBounds();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.CrsMetadata> crs();
    descriptor: ()Ljava/util/Optional;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.io.image.ImagePlacement$Kind extends java.lang.Enum<io.github.mundanej.map.io.image.ImagePlacement$Kind> {
  public static final io.github.mundanej.map.io.image.ImagePlacement$Kind UNPLACED;
    descriptor: Lio/github/mundanej/map/io/image/ImagePlacement$Kind;
  public static final io.github.mundanej.map.io.image.ImagePlacement$Kind AXIS_ALIGNED;
    descriptor: Lio/github/mundanej/map/io/image/ImagePlacement$Kind;
  public static final io.github.mundanej.map.io.image.ImagePlacement$Kind WORLD_FILE;
    descriptor: Lio/github/mundanej/map/io/image/ImagePlacement$Kind;
  public static io.github.mundanej.map.io.image.ImagePlacement$Kind[] values();
    descriptor: ()[Lio/github/mundanej/map/io/image/ImagePlacement$Kind;
  public static io.github.mundanej.map.io.image.ImagePlacement$Kind valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/io/image/ImagePlacement$Kind;
}
public final class io.github.mundanej.map.io.image.ImageSourceLimits extends java.lang.Record {
  public io.github.mundanej.map.io.image.ImageSourceLimits(long, long, int, int, long, int);
    descriptor: (JJIIJI)V
  public io.github.mundanej.map.io.image.ImageSourceLimits(long, long, int, int, long, int, long, int);
    descriptor: (JJIIJIJI)V
  public io.github.mundanej.map.io.image.ImageSourceLimits(long, long, int, int, long, int, long, int, long, long);
    descriptor: (JJIIJIJIJJ)V
  public static io.github.mundanej.map.io.image.ImageSourceLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumEncodedBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumHeaderBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumWidth(int);
    descriptor: (I)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumHeight(int);
    descriptor: (I)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumPixels(long);
    descriptor: (J)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumLogicalChannels(int);
    descriptor: (I)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumWorldFileBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumWorldFileLineBytes(int);
    descriptor: (I)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumContainerElements(long);
    descriptor: (J)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public io.github.mundanej.map.io.image.ImageSourceLimits withMaximumInflatedRasterBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/image/ImageSourceLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long maximumEncodedBytes();
    descriptor: ()J
  public long maximumHeaderBytes();
    descriptor: ()J
  public int maximumWidth();
    descriptor: ()I
  public int maximumHeight();
    descriptor: ()I
  public long maximumPixels();
    descriptor: ()J
  public int maximumLogicalChannels();
    descriptor: ()I
  public long maximumWorldFileBytes();
    descriptor: ()J
  public int maximumWorldFileLineBytes();
    descriptor: ()I
  public long maximumContainerElements();
    descriptor: ()J
  public long maximumInflatedRasterBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.io.image.RasterImages {
  public static io.github.mundanej.map.api.RgbaPixelBuffer decode(byte[], io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.image.EncodedRasterDecodeOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry, io.github.mundanej.map.api.CancellationToken);
    descriptor: ([BLio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/io/image/EncodedRasterDecodeOptions;Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RgbaPixelBuffer;
  public static io.github.mundanej.map.api.RasterSource open(java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.image.ImageOpenOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/io/image/ImageOpenOptions;Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry;)Lio/github/mundanej/map/api/RasterSource;
  public static io.github.mundanej.map.api.RasterSource open(java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.image.ImageOpenOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/io/image/ImageOpenOptions;Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterSource;
}
SHAPE io.github.mundanej.map.io.image.EncodedRasterDecodeOptions sealed=false permits=[] record=[expectedFormat:java.util.Optional<io.github.mundanej.map.api.EncodedRasterFormat>[], expectedWidth:java.util.OptionalInt[], expectedHeight:java.util.OptionalInt[], imageLimits:io.github.mundanej.map.io.image.ImageSourceLimits[], decodeLimits:io.github.mundanej.map.api.RasterRequestLimits[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<io.github.mundanej.map.api.EncodedRasterFormat>, java.util.OptionalInt, java.util.OptionalInt, io.github.mundanej.map.io.image.ImageSourceLimits, io.github.mundanej.map.api.RasterRequestLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:decodeLimits[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:expectedFormat[] throws=[] annotations=[] parameterAnnotations=[], method:expectedHeight[] throws=[] annotations=[] parameterAnnotations=[], method:expectedWidth[] throws=[] annotations=[] parameterAnnotations=[], method:expectingDimensions[int, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:expecting[io.github.mundanej.map.api.EncodedRasterFormat] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:imageLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withDecodeLimits[io.github.mundanej.map.api.RasterRequestLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:withImageLimits[io.github.mundanej.map.io.image.ImageSourceLimits] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.image.ImageCachePolicy sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:bounded[int, long] throws=[] annotations=[] parameterAnnotations=[[], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:disabled[] throws=[] annotations=[] parameterAnnotations=[], method:enabled[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumEntries[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPixelBytes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.image.ImageChannel$Opener sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:open[java.nio.file.Path] throws=[java.io.IOException] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.image.ImageOpenOptions sealed=false permits=[] record=[imageLimits:io.github.mundanej.map.io.image.ImageSourceLimits[], requestLimits:io.github.mundanej.map.api.RasterSourceLimits[], placement:io.github.mundanej.map.io.image.ImagePlacement[], cachePolicy:io.github.mundanej.map.io.image.ImageCachePolicy[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.image.ImageSourceLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.image.ImagePlacement, io.github.mundanej.map.io.image.ImageCachePolicy] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], constructor:[io.github.mundanej.map.io.image.ImageSourceLimits, io.github.mundanej.map.api.RasterSourceLimits, io.github.mundanej.map.io.image.ImagePlacement] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:cachePolicy[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:imageLimits[] throws=[] annotations=[] parameterAnnotations=[], method:placement[] throws=[] annotations=[] parameterAnnotations=[], method:requestLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withCachePolicy[io.github.mundanej.map.io.image.ImageCachePolicy] throws=[] annotations=[] parameterAnnotations=[[]], method:withImageLimits[io.github.mundanej.map.io.image.ImageSourceLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:withPlacement[io.github.mundanej.map.io.image.ImagePlacement] throws=[] annotations=[] parameterAnnotations=[[]], method:withRequestLimits[io.github.mundanej.map.api.RasterSourceLimits] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.image.ImagePlacement sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>] throws=[] annotations=[] parameterAnnotations=[[], []], method:axisAligned[io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.CrsMetadata] throws=[] annotations=[] parameterAnnotations=[[], []], method:axisAligned[io.github.mundanej.map.api.Envelope, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>] throws=[] annotations=[] parameterAnnotations=[[], []], method:crs[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:kind[] throws=[] annotations=[] parameterAnnotations=[], method:mapBounds[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unplaced[] throws=[] annotations=[] parameterAnnotations=[], method:worldFile[] throws=[] annotations=[] parameterAnnotations=[], method:worldFile[io.github.mundanej.map.api.CrsMetadata] throws=[] annotations=[] parameterAnnotations=[[]], method:worldFile[java.util.Optional<io.github.mundanej.map.api.CrsMetadata>] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.image.ImagePlacement$Kind sealed=false permits=[] record=[] enum=[UNPLACED, AXIS_ALIGNED, WORLD_FILE] annotations=[] members=[field:AXIS_ALIGNED[], field:UNPLACED[], field:WORLD_FILE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.image.ImageSourceLimits sealed=false permits=[] record=[maximumEncodedBytes:long[], maximumHeaderBytes:long[], maximumWidth:int[], maximumHeight:int[], maximumPixels:long[], maximumLogicalChannels:int[], maximumWorldFileBytes:long[], maximumWorldFileLineBytes:int[], maximumContainerElements:long[], maximumInflatedRasterBytes:long[]] enum=[] annotations=[] members=[constructor:[long, long, int, int, long, int, long, int, long, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], []], constructor:[long, long, int, int, long, int, long, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], constructor:[long, long, int, int, long, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumContainerElements[] throws=[] annotations=[] parameterAnnotations=[], method:maximumEncodedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumHeaderBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumHeight[] throws=[] annotations=[] parameterAnnotations=[], method:maximumInflatedRasterBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumLogicalChannels[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPixels[] throws=[] annotations=[] parameterAnnotations=[], method:maximumWidth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumWorldFileBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumWorldFileLineBytes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumContainerElements[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumEncodedBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumHeaderBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumHeight[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumInflatedRasterBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumLogicalChannels[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumPixels[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumWidth[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumWorldFileBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumWorldFileLineBytes[int] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.image.RasterImages sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:decode[byte[], io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.image.EncodedRasterDecodeOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:open[java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.image.ImageOpenOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:open[java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.image.ImageOpenOptions, io.github.mundanej.map.api.EncodedRasterDecoderRegistry] throws=[] annotations=[] parameterAnnotations=[[], [], [], []]]
