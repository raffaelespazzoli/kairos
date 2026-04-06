import {
  PageSection,
  Title,
  Content,
  Card,
  CardTitle,
  CardBody,
  Label,
} from '@patternfly/react-core';
import { Table, Thead, Tbody, Tr, Th, Td } from '@patternfly/react-table';
import { useNavigate, useParams } from 'react-router-dom';
import { useApplications } from '../contexts/ApplicationsContext';
import { useTeams } from '../contexts/TeamsContext';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { NoApplicationsEmptyState } from '../components/shared/NoApplicationsEmptyState';

export function TeamDashboardPage() {
  const { activeTeam } = useTeams();
  const teamName = activeTeam?.name ?? 'Team';
  const { teamId } = useParams();
  const navigate = useNavigate();
  const { applications, isLoading, error } = useApplications();

  if (isLoading) return <LoadingSpinner systemName="Portal" />;
  if (error) return <ErrorAlert error={error} />;

  if (applications.length === 0) {
    return (
      <PageSection>
        <NoApplicationsEmptyState />
      </PageSection>
    );
  }

  return (
    <>
      <PageSection>
        <Title headingLevel="h1">{teamName} Dashboard</Title>
        <Content component="p">
          {applications.length} application{applications.length !== 1 ? 's' : ''} onboarded
        </Content>
      </PageSection>

      <PageSection>
        <Card>
          <CardTitle>Applications</CardTitle>
          <CardBody style={{ padding: 0 }}>
            <Table aria-label="Applications" variant="compact">
              <Thead>
                <Tr>
                  <Th>Name</Th>
                  <Th>Runtime</Th>
                  <Th>Onboarded</Th>
                </Tr>
              </Thead>
              <Tbody>
                {applications.map((app) => (
                  <Tr
                    key={app.id}
                    isClickable
                    onRowClick={() =>
                      navigate(`/teams/${teamId}/apps/${app.id}/overview`)
                    }
                  >
                    <Td dataLabel="Name">
                      <strong>{app.name}</strong>
                    </Td>
                    <Td dataLabel="Runtime">
                      <Label isCompact>{app.runtimeType}</Label>
                    </Td>
                    <Td dataLabel="Onboarded">
                      {new Date(app.onboardedAt).toLocaleDateString()}
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          </CardBody>
        </Card>
      </PageSection>
    </>
  );
}
