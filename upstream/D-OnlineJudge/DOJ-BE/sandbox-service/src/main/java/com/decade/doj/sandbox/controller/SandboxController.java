package com.decade.doj.sandbox.controller;

import com.alibaba.fastjson.JSON;
import feign.FeignException;
import com.decade.doj.common.domain.dto.JudgingRequest;
import com.decade.doj.common.client.ProblemClient;
import com.decade.doj.common.client.SubmissionClient;
import com.decade.doj.common.config.properties.ResourceProperties;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.domain.po.Problem;
import com.decade.doj.common.domain.po.Submission;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.sandbox.domain.vo.ExecuteMessage;
import com.decade.doj.sandbox.domain.vo.JudgingTask;
import com.decade.doj.sandbox.enums.LanguageEnum;
import com.decade.doj.sandbox.service.ISandboxService;
import com.decade.doj.sandbox.service.TaskFiles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/sandbox")
@Tag(name = "沙箱相关接口")
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(ResourceProperties.class)
public class SandboxController {

    private final ResourceProperties resourceProperties;

    private final ISandboxService sandboxService;
    private final SubmissionClient submissionClient;
    private final ProblemClient problemClient;
    private final StringRedisTemplate redisTemplate;

    @Value("${DOJ_INTERNAL_TOKEN:}")
    private String internalToken;

    // Feign throws for non-2xx responses before the existing R checks can run.
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<R<Void>> upstreamFailure(FeignException failure) {
        int status = switch (failure.status()) {
            case 400, 403, 409 -> failure.status();
            default -> 503;
        };
        String message = switch (status) {
            case 400 -> "提交参数或考核状态不符合要求";
            case 403 -> "无权执行该提交";
            case 409 -> "同一幂等键不能对应不同的代码、语言或题目";
            default -> "提交服务暂不可用";
        };
        return ResponseEntity.status(status).body(R.error(status, message));
    }

    @PostMapping("/code")
    @Operation(summary = "运行代码文件(异步执行)")
    public CompletableFuture<R<ExecuteMessage>> runCode(@RequestParam("file") MultipartFile file, @RequestParam("language") @NotBlank String lang) {
        if (file.isEmpty()) {
            return CompletableFuture.completedFuture(R.error("上传的文件不能为空!"));
        }

        if (LanguageEnum.isInValidLanguage(lang)) {
            return CompletableFuture.completedFuture(R.error("不支持的编程语言: " + lang));
        }

        try {
            String[] paths = saveFile(file, resourceProperties.getCodePath(), null);
            String path = paths[0];
            return sandboxService
                    .runCodeInSandbox(path, file.getOriginalFilename(), lang)
                    .thenApply(R::ok).whenComplete((ignored,failure)->discard(paths[1]));
        } catch (IOException e) {
            log.error("保存代码文件失败", e);
            return CompletableFuture.completedFuture(R.error("文件保存失败: " + e.getMessage()));
        }
    }

    @PostMapping("/problem")
    @Operation(summary = "运行题目代码(异步执行)")
    public CompletableFuture<R<ExecuteMessage>> runProblem(@RequestParam("file") MultipartFile file, @RequestParam("input") MultipartFile input, @RequestParam("language") @NotBlank String lang, @RequestParam("pid") Long pid) {
        if (file.isEmpty()) {
            return CompletableFuture.completedFuture(R.error("上传的文件不能为空!"));
        }

        if (LanguageEnum.isInValidLanguage(lang)) {
            return CompletableFuture.completedFuture(R.error("不支持的编程语言: " + lang));
        }

        try {
            String[] Paths = saveFile(file, resourceProperties.getCodePath(), null);
            String codePath = Paths[0];
            saveFile(input, resourceProperties.getCodePath(), Paths[1]);

            return sandboxService
                    .runCodeInSandboxWI(codePath, input.getOriginalFilename(), file.getOriginalFilename(), lang)
                    .thenApply(R::ok).whenComplete((ignored,failure)->discard(Paths[1]));
        } catch (IOException e) {
            log.error("保存代码/输入文件失败", e);
            return CompletableFuture.completedFuture(R.error("文件保存失败: " + e.getMessage()));
        }
    }

