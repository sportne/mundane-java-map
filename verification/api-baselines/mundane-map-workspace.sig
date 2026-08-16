public final class io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer extends java.lang.Record implements io.github.mundanej.map.workspace.OpenedWorkspaceLayer {
  public io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer(io.github.mundanej.map.workspace.WorkspaceFeatureLayer, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol);
    descriptor: (Lio/github/mundanej/map/workspace/WorkspaceFeatureLayer;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/Symbol;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.workspace.WorkspaceFeatureLayer definition();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceFeatureLayer;
  public io.github.mundanej.map.api.FeatureSource source();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSource;
  public io.github.mundanej.map.api.Symbol marker();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
  public io.github.mundanej.map.api.Symbol line();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
  public io.github.mundanej.map.api.Symbol fill();
    descriptor: ()Lio/github/mundanej/map/api/Symbol;
  public io.github.mundanej.map.workspace.WorkspaceLayerDefinition definition();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceLayerDefinition;
}
public interface io.github.mundanej.map.workspace.OpenedWorkspaceLayer {
  public abstract io.github.mundanej.map.workspace.WorkspaceLayerDefinition definition();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceLayerDefinition;
}
public final class io.github.mundanej.map.workspace.OpenedWorkspaceRasterLayer extends java.lang.Record implements io.github.mundanej.map.workspace.OpenedWorkspaceLayer {
  public io.github.mundanej.map.workspace.OpenedWorkspaceRasterLayer(io.github.mundanej.map.workspace.WorkspaceRasterLayer, io.github.mundanej.map.api.RasterSource);
    descriptor: (Lio/github/mundanej/map/workspace/WorkspaceRasterLayer;Lio/github/mundanej/map/api/RasterSource;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.workspace.WorkspaceRasterLayer definition();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceRasterLayer;
  public io.github.mundanej.map.api.RasterSource source();
    descriptor: ()Lio/github/mundanej/map/api/RasterSource;
  public io.github.mundanej.map.workspace.WorkspaceLayerDefinition definition();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceLayerDefinition;
}
public final class io.github.mundanej.map.workspace.WorkspaceDocument extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceDocument(io.github.mundanej.map.workspace.WorkspaceViewState, java.util.List<io.github.mundanej.map.workspace.WorkspaceLayerDefinition>);
    descriptor: (Lio/github/mundanej/map/workspace/WorkspaceViewState;Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.workspace.WorkspaceViewState view();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceViewState;
  public java.util.List<io.github.mundanej.map.workspace.WorkspaceLayerDefinition> layers();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.workspace.WorkspaceException extends java.lang.RuntimeException {
  public io.github.mundanej.map.workspace.WorkspaceException(io.github.mundanej.map.workspace.WorkspaceProblem);
    descriptor: (Lio/github/mundanej/map/workspace/WorkspaceProblem;)V
  public io.github.mundanej.map.workspace.WorkspaceProblem problem();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceProblem;
  public java.util.Optional<io.github.mundanej.map.api.DiagnosticReport> sourceReport();
    descriptor: ()Ljava/util/Optional;
}
public final class io.github.mundanej.map.workspace.WorkspaceFeatureLayer extends java.lang.Record implements io.github.mundanej.map.workspace.WorkspaceLayerDefinition {
  public io.github.mundanej.map.workspace.WorkspaceFeatureLayer(java.lang.String, java.lang.String, io.github.mundanej.map.workspace.WorkspaceSourceReference, io.github.mundanej.map.workspace.WorkspaceSymbolReferences);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/workspace/WorkspaceSourceReference;Lio/github/mundanej/map/workspace/WorkspaceSymbolReferences;)V
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
  public io.github.mundanej.map.workspace.WorkspaceSourceReference source();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSourceReference;
  public io.github.mundanej.map.workspace.WorkspaceSymbolReferences symbols();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSymbolReferences;
}
public interface io.github.mundanej.map.workspace.WorkspaceFeatureSourceOpener {
  public abstract io.github.mundanej.map.api.FeatureSource open(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/FeatureSource;
}
public final class io.github.mundanej.map.workspace.WorkspaceFile extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceFile(io.github.mundanej.map.workspace.WorkspaceDocument, java.nio.file.Path);
    descriptor: (Lio/github/mundanej/map/workspace/WorkspaceDocument;Ljava/nio/file/Path;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.workspace.WorkspaceDocument document();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceDocument;
  public java.nio.file.Path baseDirectory();
    descriptor: ()Ljava/nio/file/Path;
}
public final class io.github.mundanej.map.workspace.WorkspaceFiles {
  public static io.github.mundanej.map.workspace.WorkspaceFile read(java.nio.file.Path, io.github.mundanej.map.workspace.WorkspaceLimits);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/workspace/WorkspaceLimits;)Lio/github/mundanej/map/workspace/WorkspaceFile;
  public static void write(java.nio.file.Path, io.github.mundanej.map.workspace.WorkspaceDocument, io.github.mundanej.map.workspace.WorkspaceLimits);
    descriptor: (Ljava/nio/file/Path;Lio/github/mundanej/map/workspace/WorkspaceDocument;Lio/github/mundanej/map/workspace/WorkspaceLimits;)V
}
public interface io.github.mundanej.map.workspace.WorkspaceLayerDefinition {
  public abstract java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public abstract java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public abstract io.github.mundanej.map.workspace.WorkspaceSourceReference source();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSourceReference;
}
public final class io.github.mundanej.map.workspace.WorkspaceLimits extends java.lang.Record {
  public static final io.github.mundanej.map.workspace.WorkspaceLimits DEFAULT;
    descriptor: Lio/github/mundanej/map/workspace/WorkspaceLimits;
  public io.github.mundanej.map.workspace.WorkspaceLimits(long, long, int, int, int, int, int, long);
    descriptor: (JJIIIIIJ)V
  public io.github.mundanej.map.workspace.WorkspaceLimits withInputOutputBytes(long);
    descriptor: (J)Lio/github/mundanej/map/workspace/WorkspaceLimits;
  public io.github.mundanej.map.workspace.WorkspaceLimits withOperationBytes(long);
    descriptor: (J)Lio/github/mundanej/map/workspace/WorkspaceLimits;
  public io.github.mundanej.map.workspace.WorkspaceLimits withDepth(int);
    descriptor: (I)Lio/github/mundanej/map/workspace/WorkspaceLimits;
  public io.github.mundanej.map.workspace.WorkspaceLimits withElements(int);
    descriptor: (I)Lio/github/mundanej/map/workspace/WorkspaceLimits;
  public io.github.mundanej.map.workspace.WorkspaceLimits withAttributes(int);
    descriptor: (I)Lio/github/mundanej/map/workspace/WorkspaceLimits;
  public io.github.mundanej.map.workspace.WorkspaceLimits withLayers(int);
    descriptor: (I)Lio/github/mundanej/map/workspace/WorkspaceLimits;
  public io.github.mundanej.map.workspace.WorkspaceLimits withValueChars(int);
    descriptor: (I)Lio/github/mundanej/map/workspace/WorkspaceLimits;
  public io.github.mundanej.map.workspace.WorkspaceLimits withAggregateChars(long);
    descriptor: (J)Lio/github/mundanej/map/workspace/WorkspaceLimits;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public long inputOutputBytes();
    descriptor: ()J
  public long operationBytes();
    descriptor: ()J
  public int depth();
    descriptor: ()I
  public int elements();
    descriptor: ()I
  public int attributes();
    descriptor: ()I
  public int layers();
    descriptor: ()I
  public int valueChars();
    descriptor: ()I
  public long aggregateChars();
    descriptor: ()J
}
public final class io.github.mundanej.map.workspace.WorkspaceLocalPathBranch extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceLocalPathBranch(java.lang.String, java.util.List<java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String primarySuffix();
    descriptor: ()Ljava/lang/String;
  public java.util.List<java.lang.String> replacementSuffixes();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.workspace.WorkspaceLocalPathProfile extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceLocalPathProfile(java.util.List<io.github.mundanej.map.workspace.WorkspaceLocalPathBranch>);
    descriptor: (Ljava/util/List;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.util.List<io.github.mundanej.map.workspace.WorkspaceLocalPathBranch> branches();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.workspace.WorkspaceOpenContext extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceOpenContext(io.github.mundanej.map.core.CrsRegistry, io.github.mundanej.map.workspace.WorkspaceSourceRegistry, io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry);
    descriptor: (Lio/github/mundanej/map/core/CrsRegistry;Lio/github/mundanej/map/workspace/WorkspaceSourceRegistry;Lio/github/mundanej/map/workspace/WorkspaceSymbolCatalogRegistry;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.core.CrsRegistry crsRegistry();
    descriptor: ()Lio/github/mundanej/map/core/CrsRegistry;
  public io.github.mundanej.map.workspace.WorkspaceSourceRegistry sources();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSourceRegistry;
  public io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry catalogs();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSymbolCatalogRegistry;
}
public final class io.github.mundanej.map.workspace.WorkspaceOpener {
  public static io.github.mundanej.map.workspace.WorkspaceSession open(io.github.mundanej.map.workspace.WorkspaceFile, io.github.mundanej.map.workspace.WorkspaceOpenContext, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/workspace/WorkspaceFile;Lio/github/mundanej/map/workspace/WorkspaceOpenContext;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/workspace/WorkspaceSession;
}
public final class io.github.mundanej.map.workspace.WorkspaceProblem extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceProblem(java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/util/Map;)V
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.workspace.WorkspaceRasterLayer extends java.lang.Record implements io.github.mundanej.map.workspace.WorkspaceLayerDefinition {
  public io.github.mundanej.map.workspace.WorkspaceRasterLayer(java.lang.String, java.lang.String, io.github.mundanej.map.workspace.WorkspaceSourceReference, io.github.mundanej.map.api.RasterInterpolation, double);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/workspace/WorkspaceSourceReference;Lio/github/mundanej/map/api/RasterInterpolation;D)V
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
  public io.github.mundanej.map.workspace.WorkspaceSourceReference source();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSourceReference;
  public io.github.mundanej.map.api.RasterInterpolation interpolation();
    descriptor: ()Lio/github/mundanej/map/api/RasterInterpolation;
  public double opacity();
    descriptor: ()D
}
public interface io.github.mundanej.map.workspace.WorkspaceRasterSourceOpener {
  public abstract io.github.mundanej.map.api.RasterSource open(io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/SourceIdentity;Ljava/nio/file/Path;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/RasterSource;
}
public final class io.github.mundanej.map.workspace.WorkspaceRelativePath extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceRelativePath(java.lang.String);
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
public final class io.github.mundanej.map.workspace.WorkspaceSession implements java.lang.AutoCloseable {
  public io.github.mundanej.map.workspace.WorkspaceDocument document();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceDocument;
  public io.github.mundanej.map.api.CrsDefinition mapCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public io.github.mundanej.map.api.CrsDefinition displayCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public java.util.List<io.github.mundanej.map.workspace.OpenedWorkspaceLayer> layers();
    descriptor: ()Ljava/util/List;
  public boolean isClosed();
    descriptor: ()Z
  public synchronized void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.workspace.WorkspaceSourceKind extends java.lang.Enum<io.github.mundanej.map.workspace.WorkspaceSourceKind> {
  public static final io.github.mundanej.map.workspace.WorkspaceSourceKind FEATURE;
    descriptor: Lio/github/mundanej/map/workspace/WorkspaceSourceKind;
  public static final io.github.mundanej.map.workspace.WorkspaceSourceKind RASTER;
    descriptor: Lio/github/mundanej/map/workspace/WorkspaceSourceKind;
  public static io.github.mundanej.map.workspace.WorkspaceSourceKind[] values();
    descriptor: ()[Lio/github/mundanej/map/workspace/WorkspaceSourceKind;
  public static io.github.mundanej.map.workspace.WorkspaceSourceKind valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/workspace/WorkspaceSourceKind;
}
public final class io.github.mundanej.map.workspace.WorkspaceSourceReference extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceSourceReference(java.lang.String, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.workspace.WorkspaceRelativePath);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/SourceIdentity;Lio/github/mundanej/map/workspace/WorkspaceRelativePath;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String openerId();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.SourceIdentity identity();
    descriptor: ()Lio/github/mundanej/map/api/SourceIdentity;
  public io.github.mundanej.map.workspace.WorkspaceRelativePath path();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceRelativePath;
}
public final class io.github.mundanej.map.workspace.WorkspaceSourceRegistry {
  public static io.github.mundanej.map.workspace.WorkspaceSourceRegistry$Builder builder();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSourceRegistry$Builder;
}
public final class io.github.mundanej.map.workspace.WorkspaceSourceRegistry$Builder {
  public io.github.mundanej.map.workspace.WorkspaceSourceRegistry$Builder registerFeature(java.lang.String, io.github.mundanej.map.workspace.WorkspaceLocalPathProfile, io.github.mundanej.map.workspace.WorkspaceFeatureSourceOpener);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/workspace/WorkspaceLocalPathProfile;Lio/github/mundanej/map/workspace/WorkspaceFeatureSourceOpener;)Lio/github/mundanej/map/workspace/WorkspaceSourceRegistry$Builder;
  public io.github.mundanej.map.workspace.WorkspaceSourceRegistry$Builder registerRaster(java.lang.String, io.github.mundanej.map.workspace.WorkspaceLocalPathProfile, io.github.mundanej.map.workspace.WorkspaceRasterSourceOpener);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/workspace/WorkspaceLocalPathProfile;Lio/github/mundanej/map/workspace/WorkspaceRasterSourceOpener;)Lio/github/mundanej/map/workspace/WorkspaceSourceRegistry$Builder;
  public io.github.mundanej.map.workspace.WorkspaceSourceRegistry build();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSourceRegistry;
}
public final class io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry {
  public static io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry$Builder builder();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSymbolCatalogRegistry$Builder;
}
public final class io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry$Builder {
  public io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry$Builder register(java.lang.String, io.github.mundanej.map.api.NamedSymbolCatalog);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/NamedSymbolCatalog;)Lio/github/mundanej/map/workspace/WorkspaceSymbolCatalogRegistry$Builder;
  public io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry build();
    descriptor: ()Lio/github/mundanej/map/workspace/WorkspaceSymbolCatalogRegistry;
}
public final class io.github.mundanej.map.workspace.WorkspaceSymbolReferences extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceSymbolReferences(java.lang.String, java.lang.String, java.lang.String, java.lang.String);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String catalogId();
    descriptor: ()Ljava/lang/String;
  public java.lang.String markerName();
    descriptor: ()Ljava/lang/String;
  public java.lang.String lineName();
    descriptor: ()Ljava/lang/String;
  public java.lang.String fillName();
    descriptor: ()Ljava/lang/String;
}
public final class io.github.mundanej.map.workspace.WorkspaceViewState extends java.lang.Record {
  public io.github.mundanej.map.workspace.WorkspaceViewState(java.lang.String, java.lang.String, double, double, double);
    descriptor: (Ljava/lang/String;Ljava/lang/String;DDD)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public java.lang.String mapCrsKey();
    descriptor: ()Ljava/lang/String;
  public java.lang.String displayCrsKey();
    descriptor: ()Ljava/lang/String;
  public double centerX();
    descriptor: ()D
  public double centerY();
    descriptor: ()D
  public double unitsPerPixel();
    descriptor: ()D
}
SHAPE io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer sealed=false permits=[] record=[definition:io.github.mundanej.map.workspace.WorkspaceFeatureLayer[], source:io.github.mundanej.map.api.FeatureSource[], marker:io.github.mundanej.map.api.Symbol[], line:io.github.mundanej.map.api.Symbol[], fill:io.github.mundanej.map.api.Symbol[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.workspace.WorkspaceFeatureLayer, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:definition[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fill[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:line[] throws=[] annotations=[] parameterAnnotations=[], method:marker[] throws=[] annotations=[] parameterAnnotations=[], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.OpenedWorkspaceLayer sealed=true permits=[io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer, io.github.mundanej.map.workspace.OpenedWorkspaceRasterLayer] record=[] enum=[] annotations=[] members=[method:definition[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.OpenedWorkspaceRasterLayer sealed=false permits=[] record=[definition:io.github.mundanej.map.workspace.WorkspaceRasterLayer[], source:io.github.mundanej.map.api.RasterSource[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.workspace.WorkspaceRasterLayer, io.github.mundanej.map.api.RasterSource] throws=[] annotations=[] parameterAnnotations=[[], []], method:definition[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceDocument sealed=false permits=[] record=[view:io.github.mundanej.map.workspace.WorkspaceViewState[], layers:java.util.List<io.github.mundanej.map.workspace.WorkspaceLayerDefinition>[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.workspace.WorkspaceViewState, java.util.List<io.github.mundanej.map.workspace.WorkspaceLayerDefinition>] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:layers[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:view[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.workspace.WorkspaceProblem] throws=[] annotations=[] parameterAnnotations=[[]], method:problem[] throws=[] annotations=[] parameterAnnotations=[], method:sourceReport[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceFeatureLayer sealed=false permits=[] record=[id:java.lang.String[], name:java.lang.String[], source:io.github.mundanej.map.workspace.WorkspaceSourceReference[], symbols:io.github.mundanej.map.workspace.WorkspaceSymbolReferences[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, io.github.mundanej.map.workspace.WorkspaceSourceReference, io.github.mundanej.map.workspace.WorkspaceSymbolReferences] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:symbols[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceFeatureSourceOpener sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:open[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.workspace.WorkspaceFile sealed=false permits=[] record=[document:io.github.mundanej.map.workspace.WorkspaceDocument[], baseDirectory:java.nio.file.Path[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.workspace.WorkspaceDocument, java.nio.file.Path] throws=[] annotations=[] parameterAnnotations=[[], []], method:baseDirectory[] throws=[] annotations=[] parameterAnnotations=[], method:document[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceFiles sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:read[java.nio.file.Path, io.github.mundanej.map.workspace.WorkspaceLimits] throws=[] annotations=[] parameterAnnotations=[[], []], method:write[java.nio.file.Path, io.github.mundanej.map.workspace.WorkspaceDocument, io.github.mundanej.map.workspace.WorkspaceLimits] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.workspace.WorkspaceLayerDefinition sealed=true permits=[io.github.mundanej.map.workspace.WorkspaceFeatureLayer, io.github.mundanej.map.workspace.WorkspaceRasterLayer] record=[] enum=[] annotations=[] members=[method:id[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:source[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceLimits sealed=false permits=[] record=[inputOutputBytes:long[], operationBytes:long[], depth:int[], elements:int[], attributes:int[], layers:int[], valueChars:int[], aggregateChars:long[]] enum=[] annotations=[] members=[constructor:[long, long, int, int, int, int, int, long] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], field:DEFAULT[], method:aggregateChars[] throws=[] annotations=[] parameterAnnotations=[], method:attributes[] throws=[] annotations=[] parameterAnnotations=[], method:depth[] throws=[] annotations=[] parameterAnnotations=[], method:elements[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:inputOutputBytes[] throws=[] annotations=[] parameterAnnotations=[], method:layers[] throws=[] annotations=[] parameterAnnotations=[], method:operationBytes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:valueChars[] throws=[] annotations=[] parameterAnnotations=[], method:withAggregateChars[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withAttributes[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withDepth[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withElements[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withInputOutputBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withLayers[int] throws=[] annotations=[] parameterAnnotations=[[]], method:withOperationBytes[long] throws=[] annotations=[] parameterAnnotations=[[]], method:withValueChars[int] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.workspace.WorkspaceLocalPathBranch sealed=false permits=[] record=[primarySuffix:java.lang.String[], replacementSuffixes:java.util.List<java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.List<java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:primarySuffix[] throws=[] annotations=[] parameterAnnotations=[], method:replacementSuffixes[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceLocalPathProfile sealed=false permits=[] record=[branches:java.util.List<io.github.mundanej.map.workspace.WorkspaceLocalPathBranch>[]] enum=[] annotations=[] members=[constructor:[java.util.List<io.github.mundanej.map.workspace.WorkspaceLocalPathBranch>] throws=[] annotations=[] parameterAnnotations=[[]], method:branches[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceOpenContext sealed=false permits=[] record=[crsRegistry:io.github.mundanej.map.core.CrsRegistry[], sources:io.github.mundanej.map.workspace.WorkspaceSourceRegistry[], catalogs:io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.core.CrsRegistry, io.github.mundanej.map.workspace.WorkspaceSourceRegistry, io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:catalogs[] throws=[] annotations=[] parameterAnnotations=[], method:crsRegistry[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:sources[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceOpener sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:open[io.github.mundanej.map.workspace.WorkspaceFile, io.github.mundanej.map.workspace.WorkspaceOpenContext, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.workspace.WorkspaceProblem sealed=false permits=[] record=[code:java.lang.String[], context:java.util.Map<java.lang.String, java.lang.String>[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], []], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceRasterLayer sealed=false permits=[] record=[id:java.lang.String[], name:java.lang.String[], source:io.github.mundanej.map.workspace.WorkspaceSourceReference[], interpolation:io.github.mundanej.map.api.RasterInterpolation[], opacity:double[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, io.github.mundanej.map.workspace.WorkspaceSourceReference, io.github.mundanej.map.api.RasterInterpolation, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:interpolation[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceRasterSourceOpener sealed=false permits=[] record=[] enum=[] annotations=[@java.lang.FunctionalInterface()] members=[method:open[io.github.mundanej.map.api.SourceIdentity, java.nio.file.Path, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.workspace.WorkspaceRelativePath sealed=false permits=[] record=[value:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:value[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceSession sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:close[] throws=[] annotations=[] parameterAnnotations=[], method:displayCrs[] throws=[] annotations=[] parameterAnnotations=[], method:document[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:layers[] throws=[] annotations=[] parameterAnnotations=[], method:mapCrs[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceSourceKind sealed=false permits=[] record=[] enum=[FEATURE, RASTER] annotations=[] members=[field:FEATURE[], field:RASTER[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceSourceReference sealed=false permits=[] record=[openerId:java.lang.String[], identity:io.github.mundanej.map.api.SourceIdentity[], path:io.github.mundanej.map.workspace.WorkspaceRelativePath[]] enum=[] annotations=[] members=[constructor:[java.lang.String, io.github.mundanej.map.api.SourceIdentity, io.github.mundanej.map.workspace.WorkspaceRelativePath] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:identity[] throws=[] annotations=[] parameterAnnotations=[], method:openerId[] throws=[] annotations=[] parameterAnnotations=[], method:path[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceSourceRegistry sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:builder[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceSourceRegistry$Builder sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:build[] throws=[] annotations=[] parameterAnnotations=[], method:registerFeature[java.lang.String, io.github.mundanej.map.workspace.WorkspaceLocalPathProfile, io.github.mundanej.map.workspace.WorkspaceFeatureSourceOpener] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:registerRaster[java.lang.String, io.github.mundanej.map.workspace.WorkspaceLocalPathProfile, io.github.mundanej.map.workspace.WorkspaceRasterSourceOpener] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
SHAPE io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:builder[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry$Builder sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:build[] throws=[] annotations=[] parameterAnnotations=[], method:register[java.lang.String, io.github.mundanej.map.api.NamedSymbolCatalog] throws=[] annotations=[] parameterAnnotations=[[], []]]
SHAPE io.github.mundanej.map.workspace.WorkspaceSymbolReferences sealed=false permits=[] record=[catalogId:java.lang.String[], markerName:java.lang.String[], lineName:java.lang.String[], fillName:java.lang.String[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.lang.String, java.lang.String] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:catalogId[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:fillName[] throws=[] annotations=[] parameterAnnotations=[], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:lineName[] throws=[] annotations=[] parameterAnnotations=[], method:markerName[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.workspace.WorkspaceViewState sealed=false permits=[] record=[mapCrsKey:java.lang.String[], displayCrsKey:java.lang.String[], centerX:double[], centerY:double[], unitsPerPixel:double[]] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:centerX[] throws=[] annotations=[] parameterAnnotations=[], method:centerY[] throws=[] annotations=[] parameterAnnotations=[], method:displayCrsKey[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:mapCrsKey[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:unitsPerPixel[] throws=[] annotations=[] parameterAnnotations=[]]
