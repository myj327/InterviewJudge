package com.decade.doj.problem.controller;

import com.decade.doj.common.annotation.AdminRequired;
import com.decade.doj.common.domain.R;
import com.decade.doj.problem.domain.po.Assessment;
import com.decade.doj.problem.service.AssessmentService;
import com.decade.doj.problem.service.AttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final AttemptService attemptService;

    @PostMapping
    @AdminRequired
    public R<Long> create(@RequestBody Assessment assessment) {
        return R.ok(assessmentService.create(assessment));
    }

    @PostMapping("/{id}/publish")
    @AdminRequired
    public R<Void> publish(@PathVariable Long id) {
        assessmentService.publish(id);
        return R.ok();
    }

    @PostMapping("/{id}/attempts")
    public R<Long> startAttempt(@PathVariable Long id) {
        return R.ok(attemptService.start(id));
    }

    @GetMapping("/attempts/{attemptId}/problem-id")
    public R<Long> resolveProblemId(@PathVariable("attemptId") Long attemptId) {
        return R.ok(attemptService.resolveProblemId(attemptId));
    }

    @GetMapping("/attempts/{attemptId}")
    public R<java.util.Map<String,Object>> viewAttempt(@PathVariable Long attemptId) {
        return R.ok(attemptService.view(attemptId));
    }

    @PostMapping("/attempts/{attemptId}/finish")
    public R<Void> finishAttempt(@PathVariable Long attemptId) {
        attemptService.finish(attemptId);
        return R.ok();
    }

    @GetMapping("/attempts/{attemptId}/reader")
    public R<Boolean> canReadAttempt(@PathVariable Long attemptId) {
        return R.ok(attemptService.canRead(attemptId));
    }

    @GetMapping
    @AdminRequired
    public R<java.util.List<Assessment>> owned() {
        return R.ok(assessmentService.owned());
    }

    @GetMapping("/{id}/attempts")
    @AdminRequired
    public R<java.util.List<com.decade.doj.problem.domain.po.Attempt>> attempts(@PathVariable Long id) {
        return R.ok(assessmentService.attempts(id));
    }
}
