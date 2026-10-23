package com.decade.doj.sandbox.service;
import com.decade.doj.common.client.SubmissionClient;
import com.decade.doj.common.config.properties.ResourceProperties;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.domain.po.Submission;
import com.decade.doj.common.utils.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class TaskCleanupTest {
    @TempDir Path root;
    SubmissionClient client=mock(SubmissionClient.class);
    TaskCleanup cleaner;
    String folder;
    @BeforeEach void setup() throws Exception {
        ResourceProperties props=new ResourceProperties();props.setCodePath(root.toString());cleaner=new TaskCleanup(props,client);
        ReflectionTestUtils.setField(cleaner,"token","synthetic-test-token");
        folder=UUID.randomUUID().toString();Files.createDirectory(root.resolve(folder));Files.writeString(root.resolve(folder).resolve("Main.java"),"synthetic code");
        TaskFiles.mark(root.toString(),folder,"Main.java",new Submission().setUserId(7L).setAttemptId(10L).setProblemId(20L).setLanguage("java").setIdempotencyKey("synthetic"));
    }
    @AfterEach void clear(){UserContext.clear();}
    @Test void terminalCleanupAndConflictRemoveOnlyOwnedDirectory() throws Exception {
        assertNotNull(TaskCleanup.class.getAnnotation(org.springframework.scheduling.annotation.EnableScheduling.class));
        when(client.artifactState(any(),anyString())).thenAnswer(inv->{assertEquals(7L,UserContext.getCurrentUser());return R.ok("FINISHED");});
        UserContext.setCurrentUser(8L);cleaner.scan();assertFalse(Files.exists(root.resolve(folder)));assertEquals(8L,UserContext.getCurrentUser());assertTrue(Files.exists(root));
    }
    @Test void activeOrUnavailableKeepFilesAndOnlyAgedOrphanCanBeRemoved() throws Exception {
        var marker=root.resolve(folder).resolve(TaskFiles.MARKER);Files.setLastModifiedTime(marker,FileTime.from(Instant.now().minusSeconds(90000)));
        when(client.artifactState(any(),anyString())).thenReturn(R.ok("ACTIVE"),R.error("offline"),R.ok("ABSENT"));
        cleaner.clean(folder);assertTrue(Files.exists(marker));cleaner.clean(folder);assertTrue(Files.exists(marker));
        cleaner.clean(folder);assertFalse(Files.exists(marker));assertNull(UserContext.getCurrentUser());
    }
    @Test void rootTraversalAndHistoricalUnmarkedDirectoryAreProtected() throws Exception {
        assertThrows(Exception.class,()->TaskFiles.delete(root.toString(),".."));assertThrows(Exception.class,()->TaskFiles.delete(root.toString(),"../"+folder));
        String historic=UUID.randomUUID().toString();Files.createDirectory(root.resolve(historic));cleaner.clean(historic);assertTrue(Files.exists(root.resolve(historic)));
        Path outside=Files.createDirectory(root.resolve("outside"));Files.writeString(outside.resolve("keep.txt"),"keep");
        String link=UUID.randomUUID().toString();
        try {Files.createSymbolicLink(root.resolve(link),outside);assertThrows(Exception.class,()->TaskFiles.delete(root.toString(),link));}
        catch(java.io.IOException|UnsupportedOperationException unavailable){assertTrue(Files.exists(outside.resolve("keep.txt")));}
        assertTrue(Files.exists(root.resolve(folder)));assertTrue(Files.exists(outside.resolve("keep.txt")));
    }
}
