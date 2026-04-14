import { forwardRef, useRef, useState } from 'react';
import {
  Alert,
  Card,
  CardHeader,
  CardBody,
  CardFooter,
  Label,
  Button,
  Content,
  Dropdown,
  DropdownItem,
  DropdownList,
  Flex,
  FlexItem,
  MenuToggle,
  Spinner,
  Tooltip,
} from '@patternfly/react-core';
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  SyncAltIcon,
  MinusCircleIcon,
} from '@patternfly/react-icons';
import { Table, Thead, Tr, Th, Tbody, Td } from '@patternfly/react-table';
import { DeepLinkButton } from '../shared/DeepLinkButton';
import { PromotionConfirmation } from './PromotionConfirmation';
import { useDeployments } from '../../hooks/useDeployments';
import { useAuth } from '../../hooks/useAuth';
import { triggerDeployment } from '../../api/deployments';
import type { EnvironmentChainEntry, EnvironmentStatus } from '../../types/environment';
import type { EnvironmentHealthDto, HealthStatus } from '../../types/health';
import type { DeploymentStatus } from '../../types/deployment';
import type { ReleaseSummary } from '../../types/release';

type DisplayStatus = EnvironmentStatus | 'DEGRADED';

const HEALTH_SEVERITY: Record<HealthStatus, number> = {
  NO_DATA: -1,
  HEALTHY: 0,
  DEGRADED: 1,
  UNHEALTHY: 2,
};

const ENV_STATUS_TO_HEALTH: Partial<Record<EnvironmentStatus, HealthStatus>> = {
  HEALTHY: 'HEALTHY',
  UNHEALTHY: 'UNHEALTHY',
};

/**
 * When Prometheus and ArgoCD disagree, display the more severe status.
 * DEPLOYING and NOT_DEPLOYED are ArgoCD-only states that Prometheus cannot override.
 * NO_DATA from Prometheus means no enrichment — keep ArgoCD status.
 * UNKNOWN from ArgoCD can be overridden by any Prometheus data.
 */
function mergeHealthStatus(
  argoStatus: EnvironmentStatus,
  healthInfo?: EnvironmentHealthDto,
): DisplayStatus {
  if (!healthInfo?.healthStatus) return argoStatus;
  const promStatus = healthInfo.healthStatus.status;
  if (promStatus === 'NO_DATA') return argoStatus;
  if (argoStatus === 'DEPLOYING' || argoStatus === 'NOT_DEPLOYED') return argoStatus;

  if (argoStatus === 'UNKNOWN') {
    if (promStatus === 'UNHEALTHY') return 'UNHEALTHY';
    if (promStatus === 'DEGRADED') return 'DEGRADED';
    return 'HEALTHY';
  }

  const argoHealth = ENV_STATUS_TO_HEALTH[argoStatus];
  if (!argoHealth) return argoStatus;

  return HEALTH_SEVERITY[promStatus] > HEALTH_SEVERITY[argoHealth]
    ? (promStatus as DisplayStatus)
    : argoStatus;
}

interface EnvironmentCardProps {
  entry: EnvironmentChainEntry;
  isProduction?: boolean;
  nextEnvName?: string;
  nextEnvironmentId?: number;
  nextIsProduction?: boolean;
  nextNamespace?: string;
  nextCluster?: string | null;
  isFirstNotDeployed?: boolean;
  teamId?: string;
  appId?: string;
  releases?: ReleaseSummary[] | null;
  healthInfo?: EnvironmentHealthDto;
  onDeploymentInitiated?: () => void;
}

interface StatusConfig {
  borderColor: string;
  labelProps: { status?: 'success' | 'warning' | 'danger'; color?: 'grey' };
  icon: React.ReactNode;
  text: string;
}

