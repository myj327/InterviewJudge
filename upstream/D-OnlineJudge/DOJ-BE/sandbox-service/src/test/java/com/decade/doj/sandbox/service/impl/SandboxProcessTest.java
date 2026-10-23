package com.decade.doj.sandbox.service.impl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class SandboxProcessTest {
    @TempDir Path root;
    private Process fake(InputStream stream,int exit) throws Exception {
        Process process=mock(Process.class);
        when(process.getInputStream()).thenReturn(stream);when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.waitFor(anyLong(),any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(exit);return process;
    }
    @Test void endlessOutputWithoutNewlineStopsAtByteLimit() throws Exception {
        Process endless=fake(new InputStream(){public int read(){return 'x';}},0);
        SandboxProcess.Result result=new SandboxProcess(builder->endless).run(List.of("synthetic-cli"),null,1000);
        assertEquals(153,result.exit());assertTrue(result.output().isEmpty());verify(endless).destroyForcibly();
    }
    @Test void blockedOutputHasHostDeadlineAndDestroysProcess() throws Exception {
        Process blocked=fake(new InputStream(){public int read() throws IOException{try{Thread.sleep(10000);}catch(InterruptedException e){throw new IOException("interrupted");}return -1;}},0);
        long start=System.nanoTime();
        SandboxProcess.Result result=new SandboxProcess(builder->blocked).run(List.of("synthetic-cli"),null,50);
        assertEquals(124,result.exit());assertTrue((System.nanoTime()-start)<TimeUnit.SECONDS.toNanos(2));
        verify(blocked).destroyForcibly();
    }
    @Test void sessionDeletionIsRequiredAndDeletionFailureIsInfrastructureError() throws Exception {
        Path source=Files.writeString(root.resolve("Main.java"),"class Main {}");
        List<List<String>> commands=new ArrayList<>();
        SandboxProcess runner=new SandboxProcess(builder->{
            commands.add(builder.command());
            try{return fake(new ByteArrayInputStream(new byte[0]),builder.command().contains("rm")?1:0);}
            catch(Exception e){throw new IOException(e);}
        });
        SandboxSession session=new SandboxSession(runner,source.toString(),"Main.java",root.toString(),"");
        assertThrows(IOException.class,session::close);
        assertEquals(commands.get(0).get(commands.get(0).indexOf("--name")+1),commands.get(1).get(3));
    }
}
