public final class io.github.mundanej.map.awt.AwtLogicalPaintPresence extends java.lang.Enum<io.github.mundanej.map.awt.AwtLogicalPaintPresence> {
  public static final io.github.mundanej.map.awt.AwtLogicalPaintPresence EMPTY;
    descriptor: Lio/github/mundanej/map/awt/AwtLogicalPaintPresence;
  public static final io.github.mundanej.map.awt.AwtLogicalPaintPresence PRESENT;
    descriptor: Lio/github/mundanej/map/awt/AwtLogicalPaintPresence;
  public static final io.github.mundanej.map.awt.AwtLogicalPaintPresence UNKNOWN;
    descriptor: Lio/github/mundanej/map/awt/AwtLogicalPaintPresence;
  public static io.github.mundanej.map.awt.AwtLogicalPaintPresence[] values();
    descriptor: ()[Lio/github/mundanej/map/awt/AwtLogicalPaintPresence;
  public static io.github.mundanej.map.awt.AwtLogicalPaintPresence valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/awt/AwtLogicalPaintPresence;
}
public final class io.github.mundanej.map.awt.AwtRasterDecoders {
  public static io.github.mundanej.map.api.EncodedRasterDecoderRegistry level1();
    descriptor: ()Lio/github/mundanej/map/api/EncodedRasterDecoderRegistry;
}
public final class io.github.mundanej.map.awt.AwtRasterDecoders$DecoderConfigurationException extends java.lang.IllegalStateException {
  public java.lang.String code();
    descriptor: ()Ljava/lang/String;
  public java.util.Map<java.lang.String, java.lang.String> context();
    descriptor: ()Ljava/util/Map;
}
public final class io.github.mundanej.map.awt.AwtSymbolHitContext {
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.Geometry featureGeometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
  public io.github.mundanej.map.api.Geometry renderGeometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
  public io.github.mundanej.map.core.MapViewport viewport();
    descriptor: ()Lio/github/mundanej/map/core/MapViewport;
  public double inheritedOpacity();
    descriptor: ()D
  public boolean closedRing();
    descriptor: ()Z
  public java.util.OptionalDouble endpointBearingDegrees();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> markerAnchorScreen();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.core.MapScreenBasis mapScreenBasis();
    descriptor: ()Lio/github/mundanej/map/core/MapScreenBasis;
  public double queryX();
    descriptor: ()D
  public double queryY();
    descriptor: ()D
  public double tolerancePixels();
    descriptor: ()D
  public java.awt.geom.Rectangle2D componentClip();
    descriptor: ()Ljava/awt/geom/Rectangle2D;
  public io.github.mundanej.map.api.Coordinate sourceToScreen(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public boolean hitChild(io.github.mundanej.map.api.Symbol, double);
    descriptor: (Lio/github/mundanej/map/api/Symbol;D)Z
  public boolean visibleShapeHit(java.awt.Shape);
    descriptor: (Ljava/awt/Shape;)Z
}
public final class io.github.mundanej.map.awt.AwtSymbolRenderContext {
  public io.github.mundanej.map.api.SymbolRole role();
    descriptor: ()Lio/github/mundanej/map/api/SymbolRole;
  public java.lang.String featureId();
    descriptor: ()Ljava/lang/String;
  public io.github.mundanej.map.api.Geometry featureGeometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
  public io.github.mundanej.map.api.Geometry renderGeometry();
    descriptor: ()Lio/github/mundanej/map/api/Geometry;
  public io.github.mundanej.map.core.CrsOperation mapToDisplayOperation();
    descriptor: ()Lio/github/mundanej/map/core/CrsOperation;
  public io.github.mundanej.map.core.MapViewport viewport();
    descriptor: ()Lio/github/mundanej/map/core/MapViewport;
  public double inheritedOpacity();
    descriptor: ()D
  public boolean closedRing();
    descriptor: ()Z
  public java.util.OptionalDouble endpointBearingDegrees();
    descriptor: ()Ljava/util/OptionalDouble;
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> markerAnchorScreen();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.core.MapScreenBasis mapScreenBasis();
    descriptor: ()Lio/github/mundanej/map/core/MapScreenBasis;
  public java.awt.Graphics2D createGraphics();
    descriptor: ()Ljava/awt/Graphics2D;
  public io.github.mundanej.map.api.Coordinate sourceToScreen(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Lio/github/mundanej/map/api/Coordinate;
  public io.github.mundanej.map.awt.SymbolRenderResult renderChild(io.github.mundanej.map.api.Symbol, double);
    descriptor: (Lio/github/mundanej/map/api/Symbol;D)Lio/github/mundanej/map/awt/SymbolRenderResult;
}
public interface io.github.mundanej.map.awt.AwtSymbolRenderer {
  public abstract boolean supports(io.github.mundanej.map.api.Symbol);
    descriptor: (Lio/github/mundanej/map/api/Symbol;)Z
  public abstract io.github.mundanej.map.awt.SymbolRenderResult render(io.github.mundanej.map.api.Symbol, io.github.mundanej.map.awt.AwtSymbolRenderContext);
    descriptor: (Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/awt/AwtSymbolRenderContext;)Lio/github/mundanej/map/awt/SymbolRenderResult;
  public default boolean hitTest(io.github.mundanej.map.api.Symbol, io.github.mundanej.map.awt.AwtSymbolHitContext);
    descriptor: (Lio/github/mundanej/map/api/Symbol;Lio/github/mundanej/map/awt/AwtSymbolHitContext;)Z
}
public final class io.github.mundanej.map.awt.HorizontalWrapMode extends java.lang.Enum<io.github.mundanej.map.awt.HorizontalWrapMode> {
  public static final io.github.mundanej.map.awt.HorizontalWrapMode NONE;
    descriptor: Lio/github/mundanej/map/awt/HorizontalWrapMode;
  public static final io.github.mundanej.map.awt.HorizontalWrapMode REPEAT_X;
    descriptor: Lio/github/mundanej/map/awt/HorizontalWrapMode;
  public static io.github.mundanej.map.awt.HorizontalWrapMode[] values();
    descriptor: ()[Lio/github/mundanej/map/awt/HorizontalWrapMode;
  public static io.github.mundanej.map.awt.HorizontalWrapMode valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/awt/HorizontalWrapMode;
}
public final class io.github.mundanej.map.awt.MapLayerBinding implements java.lang.AutoCloseable {
  public static io.github.mundanej.map.awt.MapLayerBinding snapshot(io.github.mundanej.map.api.Layer);
    descriptor: (Lio/github/mundanej/map/api/Layer;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding portrayedSnapshot(io.github.mundanej.map.api.Layer, io.github.mundanej.map.api.FeaturePortrayal);
    descriptor: (Lio/github/mundanej/map/api/Layer;Lio/github/mundanej/map/api/FeaturePortrayal;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding borrowedFeature(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.MarkerSymbol, io.github.mundanej.map.api.LineSymbol, io.github.mundanej.map.api.FillSymbol);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/MarkerSymbol;Lio/github/mundanej/map/api/LineSymbol;Lio/github/mundanej/map/api/FillSymbol;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding borrowedFeature(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/FeaturePortrayal;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding ownedFeature(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.MarkerSymbol, io.github.mundanej.map.api.LineSymbol, io.github.mundanej.map.api.FillSymbol);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/MarkerSymbol;Lio/github/mundanej/map/api/LineSymbol;Lio/github/mundanej/map/api/FillSymbol;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding ownedFeature(java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/FeatureSource;Lio/github/mundanej/map/api/FeaturePortrayal;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding editableFeature(java.lang.String, java.lang.String, io.github.mundanej.map.core.FeatureEditSession, io.github.mundanej.map.api.MarkerSymbol, io.github.mundanej.map.api.LineSymbol, io.github.mundanej.map.api.FillSymbol);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/core/FeatureEditSession;Lio/github/mundanej/map/api/MarkerSymbol;Lio/github/mundanej/map/api/LineSymbol;Lio/github/mundanej/map/api/FillSymbol;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding editableFeature(java.lang.String, java.lang.String, io.github.mundanej.map.core.FeatureEditSession, io.github.mundanej.map.api.FeaturePortrayal);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/core/FeatureEditSession;Lio/github/mundanej/map/api/FeaturePortrayal;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding borrowedRaster(java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/RasterSource;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding borrowedRaster(java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource, io.github.mundanej.map.awt.RasterRenderOptions);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/RasterSource;Lio/github/mundanej/map/awt/RasterRenderOptions;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding ownedRaster(java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/RasterSource;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding ownedRaster(java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource, io.github.mundanej.map.awt.RasterRenderOptions);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/RasterSource;Lio/github/mundanej/map/awt/RasterRenderOptions;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding borrowedElevation(java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/ElevationSource;Lio/github/mundanej/map/api/ElevationRasterStyle;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding borrowedElevation(java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.awt.RasterRenderOptions, io.github.mundanej.map.api.RasterRequestLimits);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/ElevationSource;Lio/github/mundanej/map/api/ElevationRasterStyle;Lio/github/mundanej/map/awt/RasterRenderOptions;Lio/github/mundanej/map/api/RasterRequestLimits;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding ownedElevation(java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/ElevationSource;Lio/github/mundanej/map/api/ElevationRasterStyle;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public static io.github.mundanej.map.awt.MapLayerBinding ownedElevation(java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.awt.RasterRenderOptions, io.github.mundanej.map.api.RasterRequestLimits);
    descriptor: (Ljava/lang/String;Ljava/lang/String;Lio/github/mundanej/map/api/ElevationSource;Lio/github/mundanej/map/api/ElevationRasterStyle;Lio/github/mundanej/map/awt/RasterRenderOptions;Lio/github/mundanej/map/api/RasterRequestLimits;)Lio/github/mundanej/map/awt/MapLayerBinding;
  public java.lang.String id();
    descriptor: ()Ljava/lang/String;
  public java.lang.String name();
    descriptor: ()Ljava/lang/String;
  public synchronized io.github.mundanej.map.awt.HorizontalWrapMode horizontalWrapMode();
    descriptor: ()Lio/github/mundanej/map/awt/HorizontalWrapMode;
  public synchronized void setHorizontalWrapMode(io.github.mundanej.map.awt.HorizontalWrapMode);
    descriptor: (Lio/github/mundanej/map/awt/HorizontalWrapMode;)V
  public synchronized void setPortrayalZoomRange(double, double);
    descriptor: (DD)V
  public boolean cancelCurrentOperation();
    descriptor: ()Z
  public synchronized boolean isClosed();
    descriptor: ()Z
  public void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.awt.MapView extends javax.swing.JComponent implements java.lang.AutoCloseable {
  public static final double DEFAULT_SELECTION_TOLERANCE_PIXELS = 4.0d;
    descriptor: D
  public static final double DEFAULT_HOVER_TOLERANCE_PIXELS = 4.0d;
    descriptor: D
  public io.github.mundanej.map.awt.MapView(io.github.mundanej.map.api.Projection);
    descriptor: (Lio/github/mundanej/map/api/Projection;)V
  public io.github.mundanej.map.awt.MapView(io.github.mundanej.map.api.Projection, io.github.mundanej.map.awt.SymbolRendererRegistry);
    descriptor: (Lio/github/mundanej/map/api/Projection;Lio/github/mundanej/map/awt/SymbolRendererRegistry;)V
  public io.github.mundanej.map.awt.MapView(io.github.mundanej.map.core.CrsRegistry, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.CrsDefinition);
    descriptor: (Lio/github/mundanej/map/core/CrsRegistry;Lio/github/mundanej/map/api/CrsDefinition;Lio/github/mundanej/map/api/CrsDefinition;)V
  public io.github.mundanej.map.awt.MapView(io.github.mundanej.map.core.CrsRegistry, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.awt.SymbolRendererRegistry);
    descriptor: (Lio/github/mundanej/map/core/CrsRegistry;Lio/github/mundanej/map/api/CrsDefinition;Lio/github/mundanej/map/api/CrsDefinition;Lio/github/mundanej/map/awt/SymbolRendererRegistry;)V
  public io.github.mundanej.map.api.CrsDefinition mapCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public io.github.mundanej.map.api.CrsDefinition displayCrs();
    descriptor: ()Lio/github/mundanej/map/api/CrsDefinition;
  public void setActiveTool(io.github.mundanej.map.api.MapTool);
    descriptor: (Lio/github/mundanej/map/api/MapTool;)V
  public void clearActiveTool();
    descriptor: ()V
  public java.util.Optional<io.github.mundanej.map.api.MapTool> activeTool();
    descriptor: ()Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.core.HorizontalWrap> horizontalWrap();
    descriptor: ()Ljava/util/Optional;
  public void setHorizontalWrap(io.github.mundanej.map.core.HorizontalWrap);
    descriptor: (Lio/github/mundanej/map/core/HorizontalWrap;)V
  public void clearHorizontalWrap();
    descriptor: ()V
  public void setLayers(java.util.List<io.github.mundanej.map.api.Layer>);
    descriptor: (Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.api.Layer> layers();
    descriptor: ()Ljava/util/List;
  public void setLayerBindings(java.util.List<io.github.mundanej.map.awt.MapLayerBinding>);
    descriptor: (Ljava/util/List;)V
  public java.util.List<io.github.mundanej.map.awt.MapLayerBinding> layerBindings();
    descriptor: ()Ljava/util/List;
  public io.github.mundanej.map.api.VectorExportSnapshot captureVectorExportSnapshot();
    descriptor: ()Lio/github/mundanej/map/api/VectorExportSnapshot;
  public io.github.mundanej.map.api.VectorExportSnapshot captureVectorExportSnapshot(io.github.mundanej.map.api.VectorExportSnapshotLimits);
    descriptor: (Lio/github/mundanej/map/api/VectorExportSnapshotLimits;)Lio/github/mundanej/map/api/VectorExportSnapshot;
  public io.github.mundanej.map.api.VectorExportSnapshot captureVectorExportSnapshot(io.github.mundanej.map.api.VectorExportSnapshotLimits, io.github.mundanej.map.api.CancellationToken);
    descriptor: (Lio/github/mundanej/map/api/VectorExportSnapshotLimits;Lio/github/mundanej/map/api/CancellationToken;)Lio/github/mundanej/map/api/VectorExportSnapshot;
  public void setRasterRenderOptions(java.lang.String, io.github.mundanej.map.awt.RasterRenderOptions);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/awt/RasterRenderOptions;)V
  public void setElevationRasterStyle(java.lang.String, io.github.mundanej.map.api.ElevationRasterStyle);
    descriptor: (Ljava/lang/String;Lio/github/mundanej/map/api/ElevationRasterStyle;)V
  public java.util.Map<java.lang.String, io.github.mundanej.map.api.DiagnosticReport> sourceReports();
    descriptor: ()Ljava/util/Map;
  public void addMapSourceReportListener(io.github.mundanej.map.api.MapSourceReportListener);
    descriptor: (Lio/github/mundanej/map/api/MapSourceReportListener;)V
  public void removeMapSourceReportListener(io.github.mundanej.map.api.MapSourceReportListener);
    descriptor: (Lio/github/mundanej/map/api/MapSourceReportListener;)V
  public java.util.Optional<io.github.mundanej.map.api.FeatureSelection> selection();
    descriptor: ()Ljava/util/Optional;
  public void setSelection(io.github.mundanej.map.api.FeatureSelection);
    descriptor: (Lio/github/mundanej/map/api/FeatureSelection;)V
  public void clearSelection();
    descriptor: ()V
  public java.util.Optional<io.github.mundanej.map.api.MapHit> hover();
    descriptor: ()Ljava/util/Optional;
  public void addMapHoverListener(io.github.mundanej.map.api.MapHoverListener);
    descriptor: (Lio/github/mundanej/map/api/MapHoverListener;)V
  public void removeMapHoverListener(io.github.mundanej.map.api.MapHoverListener);
    descriptor: (Lio/github/mundanej/map/api/MapHoverListener;)V
  public void addMapSelectionListener(io.github.mundanej.map.api.MapSelectionListener);
    descriptor: (Lio/github/mundanej/map/api/MapSelectionListener;)V
  public void removeMapSelectionListener(io.github.mundanej.map.api.MapSelectionListener);
    descriptor: (Lio/github/mundanej/map/api/MapSelectionListener;)V
  public io.github.mundanej.map.api.FeatureOverlaySymbols hoverOverlaySymbols();
    descriptor: ()Lio/github/mundanej/map/api/FeatureOverlaySymbols;
  public void setHoverOverlaySymbols(io.github.mundanej.map.api.FeatureOverlaySymbols);
    descriptor: (Lio/github/mundanej/map/api/FeatureOverlaySymbols;)V
  public io.github.mundanej.map.api.FeatureOverlaySymbols selectionOverlaySymbols();
    descriptor: ()Lio/github/mundanej/map/api/FeatureOverlaySymbols;
  public void setSelectionOverlaySymbols(io.github.mundanej.map.api.FeatureOverlaySymbols);
    descriptor: (Lio/github/mundanej/map/api/FeatureOverlaySymbols;)V
  public io.github.mundanej.map.api.MapHitResults hitTest(double, double, double);
    descriptor: (DDD)Lio/github/mundanej/map/api/MapHitResults;
  public io.github.mundanej.map.core.MapViewport viewport();
    descriptor: ()Lio/github/mundanej/map/core/MapViewport;
  public void setViewport(io.github.mundanej.map.core.MapViewport);
    descriptor: (Lio/github/mundanej/map/core/MapViewport;)V
  public void fitToData(double);
    descriptor: (D)V
  public void setEnabled(boolean);
    descriptor: (Z)V
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> screenToMap(double, double);
    descriptor: (DD)Ljava/util/Optional;
  public java.util.Optional<io.github.mundanej.map.api.Coordinate> mapToScreen(io.github.mundanej.map.api.Coordinate);
    descriptor: (Lio/github/mundanej/map/api/Coordinate;)Ljava/util/Optional;
  public void addMapPointerListener(io.github.mundanej.map.api.MapPointerListener);
    descriptor: (Lio/github/mundanej/map/api/MapPointerListener;)V
  public void removeMapPointerListener(io.github.mundanej.map.api.MapPointerListener);
    descriptor: (Lio/github/mundanej/map/api/MapPointerListener;)V
  public void addNotify();
    descriptor: ()V
  public void removeNotify();
    descriptor: ()V
  public void close();
    descriptor: ()V
}
public final class io.github.mundanej.map.awt.MeasurementTool implements io.github.mundanej.map.api.MapTool {
  public static final int DEFAULT_VERTEX_LIMIT = 10000;
    descriptor: I
  public io.github.mundanej.map.awt.MeasurementTool(io.github.mundanej.map.api.DistanceStrategy);
    descriptor: (Lio/github/mundanej/map/api/DistanceStrategy;)V
  public io.github.mundanej.map.awt.MeasurementTool(io.github.mundanej.map.api.DistanceStrategy, int);
    descriptor: (Lio/github/mundanej/map/api/DistanceStrategy;I)V
  public io.github.mundanej.map.api.DistanceStrategy distanceStrategy();
    descriptor: ()Lio/github/mundanej/map/api/DistanceStrategy;
  public io.github.mundanej.map.api.MeasurementState state();
    descriptor: ()Lio/github/mundanej/map/api/MeasurementState;
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
}
public final class io.github.mundanej.map.awt.PointEditController implements io.github.mundanej.map.api.MapTool {
  public static final double DEFAULT_SNAP_TOLERANCE_PIXELS = 8.0d;
    descriptor: D
  public io.github.mundanej.map.awt.PointEditController(io.github.mundanej.map.awt.MapView, io.github.mundanej.map.awt.MapLayerBinding);
    descriptor: (Lio/github/mundanej/map/awt/MapView;Lio/github/mundanej/map/awt/MapLayerBinding;)V
  public io.github.mundanej.map.awt.PointEditController(io.github.mundanej.map.awt.MapView, io.github.mundanej.map.awt.MapLayerBinding, io.github.mundanej.map.api.SnapReferenceSet, io.github.mundanej.map.api.SnapLimits, double);
    descriptor: (Lio/github/mundanej/map/awt/MapView;Lio/github/mundanej/map/awt/MapLayerBinding;Lio/github/mundanej/map/api/SnapReferenceSet;Lio/github/mundanej/map/api/SnapLimits;D)V
  public io.github.mundanej.map.awt.PointEditController$Mode mode();
    descriptor: ()Lio/github/mundanej/map/awt/PointEditController$Mode;
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
  public void onActivate(io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolContext;)V
  public io.github.mundanej.map.api.MapToolResult onMapToolEvent(io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolEvent;Lio/github/mundanej/map/api/MapToolContext;)Lio/github/mundanej/map/api/MapToolResult;
  public void onDeactivate(io.github.mundanej.map.api.MapToolContext);
    descriptor: (Lio/github/mundanej/map/api/MapToolContext;)V
  public io.github.mundanej.map.api.MapCursorIntent cursorIntent();
    descriptor: ()Lio/github/mundanej/map/api/MapCursorIntent;
}
public final class io.github.mundanej.map.awt.PointEditController$Mode extends java.lang.Enum<io.github.mundanej.map.awt.PointEditController$Mode> {
  public static final io.github.mundanej.map.awt.PointEditController$Mode NONE;
    descriptor: Lio/github/mundanej/map/awt/PointEditController$Mode;
  public static final io.github.mundanej.map.awt.PointEditController$Mode CREATE;
    descriptor: Lio/github/mundanej/map/awt/PointEditController$Mode;
  public static final io.github.mundanej.map.awt.PointEditController$Mode MOVE_SELECTED;
    descriptor: Lio/github/mundanej/map/awt/PointEditController$Mode;
  public static io.github.mundanej.map.awt.PointEditController$Mode[] values();
    descriptor: ()[Lio/github/mundanej/map/awt/PointEditController$Mode;
  public static io.github.mundanej.map.awt.PointEditController$Mode valueOf(java.lang.String);
    descriptor: (Ljava/lang/String;)Lio/github/mundanej/map/awt/PointEditController$Mode;
}
public final class io.github.mundanej.map.awt.RasterRenderOptions extends java.lang.Record {
  public io.github.mundanej.map.awt.RasterRenderOptions(io.github.mundanej.map.api.RasterInterpolation, double);
    descriptor: (Lio/github/mundanej/map/api/RasterInterpolation;D)V
  public static io.github.mundanej.map.awt.RasterRenderOptions defaults();
    descriptor: ()Lio/github/mundanej/map/awt/RasterRenderOptions;
  public io.github.mundanej.map.awt.RasterRenderOptions withInterpolation(io.github.mundanej.map.api.RasterInterpolation);
    descriptor: (Lio/github/mundanej/map/api/RasterInterpolation;)Lio/github/mundanej/map/awt/RasterRenderOptions;
  public io.github.mundanej.map.awt.RasterRenderOptions withOpacity(double);
    descriptor: (D)Lio/github/mundanej/map/awt/RasterRenderOptions;
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
public final class io.github.mundanej.map.awt.SymbolRenderResult {
  public static io.github.mundanej.map.awt.SymbolRenderResult none();
    descriptor: ()Lio/github/mundanej/map/awt/SymbolRenderResult;
  public static io.github.mundanej.map.awt.SymbolRenderResult none(io.github.mundanej.map.awt.AwtLogicalPaintPresence);
    descriptor: (Lio/github/mundanej/map/awt/AwtLogicalPaintPresence;)Lio/github/mundanej/map/awt/SymbolRenderResult;
  public static io.github.mundanej.map.awt.SymbolRenderResult markerBounds(io.github.mundanej.map.api.Envelope);
    descriptor: (Lio/github/mundanej/map/api/Envelope;)Lio/github/mundanej/map/awt/SymbolRenderResult;
  public static io.github.mundanej.map.awt.SymbolRenderResult markerBounds(io.github.mundanej.map.api.Envelope, io.github.mundanej.map.awt.AwtLogicalPaintPresence);
    descriptor: (Lio/github/mundanej/map/api/Envelope;Lio/github/mundanej/map/awt/AwtLogicalPaintPresence;)Lio/github/mundanej/map/awt/SymbolRenderResult;
  public java.util.Optional<io.github.mundanej.map.api.Envelope> nominalMarkerBounds();
    descriptor: ()Ljava/util/Optional;
  public io.github.mundanej.map.awt.AwtLogicalPaintPresence paintPresence();
    descriptor: ()Lio/github/mundanej/map/awt/AwtLogicalPaintPresence;
  public io.github.mundanej.map.awt.SymbolRenderResult union(io.github.mundanej.map.awt.SymbolRenderResult);
    descriptor: (Lio/github/mundanej/map/awt/SymbolRenderResult;)Lio/github/mundanej/map/awt/SymbolRenderResult;
}
public final class io.github.mundanej.map.awt.SymbolRendererRegistry {
  public static io.github.mundanej.map.awt.SymbolRendererRegistry$Builder builder();
    descriptor: ()Lio/github/mundanej/map/awt/SymbolRendererRegistry$Builder;
  public static io.github.mundanej.map.awt.SymbolRendererRegistry$Builder builderWithBuiltIns();
    descriptor: ()Lio/github/mundanej/map/awt/SymbolRendererRegistry$Builder;
  public static io.github.mundanej.map.awt.SymbolRendererRegistry builtIn();
    descriptor: ()Lio/github/mundanej/map/awt/SymbolRendererRegistry;
}
public final class io.github.mundanej.map.awt.SymbolRendererRegistry$Builder {
  public io.github.mundanej.map.awt.SymbolRendererRegistry$Builder register(io.github.mundanej.map.api.SymbolRole, io.github.mundanej.map.api.SymbolRendererKey, io.github.mundanej.map.awt.AwtSymbolRenderer);
    descriptor: (Lio/github/mundanej/map/api/SymbolRole;Lio/github/mundanej/map/api/SymbolRendererKey;Lio/github/mundanej/map/awt/AwtSymbolRenderer;)Lio/github/mundanej/map/awt/SymbolRendererRegistry$Builder;
  public io.github.mundanej.map.awt.SymbolRendererRegistry build();
    descriptor: ()Lio/github/mundanej/map/awt/SymbolRendererRegistry;
}
SHAPE io.github.mundanej.map.awt.AwtLogicalPaintPresence sealed=false permits=[] record=[] enum=[EMPTY, PRESENT, UNKNOWN] annotations=[] members=[field:EMPTY[], field:PRESENT[], field:UNKNOWN[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.AwtRasterDecoders sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:level1[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.AwtRasterDecoders$DecoderConfigurationException sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:code[] throws=[] annotations=[] parameterAnnotations=[], method:context[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.AwtSymbolHitContext sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:closedRing[] throws=[] annotations=[] parameterAnnotations=[], method:componentClip[] throws=[] annotations=[] parameterAnnotations=[], method:endpointBearingDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:featureGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:hitChild[io.github.mundanej.map.api.Symbol, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:inheritedOpacity[] throws=[] annotations=[] parameterAnnotations=[], method:mapScreenBasis[] throws=[] annotations=[] parameterAnnotations=[], method:markerAnchorScreen[] throws=[] annotations=[] parameterAnnotations=[], method:queryX[] throws=[] annotations=[] parameterAnnotations=[], method:queryY[] throws=[] annotations=[] parameterAnnotations=[], method:renderGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:sourceToScreen[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:tolerancePixels[] throws=[] annotations=[] parameterAnnotations=[], method:viewport[] throws=[] annotations=[] parameterAnnotations=[], method:visibleShapeHit[java.awt.Shape] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.awt.AwtSymbolRenderContext sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:closedRing[] throws=[] annotations=[] parameterAnnotations=[], method:createGraphics[] throws=[] annotations=[] parameterAnnotations=[], method:endpointBearingDegrees[] throws=[] annotations=[] parameterAnnotations=[], method:featureGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:featureId[] throws=[] annotations=[] parameterAnnotations=[], method:inheritedOpacity[] throws=[] annotations=[] parameterAnnotations=[], method:mapScreenBasis[] throws=[] annotations=[] parameterAnnotations=[], method:mapToDisplayOperation[] throws=[] annotations=[] parameterAnnotations=[], method:markerAnchorScreen[] throws=[] annotations=[] parameterAnnotations=[], method:renderChild[io.github.mundanej.map.api.Symbol, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:renderGeometry[] throws=[] annotations=[] parameterAnnotations=[], method:role[] throws=[] annotations=[] parameterAnnotations=[], method:sourceToScreen[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:viewport[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.AwtSymbolRenderer sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:hitTest[io.github.mundanej.map.api.Symbol, io.github.mundanej.map.awt.AwtSymbolHitContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:render[io.github.mundanej.map.api.Symbol, io.github.mundanej.map.awt.AwtSymbolRenderContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:supports[io.github.mundanej.map.api.Symbol] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.awt.HorizontalWrapMode sealed=false permits=[] record=[] enum=[NONE, REPEAT_X] annotations=[] members=[field:NONE[], field:REPEAT_X[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.MapLayerBinding sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:borrowedElevation[java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.awt.RasterRenderOptions, io.github.mundanej.map.api.RasterRequestLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:borrowedElevation[java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:borrowedFeature[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:borrowedFeature[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.MarkerSymbol, io.github.mundanej.map.api.LineSymbol, io.github.mundanej.map.api.FillSymbol] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:borrowedRaster[java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource, io.github.mundanej.map.awt.RasterRenderOptions] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:borrowedRaster[java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:cancelCurrentOperation[] throws=[] annotations=[] parameterAnnotations=[], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:editableFeature[java.lang.String, java.lang.String, io.github.mundanej.map.core.FeatureEditSession, io.github.mundanej.map.api.FeaturePortrayal] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:editableFeature[java.lang.String, java.lang.String, io.github.mundanej.map.core.FeatureEditSession, io.github.mundanej.map.api.MarkerSymbol, io.github.mundanej.map.api.LineSymbol, io.github.mundanej.map.api.FillSymbol] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:horizontalWrapMode[] throws=[] annotations=[] parameterAnnotations=[], method:id[] throws=[] annotations=[] parameterAnnotations=[], method:isClosed[] throws=[] annotations=[] parameterAnnotations=[], method:name[] throws=[] annotations=[] parameterAnnotations=[], method:ownedElevation[java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle, io.github.mundanej.map.awt.RasterRenderOptions, io.github.mundanej.map.api.RasterRequestLimits] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:ownedElevation[java.lang.String, java.lang.String, io.github.mundanej.map.api.ElevationSource, io.github.mundanej.map.api.ElevationRasterStyle] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:ownedFeature[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.FeaturePortrayal] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:ownedFeature[java.lang.String, java.lang.String, io.github.mundanej.map.api.FeatureSource, io.github.mundanej.map.api.MarkerSymbol, io.github.mundanej.map.api.LineSymbol, io.github.mundanej.map.api.FillSymbol] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], [], []], method:ownedRaster[java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource, io.github.mundanej.map.awt.RasterRenderOptions] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:ownedRaster[java.lang.String, java.lang.String, io.github.mundanej.map.api.RasterSource] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:portrayedSnapshot[io.github.mundanej.map.api.Layer, io.github.mundanej.map.api.FeaturePortrayal] throws=[] annotations=[] parameterAnnotations=[[], []], method:setHorizontalWrapMode[io.github.mundanej.map.awt.HorizontalWrapMode] throws=[] annotations=[] parameterAnnotations=[[]], method:setPortrayalZoomRange[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:snapshot[io.github.mundanej.map.api.Layer] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.awt.MapView sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.Projection, io.github.mundanej.map.awt.SymbolRendererRegistry] throws=[] annotations=[] parameterAnnotations=[[], []], constructor:[io.github.mundanej.map.api.Projection] throws=[] annotations=[] parameterAnnotations=[[]], constructor:[io.github.mundanej.map.core.CrsRegistry, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.awt.SymbolRendererRegistry] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], constructor:[io.github.mundanej.map.core.CrsRegistry, io.github.mundanej.map.api.CrsDefinition, io.github.mundanej.map.api.CrsDefinition] throws=[] annotations=[] parameterAnnotations=[[], [], []], field:DEFAULT_HOVER_TOLERANCE_PIXELS[], field:DEFAULT_SELECTION_TOLERANCE_PIXELS[], method:activeTool[] throws=[] annotations=[] parameterAnnotations=[], method:addMapHoverListener[io.github.mundanej.map.api.MapHoverListener] throws=[] annotations=[] parameterAnnotations=[[]], method:addMapPointerListener[io.github.mundanej.map.api.MapPointerListener] throws=[] annotations=[] parameterAnnotations=[[]], method:addMapSelectionListener[io.github.mundanej.map.api.MapSelectionListener] throws=[] annotations=[] parameterAnnotations=[[]], method:addMapSourceReportListener[io.github.mundanej.map.api.MapSourceReportListener] throws=[] annotations=[] parameterAnnotations=[[]], method:addNotify[] throws=[] annotations=[] parameterAnnotations=[], method:captureVectorExportSnapshot[] throws=[] annotations=[] parameterAnnotations=[], method:captureVectorExportSnapshot[io.github.mundanej.map.api.VectorExportSnapshotLimits, io.github.mundanej.map.api.CancellationToken] throws=[] annotations=[] parameterAnnotations=[[], []], method:captureVectorExportSnapshot[io.github.mundanej.map.api.VectorExportSnapshotLimits] throws=[] annotations=[] parameterAnnotations=[[]], method:clearActiveTool[] throws=[] annotations=[] parameterAnnotations=[], method:clearHorizontalWrap[] throws=[] annotations=[] parameterAnnotations=[], method:clearSelection[] throws=[] annotations=[] parameterAnnotations=[], method:close[] throws=[] annotations=[] parameterAnnotations=[], method:displayCrs[] throws=[] annotations=[] parameterAnnotations=[], method:fitToData[double] throws=[] annotations=[] parameterAnnotations=[[]], method:hitTest[double, double, double] throws=[] annotations=[] parameterAnnotations=[[], [], []], method:horizontalWrap[] throws=[] annotations=[] parameterAnnotations=[], method:hoverOverlaySymbols[] throws=[] annotations=[] parameterAnnotations=[], method:hover[] throws=[] annotations=[] parameterAnnotations=[], method:layerBindings[] throws=[] annotations=[] parameterAnnotations=[], method:layers[] throws=[] annotations=[] parameterAnnotations=[], method:mapCrs[] throws=[] annotations=[] parameterAnnotations=[], method:mapToScreen[io.github.mundanej.map.api.Coordinate] throws=[] annotations=[] parameterAnnotations=[[]], method:paintComponent[java.awt.Graphics] throws=[] annotations=[] parameterAnnotations=[[]], method:processKeyBinding[javax.swing.KeyStroke, java.awt.event.KeyEvent, int, boolean] throws=[] annotations=[] parameterAnnotations=[[], [], [], []], method:removeMapHoverListener[io.github.mundanej.map.api.MapHoverListener] throws=[] annotations=[] parameterAnnotations=[[]], method:removeMapPointerListener[io.github.mundanej.map.api.MapPointerListener] throws=[] annotations=[] parameterAnnotations=[[]], method:removeMapSelectionListener[io.github.mundanej.map.api.MapSelectionListener] throws=[] annotations=[] parameterAnnotations=[[]], method:removeMapSourceReportListener[io.github.mundanej.map.api.MapSourceReportListener] throws=[] annotations=[] parameterAnnotations=[[]], method:removeNotify[] throws=[] annotations=[] parameterAnnotations=[], method:screenToMap[double, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:selectionOverlaySymbols[] throws=[] annotations=[] parameterAnnotations=[], method:selection[] throws=[] annotations=[] parameterAnnotations=[], method:setActiveTool[io.github.mundanej.map.api.MapTool] throws=[] annotations=[] parameterAnnotations=[[]], method:setElevationRasterStyle[java.lang.String, io.github.mundanej.map.api.ElevationRasterStyle] throws=[] annotations=[] parameterAnnotations=[[], []], method:setEnabled[boolean] throws=[] annotations=[] parameterAnnotations=[[]], method:setHorizontalWrap[io.github.mundanej.map.core.HorizontalWrap] throws=[] annotations=[] parameterAnnotations=[[]], method:setHoverOverlaySymbols[io.github.mundanej.map.api.FeatureOverlaySymbols] throws=[] annotations=[] parameterAnnotations=[[]], method:setLayerBindings[java.util.List<io.github.mundanej.map.awt.MapLayerBinding>] throws=[] annotations=[] parameterAnnotations=[[]], method:setLayers[java.util.List<io.github.mundanej.map.api.Layer>] throws=[] annotations=[] parameterAnnotations=[[]], method:setRasterRenderOptions[java.lang.String, io.github.mundanej.map.awt.RasterRenderOptions] throws=[] annotations=[] parameterAnnotations=[[], []], method:setSelectionOverlaySymbols[io.github.mundanej.map.api.FeatureOverlaySymbols] throws=[] annotations=[] parameterAnnotations=[[]], method:setSelection[io.github.mundanej.map.api.FeatureSelection] throws=[] annotations=[] parameterAnnotations=[[]], method:setViewport[io.github.mundanej.map.core.MapViewport] throws=[] annotations=[] parameterAnnotations=[[]], method:sourceReports[] throws=[] annotations=[] parameterAnnotations=[], method:viewport[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.MeasurementTool sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.DistanceStrategy, int] throws=[] annotations=[] parameterAnnotations=[[], []], constructor:[io.github.mundanej.map.api.DistanceStrategy] throws=[] annotations=[] parameterAnnotations=[[]], field:DEFAULT_VERTEX_LIMIT[], method:cursorIntent[] throws=[] annotations=[] parameterAnnotations=[], method:distanceStrategy[] throws=[] annotations=[] parameterAnnotations=[], method:onActivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onDeactivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onMapToolCommand[io.github.mundanej.map.api.MapToolCommandEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:onMapToolEvent[io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:state[] throws=[] annotations=[] parameterAnnotations=[], method:vertexLimit[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.PointEditController sealed=false permits=[] record=[] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.awt.MapView, io.github.mundanej.map.awt.MapLayerBinding, io.github.mundanej.map.api.SnapReferenceSet, io.github.mundanej.map.api.SnapLimits, double] throws=[] annotations=[] parameterAnnotations=[[], [], [], [], []], constructor:[io.github.mundanej.map.awt.MapView, io.github.mundanej.map.awt.MapLayerBinding] throws=[] annotations=[] parameterAnnotations=[[], []], field:DEFAULT_SNAP_TOLERANCE_PIXELS[], method:addResultListener[java.util.function.Consumer<io.github.mundanej.map.api.FeatureEditResult>] throws=[] annotations=[] parameterAnnotations=[[]], method:clearMode[] throws=[] annotations=[] parameterAnnotations=[], method:create[io.github.mundanej.map.api.PointFeatureDraft] throws=[] annotations=[] parameterAnnotations=[[]], method:cursorIntent[] throws=[] annotations=[] parameterAnnotations=[], method:deleteSelected[] throws=[] annotations=[] parameterAnnotations=[], method:lastResult[] throws=[] annotations=[] parameterAnnotations=[], method:mode[] throws=[] annotations=[] parameterAnnotations=[], method:moveSelected[] throws=[] annotations=[] parameterAnnotations=[], method:onActivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onDeactivate[io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[]], method:onMapToolEvent[io.github.mundanej.map.api.MapToolEvent, io.github.mundanej.map.api.MapToolContext] throws=[] annotations=[] parameterAnnotations=[[], []], method:redo[] throws=[] annotations=[] parameterAnnotations=[], method:removeResultListener[java.util.function.Consumer<io.github.mundanej.map.api.FeatureEditResult>] throws=[] annotations=[] parameterAnnotations=[[]], method:undo[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.PointEditController$Mode sealed=false permits=[] record=[] enum=[NONE, CREATE, MOVE_SELECTED] annotations=[] members=[field:CREATE[], field:MOVE_SELECTED[], field:NONE[], method:valueOf[java.lang.String] throws=[] annotations=[] parameterAnnotations=[[]], method:values[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.RasterRenderOptions sealed=false permits=[] record=[interpolation:io.github.mundanej.map.api.RasterInterpolation[], opacity:double[]] enum=[] annotations=[] members=[constructor:[io.github.mundanej.map.api.RasterInterpolation, double] throws=[] annotations=[] parameterAnnotations=[[], []], method:defaults[] throws=[] annotations=[] parameterAnnotations=[], method:equals[java.lang.Object] throws=[] annotations=[] parameterAnnotations=[[]], method:hashCode[] throws=[] annotations=[] parameterAnnotations=[], method:interpolation[] throws=[] annotations=[] parameterAnnotations=[], method:opacity[] throws=[] annotations=[] parameterAnnotations=[], method:toString[] throws=[] annotations=[] parameterAnnotations=[], method:withInterpolation[io.github.mundanej.map.api.RasterInterpolation] throws=[] annotations=[] parameterAnnotations=[[]], method:withOpacity[double] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.awt.SymbolRenderResult sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:markerBounds[io.github.mundanej.map.api.Envelope, io.github.mundanej.map.awt.AwtLogicalPaintPresence] throws=[] annotations=[] parameterAnnotations=[[], []], method:markerBounds[io.github.mundanej.map.api.Envelope] throws=[] annotations=[] parameterAnnotations=[[]], method:nominalMarkerBounds[] throws=[] annotations=[] parameterAnnotations=[], method:none[] throws=[] annotations=[] parameterAnnotations=[], method:none[io.github.mundanej.map.awt.AwtLogicalPaintPresence] throws=[] annotations=[] parameterAnnotations=[[]], method:paintPresence[] throws=[] annotations=[] parameterAnnotations=[], method:union[io.github.mundanej.map.awt.SymbolRenderResult] throws=[] annotations=[] parameterAnnotations=[[]]]
SHAPE io.github.mundanej.map.awt.SymbolRendererRegistry sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:builderWithBuiltIns[] throws=[] annotations=[] parameterAnnotations=[], method:builder[] throws=[] annotations=[] parameterAnnotations=[], method:builtIn[] throws=[] annotations=[] parameterAnnotations=[]]
SHAPE io.github.mundanej.map.awt.SymbolRendererRegistry$Builder sealed=false permits=[] record=[] enum=[] annotations=[] members=[method:build[] throws=[] annotations=[] parameterAnnotations=[], method:register[io.github.mundanej.map.api.SymbolRole, io.github.mundanej.map.api.SymbolRendererKey, io.github.mundanej.map.awt.AwtSymbolRenderer] throws=[] annotations=[] parameterAnnotations=[[], [], []]]
