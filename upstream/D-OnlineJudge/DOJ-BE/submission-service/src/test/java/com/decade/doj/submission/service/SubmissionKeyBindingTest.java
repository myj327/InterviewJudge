package com.decade.doj.submission.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.decade.doj.common.exception.*;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.controller.SubmissionController;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.mapper.SubmissionMapper;
import com.decade.doj.submission.service.impl.ISubmissionServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionKeyBindingTest {
    SubmissionMapper mapper = mock(SubmissionMapper.class);
    ISubmissionServiceImpl service = new ISubmissionServiceImpl();
    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        UserContext.setCurrentUser(7L);
    }
    @AfterEach void clear() { UserContext.clear(); }
    Submission request() {
        return request("class Main {}", 21L);
    }
    Submission request(String code, Long problem) {
        Submission submission = new Submission();
        submission.setAttemptId(10L); submission.setProblemId(problem); submission.setLanguage("java");
        submission.setIdempotencyKey("request-1"); submission.setCode(code);
        return submission;
    }
    Submission existing() { return existing(7L); }
    Submission existing(Long owner) {
        Submission submission = request(); submission.setId(100L); submission.setUserId(owner); return submission;
    }

    @Test void sameKeyBindsExactCodeAndProblemWithoutCreatingAnotherRow() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing());
        assertEquals(100L, service.createPending(request()));
        for (Submission changed : new Submission[]{request("class Main { int n; }", 21L), request("class Main {}", 22L)}) {
            CommonException error = assertThrows(CommonException.class, () -> service.createPending(changed));
            assertEquals(409, error.getCode());
        }
        verify(mapper, never()).insert(any());
    }

    @Test void sameUnicodeSourceReturnsOriginalIdThroughIntakeAndReceipt() {
        String code = "class Main { String text = \"面试😀\"; }\r\n";
        Submission row = existing(); row.setCode(code);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
        assertEquals(100L, service.createPending(request(code, 21L)));
        assertEquals(100L, service.findReceipt(request(code, 21L)));
        verify(mapper, never()).insert(any());
    }

    @Test void differentSourcesWithSameJavaHashCodeStillConflict() {
        String first = "class Main { String text = \"Aa\"; }";
        String second = "class Main { String text = \"BB\"; }";
        assertEquals(first.hashCode(), second.hashCode());
        Submission row = existing(); row.setCode(first);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
        CommonException intake = assertThrows(CommonException.class,
                () -> service.createPending(request(second, 21L)));
        CommonException receipt = assertThrows(CommonException.class,
                () -> service.findReceipt(request(second, 21L)));
        assertEquals(409, intake.getCode()); assertEquals(409, receipt.getCode());
        verify(mapper, never()).insert(any());
    }

    @Test void contentComparisonKeepsLineEndingsAndNullDistinct() {
        Submission row = existing(); row.setCode("class Main {}\r\n");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
        CommonException lineEnding = assertThrows(CommonException.class,
                () -> service.findReceipt(request("class Main {}\n", 21L)));
        assertEquals(409, lineEnding.getCode());
        row.setCode(null);
        assertEquals(100L, service.findReceipt(request(null, 21L)));
        CommonException empty = assertThrows(CommonException.class,
                () -> service.findReceipt(request("", 21L)));
        assertEquals(409, empty.getCode());
        verify(mapper, never()).insert(any());
    }

    @Test void losingConcurrentInsertStillChecksWinnerAndOwner() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null, existing());
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("synthetic race"));
        CommonException conflict = assertThrows(CommonException.class,
                () -> service.createPending(request("class Main { int n; }", 21L)));
        assertEquals(409, conflict.getCode());
        reset(mapper);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing(8L));
        assertThrows(ForbiddenException.class, () -> service.createPending(request()));
    }

    @Test void privateReceiptLookupNeedsIdentityAndHasNoWrites() throws Exception {
        var receipt = SubmissionController.class.getDeclaredMethod("receipt", Submission.class, String.class);
        JudgingIntake intake = mock(JudgingIntake.class);
        SubmissionController controller = new SubmissionController(service, intake);
        ReflectionTestUtils.setField(controller, "internalToken", "synthetic-service-token");
        var forbidden = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> receipt.invoke(controller, request(), null));
        assertInstanceOf(ForbiddenException.class, forbidden.getCause());
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing());
        var result = (com.decade.doj.common.domain.R<?>) receipt.invoke(controller, request(), "synthetic-service-token");
        assertEquals(100L, result.getData());
        verifyNoInteractions(intake); verify(mapper, never()).insert(any());
        UserContext.clear();
        var unauthorized = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> receipt.invoke(controller, request(), "synthetic-service-token"));
        assertInstanceOf(UnauthorizedException.class, unauthorized.getCause());
    }
}
