public final class io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement extends java.lang.Enum<io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement> {
  public static final io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement BASEMAP;
    descriptor: Lio/github/mundanej/map/vaadin/BrowserFeatureLayerPlacement;
  public static final io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement OVERLAY;
    descriptor: Lio/github/mundanej/map/vaadin/BrowserFeatureLayerPlacement;
  public static io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement[] values();
    descriptor: ()[Lio/github/mundanej/map/vaadin/BrowserFeatureLayerPlacement;
  public static io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/vaadin/BrowserFeatureLayerPlacement;
}
public final class io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode extends java.lang.Enum<io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode> {
  public static final io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode NONE;
    descriptor: Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;
  public static final io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode REPEAT_X;
    descriptor: Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;
  public static io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode[] values();
    descriptor: ()[Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;
  public static io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;
}
public final class io.github.mundanej.map.vaadin.BrowserMeasurementTool implements io.github.mundanej.map.api.MapTool,io.github.mundanej.map.vaadin.BrowserBoundTool {
  public static final int DEFAULT_VERTEX_LIMIT = 10000;
    descriptor: I
  public io.github.mundanej.map.vaadin.BrowserMeasurementTool(io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.api.DistanceStrategy);
    descriptor: (Lio/github/mundanej/map/vaadin/MundaneMap;Lio/github/mundanej/map/api/DistanceStrategy;)V
  public io.github.mundanej.map.vaadin.BrowserMeasurementTool(io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.api.DistanceStrategy, int);
    descriptor: (Lio/github/mundanej/map/vaadin/MundaneMap;Lio/github/mundanej/map/api/DistanceStrategy;I)V
  public io.github.mundanej.map.api.DistanceStrategy distanceStrategy();
    descriptor: ()Lio/github/mundanej/map/api/DistanceStrategy;
  public io.github.mundanej.map.api.MeasurementState state();
    descriptor: ()Lio/github/mundanej/map/api/MeasurementState;
  public com.vaadin.flow.shared.Registration addStateListener(java.util.function.Consumer<io.github.mundanej.map.api.MeasurementState>);
    descriptor: (Ljava/util/function/Consumer;)Lcom/vaadin/flow/shared/Registration;
  public int vertexLimit();
    descriptor: ()I
  public void onActivate(io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolContext;)V
  public io.github.mundanej.map.api.MapToolResult onMapToolEvent(io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/api/MapToolResult;
  public io.github.mundanej.map.api.MapToolResult onMapToolCommand(io.github.mundanej.map.api.MapToolCommandEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolCommandEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/api/MapToolResult;
  public void onDeactivate(io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolContext;)V
  public io.github.mundanej.map.api.MapCursorIntent cursorIntent();
    descriptor: ()Lio/github/mundanej/map/api/MapCursorIntent;
  public boolean belongsTo(io.github.mundanej.map.vaadin.MundaneMap);
    descriptor: (Lio/github/mundanej/map/vaadin/MundaneMap;)Z
  public java.util.List<io.github.mundanej.map.api.Layer> overlayLayers();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.vaadin.BrowserPointEditController implements io.github.mundanej.map.api.MapTool,io.github.mundanej.map.vaadin.BrowserBoundTool {
  public static final double DEFAULT_SNAP_TOLERANCE_PIXELS = 8.0d;
    descriptor: D
  public static final io.github.mundanej.map.api.SnapLimits BROWSER_SNAP_LIMITS;
    descriptor: Lio/github/mundanej/map/api/SnapLimits;
  public io.github.mundanej.map.vaadin.BrowserPointEditController(io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.vaadin.FeatureEditBinding);
    descriptor: (Lio/github/mundanej/map/vaadin/MundaneMap;Lio/github/mundanej/map/vaadin/FeatureEditBinding;)V
  public io.github.mundanej.map.vaadin.BrowserPointEditController(io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.vaadin.FeatureEditBinding, io.github.mundanej.map.api.SnapReferenceSet, io.github.mundanej.map.api.SnapLimits, double);
    descriptor: (Lio/github/mundanej/map/vaadin/MundaneMap;Lio/github/mundanej/map/vaadin/FeatureEditBinding;Lio/github/mundanej/map/api/SnapReferenceSet;Lio/github/mundanej/map/api/SnapLimits;D)V
  public io.github.mundanej.map.vaadin.BrowserPointEditController(io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.vaadin.FeatureEditBinding, io.github.mundanej.map.api.SnapReferenceSet, io.github.mundanej.map.api.SnapLimits, double, java.util.Optional<io.github.mundanej.map.core.HorizontalWrap>, java.util.Set<java.lang.String>);
    descriptor: (Lio/github/mundanej/map/vaadin/MundaneMap;Lio/github/mundanej/map/vaadin/FeatureEditBinding;Lio/github/mundanej/map/api/SnapReferenceSet;Lio/github/mundanej/map/api/SnapLimits;DLjava/util/Optional;Ljava/util/Set;)V
  public io.github.mundanej.map.vaadin.BrowserPointEditController$Mode mode();
    descriptor: ()Lio/github/mundanej/map/vaadin/BrowserPointEditController$Mode;
  public void create(io.github.mundanej.map.api.PointFeatureDraft);
    descriptor: (Lio/github/mundanej/map/api/PointFeatureDraft;)V
  public void moveSelected();
    descriptor: ()V
  public void clearMode();
    descriptor: ()V
  public io.github.mundanej.map.api.FeatureEditResult deleteSelected();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditResult;
  public io.github.mundanej.map.api.FeatureEditResult undo();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditResult;
  public io.github.mundanej.map.api.FeatureEditResult redo();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditResult;
  public java.util.Optional<io.github.mundanej.map.api.FeatureEditResult> lastResult();
    descriptor: ()Ljava/util/Optional;
  public void addResultListener(java.util.function.Consumer<io.github.mundanej.map.api.FeatureEditResult>);
    descriptor: (Ljava/util/function/Consumer;)V
  public void removeResultListener(java.util.function.Consumer<io.github.mundanej.map.api.FeatureEditResult>);
    descriptor: (Ljava/util/function/Consumer;)V
  public java.util.Optional<io.github.mundanej.map.vaadin.BrowserPointEditController$Preview> preview();
    descriptor: ()Ljava/util/Optional;
  public void onActivate(io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolContext;)V
  public io.github.mundanej.map.api.MapToolResult onMapToolEvent(io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/api/MapToolResult;
  public io.github.mundanej.map.api.MapToolResult onMapToolCommand(io.github.mundanej.map.api.MapToolCommandEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolCommandEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/api/MapToolResult;
  public void onDeactivate(io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolContext;)V
  public io.github.mundanej.map.api.MapCursorIntent cursorIntent();
    descriptor: ()Lio/github/mundanej/map/api/MapCursorIntent;
  public boolean belongsTo(io.github.mundanej.map.vaadin.MundaneMap);
    descriptor: (Lio/github/mundanej/map/vaadin/MundaneMap;)Z
  public java.util.List<io.github.mundanej.map.api.Layer> overlayLayers();
    descriptor: ()Ljava/util/List;
}
public final class io.github.mundanej.map.vaadin.BrowserPointEditController$Mode extends java.lang.Enum<io.github.mundanej.map.vaadin.BrowserPointEditController$Mode> {
  public static final io.github.mundanej.map.vaadin.BrowserPointEditController$Mode NONE;
    descriptor: Lio/github/mundanej/map/vaadin/BrowserPointEditController$Mode;
  public static final io.github.mundanej.map.vaadin.BrowserPointEditController$Mode CREATE;
    descriptor: Lio/github/mundanej/map/vaadin/BrowserPointEditController$Mode;
  public static final io.github.mundanej.map.vaadin.BrowserPointEditController$Mode MOVE_SELECTED;
    descriptor: Lio/github/mundanej/map/vaadin/BrowserPointEditController$Mode;
  public static io.github.mundanej.map.vaadin.BrowserPointEditController$Mode[] values();
    descriptor: ()[Lio/github/mundanej/map/vaadin/BrowserPointEditController$Mode;
  public static io.github.mundanej.map.vaadin.BrowserPointEditController$Mode valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/vaadin/BrowserPointEditController$Mode;
}
public final class io.github.mundanej.map.vaadin.BrowserPointEditController$Preview extends java.lang.Record {
  public io.github.mundanej.map.vaadin.BrowserPointEditController$Preview(io.github.mundanej.map.core.MapViewport, java.util.Optional<io.github.mundanej.map.api.Coordinate>, io.github.mundanej.map.api.Coordinate, boolean, double);
    descriptor: (Lio/github/mundanej/map/core/MapViewport;Ljava/util/Optional;Lio/github/mundanej/map/api/Coordinate;ZD)V
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.core.MapViewport viewport();
    descriptor: ()Lio/github/mundanej/map/core/MapViewport;
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> original();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.Coordinate candidate();
    descriptor: ()Lio/github/mundanej/map/api/Coordinate;
  public boolean snapped();
    descriptor: ()Z
  public double referenceDisplayX();
    descriptor: ()D
}
public final class io.github.mundanej.map.vaadin.BrowserRasterOptions extends java.lang.Record {
  public io.github.mundanej.map.vaadin.BrowserRasterOptions(io.github.mundanej.map.api.RasterInterpolation, double);
    descriptor: (Lio/github/mundanej/map/api/RasterInterpolation;D)V
  public static io.github.mundanej.map.vaadin.BrowserRasterOptions defaults();
    descriptor: ()Lio/github/mundanej/map/vaadin/BrowserRasterOptions;
  public io.github.mundanej.map.vaadin.BrowserRasterOptions withInterpolation(io.github.mundanej.map.api.RasterInterpolation);
    descriptor: (Lio/github/mundanej/map/api/RasterInterpolation;)Lio/github/mundanej/map/vaadin/BrowserRasterOptions;
  public io.github.mundanej.map.vaadin.BrowserRasterOptions withOpacity(double);
    descriptor: (D)Lio/github/mundanej/map/vaadin/BrowserRasterOptions;
  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
  public final int hashCode();
    descriptor: ()I
  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
  public io.github.mundanej.map.api.RasterInterpolation interpolation();
    descriptor: ()Lio/github/mundanej/map/api/RasterInterpolation;
  public double opacity();
    descriptor: ()D
}
public final class io.github.mundanej.map.vaadin.ElevationSourceBinding implements java.lang.AutoCloseable {
  public static io.github.mundanej.map.vaadin.ElevationSourceBinding borrowed(java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.vaadin.BrowserRasterOptions, io.github.mundanej.map.api.RasterRequestLimits);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/ElevationSource;Lio/github/mundanej/map/api/ElevationRasterStyle;Lio/github/mundanej/map/vaadin/BrowserRasterOptions;Lio/github/mundanej/map/api/RasterRequestLimits;)Lio/github/mundanej/map/vaadin/ElevationSourceBinding;
  public static io.github.mundanej.map.vaadin.ElevationSourceBinding owned(java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.vaadin.BrowserRasterOptions, io.github.mundanej.map.api.RasterRequestLimits);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/ElevationSource;Lio/github/mundanej/map/api/ElevationRasterStyle;Lio/github/mundanej/map/vaadin/BrowserRasterOptions;Lio/github/mundanej/map/api/RasterRequestLimits;)Lio/github/mundanej/map/vaadin/ElevationSourceBinding;
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.ElevationSource source();
    descriptor: ()Lio/github/mundanej/map/api/ElevationSource;
  public io.github.mundanej.map.api.ElevationRasterStyle style();
    descriptor: ()Lio/github/mundanej/map/api/ElevationRasterStyle;
  public io.github.mundanej.map.vaadin.BrowserRasterOptions options();
    descriptor: ()Lio/github/mundanej/map/vaadin/BrowserRasterOptions;
  public io.github.mundanej.map.api.RasterRequestLimits requestLimits();
    descriptor: ()Lio/github/mundanej/map/api/RasterRequestLimits;
  public boolean owned();
    descriptor: ()Z
  public synchronized io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode horizontalWrapMode();
    descriptor: ()Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;
  public synchronized void setHorizontalWrapMode(io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode);
    descriptor: (Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;)V
  public synchronized boolean isClosed();
    descriptor: ()Z
  public synchronized void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.vaadin.FeatureEditBinding implements java.lang.AutoCloseable {
  public static io.github.mundanej.map.vaadin.FeatureEditBinding open(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeaturePortrayal);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureEditSnapshot;Lio/github/mundanej/map/api/FeaturePortrayal;)Lio/github/mundanej/map/vaadin/FeatureEditBinding;
  public static io.github.mundanej.map.vaadin.FeatureEditBinding open(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditLimits, io.github.mundanej.map.api.FeatureEditHistoryLimits, io.github.mundanej.map.api.FeaturePortrayal, io.github.mundanej.map.api.NamedSymbolCatalog);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureEditSnapshot;Lio/github/mundanej/map/api/FeatureEditLimits;Lio/github/mundanej/map/api/FeatureEditHistoryLimits;Lio/github/mundanej/map/api/FeaturePortrayal;Lio/github/mundanej/map/api/NamedSymbolCatalog;)Lio/github/mundanej/map/vaadin/FeatureEditBinding;
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.FeatureEditSnapshot snapshot();
    descriptor: ()Lio/github/mundanej/map/api/FeatureEditSnapshot;
  public boolean isClosed();
    descriptor: ()Z
  public synchronized io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode horizontalWrapMode();
    descriptor: ()Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;
  public synchronized void setHorizontalWrapMode(io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode);
    descriptor: (Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;)V
  public void addFeatureEditListener(io.github.mundanej.map.api.FeatureEditListener);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditListener;)V
  public void removeFeatureEditListener(io.github.mundanej.map.api.FeatureEditListener);
    descriptor: (Lio/github/mundanej/map/api/FeatureEditListener;)V
  public synchronized void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.vaadin.FeatureSourceBinding implements java.lang.AutoCloseable {
  public static io.github.mundanej.map.vaadin.FeatureSourceBinding borrowed(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.AttributeSelection, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/AttributeSelection;Ljava/util/Optional;)Lio/github/mundanej/map/vaadin/FeatureSourceBinding;
  public static io.github.mundanej.map.vaadin.FeatureSourceBinding owned(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.AttributeSelection, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/api/AttributeSelection;Ljava/util/Optional;)Lio/github/mundanej/map/vaadin/FeatureSourceBinding;
  public static io.github.mundanej.map.vaadin.FeatureSourceBinding borrowed(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/FeaturePortrayal;Ljava/util/Optional;)Lio/github/mundanej/map/vaadin/FeatureSourceBinding;
  public static io.github.mundanej.map.vaadin.FeatureSourceBinding borrowed(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal, io.github.mundanej.map.api.NamedSymbolCatalog, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/FeaturePortrayal;Lio/github/mundanej/map/api/NamedSymbolCatalog;Ljava/util/Optional;)Lio/github/mundanej/map/vaadin/FeatureSourceBinding;
  public static io.github.mundanej.map.vaadin.FeatureSourceBinding owned(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/FeaturePortrayal;Ljava/util/Optional;)Lio/github/mundanej/map/vaadin/FeatureSourceBinding;
  public static io.github.mundanej.map.vaadin.FeatureSourceBinding owned(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal, io.github.mundanej.map.api.NamedSymbolCatalog, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/FeaturePortrayal;Lio/github/mundanej/map/api/NamedSymbolCatalog;Ljava/util/Optional;)Lio/github/mundanej/map/vaadin/FeatureSourceBinding;
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.FeatureSource source();
    descriptor: ()Lio/github/mundanej/map/api/FeatureSource;
  public io.github.mundanej.map.api.AttributeSelection attributes();
    descriptor: ()Lio/github/mundanej/map/api/AttributeSelection;
  public java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits> tighterLimits();
    descriptor: ()Ljava/util/Optional;
  public boolean owned();
    descriptor: ()Z
  public synchronized io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode horizontalWrapMode();
    descriptor: ()Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;
  public synchronized void setHorizontalWrapMode(io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode);
    descriptor: (Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;)V
  public synchronized io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement layerPlacement();
    descriptor: ()Lio/github/mundanej/map/vaadin/BrowserFeatureLayerPlacement;
  public synchronized void setLayerPlacement(io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement);
    descriptor: (Lio/github/mundanej/map/vaadin/BrowserFeatureLayerPlacement;)V
  public synchronized boolean isClosed();
    descriptor: ()Z
  public synchronized void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.vaadin.MundaneMap extends com.vaadin.flow.component.Component implements com.vaadin.flow.component.HasSize,com.vaadin.flow.component.HasEnabled,java.lang.AutoCloseable {
  public static final double DEFAULT_SELECTION_TOLERANCE_PIXELS = 4.0d;
    descriptor: D
  public static final double DEFAULT_HOVER_TOLERANCE_PIXELS = 4.0d;
    descriptor: D
  public io.github.mundanej.map.vaadin.MundaneMap();
    descriptor: ()V
  public void setSnapshotLayers(java.util.List<? extends io.github.mundanej.map.api.Layer>);
    descriptor: (Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.api.Layer> snapshotLayers();
    descriptor: ()Ljava/util/List;
  public void setFeatureSourceBindings(java.util.List<io.github.mundanej.map.vaadin.FeatureSourceBinding>);
    descriptor: (Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.vaadin.FeatureSourceBinding> featureSourceBindings();
    descriptor: ()Ljava/util/List;
  public void setRasterSourceBindings(java.util.List<io.github.mundanej.map.vaadin.RasterSourceBinding>);
    descriptor: (Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.vaadin.RasterSourceBinding> rasterSourceBindings();
    descriptor: ()Ljava/util/List;
  public void setElevationSourceBindings(java.util.List<io.github.mundanej.map.vaadin.ElevationSourceBinding>);
    descriptor: (Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.vaadin.ElevationSourceBinding> elevationSourceBindings();
    descriptor: ()Ljava/util/List;
  public void setFeatureEditBindings(java.util.List<io.github.mundanej.map.vaadin.FeatureEditBinding>);
    descriptor: (Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.vaadin.FeatureEditBinding> featureEditBindings();
    descriptor: ()Ljava/util/List;
  public void setFeatureSourceVisible(java.lang.String, boolean);
    descriptor: (Ljava/lang/String;Z)V
  public boolean isFeatureSourceVisible(java.lang.String);
    descriptor: (Ljava/lang/String;)Z
  public void setCoordinateReferenceSystems(io.github.mundanej.map.core.CrsRegistry, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.CrsDefinition);
    descriptor: (Lio/github/mundanej/map/core/CrsRegistry;Lio/github/mundanej/map/api/CrsDefinition;Lio/github/mundanej/map/api/CrsDefinition;)V
  public io.github.mundanej.map.api.CrsDefinition mapCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public java.util.Optional<io.github.mundanej.map.core.HorizontalWrap> horizontalWrap();
    descriptor: ()Ljava/util/Optional;
  public void setHorizontalWrap(io.github.mundanej.map.core.HorizontalWrap);
    descriptor: (Lio/github/mundanej/map/core/HorizontalWrap;)V
  public void clearHorizontalWrap();
    descriptor: ()V
  public io.github.mundanej.map.api.CrsDefinition displayCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public java.util.Map<java.lang.String, io.github.mundanej.map.api.DiagnosticReport> sourceReports();
    descriptor: ()Ljava/util/Map;
  public com.vaadin.flow.shared.Registration addSourceReportListener(io.github.mundanej.map.api.MapSourceReportListener);
    descriptor: (Lio/github/mundanej/map/api/MapSourceReportListener;)Lcom/vaadin/flow/shared/Registration;
  public void setBackground(io.github.mundanej.map.api.Rgba);
    descriptor: (Lio/github/mundanej/map/api/Rgba;)V
  public io.github.mundanej.map.api.Rgba background();
    descriptor: ()Lio/github/mundanej/map/api/Rgba;
  public void setViewport(io.github.mundanej.map.core.MapViewport);
    descriptor: (Lio/github/mundanej/map/core/MapViewport;)V
  public io.github.mundanej.map.core.MapViewport viewport();
    descriptor: ()Lio/github/mundanej/map/core/MapViewport;
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> screenToMap(double, double);
    descriptor: (DD)Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> mapToScreen(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Ljava/util/Optional;
  public io.github.mundanej.map.api.MapHitResults hitTest(double, double, double);
    descriptor: (DDD)Lio/github/mundanej/map/api/MapHitResults;
  public java.util.Optional<io.github.mundanej.map.api.FeatureSelection> selection();
    descriptor: ()Ljava/util/Optional;
  public void setSelection(io.github.mundanej.map.api.FeatureSelection);
    descriptor: (Lio/github/mundanej/map/api/FeatureSelection;)V
  public void clearSelection();
    descriptor: ()V
  public java.util.Optional<io.github.mundanej.map.api.MapHit> hover();
    descriptor: ()Ljava/util/Optional;
  public com.vaadin.flow.shared.Registration addMapPointerListener(io.github.mundanej.map.api.MapPointerListener);
    descriptor: (Lio/github/mundanej/map/api/MapPointerListener;)Lcom/vaadin/flow/shared/Registration;
  public com.vaadin.flow.shared.Registration addMapHoverListener(io.github.mundanej.map.api.MapHoverListener);
    descriptor: (Lio/github/mundanej/map/api/MapHoverListener;)Lcom/vaadin/flow/shared/Registration;
  public com.vaadin.flow.shared.Registration addMapSelectionListener(io.github.mundanej.map.api.MapSelectionListener);
    descriptor: (Lio/github/mundanej/map/api/MapSelectionListener;)Lcom/vaadin/flow/shared/Registration;
  public io.github.mundanej.map.api.FeatureOverlaySymbols hoverOverlaySymbols();
    descriptor: ()Lio/github/mundanej/map/api/FeatureOverlaySymbols;
  public void setHoverOverlaySymbols(io.github.mundanej.map.api.FeatureOverlaySymbols);
    descriptor: (Lio/github/mundanej/map/api/FeatureOverlaySymbols;)V
  public io.github.mundanej.map.api.FeatureOverlaySymbols selectionOverlaySymbols();
    descriptor: ()Lio/github/mundanej/map/api/FeatureOverlaySymbols;
  public void setSelectionOverlaySymbols(io.github.mundanej.map.api.FeatureOverlaySymbols);
    descriptor: (Lio/github/mundanej/map/api/FeatureOverlaySymbols;)V
  public void setActiveTool(io.github.mundanej.map.api.MapTool);
    descriptor: (Lio/github/mundanej/map/api/MapTool;)V
  public void clearActiveTool();
    descriptor: ()V
  public java.util.Optional<io.github.mundanej.map.api.MapTool> activeTool();
    descriptor: ()Ljava/util/Optional;
  public boolean fitToContents(double);
    descriptor: (D)Z
  public com.vaadin.flow.shared.Registration addViewportChangeListener(java.util.function.Consumer<io.github.mundanej.map.core.MapViewport>);
    descriptor: (Ljava/util/function/Consumer;)Lcom/vaadin/flow/shared/Registration;
  public java.util.Optional<io.github.mundanej.map.vaadin.MundaneMapException> diagnostic();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.api.VectorExportSnapshot captureVectorExportSnapshot();
    descriptor: ()Lio/github/mundanej/map/api/VectorExportSnapshot;
  public io.github.mundanej.map.api.VectorExportSnapshot captureVectorExportSnapshot(io.github.mundanej.map.api.VectorExportSnapshotLimits);
    descriptor: (Lio/github/mundanej/map/api/VectorExportSnapshotLimits;)Lio/github/mundanej/map/api/VectorExportSnapshot;
  public io.github.mundanej.map.api.VectorExportSnapshot captureVectorExportSnapshot(io.github.mundanej.map.api.VectorExportSnapshotLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/VectorExportSnapshotLimits;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/VectorExportSnapshot;
  public java.util.Map<java.lang.String, java.lang.Object> acceptMapInteraction(int, double, double, double, double, java.lang.String, double, double, int, int, int, int, double, boolean, java.lang.String);
    descriptor: (IDDDDLjava/lang/String;DDIIIIDZLjava/lang/String;)Ljava/util/Map;
  public java.util.Map<java.lang.String, java.lang.Object> acceptMapToolResume(int, double, double, double, double);
    descriptor: (IDDDD)Ljava/util/Map;
  public java.util.Map<java.lang.String, java.lang.Object> acceptMapCommand(int, double, double, double, double, java.lang.String);
    descriptor: (IDDDDLjava/lang/String;)Ljava/util/Map;
  public boolean acceptTransientViewport(int, double, double, double, double, int, int, double, double, double);
    descriptor: (IDDDDIIDDD)Z
  public boolean acceptSettledViewport(int, double, double, double, double, int, int, double, double, double);
    descriptor: (IDDDDIIDDD)Z
  public void acceptLabelMeasurements(int, double, double, double, double[]);
    descriptor: (IDDD[D)V
  public void acceptPlacedLabels(int, double, double, double);
    descriptor: (IDDD)V
  public void acceptClientFailure(int, double, double, java.lang.String);
    descriptor: (IDDLjava/lang/String;)V
  public void setEnabled(boolean);
    descriptor: (Z)V
  public void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.vaadin.MundaneMapException extends java.lang.RuntimeException {
  public static final java.lang.String CLOSED = "CLOSED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String DISABLED = "DISABLED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String PROTOCOL_VERSION_UNSUPPORTED = "PROTOCOL_VERSION_UNSUPPORTED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String STALE_GENERATION = "STALE_GENERATION";
    descriptor: Ljava/lang/String;
  public static final java.lang.String EVENT_SEQUENCE_INVALID = "EVENT_SEQUENCE_INVALID";
    descriptor: Ljava/lang/String;
  public static final java.lang.String EVENT_RATE_EXCEEDED = "EVENT_RATE_EXCEEDED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String NON_FINITE_VALUE = "NON_FINITE_VALUE";
    descriptor: Ljava/lang/String;
  public static final java.lang.String DUPLICATE_ID = "DUPLICATE_ID";
    descriptor: Ljava/lang/String;
  public static final java.lang.String LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String UNSUPPORTED_VALUE = "SYMBOL_UNSUPPORTED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String BROWSER_CAPABILITY_UNSUPPORTED = "BROWSER_CAPABILITY_UNSUPPORTED";
    descriptor: Ljava/lang/String;
  public static final java.lang.String CLIENT_FAILURE = "CLIENT_FAILURE";
    descriptor: Ljava/lang/String;
  public static final java.lang.String RESOURCE_UNAVAILABLE = "RESOURCE_UNAVAILABLE";
    descriptor: Ljava/lang/String;
  public static final java.lang.String WORLD_WRAP_RASTER_INCOMPATIBLE = "WORLD_WRAP_RASTER_INCOMPATIBLE";
    descriptor: Ljava/lang/String;
  public io.github.mundanej.map.vaadin.MundaneMapException(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.vaadin.RasterSourceBinding implements java.lang.AutoCloseable {
  public static io.github.mundanej.map.vaadin.RasterSourceBinding borrowed(java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/RasterSource;)Lio/github/mundanej/map/vaadin/RasterSourceBinding;
  public static io.github.mundanej.map.vaadin.RasterSourceBinding borrowed(java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource, io.github.mundanej.map.vaadin.BrowserRasterOptions, java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/RasterSource;Lio/github/mundanej/map/vaadin/BrowserRasterOptions;Ljava/util/Optional;)Lio/github/mundanej/map/vaadin/RasterSourceBinding;
  public static io.github.mundanej.map.vaadin.RasterSourceBinding owned(java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource, io.github.mundanej.map.vaadin.BrowserRasterOptions, java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits>);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/RasterSource;Lio/github/mundanej/map/vaadin/BrowserRasterOptions;Ljava/util/Optional;)Lio/github/mundanej/map/vaadin/RasterSourceBinding;
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.RasterSource source();
    descriptor: ()Lio/github/mundanej/map/api/RasterSource;
  public io.github.mundanej.map.vaadin.BrowserRasterOptions options();
    descriptor: ()Lio/github/mundanej/map/vaadin/BrowserRasterOptions;
  public java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits> tighterLimits();
    descriptor: ()Ljava/util/Optional;
  public boolean owned();
    descriptor: ()Z
  public synchronized io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode horizontalWrapMode();
    descriptor: ()Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;
  public synchronized void setHorizontalWrapMode(io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode);
    descriptor: (Lio/github/mundanej/map/vaadin/BrowserHorizontalWrapMode;)V
  public synchronized boolean isClosed();
    descriptor: ()Z
  public synchronized void close();
    descriptor: ()V
}
SHAPE io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement sealed=false permits=[] record=[] enum=[BASEMAP, OVERLAY] annotations=[] members=[field:BASEMAP[], field:OVERLAY[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode sealed=false permits=[] record=[] enum=[NONE, REPEAT_X] annotations=[] members=[field:NONE[], field:REPEAT_X[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.BrowserMeasurementTool sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.api.DistanceStrategy, int] throws=[] annotations=[] parameterAnnotations=[[], [], []], constructor:[io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.api.DistanceStrategy] throws=[] annotations=[] parameterAnnotations=[[], []], field:DEFAULT_VERTEX_LIMIT[], method:addStateListener[java.util.function.Consumer<io.github.mundanej.map.api.MeasurementState>] throws=[] annotations=[] parameterAnnotations=[[]], method:belongsTo[io.github.mundanej.map.vaadin.MundaneMap] throws=[] annotations=[] parameterAnnotations=[[]], method:cursorIntent[] throws=[] annotations=[] parameterAnnotations=[], method:distanceStrategy[] throws=[] annotations=[] parameterAnnotations=[], method:onActivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onDeactivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onMapToolCommand[io.github.mundanej.map.api.MapToolCommandEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:onMapToolEvent[io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:overlayLayers[] throws=[] annotations=[] parameterAnnotations=[], method:state[] throws=[] annotations=[] parameterAnnotations=[], method:vertexLimit[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.BrowserPointEditController sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.vaadin.FeatureEditBinding, io.github.mundanej.map.api.SnapReferenceSet, io.github.mundanej.map.api.SnapLimits, double, java.util.Optional<io.github.mundanej.map.core.HorizontalWrap>, java.util.Set<java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], []], constructor:[io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.vaadin.FeatureEditBinding, io.github.mundanej.map.api.SnapReferenceSet, io.github.mundanej.map.api.SnapLimits, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], constructor:[io.github.mundanej.map.vaadin.MundaneMap, io.github.mundanej.map.vaadin.FeatureEditBinding] throws=[] annotations=[] parameterAnnotations=[[], []], field:BROWSER_SNAP_LIMITS[], field:DEFAULT_SNAP_TOLERANCE_PIXELS[], method:addResultListener[java.util.function.Consumer<io.github.mundanej.map.api.FeatureEditResult>] throws=[] annotations=[] parameterAnnotations=[[]], method:belongsTo[io.github.mundanej.map.vaadin.MundaneMap] throws=[] annotations=[] parameterAnnotations=[[]], method:clearMode[] throws=[] annotations=[] parameterAnnotations=[], method:create[io.github.mundanej.map.api.PointFeatureDraft] throws=[] annotations=[] parameterAnnotations=[[]], method:cursorIntent[] throws=[] annotations=[] parameterAnnotations=[], method:deleteSelected[] throws=[] annotations=[] parameterAnnotations=[], method:lastResult[] throws=[] annotations=[] parameterAnnotations=[], method:mode[] throws=[] annotations=[] parameterAnnotations=[], method:moveSelected[] throws=[] annotations=[] parameterAnnotations=[], method:onActivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onDeactivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onMapToolCommand[io.github.mundanej.map.api.MapToolCommandEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:onMapToolEvent[io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:overlayLayers[] throws=[] annotations=[] parameterAnnotations=[], method:preview[] throws=[] annotations=[] parameterAnnotations=[], method:redo[] throws=[] annotations=[] parameterAnnotations=[], method:removeResultListener[java.util.function.Consumer<io.github.mundanej.map.api.FeatureEditResult>] throws=[] annotations=[] parameterAnnotations=[[]], method:undo[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.BrowserPointEditController$Mode sealed=false permits=[] record=[] enum=[NONE, CREATE, MOVE_SELECTED] annotations=[] members=[field:CREATE[], field:MOVE_SELECTED[], field:NONE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.BrowserPointEditController$Preview sealed=false permits=[] record=[viewport:io.github.mundanej.map.core.MapViewport[], original:java.util.Optional<io.github.mundanej.map.api.Coordinate>[], candidate:io.github.mundanej.map.api.Coordinate[], snapped:boolean[], referenceDisplayX:double[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.core.MapViewport, java.util.Optional<io.github.mundanej.map.api.Coordinate>, io.github.mundanej.map.api.Coordinate, boolean, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:candidate[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:original[] throws=[] annotations=[] parameterAnnotations=[], method:referenceDisplayX[] throws=[] annotations=[] parameterAnnotations=[], method:snapped[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:viewport[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.BrowserRasterOptions sealed=false permits=[] record=[interpolation:io.github.mundanej.map.api.RasterInterpolation[], opacity:double[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.RasterInterpolation, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:interpolation[] throws=[] annotations=[] parameterAnnotations=[], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withInterpolation[io.github.mundanej.map.api.RasterInterpolation] throws=[] annotations=[] parameterAnnotations=[[]], method:withOpacity[double] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.vaadin.ElevationSourceBinding sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:borrowed[java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.vaadin.BrowserRasterOptions, io.github.mundanej.map.api.RasterRequestLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:horizontalWrapMode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:options[] throws=[] annotations=[] parameterAnnotations=[], method:owned[] throws=[] annotations=[] parameterAnnotations=[], method:owned[java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.vaadin.BrowserRasterOptions, io.github.mundanej.map.api.RasterRequestLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:requestLimits[] throws=[] annotations=[] parameterAnnotations=[], method:setHorizontalWrapMode[io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode] throws=[] annotations=[] parameterAnnotations=[[]], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:style[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.FeatureEditBinding sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:addFeatureEditListener[io.github.mundanej.map.api.FeatureEditListener] throws=[] annotations=[] parameterAnnotations=[[]], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:horizontalWrapMode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:open[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeatureEditLimits, io.github.mundanej.map.api.FeatureEditHistoryLimits, io.github.mundanej.map.api.FeaturePortrayal, io.github.mundanej.map.api.NamedSymbolCatalog] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], []], method:open[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureEditSnapshot, io.github.mundanej.map.api.FeaturePortrayal] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:removeFeatureEditListener[io.github.mundanej.map.api.FeatureEditListener] throws=[] annotations=[] parameterAnnotations=[[]], method:setHorizontalWrapMode[io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode] throws=[] annotations=[] parameterAnnotations=[[]], method:snapshot[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.FeatureSourceBinding sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:attributes[] throws=[] annotations=[] parameterAnnotations=[], method:borrowed[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal, io.github.mundanej.map.api.NamedSymbolCatalog, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:borrowed[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:borrowed[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.AttributeSelection, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:horizontalWrapMode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:layerPlacement[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:owned[] throws=[] annotations=[] parameterAnnotations=[], method:owned[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal, io.github.mundanej.map.api.NamedSymbolCatalog, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:owned[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:owned[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.Symbol, io.github.mundanej.map.api.AttributeSelection, java.util.Optional<io.github.mundanej.map.api.FeatureQueryLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], [], [], []], method:setHorizontalWrapMode[io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode] throws=[] annotations=[] parameterAnnotations=[[]], method:setLayerPlacement[io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement] throws=[] annotations=[] parameterAnnotations=[[]], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:tighterLimits[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.MundaneMap sealed=false permits=[] record=[] enum=[] annotations=[@com.vaadin.flow.component.Tag("mundane-map-canvas"), @com.vaadin.flow.component.dependency.JsModule(developmentOnly=false, value="./mundane-map-canvas.js")] members=[constructor:[] throws=[] annotations=[] parameterAnnotations=[], field:DEFAULT_HOVER_TOLERANCE_PIXELS[], field:DEFAULT_SELECTION_TOLERANCE_PIXELS[], method:acceptClientFailure[int, double, double, java.lang.String] throws=[] annotations=[@com.vaadin.flow.component.ClientCallable(ONLY_WHEN_ENABLED)] parameterAnnotations=[[], [], [], []], method:acceptLabelMeasurements[int, double, double, double, double[]] throws=[] annotations=[@com.vaadin.flow.component.ClientCallable(ONLY_WHEN_ENABLED)] parameterAnnotations=[[], [], [], [], []], method:acceptMapCommand[int, double, double, double, double, java.lang.String] throws=[] annotations=[@com.vaadin.flow.component.ClientCallable(ONLY_WHEN_ENABLED)] parameterAnnotations=[[], [], [], [], [], []], method:acceptMapInteraction[int, double, double, double, double, java.lang.String, double, double, int, int, int, int, double, boolean, java.lang.String] throws=[] annotations=[@com.vaadin.flow.component.ClientCallable(ONLY_WHEN_ENABLED)] parameterAnnotations=[[], [], [], [], [], [], [], [], [], [], [], [], [], [], []], method:acceptMapToolResume[int, double, double, double, double] throws=[] annotations=[@com.vaadin.flow.component.ClientCallable(ONLY_WHEN_ENABLED)] parameterAnnotations=[[], [], [], [], []], method:acceptPlacedLabels[int, double, double, double] throws=[] annotations=[@com.vaadin.flow.component.ClientCallable(ONLY_WHEN_ENABLED)] parameterAnnotations=[[], [], [], []], method:acceptSettledViewport[int, double, double, double, double, int, int, double, double, double] throws=[] annotations=[@com.vaadin.flow.component.ClientCallable(ONLY_WHEN_ENABLED)] parameterAnnotations=[[], [], [], [], [], [], [], [], [], []], method:acceptTransientViewport[int, double, double, double, double, int, int, double, double, double] throws=[] annotations=[@com.vaadin.flow.component.ClientCallable(ONLY_WHEN_ENABLED)] parameterAnnotations=[[], [], [], [], [], [], [], [], [], []], method:activeTool[] throws=[] annotations=[] parameterAnnotations=[], method:addMapHoverListener[io.github.mundanej.map.api.MapHoverListener] throws=[] annotations=[] parameterAnnotations=[[]], method:addMapPointerListener[io.github.mundanej.map.api.MapPointerListener] throws=[] annotations=[] parameterAnnotations=[[]], method:addMapSelectionListener[io.github.mundanej.map.api.MapSelectionListener] throws=[] annotations=[] parameterAnnotations=[[]], method:addSourceReportListener[io.github.mundanej.map.api.MapSourceReportListener] throws=[] annotations=[] parameterAnnotations=[[]], method:addViewportChangeListener[java.util.function.Consumer<io.github.mundanej.map.core.MapViewport>] throws=[] annotations=[] parameterAnnotations=[[]], method:background[] throws=[] annotations=[] parameterAnnotations=[], method:captureVectorExportSnapshot[] throws=[] annotations=[] parameterAnnotations=[], method:captureVectorExportSnapshot[io.github.mundanej.map.api.VectorExportSnapshotLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []], method:captureVectorExportSnapshot[io.github.mundanej.map.api.VectorExportSnapshotLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:clearActiveTool[] throws=[] annotations=[] parameterAnnotations=[], method:clearHorizontalWrap[] throws=[] annotations=[] parameterAnnotations=[], method:clearSelection[] throws=[] annotations=[] parameterAnnotations=[], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:diagnostic[] throws=[] annotations=[] parameterAnnotations=[], method:displayCrs[] throws=[] annotations=[] parameterAnnotations=[], method:elevationSourceBindings[] throws=[] annotations=[] parameterAnnotations=[], method:featureEditBindings[] throws=[] annotations=[] parameterAnnotations=[], method:featureSourceBindings[] throws=[] annotations=[] parameterAnnotations=[], method:fitToContents[double] throws=[] annotations=[] parameterAnnotations=[[]], method:hitTest[double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:horizontalWrap[] throws=[] annotations=[] parameterAnnotations=[], method:hoverOverlaySymbols[] throws=[] annotations=[] parameterAnnotations=[], method:hover[] throws=[] annotations=[] parameterAnnotations=[], method:isFeatureSourceVisible[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:mapCrs[] throws=[] annotations=[] parameterAnnotations=[], method:mapToScreen[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:onAttach[com.vaadin.flow.component.AttachEvent] throws=[] annotations=[] parameterAnnotations=[[]], method:onDetach[com.vaadin.flow.component.DetachEvent] throws=[] annotations=[] parameterAnnotations=[[]], method:rasterSourceBindings[] throws=[] annotations=[] parameterAnnotations=[], method:screenToMap[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:selectionOverlaySymbols[] throws=[] annotations=[] parameterAnnotations=[], method:selection[] throws=[] annotations=[] parameterAnnotations=[], method:setActiveTool[io.github.mundanej.map.api.MapTool] throws=[] annotations=[] parameterAnnotations=[[]], method:setBackground[io.github.mundanej.map.api.Rgba] throws=[] annotations=[] parameterAnnotations=[[]], method:setCoordinateReferenceSystems[io.github.mundanej.map.core.CrsRegistry, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.CrsDefinition] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:setElevationSourceBindings[java.util.List<io.github.mundanej.map.vaadin.ElevationSourceBinding>] throws=[] annotations=[] parameterAnnotations=[[]], method:setEnabled[boolean] throws=[] annotations=[] parameterAnnotations=[[]], method:setFeatureEditBindings[java.util.List<io.github.mundanej.map.vaadin.FeatureEditBinding>] throws=[] annotations=[] parameterAnnotations=[[]], method:setFeatureSourceBindings[java.util.List<io.github.mundanej.map.vaadin.FeatureSourceBinding>] throws=[] annotations=[] parameterAnnotations=[[]], method:setFeatureSourceVisible[java.lang.String, boolean] throws=[] annotations=[] parameterAnnotations=[[], []], method:setHorizontalWrap[io.github.mundanej.map.core.HorizontalWrap] throws=[] annotations=[] parameterAnnotations=[[]], method:setHoverOverlaySymbols[io.github.mundanej.map.api.FeatureOverlaySymbols] throws=[] annotations=[] parameterAnnotations=[[]], method:setRasterSourceBindings[java.util.List<io.github.mundanej.map.vaadin.RasterSourceBinding>] throws=[] annotations=[] parameterAnnotations=[[]], method:setSelectionOverlaySymbols[io.github.mundanej.map.api.FeatureOverlaySymbols] throws=[] annotations=[] parameterAnnotations=[[]], method:setSelection[io.github.mundanej.map.api.FeatureSelection] throws=[] annotations=[] parameterAnnotations=[[]], method:setSnapshotLayers[java.util.List<? extends io.github.mundanej.map.api.Layer>] throws=[] annotations=[] parameterAnnotations=[[]], method:setViewport[io.github.mundanej.map.core.MapViewport] throws=[] annotations=[] parameterAnnotations=[[]], method:snapshotLayers[] throws=[] annotations=[] parameterAnnotations=[], method:sourceReports[] throws=[] annotations=[] parameterAnnotations=[], method:viewport[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.MundaneMapException sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:BROWSER_CAPABILITY_UNSUPPORTED[], field:CLIENT_FAILURE[], field:CLOSED[], field:DISABLED[], field:DUPLICATE_ID[], field:EVENT_RATE_EXCEEDED[], field:EVENT_SEQUENCE_INVALID[], field:LIMIT_EXCEEDED[], field:NON_FINITE_VALUE[], field:PROTOCOL_VERSION_UNSUPPORTED[], field:RESOURCE_UNAVAILABLE[], field:STALE_GENERATION[], field:UNSUPPORTED_VALUE[], field:WORLD_WRAP_RASTER_INCOMPATIBLE[], method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.vaadin.RasterSourceBinding sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:borrowed[java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource, io.github.mundanej.map.vaadin.BrowserRasterOptions, java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:borrowed[java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:horizontalWrapMode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:options[] throws=[] annotations=[] parameterAnnotations=[], method:owned[] throws=[] annotations=[] parameterAnnotations=[], method:owned[java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource, io.github.mundanej.map.vaadin.BrowserRasterOptions, java.util.Optional<io.github.mundanej.map.api.RasterRequestLimits>] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], method:setHorizontalWrapMode[io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode] throws=[] annotations=[] parameterAnnotations=[[]], method:source[] throws=[] annotations=[] parameterAnnotations=[], method:tighterLimits[] throws=[] annotations=[] parameterAnnotations=[]]
