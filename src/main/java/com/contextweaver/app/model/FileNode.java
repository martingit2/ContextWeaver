package com.contextweaver.app.model;

import java.nio.file.Path;

/**
 * Model-klasse som representerer en node (fil eller mappe) i filtreet.
 * Holder på stien (Path) og definerer hvordan den skal vises i GUI-et.
 */
public class FileNode {
    private final Path path;

    public FileNode(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    @Override
    public String toString() {
        // Viser kun det siste elementet i stien (fil- eller mappenavnet) for et renere UI.
        return path.getFileName().toString();
    }
}