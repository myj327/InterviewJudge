package com.decade.doj.sandbox.service.impl;
import com.decade.doj.common.mq.ConfirmedPublisher;
import com.decade.doj.sandbox.domain.vo.ExecuteMessage;
import com.decade.doj.sandbox.domain.vo.JudgingTask;
import com.decade.doj.sandbox.enums.LanguageEnum;
import com.decade.doj.sandbox.service.ISandboxService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
@Service
public class SandboxService implements ISandboxService {
    private final ThreadPoolTaskExecutor runCodeExecutor;
    @Autowired private ConfirmedPublisher publisher;
    private final ThreadLocal<SandboxSession> session = new ThreadLocal<>();
    private final ThreadLocal<JudgingTask> limits = new ThreadLocal<>();
    @Value("${DOJ_CODE_PATH:/app/static/codes/}") private String containerCodePath;
    @Value("${HOST_CODE_PATH:}") private String hostCodePath;
    public SandboxService(RabbitTemplate rabbit, @Qualifier("RunCodeThreadPool") ThreadPoolTaskExecutor executor) {
        this.runCodeExecutor = executor;
    }
    public static String buildRunCommand(String raw, String base) {
        int count = raw.split("%s", -1).length - 1;
        return switch (count) {
            case 1 -> String.format(raw, base);
            case 2 -> String.format(raw, base, base);
            case 3 -> String.format(raw, base, base, base);
            default -> throw new IllegalArgumentException("Unsupported command");
        };
    }
    protected Process startSandboxProcess(ProcessBuilder builder) throws IOException { return builder.start(); }
    @Override public CompletableFuture<ExecuteMessage> runCodeInSandbox(String path, String filename, String lang) {
        return runCodeInSandboxWI(path, null, filename, lang);
    }
    @Override public CompletableFuture<ExecuteMessage> runCodeInSandboxWI(String path, String input, String filename, String lang) {
        return CompletableFuture.supplyAsync(() -> {
            try { return run(path, filename, lang, LanguageEnum.JAVA.getRunCmd(), input, null, false); }
            finally { closeSession(); }
        }, runCodeExecutor);
    }
    @Override public void execute(JudgingTask task) {
        ExecuteMessage result;
        limits.set(task);
        try {
            requireJava(task.getLang());
            requireLimits();
            result = task.getSmokeInput() != null ? executeStagedJava(task)
                    : executeLegacyJava(task);
        } finally { try { closeSession(); } finally { limits.remove(); } }
        Map<String,Object> message = new java.util.HashMap<>();
        message.put("submissionId",task.getSubmissionId()); message.put("executeMessage",result);
        if (result.getJudgeStage()!=null) message.put("judgeStage",result.getJudgeStage());
        publisher.publish("judging.result", message);
    }
    private ExecuteMessage executeLegacyJava(JudgingTask task) {
        ExecuteMessage compiled=runAttemptStage(task,"COMPILE",LanguageEnum.JAVA.getCompileCmd(),null,null);
        if(compiled.getExitValue()!=0) return compiled;
        return runAttemptStage(task,"VERDICT",LanguageEnum.JAVA.getExecuteCmd(),task.getInput(),task.getOutput());
    }
    private int timeMillis() { return limits.get()==null || limits.get().getTimeLimit()==null ? 2000 : limits.get().getTimeLimit(); }
    private int memoryMb() { return limits.get()==null || limits.get().getMemoryLimit()==null ? 128 : limits.get().getMemoryLimit(); }
    private void requireLimits() {
        if(timeMillis()<1 || timeMillis()>10000 || memoryMb()<128 || memoryMb()>1024) throw new IllegalArgumentException("Unsupported problem limits");
    }
    private ExecuteMessage executeStagedJava(JudgingTask task) {
        LanguageEnum java = LanguageEnum.JAVA;
        ExecuteMessage compile = runAttemptStage(task,"COMPILE",java.getCompileCmd(),null,null);
        if (compile.getExitValue()!=0) {
            if (compile.getExitValue()==1 || compile.getExitValue()==2) compile.setExitValue(2).setStatus("Compile Error");
            return compile.setJudgeStage("COMPILE_FAILED");
        }
        ExecuteMessage smoke = runAttemptStage(task,"SMOKE",java.getExecuteCmd(),task.getSmokeInput(),task.getSmokeOutput());
        if (smoke.getExitValue()!=10) return smoke.setJudgeStage("SMOKE_FAILED");
        return runAttemptStage(task,"VERDICT",java.getExecuteCmd(),task.getInput(),task.getOutput()).setJudgeStage("VERDICT_READY");
    }
    protected ExecuteMessage runAttemptStage(JudgingTask task, String stage, String raw, String input, String answer) {
        return run(task.getLocalPath(), task.getFilename(),task.getLang(),raw,input,answer,"COMPILE".equals(stage));
    }
    private ExecuteMessage run(String path,String filename,String lang,String raw,String input,String answer,boolean compile) {
        requireJava(lang);
        try {
            SandboxSession current = session.get();
            if (current==null) {
                current=new SandboxSession(new SandboxProcess(this::startSandboxProcess),path,filename,containerCodePath,hostCodePath,memoryMb());
                session.set(current);
            }
            String base=filename.substring(0,filename.length()-5);
            String command=buildRunCommand(raw,base).replace("java -Xmx64m","java -Xmx"+(memoryMb()/2)+"m");
            SandboxProcess.Result output=current.runMillis(command,input,compile?10000:timeMillis());
            int exit=output.exit();
            if (exit==125 || exit==126 || exit==127) throw new IOException("Sandbox execution unavailable");
            // User exit codes must never impersonate platform verdict codes (10/11).
            if (exit!=0 && exit!=124 && exit!=137 && exit!=153) exit=compile?2:1;
            String text=output.output();
            String status=ExecuteMessage.getStatus(exit);
            if (answer!=null && exit==0) { exit=text.equals(answer.trim())?10:11; status=ExecuteMessage.getStatus(exit); }
            return new ExecuteMessage().setExitValue(exit).setStatus(status)
                    .setMessage(answer!=null?status:((exit==0||exit==1||exit==2)?text:status))
                    // Program text cannot provide trusted resource statistics.
                    .setMemory(-1L).setTime(output.seconds());
        } catch (IOException failure) { throw new IllegalStateException("Sandbox infrastructure unavailable",failure); }
    }
    private void requireJava(String lang) { if (!"java".equals(lang)) throw new IllegalArgumentException("Only Java is supported"); }
    private void closeSession() {
        SandboxSession current=session.get(); session.remove();
        if (current!=null) try { current.close(); }
        catch (IOException failure) { throw new IllegalStateException("Sandbox cleanup unavailable",failure); }
    }
}
