package dev.prabodh.examedge.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import dev.prabodh.examedge.dto.Views.ExamSessionView;
import dev.prabodh.examedge.dto.Views.QuestionView;
import dev.prabodh.examedge.entity.Exam;
import dev.prabodh.examedge.repository.ExamRepository;
import dev.prabodh.examedge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
@WithUserDetails("student@examedge.com")
class StudentFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExamRepository examRepository;

    @Test
    void studentCompletesAnExamFromStartToResult() throws Exception {
        Exam exam = examRepository.findAll().get(0);

        mockMvc.perform(get("/student")).andExpect(status().isOk()).andExpect(view().name("student/dashboard"));
        mockMvc.perform(get("/student/exams/" + exam.getId() + "/instructions"))
                .andExpect(status().isOk()).andExpect(content().string(containsString(exam.getTitle())));

        MvcResult started = mockMvc.perform(post("/student/exams/" + exam.getId() + "/start").with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String attemptUrl = started.getResponse().getRedirectedUrl();

        MvcResult page = mockMvc.perform(get(attemptUrl))
                .andExpect(status().isOk()).andExpect(view().name("student/exam"))
                .andExpect(model().attributeExists("examSession"))
                .andExpect(content().string(containsString("question-panel"))).andReturn();
        ExamSessionView session = (ExamSessionView) page.getModelAndView().getModel().get("examSession");
        QuestionView first = session.questions().get(0);

        mockMvc.perform(post(attemptUrl.replace("/student/attempts/", "/student/api/attempts/") + "/answer")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":" + first.id() + ",\"optionId\":" + first.options().get(0).id() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true));

        mockMvc.perform(get(attemptUrl.replace("/student/attempts/", "/student/api/attempts/") + "/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers['" + first.id() + "']").value(first.options().get(0).id()));

        mockMvc.perform(post(attemptUrl + "/submit").with(csrf()).param("automatic", "false"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(attemptUrl + "/result"));

        mockMvc.perform(get(attemptUrl + "/result"))
                .andExpect(status().isOk()).andExpect(view().name("student/result"))
                .andExpect(content().string(containsString("Question review")));

        MockHttpServletResponse pdf = mockMvc.perform(get(attemptUrl + "/result.pdf"))
                .andExpect(status().isOk()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertTrue(pdf.getContentAsByteArray().length > 500);
        org.junit.jupiter.api.Assertions.assertEquals("application/pdf", pdf.getContentType());

        mockMvc.perform(get("/student/exams/" + exam.getId() + "/leaderboard"))
                .andExpect(status().isOk()).andExpect(view().name("student/leaderboard"));
        mockMvc.perform(get("/student/history"))
                .andExpect(status().isOk()).andExpect(content().string(containsString(exam.getTitle())));
    }

    @Test
    void leaderboardRequiresACompletedAttempt() throws Exception {
        Exam exam = examRepository.findAll().get(0);
        mockMvc.perform(get("/student/exams/" + exam.getId() + "/leaderboard"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void examPageOfAnotherAttemptIsNotFound() throws Exception {
        mockMvc.perform(get("/student/attempts/987654")).andExpect(status().isNotFound());
    }
}
