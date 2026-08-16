public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolAssessment extends java.lang.Record {
  public io.github.mundanej.map.symbology.milstd2525.MilitarySymbolAssessment(io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport, java.util.Optional<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem>);
    descriptor: (Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolSupport;Ljava/util/Optional;)V
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolAssessment supported();
    descriptor: ()Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolAssessment;
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolAssessment problem(io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem);
    descriptor: (Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolSupport;Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolProblem;)Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolAssessment;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport support();
    descriptor: ()Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolSupport;
  public java.util.Optional<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem> problem();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalog {
  public static java.util.List<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalogEntry> entries();
    descriptor: ()Ljava/util/List;
  public static io.github.mundanej.map.api.FeaturePortrayal portrayal(java.lang.String, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette, double);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolPalette;D)Lio/github/mundanej/map/api/FeaturePortrayal;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalogEntry extends java.lang.Record {
  public io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalogEntry(int, int, java.lang.String);
    descriptor: (IILjava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int symbolSet();
    descriptor: ()I
  public int entityCode();
    descriptor: ()I
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolException extends java.lang.IllegalArgumentException {
  public io.github.mundanej.map.symbology.milstd2525.MilitarySymbolException(java.lang.String, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolProblem;)V
  public io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem problem();
    descriptor: ()Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolProblem;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId {
  public static final int LENGTH = 30;
    descriptor: I
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId parse(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolId;
  public int version();
    descriptor: ()I
  public int context();
    descriptor: ()I
  public int standardIdentity();
    descriptor: ()I
  public int symbolSet();
    descriptor: ()I
  public int status();
    descriptor: ()I
  public int headquartersTaskForceDummy();
    descriptor: ()I
  public int amplifyingDescriptor();
    descriptor: ()I
  public int entity();
    descriptor: ()I
  public int entityType();
    descriptor: ()I
  public int entitySubtype();
    descriptor: ()I
  public int entityCode();
    descriptor: ()I
  public int sectorOneModifier();
    descriptor: ()I
  public int sectorTwoModifier();
    descriptor: ()I
  public int sectorOneCommonModifierSelector();
    descriptor: ()I
  public int sectorTwoCommonModifierSelector();
    descriptor: ()I
  public int frameShape();
    descriptor: ()I
  public int reserved();
    descriptor: ()I
  public int countryOrEntityCode();
    descriptor: ()I
  public java.lang.String canonical();
    descriptor: ()Ljava/lang/String;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette {
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette lightBackground();
    descriptor: ()Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolPalette;
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette darkBackground();
    descriptor: ()Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolPalette;
  public io.github.mundanej.map.api.Rgba unknown();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.Rgba friend();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.Rgba neutral();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.Rgba suspect();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.Rgba hostile();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.Rgba ink();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem extends java.lang.Record {
  public io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem(java.lang.String, java.lang.String, int, int, java.lang.String);
    descriptor: (Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.lang.String field();
    descriptor: ()Ljava/lang/String;
  public int startPosition();
    descriptor: ()I
  public int endPosition();
    descriptor: ()I
  public java.lang.String value();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProfile {
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProfile standard2525EChange1();
    descriptor: ()Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolProfile;
  public io.github.mundanej.map.symbology.milstd2525.MilitarySymbolAssessment assess(io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId);
    descriptor: (Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolId;)Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolAssessment;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolResolution extends java.lang.Record {
  public io.github.mundanej.map.symbology.milstd2525.MilitarySymbolResolution(io.github.mundanej.map.api.Symbol, java.util.Optional<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem>);
    descriptor: (Lio/github/mundanej/map/api/Symbol;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Symbol symbol();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
  public java.util.Optional<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem> problem();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport extends java.lang.Enum<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport> {
  public static final io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport SUPPORTED;
    descriptor: Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolSupport;
  public static final io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport DEGRADED_ENTITY;
    descriptor: Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolSupport;
  public static final io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport DEGRADED_MODIFIER;
    descriptor: Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolSupport;
  public static final io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport UNSUPPORTED;
    descriptor: Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolSupport;
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport[] values();
    descriptor: ()[Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolSupport;
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolSupport;
}
public final class io.github.mundanej.map.symbology.milstd2525.MilitarySymbols {
  public static io.github.mundanej.map.api.Symbol resolveStrict(io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette);
    descriptor: (Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolId;Lio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolPalette;)Lio/github/mundanej/map/api/Symbol;
  public static io.github.mundanej.map.api.Symbol resolveStrict(io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette, double);
    descriptor: (Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolId;Lio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolPalette;D)Lio/github/mundanej/map/api/Symbol;
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolResolution resolveDegraded(io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette);
    descriptor: (Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolId;Lio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolPalette;)Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolResolution;
  public static io.github.mundanej.map.symbology.milstd2525.MilitarySymbolResolution resolveDegraded(io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette, double);
    descriptor: (Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolId;Lio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolPalette;D)Lio/github/mundanej/map/symbology/milstd2525/MilitarySymbolResolution;
}
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolAssessment sealed=false permits=[] record=[support:io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport[], problem:java.util.Optional<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport, java.util.Optional<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem>] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:problem[] throws=[] annotations=[] parameterAnnotations=[], method:problem[io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem] throws=[] annotations=[] parameterAnnotations=[[], []], method:support[] throws=[] annotations=[] parameterAnnotations=[], method:supported[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalog sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:entries[] throws=[] annotations=[] parameterAnnotations=[], method:portrayal[java.lang.String, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalogEntry sealed=false permits=[] record=[symbolSet:int[], entityCode:int[], name:java.lang.String[]] enum=[] annotations=[] members=[constructor:[int, int, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:entityCode[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:symbolSet[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem] throws=[] annotations=[] parameterAnnotations=[[], []], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:LENGTH[], method:amplifyingDescriptor[] throws=[] annotations=[] parameterAnnotations=[], method:canonical[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:countryOrEntityCode[] throws=[] annotations=[] parameterAnnotations=[], method:entityCode[] throws=[] annotations=[] parameterAnnotations=[], method:entitySubtype[] throws=[] annotations=[] parameterAnnotations=[], method:entityType[] throws=[] annotations=[] parameterAnnotations=[], method:entity[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:frameShape[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:headquartersTaskForceDummy[] throws=[] annotations=[] parameterAnnotations=[], method:parse[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:reserved[] throws=[] annotations=[] parameterAnnotations=[], method:sectorOneCommonModifierSelector[] throws=[] annotations=[] parameterAnnotations=[], method:sectorOneModifier[] throws=[] annotations=[] parameterAnnotations=[], method:sectorTwoCommonModifierSelector[] throws=[] annotations=[] parameterAnnotations=[], method:sectorTwoModifier[] throws=[] annotations=[] parameterAnnotations=[], method:standardIdentity[] throws=[] annotations=[] parameterAnnotations=[], method:status[] throws=[] annotations=[] parameterAnnotations=[], method:symbolSet[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:version[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:darkBackground[] throws=[] annotations=[] parameterAnnotations=[], method:friend[] throws=[] annotations=[] parameterAnnotations=[], method:hostile[] throws=[] annotations=[] parameterAnnotations=[], method:ink[] throws=[] annotations=[] parameterAnnotations=[], method:lightBackground[] throws=[] annotations=[] parameterAnnotations=[], method:neutral[] throws=[] annotations=[] parameterAnnotations=[], method:suspect[] throws=[] annotations=[] parameterAnnotations=[], method:unknown[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem sealed=false permits=[] record=[code:java.lang.String[], field:java.lang.String[], startPosition:int[], endPosition:int[], value:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, int, int, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:endPosition[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:field[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:startPosition[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProfile sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:assess[io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId] throws=[] annotations=[] parameterAnnotations=[[]], method:standard2525EChange1[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolResolution sealed=false permits=[] record=[symbol:io.github.mundanej.map.api.Symbol[], problem:java.util.Optional<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Symbol, java.util.Optional<io.github.mundanej.map.symbology.milstd2525.MilitarySymbolProblem>] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:problem[] throws=[] annotations=[] parameterAnnotations=[], method:symbol[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbolSupport sealed=false permits=[] record=[] enum=[SUPPORTED, DEGRADED_ENTITY, DEGRADED_MODIFIER, UNSUPPORTED] annotations=[] members=[field:DEGRADED_ENTITY[], field:DEGRADED_MODIFIER[], field:SUPPORTED[], field:UNSUPPORTED[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.symbology.milstd2525.MilitarySymbols sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:resolveDegraded[io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:resolveDegraded[io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:resolveStrict[io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:resolveStrict[io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId, io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
