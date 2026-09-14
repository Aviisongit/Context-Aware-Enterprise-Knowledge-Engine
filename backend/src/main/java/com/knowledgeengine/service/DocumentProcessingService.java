package com.knowledgeengine.service;

import com.knowledgeengine.model.DocumentSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentProcessingService {

    private static final int CHUNK_SIZE = 900;
    private static final int CHUNK_OVERLAP = 150;

    public ParsedDocument parseDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        return parseDocument(file.getBytes(), filename == null ? "" : filename);
    }

    public ParsedDocument parseDocument(byte[] content, String filename) throws IOException {
        String extension = filename == null ? "" : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String rawText = extractText(content, extension);
        String cleanedText = normalizeWhitespace(rawText);
        List<Chunk> chunks = chunkText(cleanedText);

        return new ParsedDocument(filename, resolveSource(extension), cleanedText, chunks);
    }

    private String extractText(byte[] content, String extension) throws IOException {
        if ("md".equals(extension)) {
            return new String(content, StandardCharsets.UTF_8);
        }
        if ("pdf".equals(extension)) {
            try (PDDocument document = Loader.loadPDF(content)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        }

        return new String(content, StandardCharsets.UTF_8);
    }

    private String normalizeWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r\n", "\n").replace("\r", "\n").replace("\t", " ");
    }

    private List<Chunk> chunkText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] paragraphs = text.split("\\n\\s*\\n+");
        List<String> blocks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                blocks.add(trimmed);
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(text.trim());
        }

        List<Chunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String sectionName = "document";

        for (String block : blocks) {
            String normalizedBlock = block.strip();
            String computedSection = extractSectionName(normalizedBlock);
            if (computedSection != null) {
                sectionName = computedSection;
            }

            if (current.length() > 0 && current.length() + normalizedBlock.length() + 1 > CHUNK_SIZE) {
                chunks.add(new Chunk(current.toString().trim(), sectionName));
                current = new StringBuilder();
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(normalizedBlock);

            if (current.length() >= CHUNK_SIZE) {
                chunks.add(new Chunk(current.toString().trim(), sectionName));
                current = new StringBuilder();
            }
        }

        if (current.length() > 0) {
            chunks.add(new Chunk(current.toString().trim(), sectionName));
        }

        return applyOverlap(chunks);
    }

    private List<Chunk> applyOverlap(List<Chunk> chunks) {
        List<Chunk> overlapped = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            String text = chunk.text();
            String sectionName = chunk.sectionName();

            if (text.length() <= CHUNK_SIZE) {
                overlapped.add(new Chunk(text, sectionName));
                continue;
            }

            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + CHUNK_SIZE, text.length());
                String slice = text.substring(start, end).trim();
                if (!slice.isEmpty()) {
                    overlapped.add(new Chunk(slice, sectionName));
                }
                start += CHUNK_SIZE - CHUNK_OVERLAP;
            }
        }
        return overlapped;
    }

    private String extractSectionName(String block) {
        String[] lines = block.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceAll("^#+\\s*", "").trim();
            }
        }
        return null;
    }

    private DocumentSource resolveSource(String extension) {
        return switch (extension) {
            case "md" -> DocumentSource.MARKDOWN;
            case "pdf" -> DocumentSource.PDF;
            default -> DocumentSource.TEXT;
        };
    }

    public record ParsedDocument(String filename, DocumentSource source, String content, List<Chunk> chunks) {
    }

    public record Chunk(String text, String sectionName) {
    }
}
