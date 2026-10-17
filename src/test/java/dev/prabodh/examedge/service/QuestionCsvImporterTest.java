package dev.prabodh.examedge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.prabodh.examedge.service.QuestionCsvImporter.ImportResult;
import dev.prabodh.examedge.util.QuestionDraft;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class QuestionCsvImporterTest {

    private final QuestionCsvImporter importer = new QuestionCsvImporter();

    private ImportResult parse(String csv) throws IOException {
        return importer.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void importsValidRowsAndSkipsHeader() throws IOException {
        ImportResult result = parse("question,marks,option1,option2,option3,option4,option5,option6,correct\n"
                + "Capital of France?,2,Paris,Rome,Berlin,Madrid,,,1\n"
                + "2 + 2?,1,3,4,5,,,,2\n");
        assertEquals(2, result.validDrafts().size());
        assertTrue(result.errors().isEmpty());
        QuestionDraft first = result.validDrafts().get(0);
        assertEquals("Capital of France?", first.text());
        assertEquals(2, first.marks());
        assertEquals(4, first.options().size());
        assertEquals(0, first.correctIndex());
        assertEquals(1, result.validDrafts().get(1).correctIndex());
    }

    @Test
    void reportsPerRowErrorsWithLineNumbers() throws IOException {
        ImportResult result = parse("question,marks,option1,option2,option3,option4,option5,option6,correct\n"
                + "Valid,1,a,b,,,,,1\n"
                + "Bad marks,abc,a,b,,,,,1\n"
                + "One option,1,a,,,,,,1\n"
                + "Correct points at empty,1,a,b,,,,,3\n"
                + "Bad correct,1,a,b,,,,,9\n"
                + ",1,a,b,,,,,1\n");
        assertEquals(1, result.validDrafts().size());
        assertEquals(5, result.errors().size());
        assertEquals(3, result.errors().get(0).rowNumber());
        assertEquals(4, result.errors().get(1).rowNumber());
        assertEquals(7, result.errors().get(4).rowNumber());
    }

    @Test
    void handlesQuotedCommasAndEscapedQuotes() throws IOException {
        ImportResult result = parse("\"Pick one, please\",1,\"He said \"\"hi\"\"\",b,,,,,1\n");
        assertEquals(1, result.validDrafts().size());
        assertEquals("Pick one, please", result.validDrafts().get(0).text());
        assertEquals("He said \"hi\"", result.validDrafts().get(0).options().get(0));
    }

    @Test
    void compactsGapsBetweenOptionsAndKeepsCorrectAnswer() throws IOException {
        ImportResult result = parse("Gap,1,a,,c,,,,3\n");
        QuestionDraft draft = result.validDrafts().get(0);
        assertEquals(2, draft.options().size());
        assertEquals("c", draft.options().get(draft.correctIndex()));
    }

    @Test
    void ignoresBlankLinesAndRejectsTooManyColumns() throws IOException {
        ImportResult result = parse("\nA,1,a,b,,,,,1,extra\n\n");
        assertEquals(0, result.validDrafts().size());
        assertEquals(1, result.errors().size());
        assertEquals(1, result.rowCount());
    }
}
