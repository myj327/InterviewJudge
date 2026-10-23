package com.decade.doj.submission.controller;
import com.decade.doj.common.client.ProblemClient;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.repository.*;
import com.decade.doj.submission.service.ISubmissionService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ReviewAccessStateTest {
    final ISubmissionService rows=mock(ISubmissionService.class);
    final ReviewReportRepository reports=mock(ReviewReportRepository.class);
    final ReviewJobs jobs=mock(ReviewJobs.class);
    final ProblemClient problems=mock(ProblemClient.class);
    final Submission row=new Submission();
    ReviewController controller;
    @BeforeEach void setup() throws Exception {
        row.setId(91L);row.setAttemptId(10L);row.setUserId(7L);row.setJudgeStage("VERDICT_READY");row.setStatus("Accepted");
        when(rows.getById(91L)).thenReturn(row);UserContext.setCurrentUser(7L);
        // Reflection lets the red run compile before the new dependencies exist.
        var constructor=Arrays.stream(ReviewController.class.getConstructors()).max(Comparator.comparingInt(java.lang.reflect.Constructor::getParameterCount)).orElseThrow();
        controller=(ReviewController)(constructor.getParameterCount()==2 ? constructor.newInstance(rows,reports):constructor.newInstance(rows,reports,jobs,problems));
    }
    @AfterEach void clear(){UserContext.clear();}
    @Test void creatorCanReadButUnrelatedUserAndUnavailableAuthorityCannot() throws Exception {
        var report=new com.decade.doj.submission.domain.dto.ReviewReport().setSubmissionId(91L).setStatus("SUCCEEDED");
        when(reports.findBySubmissionId(91L)).thenReturn(report);UserContext.setCurrentUser(8L);
        var method=ProblemClient.class.getMethod("canReadAttempt",Long.class);
        doReturn(R.ok(true)).when(problems);method.invoke(problems,10L);
        assertSame(report,controller.getReview(91L).getData());
        doReturn(R.ok(false)).when(problems);method.invoke(problems,10L);
        assertThrows(com.decade.doj.common.exception.ForbiddenException.class,()->controller.getReview(91L));
        doReturn(R.error("offline")).when(problems);method.invoke(problems,10L);
        assertThrows(com.decade.doj.common.exception.CommonException.class,()->controller.getReview(91L));
    }
    @Test void absenceHasDistinctPendingRunningFailedAndSkippedStates(){
        for(String state:List.of("PENDING","RUNNING","FAILED")) {
            when(jobs.status(91L)).thenReturn(state);
            assertEquals(state,controller.getReview(91L).getData().getStatus());
        }
        row.setJudgeStage("SMOKE_FAILED");assertEquals("SKIPPED",controller.getReview(91L).getData().getStatus());
        row.setAttemptId(0L);assertEquals("SKIPPED",controller.getReview(91L).getData().getStatus());
        UserContext.clear();assertThrows(com.decade.doj.common.exception.UnauthorizedException.class,()->controller.getReview(91L));
    }
    @Test void attemptSummaryNeverReturnsSource() throws Exception {
        row.setCode("private synthetic source");when(rows.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row));
        var method=ProblemClient.class.getMethod("canReadAttempt",Long.class);doReturn(R.ok(true)).when(problems);method.invoke(problems,10L);
        var result=(R<?>)ReviewController.class.getMethod("attemptReviews",Long.class).invoke(controller,10L);
        assertFalse(result.getData().toString().contains("private synthetic source"));assertTrue(result.getData().toString().contains("91"));
    }
}
