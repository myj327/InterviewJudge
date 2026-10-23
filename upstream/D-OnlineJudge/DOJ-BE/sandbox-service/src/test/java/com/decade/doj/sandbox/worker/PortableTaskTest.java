package com.decade.doj.sandbox.worker;

import com.alibaba.fastjson.JSON;
import com.decade.doj.common.client.SubmissionClient;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.sandbox.domain.vo.JudgingTask;
import com.decade.doj.sandbox.service.ISandboxService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PortableTaskTest {
    @TempDir Path root;
    private final ISandboxService sandbox = mock(ISandboxService.class);
    private final Channel channel = mock(Channel.class);
    private final SubmissionClient submissions = mock(SubmissionClient.class);

    @Test void missingProducerFilesAreRecreatedFromTheSnapshotAndRemovedAfterExecution() throws Exception {
        JudgingTask task = snapshot();
        var paths = new ArrayList<Path>();
        doAnswer(call -> {
            JudgingTask received = call.getArgument(0);
            Path source = Path.of(received.getLocalPath()); paths.add(source);
            assertTrue(source.startsWith(root)); assertEquals("synthetic source", Files.readString(source));
            assertEquals("synthetic hidden input", Files.readString(source.getParent().resolve(received.getInput())));
            assertEquals("synthetic public input", Files.readString(source.getParent().resolve(received.getSmokeInput())));
            assertEquals("expected remains unchanged", received.getOutput());
            return null;
        }).when(sandbox).execute(any());
        worker().receive(message(task), channel);
        assertEquals(1, paths.size()); assertFalse(Files.exists(paths.get(0).getParent()));
        verify(channel).basicAck(8, false);
    }

    @Test void retryOwnsANewDirectoryAndAcknowledgesOnlyAfterSuccessfulExecution() throws Exception {
        var paths = new ArrayList<Path>();
        doAnswer(call -> {
            Path source = Path.of(((JudgingTask) call.getArgument(0)).getLocalPath());
            assertTrue(Files.exists(source));
            if (!paths.isEmpty()) assertFalse(Files.exists(paths.get(0).getParent()));
            paths.add(source);
            if (paths.size() == 1) throw new AmqpException("synthetic publish interruption");
            return null;
        }).when(sandbox).execute(any());
        worker().receive(message(snapshot()), channel);
        assertEquals(2, paths.size()); assertNotEquals(paths.get(0), paths.get(1));
        assertTrue(paths.stream().noneMatch(Files::exists));
        var order = inOrder(sandbox, channel);
        order.verify(sandbox, times(2)).execute(any()); order.verify(channel).basicAck(8, false);
    }

    @Test void unsafeOrIncompleteSnapshotsAreRejectedBeforeWritingFiles() throws Exception {
        var worker = worker();
        worker.receive(message(snapshot().setFilename("../outside.java")), channel);
        worker.receive(message(snapshot().setCode(null)), channel);
        verifyNoInteractions(sandbox); verify(channel, times(2)).basicReject(8, false);
        try (var entries = Files.list(root)) { assertEquals(0, entries.count()); }
        when(submissions.finished(eq(100L), any())).thenReturn(R.ok(true));
        worker.receive(message(new JudgingTask().setSubmissionId(100L).setUid(7L)), channel);
        verify(channel).basicAck(8, false); verifyNoInteractions(sandbox);
    }

    private JudgingWorker worker() {
        when(submissions.finished(eq(100L), any())).thenReturn(R.ok(false));
        ReliableDelivery delivery = new ReliableDelivery() { @Override protected void pause(long millis) {} };
        var worker = new JudgingWorker(sandbox, delivery, submissions);
        ReflectionTestUtils.setField(worker, "codePath", root.toString()); return worker;
    }
    private JudgingTask snapshot() {
        return new JudgingTask().setSubmissionId(100L).setUid(7L).setLang("java").setFilename("Main.java")
                .setLocalPath(root.resolve("absent-producer/Main.java").toString()).setCode("synthetic source")
                .setInput("untrusted/producer-name").setInputData("synthetic hidden input")
                .setSmokeInput("producer-smoke.txt").setSmokeInputData("synthetic public input")
                .setOutput("expected remains unchanged");
    }
    private Message message(JudgingTask task) {
        var properties = new MessageProperties(); properties.setDeliveryTag(8);
        return new Message(JSON.toJSONBytes(task), properties);
    }
}
