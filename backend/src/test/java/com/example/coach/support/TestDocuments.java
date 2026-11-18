package com.example.coach.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

/** Generates real PDF and DOCX files in memory, so tests need no binary fixtures. */
public final class TestDocuments {

    public static final String RESUME = """
            Asha Verma
            asha.verma@example.com | +91 98765 43210 | linkedin.com/in/ashaverma
            12 MG Road, Bengaluru - 560001

            Senior Java Developer, Acme Payments (2020 - 2025)
            - Built Spring Boot microservices processing 2 million payments per day
            - Migrated a monolith to Kubernetes, cutting deployment time by 60%
            - Mentored 4 junior engineers and ran weekly code reviews

            Skills: Java, Spring Boot, PostgreSQL, Docker, Kubernetes, AWS
            Education: B.Tech Computer Science
            """;

    public static final String JOB = """
            Backend Engineer (Java) at Globex
            We are hiring a backend engineer with 4+ years of experience to build our payments platform.
            Requirements: Java, Spring Boot, PostgreSQL, Kafka, Kubernetes. Nice to have: Terraform, GraphQL.
            You will design APIs, own services in production and mentor engineers.
            """;

    private TestDocuments() {}

    public static byte[] pdf(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        for (String line : text.split("\n")) {
            document.add(new Paragraph(line.isEmpty() ? " " : line));
        }
        document.close();
        return out.toByteArray();
    }

    public static byte[] docx(String text) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : text.split("\n")) {
                document.createParagraph().createRun().setText(line);
            }
            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
