import {
  Button,
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  Modal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Popover,
} from '@patternfly/react-core';

interface PromotionConfirmationProps {
  version: string;
  targetEnvName: string;
  targetNamespace: string;
  targetCluster: string | null;
  isProduction: boolean;
  actionType: 'deploy' | 'promote';
  isOpen: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  triggerRef: React.RefObject<HTMLElement | null>;
}

function NonProdPopover({
  version,
  targetEnvName,
  targetNamespace,
  targetCluster,
  actionType,
  isOpen,
  onConfirm,
  onCancel,
  triggerRef,
}: PromotionConfirmationProps) {
  const clusterText = targetCluster ? ` on ${targetCluster}` : '';
  const isPromote = actionType === 'promote';
  const verb = isPromote ? 'Promote' : 'Deploy';

  return (
    <Popover
      headerContent={`${verb} ${version} to ${targetEnvName}?`}
      bodyContent={`→ ${targetNamespace}${clusterText}`}
      footerContent={
        <>
          <Button variant="link" onClick={onCancel}>
            Cancel
          </Button>{' '}
          <Button variant="primary" onClick={onConfirm}>
            {verb}
          </Button>
        </>
      }
      isVisible={isOpen}
      shouldClose={() => {
        onCancel();
        return true;
      }}
      triggerRef={triggerRef}
    />
  );
}

function ProdModal({
  version,
  targetNamespace,
  targetCluster,
  isOpen,
  onConfirm,
  onCancel,
  triggerRef,
}: PromotionConfirmationProps) {
  const handleClose = () => {
    onCancel();
    triggerRef.current?.focus();
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      aria-labelledby="prod-deploy-title"
      aria-describedby="prod-deploy-body"
      onEscapePress={handleClose}
    >
      <ModalHeader title="Deploy to PRODUCTION" labelId="prod-deploy-title" titleIconVariant="warning" />
      <ModalBody id="prod-deploy-body">
        <DescriptionList isHorizontal>
          <DescriptionListGroup>
            <DescriptionListTerm>Version</DescriptionListTerm>
            <DescriptionListDescription>{version}</DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Target</DescriptionListTerm>
            <DescriptionListDescription>{targetNamespace}</DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Cluster</DescriptionListTerm>
            <DescriptionListDescription>{targetCluster ?? 'Unknown'}</DescriptionListDescription>
          </DescriptionListGroup>
        </DescriptionList>
        <p className="pf-v6-u-mt-md">This will deploy to production.</p>
      </ModalBody>
      <ModalFooter>
        <Button key="confirm" variant="danger" onClick={onConfirm}>
          Deploy to Prod
        </Button>
        <Button key="cancel" variant="link" onClick={handleClose}>
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  );
}

export function PromotionConfirmation(props: PromotionConfirmationProps) {
  if (props.isProduction) {
    return <ProdModal {...props} />;
  }
  return <NonProdPopover {...props} />;
}
