package com.portal.integration.secrets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecretManagerCredentialProviderTest {

    private SecretManagerCredentialProvider provider;
    private SecretManagerAdapter mockAdapter;

    @BeforeEach
    void setUp() throws Exception {
        mockAdapter = mock(SecretManagerAdapter.class);
        provider = new SecretManagerCredentialProvider();

        Field adapterField = SecretManagerCredentialProvider.class.getDeclaredField("adapter");
        adapterField.setAccessible(true);
        adapterField.set(provider, mockAdapter);
    }

    @Test
    void cacheMissDelegatesToAdapter() {
        ClusterCredential expected = ClusterCredential.of("token-abc", 3600);
        when(mockAdapter.getCredentials("dev-cluster", "portal-role")).thenReturn(expected);

        ClusterCredential result = provider.getCredentials("dev-cluster", "portal-role");

        assertEquals("token-abc", result.token());
        assertEquals(3600, result.ttlSeconds());
        verify(mockAdapter, times(1)).getCredentials("dev-cluster", "portal-role");
    }

    @Test
    void cacheHitReturnsCachedWithoutAdapterCall() {
        ClusterCredential cached = ClusterCredential.of("token-abc", 3600);
        when(mockAdapter.getCredentials("dev-cluster", "portal-role")).thenReturn(cached);

        provider.getCredentials("dev-cluster", "portal-role");
        Mockito.reset(mockAdapter);

        ClusterCredential result = provider.getCredentials("dev-cluster", "portal-role");

        assertEquals("token-abc", result.token());
        verifyNoInteractions(mockAdapter);
    }

    @Test
    void expiredCredentialTriggersRefresh() throws Exception {
        ClusterCredential expired = new ClusterCredential("old-token", 3600, Instant.now().minusSeconds(1));
        injectCacheEntry("dev-cluster", "portal-role", expired);

        ClusterCredential fresh = ClusterCredential.of("new-token", 3600);
        when(mockAdapter.getCredentials("dev-cluster", "portal-role")).thenReturn(fresh);

        ClusterCredential result = provider.getCredentials("dev-cluster", "portal-role");

        assertEquals("new-token", result.token());
        verify(mockAdapter, times(1)).getCredentials("dev-cluster", "portal-role");
    }

    @Test
    void approachingExpiryTriggersProactiveRefresh() throws Exception {
        // 1000s TTL but only 100s remaining → within 20% threshold (200s)
        ClusterCredential nearExpiry = new ClusterCredential("old-token", 1000, Instant.now().plusSeconds(100));
        injectCacheEntry("dev-cluster", "portal-role", nearExpiry);

        ClusterCredential fresh = ClusterCredential.of("refreshed-token", 3600);
        when(mockAdapter.getCredentials("dev-cluster", "portal-role")).thenReturn(fresh);

        ClusterCredential result = provider.getCredentials("dev-cluster", "portal-role");

        assertEquals("refreshed-token", result.token());
        verify(mockAdapter, times(1)).getCredentials("dev-cluster", "portal-role");
    }

    @Test
    void differentCacheKeysAreSeparate() {
        ClusterCredential cred1 = ClusterCredential.of("token-dev", 3600);
        ClusterCredential cred2 = ClusterCredential.of("token-staging", 3600);
        when(mockAdapter.getCredentials("dev-cluster", "portal-role")).thenReturn(cred1);
        when(mockAdapter.getCredentials("staging-cluster", "portal-role")).thenReturn(cred2);

        ClusterCredential result1 = provider.getCredentials("dev-cluster", "portal-role");
        ClusterCredential result2 = provider.getCredentials("staging-cluster", "portal-role");

        assertEquals("token-dev", result1.token());
        assertEquals("token-staging", result2.token());
        verify(mockAdapter).getCredentials("dev-cluster", "portal-role");
        verify(mockAdapter).getCredentials("staging-cluster", "portal-role");
    }

    @Test
    void concurrentRequestsForSameKeyDoNotDuplicateFetches() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger(0);
        when(mockAdapter.getCredentials("dev-cluster", "portal-role")).thenAnswer(invocation -> {
            fetchCount.incrementAndGet();
            Thread.sleep(50);
            return ClusterCredential.of("token", 3600);
        });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    provider.getCredentials("dev-cluster", "portal-role");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // ConcurrentHashMap.compute serializes updates per key — at most a small number of fetches
        assertTrue(fetchCount.get() <= 2,
                "Expected at most 2 adapter calls for concurrent requests, got " + fetchCount.get());
    }

    @Test
    void validCacheEntryIsNotRefreshed() throws Exception {
        // TTL 3600s, far from expiry → no refresh needed
        ClusterCredential valid = ClusterCredential.of("valid-token", 3600);
        injectCacheEntry("dev-cluster", "portal-role", valid);

        ClusterCredential result = provider.getCredentials("dev-cluster", "portal-role");

        assertEquals("valid-token", result.token());
        verifyNoInteractions(mockAdapter);
    }

    @SuppressWarnings("unchecked")
    private void injectCacheEntry(String cluster, String role, ClusterCredential credential) throws Exception {
        Field cacheField = SecretManagerCredentialProvider.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        ConcurrentHashMap<String, ClusterCredential> cache =
                (ConcurrentHashMap<String, ClusterCredential>) cacheField.get(provider);
        cache.put(cluster + "::" + role, credential);
    }
}
