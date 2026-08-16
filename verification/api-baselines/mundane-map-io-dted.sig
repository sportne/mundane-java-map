public final class io.github.mundanej.map.io.dted.DtedFiles {
  public static io.github.mundanej.map.api.ElevationSource open(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.dted.DtedOpenOptions);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/io/dted/DtedOpenOptions;)Lio/github/mundanej/map/api/ElevationSource;
  public static io.github.mundanej.map.api.ElevationSource open(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.dted.DtedOpenOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/io/dted/DtedOpenOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/ElevationSource;
}
public final class io.github.mundanej.map.io.dted.DtedLimits {
  public static io.github.mundanej.map.io.dted.DtedLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/dted/DtedLimits;
  public long maximumFileBytes();
    descriptor: ()J
  public io.github.mundanej.map.io.dted.DtedLimits withMaximumFileBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/dted/DtedLimits;
  public int maximumProfiles();
    descriptor: ()I
  public io.github.mundanej.map.io.dted.DtedLimits withMaximumProfiles(int);
    descriptor: (I)Lio/github/mundanej/map/io/dted/DtedLimits;
  public int maximumSamplesPerProfile();
    descriptor: ()I
  public io.github.mundanej.map.io.dted.DtedLimits withMaximumSamplesPerProfile(int);
    descriptor: (I)Lio/github/mundanej/map/io/dted/DtedLimits;
  public long maximumTotalSamples();
    descriptor: ()J
  public io.github.mundanej.map.io.dted.DtedLimits withMaximumTotalSamples(long);
    descriptor: (J)Lio/github/mundanej/map/io/dted/DtedLimits;
  public int maximumProfileBytes();
    descriptor: ()I
  public io.github.mundanej.map.io.dted.DtedLimits withMaximumProfileBytes(int);
    descriptor: (I)Lio/github/mundanej/map/io/dted/DtedLimits;
  public long maximumParserAllocationBytes();
    descriptor: ()J
  public io.github.mundanej.map.io.dted.DtedLimits withMaximumParserAllocationBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/dted/DtedLimits;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.io.dted.DtedOpenOptions {
  public static io.github.mundanej.map.io.dted.DtedOpenOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/dted/DtedOpenOptions;
  public io.github.mundanej.map.api.ElevationSourceLimits elevationSourceLimits();
    descriptor: ()Lio/github/mundanej/map/api/ElevationSourceLimits;
  public io.github.mundanej.map.io.dted.DtedOpenOptions withElevationSourceLimits(io.github.mundanej.map.api.ElevationSourceLimits);
    descriptor: (Lio/github/mundanej/map/api/ElevationSourceLimits;)Lio/github/mundanej/map/io/dted/DtedOpenOptions;
  public io.github.mundanej.map.io.dted.DtedLimits dtedLimits();
    descriptor: ()Lio/github/mundanej/map/io/dted/DtedLimits;
  public io.github.mundanej.map.io.dted.DtedOpenOptions withDtedLimits(io.github.mundanej.map.io.dted.DtedLimits);
    descriptor: (Lio/github/mundanej/map/io/dted/DtedLimits;)Lio/github/mundanej/map/io/dted/DtedOpenOptions;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
SHAPE io.github.mundanej.map.io.dted.DtedFiles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:open[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.dted.DtedOpenOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:open[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.io.dted.DtedOpenOptions] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.io.dted.DtedLimits sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumFileBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumParserAllocationBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumProfileBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumProfiles[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSamplesPerProfile[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTotalSamples[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumFileBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumParserAllocationBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumProfileBytes[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumProfiles[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumSamplesPerProfile[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumTotalSamples[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.dted.DtedOpenOptions sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:dtedLimits[] throws=[] annotations=[] parameterAnnotations=[], method:elevationSourceLimits[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withDtedLimits[io.github.mundanej.map.io.dted.DtedLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:withElevationSourceLimits[io.github.mundanej.map.api.ElevationSourceLimits] throws=[] annotations=[] parameterAnnotations=[[]]]
