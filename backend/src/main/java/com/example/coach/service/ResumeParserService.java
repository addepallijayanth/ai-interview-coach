package com.example.coach.service;

import com.example.coach.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

/**
 * Extracts plain text from PDF and DOCX resumes with Apache Tika. The file type is detected from the bytes
 * (magic numbers), not trusted from the file name or the client's Content-Type header.
 */
@Service
public class ResumeParserService {

    public static final String PDF = "application/pdf";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int MIN_TEXT_CHARS = 100;

    public record ParsedDocument(String text, String contentType) {}

    private final Tika detector = new Tika();
    private final Parser parser = new AutoDetectParser();

    public ParsedDocument parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("The file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large", "Resumes must be 2 MB or smaller.");
        }
        String contentType = detector.detect(bytes, fileName);
        if (!PDF.equals(contentType) && !DOCX.equals(contentType)) {
            throw ApiException.unsupportedFile("Upload your resume as a PDF or DOCX file.");
        }

        String text;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            parser.parse(in, handler, new Metadata(), new ParseContext());
            text = handler.toString();
        } catch (IOException | SAXException | TikaException e) {
            throw ApiException.badRequest("We couldn't read this file. Check that it isn't damaged or password-protected.");
        }

        text = normalize(text);
        if (text.length() < MIN_TEXT_CHARS) {
            throw ApiException.badRequest("We couldn't find text in this file. If it is a scanned image, "
                    + "export it as a text-based PDF or DOCX.");
        }
        return new ParsedDocument(text, contentType);
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(' ', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")
                .replaceAll("[ \t]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