function getStatusConfig(
  status: DisplayStatus,
  deployedVersion: string | null,
): StatusConfig {
  switch (status) {
    case 'HEALTHY':
      return {
        borderColor: 'var(--pf-t--global--color--status--success--default)',
        labelProps: { status: 'success' },
        icon: <CheckCircleIcon />,
        text: '✓ Healthy',
      };
    case 'UNHEALTHY':
      return {
        borderColor: 'var(--pf-t--global--color--status--danger--default)',
        labelProps: { status: 'danger' },
        icon: <ExclamationCircleIcon />,
        text: '✕ Unhealthy',
      };
    case 'DEGRADED':
      return {
        borderColor: 'var(--pf-t--global--color--status--warning--default)',
        labelProps: { status: 'warning' },
        icon: <SyncAltIcon />,
        text: '⟳ Degraded',
      };
    case 'DEPLOYING':
      return {
        borderColor: 'var(--pf-t--global--color--status--warning--default)',
        labelProps: { status: 'warning' },
        icon: <SyncAltIcon />,
        text: `⟳ Deploying ${deployedVersion ?? ''}...`.trim(),
      };
    case 'NOT_DEPLOYED':
      return {
        borderColor: 'var(--pf-t--global--color--nonstatus--gray--default)',
        labelProps: { color: 'grey' },
        icon: <MinusCircleIcon />,
        text: '— Not deployed',
      };
    case 'UNKNOWN':
    default:
      return {
        borderColor: 'var(--pf-t--global--color--nonstatus--gray--default)',
        labelProps: { color: 'grey' },
        icon: undefined as unknown as React.ReactNode,
        text: 'Status unavailable',
      };
  }
}

