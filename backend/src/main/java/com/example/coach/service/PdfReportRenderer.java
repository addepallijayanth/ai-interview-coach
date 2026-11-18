package com.example.coach.service;

import com.example.coach.ai.model.StudyPlan;
import com.example.coach.dto.InterviewDtos.AnswerFeedback;
import com.example.coach.dto.InterviewDtos.TurnResponse;
import com.example.coach.dto.ReportDtos.CategoryScore;
import com.example.coach.dto.ReportDtos.ReportResponse;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.ListItem;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/** Renders the interview report as a PDF with OpenPDF. */
@Component
public class PdfReportRenderer {

    private static final Color ACCENT = new Color(79, 70, 229);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color HEADER_BG = new Color(238, 242, 255);
    private static final Color BORDER = new Color(226, 232, 240);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, ACCENT);
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.DARK_GRAY);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
    private static final Font STAT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, ACCENT);
    private static final Font CATEGORY = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, ACCENT);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneOffset.UTC);

    public byte[] render(ReportResponse report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 56, 64);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new Footer());
        document.addTitle("Interview report: " + safe(report.jobTitle()));
        document.addCreator("AI Interview Coach");
        document.open();

        document.add(new Paragraph("Interview Coach Report", TITLE));
        String role = report.jobTitle() + (report.company() == null ? "" : " at " + report.company());
        String level = "  |  " + report.levelLabel() + " interview";
        String date = report.endedAt() == null ? "" : "  |  " + DATE.format(report.endedAt());
        document.add(spaced(new Paragraph(safe(role) + level + date, SMALL), 2, 14));

        document.add(stats(report));

        if (!report.categoryScores().isEmpty()) {
            heading(document, "Score by category");
            document.add(categoryTable(report.categoryScores()));
        }

        heading(document, "Skills");
        document.add(labelled("Matched: ", report.matchedSkills().isEmpty() ? "none identified" : String.join(", ", report.matchedSkills())));
        document.add(labelled("To develop: ", report.missingSkills().isEmpty() ? "no gaps found" : String.join(", ", report.missingSkills())));

        if (report.summary() != null) {
            heading(document, "Interviewer summary");
            document.add(new Paragraph(safe(report.summary()), BODY));
        }

        if (!report.turns().isEmpty()) {
            heading(document, "Question by question");
            document.add(questionTable(report.turns()));
        }

        StudyPlan plan = report.studyPlan();
        if (plan != null) {
            heading(document, "What to study next");
            document.add(spaced(new Paragraph(safe(plan.summary()), BODY), 0, 6));
            for (StudyPlan.FocusArea area : plan.focusAreas()) {
                Paragraph title = new Paragraph();
                title.add(new Chunk(safe(area.topic()), BOLD));
                title.add(new Chunk("  (" + area.priority() + " priority)", SMALL));
                document.add(spaced(title, 6, 2));
                document.add(new Paragraph(safe(area.why()), BODY));
                document.add(bullets(area.actions()));
                if (!area.resources().isEmpty()) {
                    document.add(new Paragraph("Resources: " + safe(String.join("; ", area.resources())), SMALL));
                }
            }
        }

        document.close();
        return out.toByteArray();
    }

    private static PdfPTable stats(ReportResponse report) {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.addCell(stat(report.matchScore() + "/100", "Resume match"));
        table.addCell(stat(report.overallScore() == null ? "-" : report.overallScore().stripTrailingZeros().toPlainString() + "/10",
                "Interview score"));
        table.addCell(stat(report.questionsAnswered() + " of " + report.questionTarget(), "Questions answered"));
        table.setSpacingAfter(8);
        return table;
    }

    private static PdfPCell stat(String value, String label) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph number = new Paragraph(value, STAT);
        number.setAlignment(Element.ALIGN_CENTER);
        Paragraph caption = new Paragraph(label, SMALL);
        caption.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(number);
        cell.addElement(caption);
        return cell;
    }

    private static PdfPTable categoryTable(List<CategoryScore> scores) {
        PdfPTable table = new PdfPTable(new float[] {4f, 2f, 2f});
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);
        for (String header : List.of("Category", "Answered", "Average")) {
            table.addCell(headerCell(header));
        }
        for (CategoryScore score : scores) {
            table.addCell(cell(score.label()));
            table.addCell(cell(score.scored() + " of " + score.asked()));
            table.addCell(cell(score.averageScore() == null ? "-"
                    : score.averageScore().stripTrailingZeros().toPlainString() + "/10"));
        }
        return table;
    }

    private static PdfPTable questionTable(List<TurnResponse> turns) {
        PdfPTable table = new PdfPTable(new float[] {0.5f, 4.6f, 0.9f, 4.4f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : List.of("#", "Question", "Score", "Feedback")) {
            table.addCell(headerCell(header));
        }
        for (TurnResponse turn : turns) {
            table.addCell(cell(String.valueOf(turn.questionNo())));
            table.addCell(questionCell(turn));
            table.addCell(cell(turn.score() == null ? "-" : turn.score() + "/10"));
            table.addCell(cell(feedbackText(turn.feedback())));
        }
        return table;
    }

    private static PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BOLD));
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorderColor(BORDER);
        cell.setPadding(6);
        return cell;
    }

    /** The question, with its category above it in small accent text. */
    private static PdfPCell questionCell(TurnResponse turn) {
        Phrase phrase = new Phrase();
        if (turn.categoryLabel() != null) {
            phrase.add(new Chunk(safe(turn.categoryLabel()).toUpperCase(Locale.ROOT) + "\n", CATEGORY));
        }
        phrase.add(new Chunk(safe(turn.question()), BODY));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorderColor(BORDER);
        cell.setPadding(6);
        return cell;
    }

    private static String feedbackText(AnswerFeedback feedback) {
        if (feedback == null) {
            return "Not scored";
        }
        StringBuilder text = new StringBuilder();
        if (!feedback.strengths().isEmpty()) {
            text.append("+ ").append(String.join("\n+ ", feedback.strengths()));
        }
        if (!feedback.weaknesses().isEmpty()) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append("- ").append(String.join("\n- ", feedback.weaknesses()));
        }
        return text.toString();
    }

    private static PdfPCell cell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), BODY));
        cell.setBorderColor(BORDER);
        cell.setPadding(6);
        return cell;
    }

    private static org.openpdf.text.List bullets(List<String> items) {
        org.openpdf.text.List list = new org.openpdf.text.List(false, 12);
        list.setListSymbol(new Chunk("•", BODY));
        items.forEach(item -> list.add(new ListItem(safe(item), BODY)));
        return list;
    }

    private static void heading(Document document, String text) {
        document.add(spaced(new Paragraph(text, HEADING), 14, 6));
    }

    private static Paragraph labelled(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label, BOLD));
        paragraph.add(new Chunk(safe(value), BODY));
        paragraph.setSpacingAfter(3);
        return paragraph;
    }

    private static Paragraph spaced(Paragraph paragraph, float before, float after) {
        paragraph.setSpacingBefore(before);
        paragraph.setSpacingAfter(after);
        return paragraph;
    }

    /** The standard PDF fonts only cover Latin-1, so map common typography and drop anything else. */
    static String safe(String text) {
        if (text == null) {
            return "";
        }
        String mapped = text.replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-').replace("…", "...").replace('•', '-')
                .replace("→", "->");
        StringBuilder out = new StringBuilder(mapped.length());
        mapped.codePoints().forEach(cp -> out.append(cp == '\n' || (cp >= 0x20 && cp <= 0xFF) ? Character.toString(cp) : "?"));
        return out.toString();
    }

    private static final class Footer extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase("AI Interview Coach  |  AI-generated feedback for practice, not a hiring decision  |  Page "
                            + writer.getPageNumber(), SMALL),
                    page.getWidth() / 2, 30, 0);
        }
    }
}
