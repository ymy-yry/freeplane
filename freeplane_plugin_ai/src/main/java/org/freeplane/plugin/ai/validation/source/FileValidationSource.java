package org.freeplane.plugin.ai.validation.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File import data source — reads content from .mm/.json files.
 * 
 * <p>Hooks into MapRestController.handleImportMap for cycle-detection interception before import.
 */
public final class FileValidationSource implements ValidationSource {
    
    private final Path filePath;
    private final String filename;
    private String content; // cached content or content passed via the legacy constructor
    
    /**
     * @param content  the file content string (already read into memory)
     * @param filename the file name (used for logging)
     */
    public FileValidationSource(String content, String filename) {
        this.filePath = null;
        this.filename = filename;
        this.content = content;
    }
    
    /**
     * @param filePath   the file path
     * @param filename   the file name (used for logging)
     */
    public FileValidationSource(Path filePath, String filename) {
        this.filePath = filePath;
        this.filename = filename;
        this.content = null;
    }
    
    @Override
    public String readContent() throws IOException {
        if (content != null) {
            return content; // return cached content
        }
        if (filePath == null || !Files.exists(filePath)) {
            throw new IOException("File not found: " + filePath);
        }
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }
    
    @Override
    public SourceType getSourceType() {
        return SourceType.FILE_IMPORT;
    }
    
    @Override
    public boolean isReady() {
        if (content != null) {
            return true; // content is already cached
        }
        return filePath != null && Files.exists(filePath);
    }
    
    @Override
    public String getDescription() {
        return "file=" + filename;
    }
}
