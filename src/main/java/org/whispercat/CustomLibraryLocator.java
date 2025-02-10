package org.whispercat;

import com.github.kwhat.jnativehook.NativeLibraryLocator;
import com.github.kwhat.jnativehook.NativeSystem;

import java.io.File;
import java.util.Iterator;
import java.util.List;

public class CustomLibraryLocator implements NativeLibraryLocator {
    @Override
    public Iterator<File> getLibraries() {
        System.out.println("Get libraries");
        String libName = System.getProperty("jnativehook.lib.name", "JNativeHook");
        String libNativeArch = NativeSystem.getArchitecture().toString().toLowerCase();
        String libNativeName = System
                .mapLibraryName(libName)
                .replaceAll("\\.jnilib$", "\\.dylib");

        String baseDir = getWritableDirectory();

        String libFilePath = baseDir + File.separator + NativeSystem.getFamily().toString().toLowerCase()
                + File.separator + libNativeArch + File.separator + libNativeName;
        File libFile = new File(libFilePath);

        if (!libFile.exists()) {
            throw new RuntimeException("Unable to locate JNI library at " + libFile.getPath() + "!");
        }

        return List.of(libFile).iterator();
    }

    public String getWritableDirectory() {
        String userHome = System.getProperty("user.home");
        String configDir;

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            configDir = System.getenv("APPDATA") + "/WhisperCat";
        } else {
            configDir = userHome + "/WhisperCat/.config";
        }

        return configDir;
    }
}