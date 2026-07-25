package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.file.Path;

/** Separate-JVM deployment probe used to verify Xerial loader failure translation. */
public final class GeoPackageDeploymentProbe {
    private GeoPackageDeploymentProbe() {}

    /** Opens one supplied strict fixture and prints only the stable terminal contract. */
    public static void main(String[] arguments) {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one fixture path");
        }
        try {
            GeoPackages.inspect(
                    Path.of(arguments[0]),
                    new SourceIdentity("deployment-probe", ""),
                    GeoPackageInspectOptions.defaults(),
                    CancellationToken.none());
            System.out.println("SUCCESS");
        } catch (SourceException failure) {
            System.out.println(
                    failure.terminal().code()
                            + "|"
                            + failure.terminal().context().getOrDefault("reason", ""));
        }
    }
}
