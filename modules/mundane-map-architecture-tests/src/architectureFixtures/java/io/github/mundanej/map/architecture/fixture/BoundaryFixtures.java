package io.github.mundanej.map.architecture.fixture;

import com.example.external.ExternalType;
import io.github.mundanej.map.api.symbol.ApiSubpackageType;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.Point;

public final class BoundaryFixtures {
    private BoundaryFixtures() {}

    public static final class ApiToCore {
        public MapViewport viewport() {
            return MapViewport.initial(1, 1);
        }
    }

    public static final class NonAwtToDesktop {
        public Point point() {
            return new Point();
        }
    }

    public static final class IoToAwt {
        public MapView mapView() {
            return new MapView(new WebMercatorProjection());
        }
    }

    public static final class IoToDesktop {
        public Point point() {
            return new Point();
        }
    }

    public static final class AwtToCore {
        public MapViewport viewport() {
            return MapViewport.initial(1, 1);
        }
    }

    public static final class PublicApiLeak {
        public ExternalType externalType() {
            return new ExternalType();
        }
    }

    public static final class PublicApiSubpackageUse {
        public ApiSubpackageType value() {
            return new ApiSubpackageType();
        }
    }
}
