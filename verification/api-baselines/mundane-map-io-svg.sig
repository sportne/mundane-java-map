public final class io.github.mundanej.map.io.svg.SvgExportException extends java.lang.RuntimeException {
  public io.github.mundanej.map.io.svg.SvgExportException(java.lang.String, io.github.mundanej.map.io.svg.SvgExportProblem, java.lang.Throwable);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/io/svg/SvgExportProblem;Ljava/lang/Throwable;)V
  public io.github.mundanej.map.io.svg.SvgExportException(java.lang.String, io.github.mundanej.map.io.svg.SvgExportProblem);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/io/svg/SvgExportProblem;)V
  public io.github.mundanej.map.io.svg.SvgExportProblem problem();
    descriptor: ()Lio/github/mundanej/map/io/svg/SvgExportProblem;
}
public final class io.github.mundanej.map.io.svg.SvgExportLimits extends java.lang.Record {
  public static final int ELEMENTS_HARD_MAXIMUM = 1000000;
    descriptor: I
  public static final int PATH_COMMANDS_HARD_MAXIMUM = 10000000;
    descriptor: I
  public static final int HATCH_SEGMENTS_HARD_MAXIMUM = 1000000;
    descriptor: I
  public static final int OUTPUT_BYTES_HARD_MAXIMUM = 67108864;
    descriptor: I
  public static final long OWNED_BYTES_HARD_MAXIMUM = 268435456l;
    descriptor: J
  public io.github.mundanej.map.io.svg.SvgExportLimits(int, int, int, int, long);
    descriptor: (IIIIJ)V
  public static io.github.mundanej.map.io.svg.SvgExportLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/svg/SvgExportLimits;
  public io.github.mundanej.map.io.svg.SvgExportLimits withMaximumElements(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgExportLimits;
  public io.github.mundanej.map.io.svg.SvgExportLimits withMaximumPathCommands(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgExportLimits;
  public io.github.mundanej.map.io.svg.SvgExportLimits withMaximumHatchSegments(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgExportLimits;
  public io.github.mundanej.map.io.svg.SvgExportLimits withMaximumOutputBytes(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgExportLimits;
  public io.github.mundanej.map.io.svg.SvgExportLimits withMaximumOwnedBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/svg/SvgExportLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumElements();
    descriptor: ()I
  public int maximumPathCommands();
    descriptor: ()I
  public int maximumHatchSegments();
    descriptor: ()I
  public int maximumOutputBytes();
    descriptor: ()I
  public long maximumOwnedBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.io.svg.SvgExportProblem extends java.lang.Record {
  public io.github.mundanej.map.io.svg.SvgExportProblem(java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
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
public final class io.github.mundanej.map.io.svg.SvgImportLimits extends java.lang.Record {
  public io.github.mundanej.map.io.svg.SvgImportLimits(int, int, int, int, int, int, int, int, int, int, int, int, long);
    descriptor: (IIIIIIIIIIIIJ)V
  public static io.github.mundanej.map.io.svg.SvgImportLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumInputBytes(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumElements(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumElementDepth(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumAttributes(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumAttributeCharacters(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumAggregateAttributeCharacters(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumNumberTokenCharacters(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumExpandedCommands(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumDrawingSegments(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumTransformFunctions(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumTransformAncestorDepth(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumPaintedOutputPaths(int);
    descriptor: (I)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public io.github.mundanej.map.io.svg.SvgImportLimits withMaximumOwnedBytes(long);
    descriptor: (J)Lio/github/mundanej/map/io/svg/SvgImportLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumInputBytes();
    descriptor: ()I
  public int maximumElements();
    descriptor: ()I
  public int maximumElementDepth();
    descriptor: ()I
  public int maximumAttributes();
    descriptor: ()I
  public int maximumAttributeCharacters();
    descriptor: ()I
  public int maximumAggregateAttributeCharacters();
    descriptor: ()I
  public int maximumNumberTokenCharacters();
    descriptor: ()I
  public int maximumExpandedCommands();
    descriptor: ()I
  public int maximumDrawingSegments();
    descriptor: ()I
  public int maximumTransformFunctions();
    descriptor: ()I
  public int maximumTransformAncestorDepth();
    descriptor: ()I
  public int maximumPaintedOutputPaths();
    descriptor: ()I
  public long maximumOwnedBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.io.svg.SvgMapExports {
  public static byte[] encode(io.github.mundanej.map.api.VectorExportSnapshot);
    descriptor: (Lio/github/mundanej/map/api/VectorExportSnapshot;)[B
  public static byte[] encode(io.github.mundanej.map.api.VectorExportSnapshot, io.github.mundanej.map.io.svg.SvgExportLimits);
    descriptor: (Lio/github/mundanej/map/api/VectorExportSnapshot;Lio/github/mundanej/map/io/svg/SvgExportLimits;)[B
  public static byte[] encode(io.github.mundanej.map.api.VectorExportSnapshot, io.github.mundanej.map.io.svg.SvgExportLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/VectorExportSnapshot;Lio/github/mundanej/map/io/svg/SvgExportLimits;Lio/github/mundanej/map/api/CancellationToken;)[B
  public static void writeAtomically(java.nio.file.Path, io.github.mundanej.map.api.VectorExportSnapshot);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/VectorExportSnapshot;)V
  public static void writeAtomically(java.nio.file.Path, io.github.mundanej.map.api.VectorExportSnapshot, io.github.mundanej.map.io.svg.SvgExportLimits);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/VectorExportSnapshot;Lio/github/mundanej/map/io/svg/SvgExportLimits;)V
  public static void writeAtomically(java.nio.file.Path, io.github.mundanej.map.api.VectorExportSnapshot, io.github.mundanej.map.io.svg.SvgExportLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/VectorExportSnapshot;Lio/github/mundanej/map/io/svg/SvgExportLimits;Lio/github/mundanej/map/api/CancellationToken;)V
}
public final class io.github.mundanej.map.io.svg.SvgSymbols {
  public static io.github.mundanej.map.api.Symbol read(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.api.MarkerPlacement);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/api/MarkerPlacement;)Lio/github/mundanej/map/api/Symbol;
  public static io.github.mundanej.map.api.Symbol read(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.io.svg.SvgImportLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/io/svg/SvgImportLimits;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/Symbol;
  public static io.github.mundanej.map.api.Symbol parse(io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.api.MarkerPlacement);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;[BLio/github/mundanej/map/api/MarkerPlacement;)Lio/github/mundanej/map/api/Symbol;
  public static io.github.mundanej.map.api.Symbol parse(io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.io.svg.SvgImportLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;[BLio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/io/svg/SvgImportLimits;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/Symbol;
}
SHAPE io.github.mundanej.map.io.svg.SvgExportException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.io.svg.SvgExportProblem, java.lang.Throwable] throws=[] annotations=[] parameterAnnotations=[[], [], []], constructor:[java.lang.String, io.github.mundanej.map.io.svg.SvgExportProblem] throws=[] annotations=[] parameterAnnotations=[[], []], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.svg.SvgExportLimits sealed=false permits=[] record=[maximumElements:int[], maximumPathCommands:int[], maximumHatchSegments:int[], maximumOutputBytes:int[], maximumOwnedBytes:long[]] enum=[] annotations=[] members=[constructor:[int, int, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], field:ELEMENTS_HARD_MAXIMUM[], field:HATCH_SEGMENTS_HARD_MAXIMUM[], field:OUTPUT_BYTES_HARD_MAXIMUM[], field:OWNED_BYTES_HARD_MAXIMUM[], field:PATH_COMMANDS_HARD_MAXIMUM[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumElements[] throws=[] annotations=[] parameterAnnotations=[], method:maximumHatchSegments[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOutputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPathCommands[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumElements[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumHatchSegments[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumOutputBytes[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumOwnedBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumPathCommands[int] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.svg.SvgExportProblem sealed=false permits=[] record=[code:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.svg.SvgImportLimits sealed=false permits=[] record=[maximumInputBytes:int[], maximumElements:int[], maximumElementDepth:int[], maximumAttributes:int[], maximumAttributeCharacters:int[], maximumAggregateAttributeCharacters:int[], maximumNumberTokenCharacters:int[], maximumExpandedCommands:int[], maximumDrawingSegments:int[], maximumTransformFunctions:int[], maximumTransformAncestorDepth:int[], maximumPaintedOutputPaths:int[], maximumOwnedBytes:long[]] enum=[] annotations=[] members=[constructor:[int, int, int, int, int, int, int, int, int, int, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], [], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAggregateAttributeCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAttributeCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAttributes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumDrawingSegments[] throws=[] annotations=[] parameterAnnotations=[], method:maximumElementDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumElements[] throws=[] annotations=[] parameterAnnotations=[], method:maximumExpandedCommands[] throws=[] annotations=[] parameterAnnotations=[], method:maximumInputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumNumberTokenCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPaintedOutputPaths[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTransformAncestorDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTransformFunctions[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumAggregateAttributeCharacters[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumAttributeCharacters[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumAttributes[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumDrawingSegments[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumElementDepth[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumElements[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumExpandedCommands[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumInputBytes[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumNumberTokenCharacters[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumOwnedBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumPaintedOutputPaths[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumTransformAncestorDepth[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumTransformFunctions[int] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.svg.SvgMapExports sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:encode[io.github.mundanej.map.api.VectorExportSnapshot, io.github.mundanej.map.io.svg.SvgExportLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:encode[io.github.mundanej.map.api.VectorExportSnapshot, io.github.mundanej.map.io.svg.SvgExportLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:encode[io.github.mundanej.map.api.VectorExportSnapshot] throws=[] annotations=[] parameterAnnotations=[[]], method:writeAtomically[java.nio.file.Path, io.github.mundanej.map.api.VectorExportSnapshot, io.github.mundanej.map.io.svg.SvgExportLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:writeAtomically[java.nio.file.Path, io.github.mundanej.map.api.VectorExportSnapshot, io.github.mundanej.map.io.svg.SvgExportLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:writeAtomically[java.nio.file.Path, io.github.mundanej.map.api.VectorExportSnapshot] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.io.svg.SvgSymbols sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:parse[io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.io.svg.SvgImportLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:parse[io.github.mundanej.map.api.SourceIdentity, byte[], io.github.mundanej.map.api.MarkerPlacement] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:read[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.io.svg.SvgImportLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:read[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.api.MarkerPlacement] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
