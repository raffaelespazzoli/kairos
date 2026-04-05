import { useState } from 'react';
import {
  PageSection,
  Content,
  Button,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Form,
  FormGroup,
  TextInput,
  FormHelperText,
  HelperText,
  HelperTextItem,
  EmptyState,
  EmptyStateBody,
  EmptyStateFooter,
  EmptyStateActions,
  Alert,
} from '@patternfly/react-core';
import {
  Table,
  Thead,
  Tbody,
  Tr,
  Th,
  Td,
  ActionsColumn,
} from '@patternfly/react-table';
import { useApiFetch } from '../hooks/useApiFetch';
import { useAuth } from '../hooks/useAuth';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { RefreshButton } from '../components/shared/RefreshButton';
import { createCluster, updateCluster, deleteCluster } from '../api/clusters';
import { ApiError } from '../api/client';
import type { Cluster, CreateClusterRequest, UpdateClusterRequest } from '../types/cluster';

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

const HTTPS_PREFIX = 'https://';

function validateName(value: string): string | null {
  if (!value.trim()) return 'Cluster name is required';
  return null;
}

function validateUrl(value: string): string | null {
  if (!value.trim()) return 'API server URL is required';
  if (!value.startsWith(HTTPS_PREFIX)) return 'URL must start with https://';
  return null;
}

export function AdminClustersPage() {
  const { role } = useAuth();
  const isAdmin = role === 'admin';

  if (!isAdmin) {
    return (
      <PageSection>
        <Alert variant="danger" title="Access Denied" isInline>
          You don&apos;t have permission to access this page. Admin role is required.
        </Alert>
      </PageSection>
    );
  }

  return <AdminClustersPageContent />;
}

