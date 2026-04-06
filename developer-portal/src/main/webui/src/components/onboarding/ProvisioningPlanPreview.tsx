import { useState, useCallback } from 'react';
import {
  Card,
  CardBody,
  CardTitle,
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  Label,
  List,
  ListItem,
  Content,
  MenuToggle,
  Select,
  SelectOption,
  SelectList,
} from '@patternfly/react-core';
import { ArrowRightIcon } from '@patternfly/react-icons';
import type { OnboardingPlanResult } from '../../types/onboarding';
import type { Cluster } from '../../types/cluster';

interface ProvisioningPlanPreviewProps {
  plan: OnboardingPlanResult;
  clusters: Cluster[];
  onClusterChange: (envName: string, clusterId: number) => void;
  onBuildClusterChange: (clusterId: number) => void;
}

export function ProvisioningPlanPreview({
  plan,
  clusters,
  onClusterChange,
  onBuildClusterChange,
}: ProvisioningPlanPreviewProps) {
  const namespaceCount = plan.namespaces.length;
  const argoAppCount = plan.argoCdApps.length;

  return (
    <div>
      <Content component="h3" className="pf-v6-u-mb-sm">
        {plan.appName} — {plan.teamName}
      </Content>

      <div className="pf-v6-u-mb-md" data-testid="resource-count">
        <Label color="blue">{namespaceCount} namespaces</Label>{' '}
        <Label color="blue">{argoAppCount} ArgoCD applications</Label>
      </div>

      <Card className="pf-v6-u-mb-md">
        <CardTitle>Namespaces</CardTitle>
        <CardBody>
          <DescriptionList>
            {plan.namespaces.map((ns) => (
              <DescriptionListGroup key={ns.name}>
                <DescriptionListTerm>{ns.name}</DescriptionListTerm>
                <DescriptionListDescription>
                  <ClusterSelect
                    clusters={clusters}
                    selectedClusterName={ns.clusterName}
                    envName={ns.environmentName}
                    isBuild={ns.isBuild}
                    onChange={(clusterId) => {
                      if (ns.isBuild) {
                        onBuildClusterChange(clusterId);
                      } else {
                        onClusterChange(ns.environmentName, clusterId);
                      }
                    }}
                  />
                </DescriptionListDescription>
              </DescriptionListGroup>
            ))}
          </DescriptionList>
        </CardBody>
      </Card>

      <Card className="pf-v6-u-mb-md">
        <CardTitle>ArgoCD Applications</CardTitle>
        <CardBody>
          <List>
            {plan.argoCdApps.map((app) => (
              <ListItem key={app.name}>
                <strong>{app.name}</strong> — {app.chartPath} / {app.valuesFile}
              </ListItem>
            ))}
          </List>
        </CardBody>
      </Card>

      {plan.promotionChain.length > 0 && (
        <Card>
          <CardTitle>Promotion Chain</CardTitle>
          <CardBody>
            <div className="pf-v6-u-display-flex pf-v6-u-align-items-center pf-v6-u-gap-sm">
              {plan.promotionChain.map((env, idx) => (
                <span key={env} className="pf-v6-u-display-flex pf-v6-u-align-items-center pf-v6-u-gap-sm">
                  <Label>{env}</Label>
                  {idx < plan.promotionChain.length - 1 && <ArrowRightIcon />}
                </span>
              ))}
            </div>
          </CardBody>
        </Card>
      )}
    </div>
  );
}

interface ClusterSelectProps {
  clusters: Cluster[];
  selectedClusterName: string;
  envName: string;
  isBuild: boolean;
  onChange: (clusterId: number) => void;
}

function ClusterSelect({ clusters, selectedClusterName, envName, isBuild, onChange }: ClusterSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const selectId = `cluster-select-${isBuild ? 'build' : envName}`;

  const handleSelect = useCallback(
    (_event: React.MouseEvent | undefined, value: string | number | undefined) => {
      if (value !== undefined) {
        onChange(Number(value));
        setIsOpen(false);
      }
    },
    [onChange],
  );

  return (
    <Select
      id={selectId}
      isOpen={isOpen}
      selected={selectedClusterName}
      onSelect={handleSelect}
      onOpenChange={setIsOpen}
      toggle={(toggleRef) => (
        <MenuToggle
          ref={toggleRef}
          onClick={() => setIsOpen((prev) => !prev)}
          isExpanded={isOpen}
          aria-label={`Select cluster for ${isBuild ? 'build' : envName}`}
        >
          {selectedClusterName}
        </MenuToggle>
      )}
    >
      <SelectList>
        {clusters.map((cluster) => (
          <SelectOption key={cluster.id} value={cluster.id}>
            {cluster.name}
          </SelectOption>
        ))}
      </SelectList>
    </Select>
  );
}
