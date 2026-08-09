package io.github.mundanej.map.example.vaadin;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.spring.SpringServlet;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

/** Starts the offline-by-default Vaadin viewer application. */
@SpringBootApplication
@Push(PushMode.AUTOMATIC)
@SuppressWarnings("serial")
public final class VaadinViewerApplication implements AppShellConfigurator {
    private static final long MAXIMUM_UPLOAD_FILE_BYTES = 16L * 1024 * 1024;
    private static final long MAXIMUM_UPLOAD_REQUEST_BYTES = 33L * 1024 * 1024;

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

    @Bean
    static BeanPostProcessor boundedVaadinMultipartRegistration() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ServletRegistrationBean<?> registration
                        && registration.getServlet() instanceof SpringServlet) {
                    registration.setMultipartConfig(
                            new MultipartConfigElement(
                                    "",
                                    MAXIMUM_UPLOAD_FILE_BYTES,
                                    MAXIMUM_UPLOAD_REQUEST_BYTES,
                                    0));
                }
                return bean;
            }
        };
    }
}
