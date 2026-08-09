package io.github.mundanej.map.example.vaadin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the offline-by-default Vaadin viewer application. */
@SpringBootApplication
public final class VaadinViewerApplication {
    /** Creates the Spring configuration bean. */
    public VaadinViewerApplication() {}

    /**
     * Starts the embedded loopback web server.
     *
     * @param arguments Spring Boot command-line arguments
     */
    public static void main(String[] arguments) {
        application().run(arguments);
    }

    static SpringApplication application() {
        SpringApplication application = new SpringApplication(VaadinViewerApplication.class);
        application.setDefaultProperties(
                java.util.Map.of(
                        "server.address", "127.0.0.1",
                        "vaadin.launch-browser", "false",
                        "vaadin.react.enable", "false"));
        return application;
    }
}