    @PostMapping("/validate")
    @Operation(summary = "验证题目代码(异步提交)")
    public R<Long> runProblemValidate(@RequestParam("file") MultipartFile file, @RequestParam("language") @NotBlank String lang, @RequestParam("pid") Long pid) throws IOException {
        if (file.isEmpty()) {
            return R.error(400, "上传的文件不能为空!");
        }

        if (LanguageEnum.isInValidLanguage(lang)) {
            return R.error(400, "不支持的编程语言: " + lang);
        }

        Problem problem=judgingProblem(pid);
        requireLimits(problem);
        String[] Paths = saveFile(file, resourceProperties.getCodePath(), null);
        String codePath = Paths[0];
        String[] data = saveText2File(pid, resourceProperties.getCodePath(), Paths[1],problem);

        // 1. 调用 submission-service 创建一个 PENDING 状态的提交记录
        Submission submissionDTO = new Submission();
        submissionDTO.setProblemId(pid);
        submissionDTO.setUserId(UserContext.getCurrentUser());
        submissionDTO.setLanguage(lang);
        submissionDTO.setCode(Paths[2]);
        submissionDTO.setAttemptId(0L).setIdempotencyKey(UUID.randomUUID().toString());

        // 2. 创建判题任务
        JudgingTask task = new JudgingTask()
            .setLocalPath(codePath)
            .setInput(data[0])
            .setInputData(problem.getTestData())
            .setOutput(data[1])
            .setFilename(file.getOriginalFilename())
            .setLang(lang)
            .setProblemId(pid)
            .setTimeLimit(problem.getTimeLimit()).setMemoryLimit(problem.getMemoryLimit())
            .setUid(UserContext.getCurrentUser());

        return queue(submissionDTO,task,Paths[1],file.getOriginalFilename());
    }

    @PostMapping("/attempts/{attemptId}/validate")
    @Operation(summary = "验证 Attempt 题目代码(异步提交)")
    public R<Long> validateAttempt(@PathVariable Long attemptId,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam("language") @NotBlank String lang,
                                   @RequestParam("idempotencyKey") String idempotencyKey) throws IOException {
        if (file.isEmpty()) {
            return R.error(400, "上传的文件不能为空!");
        }
        if (!"java".equals(lang) || idempotencyKey == null || idempotencyKey.isBlank()) {
            return R.error(400, "仅支持 java 且必须提供幂等键");
        }

        Submission submission = new Submission().setAttemptId(attemptId).setIdempotencyKey(idempotencyKey)
                .setLanguage(lang).setCode(new String(file.getBytes(), StandardCharsets.UTF_8));
        R<Long> receipt = submissionClient.receipt(submission, internalToken);
        if (receipt == null) return R.error(503, "提交受理查询暂不可用");
        if (!receipt.success() || receipt.getData() != null) return receipt;

        R<Long> problemResponse = problemClient.resolveProblemId(attemptId);
        if (!problemResponse.success()) {
            return R.error(problemResponse.getCode(), "解析 Attempt 题目失败: " + problemResponse.getMsg());
        }
        Long problemId = problemResponse.getData();
        Problem problem = judgingProblem(problemId);
        requireLimits(problem);
        String[] paths = saveFile(file, resourceProperties.getCodePath(), null);
        String[] data = saveText2File(problemId, resourceProperties.getCodePath(), paths[1],problem);
        Path smokeInputPath = Paths.get(resourceProperties.getCodePath(), paths[1], "smoke_input.txt");
        Files.writeString(smokeInputPath, problem.getInputSample().get(0));

        submission.setProblemId(problemId);
        submission.setCode(paths[2]);
            JudgingTask task = new JudgingTask()
                    .setLocalPath(paths[0])
                    .setInput(data[0])
                    .setInputData(problem.getTestData())
                    .setOutput(data[1])
                    .setSmokeInput(smokeInputPath.getFileName().toString())
                    .setSmokeInputData(problem.getInputSample().get(0))
                    .setSmokeOutput(problem.getOutputSample().get(0))
                    .setFilename(file.getOriginalFilename())
                    .setLang(lang)
                    .setProblemId(problemId)
                    .setTimeLimit(problem.getTimeLimit()).setMemoryLimit(problem.getMemoryLimit())
                    .setUid(UserContext.getCurrentUser());
        return queue(submission,task,paths[1],file.getOriginalFilename());
    }

