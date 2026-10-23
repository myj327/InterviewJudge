package com.decade.doj.problem.service;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.problem.domain.po.*;
import com.decade.doj.problem.mapper.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.TimeZone;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class AssessmentTimezoneTest {
    @Test void utcWindowWorksRegardlessOfHostTimezone(){
        var previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));UserContext.setCurrentUser(7L);
        var assessments=mock(AssessmentMapper.class);var attempts=mock(AttemptMapper.class);
        var now=LocalDateTime.now(ZoneOffset.UTC);
        when(assessments.selectById(10L)).thenReturn(new Assessment().setStatus("PUBLISHED").setStartsAt(now.minusMinutes(5)).setEndsAt(now.plusMinutes(5)));
        when(attempts.insert(any())).thenAnswer(inv->{Attempt row=inv.getArgument(0);assertTrue(row.getStartedAt().isBefore(now.plusMinutes(1)));row.setId(100L);return 1;});
        try{assertEquals(100L,new AttemptService(assessments,attempts).start(10L));}
        finally {TimeZone.setDefault(previous);UserContext.clear();}
    }
}
