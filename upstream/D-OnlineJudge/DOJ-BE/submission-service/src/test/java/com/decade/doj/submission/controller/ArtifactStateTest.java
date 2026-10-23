package com.decade.doj.submission.controller;
import com.decade.doj.common.domain.dto.JudgingRequest;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ArtifactStateTest {
    final ISubmissionService rows=mock(ISubmissionService.class);
    final SubmissionController controller=new SubmissionController(rows,mock(JudgingIntake.class));
    @Test void strongServiceCredentialRequiredForArtifactQueries() throws Exception {
        ReflectionTestUtils.setField(controller,"internalToken","synthetic-test-token");
        var method=SubmissionController.class.getMethod("finished",Long.class,String.class);
        for(String token:new String[]{null,"wrong"}) {
            var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(controller,91L,token));
            assertInstanceOf(com.decade.doj.common.exception.ForbiddenException.class,error.getCause());
        }
        verifyNoInteractions(rows);
    }
    @Test void queuedAndInfrastructureFailureKeepFilesAndOnlyRealVerdictsFinish() throws Exception {
        ReflectionTestUtils.setField(controller,"internalToken","synthetic-test-token");
        var row=new Submission();when(rows.getById(91L)).thenReturn(row);
        var finished=SubmissionController.class.getMethod("finished",Long.class,String.class);
        for(String stage:new String[]{"QUEUED","INFRA_FAILED","VERDICT_READY","COMPILE_FAILED","SMOKE_FAILED"}) {
            row.setJudgeStage(stage);
            assertEquals(!stage.equals("QUEUED")&&!stage.equals("INFRA_FAILED"),((com.decade.doj.common.domain.R<?>)finished.invoke(controller,91L,"synthetic-test-token")).getData());
        }
        Submission request=new Submission();var state=SubmissionController.class.getMethod("artifactState",Submission.class,String.class);
        assertEquals("ABSENT",((com.decade.doj.common.domain.R<?>)state.invoke(controller,request,"synthetic-test-token")).getData());
        when(rows.findReceipt(request)).thenReturn(91L);row.setJudgeStage("QUEUED");
        assertEquals("ACTIVE",((com.decade.doj.common.domain.R<?>)state.invoke(controller,request,"synthetic-test-token")).getData());
        row.setJudgeStage("VERDICT_READY");assertEquals("FINISHED",((com.decade.doj.common.domain.R<?>)state.invoke(controller,request,"synthetic-test-token")).getData());
    }
}