    private R<Long> queue(Submission submission,JudgingTask task,String folder,String filename) throws IOException {
        submission.setUserId(UserContext.getCurrentUser());
        TaskFiles.mark(resourceProperties.getCodePath(),folder,filename,submission);
        R<Long> result=submissionClient.intake(new JudgingRequest().setSubmission(submission)
                .setTask(JSON.parseObject(JSON.toJSONString(task))),internalToken);
        if(result!=null && java.util.Set.of(400,403,409).contains(result.getCode())) discard(folder);
        return result;
    }

    private void discard(String folder) {
        try { TaskFiles.delete(resourceProperties.getCodePath(),folder); }
        catch(Exception failure) { log.warn("Task files cleanup deferred: {}",failure.getClass().getSimpleName()); }
    }

    private void requireLimits(Problem problem) {
        if((problem.getTimeLimit()!=null && (problem.getTimeLimit()<1 || problem.getTimeLimit()>10000))
                || (problem.getMemoryLimit()!=null && (problem.getMemoryLimit()<128 || problem.getMemoryLimit()>1024)))
            throw new com.decade.doj.common.exception.BadRequestException("当前 Java 沙箱支持 1–10000ms、128–1024MB，请调整题目限额");
    }

    private String[] saveText2File(Long pid, String basePath, String folderName,Problem problem) throws IOException {
        if (folderName == null) {
            folderName = UUID.randomUUID().toString();
        }

        Path subFolderPath = Paths.get(basePath).toAbsolutePath().normalize().resolve(folderName).normalize();
        Files.createDirectories(subFolderPath);

        String inputFileName = pid + "_p_input.txt";
        // 从其他微服务读取input和output数据
        String inputdata = problem.getTestData();
        String outputdata = problem.getTestAns();

        Path inputFilePath = subFolderPath.resolve(inputFileName);
        Files.writeString(inputFilePath, inputdata);

        return new String[]{inputFileName, outputdata};
    }

    private Problem judgingProblem(Long problemId) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("Internal judging credential is not configured");
        }
        R<Problem> response = problemClient.getJudgingProblemById(problemId, internalToken);
        if (!response.success() || response.getData() == null) {
            throw new IllegalStateException("Unable to load internal judging data");
        }
        return response.getData();
    }

    private String[] saveFile(MultipartFile file, String basePath, String folderName) throws IOException {
        if (folderName == null) {
            folderName = UUID.randomUUID().toString();
        }

        String origFilename = file.getOriginalFilename();
        if (origFilename == null || !origFilename.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")) {
            throw new IOException("上传文件名必须是仅含字母、数字、下划线、点或横线的安全文件名");
        }

        Path root = Paths.get(basePath).toAbsolutePath().normalize();
        Path subFolderPath = root.resolve(folderName).normalize();
        Path destinationFilePath = subFolderPath.resolve(origFilename).normalize();
        if (!subFolderPath.startsWith(root) || subFolderPath.equals(root)
                || !destinationFilePath.startsWith(subFolderPath)) {
            throw new IOException("上传文件路径超出允许目录");
        }
        Files.createDirectories(subFolderPath);

        byte[] bytes = file.getBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);
        Files.write(destinationFilePath, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        if(Files.notExists(subFolderPath.resolve(TaskFiles.MARKER))) TaskFiles.mark(basePath,folderName,origFilename,null);
        // file.transferTo(destinationFilePath.toFile());

        return new String[]{destinationFilePath.toUri().getPath(), folderName, content};
    }

}
