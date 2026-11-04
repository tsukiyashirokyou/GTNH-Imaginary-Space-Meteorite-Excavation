package com.kyou.imaginaryspacemeteoriteexcavation.processing;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/** Test-only public class-loader/Loader.injectData bridge. No transformers or private access. */
public final class HeadlessForgeFixture {

    private HeadlessForgeFixture() {}

    public static boolean inside() {
        return HeadlessForgeFixture.class.getClassLoader() instanceof LaunchClassLoader;
    }

    public static synchronized void run(String testClass, String method) throws Exception {
        Set<URL> urls = new LinkedHashSet<>();
        for (ClassLoader current = HeadlessForgeFixture.class.getClassLoader(); current
            != null; current = current.getParent()) {
            if (current instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) current).getURLs()) urls.add(url);
            }
        }
        if (urls.isEmpty()) {
            for (String element : System.getProperty("java.class.path")
                .split(File.pathSeparator)) {
                urls.add(
                    new File(element).toURI()
                        .toURL());
            }
        }
        LaunchClassLoader original = Launch.classLoader;
        ClassLoader context = Thread.currentThread()
            .getContextClassLoader();
        try (LaunchClassLoader loader = new LaunchClassLoader(urls.toArray(new URL[0]))) {
            loader.addClassLoaderExclusion("org.junit.");
            loader.addClassLoaderExclusion("org.hamcrest.");
            Launch.classLoader = loader;
            Thread.currentThread()
                .setContextClassLoader(loader);
            File home = Files.createTempDirectory(new File("build").toPath(), "headless-forge-")
                .toFile();
            Class<?> fml = Class.forName("cpw.mods.fml.common.Loader", true, loader);
            Object[] data = { "7", "99", "40", "1614", "1.7.10", "test", home, new ArrayList<String>() };
            // Reflection invokes public entry points on classes in the isolated loader; no fields are bypassed.
            fml.getMethod("injectData", Object[].class)
                .invoke(null, new Object[] { data });
            Class<?> tests = Class.forName(testClass, true, loader);
            tests.getMethod("registerTestFixtures")
                .invoke(null);
            tests.getMethod(method)
                .invoke(
                    tests.getConstructor()
                        .newInstance());
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw new AssertionError(cause);
        } finally {
            Launch.classLoader = original;
            Thread.currentThread()
                .setContextClassLoader(context);
        }
    }
}
