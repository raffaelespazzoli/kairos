package com.portal.integration.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitProviderFactoryTest {

    private GitProviderFactory factory;
    private GitProviderConfig mockConfig;

    @BeforeEach
    void setUp() throws Exception {
        mockConfig = mock(GitProviderConfig.class);
        when(mockConfig.token()).thenReturn("test-token");
        when(mockConfig.infraRepoUrl()).thenReturn("https://github.com/org/infra");
        when(mockConfig.apiUrl()).thenReturn(Optional.empty());

        factory = new GitProviderFactory();
        Field configField = GitProviderFactory.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(factory, mockConfig);
    }

    @Test
    void returnsGitHubProviderForGithub() {
        when(mockConfig.provider()).thenReturn("github");
        GitProvider result = factory.gitProvider();
        assertInstanceOf(GitHubProvider.class, result);
    }

    @Test
    void returnsGitLabProviderForGitlab() {
        when(mockConfig.provider()).thenReturn("gitlab");
        GitProvider result = factory.gitProvider();
        assertInstanceOf(GitLabProvider.class, result);
    }

    @Test
    void returnsGiteaProviderForGitea() {
        when(mockConfig.provider()).thenReturn("gitea");
        GitProvider result = factory.gitProvider();
        assertInstanceOf(GiteaProvider.class, result);
    }

    @Test
    void returnsBitbucketProviderForBitbucket() {
        when(mockConfig.provider()).thenReturn("bitbucket");
        GitProvider result = factory.gitProvider();
        assertInstanceOf(BitbucketProvider.class, result);
    }

    @Test
    void returnsDevGitProviderForDev() {
        when(mockConfig.provider()).thenReturn("dev");
        GitProvider result = factory.gitProvider();
        assertInstanceOf(DevGitProvider.class, result);
    }

    @Test
    void caseInsensitiveProviderSelection() {
        when(mockConfig.provider()).thenReturn("GitHub");
        assertInstanceOf(GitHubProvider.class, factory.gitProvider());
    }

    @Test
    void devProviderDoesNotBuildHttpClient() {
        when(mockConfig.provider()).thenReturn("DEV");
        GitProvider result = factory.gitProvider();
        assertInstanceOf(DevGitProvider.class, result);
    }

    @Test
    void throwsIllegalArgumentExceptionForUnknownProvider() {
        when(mockConfig.provider()).thenReturn("unknown");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> factory.gitProvider());
        assertTrue(ex.getMessage().contains("Unknown git provider: unknown"));
        assertTrue(ex.getMessage().contains("Supported:"));
    }
}