function relativeTime(isoString: string | null): string {
  if (!isoString) return '';
  const seconds = Math.floor(
    (Date.now() - new Date(isoString).getTime()) / 1000,
  );
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

function getDeploymentStatusLabel(status: DeploymentStatus) {
  switch (status) {
    case 'Deployed':
      return { status: 'success' as const, icon: <CheckCircleIcon /> };
    case 'Deploying':
      return { status: 'warning' as const, icon: <SyncAltIcon /> };
    case 'Failed':
      return { status: 'danger' as const, icon: <ExclamationCircleIcon /> };
  }
}

export const EnvironmentCard = forwardRef<HTMLDivElement, EnvironmentCardProps>(
  function EnvironmentCard({
    entry,
    isProduction = false,
    nextEnvName,
    nextEnvironmentId,
    nextIsProduction = false,
    nextNamespace,
    nextCluster,
    isFirstNotDeployed = false,
    teamId,
    appId,
    releases,
    healthInfo,
    onDeploymentInitiated,
  }, ref) {
    const { role } = useAuth();
    const [isExpanded, setIsExpanded] = useState(false);
    const [isHovered, setIsHovered] = useState(false);
    const [pendingAction, setPendingAction] = useState<'deploy' | 'promote' | null>(null);
    const [deployError, setDeployError] = useState<string | null>(null);
    const [isDropdownOpen, setIsDropdownOpen] = useState(false);
    const [showConfirmation, setShowConfirmation] = useState(false);
    const [confirmationVersion, setConfirmationVersion] = useState<string>('');
    const [confirmationType, setConfirmationType] = useState<'deploy' | 'promote'>('deploy');
    const promoteButtonRef = useRef<HTMLElement | null>(null);
    const deployButtonRef = useRef<HTMLElement | null>(null);

    const environmentId = isExpanded ? entry.environmentId : null;
    const { data: deployments, error: deploymentsError, isLoading: deploymentsLoading } =
      useDeployments(teamId, appId, environmentId);

    const isMember = role === 'member';
    const hasReleases = releases != null && releases.length > 0;
    const hasDeploymentContext = teamId != null && appId != null;
    const canDeployToCurrentEnvironment =
      hasDeploymentContext && entry.environmentId != null;
    const canPromoteToNextEnvironment =
      hasDeploymentContext &&
      nextEnvironmentId != null &&
      entry.deployedVersion != null;

    const showDeploy =
      hasReleases &&
      canDeployToCurrentEnvironment &&
      (
        entry.status === 'HEALTHY' ||
        (entry.status === 'NOT_DEPLOYED' && isFirstNotDeployed)
      ) &&
      !(isProduction && isMember);

    const showPromote =
      entry.status === 'HEALTHY' &&
      nextEnvName != null &&
      canPromoteToNextEnvironment;

    const promoteDisabled = nextIsProduction && isMember;

    function requestConfirmation(type: 'deploy' | 'promote', version: string) {
      setConfirmationType(type);
      setConfirmationVersion(version);
      setShowConfirmation(true);
    }

    function cancelConfirmation() {
      setShowConfirmation(false);
      setConfirmationVersion('');
    }

    async function executeDeployment(version: string, envId: number, isProd: boolean) {
      if (!teamId || !appId) return;
      const actionType = confirmationType;
      setPendingAction(actionType);
      setDeployError(null);
      setShowConfirmation(false);
      try {
        await triggerDeployment(
          teamId,
          appId,
          { releaseVersion: version, environmentId: envId },
          isProd || undefined,
        );
        onDeploymentInitiated?.();
      } catch (e) {
        setDeployError(
          e instanceof Error ? e.message : actionType === 'promote' ? 'Promotion failed' : 'Deployment failed',
        );
      } finally {
        setPendingAction(null);
      }
    }

    function handleDeploySelect(releaseVersion: string) {
      if (pendingAction != null) return;
      setIsDropdownOpen(false);
      requestConfirmation('deploy', releaseVersion);
    }

    function handlePromoteClick() {
      if (!entry.deployedVersion || pendingAction != null) return;
      requestConfirmation('promote', entry.deployedVersion);
    }

    function handleConfirm() {
      if (confirmationType === 'promote' && nextEnvironmentId != null) {
        executeDeployment(confirmationVersion, nextEnvironmentId, nextIsProduction);
      } else if (confirmationType === 'deploy' && entry.environmentId != null) {
        executeDeployment(confirmationVersion, entry.environmentId, isProduction);
      }
    }

    const confirmationIsProduction =
      confirmationType === 'promote' ? nextIsProduction : isProduction;
    const confirmationEnvName =
      confirmationType === 'promote' ? (nextEnvName ?? '') : entry.environmentName;
    const confirmationNamespace =
      confirmationType === 'promote' ? (nextNamespace ?? '') : entry.namespace;
    const confirmationCluster =
      confirmationType === 'promote' ? (nextCluster ?? null) : entry.clusterName;
    const confirmationTriggerRef =
      confirmationType === 'promote' ? promoteButtonRef : deployButtonRef;

    const displayStatus = mergeHealthStatus(entry.status, healthInfo);
    const config = getStatusConfig(displayStatus, entry.deployedVersion);
    const statusLabel =
      displayStatus === 'UNKNOWN'
        ? 'status unavailable'
        : displayStatus.toLowerCase().replace('_', ' ');
    const ariaLabel = `${entry.environmentName} environment, version ${entry.deployedVersion ?? 'none'}, ${statusLabel}`;

    return (
      <div
        ref={ref}
        tabIndex={0}
        aria-label={ariaLabel}
        onClick={() => setIsExpanded((prev) => !prev)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setIsExpanded((prev) => !prev);
          }
        }}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        style={{ cursor: 'pointer' }}
      >
        <Card
          style={{
            borderTop: `3px solid ${config.borderColor}`,
            boxShadow: isHovered
              ? 'var(--pf-t--global--box-shadow--md)'
              : undefined,
            transition: 'box-shadow 0.15s ease-in-out',
          }}
        >
          <CardHeader>
            <Flex
              direction={{ default: 'column' }}
              spaceItems={{ default: 'spaceItemsSm' }}
            >
              <FlexItem>
                <Content component="h4">{entry.environmentName}</Content>
              </FlexItem>
              <FlexItem>
                <Label
                  {...config.labelProps}
                  icon={config.icon}
                >
                  {config.text}
                </Label>
                {healthInfo?.healthStatus &&
                  healthInfo.healthStatus.status !== 'NO_DATA' &&
                  displayStatus !== entry.status && (
                    <Content
                      component="small"
                      style={{
                        marginLeft: 'var(--pf-t--global--spacer--sm)',
                        color: 'var(--pf-t--global--text--color--subtle)',
                      }}
                    >
                      Prometheus: {healthInfo.healthStatus.status.charAt(0) + healthInfo.healthStatus.status.slice(1).toLowerCase()}
                    </Content>
                  )}
              </FlexItem>
            </Flex>
          </CardHeader>

          <CardBody>
            {entry.deployedVersion && (
              <Content component="p">{entry.deployedVersion}</Content>
            )}
            {entry.lastDeployedAt && (
              <Content component="small">
                {relativeTime(entry.lastDeployedAt)}
              </Content>
            )}
          </CardBody>

          {isExpanded && (
            <CardBody onClick={(e) => e.stopPropagation()}>
              <Flex
                direction={{ default: 'column' }}
                spaceItems={{ default: 'spaceItemsSm' }}
              >
                <FlexItem>
                  <Content component="small">
                    Namespace: {entry.namespace}
                  </Content>
                </FlexItem>
                {entry.clusterName && (
                  <FlexItem>
                    <Content component="small">
                      Cluster: {entry.clusterName}
                    </Content>
                  </FlexItem>
                )}
                {entry.argocdDeepLink && (
                  <FlexItem>
                    <DeepLinkButton
                      href={entry.argocdDeepLink}
                      toolName="ArgoCD"
                      ariaLabel={`Open ${entry.environmentName} in ArgoCD`}
                    />
                  </FlexItem>
                )}
                <FlexItem>
                  <DeepLinkButton
                    href={entry.vaultDeepLink}
                    toolName="Vault"
                    ariaLabel={`Open ${entry.environmentName} secrets in Vault`}
                  />
                </FlexItem>
                <FlexItem>
                  <DeepLinkButton
                    href={entry.grafanaDeepLink}
                    toolName="Grafana"
                    label="View in Grafana ↗"
                    ariaLabel={`Open ${entry.environmentName} in Grafana`}
                  />
                </FlexItem>
                <FlexItem>
                  {deploymentsLoading && (
                    <Flex spaceItems={{ default: 'spaceItemsSm' }} alignItems={{ default: 'alignItemsCenter' }}>
                      <FlexItem>
                        <Spinner size="md" aria-label="Loading deployment history" />
                      </FlexItem>
                      <FlexItem>
                        <Content component="small">Loading deployment history...</Content>
                      </FlexItem>
                    </Flex>
                  )}
                  {deploymentsError && (
                    <Alert variant="danger" title="Failed to load deployment history" isInline isPlain />
                  )}
                  {deployments && deployments.length === 0 && (
                    <Content component="small">No deployments yet</Content>
                  )}
                  {deployments && deployments.length > 0 && (
                    <Table aria-label="Deployment history" variant="compact" borders={false}>
                      <Thead>
                        <Tr>
                          <Th>Version</Th>
                          <Th>Status</Th>
                          <Th>When</Th>
                          <Th>By</Th>
                        </Tr>
                      </Thead>
                      <Tbody>
                        {deployments.map((dep, i) => {
                          const labelConfig = getDeploymentStatusLabel(dep.status);
                          const isMostRecent = i === 0;
                          return (
                            <Tr key={dep.deploymentId}>
                              <Td style={isMostRecent ? { fontWeight: 'bold' } : undefined}>
                                {dep.releaseVersion}
                              </Td>
                              <Td>
                                <Label
                                  status={labelConfig.status}
                                  icon={labelConfig.icon}
                                  isCompact
                                >
                                  {dep.status}
                                </Label>
                                {dep.status === 'Failed' && dep.argocdDeepLink && (
                                  <>
                                    {' '}
                                    <DeepLinkButton
                                      href={dep.argocdDeepLink}
                                      toolName="ArgoCD"
                                      ariaLabel={`Investigate ${dep.releaseVersion} failure in ArgoCD`}
                                    />
                                  </>
                                )}
                              </Td>
                              <Td style={isMostRecent ? { fontWeight: 'bold' } : undefined}>
                                {relativeTime(dep.startedAt)}
                              </Td>
                              <Td style={isMostRecent ? { fontWeight: 'bold' } : undefined}>
                                {dep.deployedBy}
                              </Td>
                            </Tr>
                          );
                        })}
                      </Tbody>
                    </Table>
                  )}
                </FlexItem>
              </Flex>
            </CardBody>
          )}

          <CardFooter onClick={(e) => e.stopPropagation()}>
            <Flex direction={{ default: 'column' }} spaceItems={{ default: 'spaceItemsSm' }}>
              {showDeploy && (
                <FlexItem>
                  <Dropdown
                    isOpen={isDropdownOpen}
                    onSelect={() => setIsDropdownOpen(false)}
                    onOpenChange={setIsDropdownOpen}
                    toggle={(toggleRef) => (
                      <MenuToggle
                        ref={(el) => {
                          (toggleRef as React.MutableRefObject<HTMLElement | null>).current = el;
                          deployButtonRef.current = el;
                        }}
                        onClick={(e) => {
                          e.stopPropagation();
                          setIsDropdownOpen((prev) => !prev);
                        }}
                        isExpanded={isDropdownOpen}
                        isDisabled={pendingAction != null}
                        variant="secondary"
                        data-testid="deploy-toggle"
                      >
                        {pendingAction === 'deploy' ? (
                          <>
                            <Spinner size="sm" aria-label="Deploying" />{' '}
                            Deploying...
                          </>
                        ) : (
                          'Deploy'
                        )}
                      </MenuToggle>
                    )}
                  >
                    <DropdownList>
                      {releases!.map((release) => (
                        <DropdownItem
                          key={release.version}
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeploySelect(release.version);
                          }}
                        >
                          {release.version} — {relativeTime(release.createdAt)}
                        </DropdownItem>
                      ))}
                    </DropdownList>
                  </Dropdown>
                </FlexItem>
              )}
              {showPromote && (
                <FlexItem>
                  {promoteDisabled ? (
                    <Tooltip content="Production deployments require team lead approval">
                      <span tabIndex={0} ref={promoteButtonRef as React.Ref<HTMLElement>}>
                        <Button
                          variant="secondary"
                          size="sm"
                          isDisabled
                          aria-disabled="true"
                        >
                          {`Promote to ${nextEnvName}`}
                        </Button>
                      </span>
                    </Tooltip>
                  ) : (
                    <Button
                      ref={promoteButtonRef as React.Ref<HTMLElement>}
                      variant="secondary"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        handlePromoteClick();
                      }}
                      isDisabled={pendingAction != null}
                    >
                      {pendingAction === 'promote' ? (
                        <>
                          <Spinner size="sm" aria-label="Promoting" />{' '}
                          Promoting...
                        </>
                      ) : (
                        `Promote to ${nextEnvName}`
                      )}
                    </Button>
                  )}
                </FlexItem>
              )}
              {entry.status === 'UNHEALTHY' && entry.argocdDeepLink && (
                <FlexItem>
                  <DeepLinkButton
                    href={entry.argocdDeepLink}
                    toolName="ArgoCD"
                    ariaLabel={`Open ${entry.environmentName} in ArgoCD`}
                  />
                </FlexItem>
              )}
              {deployError && (
                <FlexItem>
                  <Alert variant="danger" title={deployError} isInline isPlain />
                </FlexItem>
              )}
            </Flex>
            {showConfirmation && (
              <PromotionConfirmation
                version={confirmationVersion}
                targetEnvName={confirmationEnvName}
                targetNamespace={confirmationNamespace}
                targetCluster={confirmationCluster}
                isProduction={confirmationIsProduction}
                actionType={confirmationType}
                isOpen={showConfirmation}
                onConfirm={handleConfirm}
                onCancel={cancelConfirmation}
                triggerRef={confirmationTriggerRef}
              />
            )}
          </CardFooter>
        </Card>
      </div>
    );
  },
);
