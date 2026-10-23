package com.decade.doj.submission.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.decade.doj.common.exception.ForbiddenException;
import com.decade.doj.common.exception.UnauthorizedException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.dto.SubmissionPageQueryDTO;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.mapper.SubmissionMapper;
import com.decade.doj.submission.service.impl.ISubmissionServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubmissionQueryAuthorizationTest {
    private final SubmissionMapper mapper = mock(SubmissionMapper.class);
    private final ISubmissionServiceImpl service = new ISubmissionServiceImpl();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @AfterEach
    void clearIdentity() {
        UserContext.clear();
    }

    @Test
    void ownerCanReadTheirSourceAndVerdict() {
        UserContext.setCurrentUser(7L);
        Submission own = new Submission();
        own.setId(100L);
        own.setUserId(7L);
        own.setCode("synthetic source");
        own.setStatus("Accepted");
        when(mapper.selectById(100L)).thenReturn(own);

        Submission result = service.pageQuery(new SubmissionPageQueryDTO().setSubmissionId(100L))
                .getList().get(0);

        assertEquals("synthetic source", result.getCode());
        assertEquals("Accepted", result.getStatus());
    }

    @Test
    void rejectsOtherOwnersAndMissingOrAnonymousIdentity() {
        Submission other = new Submission();
        other.setId(100L);
        other.setUserId(8L);
        other.setCode("other synthetic source");
        when(mapper.selectById(100L)).thenReturn(other);
        UserContext.setCurrentUser(7L);
        assertThrows(ForbiddenException.class,
                () -> service.pageQuery(new SubmissionPageQueryDTO().setSubmissionId(100L)));
        clearInvocations(mapper);

        for (Long identity : new Long[]{null, 0L}) {
            UserContext.setCurrentUser(identity);
            assertThrows(UnauthorizedException.class,
                    () -> service.pageQuery(new SubmissionPageQueryDTO().setSubmissionId(100L)));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void paginationAlwaysAddsTheServerSideOwnerEvenWithAnotherUserFilter() {
        UserContext.setCurrentUser(7L);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.pageQuery(new SubmissionPageQueryDTO());
        service.pageQuery(new SubmissionPageQueryDTO().setUserId("another-candidate"));

        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, times(2)).selectPage(any(Page.class), query.capture());
        for (Wrapper wrapper : query.getAllValues()) {
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("user_id ="));
            var parameters = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper)
                    .getParamNameValuePairs();
            assertTrue(parameters.containsValue(7L));
        }
    }
}
