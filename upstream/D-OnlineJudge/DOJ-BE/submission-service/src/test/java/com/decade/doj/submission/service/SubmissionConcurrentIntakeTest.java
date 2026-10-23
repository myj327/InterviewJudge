package com.decade.doj.submission.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.mapper.SubmissionMapper;
import com.decade.doj.submission.service.impl.ISubmissionServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubmissionConcurrentIntakeTest {
    @Test
    void racingFirstInsertReturnsWinnerToBothRequests() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
        SubmissionMapper mapper = mock(SubmissionMapper.class);
        ISubmissionServiceImpl service = new ISubmissionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        CyclicBarrier firstReads = new CyclicBarrier(2);
        AtomicReference<Submission> row = new AtomicReference<>();
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(call -> {
            Wrapper<?> query = call.getArgument(0);
            if (query.getSqlSegment().contains("FOR UPDATE")) return row.get();
            firstReads.await(3, TimeUnit.SECONDS);
            return null;
        });
        when(mapper.insert(any(Submission.class))).thenAnswer(call -> {
            Submission submitted = call.getArgument(0);
            submitted.setId(100L);
            if (!row.compareAndSet(null, submitted)) throw new DuplicateKeyException("synthetic unique conflict");
            return 1;
        });
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> submit = () -> {
                UserContext.setCurrentUser(7L);
                try { return service.createPending(request()); } finally { UserContext.clear(); }
            };
            Future<Long> a = threads.submit(submit);
            Future<Long> b = threads.submit(submit);
            assertEquals(100L, a.get(5, TimeUnit.SECONDS));
            assertEquals(100L, b.get(5, TimeUnit.SECONDS));
            assertEquals(7L, row.get().getUserId());
            verify(mapper, times(2)).insert(any(Submission.class));
        } finally { threads.shutdownNow(); }
    }

    @Test
    void unrelatedUniqueConflictIsStillAnError() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
        SubmissionMapper mapper = mock(SubmissionMapper.class);
        ISubmissionServiceImpl service = new ISubmissionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(mapper.insert(any(Submission.class))).thenThrow(new DuplicateKeyException("other constraint"));
        UserContext.setCurrentUser(7L);
        try { assertThrows(DuplicateKeyException.class, () -> service.createPending(request())); }
        finally { UserContext.clear(); }
    }

    private Submission request() {
        Submission request = new Submission();
        request.setAttemptId(10L); request.setProblemId(20L);
        request.setIdempotencyKey("same-key"); request.setLanguage("java");
        return request;
    }
}
