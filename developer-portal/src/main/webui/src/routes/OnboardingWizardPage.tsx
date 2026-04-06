import { useState, useCallback, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import {
  PageSection,
  Title,
  Wizard,
  WizardStep,
  Form,
  FormGroup,
  TextInput,
  Button,
  Alert,
  Spinner,
  useWizardContext,
} from '@patternfly/react-core';
import { useAuth } from '../hooks/useAuth';
import { validateRepo, buildPlan, confirmOnboarding } from '../api/onboarding';
import { fetchClusters } from '../api/clusters';
import { ApiError } from '../api/client';
import type {
  ContractValidationResult,
  OnboardingPlanResult,
  OnboardingResult,
  ProvisioningStep,
} from '../types/onboarding';
import type { Cluster } from '../types/cluster';
import type { PortalError } from '../types/error';
import { ContractValidationChecklist } from '../components/onboarding/ContractValidationChecklist';
import { ProvisioningPlanPreview } from '../components/onboarding/ProvisioningPlanPreview';
import { ProvisioningProgressTracker } from '../components/onboarding/ProvisioningProgressTracker';
import { OnboardingCompletionPanel } from '../components/onboarding/OnboardingCompletionPanel';

export function OnboardingWizardPage() {
  const { teamId } = useParams<{ teamId: string }>();
  const auth = useAuth();
  const resolvedTeamId = teamId ?? auth.teamId;

  const [repoUrl, setRepoUrl] = useState('');
  const [validationResult, setValidationResult] = useState<ContractValidationResult | null>(null);
  const [error, setError] = useState<PortalError | null>(null);
  const [isValidating, setIsValidating] = useState(false);

  const [clusters, setClusters] = useState<Cluster[]>([]);
  const [plan, setPlan] = useState<OnboardingPlanResult | null>(null);
  const [clusterAssignments, setClusterAssignments] = useState<Record<string, number>>({});
  const [buildClusterId, setBuildClusterId] = useState<number | null>(null);
  const [isPlanLoading, setIsPlanLoading] = useState(false);
  const [planError, setPlanError] = useState<PortalError | null>(null);
  const [planStepEntered, setPlanStepEntered] = useState(false);

  const [provisioningSteps, setProvisioningSteps] = useState<ProvisioningStep[]>(
    createInitialProvisioningSteps(),
  );
  const [confirmResult, setConfirmResult] = useState<OnboardingResult | null>(null);
  const activeTimersRef = useRef<ReturnType<typeof setTimeout>[]>([]);

  useEffect(() => {
    return () => {
      activeTimersRef.current.forEach(clearTimeout);
    };
  }, []);

  const handleValidate = useCallback(async () => {
    setIsValidating(true);
    setError(null);
    setValidationResult(null);
    setPlan(null);
    setPlanStepEntered(false);
    try {
      const result = await validateRepo(resolvedTeamId, repoUrl);
      setValidationResult(result);
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.portalError);
      } else {
        setError({
          error: 'unknown',
          message: 'An unexpected error occurred',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsValidating(false);
    }
  }, [resolvedTeamId, repoUrl]);

  const handleRetry = useCallback(async () => {
    setIsValidating(true);
    setError(null);
    try {
      const result = await validateRepo(resolvedTeamId, repoUrl);
      setValidationResult(result);
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.portalError);
      } else {
        setError({
          error: 'unknown',
          message: 'An unexpected error occurred',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsValidating(false);
    }
  }, [resolvedTeamId, repoUrl]);

  const fetchPlan = useCallback(
    async (envMap: Record<string, number>, bClusterId: number) => {
      if (!validationResult) return;
      setIsPlanLoading(true);
      setPlanError(null);
      try {
        const appName = extractAppName(repoUrl);
        const result = await buildPlan(resolvedTeamId, {
          gitRepoUrl: repoUrl,
          appName,
          runtimeType: validationResult.runtimeType ?? '',
          detectedEnvironments: validationResult.detectedEnvironments,
          environmentClusterMap: envMap,
          buildClusterId: bClusterId,
        });
        setPlan(result);
      } catch (e) {
        if (e instanceof ApiError) {
          setPlanError(e.portalError);
        } else {
          setPlanError({
            error: 'unknown',
            message: 'Failed to build provisioning plan',
            timestamp: new Date().toISOString(),
          });
        }
      } finally {
        setIsPlanLoading(false);
      }
    },
    [resolvedTeamId, repoUrl, validationResult],
  );

  useEffect(() => {
    if (!planStepEntered || !validationResult?.allPassed) return;

    let cancelled = false;

    async function loadClustersAndPlan() {
      try {
        const clusterList = await fetchClusters();
        if (cancelled) return;
        setClusters(clusterList);

        if (clusterList.length > 0) {
          const defaultClusterId = clusterList[0].id;
          const defaultEnvMap: Record<string, number> = {};
          for (const env of validationResult!.detectedEnvironments) {
            defaultEnvMap[env] = defaultClusterId;
          }
          setClusterAssignments(defaultEnvMap);
          setBuildClusterId(defaultClusterId);
          await fetchPlan(defaultEnvMap, defaultClusterId);
        }
      } catch {
        if (!cancelled) {
          setPlanError({
            error: 'unknown',
            message: 'Failed to load clusters',
            timestamp: new Date().toISOString(),
          });
        }
      }
    }

    loadClustersAndPlan();
    return () => {
      cancelled = true;
    };
  }, [planStepEntered, validationResult, fetchPlan]);

  const handleClusterChange = useCallback(
    (envName: string, clusterId: number) => {
      const newMap = { ...clusterAssignments, [envName]: clusterId };
      setClusterAssignments(newMap);
      if (buildClusterId !== null) {
        fetchPlan(newMap, buildClusterId);
      }
    },
    [clusterAssignments, buildClusterId, fetchPlan],
  );

  const handleBuildClusterChange = useCallback(
    (clusterId: number) => {
      setBuildClusterId(clusterId);
      fetchPlan(clusterAssignments, clusterId);
    },
    [clusterAssignments, fetchPlan],
  );

  const runConfirm = useCallback(async () => {
    if (!validationResult || buildClusterId === null) return;

    setConfirmResult(null);
    setProvisioningSteps(createInitialProvisioningSteps());

    const appName = extractAppName(repoUrl);

    setProvisioningSteps((prev) => updateStepStatus(prev, 'branch', 'in-progress'));

    activeTimersRef.current.forEach(clearTimeout);
    activeTimersRef.current = [];

    const branchTimer = setTimeout(() => {
      setProvisioningSteps((prev) => {
        const s = updateStepStatus(prev, 'branch', 'completed');
        return updateStepStatus(s, 'commit', 'in-progress');
      });
    }, 500);
    activeTimersRef.current.push(branchTimer);

    const commitTimer = setTimeout(() => {
      setProvisioningSteps((prev) => {
        const s = updateStepStatus(prev, 'commit', 'completed');
        return updateStepStatus(s, 'pr', 'in-progress');
      });
    }, 1000);
    activeTimersRef.current.push(commitTimer);

    try {
      const result = await confirmOnboarding(resolvedTeamId, {
        appName,
        gitRepoUrl: repoUrl,
        runtimeType: validationResult.runtimeType ?? '',
        detectedEnvironments: validationResult.detectedEnvironments,
        environmentClusterMap: clusterAssignments,
        buildClusterId,
      });
      clearTimeout(branchTimer);
      clearTimeout(commitTimer);
      setProvisioningSteps([
        { id: 'branch', label: 'Creating branch in infra repo', status: 'completed' },
        { id: 'commit', label: 'Committing manifests', status: 'completed' },
        { id: 'pr', label: 'Creating pull request', status: 'completed' },
      ]);
      setConfirmResult(result);
    } catch (e) {
      clearTimeout(branchTimer);
      clearTimeout(commitTimer);
      const errorMsg =
        e instanceof ApiError
          ? e.portalError.message
          : 'An unexpected error occurred during onboarding';
      setProvisioningSteps((prev) => {
        const lastInProgress = [...prev].reverse().find((s) => s.status === 'in-progress');
        const failedId = lastInProgress?.id ?? 'pr';
        return prev.map((s) =>
          s.id === failedId ? { ...s, status: 'failed' as const, error: errorMsg } : s,
        );
      });
    } finally {
      // confirm flow complete
    }
  }, [resolvedTeamId, repoUrl, validationResult, clusterAssignments, buildClusterId]);

  const handleRetryConfirm = useCallback(() => {
    runConfirm();
  }, [runConfirm]);

  return (
    <PageSection>
      <Title headingLevel="h1">Onboard Application</Title>
      <Wizard
        onStepChange={(_event, currentStep) => {
          if (currentStep.id === 'plan') {
            setPlanStepEntered(true);
          }
        }}
      >
        <WizardStep name="Repository URL" id="repo-url">
          <AutoAdvance when={!!validationResult} toStep="contract-validation" />
          <Form
            onSubmit={(e) => {
              e.preventDefault();
              handleValidate();
            }}
          >
            <FormGroup label="Git Repository URL" isRequired fieldId="repo-url-input">
              <TextInput
                id="repo-url-input"
                isRequired
                value={repoUrl}
                onChange={(_event, value) => {
                  setRepoUrl(value);
                  if (validationResult) {
                    setValidationResult(null);
                    setPlan(null);
                    setPlanStepEntered(false);
                  }
                }}
                placeholder="https://github.com/team/app"
                isDisabled={isValidating}
              />
            </FormGroup>
            <Button
              variant="primary"
              onClick={handleValidate}
              isDisabled={!repoUrl.trim() || isValidating}
              isLoading={isValidating}
            >
              Validate Repository
            </Button>
          </Form>
          {isValidating && (
            <div className="pf-v6-u-mt-md">
              <Spinner size="md" aria-label="Validating repository" />
            </div>
          )}
          {error && (
            <Alert
              variant="danger"
              title={error.message}
              isInline
              className="pf-v6-u-mt-md"
            >
              {error.detail && <p>{error.detail}</p>}
            </Alert>
          )}
        </WizardStep>
        <WizardStep
          name="Contract Validation"
          id="contract-validation"
          isDisabled={!validationResult}
          footer={{
            isNextDisabled: !validationResult?.allPassed,
          }}
        >
          {validationResult && (
            <ContractValidationChecklist
              result={validationResult}
              onRetry={handleRetry}
              isRetrying={isValidating}
            />
          )}
        </WizardStep>
        <WizardStep
          name="Provisioning Plan"
          id="plan"
          isDisabled={!validationResult?.allPassed}
          footer={{
            nextButtonText: 'Confirm & Create PR',
            isNextDisabled: !plan || isPlanLoading,
          }}
        >
          {isPlanLoading && (
            <div className="pf-v6-u-mt-md">
              <Spinner size="lg" aria-label="Building provisioning plan" />
            </div>
          )}
          {planError && (
            <Alert
              variant="danger"
              title={planError.message}
              isInline
              className="pf-v6-u-mt-md"
            >
              {planError.detail && <p>{planError.detail}</p>}
            </Alert>
          )}
          {!isPlanLoading && !planError && !plan && clusters.length === 0 && planStepEntered && (
            <Alert
              variant="warning"
              title="No clusters registered"
              isInline
              className="pf-v6-u-mt-md"
            >
              No clusters have been registered yet. Ask a platform admin to register
              at least one cluster before onboarding applications.
            </Alert>
          )}
          {plan && !isPlanLoading && (
            <ProvisioningPlanPreview
              plan={plan}
              clusters={clusters}
              onClusterChange={handleClusterChange}
              onBuildClusterChange={handleBuildClusterChange}
            />
          )}
        </WizardStep>
        <WizardStep
          name="Create PR"
          id="create-pr"
          isDisabled={!plan}
          footer={{
            isBackDisabled: true,
            isNextDisabled: !confirmResult,
          }}
        >
          <CreatePrStepBody
            onEnter={runConfirm}
            provisioningSteps={provisioningSteps}
            onRetry={handleRetryConfirm}
          />
        </WizardStep>
        <WizardStep
          name="Complete"
          id="complete"
          isDisabled={!confirmResult}
          footer={{ isBackDisabled: true, isCancelHidden: true }}
        >
          {confirmResult && (
            <OnboardingCompletionPanel
              result={confirmResult}
              teamId={resolvedTeamId}
            />
          )}
        </WizardStep>
      </Wizard>
    </PageSection>
  );
}

function AutoAdvance({ when, toStep }: { when: boolean; toStep: string }) {
  const { goToStepById } = useWizardContext();
  const prev = useRef(false);
  useEffect(() => {
    if (when && !prev.current) {
      goToStepById(toStep);
    }
    prev.current = when;
  }, [when, toStep, goToStepById]);
  return null;
}

function CreatePrStepBody({
  onEnter,
  provisioningSteps,
  onRetry,
}: {
  onEnter: () => void;
  provisioningSteps: ProvisioningStep[];
  onRetry: () => void;
}) {
  const triggered = useRef(false);
  useEffect(() => {
    if (!triggered.current) {
      triggered.current = true;
      onEnter();
    }
  }, [onEnter]);

  return (
    <ProvisioningProgressTracker
      steps={provisioningSteps}
      totalSteps={3}
      onRetry={onRetry}
    />
  );
}

function createInitialProvisioningSteps(): ProvisioningStep[] {
  return [
    { id: 'branch', label: 'Creating branch in infra repo', status: 'pending' },
    { id: 'commit', label: 'Committing manifests', status: 'pending' },
    { id: 'pr', label: 'Creating pull request', status: 'pending' },
  ];
}

function updateStepStatus(
  steps: ProvisioningStep[],
  stepId: string,
  status: ProvisioningStep['status'],
): ProvisioningStep[] {
  return steps.map((s) => (s.id === stepId ? { ...s, status } : s));
}

function extractAppName(repoUrl: string): string {
  const cleaned = repoUrl.replace(/\.git$/, '').replace(/\/$/, '');
  const lastSlash = cleaned.lastIndexOf('/');
  const raw = lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
  return slugify(raw);
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
