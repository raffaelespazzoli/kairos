import { forwardRef, useState } from 'react';
import {
  Card,
  CardHeader,
  CardBody,
  CardFooter,
  Label,
  Button,
  Content,
  Flex,
  FlexItem,
  Tooltip,
} from '@patternfly/react-core';
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  SyncAltIcon,
  MinusCircleIcon,
} from '@patternfly/react-icons';
import { DeepLinkButton } from '../shared/DeepLinkButton';
import type { EnvironmentChainEntry, EnvironmentStatus } from '../../types/environment';

interface EnvironmentCardProps {
  entry: EnvironmentChainEntry;
  nextEnvName?: string;
}

interface StatusConfig {
  borderColor: string;
  labelProps: { status?: 'success' | 'warning' | 'danger'; color?: 'grey' };
  icon: React.ReactNode;
  text: string;
}

function getStatusConfig(
  status: EnvironmentStatus,
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

export const EnvironmentCard = forwardRef<HTMLDivElement, EnvironmentCardProps>(
  function EnvironmentCard({ entry, nextEnvName }, ref) {
    const [isExpanded, setIsExpanded] = useState(false);
    const [isHovered, setIsHovered] = useState(false);

    const config = getStatusConfig(entry.status, entry.deployedVersion);
    const statusLabel =
      entry.status === 'UNKNOWN'
        ? 'status unavailable'
        : entry.status.toLowerCase().replace('_', ' ');
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
                  <Button variant="link" isInline isDisabled>
                    View in Grafana ↗
                  </Button>
                </FlexItem>
                <FlexItem>
                  <Content component="small">
                    Deployment history coming in Epic 5
                  </Content>
                </FlexItem>
              </Flex>
            </CardBody>
          )}

          <CardFooter onClick={(e) => e.stopPropagation()}>
            {entry.status === 'HEALTHY' && nextEnvName && (
              <Tooltip content="Promotion available in a future release">
                <Button variant="secondary" isDisabled size="sm">
                  Promote to {nextEnvName}
                </Button>
              </Tooltip>
            )}
            {entry.status === 'UNHEALTHY' && (
              <>
                <Tooltip content="Promotion available in a future release">
                  <Button variant="secondary" isDisabled size="sm">
                    Promote
                  </Button>
                </Tooltip>
                {entry.argocdDeepLink && (
                  <DeepLinkButton
                    href={entry.argocdDeepLink}
                    toolName="ArgoCD"
                    ariaLabel={`Open ${entry.environmentName} in ArgoCD`}
                  />
                )}
              </>
            )}
          </CardFooter>
        </Card>
      </div>
    );
  },
);
