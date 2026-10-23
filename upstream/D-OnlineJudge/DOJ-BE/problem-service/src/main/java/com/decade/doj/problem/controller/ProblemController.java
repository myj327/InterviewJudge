package com.decade.doj.problem.controller;


import com.decade.doj.common.annotation.AdminRequired;
import com.decade.doj.common.domain.PageDTO;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.exception.ForbiddenException;
import com.decade.doj.problem.domain.dto.ProblemPageQueryDTO;
import com.decade.doj.problem.domain.po.Problem;
import com.decade.doj.problem.service.IProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 
 * @since 2024-09-17
 */
@RestController
@RequestMapping("/problem")
@Tag(name = "题目相关接口")
@Slf4j
@RequiredArgsConstructor
public class ProblemController {

    private final IProblemService problemService;

    @Value("${DOJ_INTERNAL_TOKEN:}")
    private String internalToken;

    @PostMapping("/admin/sync-es")
    @AdminRequired
    @Operation(summary = "同步题目数据到elasticsearch")
    public R<Integer> syncProblemToEs() {
        Integer res = problemService.syncAllToElasticsearch();
        return R.ok(res);
    }

    @PostMapping("/admin/reindex")
    @AdminRequired
    @Operation(summary = "重建索引并同步题目")
    public R<Integer> reindexProblems() {
        Integer res = problemService.reindexAll();
        return R.ok(res);
    }

    @PostMapping("/admin/reset")
    @AdminRequired
    @Operation(summary = "清空 problem 数据与 ES 索引")
    public R<Void> resetProblems() {
        problemService.resetProblems();
        return R.ok();
    }

    @PostMapping
    @AdminRequired
    @Operation(summary = "新增题目")
    public R<Void> createProblem(@RequestBody Problem problem) {
        problemService.save(problem);
        return R.ok();
    }

    @PutMapping
    @AdminRequired
    @Operation(summary = "修改题目")
    public R<Void> updateProblem(@RequestBody Problem problem) {
        problemService.updateById(problem);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @AdminRequired
    @Operation(summary = "删除题目")
    public R<Void> deleteProblem(@PathVariable Long id) {
        problemService.removeById(id);
        return R.ok();
    }

    @GetMapping("/list")
    @Operation(summary = "获取题目列表")
    public R<List<Problem>> list() {
        return R.ok(problemService.list().stream().map(this::candidateView).toList());
    }

    @GetMapping("/count")
    @Operation(summary = "获取题目总数")
    public R<Long> getCount() {
        return R.ok(problemService.count());
    }

    @GetMapping("/page")
    @Operation(summary = "分页获取题目列表")
    public R<PageDTO<Problem>> page(ProblemPageQueryDTO problemPageQueryDTO) {
        PageDTO<Problem> res = problemService.pageQuery(problemPageQueryDTO);
        return R.ok(PageDTO.fullPage(res.getTotal(), res.getPages(),
                res.getList().stream().map(this::candidateView).toList()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取题目详情")
    public R<Problem> getProblemById(@PathVariable Long id) {
        return R.ok(candidateView(problemService.getById(id)));
    }

    @GetMapping("/internal/{id}")
    public R<Problem> getJudgingProblemById(@PathVariable Long id,
            @RequestHeader(value = "X-InterviewJudge-Internal-Token", required = false) String token) {
        if (internalToken == null || internalToken.isBlank() || token == null
                || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("internal problem access denied");
        }
        return R.ok(problemService.getById(id));
    }

    private Problem candidateView(Problem problem) {
        if (problem == null) {
            return null;
        }
        Problem view = new Problem();
        BeanUtils.copyProperties(problem, view, "testData", "testAns");
        return view;
    }

}
