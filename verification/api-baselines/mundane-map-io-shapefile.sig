public final class io.github.mundanej.map.io.shapefile.DbfEncoding extends java.lang.Enum<io.github.mundanej.map.io.shapefile.DbfEncoding> {
  public static final io.github.mundanej.map.io.shapefile.DbfEncoding UTF_8;
    descriptor: Lio/github/mundanej/map/io/shapefile/DbfEncoding;
  public static final io.github.mundanej.map.io.shapefile.DbfEncoding ISO_8859_1;
    descriptor: Lio/github/mundanej/map/io/shapefile/DbfEncoding;
  public static final io.github.mundanej.map.io.shapefile.DbfEncoding WINDOWS_1252;
    descriptor: Lio/github/mundanej/map/io/shapefile/DbfEncoding;
  public static final io.github.mundanej.map.io.shapefile.DbfEncoding IBM437;
    descriptor: Lio/github/mundanej/map/io/shapefile/DbfEncoding;
  public static final io.github.mundanej.map.io.shapefile.DbfEncoding IBM850;
    descriptor: Lio/github/mundanej/map/io/shapefile/DbfEncoding;
  public static io.github.mundanej.map.io.shapefile.DbfEncoding[] values();
    descriptor: ()[Lio/github/mundanej/map/io/shapefile/DbfEncoding;
  public static io.github.mundanej.map.io.shapefile.DbfEncoding valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/io/shapefile/DbfEncoding;
}
public interface io.github.mundanej.map.io.shapefile.ShapefileFileAccess$Channel extends java.lang.AutoCloseable {
  public abstract long size() throws java.io.IOException;
    descriptor: ()J
  public abstract int read(java.nio.ByteBuffer, long) throws java.io.IOException;
    descriptor: (Ljava/nio/ByteBuffer;J)I
  public abstract void close() throws java.io.IOException;
    descriptor: ()V
}
public final class io.github.mundanej.map.io.shapefile.ShapefileLimits {
  public static io.github.mundanej.map.io.shapefile.ShapefileLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public long maximumComponentBytes();
    descriptor: ()J
  public long maximumPhysicalRecords();
    descriptor: ()J
  public long maximumRecordBytes();
    descriptor: ()J
  public long maximumParts();
    descriptor: ()J
  public long maximumPoints();
    descriptor: ()J
  public long maximumTopologyComparisons();
    descriptor: ()J
  public long maximumDbfFields();
    descriptor: ()J
  public long maximumDbfFieldWidth();
    descriptor: ()J
  public long maximumCpgBytes();
    descriptor: ()J
  public long maximumPrjBytes();
    descriptor: ()J
  public long maximumDecodedTextCharacters();
    descriptor: ()J
  public long maximumParserAllocationBytes();
    descriptor: ()J
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumComponentBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumPhysicalRecords(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumRecordBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumParts(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumPoints(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumTopologyComparisons(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumDbfFields(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumDbfFieldWidth(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumCpgBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumPrjBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumDecodedTextCharacters(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits withMaximumParserAllocationBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.io.shapefile.ShapefileOpenOptions {
  public static io.github.mundanej.map.io.shapefile.ShapefileOpenOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/shapefile/ShapefileOpenOptions;
  public io.github.mundanej.map.api.FeatureSourceLimits featureSourceLimits();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSourceLimits;
  public io.github.mundanej.map.io.shapefile.ShapefileLimits shapefileLimits();
    descriptor: ()Lio/github/mundanej/map/io/shapefile/ShapefileLimits;
  public java.util.Optional<io.github.mundanej.map.api.CrsDefinition> crsOverride();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.io.shapefile.DbfEncoding> dbfEncodingOverride();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.io.shapefile.ShapefileOpenOptions withFeatureSourceLimits(io.github.mundanej.map.api.FeatureSourceLimits);
    descriptor: (Lio/github/mundanej/map/api/FeatureSourceLimits;)Lio/github/mundanej/map/io/shapefile/ShapefileOpenOptions;
  public io.github.mundanej.map.io.shapefile.ShapefileOpenOptions withShapefileLimits(io.github.mundanej.map.io.shapefile.ShapefileLimits);
    descriptor: (Lio/github/mundanej/map/io/shapefile/ShapefileLimits;)Lio/github/mundanej/map/io/shapefile/ShapefileOpenOptions;
  public io.github.mundanej.map.io.shapefile.ShapefileOpenOptions withCrsOverride(io.github.mundanej.map.api.CrsDefinition);
    descriptor: (Lio/github/mundanej/map/api/CrsDefinition;)Lio/github/mundanej/map/io/shapefile/ShapefileOpenOptions;
  public io.github.mundanej.map.io.shapefile.ShapefileOpenOptions withoutCrsOverride();
    descriptor: ()Lio/github/mundanej/map/io/shapefile/ShapefileOpenOptions;
  public io.github.mundanej.map.io.shapefile.ShapefileOpenOptions withDbfEncodingOverride(io.github.mundanej.map.io.shapefile.DbfEncoding);
    descriptor: (Lio/github/mundanej/map/io/shapefile/DbfEncoding;)Lio/github/mundanej/map/io/shapefile/ShapefileOpenOptions;
  public io.github.mundanej.map.io.shapefile.ShapefileOpenOptions withoutDbfEncodingOverride();
    descriptor: ()Lio/github/mundanej/map/io/shapefile/ShapefileOpenOptions;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.io.shapefile.Shapefiles {
  public static io.github.mundanej.map.api.FeatureSource open(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.shapefile.ShapefileOpenOptions);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/io/shapefile/ShapefileOpenOptions;)Lio/github/mundanej/map/api/FeatureSource;
  public static io.github.mundanej.map.api.FeatureSource open(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.shapefile.ShapefileOpenOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/io/shapefile/ShapefileOpenOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/FeatureSource;
}
SHAPE io.github.mundanej.map.io.shapefile.DbfEncoding sealed=false permits=[] record=[] enum=[UTF_8, ISO_8859_1, WINDOWS_1252, IBM437, IBM850] annotations=[] members=[field:IBM437[], field:IBM850[], field:ISO_8859_1[], field:UTF_8[], field:WINDOWS_1252[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.shapefile.ShapefileFileAccess$Channel sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:close[] throws=[java.io.IOException] annotations=[] parameterAnnotations=[], method:read[java.nio.ByteBuffer, long] throws=[java.io.IOException] annotations=[] parameterAnnotations=[[], []], method:size[] throws=[java.io.IOException] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.shapefile.ShapefileLimits sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumComponentBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCpgBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumDbfFieldWidth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumDbfFields[] throws=[] annotations=[] parameterAnnotations=[], method:maximumDecodedTextCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumParserAllocationBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumParts[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPhysicalRecords[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPoints[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPrjBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumRecordBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTopologyComparisons[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumComponentBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumCpgBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumDbfFieldWidth[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumDbfFields[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumDecodedTextCharacters[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumParserAllocationBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumParts[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumPhysicalRecords[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumPoints[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumPrjBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumRecordBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumTopologyComparisons[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.shapefile.ShapefileOpenOptions sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:crsOverride[] throws=[] annotations=[] parameterAnnotations=[], method:dbfEncodingOverride[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureSourceLimits[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:shapefileLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withCrsOverride[io.github.mundanej.map.api.CrsDefinition] throws=[] annotations=[] parameterAnnotations=[[]], method:withDbfEncodingOverride[io.github.mundanej.map.io.shapefile.DbfEncoding] throws=[] annotations=[] parameterAnnotations=[[]], method:withFeatureSourceLimits[io.github.mundanej.map.api.FeatureSourceLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:withShapefileLimits[io.github.mundanej.map.io.shapefile.ShapefileLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:withoutCrsOverride[] throws=[] annotations=[] parameterAnnotations=[], method:withoutDbfEncodingOverride[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.shapefile.Shapefiles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:open[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.shapefile.ShapefileOpenOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:open[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.shapefile.ShapefileOpenOptions] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
