package io.github.mundanej.map.architecture.fixture;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import sun.misc.Unsafe;

public final class MechanismFixtures {
    private MechanismFixtures() {}

    public static final class ReflectionUse {
        public Object invoke(Method method, Object receiver) throws ReflectiveOperationException {
            return method.invoke(receiver);
        }
    }

    public static final class ReflectionEnumerationUse {
        public Method[] methods(Class<?> type) {
            return type.getMethods();
        }
    }

    public static final class ClassMetadataUse {
        public boolean deprecated(Class<?> type) {
            return type.isAnnotationPresent(Deprecated.class);
        }
    }

    public static final class MethodHandleUse {
        private final MethodHandle handle;

        public MethodHandleUse(MethodHandle handle) {
            this.handle = handle;
        }

        public MethodHandle handle() {
            return handle;
        }
    }

    public static final class DynamicProxyUse {
        public Object proxy() {
            return Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {Runnable.class},
                    (proxy, method, arguments) -> null);
        }
    }

    public static final class DynamicClassLoadingUse {
        public Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
            Class.forName(name);
            return loader.loadClass(name);
        }
    }

    public static final class DynamicDefinitionUse extends ClassLoader {
        public Class<?> define(byte[] bytecode) {
            return defineClass(null, bytecode, 0, bytecode.length);
        }
    }

    public static final class ServiceDiscoveryUse {
        public ServiceLoader<Runnable> services() {
            return ServiceLoader.load(Runnable.class);
        }
    }

    public static final class SerializationUse implements Serializable {
        private static final long serialVersionUID = 1L;

        public Object read(InputStream input) throws IOException, ClassNotFoundException {
            try (ObjectInputStream stream = new ObjectInputStream(input)) {
                return stream.readObject();
            }
        }
    }

    public static final class ResourceEnumerationUse {
        public Enumeration<URL> resources(ClassLoader loader, String name) throws IOException {
            return loader.getResources(name);
        }
    }

    public static final class NativeMethodUse {
        public native void invoke();
    }

    public static final class UnsafeUse {
        public int addressSize(Unsafe unsafe) {
            return unsafe.addressSize();
        }
    }

    public static final class MemoryMappingUse {
        public MappedByteBuffer map(FileChannel channel) throws IOException {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, 1);
        }
    }

    public static final class NativeLibraryUse {
        public void load(String name) {
            System.loadLibrary(name);
        }
    }

    public static final class GlobalRegistryUse {
        private static final Map<String, Object> REGISTRY = new HashMap<>();

        public static void installValue(String key, Object value) {
            REGISTRY.put(key, value);
        }
    }

    public static final class StatelessRegistrationUse {
        public static Map<String, Object> registerDefaults() {
            return Map.of();
        }
    }

    public static final class ImmutableCatalogAndInstanceRegistryUse {
        private static final Map<String, Object> BUILT_INS = Map.of("default", new Object());
        private final Map<String, Object> entries = new HashMap<>();

        public void register(String key, Object value) {
            if (!BUILT_INS.containsKey(key)) {
                entries.put(key, value);
            }
        }
    }

    public static final class RendererRegistry {
        private final Map<String, Object> entries = new HashMap<>();

        public void registerRenderer(String key, Object value) {
            entries.put(key, value);
        }
    }

    public static final class CustomGlobalRegistryUse {
        private static final RendererRegistry REGISTRY = new RendererRegistry();

        public static void installValue(String key, Object value) {
            REGISTRY.registerRenderer(key, value);
        }
    }

    public static final class ExplicitResourceUse {
        public URL resource() {
            return ExplicitResourceUse.class.getResource("/known-resource.txt");
        }
    }

    public static final class ResourceWalkingUse {
        public long count(Path directory) throws IOException {
            try (var paths = Files.walk(directory)) {
                return paths.count();
            }
        }
    }

    public static final class ArbitraryDigestUse {
        public byte[] digest(String algorithm, byte[] value) throws NoSuchAlgorithmException {
            return MessageDigest.getInstance(algorithm).digest(value);
        }
    }
}