function AdminClustersPageContent() {
  const { data: clusters, error, isLoading, refresh } = useApiFetch<Cluster[]>('/api/v1/admin/clusters');

  const [isFormModalOpen, setFormModalOpen] = useState(false);
  const [isDeleteModalOpen, setDeleteModalOpen] = useState(false);
  const [editingCluster, setEditingCluster] = useState<Cluster | null>(null);
  const [deletingCluster, setDeletingCluster] = useState<Cluster | null>(null);

  const [formName, setFormName] = useState('');
  const [formUrl, setFormUrl] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const [nameValidation, setNameValidation] = useState<string | null>(null);
  const [urlValidation, setUrlValidation] = useState<string | null>(null);
  const [nameBlurred, setNameBlurred] = useState(false);
  const [urlBlurred, setUrlBlurred] = useState(false);

  if (isLoading) return <LoadingSpinner systemName="Portal" />;
  if (error) return <ErrorAlert error={error} />;

  const openRegisterModal = () => {
    setEditingCluster(null);
    setFormName('');
    setFormUrl('');
    setFormError(null);
    setNameValidation(null);
    setUrlValidation(null);
    setNameBlurred(false);
    setUrlBlurred(false);
    setFormModalOpen(true);
  };

  const openEditModal = (cluster: Cluster) => {
    setEditingCluster(cluster);
    setFormName(cluster.name);
    setFormUrl(cluster.apiServerUrl);
    setFormError(null);
    setNameValidation(null);
    setUrlValidation(null);
    setNameBlurred(false);
    setUrlBlurred(false);
    setFormModalOpen(true);
  };

  const openDeleteModal = (cluster: Cluster) => {
    setDeletingCluster(cluster);
    setDeleteError(null);
    setDeleteModalOpen(true);
  };

  const closeFormModal = () => {
    setFormModalOpen(false);
    setEditingCluster(null);
  };

  const closeDeleteModal = () => {
    setDeleteModalOpen(false);
    setDeletingCluster(null);
  };

  const isFormValid = !validateName(formName) && !validateUrl(formUrl);

  const handleSave = async () => {
    const nameErr = validateName(formName);
    const urlErr = validateUrl(formUrl);
    setNameValidation(nameErr);
    setUrlValidation(urlErr);
    setNameBlurred(true);
    setUrlBlurred(true);
    if (nameErr || urlErr) return;

    setIsSaving(true);
    setFormError(null);
    try {
      if (editingCluster) {
        const req: UpdateClusterRequest = { name: formName.trim(), apiServerUrl: formUrl.trim() };
        await updateCluster(editingCluster.id, req);
      } else {
        const req: CreateClusterRequest = { name: formName.trim(), apiServerUrl: formUrl.trim() };
        await createCluster(req);
      }
      closeFormModal();
      refresh();
    } catch (e) {
      if (e instanceof ApiError) {
        setFormError(e.portalError.detail ?? e.portalError.message);
      } else {
        setFormError('An unexpected error occurred');
      }
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deletingCluster) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await deleteCluster(deletingCluster.id);
      closeDeleteModal();
      refresh();
    } catch (e) {
      if (e instanceof ApiError) {
        setDeleteError(e.portalError.detail ?? e.portalError.message);
      } else {
        setDeleteError('An unexpected error occurred');
      }
    } finally {
      setIsDeleting(false);
    }
  };

  const columnNames = {
    name: 'Name',
    apiServerUrl: 'API Server URL',
    createdAt: 'Created',
  };

  return (
    <>
      <PageSection>
        <Content component="h1">Cluster Management</Content>
        <Toolbar>
          <ToolbarContent>
            <ToolbarItem>
              <Button variant="primary" onClick={openRegisterModal}>
                Register Cluster
              </Button>
            </ToolbarItem>
            <ToolbarItem>
              <RefreshButton onRefresh={refresh} isRefreshing={isLoading} aria-label="Refresh clusters" />
            </ToolbarItem>
          </ToolbarContent>
        </Toolbar>
      </PageSection>

      <PageSection isFilled>
        {clusters && clusters.length === 0 ? (
          <EmptyState headingLevel="h2" titleText="No clusters registered">
            <EmptyStateBody>
              Register your first OpenShift cluster to use it as a deployment target.
            </EmptyStateBody>
            <EmptyStateFooter>
              <EmptyStateActions>
                <Button variant="primary" onClick={openRegisterModal}>
                  Register Cluster
                </Button>
              </EmptyStateActions>
            </EmptyStateFooter>
          </EmptyState>
        ) : (
          <Table aria-label="Clusters table">
            <Thead>
              <Tr>
                <Th>{columnNames.name}</Th>
                <Th>{columnNames.apiServerUrl}</Th>
                <Th>{columnNames.createdAt}</Th>
                <Th />
              </Tr>
            </Thead>
            <Tbody>
              {clusters?.map((cluster) => (
                <Tr key={cluster.id}>
                  <Td dataLabel={columnNames.name}>{cluster.name}</Td>
                  <Td dataLabel={columnNames.apiServerUrl}>{cluster.apiServerUrl}</Td>
                  <Td dataLabel={columnNames.createdAt}>{formatDate(cluster.createdAt)}</Td>
                  <Td isActionCell>
                    <ActionsColumn
                      items={[
                        { title: 'Edit', onClick: () => openEditModal(cluster) },
                        { title: 'Delete', onClick: () => openDeleteModal(cluster) },
                      ]}
                    />
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </PageSection>

      <Modal
        isOpen={isFormModalOpen}
        onClose={closeFormModal}
        aria-label={editingCluster ? 'Edit cluster' : 'Register cluster'}
        variant="small"
      >
        <ModalHeader title={editingCluster ? 'Edit Cluster' : 'Register Cluster'} />
        <ModalBody>
          {formError && (
            <Alert variant="danger" title={formError} isInline style={{ marginBottom: 'var(--pf-t--global--spacer--md)' }} />
          )}
          <Form>
            <FormGroup label="Name" isRequired fieldId="cluster-name">
              <TextInput
                isRequired
                id="cluster-name"
                value={formName}
                onChange={(_event, value) => {
                  setFormName(value);
                  if (nameBlurred) setNameValidation(validateName(value));
                }}
                onBlur={() => {
                  setNameBlurred(true);
                  setNameValidation(validateName(formName));
                }}
                validated={nameBlurred && nameValidation ? 'error' : 'default'}
              />
              {nameBlurred && nameValidation && (
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem variant="error">{nameValidation}</HelperTextItem>
                  </HelperText>
                </FormHelperText>
              )}
            </FormGroup>
            <FormGroup label="API Server URL" isRequired fieldId="cluster-url">
              <TextInput
                isRequired
                id="cluster-url"
                value={formUrl}
                onChange={(_event, value) => {
                  setFormUrl(value);
                  if (urlBlurred) setUrlValidation(validateUrl(value));
                }}
                onBlur={() => {
                  setUrlBlurred(true);
                  setUrlValidation(validateUrl(formUrl));
                }}
                validated={urlBlurred && urlValidation ? 'error' : 'default'}
                placeholder="https://api.cluster.example.com:6443"
              />
              {urlBlurred && urlValidation && (
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem variant="error">{urlValidation}</HelperTextItem>
                  </HelperText>
                </FormHelperText>
              )}
            </FormGroup>
          </Form>
        </ModalBody>
        <ModalFooter>
          <Button variant="primary" onClick={handleSave} isDisabled={!isFormValid || isSaving} isLoading={isSaving}>
            {editingCluster ? 'Save' : 'Register'}
          </Button>
          <Button variant="link" onClick={closeFormModal}>
            Cancel
          </Button>
        </ModalFooter>
      </Modal>

      <Modal
        isOpen={isDeleteModalOpen}
        onClose={closeDeleteModal}
        aria-label="Delete cluster confirmation"
        variant="small"
      >
        <ModalHeader title="Delete Cluster" />
        <ModalBody>
          {deleteError && (
            <Alert variant="danger" title={deleteError} isInline style={{ marginBottom: 'var(--pf-t--global--spacer--md)' }} />
          )}
          Are you sure you want to delete cluster <strong>{deletingCluster?.name}</strong>? This action cannot be undone.
        </ModalBody>
        <ModalFooter>
          <Button variant="danger" onClick={handleDelete} isDisabled={isDeleting} isLoading={isDeleting}>
            Delete
          </Button>
          <Button variant="link" onClick={closeDeleteModal}>
            Cancel
          </Button>
        </ModalFooter>
      </Modal>
    </>
  );
}
