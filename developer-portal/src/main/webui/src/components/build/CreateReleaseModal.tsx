import { useState } from 'react';
import {
  Modal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  TextInput,
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
} from '@patternfly/react-core';
import type { BuildSummary } from '../../types/build';

interface CreateReleaseModalProps {
  isOpen: boolean;
  build: BuildSummary;
  commitSha: string | null;
  onClose: () => void;
  onSubmit: (version: string) => void;
  isSubmitting: boolean;
}

const VERSION_PATTERN = /^v\d+(\.\d+)*(-[\w.]+)?$/;

export function CreateReleaseModal({
  isOpen,
  build,
  commitSha,
  onClose,
  onSubmit,
  isSubmitting,
}: CreateReleaseModalProps) {
  const [version, setVersion] = useState('');
  const [touched, setTouched] = useState(false);

  const isValid = VERSION_PATTERN.test(version);
  const showError = touched && !isValid && version.length > 0;

  const handleSubmit = () => {
    setTouched(true);
    if (isValid) {
      onSubmit(version);
    }
  };

  const handleClose = () => {
    setVersion('');
    setTouched(false);
    onClose();
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      aria-labelledby="create-release-title"
      aria-describedby="create-release-body"
    >
      <ModalHeader title="Create Release" labelId="create-release-title" />
      <ModalBody id="create-release-body">
        <DescriptionList isHorizontal className="pf-v6-u-mb-lg">
          <DescriptionListGroup>
            <DescriptionListTerm>Build</DescriptionListTerm>
            <DescriptionListDescription>
              #{build.buildId}
            </DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Commit</DescriptionListTerm>
            <DescriptionListDescription>
              {commitSha ? (
                <code className="pf-v6-u-font-family-mono-vf">{commitSha.substring(0, 7)}</code>
              ) : (
                'Commit SHA unavailable'
              )}
            </DescriptionListDescription>
          </DescriptionListGroup>
          {build.imageReference && (
            <DescriptionListGroup>
              <DescriptionListTerm>Image</DescriptionListTerm>
              <DescriptionListDescription>
                <code className="pf-v6-u-font-family-mono-vf">{build.imageReference}</code>
              </DescriptionListDescription>
            </DescriptionListGroup>
          )}
        </DescriptionList>

        <FormGroup label="Version tag" isRequired fieldId="release-version">
          <TextInput
            id="release-version"
            value={version}
            onChange={(_event, val) => {
              setVersion(val);
              if (!touched) setTouched(true);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleSubmit();
            }}
            placeholder="v1.0.0"
            validated={showError ? 'error' : 'default'}
            isDisabled={isSubmitting}
            aria-label="Version tag"
          />
          <FormHelperText>
            <HelperText>
              <HelperTextItem variant={showError ? 'error' : 'default'}>
                {showError
                  ? 'Version must start with "v" followed by numbers and dots (e.g., v1.0.0)'
                  : 'Enter a semver-style version tag (e.g., v1.4.2)'}
              </HelperTextItem>
            </HelperText>
          </FormHelperText>
        </FormGroup>
      </ModalBody>
      <ModalFooter>
        <Button
          variant="primary"
          onClick={handleSubmit}
          isDisabled={!isValid || isSubmitting}
          isLoading={isSubmitting}
        >
          Create
        </Button>
        <Button variant="link" onClick={handleClose} isDisabled={isSubmitting}>
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  );
}
