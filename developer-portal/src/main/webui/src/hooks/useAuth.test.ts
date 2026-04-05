import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useAuth } from './useAuth';

describe('useAuth', () => {
  it('returns authentication info with expected shape', () => {
    const { result } = renderHook(() => useAuth());
    expect(result.current).toHaveProperty('username');
    expect(result.current).toHaveProperty('teamName');
    expect(result.current).toHaveProperty('teamId');
    expect(result.current).toHaveProperty('role');
    expect(result.current).toHaveProperty('isAuthenticated');
  });

  it('returns isAuthenticated as true (MVP stub)', () => {
    const { result } = renderHook(() => useAuth());
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('returns a valid role value', () => {
    const { result } = renderHook(() => useAuth());
    expect(['member', 'lead', 'admin']).toContain(result.current.role);
  });

  it('returns non-empty username and team info', () => {
    const { result } = renderHook(() => useAuth());
    expect(result.current.username).toBeTruthy();
    expect(result.current.teamName).toBeTruthy();
    expect(result.current.teamId).toBeTruthy();
  });
});
