public final class io.github.mundanej.map.api.AdvancedFillSymbol extends java.lang.Record implements io.github.mundanej.map.api.FillSymbol {
  public static final io.github.mundanej.map.api.SymbolRendererKey RENDERER_KEY;
    descriptor: Lio/github/mundanej/map/api/SymbolRendererKey;
  public io.github.mundanej.map.api.AdvancedFillSymbol(java.util.Optional<io.github.mundanej.map.api.Rgba>, java.util.Optional<io.github.mundanej.map.api.GraphicPaint>, java.util.Optional<io.github.mundanej.map.api.AdvancedStroke>, double);
    descriptor: (Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;D)V
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<io.github.mundanej.map.api.Rgba> color();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.GraphicPaint> graphicFill();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.AdvancedStroke> outline();
    descriptor: ()Ljava/util/Optional;
  public double opacity();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.AdvancedLineSymbol extends java.lang.Record implements io.github.mundanej.map.api.LineSymbol {
  public static final io.github.mundanej.map.api.SymbolRendererKey RENDERER_KEY;
    descriptor: Lio/github/mundanej/map/api/SymbolRendererKey;
  public io.github.mundanej.map.api.AdvancedLineSymbol(io.github.mundanej.map.api.AdvancedStroke, double);
    descriptor: (Lio/github/mundanej/map/api/AdvancedStroke;D)V
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.AdvancedStroke stroke();
    descriptor: ()Lio/github/mundanej/map/api/AdvancedStroke;
  public double opacity();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.AdvancedStroke extends java.lang.Record {
  public io.github.mundanej.map.api.AdvancedStroke(io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.api.AdvancedStroke$Cap, io.github.mundanej.map.api.AdvancedStroke$Join, java.util.List<java.lang.Double>, double, double, java.util.Optional<io.github.mundanej.map.api.GraphicPaint>);
    descriptor: (Lio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/SymbolLength;Lio/github/mundanej/map/api/AdvancedStroke$Cap;Lio/github/mundanej/map/api/AdvancedStroke$Join;Ljava/util/List;DDLjava/util/Optional;)V
  public static io.github.mundanej.map.api.AdvancedStroke solid(io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.SymbolLength);
    descriptor: (Lio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/SymbolLength;)Lio/github/mundanej/map/api/AdvancedStroke;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Rgba color();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.SymbolLength width();
    descriptor: ()Lio/github/mundanej/map/api/SymbolLength;
  public io.github.mundanej.map.api.AdvancedStroke$Cap cap();
    descriptor: ()Lio/github/mundanej/map/api/AdvancedStroke$Cap;
  public io.github.mundanej.map.api.AdvancedStroke$Join join();
    descriptor: ()Lio/github/mundanej/map/api/AdvancedStroke$Join;
  public java.util.List<java.lang.Double> dashArray();
    descriptor: ()Ljava/util/List;
  public double dashOffset();
    descriptor: ()D
  public double perpendicularOffset();
    descriptor: ()D
  public java.util.Optional<io.github.mundanej.map.api.GraphicPaint> graphicStroke();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.AdvancedStroke$Cap extends java.lang.Enum<io.github.mundanej.map.api.AdvancedStroke$Cap> {
  public static final io.github.mundanej.map.api.AdvancedStroke$Cap BUTT;
    descriptor: Lio/github/mundanej/map/api/AdvancedStroke$Cap;
  public static final io.github.mundanej.map.api.AdvancedStroke$Cap ROUND;
    descriptor: Lio/github/mundanej/map/api/AdvancedStroke$Cap;
  public static final io.github.mundanej.map.api.AdvancedStroke$Cap SQUARE;
    descriptor: Lio/github/mundanej/map/api/AdvancedStroke$Cap;
  public static io.github.mundanej.map.api.AdvancedStroke$Cap[] values();
    descriptor: ()[Lio/github/mundanej/map/api/AdvancedStroke$Cap;
  public static io.github.mundanej.map.api.AdvancedStroke$Cap valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/AdvancedStroke$Cap;
}
public final class io.github.mundanej.map.api.AdvancedStroke$Join extends java.lang.Enum<io.github.mundanej.map.api.AdvancedStroke$Join> {
  public static final io.github.mundanej.map.api.AdvancedStroke$Join MITER;
    descriptor: Lio/github/mundanej/map/api/AdvancedStroke$Join;
  public static final io.github.mundanej.map.api.AdvancedStroke$Join ROUND;
    descriptor: Lio/github/mundanej/map/api/AdvancedStroke$Join;
  public static final io.github.mundanej.map.api.AdvancedStroke$Join BEVEL;
    descriptor: Lio/github/mundanej/map/api/AdvancedStroke$Join;
  public static io.github.mundanej.map.api.AdvancedStroke$Join[] values();
    descriptor: ()[Lio/github/mundanej/map/api/AdvancedStroke$Join;
  public static io.github.mundanej.map.api.AdvancedStroke$Join valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/AdvancedStroke$Join;
}
public final class io.github.mundanej.map.api.AttributeBytes {
  public io.github.mundanej.map.api.AttributeBytes(byte[]);
    descriptor: ([B)V
  public int length();
    descriptor: ()I
  public byte byteAt(int);
    descriptor: (I)B
  public byte[] toArray();
    descriptor: ()[B
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.AttributeField extends java.lang.Record {
  public io.github.mundanej.map.api.AttributeField(java.lang.String, io.github.mundanej.map.api.AttributeType, boolean);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/AttributeType;Z)V
  public boolean accepts(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.AttributeType type();
    descriptor: ()Lio/github/mundanej/map/api/AttributeType;
  public boolean nullable();
    descriptor: ()Z
}
public final class io.github.mundanej.map.api.AttributeNull extends java.lang.Enum<io.github.mundanej.map.api.AttributeNull> {
  public static final io.github.mundanej.map.api.AttributeNull INSTANCE;
    descriptor: Lio/github/mundanej/map/api/AttributeNull;
  public static io.github.mundanej.map.api.AttributeNull[] values();
    descriptor: ()[Lio/github/mundanej/map/api/AttributeNull;
  public static io.github.mundanej.map.api.AttributeNull valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/AttributeNull;
}
public final class io.github.mundanej.map.api.AttributeSchema {
  public io.github.mundanej.map.api.AttributeSchema(java.util.List<io.github.mundanej.map.api.AttributeField>);
    descriptor: (Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.api.AttributeField> fields();
    descriptor: ()Ljava/util/List;
  public java.util.Optional<io.github.mundanej.map.api.AttributeField> field(java.lang.String);
    descriptor: (Ljava/lang/String;)Ljava/util/Optional;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.AttributeSelection {
  public static final io.github.mundanej.map.api.AttributeSelection ALL;
    descriptor: Lio/github/mundanej/map/api/AttributeSelection;
  public static final io.github.mundanej.map.api.AttributeSelection NONE;
    descriptor: Lio/github/mundanej/map/api/AttributeSelection;
  public static io.github.mundanej.map.api.AttributeSelection only(java.util.List<java.lang.String>);
    descriptor: (Ljava/util/List;)Lio/github/mundanej/map/api/AttributeSelection;
  public java.util.List<java.lang.String> orderedNames();
    descriptor: ()Ljava/util/List;
  public boolean isOnly();
    descriptor: ()Z
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.AttributeType extends java.lang.Enum<io.github.mundanej.map.api.AttributeType> {
  public static final io.github.mundanej.map.api.AttributeType TEXT;
    descriptor: Lio/github/mundanej/map/api/AttributeType;
  public static final io.github.mundanej.map.api.AttributeType LOGICAL;
    descriptor: Lio/github/mundanej/map/api/AttributeType;
  public static final io.github.mundanej.map.api.AttributeType INTEGER;
    descriptor: Lio/github/mundanej/map/api/AttributeType;
  public static final io.github.mundanej.map.api.AttributeType FLOATING;
    descriptor: Lio/github/mundanej/map/api/AttributeType;
  public static final io.github.mundanej.map.api.AttributeType DECIMAL;
    descriptor: Lio/github/mundanej/map/api/AttributeType;
  public static final io.github.mundanej.map.api.AttributeType DATE;
    descriptor: Lio/github/mundanej/map/api/AttributeType;
  public static final io.github.mundanej.map.api.AttributeType BINARY;
    descriptor: Lio/github/mundanej/map/api/AttributeType;
  public static final io.github.mundanej.map.api.AttributeType STRUCTURED;
    descriptor: Lio/github/mundanej/map/api/AttributeType;
  public static io.github.mundanej.map.api.AttributeType[] values();
    descriptor: ()[Lio/github/mundanej/map/api/AttributeType;
  public static io.github.mundanej.map.api.AttributeType valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/AttributeType;
}
public interface io.github.mundanej.map.api.AttributeValueCandidate {
}
public final class io.github.mundanej.map.api.AttributeValueCandidate$Attribute extends java.lang.Record implements io.github.mundanej.map.api.AttributeValueCandidate {
  public io.github.mundanej.map.api.AttributeValueCandidate$Attribute(java.lang.String);
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
public final class io.github.mundanej.map.api.AttributeValueCandidate$Literal extends java.lang.Record implements io.github.mundanej.map.api.AttributeValueCandidate {
  public io.github.mundanej.map.api.AttributeValueCandidate$Literal(io.github.mundanej.map.api.ThematicValue);
    descriptor: (Lio/github/mundanej/map/api/ThematicValue;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.ThematicValue value();
    descriptor: ()Lio/github/mundanej/map/api/ThematicValue;
}
public final class io.github.mundanej.map.api.AttributeValueConversion {
  public static final io.github.mundanej.map.api.AttributeValueConversion IDENTITY;
    descriptor: Lio/github/mundanej/map/api/AttributeValueConversion;
  public static final io.github.mundanej.map.api.AttributeValueConversion TO_NUMBER;
    descriptor: Lio/github/mundanej/map/api/AttributeValueConversion;
  public static final io.github.mundanej.map.api.AttributeValueConversion TO_STRING;
    descriptor: Lio/github/mundanej/map/api/AttributeValueConversion;
  public static io.github.mundanej.map.api.AttributeValueConversion toNumber(java.util.List<? extends io.github.mundanej.map.api.AttributeValueCandidate>);
    descriptor: (Ljava/util/List;)Lio/github/mundanej/map/api/AttributeValueConversion;
  public io.github.mundanej.map.api.AttributeValueConversion$Operation operation();
    descriptor: ()Lio/github/mundanej/map/api/AttributeValueConversion$Operation;
  public java.util.List<io.github.mundanej.map.api.AttributeValueCandidate> candidates();
    descriptor: ()Ljava/util/List;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.AttributeValueConversion$Operation extends java.lang.Enum<io.github.mundanej.map.api.AttributeValueConversion$Operation> {
  public static final io.github.mundanej.map.api.AttributeValueConversion$Operation IDENTITY;
    descriptor: Lio/github/mundanej/map/api/AttributeValueConversion$Operation;
  public static final io.github.mundanej.map.api.AttributeValueConversion$Operation TO_NUMBER;
    descriptor: Lio/github/mundanej/map/api/AttributeValueConversion$Operation;
  public static final io.github.mundanej.map.api.AttributeValueConversion$Operation TO_STRING;
    descriptor: Lio/github/mundanej/map/api/AttributeValueConversion$Operation;
  public static io.github.mundanej.map.api.AttributeValueConversion$Operation[] values();
    descriptor: ()[Lio/github/mundanej/map/api/AttributeValueConversion$Operation;
  public static io.github.mundanej.map.api.AttributeValueConversion$Operation valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/AttributeValueConversion$Operation;
}
public final class io.github.mundanej.map.api.AttributeValues {
  public static java.util.Map<java.lang.String, java.lang.Object> canonicalize(java.util.Map<java.lang.String, ?>);
    descriptor: (Ljava/util/Map;)Ljava/util/Map;
  public static java.lang.Object canonicalizeValue(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
}
public final class io.github.mundanej.map.api.BuiltInMarker extends java.lang.Enum<io.github.mundanej.map.api.BuiltInMarker> {
  public static final io.github.mundanej.map.api.BuiltInMarker CIRCLE;
    descriptor: Lio/github/mundanej/map/api/BuiltInMarker;
  public static final io.github.mundanej.map.api.BuiltInMarker SQUARE;
    descriptor: Lio/github/mundanej/map/api/BuiltInMarker;
  public static final io.github.mundanej.map.api.BuiltInMarker TRIANGLE;
    descriptor: Lio/github/mundanej/map/api/BuiltInMarker;
  public static final io.github.mundanej.map.api.BuiltInMarker DIAMOND;
    descriptor: Lio/github/mundanej/map/api/BuiltInMarker;
  public static final io.github.mundanej.map.api.BuiltInMarker CROSS;
    descriptor: Lio/github/mundanej/map/api/BuiltInMarker;
  public static final io.github.mundanej.map.api.BuiltInMarker X;
    descriptor: Lio/github/mundanej/map/api/BuiltInMarker;
  public static final io.github.mundanej.map.api.BuiltInMarker STAR;
    descriptor: Lio/github/mundanej/map/api/BuiltInMarker;
  public static final io.github.mundanej.map.api.BuiltInMarker ARROW;
    descriptor: Lio/github/mundanej/map/api/BuiltInMarker;
  public static io.github.mundanej.map.api.BuiltInMarker[] values();
    descriptor: ()[Lio/github/mundanej/map/api/BuiltInMarker;
  public static io.github.mundanej.map.api.BuiltInMarker valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/BuiltInMarker;
}
public final class io.github.mundanej.map.api.CancellationSource {
  public io.github.mundanej.map.api.CancellationSource();
    descriptor: ()V
  public io.github.mundanej.map.api.CancellationToken token();
    descriptor: ()Lio/github/mundanej/map/api/CancellationToken;
  public void cancel();
    descriptor: ()V
}
public interface io.github.mundanej.map.api.CancellationToken {
  public abstract boolean isCancellationRequested();
    descriptor: ()Z
  public static io.github.mundanej.map.api.CancellationToken none();
    descriptor: ()Lio/github/mundanej/map/api/CancellationToken;
}
public final class io.github.mundanej.map.api.CancellationToken$NeverCancelled extends java.lang.Enum<io.github.mundanej.map.api.CancellationToken$NeverCancelled> implements io.github.mundanej.map.api.CancellationToken {
  public static final io.github.mundanej.map.api.CancellationToken$NeverCancelled INSTANCE;
    descriptor: Lio/github/mundanej/map/api/CancellationToken$NeverCancelled;
  public static io.github.mundanej.map.api.CancellationToken$NeverCancelled[] values();
    descriptor: ()[Lio/github/mundanej/map/api/CancellationToken$NeverCancelled;
  public static io.github.mundanej.map.api.CancellationToken$NeverCancelled valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/CancellationToken$NeverCancelled;
  public boolean isCancellationRequested();
    descriptor: ()Z
}
public final class io.github.mundanej.map.api.CategoricalSymbolRule extends java.lang.Record {
  public io.github.mundanej.map.api.CategoricalSymbolRule(io.github.mundanej.map.api.ThematicValue, io.github.mundanej.map.api.Symbol);
    descriptor: (Lio/github/mundanej/map/api/ThematicValue;Lio/github/mundanej/map/api/Symbol;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.ThematicValue value();
    descriptor: ()Lio/github/mundanej/map/api/ThematicValue;
  public io.github.mundanej.map.api.Symbol symbol();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
}
public final class io.github.mundanej.map.api.CategoricalSymbolSelector implements io.github.mundanej.map.api.SymbolSelector {
  public static final int MAXIMUM_RULES = 16384;
    descriptor: I
  public io.github.mundanej.map.api.CategoricalSymbolSelector(java.lang.String, java.util.List<io.github.mundanej.map.api.CategoricalSymbolRule>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>);
    descriptor: (Ljava/lang/String;Ljava/util/List;Ljava/util/Optional;)V
  public static io.github.mundanej.map.api.CategoricalSymbolSelector expressionInput(java.lang.String, java.util.List<io.github.mundanej.map.api.CategoricalSymbolRule>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>, io.github.mundanej.map.api.AttributeValueConversion);
    descriptor: (Ljava/lang/String;Ljava/util/List;Ljava/util/Optional;Lio/github/mundanej/map/api/AttributeValueConversion;)Lio/github/mundanej/map/api/CategoricalSymbolSelector;
  public java.lang.String attribute();
    descriptor: ()Ljava/lang/String;
  public java.util.List<io.github.mundanej.map.api.CategoricalSymbolRule> rules();
    descriptor: ()Ljava/util/List;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> fallback();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.AttributeValueConversion conversion();
    descriptor: ()Lio/github/mundanej/map/api/AttributeValueConversion;
  public boolean missingAsNull();
    descriptor: ()Z
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.CompositeSymbol implements io.github.mundanej.map.api.Symbol {
  public static final io.github.mundanej.map.api.SymbolRendererKey RENDERER_KEY;
    descriptor: Lio/github/mundanej/map/api/SymbolRendererKey;
  public static io.github.mundanej.map.api.CompositeSymbol of(java.util.List<? extends io.github.mundanej.map.api.Symbol>, double);
    descriptor: (Ljava/util/List;D)Lio/github/mundanej/map/api/CompositeSymbol;
  public java.util.List<io.github.mundanej.map.api.Symbol> children();
    descriptor: ()Ljava/util/List;
  public double opacity();
    descriptor: ()D
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.Coordinate extends java.lang.Record {
  public io.github.mundanej.map.api.Coordinate(double, double);
    descriptor: (DD)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double x();
    descriptor: ()D
  public double y();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.CoordinateSequence {
  public static io.github.mundanej.map.api.CoordinateSequence of(double...);
    descriptor: ([D)Lio/github/mundanej/map/api/CoordinateSequence;
  public static io.github.mundanej.map.api.CoordinateSequence of(io.github.mundanej.map.api.GeometryDimension, double...);
    descriptor: (Lio/github/mundanej/map/api/GeometryDimension;[D)Lio/github/mundanej/map/api/CoordinateSequence;
  public static io.github.mundanej.map.api.CoordinateSequence empty(io.github.mundanej.map.api.GeometryDimension);
    descriptor: (Lio/github/mundanej/map/api/GeometryDimension;)Lio/github/mundanej/map/api/CoordinateSequence;
  public io.github.mundanej.map.api.GeometryDimension dimension();
    descriptor: ()Lio/github/mundanej/map/api/GeometryDimension;
  public boolean isEmpty();
    descriptor: ()Z
  public int size();
    descriptor: ()I
  public double x(int);
    descriptor: (I)D
  public double y(int);
    descriptor: (I)D
  public double z(int);
    descriptor: (I)D
  public double m(int);
    descriptor: (I)D
  public io.github.mundanej.map.api.Coordinate coordinate(int);
    descriptor: (I)Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public java.util.Optional<io.github.mundanej.map.api.Envelope> bounds();
    descriptor: ()Ljava/util/Optional;
  public boolean isClosed();
    descriptor: ()Z
  public double[] toArray();
    descriptor: ()[D
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.CreateFeature extends java.lang.Record implements io.github.mundanej.map.api.FeatureEditCommand {
  public io.github.mundanej.map.api.CreateFeature(io.github.mundanej.map.api.FeatureRecord);
    descriptor: (Lio/github/mundanej/map/api/FeatureRecord;)V
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.FeatureRecord feature();
    descriptor: ()Lio/github/mundanej/map/api/FeatureRecord;
}
public final class io.github.mundanej.map.api.CrsAxis extends java.lang.Record {
  public io.github.mundanej.map.api.CrsAxis(io.github.mundanej.map.api.CrsAxisMeaning, io.github.mundanej.map.api.CrsUnit);
    descriptor: (Lio/github/mundanej/map/api/CrsAxisMeaning;Lio/github/mundanej/map/api/CrsUnit;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.CrsAxisMeaning meaning();
    descriptor: ()Lio/github/mundanej/map/api/CrsAxisMeaning;
  public io.github.mundanej.map.api.CrsUnit unit();
    descriptor: ()Lio/github/mundanej/map/api/CrsUnit;
}
public final class io.github.mundanej.map.api.CrsAxisMeaning extends java.lang.Enum<io.github.mundanej.map.api.CrsAxisMeaning> {
  public static final io.github.mundanej.map.api.CrsAxisMeaning LONGITUDE;
    descriptor: Lio/github/mundanej/map/api/CrsAxisMeaning;
  public static final io.github.mundanej.map.api.CrsAxisMeaning LATITUDE;
    descriptor: Lio/github/mundanej/map/api/CrsAxisMeaning;
  public static final io.github.mundanej.map.api.CrsAxisMeaning EASTING;
    descriptor: Lio/github/mundanej/map/api/CrsAxisMeaning;
  public static final io.github.mundanej.map.api.CrsAxisMeaning NORTHING;
    descriptor: Lio/github/mundanej/map/api/CrsAxisMeaning;
  public static io.github.mundanej.map.api.CrsAxisMeaning[] values();
    descriptor: ()[Lio/github/mundanej/map/api/CrsAxisMeaning;
  public static io.github.mundanej.map.api.CrsAxisMeaning valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/CrsAxisMeaning;
}
public final class io.github.mundanej.map.api.CrsDefinition extends java.lang.Record {
  public io.github.mundanej.map.api.CrsDefinition(java.lang.String, io.github.mundanej.map.api.CrsKind, io.github.mundanej.map.api.CrsAxis, io.github.mundanej.map.api.CrsAxis, io.github.mundanej.map.api.Envelope);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/CrsKind;Lio/github/mundanej/map/api/CrsAxis;Lio/github/mundanej/map/api/CrsAxis;Lio/github/mundanej/map/api/Envelope;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String canonicalIdentifier();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.CrsKind kind();
    descriptor: ()Lio/github/mundanej/map/api/CrsKind;
  public io.github.mundanej.map.api.CrsAxis xAxis();
    descriptor: ()Lio/github/mundanej/map/api/CrsAxis;
  public io.github.mundanej.map.api.CrsAxis yAxis();
    descriptor: ()Lio/github/mundanej/map/api/CrsAxis;
  public io.github.mundanej.map.api.Envelope coordinateDomain();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
}
public final class io.github.mundanej.map.api.CrsException extends java.lang.RuntimeException {
  public io.github.mundanej.map.api.CrsException(io.github.mundanej.map.api.CrsProblem);
    descriptor: (Lio/github/mundanej/map/api/CrsProblem;)V
  public io.github.mundanej.map.api.CrsProblem problem();
    descriptor: ()Lio/github/mundanej/map/api/CrsProblem;
}
public final class io.github.mundanej.map.api.CrsKind extends java.lang.Enum<io.github.mundanej.map.api.CrsKind> {
  public static final io.github.mundanej.map.api.CrsKind GEOGRAPHIC;
    descriptor: Lio/github/mundanej/map/api/CrsKind;
  public static final io.github.mundanej.map.api.CrsKind PROJECTED;
    descriptor: Lio/github/mundanej/map/api/CrsKind;
  public static final io.github.mundanej.map.api.CrsKind UNKNOWN;
    descriptor: Lio/github/mundanej/map/api/CrsKind;
  public static io.github.mundanej.map.api.CrsKind[] values();
    descriptor: ()[Lio/github/mundanej/map/api/CrsKind;
  public static io.github.mundanej.map.api.CrsKind valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/CrsKind;
}
public final class io.github.mundanej.map.api.CrsMetadata {
  public static final int DECLARED_IDENTIFIER_LIMIT = 256;
    descriptor: I
  public static final int RETAINED_DEFINITION_LIMIT = 16384;
    descriptor: I
  public static io.github.mundanej.map.api.CrsMetadata recognized(io.github.mundanej.map.api.CrsDefinition, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>);
    descriptor: (Lio/github/mundanej/map/api/CrsDefinition;Ljava/util/Optional;Ljava/util/Optional;)Lio/github/mundanej/map/api/CrsMetadata;
  public static io.github.mundanej.map.api.CrsMetadata unknown(java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>);
    descriptor: (Ljava/util/Optional;Ljava/util/Optional;)Lio/github/mundanej/map/api/CrsMetadata;
  public java.util.Optional<io.github.mundanej.map.api.CrsDefinition> definition();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<java.lang.String> declaredIdentifier();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<java.lang.String> retainedDefinition();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<java.lang.String> canonicalIdentifier();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.CrsKind kind();
    descriptor: ()Lio/github/mundanej/map/api/CrsKind;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.CrsProblem extends java.lang.Record {
  public io.github.mundanej.map.api.CrsProblem(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.lang.String message();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.api.CrsUnit extends java.lang.Enum<io.github.mundanej.map.api.CrsUnit> {
  public static final io.github.mundanej.map.api.CrsUnit DEGREE;
    descriptor: Lio/github/mundanej/map/api/CrsUnit;
  public static final io.github.mundanej.map.api.CrsUnit METRE;
    descriptor: Lio/github/mundanej/map/api/CrsUnit;
  public static io.github.mundanej.map.api.CrsUnit[] values();
    descriptor: ()[Lio/github/mundanej/map/api/CrsUnit;
  public static io.github.mundanej.map.api.CrsUnit valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/CrsUnit;
}
public final class io.github.mundanej.map.api.DeleteFeature extends java.lang.Record implements io.github.mundanej.map.api.FeatureEditCommand {
  public io.github.mundanej.map.api.DeleteFeature(java.lang.String);
    descriptor: (Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.DiagnosticLocation extends java.lang.Record {
  public io.github.mundanej.map.api.DiagnosticLocation(java.util.Optional<java.lang.String>, java.util.OptionalLong, java.util.OptionalInt, java.util.OptionalInt, java.util.Optional<java.lang.String>, java.util.OptionalLong);
    descriptor: (Ljava/util/Optional;Ljava/util/OptionalLong;Ljava/util/OptionalInt;Ljava/util/OptionalInt;Ljava/util/Optional;Ljava/util/OptionalLong;)V
  public static io.github.mundanej.map.api.DiagnosticLocation empty();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticLocation;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<java.lang.String> component();
    descriptor: ()Ljava/util/Optional;
  public java.util.OptionalLong recordNumber();
    descriptor: ()Ljava/util/OptionalLong;
  public java.util.OptionalInt partIndex();
    descriptor: ()Ljava/util/OptionalInt;
  public java.util.OptionalInt fieldIndex();
    descriptor: ()Ljava/util/OptionalInt;
  public java.util.Optional<java.lang.String> fieldName();
    descriptor: ()Ljava/util/Optional;
  public java.util.OptionalLong byteOffset();
    descriptor: ()Ljava/util/OptionalLong;
}
public final class io.github.mundanej.map.api.DiagnosticReport extends java.lang.Record {
  public io.github.mundanej.map.api.DiagnosticReport(java.util.List<io.github.mundanej.map.api.SourceDiagnostic>, long);
    descriptor: (Ljava/util/List;J)V
  public static io.github.mundanej.map.api.DiagnosticReport empty();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.List<io.github.mundanej.map.api.SourceDiagnostic> entries();
    descriptor: ()Ljava/util/List;
  public long omittedWarningCount();
    descriptor: ()J
}
public final class io.github.mundanej.map.api.DiagnosticSeverity extends java.lang.Enum<io.github.mundanej.map.api.DiagnosticSeverity> {
  public static final io.github.mundanej.map.api.DiagnosticSeverity WARNING;
    descriptor: Lio/github/mundanej/map/api/DiagnosticSeverity;
  public static final io.github.mundanej.map.api.DiagnosticSeverity ERROR;
    descriptor: Lio/github/mundanej/map/api/DiagnosticSeverity;
  public static io.github.mundanej.map.api.DiagnosticSeverity[] values();
    descriptor: ()[Lio/github/mundanej/map/api/DiagnosticSeverity;
  public static io.github.mundanej.map.api.DiagnosticSeverity valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/DiagnosticSeverity;
}
public final class io.github.mundanej.map.api.DimensionalGeometry implements io.github.mundanej.map.api.Geometry {
  public static io.github.mundanej.map.api.DimensionalGeometry point(io.github.mundanej.map.api.CoordinateSequence);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;)Lio/github/mundanej/map/api/DimensionalGeometry;
  public static io.github.mundanej.map.api.DimensionalGeometry point(io.github.mundanej.map.api.CoordinateSequence, io.github.mundanej.map.api.GeometryLimits);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;Lio/github/mundanej/map/api/GeometryLimits;)Lio/github/mundanej/map/api/DimensionalGeometry;
  public static io.github.mundanej.map.api.DimensionalGeometry lineString(io.github.mundanej.map.api.CoordinateSequence);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;)Lio/github/mundanej/map/api/DimensionalGeometry;
  public static io.github.mundanej.map.api.DimensionalGeometry polygon(io.github.mundanej.map.api.CoordinateSequence, int[]);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;[I)Lio/github/mundanej/map/api/DimensionalGeometry;
  public static io.github.mundanej.map.api.DimensionalGeometry multiPoint(io.github.mundanej.map.api.CoordinateSequence);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;)Lio/github/mundanej/map/api/DimensionalGeometry;
  public static io.github.mundanej.map.api.DimensionalGeometry multiLineString(io.github.mundanej.map.api.CoordinateSequence, int[]);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;[I)Lio/github/mundanej/map/api/DimensionalGeometry;
  public static io.github.mundanej.map.api.DimensionalGeometry multiPolygon(io.github.mundanej.map.api.CoordinateSequence, int[], int[], io.github.mundanej.map.api.GeometryLimits);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;[I[ILio/github/mundanej/map/api/GeometryLimits;)Lio/github/mundanej/map/api/DimensionalGeometry;
  public io.github.mundanej.map.api.GeometryKind kind();
    descriptor: ()Lio/github/mundanej/map/api/GeometryKind;
  public io.github.mundanej.map.api.GeometryDimension dimension();
    descriptor: ()Lio/github/mundanej/map/api/GeometryDimension;
  public io.github.mundanej.map.api.CoordinateSequence coordinates();
    descriptor: ()Lio/github/mundanej/map/api/CoordinateSequence;
  public int[] partOffsets();
    descriptor: ()[I
  public int[] polygonPartOffsets();
    descriptor: ()[I
  public int partCount();
    descriptor: ()I
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.DistanceResult extends java.lang.Record {
  public static final io.github.mundanej.map.api.DistanceResult ZERO;
    descriptor: Lio/github/mundanej/map/api/DistanceResult;
  public io.github.mundanej.map.api.DistanceResult(double);
    descriptor: (D)V
  public io.github.mundanej.map.api.DistanceResult plus(io.github.mundanej.map.api.DistanceResult);
    descriptor: (Lio/github/mundanej/map/api/DistanceResult;)Lio/github/mundanej/map/api/DistanceResult;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double metres();
    descriptor: ()D
}
public interface io.github.mundanej.map.api.DistanceStrategy {
  public abstract io.github.mundanej.map.api.CrsDefinition coordinateCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public abstract io.github.mundanej.map.api.DistanceResult distance(io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/DistanceResult;
}
public final class io.github.mundanej.map.api.ElevationColorRamp {
  public static final int MINIMUM_STOPS = 2;
    descriptor: I
  public static final int MAXIMUM_STOPS = 256;
    descriptor: I
  public io.github.mundanej.map.api.ElevationColorRamp(io.github.mundanej.map.api.ElevationUnit, java.util.List<io.github.mundanej.map.api.ElevationColorStop>);
    descriptor: (Lio/github/mundanej/map/api/ElevationUnit;Ljava/util/List;)V
  public io.github.mundanej.map.api.ElevationUnit unit();
    descriptor: ()Lio/github/mundanej/map/api/ElevationUnit;
  public java.util.List<io.github.mundanej.map.api.ElevationColorStop> stops();
    descriptor: ()Ljava/util/List;
  public io.github.mundanej.map.api.Rgba colorAt(double);
    descriptor: (D)Lio/github/mundanej/map/api/Rgba;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.ElevationColorStop extends java.lang.Record {
  public io.github.mundanej.map.api.ElevationColorStop(double, io.github.mundanej.map.api.Rgba);
    descriptor: (DLio/github/mundanej/map/api/Rgba;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double elevation();
    descriptor: ()D
  public io.github.mundanej.map.api.Rgba color();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
}
public final class io.github.mundanej.map.api.ElevationHillshade extends java.lang.Record {
  public io.github.mundanej.map.api.ElevationHillshade(double, double, double);
    descriptor: (DDD)V
  public static io.github.mundanej.map.api.ElevationHillshade defaults();
    descriptor: ()Lio/github/mundanej/map/api/ElevationHillshade;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double azimuthDegrees();
    descriptor: ()D
  public double altitudeDegrees();
    descriptor: ()D
  public double verticalExaggeration();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.ElevationQueryMode extends java.lang.Enum<io.github.mundanej.map.api.ElevationQueryMode> {
  public static final io.github.mundanej.map.api.ElevationQueryMode NEAREST;
    descriptor: Lio/github/mundanej/map/api/ElevationQueryMode;
  public static final io.github.mundanej.map.api.ElevationQueryMode BILINEAR;
    descriptor: Lio/github/mundanej/map/api/ElevationQueryMode;
  public static io.github.mundanej.map.api.ElevationQueryMode[] values();
    descriptor: ()[Lio/github/mundanej/map/api/ElevationQueryMode;
  public static io.github.mundanej.map.api.ElevationQueryMode valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/ElevationQueryMode;
}
public final class io.github.mundanej.map.api.ElevationRasterStyle extends java.lang.Record {
  public io.github.mundanej.map.api.ElevationRasterStyle(io.github.mundanej.map.api.ElevationColorRamp, io.github.mundanej.map.api.Rgba, java.util.Optional<io.github.mundanej.map.api.ElevationHillshade>);
    descriptor: (Lio/github/mundanej/map/api/ElevationColorRamp;Lio/github/mundanej/map/api/Rgba;Ljava/util/Optional;)V
  public static io.github.mundanej.map.api.ElevationRasterStyle of(io.github.mundanej.map.api.ElevationColorRamp);
    descriptor: (Lio/github/mundanej/map/api/ElevationColorRamp;)Lio/github/mundanej/map/api/ElevationRasterStyle;
  public io.github.mundanej.map.api.ElevationRasterStyle withNoDataColor(io.github.mundanej.map.api.Rgba);
    descriptor: (Lio/github/mundanej/map/api/Rgba;)Lio/github/mundanej/map/api/ElevationRasterStyle;
  public io.github.mundanej.map.api.ElevationRasterStyle withHillshade(io.github.mundanej.map.api.ElevationHillshade);
    descriptor: (Lio/github/mundanej/map/api/ElevationHillshade;)Lio/github/mundanej/map/api/ElevationRasterStyle;
  public io.github.mundanej.map.api.ElevationRasterStyle withoutHillshade();
    descriptor: ()Lio/github/mundanej/map/api/ElevationRasterStyle;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.ElevationColorRamp colorRamp();
    descriptor: ()Lio/github/mundanej/map/api/ElevationColorRamp;
  public io.github.mundanej.map.api.Rgba noDataColor();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public java.util.Optional<io.github.mundanej.map.api.ElevationHillshade> hillshade();
    descriptor: ()Ljava/util/Optional;
}
public interface io.github.mundanej.map.api.ElevationSource extends java.lang.AutoCloseable {
  public abstract io.github.mundanej.map.api.ElevationSourceMetadata metadata();
    descriptor: ()Lio/github/mundanej/map/api/ElevationSourceMetadata;
  public abstract io.github.mundanej.map.api.ElevationSourceLimits limits();
    descriptor: ()Lio/github/mundanej/map/api/ElevationSourceLimits;
  public abstract io.github.mundanej.map.api.DiagnosticReport openingDiagnostics();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
  public abstract java.util.OptionalDouble sample(int, int);
    descriptor: (II)Ljava/util/OptionalDouble;
  public abstract boolean isClosed();
    descriptor: ()Z
  public abstract void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.api.ElevationSourceLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.ElevationSourceLimits DEFAULTS;
    descriptor: Lio/github/mundanej/map/api/ElevationSourceLimits;
  public io.github.mundanej.map.api.ElevationSourceLimits(int, int, long, long, int);
    descriptor: (IIJJI)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumColumns();
    descriptor: ()I
  public int maximumRows();
    descriptor: ()I
  public long maximumSamples();
    descriptor: ()J
  public long maximumRetainedSampleBytes();
    descriptor: ()J
  public int maximumRetainedWarnings();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.ElevationSourceMetadata extends java.lang.Record {
  public io.github.mundanej.map.api.ElevationSourceMetadata(io.github.mundanej.map.api.SourceIdentity, int, int, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.CrsMetadata, io.github.mundanej.map.api.ElevationUnit);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;IILio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/CrsMetadata;Lio/github/mundanej/map/api/ElevationUnit;)V
  public long sampleCount();
    descriptor: ()J
  public double columnSpacing();
    descriptor: ()D
  public double rowSpacing();
    descriptor: ()D
  public io.github.mundanej.map.api.Coordinate sampleCoordinate(int, int);
    descriptor: (II)Lio/github/mundanej/map/api/Coordinate;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.SourceIdentity identity();
    descriptor: ()Lio/github/mundanej/map/api/SourceIdentity;
  public int columnCount();
    descriptor: ()I
  public int rowCount();
    descriptor: ()I
  public io.github.mundanej.map.api.Envelope sampleBounds();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.CrsMetadata crs();
    descriptor: ()Lio/github/mundanej/map/api/CrsMetadata;
  public io.github.mundanej.map.api.ElevationUnit elevationUnit();
    descriptor: ()Lio/github/mundanej/map/api/ElevationUnit;
}
public final class io.github.mundanej.map.api.ElevationUnit extends java.lang.Enum<io.github.mundanej.map.api.ElevationUnit> {
  public static final io.github.mundanej.map.api.ElevationUnit METRE;
    descriptor: Lio/github/mundanej/map/api/ElevationUnit;
  public static final io.github.mundanej.map.api.ElevationUnit INTERNATIONAL_FOOT;
    descriptor: Lio/github/mundanej/map/api/ElevationUnit;
  public static final io.github.mundanej.map.api.ElevationUnit US_SURVEY_FOOT;
    descriptor: Lio/github/mundanej/map/api/ElevationUnit;
  public static io.github.mundanej.map.api.ElevationUnit[] values();
    descriptor: ()[Lio/github/mundanej/map/api/ElevationUnit;
  public static io.github.mundanej.map.api.ElevationUnit valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/ElevationUnit;
  public double metresPerUnit();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.ElevationValue extends java.lang.Record {
  public io.github.mundanej.map.api.ElevationValue(double, io.github.mundanej.map.api.ElevationUnit);
    descriptor: (DLio/github/mundanej/map/api/ElevationUnit;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double value();
    descriptor: ()D
  public io.github.mundanej.map.api.ElevationUnit unit();
    descriptor: ()Lio/github/mundanej/map/api/ElevationUnit;
}
public final class io.github.mundanej.map.api.EmptyGeometry extends java.lang.Record implements io.github.mundanej.map.api.Geometry {
  public io.github.mundanej.map.api.EmptyGeometry(io.github.mundanej.map.api.GeometryKind, io.github.mundanej.map.api.GeometryDimension);
    descriptor: (Lio/github/mundanej/map/api/GeometryKind;Lio/github/mundanej/map/api/GeometryDimension;)V
  public boolean isEmpty();
    descriptor: ()Z
  public java.util.Optional<io.github.mundanej.map.api.Envelope> bounds();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.GeometryKind kind();
    descriptor: ()Lio/github/mundanej/map/api/GeometryKind;
  public io.github.mundanej.map.api.GeometryDimension dimension();
    descriptor: ()Lio/github/mundanej/map/api/GeometryDimension;
}
public interface io.github.mundanej.map.api.EncodedRasterDecodeContext {
  public abstract io.github.mundanej.map.api.SourceIdentity sourceIdentity();
    descriptor: ()Lio/github/mundanej/map/api/SourceIdentity;
  public abstract io.github.mundanej.map.api.EncodedRasterFormat format();
    descriptor: ()Lio/github/mundanej/map/api/EncodedRasterFormat;
  public abstract long encodedByteLength();
    descriptor: ()J
  public abstract int width();
    descriptor: ()I
  public abstract int height();
    descriptor: ()I
  public abstract int channelCount();
    descriptor: ()I
  public abstract int bitsPerSample();
    descriptor: ()I
  public abstract io.github.mundanej.map.api.RasterWindow sourceWindow();
    descriptor: ()Lio/github/mundanej/map/api/RasterWindow;
  public abstract int outputWidth();
    descriptor: ()I
  public abstract int outputHeight();
    descriptor: ()I
  public default io.github.mundanej.map.api.RasterInterpolation interpolation();
    descriptor: ()Lio/github/mundanej/map/api/RasterInterpolation;
  public abstract void checkpoint();
    descriptor: ()V
  public abstract void claimReservedIntermediateBytes(long);
    descriptor: (J)V
}
public interface io.github.mundanej.map.api.EncodedRasterDecoder {
  public default boolean supportsInterpolation(io.github.mundanej.map.api.RasterInterpolation);
    descriptor: (Lio/github/mundanej/map/api/RasterInterpolation;)Z
  public abstract io.github.mundanej.map.api.RgbaPixelBuffer decode(java.io.InputStream, io.github.mundanej.map.api.EncodedRasterDecodeContext);
    descriptor: (Ljava/io/InputStream;Lio/github/mundanej/map/api/EncodedRasterDecodeContext;)Lio/github/mundanej/map/api/RgbaPixelBuffer;
}
public final class io.github.mundanej.map.api.EncodedRasterDecoderRegistry {
  public static io.github.mundanej.map.api.EncodedRasterDecoderRegistry$Builder builder();
    descriptor: ()Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry$Builder;
  public java.util.List<io.github.mundanej.map.api.EncodedRasterFormat> formats();
    descriptor: ()Ljava/util/List;
  public java.util.Optional<io.github.mundanej.map.api.EncodedRasterDecoder> find(io.github.mundanej.map.api.EncodedRasterFormat);
    descriptor: (Lio/github/mundanej/map/api/EncodedRasterFormat;)Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.EncodedRasterDecoderRegistry$Builder {
  public io.github.mundanej.map.api.EncodedRasterDecoderRegistry$Builder register(io.github.mundanej.map.api.EncodedRasterFormat, io.github.mundanej.map.api.EncodedRasterDecoder);
    descriptor: (Lio/github/mundanej/map/api/EncodedRasterFormat;Lio/github/mundanej/map/api/EncodedRasterDecoder;)Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry$Builder;
  public io.github.mundanej.map.api.EncodedRasterDecoderRegistry build();
    descriptor: ()Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry;
}
public final class io.github.mundanej.map.api.EncodedRasterDecoderRegistry$RegistrationException extends java.lang.IllegalArgumentException {
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.api.EncodedRasterFormat extends java.lang.Enum<io.github.mundanej.map.api.EncodedRasterFormat> {
  public static final io.github.mundanej.map.api.EncodedRasterFormat PNG;
    descriptor: Lio/github/mundanej/map/api/EncodedRasterFormat;
  public static final io.github.mundanej.map.api.EncodedRasterFormat JPEG;
    descriptor: Lio/github/mundanej/map/api/EncodedRasterFormat;
  public static io.github.mundanej.map.api.EncodedRasterFormat[] values();
    descriptor: ()[Lio/github/mundanej/map/api/EncodedRasterFormat;
  public static io.github.mundanej.map.api.EncodedRasterFormat valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/EncodedRasterFormat;
}
public final class io.github.mundanej.map.api.Envelope extends java.lang.Record {
  public io.github.mundanej.map.api.Envelope(double, double, double, double);
    descriptor: (DDDD)V
  public static io.github.mundanej.map.api.Envelope at(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Envelope;
  public double width();
    descriptor: ()D
  public double height();
    descriptor: ()D
  public io.github.mundanej.map.api.Coordinate center();
    descriptor: ()Lio/github/mundanej/map/api/Coordinate;
  public boolean contains(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Z
  public io.github.mundanej.map.api.Envelope union(io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/api/Envelope;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double minX();
    descriptor: ()D
  public double minY();
    descriptor: ()D
  public double maxX();
    descriptor: ()D
  public double maxY();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.Feature extends java.lang.Record {
  public io.github.mundanej.map.api.Feature(java.lang.String, java.lang.String, io.github.mundanej.map.api.Geometry, java.util.Map<java.lang.String, java.lang.Object>, io.github.mundanej.map.api.Symbol);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/Geometry;Ljava/util/Map;Lio/github/mundanej/map/api/Symbol;)V
  public java.util.Map<java.lang.String, java.lang.Object> attributes();
    descriptor: ()Ljava/util/Map;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.Geometry geometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
  public io.github.mundanej.map.api.Symbol symbol();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
}
public interface io.github.mundanej.map.api.FeatureCursor extends java.lang.AutoCloseable {
  public abstract boolean advance();
    descriptor: ()Z
  public abstract io.github.mundanej.map.api.FeatureRecord current();
    descriptor: ()Lio/github/mundanej/map/api/FeatureRecord;
  public abstract io.github.mundanej.map.api.DiagnosticReport diagnostics();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
  public abstract boolean isClosed();
    descriptor: ()Z
  public abstract void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.api.FeatureEditCause extends java.lang.Enum<io.github.mundanej.map.api.FeatureEditCause> {
  public static final io.github.mundanej.map.api.FeatureEditCause COMMIT;
    descriptor: Lio/github/mundanej/map/api/FeatureEditCause;
  public static final io.github.mundanej.map.api.FeatureEditCause UNDO;
    descriptor: Lio/github/mundanej/map/api/FeatureEditCause;
  public static final io.github.mundanej.map.api.FeatureEditCause REDO;
    descriptor: Lio/github/mundanej/map/api/FeatureEditCause;
  public static io.github.mundanej.map.api.FeatureEditCause[] values();
    descriptor: ()[Lio/github/mundanej/map/api/FeatureEditCause;
  public static io.github.mundanej.map.api.FeatureEditCause valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/FeatureEditCause;
}
public interface io.github.mundanej.map.api.FeatureEditCommand {
  public abstract java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.FeatureEditConfigurationException extends java.lang.IllegalArgumentException {
  public io.github.mundanej.map.api.FeatureEditConfigurationException(io.github.mundanej.map.api.FeatureEditProblem);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditProblem;)V
  public io.github.mundanej.map.api.FeatureEditProblem problem();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditProblem;
}
public final class io.github.mundanej.map.api.FeatureEditEvent extends java.lang.Record {
  public io.github.mundanej.map.api.FeatureEditEvent(io.github.mundanej.map.api.FeatureEditCause, io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditSnapshot, java.lang.String);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditCause;Lio/github/mundanej/map/api/FeatureEditSnapshot;Lio/github/mundanej/map/api/FeatureEditSnapshot;Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.FeatureEditCause cause();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditCause;
  public io.github.mundanej.map.api.FeatureEditSnapshot previous();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditSnapshot;
  public io.github.mundanej.map.api.FeatureEditSnapshot current();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditSnapshot;
  public java.lang.String description();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.FeatureEditHistoryLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.FeatureEditHistoryLimits DEFAULT;
    descriptor: Lio/github/mundanej/map/api/FeatureEditHistoryLimits;
  public io.github.mundanej.map.api.FeatureEditHistoryLimits(int, long);
    descriptor: (IJ)V
  public io.github.mundanej.map.api.FeatureEditHistoryLimits withMaximumEntries(int);
    descriptor: (I)Lio/github/mundanej/map/api/FeatureEditHistoryLimits;
  public io.github.mundanej.map.api.FeatureEditHistoryLimits withMaximumBytes(long);
    descriptor: (J)Lio/github/mundanej/map/api/FeatureEditHistoryLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumEntries();
    descriptor: ()I
  public long maximumBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.api.FeatureEditLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.FeatureEditLimits DEFAULT;
    descriptor: Lio/github/mundanej/map/api/FeatureEditLimits;
  public io.github.mundanej.map.api.FeatureEditLimits(int, int, long);
    descriptor: (IIJ)V
  public io.github.mundanej.map.api.FeatureEditLimits withMaximumFeatures(int);
    descriptor: (I)Lio/github/mundanej/map/api/FeatureEditLimits;
  public io.github.mundanej.map.api.FeatureEditLimits withMaximumCommandsPerTransaction(int);
    descriptor: (I)Lio/github/mundanej/map/api/FeatureEditLimits;
  public io.github.mundanej.map.api.FeatureEditLimits withMaximumSnapshotBytes(long);
    descriptor: (J)Lio/github/mundanej/map/api/FeatureEditLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumFeatures();
    descriptor: ()I
  public int maximumCommandsPerTransaction();
    descriptor: ()I
  public long maximumSnapshotBytes();
    descriptor: ()J
}
public interface io.github.mundanej.map.api.FeatureEditListener {
  public abstract void onFeatureEdit(io.github.mundanej.map.api.FeatureEditEvent);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditEvent;)V
}
public final class io.github.mundanej.map.api.FeatureEditNotificationException extends java.lang.RuntimeException {
  public io.github.mundanej.map.api.FeatureEditNotificationException(io.github.mundanej.map.api.FeatureEditResult, java.lang.RuntimeException);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditResult;Ljava/lang/RuntimeException;)V
  public io.github.mundanej.map.api.FeatureEditResult committedResult();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditResult;
}
public final class io.github.mundanej.map.api.FeatureEditProblem extends java.lang.Record {
  public io.github.mundanej.map.api.FeatureEditProblem(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.lang.String message();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.api.FeatureEditResult extends java.lang.Record {
  public io.github.mundanej.map.api.FeatureEditResult(io.github.mundanej.map.api.FeatureEditStatus, io.github.mundanej.map.api.FeatureEditSnapshot, java.util.Optional<io.github.mundanej.map.api.FeatureEditProblem>);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditStatus;Lio/github/mundanej/map/api/FeatureEditSnapshot;Ljava/util/Optional;)V
  public static io.github.mundanej.map.api.FeatureEditResult applied(io.github.mundanej.map.api.FeatureEditSnapshot);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditSnapshot;)Lio/github/mundanej/map/api/FeatureEditResult;
  public static io.github.mundanej.map.api.FeatureEditResult unchanged(io.github.mundanej.map.api.FeatureEditSnapshot);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditSnapshot;)Lio/github/mundanej/map/api/FeatureEditResult;
  public static io.github.mundanej.map.api.FeatureEditResult rejected(io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditProblem);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditSnapshot;Lio/github/mundanej/map/api/FeatureEditProblem;)Lio/github/mundanej/map/api/FeatureEditResult;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.FeatureEditStatus status();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditStatus;
  public io.github.mundanej.map.api.FeatureEditSnapshot snapshot();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditSnapshot;
  public java.util.Optional<io.github.mundanej.map.api.FeatureEditProblem> problem();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.FeatureEditSnapshot extends java.lang.Record {
  public io.github.mundanej.map.api.FeatureEditSnapshot(long, io.github.mundanej.map.api.CrsDefinition, java.util.List<io.github.mundanej.map.api.FeatureRecord>);
    descriptor: (JLio/github/mundanej/map/api/CrsDefinition;Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long revision();
    descriptor: ()J
  public io.github.mundanej.map.api.CrsDefinition crs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public java.util.List<io.github.mundanej.map.api.FeatureRecord> records();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.api.FeatureEditStatus extends java.lang.Enum<io.github.mundanej.map.api.FeatureEditStatus> {
  public static final io.github.mundanej.map.api.FeatureEditStatus APPLIED;
    descriptor: Lio/github/mundanej/map/api/FeatureEditStatus;
  public static final io.github.mundanej.map.api.FeatureEditStatus UNCHANGED;
    descriptor: Lio/github/mundanej/map/api/FeatureEditStatus;
  public static final io.github.mundanej.map.api.FeatureEditStatus REJECTED;
    descriptor: Lio/github/mundanej/map/api/FeatureEditStatus;
  public static io.github.mundanej.map.api.FeatureEditStatus[] values();
    descriptor: ()[Lio/github/mundanej/map/api/FeatureEditStatus;
  public static io.github.mundanej.map.api.FeatureEditStatus valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/FeatureEditStatus;
}
public final class io.github.mundanej.map.api.FeatureEditTransaction extends java.lang.Record {
  public static final int MAXIMUM_DESCRIPTION_LENGTH = 128;
    descriptor: I
  public io.github.mundanej.map.api.FeatureEditTransaction(long, java.lang.String, java.util.List<io.github.mundanej.map.api.FeatureEditCommand>);
    descriptor: (JLjava/lang/String;Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long expectedRevision();
    descriptor: ()J
  public java.lang.String description();
    descriptor: ()Ljava/lang/String;
  public java.util.List<io.github.mundanej.map.api.FeatureEditCommand> commands();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.api.FeatureName extends java.lang.Enum<io.github.mundanej.map.api.FeatureName> implements io.github.mundanej.map.api.LabelTextSource {
  public static final io.github.mundanej.map.api.FeatureName INSTANCE;
    descriptor: Lio/github/mundanej/map/api/FeatureName;
  public static io.github.mundanej.map.api.FeatureName[] values();
    descriptor: ()[Lio/github/mundanej/map/api/FeatureName;
  public static io.github.mundanej.map.api.FeatureName valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/FeatureName;
}
public final class io.github.mundanej.map.api.FeatureOverlaySymbols extends java.lang.Record {
  public io.github.mundanej.map.api.FeatureOverlaySymbols(io.github.mundanej.map.api.MarkerSymbol, io.github.mundanej.map.api.LineSymbol, io.github.mundanej.map.api.FillSymbol);
    descriptor: (Lio/github/mundanej/map/api/MarkerSymbol;Lio/github/mundanej/map/api/LineSymbol;Lio/github/mundanej/map/api/FillSymbol;)V
  public static io.github.mundanej.map.api.FeatureOverlaySymbols defaultHover();
    descriptor: ()Lio/github/mundanej/map/api/FeatureOverlaySymbols;
  public static io.github.mundanej.map.api.FeatureOverlaySymbols defaultSelection();
    descriptor: ()Lio/github/mundanej/map/api/FeatureOverlaySymbols;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.MarkerSymbol marker();
    descriptor: ()Lio/github/mundanej/map/api/MarkerSymbol;
  public io.github.mundanej.map.api.LineSymbol line();
    descriptor: ()Lio/github/mundanej/map/api/LineSymbol;
  public io.github.mundanej.map.api.FillSymbol fill();
    descriptor: ()Lio/github/mundanej/map/api/FillSymbol;
}
public final class io.github.mundanej.map.api.FeaturePortrayal {
  public io.github.mundanej.map.api.FeaturePortrayal(java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>);
    descriptor: (Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;)V
  public io.github.mundanej.map.api.FeaturePortrayal(java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<io.github.mundanej.map.api.PointLabelProfile>);
    descriptor: (Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;)V
  public static io.github.mundanej.map.api.FeaturePortrayal fixed(io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol);
    descriptor: (Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/Symbol;)Lio/github/mundanej/map/api/FeaturePortrayal;
  public static io.github.mundanej.map.api.FeaturePortrayal markers(io.github.mundanej.map.api.SymbolSelector);
    descriptor: (Lio/github/mundanej/map/api/SymbolSelector;)Lio/github/mundanej/map/api/FeaturePortrayal;
  public java.util.Optional<io.github.mundanej.map.api.SymbolSelector> marker();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.SymbolSelector> line();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.SymbolSelector> fill();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.PointLabelProfile> pointLabel();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.FeaturePortrayal withPointLabel(io.github.mundanej.map.api.PointLabelProfile);
    descriptor: (Lio/github/mundanej/map/api/PointLabelProfile;)Lio/github/mundanej/map/api/FeaturePortrayal;
  public java.util.List<io.github.mundanej.map.api.SymbolSelector> selectors();
    descriptor: ()Ljava/util/List;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.FeatureQuery extends java.lang.Record {
  public io.github.mundanej.map.api.FeatureQuery(java.util.Optional<io.github.mundanej.map.api.Envelope>, io.github.mundanej.map.api.AttributeSelection, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>);
    descriptor: (Ljava/util/Optional;Lio/github/mundanej/map/api/AttributeSelection;Ljava/util/Optional;)V
  public static io.github.mundanej.map.api.FeatureQuery all();
    descriptor: ()Lio/github/mundanej/map/api/FeatureQuery;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<io.github.mundanej.map.api.Envelope> sourceBounds();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.AttributeSelection attributes();
    descriptor: ()Lio/github/mundanej/map/api/AttributeSelection;
  public java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits> tighterLimits();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.FeatureQueryLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.FeatureQueryLimits LEVEL_1;
    descriptor: Lio/github/mundanej/map/api/FeatureQueryLimits;
  public io.github.mundanej.map.api.FeatureQueryLimits(long, long, long, long, long, long, int);
    descriptor: (JJJJJJI)V
  public boolean tightens(io.github.mundanej.map.api.FeatureQueryLimits);
    descriptor: (Lio/github/mundanej/map/api/FeatureQueryLimits;)Z
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long recordsExamined();
    descriptor: ()J
  public long recordsReturned();
    descriptor: ()J
  public long coordinatesReturned();
    descriptor: ()J
  public long attributeValuesReturned();
    descriptor: ()J
  public long decodedTextCharactersReturned();
    descriptor: ()J
  public long ownedPayloadBytes();
    descriptor: ()J
  public int retainedWarnings();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.FeatureRecord extends java.lang.Record {
  public io.github.mundanej.map.api.FeatureRecord(java.lang.String, java.lang.String, io.github.mundanej.map.api.Geometry, java.util.Map<java.lang.String, java.lang.Object>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/Geometry;Ljava/util/Map;)V
  public java.util.Map<java.lang.String, java.lang.Object> attributes();
    descriptor: ()Ljava/util/Map;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.Geometry geometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
}
public final class io.github.mundanej.map.api.FeatureSelection extends java.lang.Record {
  public io.github.mundanej.map.api.FeatureSelection(java.lang.String, java.lang.String);
    descriptor: (Ljava/lang/String;Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String layerId();
    descriptor: ()Ljava/lang/String;
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
}
public interface io.github.mundanej.map.api.FeatureSource extends java.lang.AutoCloseable {
  public abstract io.github.mundanej.map.api.FeatureSourceMetadata metadata();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSourceMetadata;
  public abstract io.github.mundanej.map.api.FeatureSourceLimits limits();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSourceLimits;
  public abstract io.github.mundanej.map.api.DiagnosticReport openingDiagnostics();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
  public abstract io.github.mundanej.map.api.FeatureCursor openCursor(io.github.mundanej.map.api.FeatureQuery, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/FeatureQuery;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/FeatureCursor;
  public abstract boolean isClosed();
    descriptor: ()Z
  public abstract void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.api.FeatureSourceLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.FeatureSourceLimits LEVEL_1;
    descriptor: Lio/github/mundanej/map/api/FeatureSourceLimits;
  public io.github.mundanej.map.api.FeatureSourceLimits(io.github.mundanej.map.api.FeatureQueryLimits);
    descriptor: (Lio/github/mundanej/map/api/FeatureQueryLimits;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.FeatureQueryLimits queryLimits();
    descriptor: ()Lio/github/mundanej/map/api/FeatureQueryLimits;
}
public final class io.github.mundanej.map.api.FeatureSourceMetadata extends java.lang.Record {
  public io.github.mundanej.map.api.FeatureSourceMetadata(io.github.mundanej.map.api.SourceIdentity, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.OptionalLong, java.util.Optional<io.github.mundanej.map.api.AttributeSchema>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/util/Optional;Ljava/util/OptionalLong;Ljava/util/Optional;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.SourceIdentity identity();
    descriptor: ()Lio/github/mundanej/map/api/SourceIdentity;
  public java.util.Optional<io.github.mundanej.map.api.Envelope> extent();
    descriptor: ()Ljava/util/Optional;
  public java.util.OptionalLong featureCount();
    descriptor: ()Ljava/util/OptionalLong;
  public java.util.Optional<io.github.mundanej.map.api.AttributeSchema> schema();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.CrsMetadata> crs();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.FeatureStyle extends java.lang.Record implements io.github.mundanej.map.api.Symbol {
  public static final io.github.mundanej.map.api.SymbolRendererKey RENDERER_KEY;
    descriptor: Lio/github/mundanej/map/api/SymbolRendererKey;
  public io.github.mundanej.map.api.FeatureStyle(io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.Rgba, double, double);
    descriptor: (Lio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/Rgba;DD)V
  public static io.github.mundanej.map.api.FeatureStyle point(io.github.mundanej.map.api.Rgba, double);
    descriptor: (Lio/github/mundanej/map/api/Rgba;D)Lio/github/mundanej/map/api/FeatureStyle;
  public static io.github.mundanej.map.api.FeatureStyle line(io.github.mundanej.map.api.Rgba, double);
    descriptor: (Lio/github/mundanej/map/api/Rgba;D)Lio/github/mundanej/map/api/FeatureStyle;
  public static io.github.mundanej.map.api.FeatureStyle polygon(io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.Rgba, double);
    descriptor: (Lio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/Rgba;D)Lio/github/mundanej/map/api/FeatureStyle;
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public double opacity();
    descriptor: ()D
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Rgba stroke();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.Rgba fill();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public double strokeWidth();
    descriptor: ()D
  public double pointDiameter();
    descriptor: ()D
}
public interface io.github.mundanej.map.api.FillSymbol extends io.github.mundanej.map.api.Symbol {
  public default io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
}
public final class io.github.mundanej.map.api.FilteredSymbolSelector extends java.lang.Record implements io.github.mundanej.map.api.SymbolSelector {
  public io.github.mundanej.map.api.FilteredSymbolSelector(io.github.mundanej.map.api.PortrayalPredicate, io.github.mundanej.map.api.SymbolSelector);
    descriptor: (Lio/github/mundanej/map/api/PortrayalPredicate;Lio/github/mundanej/map/api/SymbolSelector;)V
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.PortrayalPredicate predicate();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalPredicate;
  public io.github.mundanej.map.api.SymbolSelector delegate();
    descriptor: ()Lio/github/mundanej/map/api/SymbolSelector;
}
public final class io.github.mundanej.map.api.FixedSymbolSelector extends java.lang.Record implements io.github.mundanej.map.api.SymbolSelector {
  public io.github.mundanej.map.api.FixedSymbolSelector(io.github.mundanej.map.api.Symbol);
    descriptor: (Lio/github/mundanej/map/api/Symbol;)V
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Symbol symbol();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
}
public interface io.github.mundanej.map.api.Geometry {
  public default io.github.mundanej.map.api.GeometryKind kind();
    descriptor: ()Lio/github/mundanej/map/api/GeometryKind;
  public default io.github.mundanej.map.api.GeometryDimension dimension();
    descriptor: ()Lio/github/mundanej/map/api/GeometryDimension;
  public default boolean isEmpty();
    descriptor: ()Z
  public default java.util.Optional<io.github.mundanej.map.api.Envelope> bounds();
    descriptor: ()Ljava/util/Optional;
  public abstract io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public default void visit(io.github.mundanej.map.api.GeometryVisitor);
    descriptor: (Lio/github/mundanej/map/api/GeometryVisitor;)V
}
public final class io.github.mundanej.map.api.GeometryCollection implements io.github.mundanej.map.api.Geometry {
  public static io.github.mundanej.map.api.GeometryCollection of(java.util.List<? extends io.github.mundanej.map.api.Geometry>);
    descriptor: (Ljava/util/List;)Lio/github/mundanej/map/api/GeometryCollection;
  public static io.github.mundanej.map.api.GeometryCollection of(java.util.List<? extends io.github.mundanej.map.api.Geometry>, io.github.mundanej.map.api.GeometryLimits);
    descriptor: (Ljava/util/List;Lio/github/mundanej/map/api/GeometryLimits;)Lio/github/mundanej/map/api/GeometryCollection;
  public static io.github.mundanej.map.api.GeometryCollection empty(io.github.mundanej.map.api.GeometryDimension);
    descriptor: (Lio/github/mundanej/map/api/GeometryDimension;)Lio/github/mundanej/map/api/GeometryCollection;
  public java.util.List<io.github.mundanej.map.api.Geometry> geometries();
    descriptor: ()Ljava/util/List;
  public io.github.mundanej.map.api.GeometryKind kind();
    descriptor: ()Lio/github/mundanej/map/api/GeometryKind;
  public io.github.mundanej.map.api.GeometryDimension dimension();
    descriptor: ()Lio/github/mundanej/map/api/GeometryDimension;
  public boolean isEmpty();
    descriptor: ()Z
  public java.util.Optional<io.github.mundanej.map.api.Envelope> bounds();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.GeometryDimension extends java.lang.Enum<io.github.mundanej.map.api.GeometryDimension> {
  public static final io.github.mundanej.map.api.GeometryDimension XY;
    descriptor: Lio/github/mundanej/map/api/GeometryDimension;
  public static final io.github.mundanej.map.api.GeometryDimension XYZ;
    descriptor: Lio/github/mundanej/map/api/GeometryDimension;
  public static final io.github.mundanej.map.api.GeometryDimension XYM;
    descriptor: Lio/github/mundanej/map/api/GeometryDimension;
  public static final io.github.mundanej.map.api.GeometryDimension XYZM;
    descriptor: Lio/github/mundanej/map/api/GeometryDimension;
  public static io.github.mundanej.map.api.GeometryDimension[] values();
    descriptor: ()[Lio/github/mundanej/map/api/GeometryDimension;
  public static io.github.mundanej.map.api.GeometryDimension valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/GeometryDimension;
  public int stride();
    descriptor: ()I
  public boolean hasZ();
    descriptor: ()Z
  public boolean hasM();
    descriptor: ()Z
  public int zOffset();
    descriptor: ()I
  public int mOffset();
    descriptor: ()I
  public io.github.mundanej.map.api.GeometryDimension union(io.github.mundanej.map.api.GeometryDimension);
    descriptor: (Lio/github/mundanej/map/api/GeometryDimension;)Lio/github/mundanej/map/api/GeometryDimension;
}
public final class io.github.mundanej.map.api.GeometryException extends java.lang.IllegalArgumentException {
  public static final java.lang.String EMPTY_ENVELOPE = "GEOMETRY_EMPTY_ENVELOPE";
    descriptor: Ljava/lang/String;
  public static final java.lang.String ORDINATE_ABSENT = "GEOMETRY_ORDINATE_ABSENT";
    descriptor: Ljava/lang/String;
  public static final java.lang.String LIMIT_EXCEEDED = "GEOMETRY_LIMIT_EXCEEDED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String ORDINATE_LOSS_REJECTED = "GEOMETRY_ORDINATE_LOSS_REJECTED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String KIND_UNSUPPORTED = "GEOMETRY_KIND_UNSUPPORTED";
    descriptor: Ljava/lang/String;
  public io.github.mundanej.map.api.GeometryException(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.api.GeometryKind extends java.lang.Enum<io.github.mundanej.map.api.GeometryKind> {
  public static final io.github.mundanej.map.api.GeometryKind POINT;
    descriptor: Lio/github/mundanej/map/api/GeometryKind;
  public static final io.github.mundanej.map.api.GeometryKind LINE_STRING;
    descriptor: Lio/github/mundanej/map/api/GeometryKind;
  public static final io.github.mundanej.map.api.GeometryKind POLYGON;
    descriptor: Lio/github/mundanej/map/api/GeometryKind;
  public static final io.github.mundanej.map.api.GeometryKind MULTI_POINT;
    descriptor: Lio/github/mundanej/map/api/GeometryKind;
  public static final io.github.mundanej.map.api.GeometryKind MULTI_LINE_STRING;
    descriptor: Lio/github/mundanej/map/api/GeometryKind;
  public static final io.github.mundanej.map.api.GeometryKind MULTI_POLYGON;
    descriptor: Lio/github/mundanej/map/api/GeometryKind;
  public static final io.github.mundanej.map.api.GeometryKind GEOMETRY_COLLECTION;
    descriptor: Lio/github/mundanej/map/api/GeometryKind;
  public static io.github.mundanej.map.api.GeometryKind[] values();
    descriptor: ()[Lio/github/mundanej/map/api/GeometryKind;
  public static io.github.mundanej.map.api.GeometryKind valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/GeometryKind;
}
public final class io.github.mundanej.map.api.GeometryLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.GeometryLimits DEFAULT;
    descriptor: Lio/github/mundanej/map/api/GeometryLimits;
  public io.github.mundanej.map.api.GeometryLimits(long, long, long, int);
    descriptor: (JJJI)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long maxCoordinates();
    descriptor: ()J
  public long maxParts();
    descriptor: ()J
  public long maxCollectionElements();
    descriptor: ()J
  public int maxDepth();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.GeometryTraversal {
  public static void visit(io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.GeometryVisitor, io.github.mundanej.map.api.GeometryLimits);
    descriptor: (Lio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/api/GeometryVisitor;Lio/github/mundanej/map/api/GeometryLimits;)V
}
public interface io.github.mundanej.map.api.GeometryVisitor {
  public abstract void visit(io.github.mundanej.map.api.Geometry, int);
    descriptor: (Lio/github/mundanej/map/api/Geometry;I)V
}
public final class io.github.mundanej.map.api.GraduatedSymbolSelector implements io.github.mundanej.map.api.SymbolSelector {
  public static final int MAXIMUM_STEPS = 64;
    descriptor: I
  public io.github.mundanej.map.api.GraduatedSymbolSelector(java.lang.String, java.util.List<io.github.mundanej.map.api.GraduatedSymbolStep>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>);
    descriptor: (Ljava/lang/String;Ljava/util/List;Ljava/util/Optional;)V
  public static io.github.mundanej.map.api.GraduatedSymbolSelector expressionInput(java.lang.String, java.util.List<io.github.mundanej.map.api.GraduatedSymbolStep>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>, io.github.mundanej.map.api.AttributeValueConversion);
    descriptor: (Ljava/lang/String;Ljava/util/List;Ljava/util/Optional;Ljava/util/Optional;Lio/github/mundanej/map/api/AttributeValueConversion;)Lio/github/mundanej/map/api/GraduatedSymbolSelector;
  public static io.github.mundanej.map.api.GraduatedSymbolSelector zoom(java.util.List<io.github.mundanej.map.api.GraduatedSymbolStep>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>);
    descriptor: (Ljava/util/List;Ljava/util/Optional;Ljava/util/Optional;)Lio/github/mundanej/map/api/GraduatedSymbolSelector;
  public java.lang.String attribute();
    descriptor: ()Ljava/lang/String;
  public java.util.List<io.github.mundanej.map.api.GraduatedSymbolStep> steps();
    descriptor: ()Ljava/util/List;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> fallback();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> invalidFallback();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.InterpolationInput input();
    descriptor: ()Lio/github/mundanej/map/api/InterpolationInput;
  public io.github.mundanej.map.api.AttributeValueConversion conversion();
    descriptor: ()Lio/github/mundanej/map/api/AttributeValueConversion;
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.GraduatedSymbolStep extends java.lang.Record {
  public io.github.mundanej.map.api.GraduatedSymbolStep(java.math.BigDecimal, io.github.mundanej.map.api.Symbol);
    descriptor: (Ljava/math/BigDecimal;Lio/github/mundanej/map/api/Symbol;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.math.BigDecimal lowerInclusive();
    descriptor: ()Ljava/math/BigDecimal;
  public io.github.mundanej.map.api.Symbol symbol();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
}
public final class io.github.mundanej.map.api.GraphicPaint extends java.lang.Record {
  public io.github.mundanej.map.api.GraphicPaint(io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.SymbolSize, io.github.mundanej.map.api.SymbolLength, double, double);
    descriptor: (Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/SymbolSize;Lio/github/mundanej/map/api/SymbolLength;DD)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Symbol graphic();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
  public io.github.mundanej.map.api.SymbolSize size();
    descriptor: ()Lio/github/mundanej/map/api/SymbolSize;
  public io.github.mundanej.map.api.SymbolLength gap();
    descriptor: ()Lio/github/mundanej/map/api/SymbolLength;
  public double rotationDegrees();
    descriptor: ()D
  public double opacity();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.HatchFillSymbol implements io.github.mundanej.map.api.FillSymbol {
  public static final int DEFAULT_MAX_SEGMENTS = 8192;
    descriptor: I
  public static final io.github.mundanej.map.api.SymbolRendererKey RENDERER_KEY;
    descriptor: Lio/github/mundanej/map/api/SymbolRendererKey;
  public static io.github.mundanej.map.api.HatchFillSymbol of(io.github.mundanej.map.api.HatchPattern, io.github.mundanej.map.api.SymbolStroke, io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.api.SymbolRotationMode, java.util.Optional<io.github.mundanej.map.api.Symbol>, double, int);
    descriptor: (Lio/github/mundanej/map/api/HatchPattern;Lio/github/mundanej/map/api/SymbolStroke;Lio/github/mundanej/map/api/SymbolLength;Lio/github/mundanej/map/api/SymbolRotationMode;Ljava/util/Optional;DI)Lio/github/mundanej/map/api/HatchFillSymbol;
  public static io.github.mundanej.map.api.HatchFillSymbol of(io.github.mundanej.map.api.HatchPattern, io.github.mundanej.map.api.SymbolStroke, io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.api.SymbolRotationMode, double);
    descriptor: (Lio/github/mundanej/map/api/HatchPattern;Lio/github/mundanej/map/api/SymbolStroke;Lio/github/mundanej/map/api/SymbolLength;Lio/github/mundanej/map/api/SymbolRotationMode;D)Lio/github/mundanej/map/api/HatchFillSymbol;
  public io.github.mundanej.map.api.HatchPattern pattern();
    descriptor: ()Lio/github/mundanej/map/api/HatchPattern;
  public io.github.mundanej.map.api.SymbolStroke stroke();
    descriptor: ()Lio/github/mundanej/map/api/SymbolStroke;
  public io.github.mundanej.map.api.SymbolLength spacing();
    descriptor: ()Lio/github/mundanej/map/api/SymbolLength;
  public io.github.mundanej.map.api.SymbolRotationMode rotationMode();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRotationMode;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> outline();
    descriptor: ()Ljava/util/Optional;
  public double opacity();
    descriptor: ()D
  public int maxSegments();
    descriptor: ()I
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.HatchPattern extends java.lang.Enum<io.github.mundanej.map.api.HatchPattern> {
  public static final io.github.mundanej.map.api.HatchPattern FORWARD_DIAGONAL;
    descriptor: Lio/github/mundanej/map/api/HatchPattern;
  public static final io.github.mundanej.map.api.HatchPattern BACKWARD_DIAGONAL;
    descriptor: Lio/github/mundanej/map/api/HatchPattern;
  public static final io.github.mundanej.map.api.HatchPattern CROSS_DIAGONAL;
    descriptor: Lio/github/mundanej/map/api/HatchPattern;
  public static io.github.mundanej.map.api.HatchPattern[] values();
    descriptor: ()[Lio/github/mundanej/map/api/HatchPattern;
  public static io.github.mundanej.map.api.HatchPattern valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/HatchPattern;
}
public final class io.github.mundanej.map.api.InterpolatedSymbolSelector implements io.github.mundanej.map.api.SymbolSelector {
  public static final int MAXIMUM_STOPS = 64;
    descriptor: I
  public static io.github.mundanej.map.api.InterpolatedSymbolSelector attribute(java.lang.String, java.util.List<io.github.mundanej.map.api.InterpolatedSymbolStop>, io.github.mundanej.map.api.Symbol);
    descriptor: (Ljava/lang/String;Ljava/util/List;Lio/github/mundanej/map/api/Symbol;)Lio/github/mundanej/map/api/InterpolatedSymbolSelector;
  public static io.github.mundanej.map.api.InterpolatedSymbolSelector expressionInput(java.lang.String, java.util.List<io.github.mundanej.map.api.InterpolatedSymbolStop>, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.AttributeValueConversion);
    descriptor: (Ljava/lang/String;Ljava/util/List;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/AttributeValueConversion;)Lio/github/mundanej/map/api/InterpolatedSymbolSelector;
  public static io.github.mundanej.map.api.InterpolatedSymbolSelector zoom(java.util.List<io.github.mundanej.map.api.InterpolatedSymbolStop>, io.github.mundanej.map.api.Symbol);
    descriptor: (Ljava/util/List;Lio/github/mundanej/map/api/Symbol;)Lio/github/mundanej/map/api/InterpolatedSymbolSelector;
  public io.github.mundanej.map.api.InterpolationInput input();
    descriptor: ()Lio/github/mundanej/map/api/InterpolationInput;
  public java.util.Optional<java.lang.String> attribute();
    descriptor: ()Ljava/util/Optional;
  public java.util.List<io.github.mundanej.map.api.InterpolatedSymbolStop> stops();
    descriptor: ()Ljava/util/List;
  public io.github.mundanej.map.api.Symbol fallback();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
  public io.github.mundanej.map.api.AttributeValueConversion conversion();
    descriptor: ()Lio/github/mundanej/map/api/AttributeValueConversion;
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.InterpolatedSymbolStop extends java.lang.Record {
  public io.github.mundanej.map.api.InterpolatedSymbolStop(java.math.BigDecimal, io.github.mundanej.map.api.Symbol);
    descriptor: (Ljava/math/BigDecimal;Lio/github/mundanej/map/api/Symbol;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.math.BigDecimal input();
    descriptor: ()Ljava/math/BigDecimal;
  public io.github.mundanej.map.api.Symbol symbol();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
}
public final class io.github.mundanej.map.api.InterpolationInput extends java.lang.Enum<io.github.mundanej.map.api.InterpolationInput> {
  public static final io.github.mundanej.map.api.InterpolationInput ATTRIBUTE;
    descriptor: Lio/github/mundanej/map/api/InterpolationInput;
  public static final io.github.mundanej.map.api.InterpolationInput ZOOM;
    descriptor: Lio/github/mundanej/map/api/InterpolationInput;
  public static io.github.mundanej.map.api.InterpolationInput[] values();
    descriptor: ()[Lio/github/mundanej/map/api/InterpolationInput;
  public static io.github.mundanej.map.api.InterpolationInput valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/InterpolationInput;
}
public final class io.github.mundanej.map.api.LabelPlacementException extends java.lang.RuntimeException {
  public io.github.mundanej.map.api.LabelPlacementException(io.github.mundanej.map.api.LabelPlacementProblem);
    descriptor: (Lio/github/mundanej/map/api/LabelPlacementProblem;)V
  public io.github.mundanej.map.api.LabelPlacementProblem problem();
    descriptor: ()Lio/github/mundanej/map/api/LabelPlacementProblem;
}
public final class io.github.mundanej.map.api.LabelPlacementProblem extends java.lang.Record {
  public io.github.mundanej.map.api.LabelPlacementProblem(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.lang.String message();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public interface io.github.mundanej.map.api.LabelTextSource {
}
public final class io.github.mundanej.map.api.LabelTextStyle extends java.lang.Record {
  public io.github.mundanej.map.api.LabelTextStyle(io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.LabelWeight, double);
    descriptor: (Lio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/LabelWeight;D)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Rgba color();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.LabelWeight weight();
    descriptor: ()Lio/github/mundanej/map/api/LabelWeight;
  public double sizePixels();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.LabelWeight extends java.lang.Enum<io.github.mundanej.map.api.LabelWeight> {
  public static final io.github.mundanej.map.api.LabelWeight NORMAL;
    descriptor: Lio/github/mundanej/map/api/LabelWeight;
  public static final io.github.mundanej.map.api.LabelWeight BOLD;
    descriptor: Lio/github/mundanej/map/api/LabelWeight;
  public static io.github.mundanej.map.api.LabelWeight[] values();
    descriptor: ()[Lio/github/mundanej/map/api/LabelWeight;
  public static io.github.mundanej.map.api.LabelWeight valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/LabelWeight;
}
public interface io.github.mundanej.map.api.Layer {
  public abstract java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public abstract java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public abstract java.util.List<io.github.mundanej.map.api.Feature> features();
    descriptor: ()Ljava/util/List;
  public abstract java.util.Optional<io.github.mundanej.map.api.Envelope> envelope();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.LineStringGeometry extends java.lang.Record implements io.github.mundanej.map.api.Geometry {
  public io.github.mundanej.map.api.LineStringGeometry(io.github.mundanej.map.api.CoordinateSequence);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;)V
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.CoordinateSequence coordinates();
    descriptor: ()Lio/github/mundanej/map/api/CoordinateSequence;
}
public interface io.github.mundanej.map.api.LineSymbol extends io.github.mundanej.map.api.Symbol {
  public default io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
}
public final class io.github.mundanej.map.api.LiteralLabelText extends java.lang.Record implements io.github.mundanej.map.api.LabelTextSource {
  public io.github.mundanej.map.api.LiteralLabelText(java.lang.String);
    descriptor: (Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String text();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.MapCursorIntent extends java.lang.Enum<io.github.mundanej.map.api.MapCursorIntent> {
  public static final io.github.mundanej.map.api.MapCursorIntent DEFAULT;
    descriptor: Lio/github/mundanej/map/api/MapCursorIntent;
  public static final io.github.mundanej.map.api.MapCursorIntent CROSSHAIR;
    descriptor: Lio/github/mundanej/map/api/MapCursorIntent;
  public static final io.github.mundanej.map.api.MapCursorIntent HAND;
    descriptor: Lio/github/mundanej/map/api/MapCursorIntent;
  public static final io.github.mundanej.map.api.MapCursorIntent MOVE;
    descriptor: Lio/github/mundanej/map/api/MapCursorIntent;
  public static io.github.mundanej.map.api.MapCursorIntent[] values();
    descriptor: ()[Lio/github/mundanej/map/api/MapCursorIntent;
  public static io.github.mundanej.map.api.MapCursorIntent valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/MapCursorIntent;
}
public final class io.github.mundanej.map.api.MapHit extends java.lang.Record {
  public io.github.mundanej.map.api.MapHit(java.lang.String, java.lang.String);
    descriptor: (Ljava/lang/String;Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String layerId();
    descriptor: ()Ljava/lang/String;
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.MapHitResults implements java.lang.Iterable<io.github.mundanej.map.api.MapHit> {
  public static io.github.mundanej.map.api.MapHitResults of(java.util.List<io.github.mundanej.map.api.MapHit>);
    descriptor: (Ljava/util/List;)Lio/github/mundanej/map/api/MapHitResults;
  public int size();
    descriptor: ()I
  public java.util.List<io.github.mundanej.map.api.MapHit> hits();
    descriptor: ()Ljava/util/List;
  public java.util.Optional<io.github.mundanej.map.api.MapHit> topmost();
    descriptor: ()Ljava/util/Optional;
  public java.util.Iterator<io.github.mundanej.map.api.MapHit> iterator();
    descriptor: ()Ljava/util/Iterator;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.MapHoverEvent extends java.lang.Record {
  public io.github.mundanej.map.api.MapHoverEvent(java.util.Optional<io.github.mundanej.map.api.MapHit>, java.util.Optional<io.github.mundanej.map.api.MapHit>);
    descriptor: (Ljava/util/Optional;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<io.github.mundanej.map.api.MapHit> previous();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.MapHit> current();
    descriptor: ()Ljava/util/Optional;
}
public interface io.github.mundanej.map.api.MapHoverListener {
  public abstract void onMapHoverChanged(io.github.mundanej.map.api.MapHoverEvent);
    descriptor: (Lio/github/mundanej/map/api/MapHoverEvent;)V
}
public final class io.github.mundanej.map.api.MapInputModifier extends java.lang.Enum<io.github.mundanej.map.api.MapInputModifier> {
  public static final io.github.mundanej.map.api.MapInputModifier SHIFT;
    descriptor: Lio/github/mundanej/map/api/MapInputModifier;
  public static final io.github.mundanej.map.api.MapInputModifier CONTROL;
    descriptor: Lio/github/mundanej/map/api/MapInputModifier;
  public static final io.github.mundanej.map.api.MapInputModifier ALT;
    descriptor: Lio/github/mundanej/map/api/MapInputModifier;
  public static final io.github.mundanej.map.api.MapInputModifier META;
    descriptor: Lio/github/mundanej/map/api/MapInputModifier;
  public static final io.github.mundanej.map.api.MapInputModifier ALT_GRAPH;
    descriptor: Lio/github/mundanej/map/api/MapInputModifier;
  public static io.github.mundanej.map.api.MapInputModifier[] values();
    descriptor: ()[Lio/github/mundanej/map/api/MapInputModifier;
  public static io.github.mundanej.map.api.MapInputModifier valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/MapInputModifier;
}
public final class io.github.mundanej.map.api.MapPointerButton extends java.lang.Record {
  public static final io.github.mundanej.map.api.MapPointerButton NONE;
    descriptor: Lio/github/mundanej/map/api/MapPointerButton;
  public static final io.github.mundanej.map.api.MapPointerButton PRIMARY;
    descriptor: Lio/github/mundanej/map/api/MapPointerButton;
  public static final io.github.mundanej.map.api.MapPointerButton MIDDLE;
    descriptor: Lio/github/mundanej/map/api/MapPointerButton;
  public static final io.github.mundanej.map.api.MapPointerButton SECONDARY;
    descriptor: Lio/github/mundanej/map/api/MapPointerButton;
  public io.github.mundanej.map.api.MapPointerButton(int);
    descriptor: (I)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int number();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.MapPointerEvent extends java.lang.Record {
  public io.github.mundanej.map.api.MapPointerEvent(io.github.mundanej.map.api.MapPointerEvent$Type, double, double, java.util.Optional<io.github.mundanej.map.api.Coordinate>);
    descriptor: (Lio/github/mundanej/map/api/MapPointerEvent$Type;DDLjava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.MapPointerEvent$Type type();
    descriptor: ()Lio/github/mundanej/map/api/MapPointerEvent$Type;
  public double screenX();
    descriptor: ()D
  public double screenY();
    descriptor: ()D
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> mapCoordinate();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.MapPointerEvent$Type extends java.lang.Enum<io.github.mundanej.map.api.MapPointerEvent$Type> {
  public static final io.github.mundanej.map.api.MapPointerEvent$Type MOVED;
    descriptor: Lio/github/mundanej/map/api/MapPointerEvent$Type;
  public static final io.github.mundanej.map.api.MapPointerEvent$Type CLICKED;
    descriptor: Lio/github/mundanej/map/api/MapPointerEvent$Type;
  public static io.github.mundanej.map.api.MapPointerEvent$Type[] values();
    descriptor: ()[Lio/github/mundanej/map/api/MapPointerEvent$Type;
  public static io.github.mundanej.map.api.MapPointerEvent$Type valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/MapPointerEvent$Type;
}
public interface io.github.mundanej.map.api.MapPointerListener {
  public abstract void onMapPointerEvent(io.github.mundanej.map.api.MapPointerEvent);
    descriptor: (Lio/github/mundanej/map/api/MapPointerEvent;)V
}
public final class io.github.mundanej.map.api.MapSelectionEvent extends java.lang.Record {
  public io.github.mundanej.map.api.MapSelectionEvent(java.util.Optional<io.github.mundanej.map.api.FeatureSelection>, java.util.Optional<io.github.mundanej.map.api.FeatureSelection>);
    descriptor: (Ljava/util/Optional;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<io.github.mundanej.map.api.FeatureSelection> previous();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.FeatureSelection> current();
    descriptor: ()Ljava/util/Optional;
}
public interface io.github.mundanej.map.api.MapSelectionListener {
  public abstract void onMapSelectionChanged(io.github.mundanej.map.api.MapSelectionEvent);
    descriptor: (Lio/github/mundanej/map/api/MapSelectionEvent;)V
}
public final class io.github.mundanej.map.api.MapSourceReportEvent extends java.lang.Record {
  public io.github.mundanej.map.api.MapSourceReportEvent(java.lang.String, java.util.Optional<io.github.mundanej.map.api.DiagnosticReport>, java.util.Optional<io.github.mundanej.map.api.DiagnosticReport>);
    descriptor: (Ljava/lang/String;Ljava/util/Optional;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String layerId();
    descriptor: ()Ljava/lang/String;
  public java.util.Optional<io.github.mundanej.map.api.DiagnosticReport> previous();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.DiagnosticReport> current();
    descriptor: ()Ljava/util/Optional;
}
public interface io.github.mundanej.map.api.MapSourceReportListener {
  public abstract void onMapSourceReportChanged(io.github.mundanej.map.api.MapSourceReportEvent);
    descriptor: (Lio/github/mundanej/map/api/MapSourceReportEvent;)V
}
public interface io.github.mundanej.map.api.MapTool {
  public default void onActivate(io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolContext;)V
  public abstract io.github.mundanej.map.api.MapToolResult onMapToolEvent(io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/api/MapToolResult;
  public default io.github.mundanej.map.api.MapToolResult onMapToolCommand(io.github.mundanej.map.api.MapToolCommandEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolCommandEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/api/MapToolResult;
  public default void onDeactivate(io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolContext;)V
  public default io.github.mundanej.map.api.MapCursorIntent cursorIntent();
    descriptor: ()Lio/github/mundanej/map/api/MapCursorIntent;
}
public final class io.github.mundanej.map.api.MapToolCancelReason extends java.lang.Enum<io.github.mundanej.map.api.MapToolCancelReason> {
  public static final io.github.mundanej.map.api.MapToolCancelReason TOOL_REPLACED;
    descriptor: Lio/github/mundanej/map/api/MapToolCancelReason;
  public static final io.github.mundanej.map.api.MapToolCancelReason TOOL_CLEARED;
    descriptor: Lio/github/mundanej/map/api/MapToolCancelReason;
  public static final io.github.mundanej.map.api.MapToolCancelReason FOCUS_LOST;
    descriptor: Lio/github/mundanej/map/api/MapToolCancelReason;
  public static final io.github.mundanej.map.api.MapToolCancelReason VIEW_DISABLED;
    descriptor: Lio/github/mundanej/map/api/MapToolCancelReason;
  public static final io.github.mundanej.map.api.MapToolCancelReason VIEW_REMOVED;
    descriptor: Lio/github/mundanej/map/api/MapToolCancelReason;
  public static final io.github.mundanej.map.api.MapToolCancelReason POINTER_EXITED;
    descriptor: Lio/github/mundanej/map/api/MapToolCancelReason;
  public static final io.github.mundanej.map.api.MapToolCancelReason POINTER_STATE_LOST;
    descriptor: Lio/github/mundanej/map/api/MapToolCancelReason;
  public static final io.github.mundanej.map.api.MapToolCancelReason SOURCE_FAILURE;
    descriptor: Lio/github/mundanej/map/api/MapToolCancelReason;
  public static final io.github.mundanej.map.api.MapToolCancelReason USER_CANCEL;
    descriptor: Lio/github/mundanej/map/api/MapToolCancelReason;
  public static io.github.mundanej.map.api.MapToolCancelReason[] values();
    descriptor: ()[Lio/github/mundanej/map/api/MapToolCancelReason;
  public static io.github.mundanej.map.api.MapToolCancelReason valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/MapToolCancelReason;
}
public final class io.github.mundanej.map.api.MapToolCommand extends java.lang.Enum<io.github.mundanej.map.api.MapToolCommand> {
  public static final io.github.mundanej.map.api.MapToolCommand DELETE_BACKWARD;
    descriptor: Lio/github/mundanej/map/api/MapToolCommand;
  public static final io.github.mundanej.map.api.MapToolCommand UNDO;
    descriptor: Lio/github/mundanej/map/api/MapToolCommand;
  public static final io.github.mundanej.map.api.MapToolCommand REDO;
    descriptor: Lio/github/mundanej/map/api/MapToolCommand;
  public static io.github.mundanej.map.api.MapToolCommand[] values();
    descriptor: ()[Lio/github/mundanej/map/api/MapToolCommand;
  public static io.github.mundanej.map.api.MapToolCommand valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/MapToolCommand;
}
public final class io.github.mundanej.map.api.MapToolCommandEvent extends java.lang.Record {
  public io.github.mundanej.map.api.MapToolCommandEvent(long, io.github.mundanej.map.api.MapToolCommand);
    descriptor: (JLio/github/mundanej/map/api/MapToolCommand;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long sequence();
    descriptor: ()J
  public io.github.mundanej.map.api.MapToolCommand command();
    descriptor: ()Lio/github/mundanej/map/api/MapToolCommand;
}
public interface io.github.mundanej.map.api.MapToolContext {
  public abstract io.github.mundanej.map.api.CrsDefinition mapCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public abstract io.github.mundanej.map.api.CrsDefinition displayCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public abstract java.util.Optional<io.github.mundanej.map.api.Coordinate> mapToScreen(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Ljava/util/Optional;
  public abstract java.util.Optional<io.github.mundanej.map.api.Coordinate> screenToMap(double, double);
    descriptor: (DD)Ljava/util/Optional;
  public abstract void requestRepaint();
    descriptor: ()V
}
public final class io.github.mundanej.map.api.MapToolEvent extends java.lang.Record {
  public io.github.mundanej.map.api.MapToolEvent(long, io.github.mundanej.map.api.MapToolEvent$Type, double, double, java.util.Optional<io.github.mundanej.map.api.Coordinate>, io.github.mundanej.map.api.MapPointerButton, java.util.Set<io.github.mundanej.map.api.MapPointerButton>, java.util.Set<io.github.mundanej.map.api.MapInputModifier>, int, double, boolean, java.util.Optional<io.github.mundanej.map.api.MapToolCancelReason>);
    descriptor: (JLio/github/mundanej/map/api/MapToolEvent$Type;DDLjava/util/Optional;Lio/github/mundanej/map/api/MapPointerButton;Ljava/util/Set;Ljava/util/Set;IDZLjava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long sequence();
    descriptor: ()J
  public io.github.mundanej.map.api.MapToolEvent$Type type();
    descriptor: ()Lio/github/mundanej/map/api/MapToolEvent$Type;
  public double screenX();
    descriptor: ()D
  public double screenY();
    descriptor: ()D
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> mapCoordinate();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.MapPointerButton button();
    descriptor: ()Lio/github/mundanej/map/api/MapPointerButton;
  public java.util.Set<io.github.mundanej.map.api.MapPointerButton> buttonsDown();
    descriptor: ()Ljava/util/Set;
  public java.util.Set<io.github.mundanej.map.api.MapInputModifier> modifiers();
    descriptor: ()Ljava/util/Set;
  public int clickCount();
    descriptor: ()I
  public double wheelRotation();
    descriptor: ()D
  public boolean popupTrigger();
    descriptor: ()Z
  public java.util.Optional<io.github.mundanej.map.api.MapToolCancelReason> cancelReason();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.MapToolEvent$Type extends java.lang.Enum<io.github.mundanej.map.api.MapToolEvent$Type> {
  public static final io.github.mundanej.map.api.MapToolEvent$Type PRESS;
    descriptor: Lio/github/mundanej/map/api/MapToolEvent$Type;
  public static final io.github.mundanej.map.api.MapToolEvent$Type DRAG;
    descriptor: Lio/github/mundanej/map/api/MapToolEvent$Type;
  public static final io.github.mundanej.map.api.MapToolEvent$Type RELEASE;
    descriptor: Lio/github/mundanej/map/api/MapToolEvent$Type;
  public static final io.github.mundanej.map.api.MapToolEvent$Type MOVE;
    descriptor: Lio/github/mundanej/map/api/MapToolEvent$Type;
  public static final io.github.mundanej.map.api.MapToolEvent$Type CLICK;
    descriptor: Lio/github/mundanej/map/api/MapToolEvent$Type;
  public static final io.github.mundanej.map.api.MapToolEvent$Type WHEEL;
    descriptor: Lio/github/mundanej/map/api/MapToolEvent$Type;
  public static final io.github.mundanej.map.api.MapToolEvent$Type CANCEL;
    descriptor: Lio/github/mundanej/map/api/MapToolEvent$Type;
  public static io.github.mundanej.map.api.MapToolEvent$Type[] values();
    descriptor: ()[Lio/github/mundanej/map/api/MapToolEvent$Type;
  public static io.github.mundanej.map.api.MapToolEvent$Type valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/MapToolEvent$Type;
}
public final class io.github.mundanej.map.api.MapToolResult extends java.lang.Enum<io.github.mundanej.map.api.MapToolResult> {
  public static final io.github.mundanej.map.api.MapToolResult PASS;
    descriptor: Lio/github/mundanej/map/api/MapToolResult;
  public static final io.github.mundanej.map.api.MapToolResult CONSUME;
    descriptor: Lio/github/mundanej/map/api/MapToolResult;
  public static final io.github.mundanej.map.api.MapToolResult CAPTURE;
    descriptor: Lio/github/mundanej/map/api/MapToolResult;
  public static io.github.mundanej.map.api.MapToolResult[] values();
    descriptor: ()[Lio/github/mundanej/map/api/MapToolResult;
  public static io.github.mundanej.map.api.MapToolResult valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/MapToolResult;
}
public final class io.github.mundanej.map.api.MarkerPlacement extends java.lang.Record {
  public io.github.mundanej.map.api.MarkerPlacement(io.github.mundanej.map.api.SymbolSize, io.github.mundanej.map.api.SymbolAnchor, double, double, double, io.github.mundanej.map.api.SymbolRotationMode);
    descriptor: (Lio/github/mundanej/map/api/SymbolSize;Lio/github/mundanej/map/api/SymbolAnchor;DDDLio/github/mundanej/map/api/SymbolRotationMode;)V
  public static io.github.mundanej.map.api.MarkerPlacement centeredScreen(double);
    descriptor: (D)Lio/github/mundanej/map/api/MarkerPlacement;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.SymbolSize size();
    descriptor: ()Lio/github/mundanej/map/api/SymbolSize;
  public io.github.mundanej.map.api.SymbolAnchor anchor();
    descriptor: ()Lio/github/mundanej/map/api/SymbolAnchor;
  public double offsetX();
    descriptor: ()D
  public double offsetY();
    descriptor: ()D
  public double rotationDegrees();
    descriptor: ()D
  public io.github.mundanej.map.api.SymbolRotationMode rotationMode();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRotationMode;
}
public interface io.github.mundanej.map.api.MarkerSymbol extends io.github.mundanej.map.api.Symbol {
  public default io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
}
public final class io.github.mundanej.map.api.MeasurementPhase extends java.lang.Enum<io.github.mundanej.map.api.MeasurementPhase> {
  public static final io.github.mundanej.map.api.MeasurementPhase EMPTY;
    descriptor: Lio/github/mundanej/map/api/MeasurementPhase;
  public static final io.github.mundanej.map.api.MeasurementPhase MEASURING;
    descriptor: Lio/github/mundanej/map/api/MeasurementPhase;
  public static final io.github.mundanej.map.api.MeasurementPhase COMPLETE;
    descriptor: Lio/github/mundanej/map/api/MeasurementPhase;
  public static io.github.mundanej.map.api.MeasurementPhase[] values();
    descriptor: ()[Lio/github/mundanej/map/api/MeasurementPhase;
  public static io.github.mundanej.map.api.MeasurementPhase valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/MeasurementPhase;
}
public final class io.github.mundanej.map.api.MeasurementState {
  public io.github.mundanej.map.api.MeasurementState(io.github.mundanej.map.api.MeasurementPhase, double[], java.util.Optional<io.github.mundanej.map.api.Coordinate>, io.github.mundanej.map.api.DistanceResult, java.util.Optional<io.github.mundanej.map.api.DistanceResult>, java.util.Optional<io.github.mundanej.map.api.DistanceResult>);
    descriptor: (Lio/github/mundanej/map/api/MeasurementPhase;[DLjava/util/Optional;Lio/github/mundanej/map/api/DistanceResult;Ljava/util/Optional;Ljava/util/Optional;)V
  public static io.github.mundanej.map.api.MeasurementState empty();
    descriptor: ()Lio/github/mundanej/map/api/MeasurementState;
  public io.github.mundanej.map.api.MeasurementPhase phase();
    descriptor: ()Lio/github/mundanej/map/api/MeasurementPhase;
  public int vertexCount();
    descriptor: ()I
  public io.github.mundanej.map.api.Coordinate vertex(int);
    descriptor: (I)Lio/github/mundanej/map/api/Coordinate;
  public double[] packedVertices();
    descriptor: ()[D
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> preview();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.DistanceResult committedDistance();
    descriptor: ()Lio/github/mundanej/map/api/DistanceResult;
  public java.util.Optional<io.github.mundanej.map.api.DistanceResult> lastCommittedSegmentDistance();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.DistanceResult> previewSegmentDistance();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.DistanceResult displayedDistance();
    descriptor: ()Lio/github/mundanej/map/api/DistanceResult;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.MultiLineStringGeometry implements io.github.mundanej.map.api.Geometry {
  public static io.github.mundanej.map.api.MultiLineStringGeometry of(io.github.mundanej.map.api.CoordinateSequence, int[]);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;[I)Lio/github/mundanej/map/api/MultiLineStringGeometry;
  public static io.github.mundanej.map.api.MultiLineStringGeometry ofParts(java.util.List<io.github.mundanej.map.api.CoordinateSequence>);
    descriptor: (Ljava/util/List;)Lio/github/mundanej/map/api/MultiLineStringGeometry;
  public io.github.mundanej.map.api.CoordinateSequence coordinates();
    descriptor: ()Lio/github/mundanej/map/api/CoordinateSequence;
  public int partCount();
    descriptor: ()I
  public int partOffset(int);
    descriptor: (I)I
  public int[] partOffsets();
    descriptor: ()[I
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.MultiPointGeometry extends java.lang.Record implements io.github.mundanej.map.api.Geometry {
  public io.github.mundanej.map.api.MultiPointGeometry(io.github.mundanej.map.api.CoordinateSequence);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;)V
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.CoordinateSequence coordinates();
    descriptor: ()Lio/github/mundanej/map/api/CoordinateSequence;
}
public final class io.github.mundanej.map.api.MultiPolygonGeometry implements io.github.mundanej.map.api.Geometry {
  public static io.github.mundanej.map.api.MultiPolygonGeometry of(io.github.mundanej.map.api.CoordinateSequence, int[], int[]);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;[I[I)Lio/github/mundanej/map/api/MultiPolygonGeometry;
  public static io.github.mundanej.map.api.MultiPolygonGeometry ofPolygons(java.util.List<io.github.mundanej.map.api.PolygonGeometry>);
    descriptor: (Ljava/util/List;)Lio/github/mundanej/map/api/MultiPolygonGeometry;
  public io.github.mundanej.map.api.CoordinateSequence coordinates();
    descriptor: ()Lio/github/mundanej/map/api/CoordinateSequence;
  public int ringCount();
    descriptor: ()I
  public int polygonCount();
    descriptor: ()I
  public int ringOffset(int);
    descriptor: (I)I
  public int polygonRingOffset(int);
    descriptor: (I)I
  public int[] ringOffsets();
    descriptor: ()[I
  public int[] polygonRingOffsets();
    descriptor: ()[I
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.NamedSymbol extends java.lang.Record {
  public io.github.mundanej.map.api.NamedSymbol(java.lang.String, io.github.mundanej.map.api.Symbol);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/Symbol;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.Symbol symbol();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
}
public final class io.github.mundanej.map.api.NamedSymbolCatalog implements java.lang.Iterable<io.github.mundanej.map.api.NamedSymbol> {
  public static io.github.mundanej.map.api.NamedSymbolCatalog of(java.util.List<io.github.mundanej.map.api.NamedSymbol>);
    descriptor: (Ljava/util/List;)Lio/github/mundanej/map/api/NamedSymbolCatalog;
  public java.util.List<io.github.mundanej.map.api.NamedSymbol> entries();
    descriptor: ()Ljava/util/List;
  public int size();
    descriptor: ()I
  public java.util.Optional<io.github.mundanej.map.api.Symbol> find(java.lang.String);
    descriptor: (Ljava/lang/String;)Ljava/util/Optional;
  public io.github.mundanej.map.api.Symbol require(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/Symbol;
  public java.util.Iterator<io.github.mundanej.map.api.NamedSymbol> iterator();
    descriptor: ()Ljava/util/Iterator;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.OmittedSymbol implements io.github.mundanej.map.api.Symbol {
  public static io.github.mundanej.map.api.OmittedSymbol of(io.github.mundanej.map.api.SymbolRole);
    descriptor: (Lio/github/mundanej/map/api/SymbolRole;)Lio/github/mundanej/map/api/OmittedSymbol;
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public double opacity();
    descriptor: ()D
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.OrdinateLossPolicy extends java.lang.Enum<io.github.mundanej.map.api.OrdinateLossPolicy> {
  public static final io.github.mundanej.map.api.OrdinateLossPolicy REJECT;
    descriptor: Lio/github/mundanej/map/api/OrdinateLossPolicy;
  public static final io.github.mundanej.map.api.OrdinateLossPolicy DROP_TO_XY;
    descriptor: Lio/github/mundanej/map/api/OrdinateLossPolicy;
  public static io.github.mundanej.map.api.OrdinateLossPolicy[] values();
    descriptor: ()[Lio/github/mundanej/map/api/OrdinateLossPolicy;
  public static io.github.mundanej.map.api.OrdinateLossPolicy valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/OrdinateLossPolicy;
}
public final class io.github.mundanej.map.api.PlacedPointLabel extends java.lang.Record {
  public io.github.mundanej.map.api.PlacedPointLabel(java.lang.String, java.lang.String, java.lang.String, io.github.mundanej.map.api.LabelTextStyle, double, double, double, io.github.mundanej.map.api.ScreenBox, io.github.mundanej.map.api.ScreenBox, int);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/LabelTextStyle;DDDLio/github/mundanej/map/api/ScreenBox;Lio/github/mundanej/map/api/ScreenBox;I)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String layerId();
    descriptor: ()Ljava/lang/String;
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
  public java.lang.String text();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.LabelTextStyle style();
    descriptor: ()Lio/github/mundanej/map/api/LabelTextStyle;
  public double baselineX();
    descriptor: ()D
  public double baselineY();
    descriptor: ()D
  public double advance();
    descriptor: ()D
  public io.github.mundanej.map.api.ScreenBox visualBounds();
    descriptor: ()Lio/github/mundanej/map/api/ScreenBox;
  public io.github.mundanej.map.api.ScreenBox collisionBounds();
    descriptor: ()Lio/github/mundanej/map/api/ScreenBox;
  public int ordinaryPaintOrdinal();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.PointFeatureDraft extends java.lang.Record {
  public io.github.mundanej.map.api.PointFeatureDraft(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.Object>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public java.util.Map<java.lang.String, java.lang.Object> attributes();
    descriptor: ()Ljava/util/Map;
  public io.github.mundanej.map.api.FeatureRecord at(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/FeatureRecord;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.PointGeometry extends java.lang.Record implements io.github.mundanej.map.api.Geometry {
  public io.github.mundanej.map.api.PointGeometry(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)V
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Coordinate coordinate();
    descriptor: ()Lio/github/mundanej/map/api/Coordinate;
}
public final class io.github.mundanej.map.api.PointLabelAnchorBasis extends java.lang.Enum<io.github.mundanej.map.api.PointLabelAnchorBasis> {
  public static final io.github.mundanej.map.api.PointLabelAnchorBasis MARKER_BOUNDS;
    descriptor: Lio/github/mundanej/map/api/PointLabelAnchorBasis;
  public static final io.github.mundanej.map.api.PointLabelAnchorBasis FEATURE_POINT;
    descriptor: Lio/github/mundanej/map/api/PointLabelAnchorBasis;
  public static io.github.mundanej.map.api.PointLabelAnchorBasis[] values();
    descriptor: ()[Lio/github/mundanej/map/api/PointLabelAnchorBasis;
  public static io.github.mundanej.map.api.PointLabelAnchorBasis valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/PointLabelAnchorBasis;
}
public final class io.github.mundanej.map.api.PointLabelPosition extends java.lang.Enum<io.github.mundanej.map.api.PointLabelPosition> {
  public static final io.github.mundanej.map.api.PointLabelPosition CENTER;
    descriptor: Lio/github/mundanej/map/api/PointLabelPosition;
  public static final io.github.mundanej.map.api.PointLabelPosition N;
    descriptor: Lio/github/mundanej/map/api/PointLabelPosition;
  public static final io.github.mundanej.map.api.PointLabelPosition NE;
    descriptor: Lio/github/mundanej/map/api/PointLabelPosition;
  public static final io.github.mundanej.map.api.PointLabelPosition E;
    descriptor: Lio/github/mundanej/map/api/PointLabelPosition;
  public static final io.github.mundanej.map.api.PointLabelPosition SE;
    descriptor: Lio/github/mundanej/map/api/PointLabelPosition;
  public static final io.github.mundanej.map.api.PointLabelPosition S;
    descriptor: Lio/github/mundanej/map/api/PointLabelPosition;
  public static final io.github.mundanej.map.api.PointLabelPosition SW;
    descriptor: Lio/github/mundanej/map/api/PointLabelPosition;
  public static final io.github.mundanej.map.api.PointLabelPosition W;
    descriptor: Lio/github/mundanej/map/api/PointLabelPosition;
  public static final io.github.mundanej.map.api.PointLabelPosition NW;
    descriptor: Lio/github/mundanej/map/api/PointLabelPosition;
  public static io.github.mundanej.map.api.PointLabelPosition[] values();
    descriptor: ()[Lio/github/mundanej/map/api/PointLabelPosition;
  public static io.github.mundanej.map.api.PointLabelPosition valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/PointLabelPosition;
}
public final class io.github.mundanej.map.api.PointLabelProfile extends java.lang.Record {
  public io.github.mundanej.map.api.PointLabelProfile(io.github.mundanej.map.api.LabelTextSource, io.github.mundanej.map.api.LabelTextStyle, java.util.List<io.github.mundanej.map.api.PointLabelPosition>, double, double, double, double, int, io.github.mundanej.map.api.ResolutionRange, io.github.mundanej.map.api.PointLabelAnchorBasis);
    descriptor: (Lio/github/mundanej/map/api/LabelTextSource;Lio/github/mundanej/map/api/LabelTextStyle;Ljava/util/List;DDDDILio/github/mundanej/map/api/ResolutionRange;Lio/github/mundanej/map/api/PointLabelAnchorBasis;)V
  public io.github.mundanej.map.api.PointLabelProfile(io.github.mundanej.map.api.LabelTextSource, io.github.mundanej.map.api.LabelTextStyle, java.util.List<io.github.mundanej.map.api.PointLabelPosition>, double, double, double, double, int, io.github.mundanej.map.api.ResolutionRange);
    descriptor: (Lio/github/mundanej/map/api/LabelTextSource;Lio/github/mundanej/map/api/LabelTextStyle;Ljava/util/List;DDDDILio/github/mundanej/map/api/ResolutionRange;)V
  public static io.github.mundanej.map.api.PointLabelProfile compatibility();
    descriptor: ()Lio/github/mundanej/map/api/PointLabelProfile;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.LabelTextSource textSource();
    descriptor: ()Lio/github/mundanej/map/api/LabelTextSource;
  public io.github.mundanej.map.api.LabelTextStyle style();
    descriptor: ()Lio/github/mundanej/map/api/LabelTextStyle;
  public java.util.List<io.github.mundanej.map.api.PointLabelPosition> positions();
    descriptor: ()Ljava/util/List;
  public double gapPixels();
    descriptor: ()D
  public double offsetXPixels();
    descriptor: ()D
  public double offsetYPixels();
    descriptor: ()D
  public double collisionPaddingPixels();
    descriptor: ()D
  public int priority();
    descriptor: ()I
  public io.github.mundanej.map.api.ResolutionRange visibleResolution();
    descriptor: ()Lio/github/mundanej/map/api/ResolutionRange;
  public io.github.mundanej.map.api.PointLabelAnchorBasis anchorBasis();
    descriptor: ()Lio/github/mundanej/map/api/PointLabelAnchorBasis;
}
public final class io.github.mundanej.map.api.PointLabelTexts {
  public static final int MAXIMUM_CODE_POINTS = 256;
    descriptor: I
  public static int requireSupported(java.lang.String);
    descriptor: (Ljava/lang/String;)I
  public static boolean isLineSeparator(int);
    descriptor: (I)Z
}
public final class io.github.mundanej.map.api.PointLabelTexts$FailureReason extends java.lang.Enum<io.github.mundanej.map.api.PointLabelTexts$FailureReason> {
  public static final io.github.mundanej.map.api.PointLabelTexts$FailureReason BLANK;
    descriptor: Lio/github/mundanej/map/api/PointLabelTexts$FailureReason;
  public static final io.github.mundanej.map.api.PointLabelTexts$FailureReason TOO_LONG;
    descriptor: Lio/github/mundanej/map/api/PointLabelTexts$FailureReason;
  public static final io.github.mundanej.map.api.PointLabelTexts$FailureReason MULTILINE;
    descriptor: Lio/github/mundanej/map/api/PointLabelTexts$FailureReason;
  public static io.github.mundanej.map.api.PointLabelTexts$FailureReason[] values();
    descriptor: ()[Lio/github/mundanej/map/api/PointLabelTexts$FailureReason;
  public static io.github.mundanej.map.api.PointLabelTexts$FailureReason valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/PointLabelTexts$FailureReason;
}
public final class io.github.mundanej.map.api.PointLabelTexts$ValidationException extends java.lang.IllegalArgumentException {
  public io.github.mundanej.map.api.PointLabelTexts$FailureReason reason();
    descriptor: ()Lio/github/mundanej/map/api/PointLabelTexts$FailureReason;
  public int codePoint();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.PolygonGeometry extends java.lang.Record implements io.github.mundanej.map.api.Geometry {
  public io.github.mundanej.map.api.PolygonGeometry(io.github.mundanej.map.api.CoordinateSequence, java.util.List<io.github.mundanej.map.api.CoordinateSequence>);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;Ljava/util/List;)V
  public io.github.mundanej.map.api.PolygonGeometry(io.github.mundanej.map.api.CoordinateSequence);
    descriptor: (Lio/github/mundanej/map/api/CoordinateSequence;)V
  public io.github.mundanej.map.api.Envelope envelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.CoordinateSequence exterior();
    descriptor: ()Lio/github/mundanej/map/api/CoordinateSequence;
  public java.util.List<io.github.mundanej.map.api.CoordinateSequence> holes();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.api.PortrayalComparison extends java.lang.Enum<io.github.mundanej.map.api.PortrayalComparison> {
  public static final io.github.mundanej.map.api.PortrayalComparison EQUAL;
    descriptor: Lio/github/mundanej/map/api/PortrayalComparison;
  public static final io.github.mundanej.map.api.PortrayalComparison NOT_EQUAL;
    descriptor: Lio/github/mundanej/map/api/PortrayalComparison;
  public static final io.github.mundanej.map.api.PortrayalComparison LESS_THAN;
    descriptor: Lio/github/mundanej/map/api/PortrayalComparison;
  public static final io.github.mundanej.map.api.PortrayalComparison LESS_THAN_OR_EQUAL;
    descriptor: Lio/github/mundanej/map/api/PortrayalComparison;
  public static final io.github.mundanej.map.api.PortrayalComparison GREATER_THAN;
    descriptor: Lio/github/mundanej/map/api/PortrayalComparison;
  public static final io.github.mundanej.map.api.PortrayalComparison GREATER_THAN_OR_EQUAL;
    descriptor: Lio/github/mundanej/map/api/PortrayalComparison;
  public static io.github.mundanej.map.api.PortrayalComparison[] values();
    descriptor: ()[Lio/github/mundanej/map/api/PortrayalComparison;
  public static io.github.mundanej.map.api.PortrayalComparison valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/PortrayalComparison;
}
public final class io.github.mundanej.map.api.PortrayalEvaluationContext extends java.lang.Record {
  public static final io.github.mundanej.map.api.PortrayalEvaluationContext UNSCALED;
    descriptor: Lio/github/mundanej/map/api/PortrayalEvaluationContext;
  public io.github.mundanej.map.api.PortrayalEvaluationContext(java.util.OptionalDouble, java.util.OptionalDouble, java.util.Optional<io.github.mundanej.map.api.PortrayalGeometryType>);
    descriptor: (Ljava/util/OptionalDouble;Ljava/util/OptionalDouble;Ljava/util/Optional;)V
  public io.github.mundanej.map.api.PortrayalEvaluationContext(java.util.OptionalDouble);
    descriptor: (Ljava/util/OptionalDouble;)V
  public static io.github.mundanej.map.api.PortrayalEvaluationContext atScale(double);
    descriptor: (D)Lio/github/mundanej/map/api/PortrayalEvaluationContext;
  public static io.github.mundanej.map.api.PortrayalEvaluationContext atScaleAndZoom(double, double);
    descriptor: (DD)Lio/github/mundanej/map/api/PortrayalEvaluationContext;
  public io.github.mundanej.map.api.PortrayalEvaluationContext withGeometryType(io.github.mundanej.map.api.PortrayalGeometryType);
    descriptor: (Lio/github/mundanej/map/api/PortrayalGeometryType;)Lio/github/mundanej/map/api/PortrayalEvaluationContext;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.OptionalDouble scaleDenominator();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.OptionalDouble zoomLevel();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.Optional<io.github.mundanej.map.api.PortrayalGeometryType> geometryType();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.PortrayalEvaluationResult extends java.lang.Record {
  public io.github.mundanej.map.api.PortrayalEvaluationResult(java.util.Optional<java.lang.Object>, java.lang.String, java.lang.String);
    descriptor: (Ljava/util/Optional;Ljava/lang/String;Ljava/lang/String;)V
  public static io.github.mundanej.map.api.PortrayalEvaluationResult success(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Lio/github/mundanej/map/api/PortrayalEvaluationResult;
  public static io.github.mundanej.map.api.PortrayalEvaluationResult failure(java.lang.String, java.lang.String);
    descriptor: (Ljava/lang/String;Ljava/lang/String;)Lio/github/mundanej/map/api/PortrayalEvaluationResult;
  public boolean succeeded();
    descriptor: ()Z
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<java.lang.Object> value();
    descriptor: ()Ljava/util/Optional;
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.lang.String message();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.PortrayalExpression {
  public static io.github.mundanej.map.api.PortrayalExpression literal(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Lio/github/mundanej/map/api/PortrayalExpression;
  public static io.github.mundanej.map.api.PortrayalExpression attribute(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/PortrayalExpression;
  public static io.github.mundanej.map.api.PortrayalExpression input(io.github.mundanej.map.api.PortrayalExpression$Operator);
    descriptor: (Lio/github/mundanej/map/api/PortrayalExpression$Operator;)Lio/github/mundanej/map/api/PortrayalExpression;
  public static io.github.mundanej.map.api.PortrayalExpression call(io.github.mundanej.map.api.PortrayalExpression$Operator, java.util.List<io.github.mundanej.map.api.PortrayalExpression>);
    descriptor: (Lio/github/mundanej/map/api/PortrayalExpression$Operator;Ljava/util/List;)Lio/github/mundanej/map/api/PortrayalExpression;
  public static io.github.mundanej.map.api.PortrayalExpression call(io.github.mundanej.map.api.PortrayalExpression$Operator, java.util.List<io.github.mundanej.map.api.PortrayalExpression>, io.github.mundanej.map.api.PortrayalExpressionLimits);
    descriptor: (Lio/github/mundanej/map/api/PortrayalExpression$Operator;Ljava/util/List;Lio/github/mundanej/map/api/PortrayalExpressionLimits;)Lio/github/mundanej/map/api/PortrayalExpression;
  public io.github.mundanej.map.api.PortrayalExpression$Operator operator();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public java.lang.Object literal();
    descriptor: ()Ljava/lang/Object;
  public java.lang.String attributeName();
    descriptor: ()Ljava/lang/String;
  public java.util.List<io.github.mundanej.map.api.PortrayalExpression> arguments();
    descriptor: ()Ljava/util/List;
  public int nodeCount();
    descriptor: ()I
  public int depth();
    descriptor: ()I
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.PortrayalExpression$Operator extends java.lang.Enum<io.github.mundanej.map.api.PortrayalExpression$Operator> {
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator LITERAL;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator ATTRIBUTE;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator SCALE_DENOMINATOR;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator ZOOM_LEVEL;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator GEOMETRY_TYPE;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator ADD;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator MULTIPLY;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator CONCAT;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator EQUAL;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static final io.github.mundanej.map.api.PortrayalExpression$Operator COALESCE;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static io.github.mundanej.map.api.PortrayalExpression$Operator[] values();
    descriptor: ()[Lio/github/mundanej/map/api/PortrayalExpression$Operator;
  public static io.github.mundanej.map.api.PortrayalExpression$Operator valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/PortrayalExpression$Operator;
}
public final class io.github.mundanej.map.api.PortrayalExpressionLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.PortrayalExpressionLimits DEFAULT;
    descriptor: Lio/github/mundanej/map/api/PortrayalExpressionLimits;
  public io.github.mundanej.map.api.PortrayalExpressionLimits(int, int, int);
    descriptor: (III)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maxDepth();
    descriptor: ()I
  public int maxNodes();
    descriptor: ()I
  public int maxArguments();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.PortrayalGeometryType extends java.lang.Enum<io.github.mundanej.map.api.PortrayalGeometryType> {
  public static final io.github.mundanej.map.api.PortrayalGeometryType POINT;
    descriptor: Lio/github/mundanej/map/api/PortrayalGeometryType;
  public static final io.github.mundanej.map.api.PortrayalGeometryType LINE_STRING;
    descriptor: Lio/github/mundanej/map/api/PortrayalGeometryType;
  public static final io.github.mundanej.map.api.PortrayalGeometryType POLYGON;
    descriptor: Lio/github/mundanej/map/api/PortrayalGeometryType;
  public static io.github.mundanej.map.api.PortrayalGeometryType[] values();
    descriptor: ()[Lio/github/mundanej/map/api/PortrayalGeometryType;
  public static io.github.mundanej.map.api.PortrayalGeometryType valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/PortrayalGeometryType;
  public static io.github.mundanej.map.api.PortrayalGeometryType fromGeometry(io.github.mundanej.map.api.Geometry);
    descriptor: (Lio/github/mundanej/map/api/Geometry;)Lio/github/mundanej/map/api/PortrayalGeometryType;
}
public final class io.github.mundanej.map.api.PortrayalLogicalOperator extends java.lang.Enum<io.github.mundanej.map.api.PortrayalLogicalOperator> {
  public static final io.github.mundanej.map.api.PortrayalLogicalOperator AND;
    descriptor: Lio/github/mundanej/map/api/PortrayalLogicalOperator;
  public static final io.github.mundanej.map.api.PortrayalLogicalOperator OR;
    descriptor: Lio/github/mundanej/map/api/PortrayalLogicalOperator;
  public static final io.github.mundanej.map.api.PortrayalLogicalOperator NOT;
    descriptor: Lio/github/mundanej/map/api/PortrayalLogicalOperator;
  public static io.github.mundanej.map.api.PortrayalLogicalOperator[] values();
    descriptor: ()[Lio/github/mundanej/map/api/PortrayalLogicalOperator;
  public static io.github.mundanej.map.api.PortrayalLogicalOperator valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/PortrayalLogicalOperator;
}
public interface io.github.mundanej.map.api.PortrayalOperand {
}
public final class io.github.mundanej.map.api.PortrayalOperand$Literal extends java.lang.Record implements io.github.mundanej.map.api.PortrayalOperand {
  public io.github.mundanej.map.api.PortrayalOperand$Literal(java.lang.String);
    descriptor: (Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String text();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.PortrayalOperand$Property extends java.lang.Record implements io.github.mundanej.map.api.PortrayalOperand {
  public io.github.mundanej.map.api.PortrayalOperand$Property(java.lang.String);
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
public final class io.github.mundanej.map.api.PortrayalOperand$TypedLiteral extends java.lang.Record implements io.github.mundanej.map.api.PortrayalOperand {
  public io.github.mundanej.map.api.PortrayalOperand$TypedLiteral(io.github.mundanej.map.api.ThematicValue);
    descriptor: (Lio/github/mundanej/map/api/ThematicValue;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.ThematicValue value();
    descriptor: ()Lio/github/mundanej/map/api/ThematicValue;
}
public interface io.github.mundanej.map.api.PortrayalPredicate {
}
public final class io.github.mundanej.map.api.PortrayalPredicate$Between extends java.lang.Record implements io.github.mundanej.map.api.PortrayalPredicate {
  public io.github.mundanej.map.api.PortrayalPredicate$Between(io.github.mundanej.map.api.PortrayalOperand$Property, io.github.mundanej.map.api.PortrayalOperand, io.github.mundanej.map.api.PortrayalOperand);
    descriptor: (Lio/github/mundanej/map/api/PortrayalOperand$Property;Lio/github/mundanej/map/api/PortrayalOperand;Lio/github/mundanej/map/api/PortrayalOperand;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.PortrayalOperand$Property property();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalOperand$Property;
  public io.github.mundanej.map.api.PortrayalOperand lower();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalOperand;
  public io.github.mundanej.map.api.PortrayalOperand upper();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalOperand;
}
public final class io.github.mundanej.map.api.PortrayalPredicate$Comparison extends java.lang.Record implements io.github.mundanej.map.api.PortrayalPredicate {
  public io.github.mundanej.map.api.PortrayalPredicate$Comparison(io.github.mundanej.map.api.PortrayalComparison, io.github.mundanej.map.api.PortrayalOperand, io.github.mundanej.map.api.PortrayalOperand);
    descriptor: (Lio/github/mundanej/map/api/PortrayalComparison;Lio/github/mundanej/map/api/PortrayalOperand;Lio/github/mundanej/map/api/PortrayalOperand;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.PortrayalComparison operation();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalComparison;
  public io.github.mundanej.map.api.PortrayalOperand left();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalOperand;
  public io.github.mundanej.map.api.PortrayalOperand right();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalOperand;
}
public final class io.github.mundanej.map.api.PortrayalPredicate$Constant extends java.lang.Record implements io.github.mundanej.map.api.PortrayalPredicate {
  public io.github.mundanej.map.api.PortrayalPredicate$Constant(boolean);
    descriptor: (Z)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public boolean value();
    descriptor: ()Z
}
public final class io.github.mundanej.map.api.PortrayalPredicate$Exists extends java.lang.Record implements io.github.mundanej.map.api.PortrayalPredicate {
  public io.github.mundanej.map.api.PortrayalPredicate$Exists(io.github.mundanej.map.api.PortrayalOperand$Property);
    descriptor: (Lio/github/mundanej/map/api/PortrayalOperand$Property;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.PortrayalOperand$Property property();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalOperand$Property;
}
public final class io.github.mundanej.map.api.PortrayalPredicate$GeometryTypeIs extends java.lang.Record implements io.github.mundanej.map.api.PortrayalPredicate {
  public io.github.mundanej.map.api.PortrayalPredicate$GeometryTypeIs(java.util.Set<io.github.mundanej.map.api.PortrayalGeometryType>);
    descriptor: (Ljava/util/Set;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Set<io.github.mundanej.map.api.PortrayalGeometryType> types();
    descriptor: ()Ljava/util/Set;
}
public final class io.github.mundanej.map.api.PortrayalPredicate$IsNull extends java.lang.Record implements io.github.mundanej.map.api.PortrayalPredicate {
  public io.github.mundanej.map.api.PortrayalPredicate$IsNull(io.github.mundanej.map.api.PortrayalOperand$Property);
    descriptor: (Lio/github/mundanej/map/api/PortrayalOperand$Property;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.PortrayalOperand$Property property();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalOperand$Property;
}
public final class io.github.mundanej.map.api.PortrayalPredicate$Logical extends java.lang.Record implements io.github.mundanej.map.api.PortrayalPredicate {
  public io.github.mundanej.map.api.PortrayalPredicate$Logical(io.github.mundanej.map.api.PortrayalLogicalOperator, java.util.List<io.github.mundanej.map.api.PortrayalPredicate>);
    descriptor: (Lio/github/mundanej/map/api/PortrayalLogicalOperator;Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.PortrayalLogicalOperator operator();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalLogicalOperator;
  public java.util.List<io.github.mundanej.map.api.PortrayalPredicate> children();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.api.PortrayalRule extends java.lang.Record {
  public io.github.mundanej.map.api.PortrayalRule(java.util.Optional<java.lang.String>, io.github.mundanej.map.api.ScaleInterval, java.util.Optional<io.github.mundanej.map.api.PortrayalPredicate>, boolean, java.util.List<io.github.mundanej.map.api.Symbol>, java.util.List<io.github.mundanej.map.api.Symbol>, java.util.List<io.github.mundanej.map.api.Symbol>);
    descriptor: (Ljava/util/Optional;Lio/github/mundanej/map/api/ScaleInterval;Ljava/util/Optional;ZLjava/util/List;Ljava/util/List;Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.api.Symbol> markers();
    descriptor: ()Ljava/util/List;
  public java.util.List<io.github.mundanej.map.api.Symbol> lines();
    descriptor: ()Ljava/util/List;
  public java.util.List<io.github.mundanej.map.api.Symbol> fills();
    descriptor: ()Ljava/util/List;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<java.lang.String> name();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.ScaleInterval scale();
    descriptor: ()Lio/github/mundanej/map/api/ScaleInterval;
  public java.util.Optional<io.github.mundanej.map.api.PortrayalPredicate> predicate();
    descriptor: ()Ljava/util/Optional;
  public boolean elseRule();
    descriptor: ()Z
}
public interface io.github.mundanej.map.api.Projection {
  public abstract io.github.mundanej.map.api.CrsDefinition sourceCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public abstract io.github.mundanej.map.api.CrsDefinition targetCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public abstract io.github.mundanej.map.api.Envelope sourceDomain();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public abstract io.github.mundanej.map.api.Envelope targetDomain();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public abstract io.github.mundanej.map.api.Coordinate project(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public abstract io.github.mundanej.map.api.Coordinate unproject(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public abstract io.github.mundanej.map.api.Envelope projectEnvelope(io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/api/Envelope;
  public abstract io.github.mundanej.map.api.Envelope unprojectEnvelope(io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/api/Envelope;
}
public final class io.github.mundanej.map.api.RasterAffineTransform {
  public static io.github.mundanej.map.api.RasterAffineTransform of(double, double, double, double, double, double);
    descriptor: (DDDDDD)Lio/github/mundanej/map/api/RasterAffineTransform;
  public double a();
    descriptor: ()D
  public double d();
    descriptor: ()D
  public double b();
    descriptor: ()D
  public double e();
    descriptor: ()D
  public double c();
    descriptor: ()D
  public double f();
    descriptor: ()D
  public io.github.mundanej.map.api.Coordinate gridToMap(double, double);
    descriptor: (DD)Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.api.Coordinate mapToGrid(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.RasterGridPlacement {
  public static io.github.mundanej.map.api.RasterGridPlacement axisAligned(io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/api/RasterGridPlacement;
  public static io.github.mundanej.map.api.RasterGridPlacement affine(io.github.mundanej.map.api.RasterAffineTransform);
    descriptor: (Lio/github/mundanej/map/api/RasterAffineTransform;)Lio/github/mundanej/map/api/RasterGridPlacement;
  public io.github.mundanej.map.api.RasterGridPlacement$Kind kind();
    descriptor: ()Lio/github/mundanej/map/api/RasterGridPlacement$Kind;
  public java.util.Optional<io.github.mundanej.map.api.Envelope> axisAlignedBounds();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.RasterAffineTransform> affineTransform();
    descriptor: ()Ljava/util/Optional;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.RasterGridPlacement$Kind extends java.lang.Enum<io.github.mundanej.map.api.RasterGridPlacement$Kind> {
  public static final io.github.mundanej.map.api.RasterGridPlacement$Kind AXIS_ALIGNED;
    descriptor: Lio/github/mundanej/map/api/RasterGridPlacement$Kind;
  public static final io.github.mundanej.map.api.RasterGridPlacement$Kind AFFINE;
    descriptor: Lio/github/mundanej/map/api/RasterGridPlacement$Kind;
  public static io.github.mundanej.map.api.RasterGridPlacement$Kind[] values();
    descriptor: ()[Lio/github/mundanej/map/api/RasterGridPlacement$Kind;
  public static io.github.mundanej.map.api.RasterGridPlacement$Kind valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/RasterGridPlacement$Kind;
}
public final class io.github.mundanej.map.api.RasterIconSymbol implements io.github.mundanej.map.api.MarkerSymbol {
  public static final int MAX_DIMENSION = 4096;
    descriptor: I
  public static final int MAX_PIXELS = 4194304;
    descriptor: I
  public static final io.github.mundanej.map.api.SymbolRendererKey RENDERER_KEY;
    descriptor: Lio/github/mundanej/map/api/SymbolRendererKey;
  public static io.github.mundanej.map.api.RasterIconSymbol of(int, int, int[], io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.api.RasterInterpolation, double);
    descriptor: (II[ILio/github/mundanej/map/api/MarkerPlacement;Lio/github/mundanej/map/api/RasterInterpolation;D)Lio/github/mundanej/map/api/RasterIconSymbol;
  public static io.github.mundanej.map.api.RasterIconSymbol nativeScreenSize(int, int, int[], io.github.mundanej.map.api.RasterInterpolation, double);
    descriptor: (II[ILio/github/mundanej/map/api/RasterInterpolation;D)Lio/github/mundanej/map/api/RasterIconSymbol;
  public static io.github.mundanej.map.api.RasterIconSymbol screenWidth(int, int, int[], double, io.github.mundanej.map.api.RasterInterpolation, double);
    descriptor: (II[IDLio/github/mundanej/map/api/RasterInterpolation;D)Lio/github/mundanej/map/api/RasterIconSymbol;
  public int width();
    descriptor: ()I
  public int height();
    descriptor: ()I
  public int rgbaAt(int, int);
    descriptor: (II)I
  public int[] toRgbaArray();
    descriptor: ()[I
  public io.github.mundanej.map.api.MarkerPlacement placement();
    descriptor: ()Lio/github/mundanej/map/api/MarkerPlacement;
  public io.github.mundanej.map.api.RasterInterpolation interpolation();
    descriptor: ()Lio/github/mundanej/map/api/RasterInterpolation;
  public double opacity();
    descriptor: ()D
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.RasterInterpolation extends java.lang.Enum<io.github.mundanej.map.api.RasterInterpolation> {
  public static final io.github.mundanej.map.api.RasterInterpolation NEAREST;
    descriptor: Lio/github/mundanej/map/api/RasterInterpolation;
  public static final io.github.mundanej.map.api.RasterInterpolation BILINEAR;
    descriptor: Lio/github/mundanej/map/api/RasterInterpolation;
  public static io.github.mundanej.map.api.RasterInterpolation[] values();
    descriptor: ()[Lio/github/mundanej/map/api/RasterInterpolation;
  public static io.github.mundanej.map.api.RasterInterpolation valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/RasterInterpolation;
}
public final class io.github.mundanej.map.api.RasterPlacementException extends java.lang.IllegalArgumentException {
  public io.github.mundanej.map.api.RasterPlacementException(io.github.mundanej.map.api.RasterPlacementException$Reason);
    descriptor: (Lio/github/mundanej/map/api/RasterPlacementException$Reason;)V
  public io.github.mundanej.map.api.RasterPlacementException(io.github.mundanej.map.api.RasterPlacementException$Reason, java.lang.Throwable);
    descriptor: (Lio/github/mundanej/map/api/RasterPlacementException$Reason;Ljava/lang/Throwable;)V
  public io.github.mundanej.map.api.RasterPlacementException$Reason reason();
    descriptor: ()Lio/github/mundanej/map/api/RasterPlacementException$Reason;
}
public final class io.github.mundanej.map.api.RasterPlacementException$Reason extends java.lang.Enum<io.github.mundanej.map.api.RasterPlacementException$Reason> {
  public static final io.github.mundanej.map.api.RasterPlacementException$Reason SINGULAR;
    descriptor: Lio/github/mundanej/map/api/RasterPlacementException$Reason;
  public static final io.github.mundanej.map.api.RasterPlacementException$Reason INVERSE_NON_FINITE;
    descriptor: Lio/github/mundanej/map/api/RasterPlacementException$Reason;
  public static final io.github.mundanej.map.api.RasterPlacementException$Reason CORNER_NON_FINITE;
    descriptor: Lio/github/mundanej/map/api/RasterPlacementException$Reason;
  public static final io.github.mundanej.map.api.RasterPlacementException$Reason ENVELOPE_NON_FINITE;
    descriptor: Lio/github/mundanej/map/api/RasterPlacementException$Reason;
  public static final io.github.mundanej.map.api.RasterPlacementException$Reason ENVELOPE_NON_POSITIVE;
    descriptor: Lio/github/mundanej/map/api/RasterPlacementException$Reason;
  public static io.github.mundanej.map.api.RasterPlacementException$Reason[] values();
    descriptor: ()[Lio/github/mundanej/map/api/RasterPlacementException$Reason;
  public static io.github.mundanej.map.api.RasterPlacementException$Reason valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/RasterPlacementException$Reason;
}
public final class io.github.mundanej.map.api.RasterPortrayal extends java.lang.Record {
  public io.github.mundanej.map.api.RasterPortrayal(java.util.List<java.lang.Integer>, java.util.List<io.github.mundanej.map.api.RasterPortrayal$ColorStop>, io.github.mundanej.map.api.RasterPortrayal$ColorMapMode, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.RasterInterpolation, double);
    descriptor: (Ljava/util/List;Ljava/util/List;Lio/github/mundanej/map/api/RasterPortrayal$ColorMapMode;Lio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/RasterInterpolation;D)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.List<java.lang.Integer> bands();
    descriptor: ()Ljava/util/List;
  public java.util.List<io.github.mundanej.map.api.RasterPortrayal$ColorStop> colorMap();
    descriptor: ()Ljava/util/List;
  public io.github.mundanej.map.api.RasterPortrayal$ColorMapMode colorMapMode();
    descriptor: ()Lio/github/mundanej/map/api/RasterPortrayal$ColorMapMode;
  public io.github.mundanej.map.api.Rgba fallback();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.RasterInterpolation interpolation();
    descriptor: ()Lio/github/mundanej/map/api/RasterInterpolation;
  public double opacity();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.RasterPortrayal$ColorMapMode extends java.lang.Enum<io.github.mundanej.map.api.RasterPortrayal$ColorMapMode> {
  public static final io.github.mundanej.map.api.RasterPortrayal$ColorMapMode INTERVALS;
    descriptor: Lio/github/mundanej/map/api/RasterPortrayal$ColorMapMode;
  public static final io.github.mundanej.map.api.RasterPortrayal$ColorMapMode VALUES;
    descriptor: Lio/github/mundanej/map/api/RasterPortrayal$ColorMapMode;
  public static final io.github.mundanej.map.api.RasterPortrayal$ColorMapMode RAMP;
    descriptor: Lio/github/mundanej/map/api/RasterPortrayal$ColorMapMode;
  public static io.github.mundanej.map.api.RasterPortrayal$ColorMapMode[] values();
    descriptor: ()[Lio/github/mundanej/map/api/RasterPortrayal$ColorMapMode;
  public static io.github.mundanej.map.api.RasterPortrayal$ColorMapMode valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/RasterPortrayal$ColorMapMode;
}
public final class io.github.mundanej.map.api.RasterPortrayal$ColorStop extends java.lang.Record {
  public io.github.mundanej.map.api.RasterPortrayal$ColorStop(double, io.github.mundanej.map.api.Rgba, java.util.Optional<java.lang.String>);
    descriptor: (DLio/github/mundanej/map/api/Rgba;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double value();
    descriptor: ()D
  public io.github.mundanej.map.api.Rgba color();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public java.util.Optional<java.lang.String> label();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.RasterRead extends java.lang.Record {
  public io.github.mundanej.map.api.RasterRead(io.github.mundanej.map.api.RasterWindow, io.github.mundanej.map.api.RgbaPixelBuffer, io.github.mundanej.map.api.DiagnosticReport);
    descriptor: (Lio/github/mundanej/map/api/RasterWindow;Lio/github/mundanej/map/api/RgbaPixelBuffer;Lio/github/mundanej/map/api/DiagnosticReport;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.RasterWindow sourceWindow();
    descriptor: ()Lio/github/mundanej/map/api/RasterWindow;
  public io.github.mundanej.map.api.RgbaPixelBuffer pixels();
    descriptor: ()Lio/github/mundanej/map/api/RgbaPixelBuffer;
  public io.github.mundanej.map.api.DiagnosticReport diagnostics();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
}
public final class io.github.mundanej.map.api.RasterRequest extends java.lang.Record {
  public io.github.mundanej.map.api.RasterRequest(io.github.mundanej.map.api.RasterWindow, int, int, java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits>);
    descriptor: (Lio/github/mundanej/map/api/RasterWindow;IILjava/util/Optional;)V
  public io.github.mundanej.map.api.RasterRequest(io.github.mundanej.map.api.RasterWindow, int, int, io.github.mundanej.map.api.RasterInterpolation, java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits>);
    descriptor: (Lio/github/mundanej/map/api/RasterWindow;IILio/github/mundanej/map/api/RasterInterpolation;Ljava/util/Optional;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.RasterWindow sourceWindow();
    descriptor: ()Lio/github/mundanej/map/api/RasterWindow;
  public int outputWidth();
    descriptor: ()I
  public int outputHeight();
    descriptor: ()I
  public io.github.mundanej.map.api.RasterInterpolation interpolation();
    descriptor: ()Lio/github/mundanej/map/api/RasterInterpolation;
  public java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits> tighterLimits();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.RasterRequestLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.RasterRequestLimits LEVEL_1;
    descriptor: Lio/github/mundanej/map/api/RasterRequestLimits;
  public io.github.mundanej.map.api.RasterRequestLimits(long, int, long, long, long, int);
    descriptor: (JIJJJI)V
  public boolean tightens(io.github.mundanej.map.api.RasterRequestLimits);
    descriptor: (Lio/github/mundanej/map/api/RasterRequestLimits;)Z
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long sourceWindowPixels();
    descriptor: ()J
  public int outputDimension();
    descriptor: ()I
  public long outputPixels();
    descriptor: ()J
  public long decodedIntermediateBytes();
    descriptor: ()J
  public long ownedPayloadBytes();
    descriptor: ()J
  public int retainedWarnings();
    descriptor: ()I
}
public interface io.github.mundanej.map.api.RasterSource extends java.lang.AutoCloseable {
  public abstract io.github.mundanej.map.api.RasterSourceMetadata metadata();
    descriptor: ()Lio/github/mundanej/map/api/RasterSourceMetadata;
  public abstract io.github.mundanej.map.api.RasterSourceLimits limits();
    descriptor: ()Lio/github/mundanej/map/api/RasterSourceLimits;
  public abstract io.github.mundanej.map.api.DiagnosticReport openingDiagnostics();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
  public abstract io.github.mundanej.map.api.RasterRead read(io.github.mundanej.map.api.RasterRequest, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/RasterRequest;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterRead;
  public abstract boolean isClosed();
    descriptor: ()Z
  public abstract void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.api.RasterSourceLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.RasterSourceLimits LEVEL_1;
    descriptor: Lio/github/mundanej/map/api/RasterSourceLimits;
  public io.github.mundanej.map.api.RasterSourceLimits(io.github.mundanej.map.api.RasterRequestLimits);
    descriptor: (Lio/github/mundanej/map/api/RasterRequestLimits;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.RasterRequestLimits requestLimits();
    descriptor: ()Lio/github/mundanej/map/api/RasterRequestLimits;
}
public final class io.github.mundanej.map.api.RasterSourceMetadata {
  public io.github.mundanej.map.api.RasterSourceMetadata(io.github.mundanej.map.api.SourceIdentity, int, int, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;IILjava/util/Optional;Ljava/util/Optional;)V
  public static io.github.mundanej.map.api.RasterSourceMetadata withPlacement(io.github.mundanej.map.api.SourceIdentity, int, int, io.github.mundanej.map.api.RasterGridPlacement, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;IILio/github/mundanej/map/api/RasterGridPlacement;Ljava/util/Optional;)Lio/github/mundanej/map/api/RasterSourceMetadata;
  public io.github.mundanej.map.api.SourceIdentity identity();
    descriptor: ()Lio/github/mundanej/map/api/SourceIdentity;
  public int width();
    descriptor: ()I
  public int height();
    descriptor: ()I
  public java.util.Optional<io.github.mundanej.map.api.Envelope> mapBounds();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.RasterGridPlacement> gridPlacement();
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
public final class io.github.mundanej.map.api.RasterWindow extends java.lang.Record {
  public io.github.mundanej.map.api.RasterWindow(int, int, int, int);
    descriptor: (IIII)V
  public long endColumn();
    descriptor: ()J
  public long endRow();
    descriptor: ()J
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int column();
    descriptor: ()I
  public int row();
    descriptor: ()I
  public int width();
    descriptor: ()I
  public int height();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.RendererCapability extends java.lang.Record {
  public io.github.mundanej.map.api.RendererCapability(io.github.mundanej.map.api.RendererCapability$Support, java.util.Optional<java.lang.String>, java.lang.String);
    descriptor: (Lio/github/mundanej/map/api/RendererCapability$Support;Ljava/util/Optional;Ljava/lang/String;)V
  public static io.github.mundanej.map.api.RendererCapability accept();
    descriptor: ()Lio/github/mundanej/map/api/RendererCapability;
  public static io.github.mundanej.map.api.RendererCapability approximate(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/RendererCapability;
  public static io.github.mundanej.map.api.RendererCapability reject(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/RendererCapability;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.RendererCapability$Support support();
    descriptor: ()Lio/github/mundanej/map/api/RendererCapability$Support;
  public java.util.Optional<java.lang.String> approximationPolicy();
    descriptor: ()Ljava/util/Optional;
  public java.lang.String diagnosticCode();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.RendererCapability$Support extends java.lang.Enum<io.github.mundanej.map.api.RendererCapability$Support> {
  public static final io.github.mundanej.map.api.RendererCapability$Support ACCEPT;
    descriptor: Lio/github/mundanej/map/api/RendererCapability$Support;
  public static final io.github.mundanej.map.api.RendererCapability$Support APPROXIMATE;
    descriptor: Lio/github/mundanej/map/api/RendererCapability$Support;
  public static final io.github.mundanej.map.api.RendererCapability$Support REJECT;
    descriptor: Lio/github/mundanej/map/api/RendererCapability$Support;
  public static io.github.mundanej.map.api.RendererCapability$Support[] values();
    descriptor: ()[Lio/github/mundanej/map/api/RendererCapability$Support;
  public static io.github.mundanej.map.api.RendererCapability$Support valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/RendererCapability$Support;
}
public final class io.github.mundanej.map.api.ReplaceFeature extends java.lang.Record implements io.github.mundanej.map.api.FeatureEditCommand {
  public io.github.mundanej.map.api.ReplaceFeature(java.lang.String, io.github.mundanej.map.api.FeatureRecord);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/FeatureRecord;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.FeatureRecord replacement();
    descriptor: ()Lio/github/mundanej/map/api/FeatureRecord;
}
public final class io.github.mundanej.map.api.ResolutionRange extends java.lang.Record {
  public static final io.github.mundanej.map.api.ResolutionRange ALL;
    descriptor: Lio/github/mundanej/map/api/ResolutionRange;
  public io.github.mundanej.map.api.ResolutionRange(double, double);
    descriptor: (DD)V
  public boolean includes(double);
    descriptor: (D)Z
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double minUnitsPerPixelInclusive();
    descriptor: ()D
  public double maxUnitsPerPixelInclusive();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.ResolvedFeaturePortrayal extends java.lang.Record {
  public static final io.github.mundanej.map.api.ResolvedFeaturePortrayal EMPTY;
    descriptor: Lio/github/mundanej/map/api/ResolvedFeaturePortrayal;
  public io.github.mundanej.map.api.ResolvedFeaturePortrayal(java.util.Optional<io.github.mundanej.map.api.Symbol>, java.util.Optional<io.github.mundanej.map.api.Symbol>, java.util.Optional<io.github.mundanej.map.api.Symbol>);
    descriptor: (Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;)V
  public java.util.Optional<io.github.mundanej.map.api.Symbol> forRole(io.github.mundanej.map.api.SymbolRole);
    descriptor: (Lio/github/mundanej/map/api/SymbolRole;)Ljava/util/Optional;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.Optional<io.github.mundanej.map.api.Symbol> marker();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> line();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> fill();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.Rgba extends java.lang.Record {
  public static final io.github.mundanej.map.api.Rgba TRANSPARENT;
    descriptor: Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.Rgba(int, int, int, int);
    descriptor: (IIII)V
  public static io.github.mundanej.map.api.Rgba rgb(int, int, int);
    descriptor: (III)Lio/github/mundanej/map/api/Rgba;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int red();
    descriptor: ()I
  public int green();
    descriptor: ()I
  public int blue();
    descriptor: ()I
  public int alpha();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.RgbaPixelBuffer {
  public static io.github.mundanej.map.api.RgbaPixelBuffer copyOf(int, int, int[]);
    descriptor: (II[I)Lio/github/mundanej/map/api/RgbaPixelBuffer;
  public static io.github.mundanej.map.api.RgbaPixelBuffer$Builder builder(int, int);
    descriptor: (II)Lio/github/mundanej/map/api/RgbaPixelBuffer$Builder;
  public int width();
    descriptor: ()I
  public int height();
    descriptor: ()I
  public int rgbaAt(int, int);
    descriptor: (II)I
  public int[] rgba();
    descriptor: ()[I
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.RgbaPixelBuffer$Builder {
  public io.github.mundanej.map.api.RgbaPixelBuffer$Builder setRgba(int, int, int);
    descriptor: (III)Lio/github/mundanej/map/api/RgbaPixelBuffer$Builder;
  public io.github.mundanej.map.api.RgbaPixelBuffer build();
    descriptor: ()Lio/github/mundanej/map/api/RgbaPixelBuffer;
}
public final class io.github.mundanej.map.api.RulePortrayalPlan {
  public io.github.mundanej.map.api.RulePortrayalPlan(java.util.List<io.github.mundanej.map.api.PortrayalRule>);
    descriptor: (Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.api.PortrayalRule> rules();
    descriptor: ()Ljava/util/List;
  public boolean requiresScaleContext();
    descriptor: ()Z
  public io.github.mundanej.map.api.FeaturePortrayal portrayal();
    descriptor: ()Lio/github/mundanej/map/api/FeaturePortrayal;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.RuleSymbolSelector extends java.lang.Record implements io.github.mundanej.map.api.SymbolSelector {
  public io.github.mundanej.map.api.RuleSymbolSelector(io.github.mundanej.map.api.RulePortrayalPlan, io.github.mundanej.map.api.SymbolRole);
    descriptor: (Lio/github/mundanej/map/api/RulePortrayalPlan;Lio/github/mundanej/map/api/SymbolRole;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.RulePortrayalPlan plan();
    descriptor: ()Lio/github/mundanej/map/api/RulePortrayalPlan;
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
}
public final class io.github.mundanej.map.api.ScaleInterval extends java.lang.Record {
  public static final io.github.mundanej.map.api.ScaleInterval ALL;
    descriptor: Lio/github/mundanej/map/api/ScaleInterval;
  public io.github.mundanej.map.api.ScaleInterval(java.util.OptionalDouble, java.util.OptionalDouble);
    descriptor: (Ljava/util/OptionalDouble;Ljava/util/OptionalDouble;)V
  public boolean includes(double);
    descriptor: (D)Z
  public boolean constrained();
    descriptor: ()Z
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.OptionalDouble minimumInclusive();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.OptionalDouble maximumExclusive();
    descriptor: ()Ljava/util/OptionalDouble;
}
public final class io.github.mundanej.map.api.ScreenBox extends java.lang.Record {
  public io.github.mundanej.map.api.ScreenBox(double, double, double, double);
    descriptor: (DDDD)V
  public io.github.mundanej.map.api.ScreenBox translated(double, double);
    descriptor: (DD)Lio/github/mundanej/map/api/ScreenBox;
  public io.github.mundanej.map.api.ScreenBox expanded(double);
    descriptor: (D)Lio/github/mundanej/map/api/ScreenBox;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double minX();
    descriptor: ()D
  public double minY();
    descriptor: ()D
  public double maxX();
    descriptor: ()D
  public double maxY();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.SnapFeature extends java.lang.Record {
  public io.github.mundanej.map.api.SnapFeature(java.lang.String, io.github.mundanej.map.api.Geometry);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/Geometry;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.Geometry geometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
}
public final class io.github.mundanej.map.api.SnapLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.SnapLimits DEFAULT;
    descriptor: Lio/github/mundanej/map/api/SnapLimits;
  public io.github.mundanej.map.api.SnapLimits(int, int, long, long);
    descriptor: (IIJJ)V
  public io.github.mundanej.map.api.SnapLimits withMaximumLayers(int);
    descriptor: (I)Lio/github/mundanej/map/api/SnapLimits;
  public io.github.mundanej.map.api.SnapLimits withMaximumFeatures(int);
    descriptor: (I)Lio/github/mundanej/map/api/SnapLimits;
  public io.github.mundanej.map.api.SnapLimits withMaximumCoordinates(long);
    descriptor: (J)Lio/github/mundanej/map/api/SnapLimits;
  public io.github.mundanej.map.api.SnapLimits withMaximumSegments(long);
    descriptor: (J)Lio/github/mundanej/map/api/SnapLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumLayers();
    descriptor: ()I
  public int maximumFeatures();
    descriptor: ()I
  public long maximumCoordinates();
    descriptor: ()J
  public long maximumSegments();
    descriptor: ()J
}
public final class io.github.mundanej.map.api.SnapQueryResult extends java.lang.Record {
  public io.github.mundanej.map.api.SnapQueryResult(io.github.mundanej.map.api.SnapQueryStatus, java.util.Optional<io.github.mundanej.map.api.SnapResult>, java.util.Optional<io.github.mundanej.map.api.FeatureEditProblem>);
    descriptor: (Lio/github/mundanej/map/api/SnapQueryStatus;Ljava/util/Optional;Ljava/util/Optional;)V
  public static io.github.mundanej.map.api.SnapQueryResult snapped(io.github.mundanej.map.api.SnapResult);
    descriptor: (Lio/github/mundanej/map/api/SnapResult;)Lio/github/mundanej/map/api/SnapQueryResult;
  public static io.github.mundanej.map.api.SnapQueryResult unsnapped();
    descriptor: ()Lio/github/mundanej/map/api/SnapQueryResult;
  public static io.github.mundanej.map.api.SnapQueryResult rejected(io.github.mundanej.map.api.FeatureEditProblem);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditProblem;)Lio/github/mundanej/map/api/SnapQueryResult;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.SnapQueryStatus status();
    descriptor: ()Lio/github/mundanej/map/api/SnapQueryStatus;
  public java.util.Optional<io.github.mundanej.map.api.SnapResult> result();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.FeatureEditProblem> problem();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.api.SnapQueryStatus extends java.lang.Enum<io.github.mundanej.map.api.SnapQueryStatus> {
  public static final io.github.mundanej.map.api.SnapQueryStatus SNAPPED;
    descriptor: Lio/github/mundanej/map/api/SnapQueryStatus;
  public static final io.github.mundanej.map.api.SnapQueryStatus UNSNAPPED;
    descriptor: Lio/github/mundanej/map/api/SnapQueryStatus;
  public static final io.github.mundanej.map.api.SnapQueryStatus REJECTED;
    descriptor: Lio/github/mundanej/map/api/SnapQueryStatus;
  public static io.github.mundanej.map.api.SnapQueryStatus[] values();
    descriptor: ()[Lio/github/mundanej/map/api/SnapQueryStatus;
  public static io.github.mundanej.map.api.SnapQueryStatus valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/SnapQueryStatus;
}
public final class io.github.mundanej.map.api.SnapReferenceLayer extends java.lang.Record {
  public io.github.mundanej.map.api.SnapReferenceLayer(java.lang.String, java.util.List<io.github.mundanej.map.api.SnapFeature>);
    descriptor: (Ljava/lang/String;Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String layerId();
    descriptor: ()Ljava/lang/String;
  public java.util.List<io.github.mundanej.map.api.SnapFeature> features();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.api.SnapReferenceSet extends java.lang.Record {
  public io.github.mundanej.map.api.SnapReferenceSet(io.github.mundanej.map.api.CrsDefinition, java.util.List<io.github.mundanej.map.api.SnapReferenceLayer>);
    descriptor: (Lio/github/mundanej/map/api/CrsDefinition;Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.CrsDefinition crs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public java.util.List<io.github.mundanej.map.api.SnapReferenceLayer> layers();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.api.SnapResult extends java.lang.Record {
  public io.github.mundanej.map.api.SnapResult(io.github.mundanej.map.api.Coordinate, double, io.github.mundanej.map.api.SnapTargetType, java.lang.String, java.lang.String, int, int, int);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;DLio/github/mundanej/map/api/SnapTargetType;Ljava/lang/String;Ljava/lang/String;III)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Coordinate coordinate();
    descriptor: ()Lio/github/mundanej/map/api/Coordinate;
  public double distancePixels();
    descriptor: ()D
  public io.github.mundanej.map.api.SnapTargetType targetType();
    descriptor: ()Lio/github/mundanej/map/api/SnapTargetType;
  public java.lang.String layerId();
    descriptor: ()Ljava/lang/String;
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
  public int componentIndex();
    descriptor: ()I
  public int partIndex();
    descriptor: ()I
  public int elementIndex();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.SnapTargetType extends java.lang.Enum<io.github.mundanej.map.api.SnapTargetType> {
  public static final io.github.mundanej.map.api.SnapTargetType VERTEX;
    descriptor: Lio/github/mundanej/map/api/SnapTargetType;
  public static final io.github.mundanej.map.api.SnapTargetType SEGMENT;
    descriptor: Lio/github/mundanej/map/api/SnapTargetType;
  public static io.github.mundanej.map.api.SnapTargetType[] values();
    descriptor: ()[Lio/github/mundanej/map/api/SnapTargetType;
  public static io.github.mundanej.map.api.SnapTargetType valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/SnapTargetType;
}
public final class io.github.mundanej.map.api.SolidFillSymbol implements io.github.mundanej.map.api.FillSymbol {
  public static final io.github.mundanej.map.api.SymbolRendererKey RENDERER_KEY;
    descriptor: Lio/github/mundanej/map/api/SymbolRendererKey;
  public static io.github.mundanej.map.api.SolidFillSymbol of(io.github.mundanej.map.api.Rgba, java.util.Optional<io.github.mundanej.map.api.Symbol>, double);
    descriptor: (Lio/github/mundanej/map/api/Rgba;Ljava/util/Optional;D)Lio/github/mundanej/map/api/SolidFillSymbol;
  public static io.github.mundanej.map.api.SolidFillSymbol of(io.github.mundanej.map.api.Rgba, double);
    descriptor: (Lio/github/mundanej/map/api/Rgba;D)Lio/github/mundanej/map/api/SolidFillSymbol;
  public io.github.mundanej.map.api.Rgba fill();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> outline();
    descriptor: ()Ljava/util/Optional;
  public double opacity();
    descriptor: ()D
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.SolidLineSymbol implements io.github.mundanej.map.api.LineSymbol {
  public static final io.github.mundanej.map.api.SymbolRendererKey RENDERER_KEY;
    descriptor: Lio/github/mundanej/map/api/SymbolRendererKey;
  public static io.github.mundanej.map.api.SolidLineSymbol of(io.github.mundanej.map.api.SymbolStroke, java.util.Optional<io.github.mundanej.map.api.Symbol>, java.util.Optional<io.github.mundanej.map.api.Symbol>, double);
    descriptor: (Lio/github/mundanej/map/api/SymbolStroke;Ljava/util/Optional;Ljava/util/Optional;D)Lio/github/mundanej/map/api/SolidLineSymbol;
  public static io.github.mundanej.map.api.SolidLineSymbol of(io.github.mundanej.map.api.SymbolStroke, double);
    descriptor: (Lio/github/mundanej/map/api/SymbolStroke;D)Lio/github/mundanej/map/api/SolidLineSymbol;
  public io.github.mundanej.map.api.SymbolStroke stroke();
    descriptor: ()Lio/github/mundanej/map/api/SymbolStroke;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> startMarker();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.Symbol> endMarker();
    descriptor: ()Ljava/util/Optional;
  public double opacity();
    descriptor: ()D
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.SourceDiagnostic extends java.lang.Record {
  public io.github.mundanej.map.api.SourceDiagnostic(java.lang.String, io.github.mundanej.map.api.DiagnosticSeverity, java.lang.String, java.util.Optional<io.github.mundanej.map.api.DiagnosticLocation>, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/DiagnosticSeverity;Ljava/lang/String;Ljava/util/Optional;Ljava/lang/String;Ljava/util/Map;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.DiagnosticSeverity severity();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticSeverity;
  public java.lang.String sourceId();
    descriptor: ()Ljava/lang/String;
  public java.util.Optional<io.github.mundanej.map.api.DiagnosticLocation> location();
    descriptor: ()Ljava/util/Optional;
  public java.lang.String message();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.api.SourceException extends java.lang.RuntimeException {
  public io.github.mundanej.map.api.SourceException(io.github.mundanej.map.api.DiagnosticReport, io.github.mundanej.map.api.SourceDiagnostic);
    descriptor: (Lio/github/mundanej/map/api/DiagnosticReport;Lio/github/mundanej/map/api/SourceDiagnostic;)V
  public io.github.mundanej.map.api.SourceException(io.github.mundanej.map.api.DiagnosticReport, io.github.mundanej.map.api.SourceDiagnostic, java.lang.Throwable);
    descriptor: (Lio/github/mundanej/map/api/DiagnosticReport;Lio/github/mundanej/map/api/SourceDiagnostic;Ljava/lang/Throwable;)V
  public io.github.mundanej.map.api.DiagnosticReport report();
    descriptor: ()Lio/github/mundanej/map/api/DiagnosticReport;
  public io.github.mundanej.map.api.SourceDiagnostic terminal();
    descriptor: ()Lio/github/mundanej/map/api/SourceDiagnostic;
}
public final class io.github.mundanej.map.api.SourceIdentity extends java.lang.Record {
  public io.github.mundanej.map.api.SourceIdentity(java.lang.String, java.lang.String);
    descriptor: (Ljava/lang/String;Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String displayName();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.StringifiedTextAttribute extends java.lang.Record implements io.github.mundanej.map.api.LabelTextSource {
  public io.github.mundanej.map.api.StringifiedTextAttribute(java.lang.String);
    descriptor: (Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String attribute();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.StructuredAttributeLimits extends java.lang.Record {
  public static final io.github.mundanej.map.api.StructuredAttributeLimits DEFAULT;
    descriptor: Lio/github/mundanej/map/api/StructuredAttributeLimits;
  public io.github.mundanej.map.api.StructuredAttributeLimits(int, int, int, int);
    descriptor: (IIII)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maxDepth();
    descriptor: ()I
  public int maxValues();
    descriptor: ()I
  public int maxObjectMembers();
    descriptor: ()I
  public int maxArrayElements();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.StructuredAttributeValue {
  public static final java.lang.String LIMIT_EXCEEDED = "ATTRIBUTE_STRUCTURE_LIMIT_EXCEEDED";
    descriptor: Ljava/lang/String;
  public static io.github.mundanej.map.api.StructuredAttributeValue of(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Lio/github/mundanej/map/api/StructuredAttributeValue;
  public static io.github.mundanej.map.api.StructuredAttributeValue of(java.lang.Object, io.github.mundanej.map.api.StructuredAttributeLimits);
    descriptor: (Ljava/lang/Object;Lio/github/mundanej/map/api/StructuredAttributeLimits;)Lio/github/mundanej/map/api/StructuredAttributeValue;
  public java.lang.Object value();
    descriptor: ()Ljava/lang/Object;
  public int valueCount();
    descriptor: ()I
  public int depth();
    descriptor: ()I
  public long logicalSizeBytes();
    descriptor: ()J
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public interface io.github.mundanej.map.api.Symbol {
  public abstract io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public abstract io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public abstract double opacity();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.SymbolAnchor extends java.lang.Enum<io.github.mundanej.map.api.SymbolAnchor> {
  public static final io.github.mundanej.map.api.SymbolAnchor CENTER;
    descriptor: Lio/github/mundanej/map/api/SymbolAnchor;
  public static final io.github.mundanej.map.api.SymbolAnchor NORTH;
    descriptor: Lio/github/mundanej/map/api/SymbolAnchor;
  public static final io.github.mundanej.map.api.SymbolAnchor NORTH_EAST;
    descriptor: Lio/github/mundanej/map/api/SymbolAnchor;
  public static final io.github.mundanej.map.api.SymbolAnchor EAST;
    descriptor: Lio/github/mundanej/map/api/SymbolAnchor;
  public static final io.github.mundanej.map.api.SymbolAnchor SOUTH_EAST;
    descriptor: Lio/github/mundanej/map/api/SymbolAnchor;
  public static final io.github.mundanej.map.api.SymbolAnchor SOUTH;
    descriptor: Lio/github/mundanej/map/api/SymbolAnchor;
  public static final io.github.mundanej.map.api.SymbolAnchor SOUTH_WEST;
    descriptor: Lio/github/mundanej/map/api/SymbolAnchor;
  public static final io.github.mundanej.map.api.SymbolAnchor WEST;
    descriptor: Lio/github/mundanej/map/api/SymbolAnchor;
  public static final io.github.mundanej.map.api.SymbolAnchor NORTH_WEST;
    descriptor: Lio/github/mundanej/map/api/SymbolAnchor;
  public static io.github.mundanej.map.api.SymbolAnchor[] values();
    descriptor: ()[Lio/github/mundanej/map/api/SymbolAnchor;
  public static io.github.mundanej.map.api.SymbolAnchor valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/SymbolAnchor;
}
public final class io.github.mundanej.map.api.SymbolException extends java.lang.RuntimeException {
  public static final java.lang.String ROLE_MISMATCH = "SYMBOL_ROLE_MISMATCH";
    descriptor: Ljava/lang/String;
  public static final java.lang.String RENDERER_NOT_REGISTERED = "SYMBOL_RENDERER_NOT_REGISTERED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String RENDERER_VALUE_MISMATCH = "SYMBOL_RENDERER_VALUE_MISMATCH";
    descriptor: Ljava/lang/String;
  public static final java.lang.String TRANSFORM_NON_FINITE = "SYMBOL_TRANSFORM_NON_FINITE";
    descriptor: Ljava/lang/String;
  public static final java.lang.String HATCH_SEGMENT_LIMIT_EXCEEDED = "SYMBOL_HATCH_SEGMENT_LIMIT_EXCEEDED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String CATALOG_DUPLICATE = "SYMBOL_CATALOG_DUPLICATE";
    descriptor: Ljava/lang/String;
  public static final java.lang.String CATALOG_MISSING = "SYMBOL_CATALOG_MISSING";
    descriptor: Ljava/lang/String;
  public static final java.lang.String RENDERER_RESERVED_KEY = "SYMBOL_RENDERER_RESERVED_KEY";
    descriptor: Ljava/lang/String;
  public static final java.lang.String RENDERER_DUPLICATE = "SYMBOL_RENDERER_DUPLICATE";
    descriptor: Ljava/lang/String;
  public static final java.lang.String PORTRAYAL_SCALE_CONTEXT_REQUIRED = "PORTRAYAL_SCALE_CONTEXT_REQUIRED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String PORTRAYAL_SCALE_CRS_UNSUPPORTED = "PORTRAYAL_SCALE_CRS_UNSUPPORTED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String RENDERER_INVALID_RESULT = "SYMBOL_RENDERER_INVALID_RESULT";
    descriptor: Ljava/lang/String;
  public io.github.mundanej.map.api.SymbolException(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.api.SymbolLength extends java.lang.Record {
  public io.github.mundanej.map.api.SymbolLength(double, io.github.mundanej.map.api.SymbolUnit);
    descriptor: (DLio/github/mundanej/map/api/SymbolUnit;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double value();
    descriptor: ()D
  public io.github.mundanej.map.api.SymbolUnit unit();
    descriptor: ()Lio/github/mundanej/map/api/SymbolUnit;
}
public interface io.github.mundanej.map.api.SymbolRendererCapabilities {
  public abstract io.github.mundanej.map.api.RendererCapability capability(io.github.mundanej.map.api.Symbol);
    descriptor: (Lio/github/mundanej/map/api/Symbol;)Lio/github/mundanej/map/api/RendererCapability;
}
public final class io.github.mundanej.map.api.SymbolRendererKey extends java.lang.Record {
  public io.github.mundanej.map.api.SymbolRendererKey(java.lang.String);
    descriptor: (Ljava/lang/String;)V
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String value();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.SymbolRole extends java.lang.Enum<io.github.mundanej.map.api.SymbolRole> {
  public static final io.github.mundanej.map.api.SymbolRole MARKER;
    descriptor: Lio/github/mundanej/map/api/SymbolRole;
  public static final io.github.mundanej.map.api.SymbolRole LINE;
    descriptor: Lio/github/mundanej/map/api/SymbolRole;
  public static final io.github.mundanej.map.api.SymbolRole FILL;
    descriptor: Lio/github/mundanej/map/api/SymbolRole;
  public static final io.github.mundanej.map.api.SymbolRole LEGACY_GEOMETRY;
    descriptor: Lio/github/mundanej/map/api/SymbolRole;
  public static io.github.mundanej.map.api.SymbolRole[] values();
    descriptor: ()[Lio/github/mundanej/map/api/SymbolRole;
  public static io.github.mundanej.map.api.SymbolRole valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/SymbolRole;
}
public final class io.github.mundanej.map.api.SymbolRotationMode extends java.lang.Enum<io.github.mundanej.map.api.SymbolRotationMode> {
  public static final io.github.mundanej.map.api.SymbolRotationMode SCREEN_RELATIVE;
    descriptor: Lio/github/mundanej/map/api/SymbolRotationMode;
  public static final io.github.mundanej.map.api.SymbolRotationMode MAP_RELATIVE;
    descriptor: Lio/github/mundanej/map/api/SymbolRotationMode;
  public static io.github.mundanej.map.api.SymbolRotationMode[] values();
    descriptor: ()[Lio/github/mundanej/map/api/SymbolRotationMode;
  public static io.github.mundanej.map.api.SymbolRotationMode valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/SymbolRotationMode;
}
public interface io.github.mundanej.map.api.SymbolSelector {
  public abstract io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
}
public final class io.github.mundanej.map.api.SymbolSize extends java.lang.Record {
  public io.github.mundanej.map.api.SymbolSize(double, double, io.github.mundanej.map.api.SymbolUnit);
    descriptor: (DDLio/github/mundanej/map/api/SymbolUnit;)V
  public static io.github.mundanej.map.api.SymbolSize square(double, io.github.mundanej.map.api.SymbolUnit);
    descriptor: (DLio/github/mundanej/map/api/SymbolUnit;)Lio/github/mundanej/map/api/SymbolSize;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double width();
    descriptor: ()D
  public double height();
    descriptor: ()D
  public io.github.mundanej.map.api.SymbolUnit unit();
    descriptor: ()Lio/github/mundanej/map/api/SymbolUnit;
}
public final class io.github.mundanej.map.api.SymbolStroke extends java.lang.Record {
  public io.github.mundanej.map.api.SymbolStroke(io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.SymbolLength);
    descriptor: (Lio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/SymbolLength;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Rgba color();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.SymbolLength width();
    descriptor: ()Lio/github/mundanej/map/api/SymbolLength;
}
public final class io.github.mundanej.map.api.SymbolUnit extends java.lang.Enum<io.github.mundanej.map.api.SymbolUnit> {
  public static final io.github.mundanej.map.api.SymbolUnit SCREEN_PIXEL;
    descriptor: Lio/github/mundanej/map/api/SymbolUnit;
  public static final io.github.mundanej.map.api.SymbolUnit MAP_UNIT;
    descriptor: Lio/github/mundanej/map/api/SymbolUnit;
  public static io.github.mundanej.map.api.SymbolUnit[] values();
    descriptor: ()[Lio/github/mundanej/map/api/SymbolUnit;
  public static io.github.mundanej.map.api.SymbolUnit valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/SymbolUnit;
}
public final class io.github.mundanej.map.api.TextAttribute extends java.lang.Record implements io.github.mundanej.map.api.LabelTextSource {
  public io.github.mundanej.map.api.TextAttribute(java.lang.String);
    descriptor: (Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String attribute();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.TextPortrayal extends java.lang.Record {
  public io.github.mundanej.map.api.TextPortrayal(io.github.mundanej.map.api.PortrayalExpression, java.util.List<java.lang.String>, int, io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.TextPortrayal$Placement, java.util.Optional<io.github.mundanej.map.api.TextPortrayal$Halo>, double);
    descriptor: (Lio/github/mundanej/map/api/PortrayalExpression;Ljava/util/List;ILio/github/mundanej/map/api/SymbolLength;Lio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/TextPortrayal$Placement;Ljava/util/Optional;D)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.PortrayalExpression label();
    descriptor: ()Lio/github/mundanej/map/api/PortrayalExpression;
  public java.util.List<java.lang.String> fontFamilies();
    descriptor: ()Ljava/util/List;
  public int weight();
    descriptor: ()I
  public io.github.mundanej.map.api.SymbolLength size();
    descriptor: ()Lio/github/mundanej/map/api/SymbolLength;
  public io.github.mundanej.map.api.Rgba color();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.TextPortrayal$Placement placement();
    descriptor: ()Lio/github/mundanej/map/api/TextPortrayal$Placement;
  public java.util.Optional<io.github.mundanej.map.api.TextPortrayal$Halo> halo();
    descriptor: ()Ljava/util/Optional;
  public double opacity();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.TextPortrayal$Halo extends java.lang.Record {
  public io.github.mundanej.map.api.TextPortrayal$Halo(io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.SymbolLength);
    descriptor: (Lio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/SymbolLength;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.Rgba color();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.SymbolLength radius();
    descriptor: ()Lio/github/mundanej/map/api/SymbolLength;
}
public final class io.github.mundanej.map.api.TextPortrayal$Mode extends java.lang.Enum<io.github.mundanej.map.api.TextPortrayal$Mode> {
  public static final io.github.mundanej.map.api.TextPortrayal$Mode POINT;
    descriptor: Lio/github/mundanej/map/api/TextPortrayal$Mode;
  public static final io.github.mundanej.map.api.TextPortrayal$Mode LINE;
    descriptor: Lio/github/mundanej/map/api/TextPortrayal$Mode;
  public static io.github.mundanej.map.api.TextPortrayal$Mode[] values();
    descriptor: ()[Lio/github/mundanej/map/api/TextPortrayal$Mode;
  public static io.github.mundanej.map.api.TextPortrayal$Mode valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/TextPortrayal$Mode;
}
public final class io.github.mundanej.map.api.TextPortrayal$Placement extends java.lang.Record {
  public io.github.mundanej.map.api.TextPortrayal$Placement(io.github.mundanej.map.api.TextPortrayal$Mode, double, double, double, double, double, double, double);
    descriptor: (Lio/github/mundanej/map/api/TextPortrayal$Mode;DDDDDDD)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.TextPortrayal$Mode mode();
    descriptor: ()Lio/github/mundanej/map/api/TextPortrayal$Mode;
  public double anchorX();
    descriptor: ()D
  public double anchorY();
    descriptor: ()D
  public double displacementX();
    descriptor: ()D
  public double displacementY();
    descriptor: ()D
  public double rotationDegrees();
    descriptor: ()D
  public double repeatGap();
    descriptor: ()D
  public double maximumAngleDelta();
    descriptor: ()D
}
public final class io.github.mundanej.map.api.ThematicValue {
  public static io.github.mundanej.map.api.ThematicValue text(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/ThematicValue;
  public static io.github.mundanej.map.api.ThematicValue logical(boolean);
    descriptor: (Z)Lio/github/mundanej/map/api/ThematicValue;
  public static io.github.mundanej.map.api.ThematicValue numeric(long);
    descriptor: (J)Lio/github/mundanej/map/api/ThematicValue;
  public static io.github.mundanej.map.api.ThematicValue numeric(double);
    descriptor: (D)Lio/github/mundanej/map/api/ThematicValue;
  public static io.github.mundanej.map.api.ThematicValue numeric(java.math.BigDecimal);
    descriptor: (Ljava/math/BigDecimal;)Lio/github/mundanej/map/api/ThematicValue;
  public static io.github.mundanej.map.api.ThematicValue date(java.time.LocalDate);
    descriptor: (Ljava/time/LocalDate;)Lio/github/mundanej/map/api/ThematicValue;
  public static io.github.mundanej.map.api.ThematicValue nullValue();
    descriptor: ()Lio/github/mundanej/map/api/ThematicValue;
  public static java.util.Optional<io.github.mundanej.map.api.ThematicValue> fromAttribute(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Ljava/util/Optional;
  public io.github.mundanej.map.api.ThematicValue$Kind kind();
    descriptor: ()Lio/github/mundanej/map/api/ThematicValue$Kind;
  public java.lang.Object value();
    descriptor: ()Ljava/lang/Object;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.ThematicValue$Kind extends java.lang.Enum<io.github.mundanej.map.api.ThematicValue$Kind> {
  public static final io.github.mundanej.map.api.ThematicValue$Kind TEXT;
    descriptor: Lio/github/mundanej/map/api/ThematicValue$Kind;
  public static final io.github.mundanej.map.api.ThematicValue$Kind LOGICAL;
    descriptor: Lio/github/mundanej/map/api/ThematicValue$Kind;
  public static final io.github.mundanej.map.api.ThematicValue$Kind NUMERIC;
    descriptor: Lio/github/mundanej/map/api/ThematicValue$Kind;
  public static final io.github.mundanej.map.api.ThematicValue$Kind DATE;
    descriptor: Lio/github/mundanej/map/api/ThematicValue$Kind;
  public static final io.github.mundanej.map.api.ThematicValue$Kind NULL;
    descriptor: Lio/github/mundanej/map/api/ThematicValue$Kind;
  public static io.github.mundanej.map.api.ThematicValue$Kind[] values();
    descriptor: ()[Lio/github/mundanej/map/api/ThematicValue$Kind;
  public static io.github.mundanej.map.api.ThematicValue$Kind valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/ThematicValue$Kind;
}
public final class io.github.mundanej.map.api.VectorExportSnapshot {
  public static io.github.mundanej.map.api.VectorExportSnapshot of(int, int, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame, int, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Primitive>, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Label>);
    descriptor: (IILio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/VectorExportSnapshot$ViewFrame;ILjava/util/List;Ljava/util/List;)Lio/github/mundanej/map/api/VectorExportSnapshot;
  public static io.github.mundanej.map.api.VectorExportSnapshot of(int, int, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame, int, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Primitive>, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Label>, io.github.mundanej.map.api.VectorExportSnapshotLimits);
    descriptor: (IILio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/VectorExportSnapshot$ViewFrame;ILjava/util/List;Ljava/util/List;Lio/github/mundanej/map/api/VectorExportSnapshotLimits;)Lio/github/mundanej/map/api/VectorExportSnapshot;
  public static io.github.mundanej.map.api.VectorExportSnapshot of(int, int, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame, int, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Primitive>, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Label>, io.github.mundanej.map.api.VectorExportSnapshotLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (IILio/github/mundanej/map/api/Rgba;Lio/github/mundanej/map/api/VectorExportSnapshot$ViewFrame;ILjava/util/List;Ljava/util/List;Lio/github/mundanej/map/api/VectorExportSnapshotLimits;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/VectorExportSnapshot;
  public int widthPixels();
    descriptor: ()I
  public int heightPixels();
    descriptor: ()I
  public io.github.mundanej.map.api.Rgba background();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame viewFrame();
    descriptor: ()Lio/github/mundanej/map/api/VectorExportSnapshot$ViewFrame;
  public int layerCount();
    descriptor: ()I
  public java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Primitive> primitives();
    descriptor: ()Ljava/util/List;
  public java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Label> labels();
    descriptor: ()Ljava/util/List;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.VectorExportSnapshot$Label extends java.lang.Record {
  public io.github.mundanej.map.api.VectorExportSnapshot$Label(java.lang.String, io.github.mundanej.map.api.LabelTextStyle, double, double, double, int);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/LabelTextStyle;DDDI)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String text();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.LabelTextStyle style();
    descriptor: ()Lio/github/mundanej/map/api/LabelTextStyle;
  public double baselineX();
    descriptor: ()D
  public double baselineY();
    descriptor: ()D
  public double measuredAdvance();
    descriptor: ()D
  public int ordinaryPaintOrdinal();
    descriptor: ()I
}
public final class io.github.mundanej.map.api.VectorExportSnapshot$Primitive extends java.lang.Record {
  public io.github.mundanej.map.api.VectorExportSnapshot$Primitive(int, int, io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Symbol);
    descriptor: (IILio/github/mundanej/map/api/Geometry;Lio/github/mundanej/map/api/Symbol;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int layerIndex();
    descriptor: ()I
  public int featureIndex();
    descriptor: ()I
  public io.github.mundanej.map.api.Geometry screenGeometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
  public io.github.mundanej.map.api.Symbol symbol();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
}
public final class io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame extends java.lang.Record {
  public io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame(double, double, io.github.mundanej.map.api.Coordinate);
    descriptor: (DDLio/github/mundanej/map/api/Coordinate;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public double screenPixelsPerMapUnit();
    descriptor: ()D
  public double mapXAxisScreenBearingDegrees();
    descriptor: ()D
  public io.github.mundanej.map.api.Coordinate mapOriginScreen();
    descriptor: ()Lio/github/mundanej/map/api/Coordinate;
}
public final class io.github.mundanej.map.api.VectorExportSnapshotException extends java.lang.RuntimeException {
  public io.github.mundanej.map.api.VectorExportSnapshotException(java.lang.String, io.github.mundanej.map.api.VectorExportSnapshotProblem, java.lang.Throwable);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/VectorExportSnapshotProblem;Ljava/lang/Throwable;)V
  public io.github.mundanej.map.api.VectorExportSnapshotException(java.lang.String, io.github.mundanej.map.api.VectorExportSnapshotProblem);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/VectorExportSnapshotProblem;)V
  public io.github.mundanej.map.api.VectorExportSnapshotProblem problem();
    descriptor: ()Lio/github/mundanej/map/api/VectorExportSnapshotProblem;
}
public final class io.github.mundanej.map.api.VectorExportSnapshotLimits extends java.lang.Record {
  public static final int PAGE_AXIS_HARD_MAXIMUM = 16384;
    descriptor: I
  public static final int LAYERS_HARD_MAXIMUM = 1024;
    descriptor: I
  public static final int FEATURES_HARD_MAXIMUM = 100000;
    descriptor: I
  public static final int COORDINATES_HARD_MAXIMUM = 10000000;
    descriptor: I
  public static final int COMPOSITE_DEPTH_HARD_MAXIMUM = 64;
    descriptor: I
  public static final int SYMBOL_NODES_HARD_MAXIMUM = 1000000;
    descriptor: I
  public static final int LABELS_HARD_MAXIMUM = 4096;
    descriptor: I
  public static final int LABEL_CODE_POINTS_HARD_MAXIMUM = 262144;
    descriptor: I
  public static final long OWNED_BYTES_HARD_MAXIMUM = 268435456l;
    descriptor: J
  public io.github.mundanej.map.api.VectorExportSnapshotLimits(int, int, int, int, int, int, int, int, long);
    descriptor: (IIIIIIIIJ)V
  public static io.github.mundanej.map.api.VectorExportSnapshotLimits defaults();
    descriptor: ()Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public io.github.mundanej.map.api.VectorExportSnapshotLimits withMaximumPageAxis(int);
    descriptor: (I)Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public io.github.mundanej.map.api.VectorExportSnapshotLimits withMaximumLayers(int);
    descriptor: (I)Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public io.github.mundanej.map.api.VectorExportSnapshotLimits withMaximumFeatures(int);
    descriptor: (I)Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public io.github.mundanej.map.api.VectorExportSnapshotLimits withMaximumCoordinates(int);
    descriptor: (I)Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public io.github.mundanej.map.api.VectorExportSnapshotLimits withMaximumCompositeDepth(int);
    descriptor: (I)Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public io.github.mundanej.map.api.VectorExportSnapshotLimits withMaximumSymbolNodes(int);
    descriptor: (I)Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public io.github.mundanej.map.api.VectorExportSnapshotLimits withMaximumLabels(int);
    descriptor: (I)Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public io.github.mundanej.map.api.VectorExportSnapshotLimits withMaximumLabelCodePoints(int);
    descriptor: (I)Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public io.github.mundanej.map.api.VectorExportSnapshotLimits withMaximumOwnedBytes(long);
    descriptor: (J)Lio/github/mundanej/map/api/VectorExportSnapshotLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int maximumPageAxis();
    descriptor: ()I
  public int maximumLayers();
    descriptor: ()I
  public int maximumFeatures();
    descriptor: ()I
  public int maximumCoordinates();
    descriptor: ()I
  public int maximumCompositeDepth();
    descriptor: ()I
  public int maximumSymbolNodes();
    descriptor: ()I
  public int maximumLabels();
    descriptor: ()I
  public int maximumLabelCodePoints();
    descriptor: ()I
  public long maximumOwnedBytes();
    descriptor: ()J
}
public final class io.github.mundanej.map.api.VectorExportSnapshotProblem extends java.lang.Record {
  public io.github.mundanej.map.api.VectorExportSnapshotProblem(java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
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
public final class io.github.mundanej.map.api.VectorMarkerSymbol implements io.github.mundanej.map.api.MarkerSymbol {
  public static final io.github.mundanej.map.api.SymbolRendererKey RENDERER_KEY;
    descriptor: Lio/github/mundanej/map/api/SymbolRendererKey;
  public static io.github.mundanej.map.api.VectorMarkerSymbol of(io.github.mundanej.map.api.VectorPath, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.Rgba, java.util.Optional<io.github.mundanej.map.api.SymbolStroke>, io.github.mundanej.map.api.MarkerPlacement, double);
    descriptor: (Lio/github/mundanej/map/api/VectorPath;Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/Rgba;Ljava/util/Optional;Lio/github/mundanej/map/api/MarkerPlacement;D)Lio/github/mundanej/map/api/VectorMarkerSymbol;
  public static io.github.mundanej.map.api.VectorMarkerSymbol filledScreen(io.github.mundanej.map.api.VectorPath, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.Rgba, double, double);
    descriptor: (Lio/github/mundanej/map/api/VectorPath;Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/api/Rgba;DD)Lio/github/mundanej/map/api/VectorMarkerSymbol;
  public io.github.mundanej.map.api.VectorPath path();
    descriptor: ()Lio/github/mundanej/map/api/VectorPath;
  public io.github.mundanej.map.api.Envelope viewBox();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public io.github.mundanej.map.api.Rgba fill();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public java.util.Optional<io.github.mundanej.map.api.SymbolStroke> stroke();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.MarkerPlacement placement();
    descriptor: ()Lio/github/mundanej/map/api/MarkerPlacement;
  public double opacity();
    descriptor: ()D
  public io.github.mundanej.map.api.SymbolRendererKey rendererKey();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRendererKey;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.VectorPath {
  public static io.github.mundanej.map.api.VectorPath of(io.github.mundanej.map.api.VectorPathCommand[], double...);
    descriptor: ([Lio/github/mundanej/map/api/VectorPathCommand;[D)Lio/github/mundanej/map/api/VectorPath;
  public static io.github.mundanej.map.api.VectorPath$Builder builder();
    descriptor: ()Lio/github/mundanej/map/api/VectorPath$Builder;
  public int commandCount();
    descriptor: ()I
  public io.github.mundanej.map.api.VectorPathCommand commandAt(int);
    descriptor: (I)Lio/github/mundanej/map/api/VectorPathCommand;
  public int ordinateCount();
    descriptor: ()I
  public double ordinateAt(int);
    descriptor: (I)D
  public io.github.mundanej.map.api.VectorPathCommand[] toCommandArray();
    descriptor: ()[Lio/github/mundanej/map/api/VectorPathCommand;
  public double[] toOrdinateArray();
    descriptor: ()[D
  public io.github.mundanej.map.api.Envelope coordinateEnvelope();
    descriptor: ()Lio/github/mundanej/map/api/Envelope;
  public boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public int hashCode();
    descriptor: ()I
  public java.lang.String toString();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.api.VectorPath$Builder {
  public io.github.mundanej.map.api.VectorPath$Builder moveTo(double, double);
    descriptor: (DD)Lio/github/mundanej/map/api/VectorPath$Builder;
  public io.github.mundanej.map.api.VectorPath$Builder lineTo(double, double);
    descriptor: (DD)Lio/github/mundanej/map/api/VectorPath$Builder;
  public io.github.mundanej.map.api.VectorPath$Builder quadraticTo(double, double, double, double);
    descriptor: (DDDD)Lio/github/mundanej/map/api/VectorPath$Builder;
  public io.github.mundanej.map.api.VectorPath$Builder cubicTo(double, double, double, double, double, double);
    descriptor: (DDDDDD)Lio/github/mundanej/map/api/VectorPath$Builder;
  public io.github.mundanej.map.api.VectorPath$Builder close();
    descriptor: ()Lio/github/mundanej/map/api/VectorPath$Builder;
  public io.github.mundanej.map.api.VectorPath build();
    descriptor: ()Lio/github/mundanej/map/api/VectorPath;
}
public final class io.github.mundanej.map.api.VectorPathCommand extends java.lang.Enum<io.github.mundanej.map.api.VectorPathCommand> {
  public static final io.github.mundanej.map.api.VectorPathCommand MOVE_TO;
    descriptor: Lio/github/mundanej/map/api/VectorPathCommand;
  public static final io.github.mundanej.map.api.VectorPathCommand LINE_TO;
    descriptor: Lio/github/mundanej/map/api/VectorPathCommand;
  public static final io.github.mundanej.map.api.VectorPathCommand QUADRATIC_TO;
    descriptor: Lio/github/mundanej/map/api/VectorPathCommand;
  public static final io.github.mundanej.map.api.VectorPathCommand CUBIC_TO;
    descriptor: Lio/github/mundanej/map/api/VectorPathCommand;
  public static final io.github.mundanej.map.api.VectorPathCommand CLOSE;
    descriptor: Lio/github/mundanej/map/api/VectorPathCommand;
  public static io.github.mundanej.map.api.VectorPathCommand[] values();
    descriptor: ()[Lio/github/mundanej/map/api/VectorPathCommand;
  public static io.github.mundanej.map.api.VectorPathCommand valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/api/VectorPathCommand;
  public int arity();
    descriptor: ()I
}
SHAPE io.github.mundanej.map.api.AdvancedFillSymbol sealed=false permits=[] record=[color:java.util.Optional<io.github.mundanej.map.api.Rgba>[], graphicFill:java.util.Optional<io.github.mundanej.map.api.GraphicPaint>[], outline:java.util.Optional<io.github.mundanej.map.api.AdvancedStroke>[], opacity:double[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<io.github.mundanej.map.api.Rgba>, java.util.Optional<io.github.mundanej.map.api.GraphicPaint>, java.util.Optional<io.github.mundanej.map.api.AdvancedStroke>, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], field:RENDERER_KEY[], method:color[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:graphicFill[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:outline[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AdvancedLineSymbol sealed=false permits=[] record=[stroke:io.github.mundanej.map.api.AdvancedStroke[], opacity:double[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.AdvancedStroke, double] throws=[] annotations=[] parameterAnnotations=[[], []], field:RENDERER_KEY[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:stroke[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AdvancedStroke sealed=false permits=[] record=[color:io.github.mundanej.map.api.Rgba[], width:io.github.mundanej.map.api.SymbolLength[], cap:io.github.mundanej.map.api.AdvancedStroke$Cap[], join:io.github.mundanej.map.api.AdvancedStroke$Join[], dashArray:java.util.List<java.lang.Double>[], dashOffset:double[], perpendicularOffset:double[], graphicStroke:java.util.Optional<io.github.mundanej.map.api.GraphicPaint>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.api.AdvancedStroke$Cap, io.github.mundanej.map.api.AdvancedStroke$Join, java.util.List<java.lang.Double>, double, double, java.util.Optional<io.github.mundanej.map.api.GraphicPaint>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], method:cap[] throws=[] annotations=[] parameterAnnotations=[], method:color[] throws=[] annotations=[] parameterAnnotations=[], method:dashArray[] throws=[] annotations=[] parameterAnnotations=[], method:dashOffset[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:graphicStroke[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:join[] throws=[] annotations=[] parameterAnnotations=[], method:perpendicularOffset[] throws=[] annotations=[] parameterAnnotations=[], method:solid[io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.SymbolLength] throws=[] annotations=[] parameterAnnotations=[[], []], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AdvancedStroke$Cap sealed=false permits=[] record=[] enum=[BUTT, ROUND, SQUARE] annotations=[] members=[field:BUTT[], field:ROUND[], field:SQUARE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AdvancedStroke$Join sealed=false permits=[] record=[] enum=[MITER, ROUND, BEVEL] annotations=[] members=[field:BEVEL[], field:MITER[], field:ROUND[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeBytes sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[byte[]] throws=[] annotations=[] parameterAnnotations=[[]], method:byteAt[int] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:length[] throws=[] annotations=[] parameterAnnotations=[], method:toArray[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeField sealed=false permits=[] record=[name:java.lang.String[], type:io.github.mundanej.map.api.AttributeType[], nullable:boolean[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.AttributeType, boolean] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:accepts[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:nullable[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:type[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeNull sealed=false permits=[] record=[] enum=[INSTANCE] annotations=[] members=[field:INSTANCE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeSchema sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.util.List<io.github.mundanej.map.api.AttributeField>] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:field[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:fields[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeSelection sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:ALL[], field:NONE[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:isOnly[] throws=[] annotations=[] parameterAnnotations=[], method:only[java.util.List<java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[]], method:orderedNames[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeType sealed=false permits=[] record=[] enum=[TEXT, LOGICAL, INTEGER, FLOATING, DECIMAL, DATE, BINARY, STRUCTURED] annotations=[] members=[field:BINARY[], field:DATE[], field:DECIMAL[], field:FLOATING[], field:INTEGER[], field:LOGICAL[], field:STRUCTURED[], field:TEXT[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeValueCandidate sealed=true permits=[io.github.mundanej.map.api.AttributeValueCandidate$Attribute, io.github.mundanej.map.api.AttributeValueCandidate$Literal] record=[] enum=[] annotations=[] members=[]
SHAPE io.github.mundanej.map.api.AttributeValueCandidate$Attribute sealed=false permits=[] record=[name:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeValueCandidate$Literal sealed=false permits=[] record=[value:io.github.mundanej.map.api.ThematicValue[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.ThematicValue] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeValueConversion sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:IDENTITY[], field:TO_NUMBER[], field:TO_STRING[], method:candidates[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:operation[] throws=[] annotations=[] parameterAnnotations=[], method:toNumber[java.util.List<? extends io.github.mundanej.map.api.AttributeValueCandidate>] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeValueConversion$Operation sealed=false permits=[] record=[] enum=[IDENTITY, TO_NUMBER, TO_STRING] annotations=[] members=[field:IDENTITY[], field:TO_NUMBER[], field:TO_STRING[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.AttributeValues sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:canonicalizeValue[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:canonicalize[java.util.Map<java.lang.String, ?>] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.BuiltInMarker sealed=false permits=[] record=[] enum=[CIRCLE, SQUARE, TRIANGLE, DIAMOND, CROSS, X, STAR, ARROW] annotations=[] members=[field:ARROW[], field:CIRCLE[], field:CROSS[], field:DIAMOND[], field:SQUARE[], field:STAR[], field:TRIANGLE[], field:X[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CancellationSource sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[] throws=[] annotations=[] parameterAnnotations=[], method:cancel[] throws=[] annotations=[] parameterAnnotations=[], method:token[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CancellationToken sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:isCancellationRequested[] throws=[] annotations=[] parameterAnnotations=[], method:none[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CancellationToken$NeverCancelled sealed=false permits=[] record=[] enum=[INSTANCE] annotations=[] members=[field:INSTANCE[], method:isCancellationRequested[] throws=[] annotations=[] parameterAnnotations=[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CategoricalSymbolRule sealed=false permits=[] record=[value:io.github.mundanej.map.api.ThematicValue[], symbol:io.github.mundanej.map.api.Symbol[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.ThematicValue, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:symbol[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CategoricalSymbolSelector sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.List<io.github.mundanej.map.api.CategoricalSymbolRule>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:MAXIMUM_RULES[], method:attribute[] throws=[] annotations=[] parameterAnnotations=[], method:conversion[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:expressionInput[java.lang.String, java.util.List<io.github.mundanej.map.api.CategoricalSymbolRule>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>, io.github.mundanej.map.api.AttributeValueConversion] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:fallback[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:missingAsNull[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:rules[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CompositeSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:RENDERER_KEY[], method:children[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:of[java.util.List<? extends io.github.mundanej.map.api.Symbol>, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.Coordinate sealed=false permits=[] record=[x:double[], y:double[]] enum=[] annotations=[] members=[constructor:[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:x[] throws=[] annotations=[] parameterAnnotations=[], method:y[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CoordinateSequence sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:bounds[] throws=[] annotations=[] parameterAnnotations=[], method:coordinate[int] throws=[] annotations=[] parameterAnnotations=[[]], method:dimension[] throws=[] annotations=[] parameterAnnotations=[], method:empty[io.github.mundanej.map.api.GeometryDimension] throws=[] annotations=[] parameterAnnotations=[[]], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:isEmpty[] throws=[] annotations=[] parameterAnnotations=[], method:m[int] throws=[] annotations=[] parameterAnnotations=[[]], method:of[double[]] throws=[] annotations=[] parameterAnnotations=[[]], method:of[io.github.mundanej.map.api.GeometryDimension, double[]] throws=[] annotations=[] parameterAnnotations=[[], []], method:size[] throws=[] annotations=[] parameterAnnotations=[], method:toArray[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:x[int] throws=[] annotations=[] parameterAnnotations=[[]], method:y[int] throws=[] annotations=[] parameterAnnotations=[[]], method:z[int] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.CreateFeature sealed=false permits=[] record=[feature:io.github.mundanej.map.api.FeatureRecord[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.FeatureRecord] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:feature[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CrsAxis sealed=false permits=[] record=[meaning:io.github.mundanej.map.api.CrsAxisMeaning[], unit:io.github.mundanej.map.api.CrsUnit[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.CrsAxisMeaning, io.github.mundanej.map.api.CrsUnit] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:meaning[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unit[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CrsAxisMeaning sealed=false permits=[] record=[] enum=[LONGITUDE, LATITUDE, EASTING, NORTHING] annotations=[] members=[field:EASTING[], field:LATITUDE[], field:LONGITUDE[], field:NORTHING[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CrsDefinition sealed=false permits=[] record=[canonicalIdentifier:java.lang.String[], kind:io.github.mundanej.map.api.CrsKind[], xAxis:io.github.mundanej.map.api.CrsAxis[], yAxis:io.github.mundanej.map.api.CrsAxis[], coordinateDomain:io.github.mundanej.map.api.Envelope[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.CrsKind, io.github.mundanej.map.api.CrsAxis, io.github.mundanej.map.api.CrsAxis, io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:canonicalIdentifier[] throws=[] annotations=[] parameterAnnotations=[], method:coordinateDomain[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:kind[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:xAxis[] throws=[] annotations=[] parameterAnnotations=[], method:yAxis[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CrsException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.CrsProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CrsKind sealed=false permits=[] record=[] enum=[GEOGRAPHIC, PROJECTED, UNKNOWN] annotations=[] members=[field:GEOGRAPHIC[], field:PROJECTED[], field:UNKNOWN[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CrsMetadata sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:DECLARED_IDENTIFIER_LIMIT[], field:RETAINED_DEFINITION_LIMIT[], method:canonicalIdentifier[] throws=[] annotations=[] parameterAnnotations=[], method:declaredIdentifier[] throws=[] annotations=[] parameterAnnotations=[], method:definition[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:kind[] throws=[] annotations=[] parameterAnnotations=[], method:recognized[io.github.mundanej.map.api.CrsDefinition, java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:retainedDefinition[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unknown[java.util.Optional<java.lang.String>, java.util.Optional<java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.CrsProblem sealed=false permits=[] record=[code:java.lang.String[], message:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:message[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.CrsUnit sealed=false permits=[] record=[] enum=[DEGREE, METRE] annotations=[] members=[field:DEGREE[], field:METRE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.DeleteFeature sealed=false permits=[] record=[featureId:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.DiagnosticLocation sealed=false permits=[] record=[component:java.util.Optional<java.lang.String>[], recordNumber:java.util.OptionalLong[], partIndex:java.util.OptionalInt[], fieldIndex:java.util.OptionalInt[], fieldName:java.util.Optional<java.lang.String>[], byteOffset:java.util.OptionalLong[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<java.lang.String>, java.util.OptionalLong, java.util.OptionalInt, java.util.OptionalInt, java.util.Optional<java.lang.String>, java.util.OptionalLong] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:byteOffset[] throws=[] annotations=[] parameterAnnotations=[], method:component[] throws=[] annotations=[] parameterAnnotations=[], method:empty[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fieldIndex[] throws=[] annotations=[] parameterAnnotations=[], method:fieldName[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:partIndex[] throws=[] annotations=[] parameterAnnotations=[], method:recordNumber[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.DiagnosticReport sealed=false permits=[] record=[entries:java.util.List<io.github.mundanej.map.api.SourceDiagnostic>[], omittedWarningCount:long[]] enum=[] annotations=[] members=[constructor:[java.util.List<io.github.mundanej.map.api.SourceDiagnostic>, long] throws=[] annotations=[] parameterAnnotations=[[], []], method:empty[] throws=[] annotations=[] parameterAnnotations=[], method:entries[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:omittedWarningCount[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.DiagnosticSeverity sealed=false permits=[] record=[] enum=[WARNING, ERROR] annotations=[] members=[field:ERROR[], field:WARNING[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.DimensionalGeometry sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:coordinates[] throws=[] annotations=[] parameterAnnotations=[], method:dimension[] throws=[] annotations=[] parameterAnnotations=[], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:kind[] throws=[] annotations=[] parameterAnnotations=[], method:lineString[io.github.mundanej.map.api.CoordinateSequence] throws=[] annotations=[] parameterAnnotations=[[]], method:multiLineString[io.github.mundanej.map.api.CoordinateSequence, int[]] throws=[] annotations=[] parameterAnnotations=[[], []], method:multiPoint[io.github.mundanej.map.api.CoordinateSequence] throws=[] annotations=[] parameterAnnotations=[[]], method:multiPolygon[io.github.mundanej.map.api.CoordinateSequence, int[], int[], io.github.mundanej.map.api.GeometryLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:partCount[] throws=[] annotations=[] parameterAnnotations=[], method:partOffsets[] throws=[] annotations=[] parameterAnnotations=[], method:point[io.github.mundanej.map.api.CoordinateSequence, io.github.mundanej.map.api.GeometryLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:point[io.github.mundanej.map.api.CoordinateSequence] throws=[] annotations=[] parameterAnnotations=[[]], method:polygonPartOffsets[] throws=[] annotations=[] parameterAnnotations=[], method:polygon[io.github.mundanej.map.api.CoordinateSequence, int[]] throws=[] annotations=[] parameterAnnotations=[[], []], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.DistanceResult sealed=false permits=[] record=[metres:double[]] enum=[] annotations=[] members=[constructor:[double] throws=[] annotations=[] parameterAnnotations=[[]], field:ZERO[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:metres[] throws=[] annotations=[] parameterAnnotations=[], method:plus[io.github.mundanej.map.api.DistanceResult] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.DistanceStrategy sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:coordinateCrs[] throws=[] annotations=[] parameterAnnotations=[], method:distance[io.github.mundanej.map.api.Coordinate, io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.ElevationColorRamp sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.ElevationUnit, java.util.List<io.github.mundanej.map.api.ElevationColorStop>] throws=[] annotations=[] parameterAnnotations=[[], []], field:MAXIMUM_STOPS[], field:MINIMUM_STOPS[], method:colorAt[double] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:stops[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unit[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ElevationColorStop sealed=false permits=[] record=[elevation:double[], color:io.github.mundanej.map.api.Rgba[]] enum=[] annotations=[] members=[constructor:[double, io.github.mundanej.map.api.Rgba] throws=[] annotations=[] parameterAnnotations=[[], []], method:color[] throws=[] annotations=[] parameterAnnotations=[], method:elevation[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ElevationHillshade sealed=false permits=[] record=[azimuthDegrees:double[], altitudeDegrees:double[], verticalExaggeration:double[]] enum=[] annotations=[] members=[constructor:[double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:altitudeDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:azimuthDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:verticalExaggeration[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ElevationQueryMode sealed=false permits=[] record=[] enum=[NEAREST, BILINEAR] annotations=[] members=[field:BILINEAR[], field:NEAREST[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ElevationRasterStyle sealed=false permits=[] record=[colorRamp:io.github.mundanej.map.api.ElevationColorRamp[], noDataColor:io.github.mundanej.map.api.Rgba[], hillshade:java.util.Optional<io.github.mundanej.map.api.ElevationHillshade>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.ElevationColorRamp, io.github.mundanej.map.api.Rgba, java.util.Optional<io.github.mundanej.map.api.ElevationHillshade>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:colorRamp[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:hillshade[] throws=[] annotations=[] parameterAnnotations=[], method:noDataColor[] throws=[] annotations=[] parameterAnnotations=[], method:of[io.github.mundanej.map.api.ElevationColorRamp] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withHillshade[io.github.mundanej.map.api.ElevationHillshade] throws=[] annotations=[] parameterAnnotations=[[]], method:withNoDataColor[io.github.mundanej.map.api.Rgba] throws=[] annotations=[] parameterAnnotations=[[]], method:withoutHillshade[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ElevationSource sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:close[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:metadata[] throws=[] annotations=[] parameterAnnotations=[], method:openingDiagnostics[] throws=[] annotations=[] parameterAnnotations=[], method:sample[int, int] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.ElevationSourceLimits sealed=false permits=[] record=[maximumColumns:int[], maximumRows:int[], maximumSamples:long[], maximumRetainedSampleBytes:long[], maximumRetainedWarnings:int[]] enum=[] annotations=[] members=[constructor:[int, int, long, long, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], field:DEFAULTS[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumColumns[] throws=[] annotations=[] parameterAnnotations=[], method:maximumRetainedSampleBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumRetainedWarnings[] throws=[] annotations=[] parameterAnnotations=[], method:maximumRows[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSamples[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ElevationSourceMetadata sealed=false permits=[] record=[identity:io.github.mundanej.map.api.SourceIdentity[], columnCount:int[], rowCount:int[], sampleBounds:io.github.mundanej.map.api.Envelope[], crs:io.github.mundanej.map.api.CrsMetadata[], elevationUnit:io.github.mundanej.map.api.ElevationUnit[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.SourceIdentity, int, int, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.CrsMetadata, io.github.mundanej.map.api.ElevationUnit] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:columnCount[] throws=[] annotations=[] parameterAnnotations=[], method:columnSpacing[] throws=[] annotations=[] parameterAnnotations=[], method:crs[] throws=[] annotations=[] parameterAnnotations=[], method:elevationUnit[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:identity[] throws=[] annotations=[] parameterAnnotations=[], method:rowCount[] throws=[] annotations=[] parameterAnnotations=[], method:rowSpacing[] throws=[] annotations=[] parameterAnnotations=[], method:sampleBounds[] throws=[] annotations=[] parameterAnnotations=[], method:sampleCoordinate[int, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:sampleCount[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ElevationUnit sealed=false permits=[] record=[] enum=[METRE, INTERNATIONAL_FOOT, US_SURVEY_FOOT] annotations=[] members=[field:INTERNATIONAL_FOOT[], field:METRE[], field:US_SURVEY_FOOT[], method:metresPerUnit[] throws=[] annotations=[] parameterAnnotations=[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ElevationValue sealed=false permits=[] record=[value:double[], unit:io.github.mundanej.map.api.ElevationUnit[]] enum=[] annotations=[] members=[constructor:[double, io.github.mundanej.map.api.ElevationUnit] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unit[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.EmptyGeometry sealed=false permits=[] record=[kind:io.github.mundanej.map.api.GeometryKind[], dimension:io.github.mundanej.map.api.GeometryDimension[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.GeometryKind, io.github.mundanej.map.api.GeometryDimension] throws=[] annotations=[] parameterAnnotations=[[], []], method:bounds[] throws=[] annotations=[] parameterAnnotations=[], method:dimension[] throws=[] annotations=[] parameterAnnotations=[], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:isEmpty[] throws=[] annotations=[] parameterAnnotations=[], method:kind[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.EncodedRasterDecodeContext sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:bitsPerSample[] throws=[] annotations=[] parameterAnnotations=[], method:channelCount[] throws=[] annotations=[] parameterAnnotations=[], method:checkpoint[] throws=[] annotations=[] parameterAnnotations=[], method:claimReservedIntermediateBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:encodedByteLength[] throws=[] annotations=[] parameterAnnotations=[], method:format[] throws=[] annotations=[] parameterAnnotations=[], method:height[] throws=[] annotations=[] parameterAnnotations=[], method:interpolation[] throws=[] annotations=[] parameterAnnotations=[], method:outputHeight[] throws=[] annotations=[] parameterAnnotations=[], method:outputWidth[] throws=[] annotations=[] parameterAnnotations=[], method:sourceIdentity[] throws=[] annotations=[] parameterAnnotations=[], method:sourceWindow[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.EncodedRasterDecoder sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:decode[java.io.InputStream, io.github.mundanej.map.api.EncodedRasterDecodeContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:supportsInterpolation[io.github.mundanej.map.api.RasterInterpolation] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.EncodedRasterDecoderRegistry sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:builder[] throws=[] annotations=[] parameterAnnotations=[], method:find[io.github.mundanej.map.api.EncodedRasterFormat] throws=[] annotations=[] parameterAnnotations=[[]], method:formats[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.EncodedRasterDecoderRegistry$Builder sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:build[] throws=[] annotations=[] parameterAnnotations=[], method:register[io.github.mundanej.map.api.EncodedRasterFormat, io.github.mundanej.map.api.EncodedRasterDecoder] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.EncodedRasterDecoderRegistry$RegistrationException sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.EncodedRasterFormat sealed=false permits=[] record=[] enum=[PNG, JPEG] annotations=[] members=[field:JPEG[], field:PNG[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.Envelope sealed=false permits=[] record=[minX:double[], minY:double[], maxX:double[], maxY:double[]] enum=[] annotations=[] members=[constructor:[double, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:at[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:center[] throws=[] annotations=[] parameterAnnotations=[], method:contains[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:height[] throws=[] annotations=[] parameterAnnotations=[], method:maxX[] throws=[] annotations=[] parameterAnnotations=[], method:maxY[] throws=[] annotations=[] parameterAnnotations=[], method:minX[] throws=[] annotations=[] parameterAnnotations=[], method:minY[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:union[io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[]], method:width[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.Feature sealed=false permits=[] record=[id:java.lang.String[], name:java.lang.String[], geometry:io.github.mundanej.map.api.Geometry[], attributes:java.util.Map<java.lang.String, java.lang.Object>[], symbol:io.github.mundanej.map.api.Symbol[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, io.github.mundanej.map.api.Geometry, java.util.Map<java.lang.String, java.lang.Object>, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:attributes[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:geometry[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:symbol[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureCursor sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:advance[] throws=[] annotations=[] parameterAnnotations=[], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:current[] throws=[] annotations=[] parameterAnnotations=[], method:diagnostics[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureEditCause sealed=false permits=[] record=[] enum=[COMMIT, UNDO, REDO] annotations=[] members=[field:COMMIT[], field:REDO[], field:UNDO[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureEditCommand sealed=true permits=[io.github.mundanej.map.api.CreateFeature, io.github.mundanej.map.api.DeleteFeature, io.github.mundanej.map.api.ReplaceFeature] record=[] enum=[] annotations=[] members=[method:featureId[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureEditConfigurationException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.FeatureEditProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureEditEvent sealed=false permits=[] record=[cause:io.github.mundanej.map.api.FeatureEditCause[], previous:io.github.mundanej.map.api.FeatureEditSnapshot[], current:io.github.mundanej.map.api.FeatureEditSnapshot[], description:java.lang.String[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.FeatureEditCause, io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditSnapshot, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:cause[] throws=[] annotations=[] parameterAnnotations=[], method:current[] throws=[] annotations=[] parameterAnnotations=[], method:description[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:previous[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureEditHistoryLimits sealed=false permits=[] record=[maximumEntries:int[], maximumBytes:long[]] enum=[] annotations=[] members=[constructor:[int, long] throws=[] annotations=[] parameterAnnotations=[[], []], field:DEFAULT[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumEntries[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumEntries[int] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.FeatureEditLimits sealed=false permits=[] record=[maximumFeatures:int[], maximumCommandsPerTransaction:int[], maximumSnapshotBytes:long[]] enum=[] annotations=[] members=[constructor:[int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:DEFAULT[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCommandsPerTransaction[] throws=[] annotations=[] parameterAnnotations=[], method:maximumFeatures[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSnapshotBytes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumCommandsPerTransaction[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumFeatures[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumSnapshotBytes[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.FeatureEditListener sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:onFeatureEdit[io.github.mundanej.map.api.FeatureEditEvent] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.FeatureEditNotificationException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.FeatureEditResult, java.lang.RuntimeException] throws=[] annotations=[] parameterAnnotations=[[], []], method:committedResult[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureEditProblem sealed=false permits=[] record=[code:java.lang.String[], message:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:message[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureEditResult sealed=false permits=[] record=[status:io.github.mundanej.map.api.FeatureEditStatus[], snapshot:io.github.mundanej.map.api.FeatureEditSnapshot[], problem:java.util.Optional<io.github.mundanej.map.api.FeatureEditProblem>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.FeatureEditStatus, io.github.mundanej.map.api.FeatureEditSnapshot, java.util.Optional<io.github.mundanej.map.api.FeatureEditProblem>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:applied[io.github.mundanej.map.api.FeatureEditSnapshot] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:problem[] throws=[] annotations=[] parameterAnnotations=[], method:rejected[io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditProblem] throws=[] annotations=[] parameterAnnotations=[[], []], method:snapshot[] throws=[] annotations=[] parameterAnnotations=[], method:status[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unchanged[io.github.mundanej.map.api.FeatureEditSnapshot] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.FeatureEditSnapshot sealed=false permits=[] record=[revision:long[], crs:io.github.mundanej.map.api.CrsDefinition[], records:java.util.List<io.github.mundanej.map.api.FeatureRecord>[]] enum=[] annotations=[] members=[constructor:[long, io.github.mundanej.map.api.CrsDefinition, java.util.List<io.github.mundanej.map.api.FeatureRecord>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:crs[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:records[] throws=[] annotations=[] parameterAnnotations=[], method:revision[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureEditStatus sealed=false permits=[] record=[] enum=[APPLIED, UNCHANGED, REJECTED] annotations=[] members=[field:APPLIED[], field:REJECTED[], field:UNCHANGED[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureEditTransaction sealed=false permits=[] record=[expectedRevision:long[], description:java.lang.String[], commands:java.util.List<io.github.mundanej.map.api.FeatureEditCommand>[]] enum=[] annotations=[] members=[constructor:[long, java.lang.String, java.util.List<io.github.mundanej.map.api.FeatureEditCommand>] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:MAXIMUM_DESCRIPTION_LENGTH[], method:commands[] throws=[] annotations=[] parameterAnnotations=[], method:description[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:expectedRevision[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureName sealed=false permits=[] record=[] enum=[INSTANCE] annotations=[] members=[field:INSTANCE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureOverlaySymbols sealed=false permits=[] record=[marker:io.github.mundanej.map.api.MarkerSymbol[], line:io.github.mundanej.map.api.LineSymbol[], fill:io.github.mundanej.map.api.FillSymbol[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.MarkerSymbol, io.github.mundanej.map.api.LineSymbol, io.github.mundanej.map.api.FillSymbol] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:defaultHover[] throws=[] annotations=[] parameterAnnotations=[], method:defaultSelection[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fill[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:line[] throws=[] annotations=[] parameterAnnotations=[], method:marker[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeaturePortrayal sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<io.github.mundanej.map.api.PointLabelProfile>] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], constructor:[java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>, java.util.Optional<? extends io.github.mundanej.map.api.SymbolSelector>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fill[] throws=[] annotations=[] parameterAnnotations=[], method:fixed[io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:line[] throws=[] annotations=[] parameterAnnotations=[], method:marker[] throws=[] annotations=[] parameterAnnotations=[], method:markers[io.github.mundanej.map.api.SymbolSelector] throws=[] annotations=[] parameterAnnotations=[[]], method:pointLabel[] throws=[] annotations=[] parameterAnnotations=[], method:selectors[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withPointLabel[io.github.mundanej.map.api.PointLabelProfile] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.FeatureQuery sealed=false permits=[] record=[sourceBounds:java.util.Optional<io.github.mundanej.map.api.Envelope>[], attributes:io.github.mundanej.map.api.AttributeSelection[], tighterLimits:java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<io.github.mundanej.map.api.Envelope>, io.github.mundanej.map.api.AttributeSelection, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:all[] throws=[] annotations=[] parameterAnnotations=[], method:attributes[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:sourceBounds[] throws=[] annotations=[] parameterAnnotations=[], method:tighterLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureQueryLimits sealed=false permits=[] record=[recordsExamined:long[], recordsReturned:long[], coordinatesReturned:long[], attributeValuesReturned:long[], decodedTextCharactersReturned:long[], ownedPayloadBytes:long[], retainedWarnings:int[]] enum=[] annotations=[] members=[constructor:[long, long, long, long, long, long, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], []], field:LEVEL_1[], method:attributeValuesReturned[] throws=[] annotations=[] parameterAnnotations=[], method:coordinatesReturned[] throws=[] annotations=[] parameterAnnotations=[], method:decodedTextCharactersReturned[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:ownedPayloadBytes[] throws=[] annotations=[] parameterAnnotations=[], method:recordsExamined[] throws=[] annotations=[] parameterAnnotations=[], method:recordsReturned[] throws=[] annotations=[] parameterAnnotations=[], method:retainedWarnings[] throws=[] annotations=[] parameterAnnotations=[], method:tightens[io.github.mundanej.map.api.FeatureQueryLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureRecord sealed=false permits=[] record=[id:java.lang.String[], name:java.lang.String[], geometry:io.github.mundanej.map.api.Geometry[], attributes:java.util.Map<java.lang.String, java.lang.Object>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, io.github.mundanej.map.api.Geometry, java.util.Map<java.lang.String, java.lang.Object>] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:attributes[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:geometry[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureSelection sealed=false permits=[] record=[layerId:java.lang.String[], featureId:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layerId[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureSource sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:close[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:metadata[] throws=[] annotations=[] parameterAnnotations=[], method:openCursor[io.github.mundanej.map.api.FeatureQuery, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []], method:openingDiagnostics[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureSourceLimits sealed=false permits=[] record=[queryLimits:io.github.mundanej.map.api.FeatureQueryLimits[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.FeatureQueryLimits] throws=[] annotations=[] parameterAnnotations=[[]], field:LEVEL_1[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:queryLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureSourceMetadata sealed=false permits=[] record=[identity:io.github.mundanej.map.api.SourceIdentity[], extent:java.util.Optional<io.github.mundanej.map.api.Envelope>[], featureCount:java.util.OptionalLong[], schema:java.util.Optional<io.github.mundanej.map.api.AttributeSchema>[], crs:java.util.Optional<io.github.mundanej.map.api.CrsMetadata>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.SourceIdentity, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.OptionalLong, java.util.Optional<io.github.mundanej.map.api.AttributeSchema>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:crs[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:extent[] throws=[] annotations=[] parameterAnnotations=[], method:featureCount[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:identity[] throws=[] annotations=[] parameterAnnotations=[], method:schema[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FeatureStyle sealed=false permits=[] record=[stroke:io.github.mundanej.map.api.Rgba[], fill:io.github.mundanej.map.api.Rgba[], strokeWidth:double[], pointDiameter:double[]] enum=[] annotations=[@java.lang.Deprecated(forRemoval=false, since="")] members=[constructor:[io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.Rgba, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], field:RENDERER_KEY[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fill[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:line[io.github.mundanej.map.api.Rgba, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:pointDiameter[] throws=[] annotations=[] parameterAnnotations=[], method:point[io.github.mundanej.map.api.Rgba, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:polygon[io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.Rgba, double] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:strokeWidth[] throws=[] annotations=[] parameterAnnotations=[], method:stroke[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FillSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:role[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FilteredSymbolSelector sealed=false permits=[] record=[predicate:io.github.mundanej.map.api.PortrayalPredicate[], delegate:io.github.mundanej.map.api.SymbolSelector[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.PortrayalPredicate, io.github.mundanej.map.api.SymbolSelector] throws=[] annotations=[] parameterAnnotations=[[], []], method:delegate[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:predicate[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.FixedSymbolSelector sealed=false permits=[] record=[symbol:io.github.mundanej.map.api.Symbol[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:symbol[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.Geometry sealed=true permits=[io.github.mundanej.map.api.DimensionalGeometry, io.github.mundanej.map.api.EmptyGeometry, io.github.mundanej.map.api.GeometryCollection, io.github.mundanej.map.api.LineStringGeometry, io.github.mundanej.map.api.MultiLineStringGeometry, io.github.mundanej.map.api.MultiPointGeometry, io.github.mundanej.map.api.MultiPolygonGeometry, io.github.mundanej.map.api.PointGeometry, io.github.mundanej.map.api.PolygonGeometry] record=[] enum=[] annotations=[] members=[method:bounds[] throws=[] annotations=[] parameterAnnotations=[], method:dimension[] throws=[] annotations=[] parameterAnnotations=[], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:isEmpty[] throws=[] annotations=[] parameterAnnotations=[], method:kind[] throws=[] annotations=[] parameterAnnotations=[], method:visit[io.github.mundanej.map.api.GeometryVisitor] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.GeometryCollection sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:bounds[] throws=[] annotations=[] parameterAnnotations=[], method:dimension[] throws=[] annotations=[] parameterAnnotations=[], method:empty[io.github.mundanej.map.api.GeometryDimension] throws=[] annotations=[] parameterAnnotations=[[]], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:geometries[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:isEmpty[] throws=[] annotations=[] parameterAnnotations=[], method:kind[] throws=[] annotations=[] parameterAnnotations=[], method:of[java.util.List<? extends io.github.mundanej.map.api.Geometry>, io.github.mundanej.map.api.GeometryLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:of[java.util.List<? extends io.github.mundanej.map.api.Geometry>] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.GeometryDimension sealed=false permits=[] record=[] enum=[XY, XYZ, XYM, XYZM] annotations=[] members=[field:XYM[], field:XYZM[], field:XYZ[], field:XY[], method:hasM[] throws=[] annotations=[] parameterAnnotations=[], method:hasZ[] throws=[] annotations=[] parameterAnnotations=[], method:mOffset[] throws=[] annotations=[] parameterAnnotations=[], method:stride[] throws=[] annotations=[] parameterAnnotations=[], method:union[io.github.mundanej.map.api.GeometryDimension] throws=[] annotations=[] parameterAnnotations=[[]], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[], method:zOffset[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.GeometryException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:EMPTY_ENVELOPE[], field:KIND_UNSUPPORTED[], field:LIMIT_EXCEEDED[], field:ORDINATE_ABSENT[], field:ORDINATE_LOSS_REJECTED[], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.GeometryKind sealed=false permits=[] record=[] enum=[POINT, LINE_STRING, POLYGON, MULTI_POINT, MULTI_LINE_STRING, MULTI_POLYGON, GEOMETRY_COLLECTION] annotations=[] members=[field:GEOMETRY_COLLECTION[], field:LINE_STRING[], field:MULTI_LINE_STRING[], field:MULTI_POINT[], field:MULTI_POLYGON[], field:POINT[], field:POLYGON[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.GeometryLimits sealed=false permits=[] record=[maxCoordinates:long[], maxParts:long[], maxCollectionElements:long[], maxDepth:int[]] enum=[] annotations=[] members=[constructor:[long, long, long, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], field:DEFAULT[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maxCollectionElements[] throws=[] annotations=[] parameterAnnotations=[], method:maxCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maxDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maxParts[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.GeometryTraversal sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:visit[io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.GeometryVisitor, io.github.mundanej.map.api.GeometryLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.api.GeometryVisitor sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:visit[io.github.mundanej.map.api.Geometry, int] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.GraduatedSymbolSelector sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.List<io.github.mundanej.map.api.GraduatedSymbolStep>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:MAXIMUM_STEPS[], method:attribute[] throws=[] annotations=[] parameterAnnotations=[], method:conversion[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:expressionInput[java.lang.String, java.util.List<io.github.mundanej.map.api.GraduatedSymbolStep>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>, io.github.mundanej.map.api.AttributeValueConversion] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:fallback[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:input[] throws=[] annotations=[] parameterAnnotations=[], method:invalidFallback[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:steps[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:zoom[java.util.List<io.github.mundanej.map.api.GraduatedSymbolStep>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>, java.util.Optional<? extends io.github.mundanej.map.api.Symbol>] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.api.GraduatedSymbolStep sealed=false permits=[] record=[lowerInclusive:java.math.BigDecimal[], symbol:io.github.mundanej.map.api.Symbol[]] enum=[] annotations=[] members=[constructor:[java.math.BigDecimal, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:lowerInclusive[] throws=[] annotations=[] parameterAnnotations=[], method:symbol[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.GraphicPaint sealed=false permits=[] record=[graphic:io.github.mundanej.map.api.Symbol[], size:io.github.mundanej.map.api.SymbolSize[], gap:io.github.mundanej.map.api.SymbolLength[], rotationDegrees:double[], opacity:double[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.SymbolSize, io.github.mundanej.map.api.SymbolLength, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:gap[] throws=[] annotations=[] parameterAnnotations=[], method:graphic[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:rotationDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:size[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.HatchFillSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:DEFAULT_MAX_SEGMENTS[], field:RENDERER_KEY[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maxSegments[] throws=[] annotations=[] parameterAnnotations=[], method:of[io.github.mundanej.map.api.HatchPattern, io.github.mundanej.map.api.SymbolStroke, io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.api.SymbolRotationMode, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:of[io.github.mundanej.map.api.HatchPattern, io.github.mundanej.map.api.SymbolStroke, io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.api.SymbolRotationMode, java.util.Optional<io.github.mundanej.map.api.Symbol>, double, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], []], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:outline[] throws=[] annotations=[] parameterAnnotations=[], method:pattern[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:rotationMode[] throws=[] annotations=[] parameterAnnotations=[], method:spacing[] throws=[] annotations=[] parameterAnnotations=[], method:stroke[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.HatchPattern sealed=false permits=[] record=[] enum=[FORWARD_DIAGONAL, BACKWARD_DIAGONAL, CROSS_DIAGONAL] annotations=[] members=[field:BACKWARD_DIAGONAL[], field:CROSS_DIAGONAL[], field:FORWARD_DIAGONAL[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.InterpolatedSymbolSelector sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:MAXIMUM_STOPS[], method:attribute[] throws=[] annotations=[] parameterAnnotations=[], method:attribute[java.lang.String, java.util.List<io.github.mundanej.map.api.InterpolatedSymbolStop>, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:conversion[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:expressionInput[java.lang.String, java.util.List<io.github.mundanej.map.api.InterpolatedSymbolStop>, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.AttributeValueConversion] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:fallback[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:input[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:stops[] throws=[] annotations=[] parameterAnnotations=[], method:zoom[java.util.List<io.github.mundanej.map.api.InterpolatedSymbolStop>, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.InterpolatedSymbolStop sealed=false permits=[] record=[input:java.math.BigDecimal[], symbol:io.github.mundanej.map.api.Symbol[]] enum=[] annotations=[] members=[constructor:[java.math.BigDecimal, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:input[] throws=[] annotations=[] parameterAnnotations=[], method:symbol[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.InterpolationInput sealed=false permits=[] record=[] enum=[ATTRIBUTE, ZOOM] annotations=[] members=[field:ATTRIBUTE[], field:ZOOM[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.LabelPlacementException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.LabelPlacementProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.LabelPlacementProblem sealed=false permits=[] record=[code:java.lang.String[], message:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:message[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.LabelTextSource sealed=true permits=[io.github.mundanej.map.api.FeatureName, io.github.mundanej.map.api.LiteralLabelText, io.github.mundanej.map.api.StringifiedTextAttribute, io.github.mundanej.map.api.TextAttribute] record=[] enum=[] annotations=[] members=[]
SHAPE io.github.mundanej.map.api.LabelTextStyle sealed=false permits=[] record=[color:io.github.mundanej.map.api.Rgba[], weight:io.github.mundanej.map.api.LabelWeight[], sizePixels:double[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.LabelWeight, double] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:color[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:sizePixels[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:weight[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.LabelWeight sealed=false permits=[] record=[] enum=[NORMAL, BOLD] annotations=[] members=[field:BOLD[], field:NORMAL[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.Layer sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:features[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.LineStringGeometry sealed=false permits=[] record=[coordinates:io.github.mundanej.map.api.CoordinateSequence[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.CoordinateSequence] throws=[] annotations=[] parameterAnnotations=[[]], method:coordinates[] throws=[] annotations=[] parameterAnnotations=[], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.LineSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:role[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.LiteralLabelText sealed=false permits=[] record=[text:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:text[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapCursorIntent sealed=false permits=[] record=[] enum=[DEFAULT, CROSSHAIR, HAND, MOVE] annotations=[] members=[field:CROSSHAIR[], field:DEFAULT[], field:HAND[], field:MOVE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapHit sealed=false permits=[] record=[layerId:java.lang.String[], featureId:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layerId[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapHitResults sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:hits[] throws=[] annotations=[] parameterAnnotations=[], method:iterator[] throws=[] annotations=[] parameterAnnotations=[], method:of[java.util.List<io.github.mundanej.map.api.MapHit>] throws=[] annotations=[] parameterAnnotations=[[]], method:size[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:topmost[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapHoverEvent sealed=false permits=[] record=[previous:java.util.Optional<io.github.mundanej.map.api.MapHit>[], current:java.util.Optional<io.github.mundanej.map.api.MapHit>[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<io.github.mundanej.map.api.MapHit>, java.util.Optional<io.github.mundanej.map.api.MapHit>] throws=[] annotations=[] parameterAnnotations=[[], []], method:current[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:previous[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapHoverListener sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:onMapHoverChanged[io.github.mundanej.map.api.MapHoverEvent] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.MapInputModifier sealed=false permits=[] record=[] enum=[SHIFT, CONTROL, ALT, META, ALT_GRAPH] annotations=[] members=[field:ALT[], field:ALT_GRAPH[], field:CONTROL[], field:META[], field:SHIFT[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapPointerButton sealed=false permits=[] record=[number:int[]] enum=[] annotations=[] members=[constructor:[int] throws=[] annotations=[] parameterAnnotations=[[]], field:MIDDLE[], field:NONE[], field:PRIMARY[], field:SECONDARY[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:number[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapPointerEvent sealed=false permits=[] record=[type:io.github.mundanej.map.api.MapPointerEvent$Type[], screenX:double[], screenY:double[], mapCoordinate:java.util.Optional<io.github.mundanej.map.api.Coordinate>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.MapPointerEvent$Type, double, double, java.util.Optional<io.github.mundanej.map.api.Coordinate>] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:mapCoordinate[] throws=[] annotations=[] parameterAnnotations=[], method:screenX[] throws=[] annotations=[] parameterAnnotations=[], method:screenY[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:type[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapPointerEvent$Type sealed=false permits=[] record=[] enum=[MOVED, CLICKED] annotations=[] members=[field:CLICKED[], field:MOVED[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapPointerListener sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:onMapPointerEvent[io.github.mundanej.map.api.MapPointerEvent] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.MapSelectionEvent sealed=false permits=[] record=[previous:java.util.Optional<io.github.mundanej.map.api.FeatureSelection>[], current:java.util.Optional<io.github.mundanej.map.api.FeatureSelection>[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<io.github.mundanej.map.api.FeatureSelection>, java.util.Optional<io.github.mundanej.map.api.FeatureSelection>] throws=[] annotations=[] parameterAnnotations=[[], []], method:current[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:previous[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapSelectionListener sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:onMapSelectionChanged[io.github.mundanej.map.api.MapSelectionEvent] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.MapSourceReportEvent sealed=false permits=[] record=[layerId:java.lang.String[], previous:java.util.Optional<io.github.mundanej.map.api.DiagnosticReport>[], current:java.util.Optional<io.github.mundanej.map.api.DiagnosticReport>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.Optional<io.github.mundanej.map.api.DiagnosticReport>, java.util.Optional<io.github.mundanej.map.api.DiagnosticReport>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:current[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layerId[] throws=[] annotations=[] parameterAnnotations=[], method:previous[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapSourceReportListener sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:onMapSourceReportChanged[io.github.mundanej.map.api.MapSourceReportEvent] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.MapTool sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:cursorIntent[] throws=[] annotations=[] parameterAnnotations=[], method:onActivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onDeactivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onMapToolCommand[io.github.mundanej.map.api.MapToolCommandEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:onMapToolEvent[io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.MapToolCancelReason sealed=false permits=[] record=[] enum=[TOOL_REPLACED, TOOL_CLEARED, FOCUS_LOST, VIEW_DISABLED, VIEW_REMOVED, POINTER_EXITED, POINTER_STATE_LOST, SOURCE_FAILURE, USER_CANCEL] annotations=[] members=[field:FOCUS_LOST[], field:POINTER_EXITED[], field:POINTER_STATE_LOST[], field:SOURCE_FAILURE[], field:TOOL_CLEARED[], field:TOOL_REPLACED[], field:USER_CANCEL[], field:VIEW_DISABLED[], field:VIEW_REMOVED[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapToolCommand sealed=false permits=[] record=[] enum=[DELETE_BACKWARD, UNDO, REDO] annotations=[] members=[field:DELETE_BACKWARD[], field:REDO[], field:UNDO[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapToolCommandEvent sealed=false permits=[] record=[sequence:long[], command:io.github.mundanej.map.api.MapToolCommand[]] enum=[] annotations=[] members=[constructor:[long, io.github.mundanej.map.api.MapToolCommand] throws=[] annotations=[] parameterAnnotations=[[], []], method:command[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:sequence[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapToolContext sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:displayCrs[] throws=[] annotations=[] parameterAnnotations=[], method:mapCrs[] throws=[] annotations=[] parameterAnnotations=[], method:mapToScreen[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:requestRepaint[] throws=[] annotations=[] parameterAnnotations=[], method:screenToMap[double, double] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.MapToolEvent sealed=false permits=[] record=[sequence:long[], type:io.github.mundanej.map.api.MapToolEvent$Type[], screenX:double[], screenY:double[], mapCoordinate:java.util.Optional<io.github.mundanej.map.api.Coordinate>[], button:io.github.mundanej.map.api.MapPointerButton[], buttonsDown:java.util.Set<io.github.mundanej.map.api.MapPointerButton>[], modifiers:java.util.Set<io.github.mundanej.map.api.MapInputModifier>[], clickCount:int[], wheelRotation:double[], popupTrigger:boolean[], cancelReason:java.util.Optional<io.github.mundanej.map.api.MapToolCancelReason>[]] enum=[] annotations=[] members=[constructor:[long, io.github.mundanej.map.api.MapToolEvent$Type, double, double, java.util.Optional<io.github.mundanej.map.api.Coordinate>, io.github.mundanej.map.api.MapPointerButton, java.util.Set<io.github.mundanej.map.api.MapPointerButton>, java.util.Set<io.github.mundanej.map.api.MapInputModifier>, int, double, boolean, java.util.Optional<io.github.mundanej.map.api.MapToolCancelReason>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], []], method:button[] throws=[] annotations=[] parameterAnnotations=[], method:buttonsDown[] throws=[] annotations=[] parameterAnnotations=[], method:cancelReason[] throws=[] annotations=[] parameterAnnotations=[], method:clickCount[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:mapCoordinate[] throws=[] annotations=[] parameterAnnotations=[], method:modifiers[] throws=[] annotations=[] parameterAnnotations=[], method:popupTrigger[] throws=[] annotations=[] parameterAnnotations=[], method:screenX[] throws=[] annotations=[] parameterAnnotations=[], method:screenY[] throws=[] annotations=[] parameterAnnotations=[], method:sequence[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:type[] throws=[] annotations=[] parameterAnnotations=[], method:wheelRotation[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapToolEvent$Type sealed=false permits=[] record=[] enum=[PRESS, DRAG, RELEASE, MOVE, CLICK, WHEEL, CANCEL] annotations=[] members=[field:CANCEL[], field:CLICK[], field:DRAG[], field:MOVE[], field:PRESS[], field:RELEASE[], field:WHEEL[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MapToolResult sealed=false permits=[] record=[] enum=[PASS, CONSUME, CAPTURE] annotations=[] members=[field:CAPTURE[], field:CONSUME[], field:PASS[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MarkerPlacement sealed=false permits=[] record=[size:io.github.mundanej.map.api.SymbolSize[], anchor:io.github.mundanej.map.api.SymbolAnchor[], offsetX:double[], offsetY:double[], rotationDegrees:double[], rotationMode:io.github.mundanej.map.api.SymbolRotationMode[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.SymbolSize, io.github.mundanej.map.api.SymbolAnchor, double, double, double, io.github.mundanej.map.api.SymbolRotationMode] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:anchor[] throws=[] annotations=[] parameterAnnotations=[], method:centeredScreen[double] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:offsetX[] throws=[] annotations=[] parameterAnnotations=[], method:offsetY[] throws=[] annotations=[] parameterAnnotations=[], method:rotationDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:rotationMode[] throws=[] annotations=[] parameterAnnotations=[], method:size[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MarkerSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:role[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MeasurementPhase sealed=false permits=[] record=[] enum=[EMPTY, MEASURING, COMPLETE] annotations=[] members=[field:COMPLETE[], field:EMPTY[], field:MEASURING[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MeasurementState sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.MeasurementPhase, double[], java.util.Optional<io.github.mundanej.map.api.Coordinate>, io.github.mundanej.map.api.DistanceResult, java.util.Optional<io.github.mundanej.map.api.DistanceResult>, java.util.Optional<io.github.mundanej.map.api.DistanceResult>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:committedDistance[] throws=[] annotations=[] parameterAnnotations=[], method:displayedDistance[] throws=[] annotations=[] parameterAnnotations=[], method:empty[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:lastCommittedSegmentDistance[] throws=[] annotations=[] parameterAnnotations=[], method:packedVertices[] throws=[] annotations=[] parameterAnnotations=[], method:phase[] throws=[] annotations=[] parameterAnnotations=[], method:previewSegmentDistance[] throws=[] annotations=[] parameterAnnotations=[], method:preview[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:vertexCount[] throws=[] annotations=[] parameterAnnotations=[], method:vertex[int] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.MultiLineStringGeometry sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:coordinates[] throws=[] annotations=[] parameterAnnotations=[], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:ofParts[java.util.List<io.github.mundanej.map.api.CoordinateSequence>] throws=[] annotations=[] parameterAnnotations=[[]], method:of[io.github.mundanej.map.api.CoordinateSequence, int[]] throws=[] annotations=[] parameterAnnotations=[[], []], method:partCount[] throws=[] annotations=[] parameterAnnotations=[], method:partOffset[int] throws=[] annotations=[] parameterAnnotations=[[]], method:partOffsets[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MultiPointGeometry sealed=false permits=[] record=[coordinates:io.github.mundanej.map.api.CoordinateSequence[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.CoordinateSequence] throws=[] annotations=[] parameterAnnotations=[[]], method:coordinates[] throws=[] annotations=[] parameterAnnotations=[], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.MultiPolygonGeometry sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:coordinates[] throws=[] annotations=[] parameterAnnotations=[], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:ofPolygons[java.util.List<io.github.mundanej.map.api.PolygonGeometry>] throws=[] annotations=[] parameterAnnotations=[[]], method:of[io.github.mundanej.map.api.CoordinateSequence, int[], int[]] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:polygonCount[] throws=[] annotations=[] parameterAnnotations=[], method:polygonRingOffset[int] throws=[] annotations=[] parameterAnnotations=[[]], method:polygonRingOffsets[] throws=[] annotations=[] parameterAnnotations=[], method:ringCount[] throws=[] annotations=[] parameterAnnotations=[], method:ringOffset[int] throws=[] annotations=[] parameterAnnotations=[[]], method:ringOffsets[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.NamedSymbol sealed=false permits=[] record=[name:java.lang.String[], symbol:io.github.mundanej.map.api.Symbol[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:symbol[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.NamedSymbolCatalog sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:entries[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:find[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:iterator[] throws=[] annotations=[] parameterAnnotations=[], method:of[java.util.List<io.github.mundanej.map.api.NamedSymbol>] throws=[] annotations=[] parameterAnnotations=[[]], method:require[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:size[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.OmittedSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:of[io.github.mundanej.map.api.SymbolRole] throws=[] annotations=[] parameterAnnotations=[[]], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.OrdinateLossPolicy sealed=false permits=[] record=[] enum=[REJECT, DROP_TO_XY] annotations=[] members=[field:DROP_TO_XY[], field:REJECT[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PlacedPointLabel sealed=false permits=[] record=[layerId:java.lang.String[], featureId:java.lang.String[], text:java.lang.String[], style:io.github.mundanej.map.api.LabelTextStyle[], baselineX:double[], baselineY:double[], advance:double[], visualBounds:io.github.mundanej.map.api.ScreenBox[], collisionBounds:io.github.mundanej.map.api.ScreenBox[], ordinaryPaintOrdinal:int[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.lang.String, io.github.mundanej.map.api.LabelTextStyle, double, double, double, io.github.mundanej.map.api.ScreenBox, io.github.mundanej.map.api.ScreenBox, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], []], method:advance[] throws=[] annotations=[] parameterAnnotations=[], method:baselineX[] throws=[] annotations=[] parameterAnnotations=[], method:baselineY[] throws=[] annotations=[] parameterAnnotations=[], method:collisionBounds[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layerId[] throws=[] annotations=[] parameterAnnotations=[], method:ordinaryPaintOrdinal[] throws=[] annotations=[] parameterAnnotations=[], method:style[] throws=[] annotations=[] parameterAnnotations=[], method:text[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:visualBounds[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PointFeatureDraft sealed=false permits=[] record=[id:java.lang.String[], name:java.lang.String[], attributes:java.util.Map<java.lang.String, java.lang.Object>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.Object>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:at[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:attributes[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PointGeometry sealed=false permits=[] record=[coordinate:io.github.mundanej.map.api.Coordinate[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:coordinate[] throws=[] annotations=[] parameterAnnotations=[], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PointLabelAnchorBasis sealed=false permits=[] record=[] enum=[MARKER_BOUNDS, FEATURE_POINT] annotations=[] members=[field:FEATURE_POINT[], field:MARKER_BOUNDS[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PointLabelPosition sealed=false permits=[] record=[] enum=[CENTER, N, NE, E, SE, S, SW, W, NW] annotations=[] members=[field:CENTER[], field:E[], field:NE[], field:NW[], field:N[], field:SE[], field:SW[], field:S[], field:W[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PointLabelProfile sealed=false permits=[] record=[textSource:io.github.mundanej.map.api.LabelTextSource[], style:io.github.mundanej.map.api.LabelTextStyle[], positions:java.util.List<io.github.mundanej.map.api.PointLabelPosition>[], gapPixels:double[], offsetXPixels:double[], offsetYPixels:double[], collisionPaddingPixels:double[], priority:int[], visibleResolution:io.github.mundanej.map.api.ResolutionRange[], anchorBasis:io.github.mundanej.map.api.PointLabelAnchorBasis[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.LabelTextSource, io.github.mundanej.map.api.LabelTextStyle, java.util.List<io.github.mundanej.map.api.PointLabelPosition>, double, double, double, double, int, io.github.mundanej.map.api.ResolutionRange, io.github.mundanej.map.api.PointLabelAnchorBasis] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], [], []], constructor:[io.github.mundanej.map.api.LabelTextSource, io.github.mundanej.map.api.LabelTextStyle, java.util.List<io.github.mundanej.map.api.PointLabelPosition>, double, double, double, double, int, io.github.mundanej.map.api.ResolutionRange] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], []], method:anchorBasis[] throws=[] annotations=[] parameterAnnotations=[], method:collisionPaddingPixels[] throws=[] annotations=[] parameterAnnotations=[], method:compatibility[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:gapPixels[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:offsetXPixels[] throws=[] annotations=[] parameterAnnotations=[], method:offsetYPixels[] throws=[] annotations=[] parameterAnnotations=[], method:positions[] throws=[] annotations=[] parameterAnnotations=[], method:priority[] throws=[] annotations=[] parameterAnnotations=[], method:style[] throws=[] annotations=[] parameterAnnotations=[], method:textSource[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:visibleResolution[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PointLabelTexts sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:MAXIMUM_CODE_POINTS[], method:isLineSeparator[int] throws=[] annotations=[] parameterAnnotations=[[]], method:requireSupported[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.PointLabelTexts$FailureReason sealed=false permits=[] record=[] enum=[BLANK, TOO_LONG, MULTILINE] annotations=[] members=[field:BLANK[], field:MULTILINE[], field:TOO_LONG[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PointLabelTexts$ValidationException sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:codePoint[] throws=[] annotations=[] parameterAnnotations=[], method:reason[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PolygonGeometry sealed=false permits=[] record=[exterior:io.github.mundanej.map.api.CoordinateSequence[], holes:java.util.List<io.github.mundanej.map.api.CoordinateSequence>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.CoordinateSequence, java.util.List<io.github.mundanej.map.api.CoordinateSequence>] throws=[] annotations=[] parameterAnnotations=[[], []], constructor:[io.github.mundanej.map.api.CoordinateSequence] throws=[] annotations=[] parameterAnnotations=[[]], method:envelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:exterior[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:holes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalComparison sealed=false permits=[] record=[] enum=[EQUAL, NOT_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL] annotations=[] members=[field:EQUAL[], field:GREATER_THAN[], field:GREATER_THAN_OR_EQUAL[], field:LESS_THAN[], field:LESS_THAN_OR_EQUAL[], field:NOT_EQUAL[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalEvaluationContext sealed=false permits=[] record=[scaleDenominator:java.util.OptionalDouble[], zoomLevel:java.util.OptionalDouble[], geometryType:java.util.Optional<io.github.mundanej.map.api.PortrayalGeometryType>[]] enum=[] annotations=[] members=[constructor:[java.util.OptionalDouble, java.util.OptionalDouble, java.util.Optional<io.github.mundanej.map.api.PortrayalGeometryType>] throws=[] annotations=[] parameterAnnotations=[[], [], []], constructor:[java.util.OptionalDouble] throws=[] annotations=[] parameterAnnotations=[[]], field:UNSCALED[], method:atScaleAndZoom[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:atScale[double] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:geometryType[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:scaleDenominator[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withGeometryType[io.github.mundanej.map.api.PortrayalGeometryType] throws=[] annotations=[] parameterAnnotations=[[]], method:zoomLevel[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalEvaluationResult sealed=false permits=[] record=[value:java.util.Optional<java.lang.Object>[], code:java.lang.String[], message:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<java.lang.Object>, java.lang.String, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:failure[java.lang.String, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], []], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:message[] throws=[] annotations=[] parameterAnnotations=[], method:succeeded[] throws=[] annotations=[] parameterAnnotations=[], method:success[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalExpression sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:arguments[] throws=[] annotations=[] parameterAnnotations=[], method:attributeName[] throws=[] annotations=[] parameterAnnotations=[], method:attribute[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:call[io.github.mundanej.map.api.PortrayalExpression$Operator, java.util.List<io.github.mundanej.map.api.PortrayalExpression>, io.github.mundanej.map.api.PortrayalExpressionLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:call[io.github.mundanej.map.api.PortrayalExpression$Operator, java.util.List<io.github.mundanej.map.api.PortrayalExpression>] throws=[] annotations=[] parameterAnnotations=[[], []], method:depth[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:input[io.github.mundanej.map.api.PortrayalExpression$Operator] throws=[] annotations=[] parameterAnnotations=[[]], method:literal[] throws=[] annotations=[] parameterAnnotations=[], method:literal[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:nodeCount[] throws=[] annotations=[] parameterAnnotations=[], method:operator[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalExpression$Operator sealed=false permits=[] record=[] enum=[LITERAL, ATTRIBUTE, SCALE_DENOMINATOR, ZOOM_LEVEL, GEOMETRY_TYPE, ADD, MULTIPLY, CONCAT, EQUAL, COALESCE] annotations=[] members=[field:ADD[], field:ATTRIBUTE[], field:COALESCE[], field:CONCAT[], field:EQUAL[], field:GEOMETRY_TYPE[], field:LITERAL[], field:MULTIPLY[], field:SCALE_DENOMINATOR[], field:ZOOM_LEVEL[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalExpressionLimits sealed=false permits=[] record=[maxDepth:int[], maxNodes:int[], maxArguments:int[]] enum=[] annotations=[] members=[constructor:[int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:DEFAULT[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maxArguments[] throws=[] annotations=[] parameterAnnotations=[], method:maxDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maxNodes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalGeometryType sealed=false permits=[] record=[] enum=[POINT, LINE_STRING, POLYGON] annotations=[] members=[field:LINE_STRING[], field:POINT[], field:POLYGON[], method:fromGeometry[io.github.mundanej.map.api.Geometry] throws=[] annotations=[] parameterAnnotations=[[]], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalLogicalOperator sealed=false permits=[] record=[] enum=[AND, OR, NOT] annotations=[] members=[field:AND[], field:NOT[], field:OR[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalOperand sealed=true permits=[io.github.mundanej.map.api.PortrayalOperand$Literal, io.github.mundanej.map.api.PortrayalOperand$Property, io.github.mundanej.map.api.PortrayalOperand$TypedLiteral] record=[] enum=[] annotations=[] members=[]
SHAPE io.github.mundanej.map.api.PortrayalOperand$Literal sealed=false permits=[] record=[text:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:text[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalOperand$Property sealed=false permits=[] record=[name:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalOperand$TypedLiteral sealed=false permits=[] record=[value:io.github.mundanej.map.api.ThematicValue[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.ThematicValue] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalPredicate sealed=true permits=[io.github.mundanej.map.api.PortrayalPredicate$Between, io.github.mundanej.map.api.PortrayalPredicate$Comparison, io.github.mundanej.map.api.PortrayalPredicate$Constant, io.github.mundanej.map.api.PortrayalPredicate$Exists, io.github.mundanej.map.api.PortrayalPredicate$GeometryTypeIs, io.github.mundanej.map.api.PortrayalPredicate$IsNull, io.github.mundanej.map.api.PortrayalPredicate$Logical] record=[] enum=[] annotations=[] members=[]
SHAPE io.github.mundanej.map.api.PortrayalPredicate$Between sealed=false permits=[] record=[property:io.github.mundanej.map.api.PortrayalOperand$Property[], lower:io.github.mundanej.map.api.PortrayalOperand[], upper:io.github.mundanej.map.api.PortrayalOperand[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.PortrayalOperand$Property, io.github.mundanej.map.api.PortrayalOperand, io.github.mundanej.map.api.PortrayalOperand] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:lower[] throws=[] annotations=[] parameterAnnotations=[], method:property[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:upper[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalPredicate$Comparison sealed=false permits=[] record=[operation:io.github.mundanej.map.api.PortrayalComparison[], left:io.github.mundanej.map.api.PortrayalOperand[], right:io.github.mundanej.map.api.PortrayalOperand[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.PortrayalComparison, io.github.mundanej.map.api.PortrayalOperand, io.github.mundanej.map.api.PortrayalOperand] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:left[] throws=[] annotations=[] parameterAnnotations=[], method:operation[] throws=[] annotations=[] parameterAnnotations=[], method:right[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalPredicate$Constant sealed=false permits=[] record=[value:boolean[]] enum=[] annotations=[] members=[constructor:[boolean] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalPredicate$Exists sealed=false permits=[] record=[property:io.github.mundanej.map.api.PortrayalOperand$Property[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.PortrayalOperand$Property] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:property[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalPredicate$GeometryTypeIs sealed=false permits=[] record=[types:java.util.Set<io.github.mundanej.map.api.PortrayalGeometryType>[]] enum=[] annotations=[] members=[constructor:[java.util.Set<io.github.mundanej.map.api.PortrayalGeometryType>] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:types[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalPredicate$IsNull sealed=false permits=[] record=[property:io.github.mundanej.map.api.PortrayalOperand$Property[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.PortrayalOperand$Property] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:property[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalPredicate$Logical sealed=false permits=[] record=[operator:io.github.mundanej.map.api.PortrayalLogicalOperator[], children:java.util.List<io.github.mundanej.map.api.PortrayalPredicate>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.PortrayalLogicalOperator, java.util.List<io.github.mundanej.map.api.PortrayalPredicate>] throws=[] annotations=[] parameterAnnotations=[[], []], method:children[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:operator[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.PortrayalRule sealed=false permits=[] record=[name:java.util.Optional<java.lang.String>[], scale:io.github.mundanej.map.api.ScaleInterval[], predicate:java.util.Optional<io.github.mundanej.map.api.PortrayalPredicate>[], elseRule:boolean[], markers:java.util.List<io.github.mundanej.map.api.Symbol>[], lines:java.util.List<io.github.mundanej.map.api.Symbol>[], fills:java.util.List<io.github.mundanej.map.api.Symbol>[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<java.lang.String>, io.github.mundanej.map.api.ScaleInterval, java.util.Optional<io.github.mundanej.map.api.PortrayalPredicate>, boolean, java.util.List<io.github.mundanej.map.api.Symbol>, java.util.List<io.github.mundanej.map.api.Symbol>, java.util.List<io.github.mundanej.map.api.Symbol>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], []], method:elseRule[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fills[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:lines[] throws=[] annotations=[] parameterAnnotations=[], method:markers[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:predicate[] throws=[] annotations=[] parameterAnnotations=[], method:scale[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.Projection sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:projectEnvelope[io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[]], method:project[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:sourceCrs[] throws=[] annotations=[] parameterAnnotations=[], method:sourceDomain[] throws=[] annotations=[] parameterAnnotations=[], method:targetCrs[] throws=[] annotations=[] parameterAnnotations=[], method:targetDomain[] throws=[] annotations=[] parameterAnnotations=[], method:unprojectEnvelope[io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[]], method:unproject[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.RasterAffineTransform sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:a[] throws=[] annotations=[] parameterAnnotations=[], method:b[] throws=[] annotations=[] parameterAnnotations=[], method:c[] throws=[] annotations=[] parameterAnnotations=[], method:d[] throws=[] annotations=[] parameterAnnotations=[], method:e[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:f[] throws=[] annotations=[] parameterAnnotations=[], method:gridToMap[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:mapToGrid[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:of[double, double, double, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterGridPlacement sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:affineTransform[] throws=[] annotations=[] parameterAnnotations=[], method:affine[io.github.mundanej.map.api.RasterAffineTransform] throws=[] annotations=[] parameterAnnotations=[[]], method:axisAlignedBounds[] throws=[] annotations=[] parameterAnnotations=[], method:axisAligned[io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:kind[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterGridPlacement$Kind sealed=false permits=[] record=[] enum=[AXIS_ALIGNED, AFFINE] annotations=[] members=[field:AFFINE[], field:AXIS_ALIGNED[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterIconSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:MAX_DIMENSION[], field:MAX_PIXELS[], field:RENDERER_KEY[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:height[] throws=[] annotations=[] parameterAnnotations=[], method:interpolation[] throws=[] annotations=[] parameterAnnotations=[], method:nativeScreenSize[int, int, int[], io.github.mundanej.map.api.RasterInterpolation, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:of[int, int, int[], io.github.mundanej.map.api.MarkerPlacement, io.github.mundanej.map.api.RasterInterpolation, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:placement[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:rgbaAt[int, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:screenWidth[int, int, int[], double, io.github.mundanej.map.api.RasterInterpolation, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:toRgbaArray[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterInterpolation sealed=false permits=[] record=[] enum=[NEAREST, BILINEAR] annotations=[] members=[field:BILINEAR[], field:NEAREST[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterPlacementException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.RasterPlacementException$Reason, java.lang.Throwable] throws=[] annotations=[] parameterAnnotations=[[], []], constructor:[io.github.mundanej.map.api.RasterPlacementException$Reason] throws=[] annotations=[] parameterAnnotations=[[]], method:reason[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterPlacementException$Reason sealed=false permits=[] record=[] enum=[SINGULAR, INVERSE_NON_FINITE, CORNER_NON_FINITE, ENVELOPE_NON_FINITE, ENVELOPE_NON_POSITIVE] annotations=[] members=[field:CORNER_NON_FINITE[], field:ENVELOPE_NON_FINITE[], field:ENVELOPE_NON_POSITIVE[], field:INVERSE_NON_FINITE[], field:SINGULAR[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterPortrayal sealed=false permits=[] record=[bands:java.util.List<java.lang.Integer>[], colorMap:java.util.List<io.github.mundanej.map.api.RasterPortrayal$ColorStop>[], colorMapMode:io.github.mundanej.map.api.RasterPortrayal$ColorMapMode[], fallback:io.github.mundanej.map.api.Rgba[], interpolation:io.github.mundanej.map.api.RasterInterpolation[], opacity:double[]] enum=[] annotations=[] members=[constructor:[java.util.List<java.lang.Integer>, java.util.List<io.github.mundanej.map.api.RasterPortrayal$ColorStop>, io.github.mundanej.map.api.RasterPortrayal$ColorMapMode, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.RasterInterpolation, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:bands[] throws=[] annotations=[] parameterAnnotations=[], method:colorMapMode[] throws=[] annotations=[] parameterAnnotations=[], method:colorMap[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fallback[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:interpolation[] throws=[] annotations=[] parameterAnnotations=[], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterPortrayal$ColorMapMode sealed=false permits=[] record=[] enum=[INTERVALS, VALUES, RAMP] annotations=[] members=[field:INTERVALS[], field:RAMP[], field:VALUES[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterPortrayal$ColorStop sealed=false permits=[] record=[value:double[], color:io.github.mundanej.map.api.Rgba[], label:java.util.Optional<java.lang.String>[]] enum=[] annotations=[] members=[constructor:[double, io.github.mundanej.map.api.Rgba, java.util.Optional<java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:color[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:label[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterRead sealed=false permits=[] record=[sourceWindow:io.github.mundanej.map.api.RasterWindow[], pixels:io.github.mundanej.map.api.RgbaPixelBuffer[], diagnostics:io.github.mundanej.map.api.DiagnosticReport[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.RasterWindow, io.github.mundanej.map.api.RgbaPixelBuffer, io.github.mundanej.map.api.DiagnosticReport] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:diagnostics[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:pixels[] throws=[] annotations=[] parameterAnnotations=[], method:sourceWindow[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterRequest sealed=false permits=[] record=[sourceWindow:io.github.mundanej.map.api.RasterWindow[], outputWidth:int[], outputHeight:int[], interpolation:io.github.mundanej.map.api.RasterInterpolation[], tighterLimits:java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.RasterWindow, int, int, io.github.mundanej.map.api.RasterInterpolation, java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], constructor:[io.github.mundanej.map.api.RasterWindow, int, int, java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:interpolation[] throws=[] annotations=[] parameterAnnotations=[], method:outputHeight[] throws=[] annotations=[] parameterAnnotations=[], method:outputWidth[] throws=[] annotations=[] parameterAnnotations=[], method:sourceWindow[] throws=[] annotations=[] parameterAnnotations=[], method:tighterLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterRequestLimits sealed=false permits=[] record=[sourceWindowPixels:long[], outputDimension:int[], outputPixels:long[], decodedIntermediateBytes:long[], ownedPayloadBytes:long[], retainedWarnings:int[]] enum=[] annotations=[] members=[constructor:[long, int, long, long, long, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], field:LEVEL_1[], method:decodedIntermediateBytes[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:outputDimension[] throws=[] annotations=[] parameterAnnotations=[], method:outputPixels[] throws=[] annotations=[] parameterAnnotations=[], method:ownedPayloadBytes[] throws=[] annotations=[] parameterAnnotations=[], method:retainedWarnings[] throws=[] annotations=[] parameterAnnotations=[], method:sourceWindowPixels[] throws=[] annotations=[] parameterAnnotations=[], method:tightens[io.github.mundanej.map.api.RasterRequestLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterSource sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:close[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:limits[] throws=[] annotations=[] parameterAnnotations=[], method:metadata[] throws=[] annotations=[] parameterAnnotations=[], method:openingDiagnostics[] throws=[] annotations=[] parameterAnnotations=[], method:read[io.github.mundanej.map.api.RasterRequest, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.RasterSourceLimits sealed=false permits=[] record=[requestLimits:io.github.mundanej.map.api.RasterRequestLimits[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.RasterRequestLimits] throws=[] annotations=[] parameterAnnotations=[[]], field:LEVEL_1[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:requestLimits[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RasterSourceMetadata sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.SourceIdentity, int, int, java.util.Optional<io.github.mundanej.map.api.Envelope>, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:crs[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:gridPlacement[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:height[] throws=[] annotations=[] parameterAnnotations=[], method:identity[] throws=[] annotations=[] parameterAnnotations=[], method:mapBounds[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[], method:withPlacement[io.github.mundanej.map.api.SourceIdentity, int, int, io.github.mundanej.map.api.RasterGridPlacement, java.util.Optional<io.github.mundanej.map.api.CrsMetadata>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []]]
SHAPE io.github.mundanej.map.api.RasterWindow sealed=false permits=[] record=[column:int[], row:int[], width:int[], height:int[]] enum=[] annotations=[] members=[constructor:[int, int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:column[] throws=[] annotations=[] parameterAnnotations=[], method:endColumn[] throws=[] annotations=[] parameterAnnotations=[], method:endRow[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:height[] throws=[] annotations=[] parameterAnnotations=[], method:row[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RendererCapability sealed=false permits=[] record=[support:io.github.mundanej.map.api.RendererCapability$Support[], approximationPolicy:java.util.Optional<java.lang.String>[], diagnosticCode:java.lang.String[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.RendererCapability$Support, java.util.Optional<java.lang.String>, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:accept[] throws=[] annotations=[] parameterAnnotations=[], method:approximate[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:approximationPolicy[] throws=[] annotations=[] parameterAnnotations=[], method:diagnosticCode[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:reject[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:support[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RendererCapability$Support sealed=false permits=[] record=[] enum=[ACCEPT, APPROXIMATE, REJECT] annotations=[] members=[field:ACCEPT[], field:APPROXIMATE[], field:REJECT[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ReplaceFeature sealed=false permits=[] record=[featureId:java.lang.String[], replacement:io.github.mundanej.map.api.FeatureRecord[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.FeatureRecord] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:replacement[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ResolutionRange sealed=false permits=[] record=[minUnitsPerPixelInclusive:double[], maxUnitsPerPixelInclusive:double[]] enum=[] annotations=[] members=[constructor:[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], field:ALL[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:includes[double] throws=[] annotations=[] parameterAnnotations=[[]], method:maxUnitsPerPixelInclusive[] throws=[] annotations=[] parameterAnnotations=[], method:minUnitsPerPixelInclusive[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ResolvedFeaturePortrayal sealed=false permits=[] record=[marker:java.util.Optional<io.github.mundanej.map.api.Symbol>[], line:java.util.Optional<io.github.mundanej.map.api.Symbol>[], fill:java.util.Optional<io.github.mundanej.map.api.Symbol>[]] enum=[] annotations=[] members=[constructor:[java.util.Optional<io.github.mundanej.map.api.Symbol>, java.util.Optional<io.github.mundanej.map.api.Symbol>, java.util.Optional<io.github.mundanej.map.api.Symbol>] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:EMPTY[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fill[] throws=[] annotations=[] parameterAnnotations=[], method:forRole[io.github.mundanej.map.api.SymbolRole] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:line[] throws=[] annotations=[] parameterAnnotations=[], method:marker[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.Rgba sealed=false permits=[] record=[red:int[], green:int[], blue:int[], alpha:int[]] enum=[] annotations=[] members=[constructor:[int, int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], field:TRANSPARENT[], method:alpha[] throws=[] annotations=[] parameterAnnotations=[], method:blue[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:green[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:red[] throws=[] annotations=[] parameterAnnotations=[], method:rgb[int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RgbaPixelBuffer sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:builder[int, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:copyOf[int, int, int[]] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:height[] throws=[] annotations=[] parameterAnnotations=[], method:rgbaAt[int, int] throws=[] annotations=[] parameterAnnotations=[[], []], method:rgba[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RgbaPixelBuffer$Builder sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:build[] throws=[] annotations=[] parameterAnnotations=[], method:setRgba[int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.api.RulePortrayalPlan sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.util.List<io.github.mundanej.map.api.PortrayalRule>] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:portrayal[] throws=[] annotations=[] parameterAnnotations=[], method:requiresScaleContext[] throws=[] annotations=[] parameterAnnotations=[], method:rules[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.RuleSymbolSelector sealed=false permits=[] record=[plan:io.github.mundanej.map.api.RulePortrayalPlan[], role:io.github.mundanej.map.api.SymbolRole[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.RulePortrayalPlan, io.github.mundanej.map.api.SymbolRole] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:plan[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ScaleInterval sealed=false permits=[] record=[minimumInclusive:java.util.OptionalDouble[], maximumExclusive:java.util.OptionalDouble[]] enum=[] annotations=[] members=[constructor:[java.util.OptionalDouble, java.util.OptionalDouble] throws=[] annotations=[] parameterAnnotations=[[], []], field:ALL[], method:constrained[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:includes[double] throws=[] annotations=[] parameterAnnotations=[[]], method:maximumExclusive[] throws=[] annotations=[] parameterAnnotations=[], method:minimumInclusive[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ScreenBox sealed=false permits=[] record=[minX:double[], minY:double[], maxX:double[], maxY:double[]] enum=[] annotations=[] members=[constructor:[double, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:expanded[double] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maxX[] throws=[] annotations=[] parameterAnnotations=[], method:maxY[] throws=[] annotations=[] parameterAnnotations=[], method:minX[] throws=[] annotations=[] parameterAnnotations=[], method:minY[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:translated[double, double] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.api.SnapFeature sealed=false permits=[] record=[featureId:java.lang.String[], geometry:io.github.mundanej.map.api.Geometry[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.Geometry] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:geometry[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SnapLimits sealed=false permits=[] record=[maximumLayers:int[], maximumFeatures:int[], maximumCoordinates:long[], maximumSegments:long[]] enum=[] annotations=[] members=[constructor:[int, int, long, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], field:DEFAULT[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maximumFeatures[] throws=[] annotations=[] parameterAnnotations=[], method:maximumLayers[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSegments[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumCoordinates[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumFeatures[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumLayers[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumSegments[long] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.SnapQueryResult sealed=false permits=[] record=[status:io.github.mundanej.map.api.SnapQueryStatus[], result:java.util.Optional<io.github.mundanej.map.api.SnapResult>[], problem:java.util.Optional<io.github.mundanej.map.api.FeatureEditProblem>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.SnapQueryStatus, java.util.Optional<io.github.mundanej.map.api.SnapResult>, java.util.Optional<io.github.mundanej.map.api.FeatureEditProblem>] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:problem[] throws=[] annotations=[] parameterAnnotations=[], method:rejected[io.github.mundanej.map.api.FeatureEditProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:result[] throws=[] annotations=[] parameterAnnotations=[], method:snapped[io.github.mundanej.map.api.SnapResult] throws=[] annotations=[] parameterAnnotations=[[]], method:status[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unsnapped[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SnapQueryStatus sealed=false permits=[] record=[] enum=[SNAPPED, UNSNAPPED, REJECTED] annotations=[] members=[field:REJECTED[], field:SNAPPED[], field:UNSNAPPED[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SnapReferenceLayer sealed=false permits=[] record=[layerId:java.lang.String[], features:java.util.List<io.github.mundanej.map.api.SnapFeature>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.List<io.github.mundanej.map.api.SnapFeature>] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:features[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layerId[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SnapReferenceSet sealed=false permits=[] record=[crs:io.github.mundanej.map.api.CrsDefinition[], layers:java.util.List<io.github.mundanej.map.api.SnapReferenceLayer>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.CrsDefinition, java.util.List<io.github.mundanej.map.api.SnapReferenceLayer>] throws=[] annotations=[] parameterAnnotations=[[], []], method:crs[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layers[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SnapResult sealed=false permits=[] record=[coordinate:io.github.mundanej.map.api.Coordinate[], distancePixels:double[], targetType:io.github.mundanej.map.api.SnapTargetType[], layerId:java.lang.String[], featureId:java.lang.String[], componentIndex:int[], partIndex:int[], elementIndex:int[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Coordinate, double, io.github.mundanej.map.api.SnapTargetType, java.lang.String, java.lang.String, int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], method:componentIndex[] throws=[] annotations=[] parameterAnnotations=[], method:coordinate[] throws=[] annotations=[] parameterAnnotations=[], method:distancePixels[] throws=[] annotations=[] parameterAnnotations=[], method:elementIndex[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layerId[] throws=[] annotations=[] parameterAnnotations=[], method:partIndex[] throws=[] annotations=[] parameterAnnotations=[], method:targetType[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SnapTargetType sealed=false permits=[] record=[] enum=[VERTEX, SEGMENT] annotations=[] members=[field:SEGMENT[], field:VERTEX[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SolidFillSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:RENDERER_KEY[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fill[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:of[io.github.mundanej.map.api.Rgba, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:of[io.github.mundanej.map.api.Rgba, java.util.Optional<io.github.mundanej.map.api.Symbol>, double] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:outline[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SolidLineSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:RENDERER_KEY[], method:endMarker[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:of[io.github.mundanej.map.api.SymbolStroke, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:of[io.github.mundanej.map.api.SymbolStroke, java.util.Optional<io.github.mundanej.map.api.Symbol>, java.util.Optional<io.github.mundanej.map.api.Symbol>, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:startMarker[] throws=[] annotations=[] parameterAnnotations=[], method:stroke[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SourceDiagnostic sealed=false permits=[] record=[code:java.lang.String[], severity:io.github.mundanej.map.api.DiagnosticSeverity[], sourceId:java.lang.String[], location:java.util.Optional<io.github.mundanej.map.api.DiagnosticLocation>[], message:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.DiagnosticSeverity, java.lang.String, java.util.Optional<io.github.mundanej.map.api.DiagnosticLocation>, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:location[] throws=[] annotations=[] parameterAnnotations=[], method:message[] throws=[] annotations=[] parameterAnnotations=[], method:severity[] throws=[] annotations=[] parameterAnnotations=[], method:sourceId[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SourceException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.DiagnosticReport, io.github.mundanej.map.api.SourceDiagnostic, java.lang.Throwable] throws=[] annotations=[] parameterAnnotations=[[], [], []], constructor:[io.github.mundanej.map.api.DiagnosticReport, io.github.mundanej.map.api.SourceDiagnostic] throws=[] annotations=[] parameterAnnotations=[[], []], method:report[] throws=[] annotations=[] parameterAnnotations=[], method:terminal[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SourceIdentity sealed=false permits=[] record=[id:java.lang.String[], displayName:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], []], method:displayName[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.StringifiedTextAttribute sealed=false permits=[] record=[attribute:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:attribute[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.StructuredAttributeLimits sealed=false permits=[] record=[maxDepth:int[], maxValues:int[], maxObjectMembers:int[], maxArrayElements:int[]] enum=[] annotations=[] members=[constructor:[int, int, int, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], field:DEFAULT[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maxArrayElements[] throws=[] annotations=[] parameterAnnotations=[], method:maxDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maxObjectMembers[] throws=[] annotations=[] parameterAnnotations=[], method:maxValues[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.StructuredAttributeValue sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:LIMIT_EXCEEDED[], method:depth[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:logicalSizeBytes[] throws=[] annotations=[] parameterAnnotations=[], method:of[java.lang.Object, io.github.mundanej.map.api.StructuredAttributeLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:of[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:valueCount[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.Symbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolAnchor sealed=false permits=[] record=[] enum=[CENTER, NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST] annotations=[] members=[field:CENTER[], field:EAST[], field:NORTH[], field:NORTH_EAST[], field:NORTH_WEST[], field:SOUTH[], field:SOUTH_EAST[], field:SOUTH_WEST[], field:WEST[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:CATALOG_DUPLICATE[], field:CATALOG_MISSING[], field:HATCH_SEGMENT_LIMIT_EXCEEDED[], field:PORTRAYAL_SCALE_CONTEXT_REQUIRED[], field:PORTRAYAL_SCALE_CRS_UNSUPPORTED[], field:RENDERER_DUPLICATE[], field:RENDERER_INVALID_RESULT[], field:RENDERER_NOT_REGISTERED[], field:RENDERER_RESERVED_KEY[], field:RENDERER_VALUE_MISMATCH[], field:ROLE_MISMATCH[], field:TRANSFORM_NON_FINITE[], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolLength sealed=false permits=[] record=[value:double[], unit:io.github.mundanej.map.api.SymbolUnit[]] enum=[] annotations=[] members=[constructor:[double, io.github.mundanej.map.api.SymbolUnit] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unit[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolRendererCapabilities sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:capability[io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.SymbolRendererKey sealed=false permits=[] record=[value:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolRole sealed=false permits=[] record=[] enum=[MARKER, LINE, FILL, LEGACY_GEOMETRY] annotations=[] members=[field:FILL[], field:LEGACY_GEOMETRY[], field:LINE[], field:MARKER[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolRotationMode sealed=false permits=[] record=[] enum=[SCREEN_RELATIVE, MAP_RELATIVE] annotations=[] members=[field:MAP_RELATIVE[], field:SCREEN_RELATIVE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolSelector sealed=true permits=[io.github.mundanej.map.api.CategoricalSymbolSelector, io.github.mundanej.map.api.FilteredSymbolSelector, io.github.mundanej.map.api.FixedSymbolSelector, io.github.mundanej.map.api.GraduatedSymbolSelector, io.github.mundanej.map.api.InterpolatedSymbolSelector, io.github.mundanej.map.api.RuleSymbolSelector] record=[] enum=[] annotations=[] members=[method:role[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolSize sealed=false permits=[] record=[width:double[], height:double[], unit:io.github.mundanej.map.api.SymbolUnit[]] enum=[] annotations=[] members=[constructor:[double, double, io.github.mundanej.map.api.SymbolUnit] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:height[] throws=[] annotations=[] parameterAnnotations=[], method:square[double, io.github.mundanej.map.api.SymbolUnit] throws=[] annotations=[] parameterAnnotations=[[], []], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unit[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolStroke sealed=false permits=[] record=[color:io.github.mundanej.map.api.Rgba[], width:io.github.mundanej.map.api.SymbolLength[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.SymbolLength] throws=[] annotations=[] parameterAnnotations=[[], []], method:color[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:width[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.SymbolUnit sealed=false permits=[] record=[] enum=[SCREEN_PIXEL, MAP_UNIT] annotations=[] members=[field:MAP_UNIT[], field:SCREEN_PIXEL[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.TextAttribute sealed=false permits=[] record=[attribute:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:attribute[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.TextPortrayal sealed=false permits=[] record=[label:io.github.mundanej.map.api.PortrayalExpression[], fontFamilies:java.util.List<java.lang.String>[], weight:int[], size:io.github.mundanej.map.api.SymbolLength[], color:io.github.mundanej.map.api.Rgba[], placement:io.github.mundanej.map.api.TextPortrayal$Placement[], halo:java.util.Optional<io.github.mundanej.map.api.TextPortrayal$Halo>[], opacity:double[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.PortrayalExpression, java.util.List<java.lang.String>, int, io.github.mundanej.map.api.SymbolLength, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.TextPortrayal$Placement, java.util.Optional<io.github.mundanej.map.api.TextPortrayal$Halo>, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], method:color[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fontFamilies[] throws=[] annotations=[] parameterAnnotations=[], method:halo[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:label[] throws=[] annotations=[] parameterAnnotations=[], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:placement[] throws=[] annotations=[] parameterAnnotations=[], method:size[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:weight[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.TextPortrayal$Halo sealed=false permits=[] record=[color:io.github.mundanej.map.api.Rgba[], radius:io.github.mundanej.map.api.SymbolLength[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.SymbolLength] throws=[] annotations=[] parameterAnnotations=[[], []], method:color[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:radius[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.TextPortrayal$Mode sealed=false permits=[] record=[] enum=[POINT, LINE] annotations=[] members=[field:LINE[], field:POINT[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.TextPortrayal$Placement sealed=false permits=[] record=[mode:io.github.mundanej.map.api.TextPortrayal$Mode[], anchorX:double[], anchorY:double[], displacementX:double[], displacementY:double[], rotationDegrees:double[], repeatGap:double[], maximumAngleDelta:double[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.TextPortrayal$Mode, double, double, double, double, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], method:anchorX[] throws=[] annotations=[] parameterAnnotations=[], method:anchorY[] throws=[] annotations=[] parameterAnnotations=[], method:displacementX[] throws=[] annotations=[] parameterAnnotations=[], method:displacementY[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumAngleDelta[] throws=[] annotations=[] parameterAnnotations=[], method:mode[] throws=[] annotations=[] parameterAnnotations=[], method:repeatGap[] throws=[] annotations=[] parameterAnnotations=[], method:rotationDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ThematicValue sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:date[java.time.LocalDate] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fromAttribute[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:kind[] throws=[] annotations=[] parameterAnnotations=[], method:logical[boolean] throws=[] annotations=[] parameterAnnotations=[[]], method:nullValue[] throws=[] annotations=[] parameterAnnotations=[], method:numeric[double] throws=[] annotations=[] parameterAnnotations=[[]], method:numeric[java.math.BigDecimal] throws=[] annotations=[] parameterAnnotations=[[]], method:numeric[long] throws=[] annotations=[] parameterAnnotations=[[]], method:text[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.ThematicValue$Kind sealed=false permits=[] record=[] enum=[TEXT, LOGICAL, NUMERIC, DATE, NULL] annotations=[] members=[field:DATE[], field:LOGICAL[], field:NULL[], field:NUMERIC[], field:TEXT[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.VectorExportSnapshot sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:background[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:heightPixels[] throws=[] annotations=[] parameterAnnotations=[], method:labels[] throws=[] annotations=[] parameterAnnotations=[], method:layerCount[] throws=[] annotations=[] parameterAnnotations=[], method:of[int, int, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame, int, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Primitive>, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Label>, io.github.mundanej.map.api.VectorExportSnapshotLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], []], method:of[int, int, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame, int, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Primitive>, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Label>, io.github.mundanej.map.api.VectorExportSnapshotLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], method:of[int, int, io.github.mundanej.map.api.Rgba, io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame, int, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Primitive>, java.util.List<io.github.mundanej.map.api.VectorExportSnapshot$Label>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], []], method:primitives[] throws=[] annotations=[] parameterAnnotations=[], method:viewFrame[] throws=[] annotations=[] parameterAnnotations=[], method:widthPixels[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.VectorExportSnapshot$Label sealed=false permits=[] record=[text:java.lang.String[], style:io.github.mundanej.map.api.LabelTextStyle[], baselineX:double[], baselineY:double[], measuredAdvance:double[], ordinaryPaintOrdinal:int[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.LabelTextStyle, double, double, double, int] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:baselineX[] throws=[] annotations=[] parameterAnnotations=[], method:baselineY[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:measuredAdvance[] throws=[] annotations=[] parameterAnnotations=[], method:ordinaryPaintOrdinal[] throws=[] annotations=[] parameterAnnotations=[], method:style[] throws=[] annotations=[] parameterAnnotations=[], method:text[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.VectorExportSnapshot$Primitive sealed=false permits=[] record=[layerIndex:int[], featureIndex:int[], screenGeometry:io.github.mundanej.map.api.Geometry[], symbol:io.github.mundanej.map.api.Symbol[]] enum=[] annotations=[] members=[constructor:[int, int, io.github.mundanej.map.api.Geometry, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:featureIndex[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layerIndex[] throws=[] annotations=[] parameterAnnotations=[], method:screenGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:symbol[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.VectorExportSnapshot$ViewFrame sealed=false permits=[] record=[screenPixelsPerMapUnit:double[], mapXAxisScreenBearingDegrees:double[], mapOriginScreen:io.github.mundanej.map.api.Coordinate[]] enum=[] annotations=[] members=[constructor:[double, double, io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:mapOriginScreen[] throws=[] annotations=[] parameterAnnotations=[], method:mapXAxisScreenBearingDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:screenPixelsPerMapUnit[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.VectorExportSnapshotException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.VectorExportSnapshotProblem, java.lang.Throwable] throws=[] annotations=[] parameterAnnotations=[[], [], []], constructor:[java.lang.String, io.github.mundanej.map.api.VectorExportSnapshotProblem] throws=[] annotations=[] parameterAnnotations=[[], []], method:problem[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.VectorExportSnapshotLimits sealed=false permits=[] record=[maximumPageAxis:int[], maximumLayers:int[], maximumFeatures:int[], maximumCoordinates:int[], maximumCompositeDepth:int[], maximumSymbolNodes:int[], maximumLabels:int[], maximumLabelCodePoints:int[], maximumOwnedBytes:long[]] enum=[] annotations=[] members=[constructor:[int, int, int, int, int, int, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], [], []], field:COMPOSITE_DEPTH_HARD_MAXIMUM[], field:COORDINATES_HARD_MAXIMUM[], field:FEATURES_HARD_MAXIMUM[], field:LABELS_HARD_MAXIMUM[], field:LABEL_CODE_POINTS_HARD_MAXIMUM[], field:LAYERS_HARD_MAXIMUM[], field:OWNED_BYTES_HARD_MAXIMUM[], field:PAGE_AXIS_HARD_MAXIMUM[], field:SYMBOL_NODES_HARD_MAXIMUM[], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCompositeDepth[] throws=[] annotations=[] parameterAnnotations=[], method:maximumCoordinates[] throws=[] annotations=[] parameterAnnotations=[], method:maximumFeatures[] throws=[] annotations=[] parameterAnnotations=[], method:maximumLabelCodePoints[] throws=[] annotations=[] parameterAnnotations=[], method:maximumLabels[] throws=[] annotations=[] parameterAnnotations=[], method:maximumLayers[] throws=[] annotations=[] parameterAnnotations=[], method:maximumOwnedBytes[] throws=[] annotations=[] parameterAnnotations=[], method:maximumPageAxis[] throws=[] annotations=[] parameterAnnotations=[], method:maximumSymbolNodes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withMaximumCompositeDepth[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumCoordinates[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumFeatures[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumLabelCodePoints[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumLabels[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumLayers[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumOwnedBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumPageAxis[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withMaximumSymbolNodes[int] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.api.VectorExportSnapshotProblem sealed=false permits=[] record=[code:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.VectorMarkerSymbol sealed=false permits=[] record=[] enum=[] annotations=[] members=[field:RENDERER_KEY[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fill[] throws=[] annotations=[] parameterAnnotations=[], method:filledScreen[io.github.mundanej.map.api.VectorPath, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.Rgba, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:of[io.github.mundanej.map.api.VectorPath, io.github.mundanej.map.api.Envelope, io.github.mundanej.map.api.Rgba, java.util.Optional<io.github.mundanej.map.api.SymbolStroke>, io.github.mundanej.map.api.MarkerPlacement, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:path[] throws=[] annotations=[] parameterAnnotations=[], method:placement[] throws=[] annotations=[] parameterAnnotations=[], method:rendererKey[] throws=[] annotations=[] parameterAnnotations=[], method:stroke[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:viewBox[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.VectorPath sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:builder[] throws=[] annotations=[] parameterAnnotations=[], method:commandAt[int] throws=[] annotations=[] parameterAnnotations=[[]], method:commandCount[] throws=[] annotations=[] parameterAnnotations=[], method:coordinateEnvelope[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:of[io.github.mundanej.map.api.VectorPathCommand[], double[]] throws=[] annotations=[] parameterAnnotations=[[], []], method:ordinateAt[int] throws=[] annotations=[] parameterAnnotations=[[]], method:ordinateCount[] throws=[] annotations=[] parameterAnnotations=[], method:toCommandArray[] throws=[] annotations=[] parameterAnnotations=[], method:toOrdinateArray[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.api.VectorPath$Builder sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:build[] throws=[] annotations=[] parameterAnnotations=[], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:cubicTo[double, double, double, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:lineTo[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:moveTo[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:quadraticTo[double, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], []]]
SHAPE io.github.mundanej.map.api.VectorPathCommand sealed=false permits=[] record=[] enum=[MOVE_TO, LINE_TO, QUADRATIC_TO, CUBIC_TO, CLOSE] annotations=[] members=[field:CLOSE[], field:CUBIC_TO[], field:LINE_TO[], field:MOVE_TO[], field:QUADRATIC_TO[], method:arity[] throws=[] annotations=[] parameterAnnotations=[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
