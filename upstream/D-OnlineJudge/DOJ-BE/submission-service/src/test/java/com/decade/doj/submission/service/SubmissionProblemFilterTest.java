package com.decade.doj.submission.service;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.dto.SubmissionPageQueryDTO;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.mapper.SubmissionMapper;
import com.decade.doj.submission.service.impl.ISubmissionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class SubmissionProblemFilterTest {
    @Test void numericProblemFilterDoesNotDependOnMetadataName() {
        TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new MybatisConfiguration(),""),Submission.class);
        var mapper=mock(SubmissionMapper.class);var service=new ISubmissionServiceImpl();ReflectionTestUtils.setField(service,"baseMapper",mapper);
        when(mapper.selectPage(any(Page.class),any(Wrapper.class))).thenAnswer(inv->{
            Wrapper<?> query=inv.getArgument(1);assertTrue(query.getSqlSegment().contains("problem_id ="));assertFalse(query.getSqlSegment().contains("problem_name LIKE"));return inv.getArgument(0);});
        UserContext.setCurrentUser(7L);
        try {service.pageQuery(new SubmissionPageQueryDTO().setProblemId("20"));} finally {UserContext.clear();}
    }
}
