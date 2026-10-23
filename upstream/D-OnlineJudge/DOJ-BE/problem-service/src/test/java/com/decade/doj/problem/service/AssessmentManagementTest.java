package com.decade.doj.problem.service;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.problem.domain.po.*;
import com.decade.doj.problem.mapper.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class AssessmentManagementTest {
    final AssessmentMapper rows=mock(AssessmentMapper.class);
    final ProblemMapper problems=mock(ProblemMapper.class);
    final AttemptMapper attempts=mock(AttemptMapper.class);
    AssessmentService service;
    @BeforeEach void setup() throws Exception {
        UserContext.setCurrentUser(7L);
        var constructor=Arrays.stream(AssessmentService.class.getConstructors()).max(Comparator.comparingInt(java.lang.reflect.Constructor::getParameterCount)).orElseThrow();
        service=(AssessmentService)(constructor.getParameterCount()==2 ? constructor.newInstance(rows,problems):constructor.newInstance(rows,problems,attempts));
    }
    @AfterEach void clear(){UserContext.clear();}
    @Test void blankTitleAndMissingIdentityAreRejectedBeforeInsert(){
        var draft=new Assessment().setProblemId(20L).setTitle(" ").setStartsAt(LocalDateTime.now()).setEndsAt(LocalDateTime.now().plusHours(1));
        when(problems.selectById(20L)).thenReturn(new Problem());
        assertThrows(RuntimeException.class,()->service.create(draft));verify(rows,never()).insert(any());
        UserContext.clear();draft.setTitle("Synthetic assessment");assertThrows(RuntimeException.class,()->service.create(draft));
    }
    @Test void ownedListBindsCurrentOwnerAndManagementEndpointsRequireAdmin() throws Exception {
        when(rows.selectList(any())).thenAnswer(inv->{var query=(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?>)inv.getArgument(0);
            query.getSqlSegment();assertTrue(query.getParamNameValuePairs().containsValue(7L));return List.of();});
        service.getClass().getMethod("owned").invoke(service);
        var controller=com.decade.doj.problem.controller.AssessmentController.class;
        assertNotNull(controller.getMethod("owned").getAnnotation(com.decade.doj.common.annotation.AdminRequired.class));
        assertNotNull(controller.getMethod("attempts",Long.class).getAnnotation(com.decade.doj.common.annotation.AdminRequired.class));
    }
    @Test void attemptsAndReportsRequireCorrectAssessmentOwner() throws Exception {
        when(rows.selectById(10L)).thenReturn(new Assessment().setOwnerId(7L));
        when(attempts.selectList(any())).thenReturn(List.of());service.getClass().getMethod("attempts",Long.class).invoke(service,10L);
        UserContext.setCurrentUser(8L);
        assertThrows(java.lang.reflect.InvocationTargetException.class,()->service.getClass().getMethod("attempts",Long.class).invoke(service,10L));
        when(attempts.selectById(100L)).thenReturn(new Attempt().setAssessmentId(10L).setCandidateId(8L));
        var reader=new AttemptService(rows,attempts);
        var method=AttemptService.class.getMethod("canRead",Long.class);
        assertEquals(true,method.invoke(reader,100L));UserContext.setCurrentUser(7L);assertEquals(true,method.invoke(reader,100L));
        UserContext.setCurrentUser(9L);assertEquals(false,method.invoke(reader,100L));
    }
}
