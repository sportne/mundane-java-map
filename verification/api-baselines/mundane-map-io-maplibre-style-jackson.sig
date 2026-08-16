public final class io.github.mundanej.map.io.maplibre.style.MapLibreBindException extends java.lang.RuntimeException {
  public io.github.mundanej.map.io.maplibre.style.MapLibreBindException(io.github.mundanej.map.io.maplibre.style.MapLibreProblem);
    descriptor: (Lio/github/mundanej/map/io/maplibre/style/MapLibreProblem;)V
  public io.github.mundanej.map.io.maplibre.style.MapLibreProblem problem();
    descriptor: ()Lio/github/mundanej/map/io/maplibre/style/MapLibreProblem;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreBoundLayer extends java.lang.Record {
  public io.github.mundanej.map.io.maplibre.style.MapLibreBoundLayer(java.lang.String, io.github.mundanej.map.api.FeatureSource, java.util.Optional<io.github.mundanej.map.api.FeaturePortrayal>, io.github.mundanej.map.api.AttributeSelection, double, double);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Ljava/util/Optional;Lio/github/mundanej/map/api/AttributeSelection;DD)V
  public boolean activeAt(double);
    descriptor: (D)Z
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.FeatureSource source();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSource;
  public java.util.Optional<io.github.mundanej.map.api.FeaturePortrayal> portrayal();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.AttributeSelection queryAttributes();
    descriptor: ()Lio/github/mundanej/map/api/AttributeSelection;
  public double minimumZoom();
    descriptor: ()D
  public double maximumZoom();
    descriptor: ()D
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreCamera extends java.lang.Record {
  public static final io.github.mundanej.map.io.maplibre.style.MapLibreCamera EMPTY;
    descriptor: Lio/github/mundanej/map/io/maplibre/style/MapLibreCamera;
  public io.github.mundanej.map.io.maplibre.style.MapLibreCamera(java.util.OptionalDouble, java.util.OptionalDouble, java.util.OptionalDouble, java.util.OptionalDouble, java.util.OptionalDouble);
    descriptor: (Ljava/util/OptionalDouble;Ljava/util/OptionalDouble;Ljava/util/OptionalDouble;Ljava/util/OptionalDouble;Ljava/util/OptionalDouble;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.OptionalDouble longitude();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.OptionalDouble latitude();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.OptionalDouble zoom();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.OptionalDouble bearing();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.OptionalDouble pitch();
    descriptor: ()Ljava/util/OptionalDouble;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreLayer extends java.lang.Record {
  public io.github.mundanej.map.io.maplibre.style.MapLibreLayer(java.lang.String, java.lang.String, io.github.mundanej.map.io.maplibre.style.MapLibreLayerType, boolean, double, double, java.util.Map<java.lang.String, java.lang.Object>, java.util.Optional<io.github.mundanej.map.api.FeaturePortrayal>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/io/maplibre/style/MapLibreLayerType;ZDDLjava/util/Map;Ljava/util/Optional;)V
  public java.util.Map<java.lang.String, java.lang.Object> metadata();
    descriptor: ()Ljava/util/Map;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String source();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.io.maplibre.style.MapLibreLayerType type();
    descriptor: ()Lio/github/mundanej/map/io/maplibre/style/MapLibreLayerType;
  public boolean visible();
    descriptor: ()Z
  public double minimumZoom();
    descriptor: ()D
  public double maximumZoom();
    descriptor: ()D
  public java.util.Optional<io.github.mundanej.map.api.FeaturePortrayal> portrayal();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreLayerType extends java.lang.Enum<io.github.mundanej.map.io.maplibre.style.MapLibreLayerType> {
  public static final io.github.mundanej.map.io.maplibre.style.MapLibreLayerType CIRCLE;
    descriptor: Lio/github/mundanej/map/io/maplibre/style/MapLibreLayerType;
  public static final io.github.mundanej.map.io.maplibre.style.MapLibreLayerType LINE;
    descriptor: Lio/github/mundanej/map/io/maplibre/style/MapLibreLayerType;
  public static final io.github.mundanej.map.io.maplibre.style.MapLibreLayerType FILL;
    descriptor: Lio/github/mundanej/map/io/maplibre/style/MapLibreLayerType;
  public static final io.github.mundanej.map.io.maplibre.style.MapLibreLayerType SYMBOL;
    descriptor: Lio/github/mundanej/map/io/maplibre/style/MapLibreLayerType;
  public static io.github.mundanej.map.io.maplibre.style.MapLibreLayerType[] values();
    descriptor: ()[Lio/github/mundanej/map/io/maplibre/style/MapLibreLayerType;
  public static io.github.mundanej.map.io.maplibre.style.MapLibreLayerType valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/io/maplibre/style/MapLibreLayerType;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreProblem extends java.lang.Record {
  public io.github.mundanej.map.io.maplibre.style.MapLibreProblem(java.lang.String, java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.lang.String phase();
    descriptor: ()Ljava/lang/String;
  public java.lang.String location();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreReadException extends java.lang.RuntimeException {
  public io.github.mundanej.map.io.maplibre.style.MapLibreReadException(io.github.mundanej.map.io.maplibre.style.MapLibreProblem);
    descriptor: (Lio/github/mundanej/map/io/maplibre/style/MapLibreProblem;)V
  public io.github.mundanej.map.io.maplibre.style.MapLibreProblem problem();
    descriptor: ()Lio/github/mundanej/map/io/maplibre/style/MapLibreProblem;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreReadLimits extends java.lang.Record {
  public io.github.mundanej.map.io.maplibre.style.MapLibreReadLimits(int, int, long, int, int, int, int, int, int, int, int, int, int, int, int, long);
    descriptor: (IIJIIIIIIIIIIIIJ)V
  public static io.github.mundanej.map.io.maplibre.style.MapLibreReadLimits defaults();
    descriptor: ()Lio/github/mundanej/map/io/maplibre/style/MapLibreReadLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumInputBytes();
    descriptor: ()I
  public int maximumNestingDepth();
    descriptor: ()I
  public long maximumTokens();
    descriptor: ()J
  public int maximumStringCharacters();
    descriptor: ()I
  public int maximumAggregateCharacters();
    descriptor: ()I
  public int maximumObjectMembers();
    descriptor: ()I
  public int maximumSources();
    descriptor: ()I
  public int maximumLayers();
    descriptor: ()I
  public int maximumMetadataEntries();
    descriptor: ()I
  public int maximumExpressionNodes();
    descriptor: ()I
  public int maximumExpressionDepth();
    descriptor: ()I
  public int maximumStops();
    descriptor: ()I
  public int maximumCategories();
    descriptor: ()I
  public int maximumCatalogReferences();
    descriptor: ()I
  public int maximumProducedRules();
    descriptor: ()I
  public long maximumOwnedBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreReadOptions extends java.lang.Record {
  public io.github.mundanej.map.io.maplibre.style.MapLibreReadOptions(io.github.mundanej.map.io.maplibre.style.MapLibreReadLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/io/maplibre/style/MapLibreReadLimits;Lio/github/mundanej/map/api/CancellationToken;)V
  public static io.github.mundanej.map.io.maplibre.style.MapLibreReadOptions defaults();
    descriptor: ()Lio/github/mundanej/map/io/maplibre/style/MapLibreReadOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.io.maplibre.style.MapLibreReadLimits limits();
    descriptor: ()Lio/github/mundanej/map/io/maplibre/style/MapLibreReadLimits;
  public io.github.mundanej.map.api.CancellationToken cancellation();
    descriptor: ()Lio/github/mundanej/map/api/CancellationToken;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreSourceDescriptor extends java.lang.Record {
  public io.github.mundanej.map.io.maplibre.style.MapLibreSourceDescriptor(java.lang.String, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/util/Optional;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.util.Optional<java.lang.String> dataLocator();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<java.lang.String> attribution();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry {
  public static io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry$Builder builder();
    descriptor: ()Lio/github/mundanej/map/io/maplibre/style/MapLibreSourceRegistry$Builder;
  public int size();
    descriptor: ()I
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry$Builder {
  public io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry$Builder register(java.lang.String, io.github.mundanej.map.api.FeatureSource);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;)Lio/github/mundanej/map/io/maplibre/style/MapLibreSourceRegistry$Builder;
  public io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry build();
    descriptor: ()Lio/github/mundanej/map/io/maplibre/style/MapLibreSourceRegistry;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreStyle extends java.lang.Record {
  public io.github.mundanej.map.io.maplibre.style.MapLibreStyle(java.util.Optional<java.lang.String>, java.util.Map<java.lang.String, java.lang.Object>, io.github.mundanej.map.io.maplibre.style.MapLibreCamera, java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSourceDescriptor>, java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreLayer>);
    descriptor: (Ljava/util/Optional;Ljava/util/Map;Lio/github/mundanej/map/io/maplibre/style/MapLibreCamera;Ljava/util/List;Ljava/util/List;)V
  public java.util.Map<java.lang.String, java.lang.Object> metadata();
    descriptor: ()Ljava/util/Map;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<java.lang.String> name();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.io.maplibre.style.MapLibreCamera camera();
    descriptor: ()Lio/github/mundanej/map/io/maplibre/style/MapLibreCamera;
  public java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSourceDescriptor> sources();
    descriptor: ()Ljava/util/List;
  public java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreLayer> layers();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinder {
  public static io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinding bind(io.github.mundanej.map.io.maplibre.style.MapLibreStyle, io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry);
    descriptor: (Lio/github/mundanej/map/io/maplibre/style/MapLibreStyle;Lio/github/mundanej/map/io/maplibre/style/MapLibreSourceRegistry;)Lio/github/mundanej/map/io/maplibre/style/MapLibreStyleBinding;
  public static io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinding bind(io.github.mundanej.map.io.maplibre.style.MapLibreStyle, io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry, io.github.mundanej.map.api.NamedSymbolCatalog);
    descriptor: (Lio/github/mundanej/map/io/maplibre/style/MapLibreStyle;Lio/github/mundanej/map/io/maplibre/style/MapLibreSourceRegistry;Lio/github/mundanej/map/api/NamedSymbolCatalog;)Lio/github/mundanej/map/io/maplibre/style/MapLibreStyleBinding;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinding implements java.lang.AutoCloseable {
  public java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreBoundLayer> layers();
    descriptor: ()Ljava/util/List;
  public java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreBoundLayer> activeLayers(double);
    descriptor: (D)Ljava/util/List;
  public boolean isClosed();
    descriptor: ()Z
  public void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreStyles {
  public static io.github.mundanej.map.io.maplibre.style.MapLibreStyle read(byte[]);
    descriptor: ([B)Lio/github/mundanej/map/io/maplibre/style/MapLibreStyle;
  public static io.github.mundanej.map.io.maplibre.style.MapLibreStyle read(byte[], io.github.mundanej.map.io.maplibre.style.MapLibreReadOptions);
    descriptor: ([BLio/github/mundanej/map/io/maplibre/style/MapLibreReadOptions;)Lio/github/mundanej/map/io/maplibre/style/MapLibreStyle;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Attribute extends java.lang.Record implements io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression {
  public io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Attribute(java.lang.String, boolean);
    descriptor: (Ljava/lang/String;Z)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String attribute();
    descriptor: ()Ljava/lang/String;
  public boolean stringify();
    descriptor: ()Z
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Case extends java.lang.Record implements io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression {
  public io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Case(java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$CaseRule>, java.lang.String);
    descriptor: (Ljava/util/List;Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$CaseRule> rules();
    descriptor: ()Ljava/util/List;
  public java.lang.String fallback();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Literal extends java.lang.Record implements io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression {
  public io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Literal(java.lang.String);
    descriptor: (Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Match extends java.lang.Record implements io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression {
  public io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Match(java.lang.String, java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$MatchRule>, java.lang.String);
    descriptor: (Ljava/lang/String;Ljava/util/List;Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String attribute();
    descriptor: ()Ljava/lang/String;
  public java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$MatchRule> rules();
    descriptor: ()Ljava/util/List;
  public java.lang.String fallback();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.io.maplibre.style.MapLibreZoom {
  public static double fromWebMercatorResolution(io.github.mundanej.map.api.CrsDefinition, double);
    descriptor: (Lio/github/mundanej/map/api/CrsDefinition;D)D
}
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreBindException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.maplibre.style.MapLibreProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreBoundLayer sealed=false permits=[] record=[id:java.lang.String[], source:io.github.mundanej.map.api.FeatureSource[], portrayal:java.util.Optional<io.github.mundanej.map.api.FeaturePortrayal>[], queryAttributes:io.github.mundanej.map.api.AttributeSelection[], minimumZoom:double[], maximumZoom:double[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.FeatureSource, java.util.Optional<io.github.mundanej.map.api.FeaturePortrayal>, io.github.mundanej.map.api.AttributeSelection, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:activeAt[double] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:maximumZoom[] throws=[] annotations=[] parameterAnnotations=[], method:minimumZoom[] throws=[] annotations=[] parameterAnnotations=[], method:portrayal[] throws=[] annotations=[] parameterAnnotations=[], method:queryAttributes[] throws=[] annotations=[] parameterAnnotations=[], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreCamera sealed=false permits=[] record=[longitude:java.util.OptionalDouble[], latitude:java.util.OptionalDouble[], zoom:java.util.OptionalDouble[], bearing:java.util.OptionalDouble[], pitch:java.util.OptionalDouble[]] enum=[] annotations=[] members=[constructor:[java.util.OptionalDouble, java.util.OptionalDouble, java.util.OptionalDouble, java.util.OptionalDouble, java.util.OptionalDouble] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], field:EMPTY[], method:bearing[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:latitude[] throws=[] annotations=[] parameterAnnotations=[], method:longitude[] throws=[] annotations=[] parameterAnnotations=[], method:pitch[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:zoom[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreLayer sealed=false permits=[] record=[id:java.lang.String[], source:java.lang.String[], type:io.github.mundanej.map.io.maplibre.style.MapLibreLayerType[], visible:boolean[], minimumZoom:double[], maximumZoom:double[], metadata:java.util.Map<java.lang.String, java.lang.Object>[], portrayal:java.util.Optional<io.github.mundanej.map.api.FeaturePortrayal>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, io.github.mundanej.map.io.maplibre.style.MapLibreLayerType, boolean, double, double, java.util.Map<java.lang.String, java.lang.Object>, java.util.Optional<io.github.mundanej.map.api.FeaturePortrayal>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:maximumZoom[] throws=[] annotations=[] parameterAnnotations=[], method:metadata[] throws=[] annotations=[] parameterAnnotations=[], method:minimumZoom[] throws=[] annotations=[] parameterAnnotations=[], method:portrayal[] throws=[] annotations=[] parameterAnnotations=[], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:type[] throws=[] annotations=[] parameterAnnotations=[], method:visible[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreLayerType sealed=false permits=[] record=[] enum=[CIRCLE, LINE, FILL, SYMBOL] annotations=[] members=[field:CIRCLE[], field:FILL[], field:LINE[], field:SYMBOL[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreProblem sealed=false permits=[] record=[code:java.lang.String[], phase:java.lang.String[], location:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:location[] throws=[] annotations=[] parameterAnnotations=[], method:phase[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreReadException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.maplibre.style.MapLibreProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreReadLimits sealed=false permits=[] record=[maximumInputBytes:int[], maximumNestingDepth:int[], maximumTokens:long[], maximumStringCharacters:int[], maximumAggregateCharacters:int[], maximumObjectMembers:int[], maximumSources:int[], maximumLayers:int[], maximumMetadataEntries:int[], maximumExpressionNodes:int[], maximumExpressionDepth:int[], maximumStops:int[], maximumCategories:int[], maximumCatalogReferences:int[], maximumProducedRules:int[], maximumOwnedBytes:long[]] enum=[] annotations=[] members=[constructor:[int, int, long, int, int, int, int, int, int, int, int, int, int, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], [], [], [], [], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAggregateCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCatalogReferences[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCategories[] throws=[] annotations=[] parameterAnnotations=[], method:maximumExpressionDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumExpressionNodes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumInputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumLayers[] throws=[] annotations=[] parameterAnnotations=[], method:maximumMetadataEntries[] throws=[] annotations=[] parameterAnnotations=[], method:maximumNestingDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumObjectMembers[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumProducedRules[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSources[] throws=[] annotations=[] parameterAnnotations=[], method:maximumStops[] throws=[] annotations=[] parameterAnnotations=[], method:maximumStringCharacters[] throws=[] annotations=[] parameterAnnotations=[], method:maximumTokens[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreReadOptions sealed=false permits=[] record=[limits:io.github.mundanej.map.io.maplibre.style.MapLibreReadLimits[], cancellation:io.github.mundanej.map.api.CancellationToken[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.io.maplibre.style.MapLibreReadLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []], method:cancellation[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreSourceDescriptor sealed=false permits=[] record=[id:java.lang.String[], dataLocator:java.util.Optional<java.lang.String>[], attribution:java.util.Optional<java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:attribution[] throws=[] annotations=[] parameterAnnotations=[], method:dataLocator[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:builder[] throws=[] annotations=[] parameterAnnotations=[], method:size[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry$Builder sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:build[] throws=[] annotations=[] parameterAnnotations=[], method:register[java.lang.String, io.github.mundanej.map.api.FeatureSource] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreStyle sealed=false permits=[] record=[name:java.util.Optional<java.lang.String>[], metadata:java.util.Map<java.lang.String, java.lang.Object>[], camera:io.github.mundanej.map.io.maplibre.style.MapLibreCamera[], sources:java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSourceDescriptor>[], layers:java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreLayer>[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<java.lang.String>, java.util.Map<java.lang.String, java.lang.Object>, io.github.mundanej.map.io.maplibre.style.MapLibreCamera, java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSourceDescriptor>, java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreLayer>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:camera[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layers[] throws=[] annotations=[] parameterAnnotations=[], method:metadata[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:sources[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinder sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:bind[io.github.mundanej.map.io.maplibre.style.MapLibreStyle, io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry, io.github.mundanej.map.api.NamedSymbolCatalog] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:bind[io.github.mundanej.map.io.maplibre.style.MapLibreStyle, io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinding sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:activeLayers[double] throws=[] annotations=[] parameterAnnotations=[[]], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:layers[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreStyles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:read[byte[], io.github.mundanej.map.io.maplibre.style.MapLibreReadOptions] throws=[] annotations=[] parameterAnnotations=[[], []], method:read[byte[]] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Attribute sealed=false permits=[] record=[attribute:java.lang.String[], stringify:boolean[]] enum=[] annotations=[] members=[constructor:[java.lang.String, boolean] throws=[] annotations=[] parameterAnnotations=[[], []], method:attribute[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:stringify[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Case sealed=false permits=[] record=[rules:java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$CaseRule>[], fallback:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$CaseRule>, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fallback[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:rules[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Literal sealed=false permits=[] record=[name:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$IconExpression$Match sealed=false permits=[] record=[attribute:java.lang.String[], rules:java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$MatchRule>[], fallback:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.List<io.github.mundanej.map.io.maplibre.style.MapLibreSymbolSpec$MatchRule>, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:attribute[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fallback[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:rules[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.io.maplibre.style.MapLibreZoom sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:fromWebMercatorResolution[io.github.mundanej.map.api.CrsDefinition, double] throws=[] annotations=[] parameterAnnotations=[[], []]]
