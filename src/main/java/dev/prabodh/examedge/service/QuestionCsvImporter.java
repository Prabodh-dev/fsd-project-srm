package dev.prabodh.examedge.service;

import dev.prabodh.examedge.util.CsvLines;
import dev.prabodh.examedge.util.QuestionDraft;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QuestionCsvImporter {

    public static final int MAX_ROWS = 500;
    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
    private static final int COLUMN_COUNT = 9;
    private static final int FIRST_OPTION_COLUMN = 2;
    private static final int CORRECT_COLUMN = 8;

    public record RowError(int rowNumber, String message) {
    }

    public record ImportResult(List<QuestionDraft> validDrafts, List<RowError> errors, int rowCount) {
    }

    public ImportResult parse(InputStream input) throws IOException {
        List<QuestionDraft> drafts = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int rowCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == BYTE_ORDER_MARK) {
                    line = line.substring(1);
                }
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = CsvLines.split(line);
                if (lineNumber == 1 && "question".equalsIgnoreCase(cells.get(0).trim())) {
                    continue;
                }
                rowCount++;
                if (rowCount > MAX_ROWS) {
                    errors.add(new RowError(lineNumber, "Import is limited to " + MAX_ROWS + " rows."));
                    break;
                }
                parseRow(lineNumber, cells, drafts, errors);
            }
        }
        return new ImportResult(drafts, errors, rowCount);
    }

    private void parseRow(int lineNumber, List<String> cells, List<QuestionDraft> drafts, List<RowError> errors) {
        if (cells.size() > COLUMN_COUNT) {
            errors.add(new RowError(lineNumber, "Expected at most " + COLUMN_COUNT + " columns but found " + cells.size() + "."));
            return;
        }
        List<String> padded = new ArrayList<>(cells);
        while (padded.size() < COLUMN_COUNT) {
            padded.add("");
        }
        Integer marks = parseNumber(padded.get(1));
        if (marks == null) {
            errors.add(new RowError(lineNumber, "Marks must be a whole number."));
            return;
        }
        Integer correctNumber = parseNumber(padded.get(CORRECT_COLUMN));
        if (correctNumber == null || correctNumber < 1 || correctNumber > QuestionDraft.MAX_OPTIONS) {
            errors.add(new RowError(lineNumber, "Correct must be a number from 1 to " + QuestionDraft.MAX_OPTIONS + "."));
            return;
        }
        List<String> rawOptions = padded.subList(FIRST_OPTION_COLUMN, FIRST_OPTION_COLUMN + QuestionDraft.MAX_OPTIONS);
        QuestionDraft draft = QuestionDraft.fromRaw(padded.get(0), marks, rawOptions, correctNumber - 1);
        List<String> problems = draft.problems();
        if (problems.isEmpty()) {
            drafts.add(draft);
        } else {
            errors.add(new RowError(lineNumber, String.join(" ", problems)));
        }
    }

    private Integer parseNumber(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
