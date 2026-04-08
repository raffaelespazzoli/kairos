import { useState, useEffect, useCallback, useRef } from 'react';
import { fetchBuilds, triggerBuild, fetchBuildDetail, fetchBuildLogs } from '../api/builds';
import { ApiError } from '../api/client';
import type { BuildSummary, BuildDetail } from '../types/build';
import type { PortalError } from '../types/error';

interface UseBuildsResult {
  data: BuildSummary[] | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: () => void;
  prepend: (build: BuildSummary) => void;
}

export function useBuilds(
  teamId: string | undefined,
  appId: string | undefined,
): UseBuildsResult {
  const [data, setData] = useState<BuildSummary[] | null>(null);
  const [error, setError] = useState<PortalError | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const load = useCallback(async () => {
    if (!teamId || !appId) return;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setIsLoading(true);
    setError(null);
    try {
      const builds = await fetchBuilds(teamId, appId, controller.signal);
      if (!controller.signal.aborted) setData(builds);
    } catch (e) {
      if (!controller.signal.aborted) {
        setData(null);
        setError(toPortalError(e));
      }
    } finally {
      if (!controller.signal.aborted) setIsLoading(false);
    }
  }, [teamId, appId]);

  useEffect(() => {
    load();
    return () => {
      abortRef.current?.abort();
    };
  }, [load]);

  const prepend = useCallback((build: BuildSummary) => {
    setData((prev) => (prev ? [build, ...prev] : [build]));
  }, []);

  return { data, error, isLoading, refresh: load, prepend };
}

interface UseTriggerBuildResult {
  trigger: () => Promise<BuildSummary | null>;
  error: PortalError | null;
  isTriggering: boolean;
}

export function useTriggerBuild(
  teamId: string | undefined,
  appId: string | undefined,
): UseTriggerBuildResult {
  const [error, setError] = useState<PortalError | null>(null);
  const [isTriggering, setIsTriggering] = useState(false);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const trigger = useCallback(async (): Promise<BuildSummary | null> => {
    if (!teamId || !appId) return null;
    setIsTriggering(true);
    setError(null);
    try {
      const result = await triggerBuild(teamId, appId);
      return result;
    } catch (e) {
      if (mountedRef.current) setError(toPortalError(e));
      return null;
    } finally {
      if (mountedRef.current) setIsTriggering(false);
    }
  }, [teamId, appId]);

  return { trigger, error, isTriggering };
}

interface UseBuildDetailResult {
  data: BuildDetail | null;
  error: PortalError | null;
  isLoading: boolean;
  load: () => void;
}

export function useBuildDetail(
  teamId: string | undefined,
  appId: string | undefined,
  buildId: string | null,
): UseBuildDetailResult {
  const [data, setData] = useState<BuildDetail | null>(null);
  const [error, setError] = useState<PortalError | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const load = useCallback(async () => {
    if (!teamId || !appId || !buildId) return;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setIsLoading(true);
    setError(null);
    try {
      const detail = await fetchBuildDetail(teamId, appId, buildId, controller.signal);
      if (!controller.signal.aborted) setData(detail);
    } catch (e) {
      if (!controller.signal.aborted) {
        setData(null);
        setError(toPortalError(e));
      }
    } finally {
      if (!controller.signal.aborted) setIsLoading(false);
    }
  }, [teamId, appId, buildId]);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  return { data, error, isLoading, load };
}

interface UseBuildLogsResult {
  logs: string | null;
  error: PortalError | null;
  isLoading: boolean;
  load: () => void;
}

export function useBuildLogs(
  teamId: string | undefined,
  appId: string | undefined,
  buildId: string | null,
): UseBuildLogsResult {
  const [logs, setLogs] = useState<string | null>(null);
  const [error, setError] = useState<PortalError | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const load = useCallback(async () => {
    if (!teamId || !appId || !buildId) return;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setIsLoading(true);
    setError(null);
    try {
      const text = await fetchBuildLogs(teamId, appId, buildId, controller.signal);
      if (!controller.signal.aborted) setLogs(text);
    } catch (e) {
      if (!controller.signal.aborted) {
        setLogs(null);
        setError(toPortalError(e));
      }
    } finally {
      if (!controller.signal.aborted) setIsLoading(false);
    }
  }, [teamId, appId, buildId]);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  return { logs, error, isLoading, load };
}

function toPortalError(e: unknown): PortalError {
  if (e instanceof ApiError) return e.portalError;
  if (e instanceof DOMException && e.name === 'AbortError') {
    return {
      error: 'aborted',
      message: 'Request was cancelled',
      timestamp: new Date().toISOString(),
    };
  }
  return {
    error: 'unknown',
    message: e instanceof Error ? e.message : 'An unexpected error occurred',
    timestamp: new Date().toISOString(),
  };
}
