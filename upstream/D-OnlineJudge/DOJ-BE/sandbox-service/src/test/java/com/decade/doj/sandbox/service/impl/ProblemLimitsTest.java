package com.decade.doj.sandbox.service.impl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ProblemLimitsTest {
    @TempDir Path root;
    @Test void acceptedTaskCarriesProblemLimitsAndUnsupportedConfigurationWritesNothing() throws Exception {
        var resources=new com.decade.doj.common.config.properties.ResourceProperties();resources.setCodePath(root.toString());
        var client=org.mockito.Mockito.mock(com.decade.doj.common.client.SubmissionClient.class);
        var problems=org.mockito.Mockito.mock(com.decade.doj.common.client.ProblemClient.class);
        var controller=new com.decade.doj.sandbox.controller.SandboxController(resources,org.mockito.Mockito.mock(com.decade.doj.sandbox.service.ISandboxService.class),client,problems,
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class));
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"internalToken","synthetic");
        var problem=new com.decade.doj.common.domain.po.Problem().setTimeLimit(750).setMemoryLimit(512).setTestData("input").setTestAns("5")
                .setInputSample(List.of("input")).setOutputSample(List.of("5"));
        org.mockito.Mockito.when(client.receipt(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenReturn(com.decade.doj.common.domain.R.ok(null));
        org.mockito.Mockito.when(problems.resolveProblemId(10L)).thenReturn(com.decade.doj.common.domain.R.ok(20L));
        org.mockito.Mockito.when(problems.getJudgingProblemById(20L,"synthetic")).thenReturn(com.decade.doj.common.domain.R.ok(problem));
        org.mockito.Mockito.when(client.intake(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv->{
            com.decade.doj.common.domain.dto.JudgingRequest request=inv.getArgument(0);
            assertEquals(750,request.getTask().get("timeLimit"));assertEquals(512,request.getTask().get("memoryLimit"));return com.decade.doj.common.domain.R.ok(91L);});
        var file=new org.springframework.mock.web.MockMultipartFile("file","Main.java","text/plain","synthetic".getBytes());
        com.decade.doj.common.utils.UserContext.setCurrentUser(7L);
        try {
            assertEquals(91L,controller.validateAttempt(10L,file,"java","key").getData());
            long before;try(var files=Files.list(root)){before=files.count();}
            problem.setMemoryLimit(64);
            assertThrows(com.decade.doj.common.exception.BadRequestException.class,()->controller.validateAttempt(10L,file,"java","new-key"));
            try(var files=Files.list(root)){assertEquals(before,files.count());}
        } finally {com.decade.doj.common.utils.UserContext.clear();}
    }
    @Test void hostDeadlineDoesNotExceedExecutionLimitPlusFiveSeconds() throws Exception {
        Files.writeString(root.resolve("Main.java"),"synthetic");
        var process=new SandboxProcess(builder->new SandboxExitCodeTest.FakeProcess(new SandboxExitCodeTest.Exit(0,"")) {
            @Override public boolean waitFor(long timeout,java.util.concurrent.TimeUnit unit){
                if(builder.command().contains("exec")) assertTrue(unit.toMillis(timeout)<=5750);
                return true;
            }
        });
        try(var session=new SandboxSession(process,root.resolve("Main.java").toString(),"Main.java",root.toString(),"",512)) {
            assertEquals(0,session.runMillis("java -Xmx256m Main",null,750).exit());
        }
    }
    @Test void problemMemoryAndMillisecondTimeoutAreAppliedWithoutWeakeningIsolation() throws Exception {
        Path source=Files.writeString(root.resolve("Main.java"),"synthetic");
        var create=(List<String>)SandboxCommand.class.getDeclaredMethod("create",String.class,Path.class,String.class,String.class,int.class)
                .invoke(null,"ij-test",source,root.toString(),"",512);
        assertEquals("512m",create.get(create.indexOf("--memory")+1));assertEquals("512m",create.get(create.indexOf("--memory-swap")+1));
        assertEquals("none",create.get(create.indexOf("--network")+1));assertTrue(create.contains("--read-only"));
        var exec=(List<String>)SandboxCommand.class.getDeclaredMethod("execMillis",String.class,String.class,int.class)
                .invoke(null,"ij-test","java Main",750);
        assertTrue(exec.get(exec.size()-1).contains("0.75s"));
    }
}
