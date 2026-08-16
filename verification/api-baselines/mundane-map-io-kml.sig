public final class io.github.mundanej.map.io.kml.KmlFiles {
  public static io.github.mundanej.map.api.FeatureSource open(java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.kml.KmlOpenOptions, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/io/kml/KmlOpenOptions;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/FeatureSource;
}
public final class io.github.mundanej.map.io.kml.KmlLimits extends java.lang.Record {
  public io.github.mundanej.map.io.kml.KmlLimits(int, int, int, int, int, int, int, int, int, int, int, int, int, int, long, int);
    descriptor: (IIIIIIIIIIIIIIJI)V
  public static io.github.mundanej.map.io.kml.KmlLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/kml/KmlLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumInputBytes();
    descriptor: ()I
  public int maximumXmlDepth();
    descriptor: ()I
  public int maximumXmlEvents();
    descriptor: ()I
  public int maximumElements();
    descriptor: ()I
  public int maximumAttributes();
    descriptor: ()I
  public int maximumNamespaceDeclarations();
    descriptor: ()I
  public int maximumFeatureDepth();
    descriptor: ()I
  public int maximumPhysicalFeatures();
    descriptor: ()I
  public int maximumTotalCoordinates();
    descriptor: ()I
  public int maximumCoordinatesPerGeometry();
    descriptor: ()I
  public int maximumParts();
    descriptor: ()I
  public int maximumScalarCharacters();
    descriptor: ()I
  public int maximumTextCharacters();
    descriptor: ()I
  public int maximumNumberCharacters();
    descriptor: ()I
  public long maximumOwnedBytes();
    descriptor: ()J
  public int retainedWarnings();
    descriptor: ()I
}
public final class io.github.mundanej.map.io.kml.KmlOpenOptions extends java.lang.Record {
  public io.github.mundanej.map.io.kml.KmlOpenOptions(io.github.mundanej.map.io.kml.KmlLimits, io.github.mundanej.map.api.FeatureSourceLimits);
    descriptor: (Lio/github/mundanej/map/io/kml/KmlLimits;Lio/github/mundanej/map/api/FeatureSourceLimits;)V
  public static io.github.mundanej.map.io.kml.KmlOpenOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/kml/KmlOpenOptions;
  public io.github.mundanej.map.io.kml.KmlOpenOptions withFormatLimits(io.github.mundanej.map.io.kml.KmlLimits);
    descriptor: (Lio/github/mundanej/map/io/kml/KmlLimits;)Lio/github/mundanej/map/io/kml/KmlOpenOptions;
  public io.github.mundanej.map.io.kml.KmlOpenOptions withSourceLimits(io.github.mundanej.map.api.FeatureSourceLimits);
    descriptor: (Lio/github/mundanej/map/api/FeatureSourceLimits;)Lio/github/mundanej/map/io/kml/KmlOpenOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.kml.KmlLimits formatLimits();
    descriptor: ()Lio/github/mundanej/map/io/kml/KmlLimits;
  public io.github.mundanej.map.api.FeatureSourceLimits sourceLimits();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSourceLimits;
}
SHAPE io.github.mundanej.map.io.kml.KmlFiles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:open[java.nio.file.Path, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.io.kml.KmlOpenOptions, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []]]
SHAPE io.github.mundanej.map.io.kml.KmlLimits sealed=false permits=[] record=[maximumInputBytes:int[], maximumXmlDepth:int[], maximumXmlEvents:int[], maximumElements:int[], maximumAttributes:int[], maximumNamespaceDeclarations:int[], maximumFeatureDepth:int[], maximumPhysicalFeatures:int[], maximumTotalCoordinates:int[], maximumCoordinatesPerGeometry:int[], maximumParts:int[], maximumScalarCharacters:int[], maximumTextCharacters:int[], maximumNumberCharacters:int[], maximumOwnedBytes:long[], retainedWarnings:int[]] enum=[] annotations=[] members=[constructor:[int, int, int, int, int, int, int, int, int, int, int, int, int, int, long, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], [], [], [], [], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAttributes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCoordinatesPerGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:maximumElements[] throws=[] annotations=[] parameterAnnotations=[], method:maximumFeatureDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumInputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumNamespaceDeclarations[] throws=[] annotations=[] parameterAnnotations=[], method:maximumNumberCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumParts[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPhysicalFeatures[] throws=[] annotations=[] parameterAnnotations=[], method:maximumScalarCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTextCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTotalCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maximumXmlDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumXmlEvents[] throws=[] annotations=[] parameterAnnotations=[], method:retainedWarnings[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.kml.KmlOpenOptions sealed=false permits=[] record=[formatLimits:io.github.mundanej.map.io.kml.KmlLimits[], sourceLimits:io.github.mundanej.map.api.FeatureSourceLimits[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.kml.KmlLimits, io.github.mundanej.map.api.FeatureSourceLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:formatLimits[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:sourceLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withFormatLimits[io.github.mundanej.map.io.kml.KmlLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:withSourceLimits[io.github.mundanej.map.api.FeatureSourceLimits] throws=[] annotations=[] parameterAnnotations=[[]]]
