package io.github.mundanej.map.buildlogic;

/** Fixture containing visible and hidden nested types for publication-surface verification. */
public final class PublicationSurfaceFixture {
    private PublicationSurfaceFixture() {}

    /** Public nested type that requires generated Javadocs. */
    public static final class PublicNested {
        private PublicNested() {}
    }

    /** Protected nested type that requires generated Javadocs. */
    protected static final class ProtectedNested {
        private ProtectedNested() {}
    }

    static final class PackageNested {
        private PackageNested() {}
    }

    private static final class PrivateNested {
        private PrivateNested() {}
    }
}
