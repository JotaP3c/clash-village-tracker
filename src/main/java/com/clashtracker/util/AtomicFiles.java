package com.clashtracker.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static Path temporarySibling(Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        return absolute.resolveSibling("." + absolute.getFileName() + "." + UUID.randomUUID() + ".tmp");
    }

    public static void replace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
