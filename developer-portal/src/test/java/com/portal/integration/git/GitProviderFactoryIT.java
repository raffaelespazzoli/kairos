package com.portal.integration.git;

import com.portal.integration.git.model.PullRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GitProviderFactoryIT {

    @Inject
    GitProvider gitProvider;

    @Test
    void gitProviderIsInjectable() {
        assertNotNull(gitProvider);
    }

    @Test
    void devProviderIsSelectedInTestProfile() {
        // CDI wraps @ApplicationScoped beans in a proxy; verify behavior instead of type
        assertDoesNotThrow(() -> gitProvider.validateRepoAccess("https://github.com/test/repo"));
        assertEquals("", gitProvider.readFile("https://github.com/test/repo", "main", "any"));
        List<String> files = gitProvider.listDirectory("https://github.com/test/repo", "main", "src");
        assertTrue(files.isEmpty());
        PullRequest pr = gitProvider.createPullRequest("https://github.com/test/repo",
                "feat", "main", "Test PR", "description");
        assertEquals("https://dev-git/pr/1", pr.url());
        assertEquals(1, pr.number());
    }

    @Test
    void devProviderValidateRepoAccessSucceeds() {
        assertDoesNotThrow(() -> gitProvider.validateRepoAccess("https://github.com/test/repo"));
    }
}
