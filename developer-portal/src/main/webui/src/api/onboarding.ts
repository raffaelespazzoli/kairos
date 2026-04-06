import { apiFetch } from './client';
import type {
  ContractValidationResult,
  ValidateRepoRequest,
  OnboardingPlanRequest,
  OnboardingPlanResult,
  OnboardingConfirmRequest,
  OnboardingResult,
} from '../types/onboarding';

export function validateRepo(
  teamId: string,
  gitRepoUrl: string,
): Promise<ContractValidationResult> {
  return apiFetch<ContractValidationResult>(
    `/api/v1/teams/${teamId}/applications/onboard`,
    {
      method: 'POST',
      body: JSON.stringify({ gitRepoUrl } satisfies ValidateRepoRequest),
    },
  );
}

export function buildPlan(
  teamId: string,
  request: OnboardingPlanRequest,
): Promise<OnboardingPlanResult> {
  return apiFetch<OnboardingPlanResult>(
    `/api/v1/teams/${teamId}/applications/onboard/plan`,
    {
      method: 'POST',
      body: JSON.stringify(request),
    },
  );
}

export function confirmOnboarding(
  teamId: string,
  request: OnboardingConfirmRequest,
): Promise<OnboardingResult> {
  return apiFetch<OnboardingResult>(
    `/api/v1/teams/${teamId}/applications/onboard/confirm`,
    {
      method: 'POST',
      body: JSON.stringify(request),
    },
  );
}
