public final class io.github.mundanej.map.io.se.SeDescription extends java.lang.Record {
  public io.github.mundanej.map.io.se.SeDescription(java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>);
    descriptor: (Ljava/util/Optional;Ljava/util/Optional;)V
  public static io.github.mundanej.map.io.se.SeDescription empty();
    descriptor: ()Lio/github/mundanej/map/io/se/SeDescription;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<java.lang.String> title();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<java.lang.String> abstractText();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.io.se.SeFeatureStyle extends java.lang.Record {
  public io.github.mundanej.map.io.se.SeFeatureStyle(java.util.Optional<java.lang.String>, io.github.mundanej.map.io.se.SeDescription, java.util.Optional<java.lang.String>, java.util.List<java.lang.String>, java.util.List<io.github.mundanej.map.io.se.SeRuleMetadata>, io.github.mundanej.map.api.FeaturePortrayal);
    descriptor: (Ljava/util/Optional;Lio/github/mundanej/map/io/se/SeDescription;Ljava/util/Optional;Ljava/util/List;Ljava/util/List;Lio/github/mundanej/map/api/FeaturePortrayal;)V
  public java.util.List<java.lang.String> semanticTypeIdentifiers();
    descriptor: ()Ljava/util/List;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<java.lang.String> name();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.io.se.SeDescription description();
    descriptor: ()Lio/github/mundanej/map/io/se/SeDescription;
  public java.util.Optional<java.lang.String> featureTypeName();
    descriptor: ()Ljava/util/Optional;
  public java.util.List<io.github.mundanej.map.io.se.SeRuleMetadata> rules();
    descriptor: ()Ljava/util/List;
  public io.github.mundanej.map.api.FeaturePortrayal portrayal();
    descriptor: ()Lio/github/mundanej/map/api/FeaturePortrayal;
}
public final class io.github.mundanej.map.io.se.SeReadException extends java.lang.RuntimeException {
  public io.github.mundanej.map.io.se.SeReadException(io.github.mundanej.map.io.se.SeReadProblem);
    descriptor: (Lio/github/mundanej/map/io/se/SeReadProblem;)V
  public io.github.mundanej.map.io.se.SeReadException(io.github.mundanej.map.io.se.SeReadProblem, java.lang.Throwable);
    descriptor: (Lio/github/mundanej/map/io/se/SeReadProblem;Ljava/lang/Throwable;)V
  public io.github.mundanej.map.io.se.SeReadProblem problem();
    descriptor: ()Lio/github/mundanej/map/io/se/SeReadProblem;
}
public final class io.github.mundanej.map.io.se.SeReadLimits extends java.lang.Record {
  public io.github.mundanej.map.io.se.SeReadLimits(int, int, int, int, int, int, int, int, int, int, int, int, long);
    descriptor: (IIIIIIIIIIIIJ)V
  public static io.github.mundanej.map.io.se.SeReadLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/se/SeReadLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumInputBytes();
    descriptor: ()I
  public int maximumElementDepth();
    descriptor: ()I
  public int maximumElements();
    descriptor: ()I
  public int maximumAttributes();
    descriptor: ()I
  public int maximumAggregateTextCharacters();
    descriptor: ()I
  public int maximumValueCharacters();
    descriptor: ()I
  public int maximumRules();
    descriptor: ()I
  public int maximumPredicates();
    descriptor: ()I
  public int maximumPredicateDepth();
    descriptor: ()I
  public int maximumSymbolizers();
    descriptor: ()I
  public int maximumCatalogReferences();
    descriptor: ()I
  public int maximumOutputSymbols();
    descriptor: ()I
  public long maximumOwnedBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.io.se.SeReadOptions extends java.lang.Record {
  public io.github.mundanej.map.io.se.SeReadOptions(io.github.mundanej.map.io.se.SeReadLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/io/se/SeReadLimits;Lio/github/mundanej/map/api/CancellationToken;)V
  public static io.github.mundanej.map.io.se.SeReadOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/se/SeReadOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.se.SeReadLimits limits();
    descriptor: ()Lio/github/mundanej/map/io/se/SeReadLimits;
  public io.github.mundanej.map.api.CancellationToken cancellation();
    descriptor: ()Lio/github/mundanej/map/api/CancellationToken;
}
public final class io.github.mundanej.map.io.se.SeReadProblem extends java.lang.Record {
  public io.github.mundanej.map.io.se.SeReadProblem(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.lang.String sourceName();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.io.se.SeRuleMetadata extends java.lang.Record {
  public io.github.mundanej.map.io.se.SeRuleMetadata(java.util.Optional<java.lang.String>, io.github.mundanej.map.io.se.SeDescription);
    descriptor: (Ljava/util/Optional;Lio/github/mundanej/map/io/se/SeDescription;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<java.lang.String> name();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.io.se.SeDescription description();
    descriptor: ()Lio/github/mundanej/map/io/se/SeDescription;
}
public final class io.github.mundanej.map.io.se.SeStyles {
  public static io.github.mundanej.map.io.se.SeFeatureStyle read(java.nio.file.Path, io.github.mundanej.map.api.NamedSymbolCatalog, io.github.mundanej.map.io.se.SeReadOptions);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/api/NamedSymbolCatalog;Lio/github/mundanej/map/io/se/SeReadOptions;)Lio/github/mundanej/map/io/se/SeFeatureStyle;
  public static io.github.mundanej.map.io.se.SeFeatureStyle read(java.lang.String, byte[], io.github.mundanej.map.api.NamedSymbolCatalog, io.github.mundanej.map.io.se.SeReadOptions);
    descriptor: (Ljava/lang/String;[BLio/github/mundanej/map/api/NamedSymbolCatalog;Lio/github/mundanej/map/io/se/SeReadOptions;)Lio/github/mundanej/map/io/se/SeFeatureStyle;
}
SHAPE io.github.mundanej.map.io.se.SeDescription sealed=false permits=[] record=[title:java.util.Optional<java.lang.String>[], abstractText:java.util.Optional<java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], []], method:abstractText[] throws=[] annotations=[] parameterAnnotations=[], method:empty[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:title[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.se.SeFeatureStyle sealed=false permits=[] record=[name:java.util.Optional<java.lang.String>[], description:io.github.mundanej.map.io.se.SeDescription[], featureTypeName:java.util.Optional<java.lang.String>[], semanticTypeIdentifiers:java.util.List<java.lang.String>[], rules:java.util.List<io.github.mundanej.map.io.se.SeRuleMetadata>[], portrayal:io.github.mundanej.map.api.FeaturePortrayal[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<java.lang.String>, io.github.mundanej.map.io.se.SeDescription, java.util.Optional<java.lang.String>, java.util.List<java.lang.String>, java.util.List<io.github.mundanej.map.io.se.SeRuleMetadata>, io.github.mundanej.map.api.FeaturePortrayal] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:description[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureTypeName[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:portrayal[] throws=[] annotations=[] parameterAnnotations=[], method:rules[] throws=[] annotations=[] parameterAnnotations=[], method:semanticTypeIdentifiers[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.se.SeReadException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.se.SeReadProblem, java.lang.Throwable] throws=[] annotations=[] parameterAnnotations=[[], []], constructor:[io.github.mundanej.map.io.se.SeReadProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.se.SeReadLimits sealed=false permits=[] record=[maximumInputBytes:int[], maximumElementDepth:int[], maximumElements:int[], maximumAttributes:int[], maximumAggregateTextCharacters:int[], maximumValueCharacters:int[], maximumRules:int[], maximumPredicates:int[], maximumPredicateDepth:int[], maximumSymbolizers:int[], maximumCatalogReferences:int[], maximumOutputSymbols:int[], maximumOwnedBytes:long[]] enum=[] annotations=[] members=[constructor:[int, int, int, int, int, int, int, int, int, int, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], [], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAggregateTextCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAttributes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCatalogReferences[] throws=[] annotations=[] parameterAnnotations=[], method:maximumElementDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumElements[] throws=[] annotations=[] parameterAnnotations=[], method:maximumInputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOutputSymbols[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPredicateDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPredicates[] throws=[] annotations=[] parameterAnnotations=[], method:maximumRules[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSymbolizers[] throws=[] annotations=[] parameterAnnotations=[], method:maximumValueCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.se.SeReadOptions sealed=false permits=[] record=[limits:io.github.mundanej.map.io.se.SeReadLimits[], cancellation:io.github.mundanej.map.api.CancellationToken[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.se.SeReadLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []], method:cancellation[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.se.SeReadProblem sealed=false permits=[] record=[code:java.lang.String[], sourceName:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:sourceName[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.se.SeRuleMetadata sealed=false permits=[] record=[name:java.util.Optional<java.lang.String>[], description:io.github.mundanej.map.io.se.SeDescription[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<java.lang.String>, io.github.mundanej.map.io.se.SeDescription] throws=[] annotations=[] parameterAnnotations=[[], []], method:description[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.se.SeStyles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:read[java.lang.String, byte[], io.github.mundanej.map.api.NamedSymbolCatalog, io.github.mundanej.map.io.se.SeReadOptions] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:read[java.nio.file.Path, io.github.mundanej.map.api.NamedSymbolCatalog, io.github.mundanej.map.io.se.SeReadOptions] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
