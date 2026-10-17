package dev.prabodh.examedge.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import dev.prabodh.examedge.dto.Views.AttemptResultView;
import dev.prabodh.examedge.dto.Views.AttemptSummary;
import dev.prabodh.examedge.dto.Views.ReviewItem;
import dev.prabodh.examedge.dto.Views.ReviewOption;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class PdfService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public byte[] renderResult(AttemptResultView result) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            AttemptSummary summary = result.summary();
            document.add(new Paragraph("ExamEdge Result Report", titleFont));
            document.add(new Paragraph(summary.examTitle(), headingFont));
            document.add(new Paragraph(" "));

            PdfPTable details = new PdfPTable(2);
            details.setWidthPercentage(100);
            addDetail(details, "Student", summary.studentName(), bodyFont);
            addDetail(details, "Score", summary.score() + " / " + summary.totalMarks(), bodyFont);
            addDetail(details, "Percentage", summary.percentage() + "%", bodyFont);
            addDetail(details, "Outcome", summary.passed() ? "Passed" : "Failed", bodyFont);
            addDetail(details, "Passing percentage", result.passingPercentage() + "%", bodyFont);
            addDetail(details, "Status", summary.status().name(), bodyFont);
            addDetail(details, "Started", TIME.format(summary.startedAt()), bodyFont);
            addDetail(details, "Submitted", TIME.format(summary.submittedAt()), bodyFont);
            addDetail(details, "Time taken", summary.timeTakenText(), bodyFont);
            document.add(details);

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Question review", headingFont));
            for (ReviewItem item : result.items()) {
                document.add(new Paragraph(" "));
                String verdict = !item.answered() ? "Not answered" : item.correct() ? "Correct" : "Incorrect";
                document.add(new Paragraph("Q" + item.number() + ". " + item.questionText()
                        + "  [" + item.awarded() + "/" + item.marks() + " marks - " + verdict + "]", headingFont));
                for (ReviewOption option : item.options()) {
                    String marker = option.correct() ? "[correct] " : "";
                    String chosen = option.chosen() ? "(your answer) " : "";
                    document.add(new Paragraph("    " + marker + chosen + option.text(), bodyFont));
                }
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("The PDF could not be generated", e);
        }
        return output.toByteArray();
    }

    private void addDetail(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        labelCell.setPadding(5);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}
