import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AdminClustersPage } from './AdminClustersPage';
import type { Cluster } from '../types/cluster';

const mockClusters: Cluster[] = [
  {
    id: 1,
    name: 'ocp-dev-01',
    apiServerUrl: 'https://api.ocp-dev-01.example.com:6443',
    createdAt: '2026-04-01T10:00:00Z',
    updatedAt: '2026-04-01T10:00:00Z',
  },
  {
    id: 2,
    name: 'ocp-prod-01',
    apiServerUrl: 'https://api.ocp-prod-01.example.com:6443',
    createdAt: '2026-04-02T12:00:00Z',
    updatedAt: '2026-04-02T12:00:00Z',
  },
];

vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({
    username: 'admin-user',
    teamName: 'Platform',
    teamId: 'platform',
    role: 'admin' as const,
    isAuthenticated: true,
    token: 'test-token',
  }),
}));

const mockApiFetch = vi.fn();

vi.mock('../api/client', () => ({
  apiFetch: (...args: unknown[]) => mockApiFetch(...args),
  ApiError: class extends Error {
    status: number;
    portalError: { error: string; message: string; detail?: string; timestamp: string };
    constructor(status: number, portalError: { error: string; message: string; detail?: string; timestamp: string }) {
      super(portalError.message);
      this.name = 'ApiError';
      this.status = status;
      this.portalError = portalError;
    }
  },
  setTokenAccessor: vi.fn(),
}));

describe('AdminClustersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders cluster data in a table', async () => {
    mockApiFetch.mockResolvedValueOnce(mockClusters);

    render(<AdminClustersPage />);

    await waitFor(() => {
      expect(screen.getByText('ocp-dev-01')).toBeInTheDocument();
    });

    expect(screen.getByText('ocp-prod-01')).toBeInTheDocument();
    expect(screen.getByText('https://api.ocp-dev-01.example.com:6443')).toBeInTheDocument();
    expect(screen.getByText('https://api.ocp-prod-01.example.com:6443')).toBeInTheDocument();
  });

  it('shows empty state when no clusters exist', async () => {
    mockApiFetch.mockResolvedValueOnce([]);

    render(<AdminClustersPage />);

    await waitFor(() => {
      expect(screen.getByText('No clusters registered')).toBeInTheDocument();
    });
  });

  it('opens register modal on button click', async () => {
    mockApiFetch.mockResolvedValueOnce(mockClusters);
    const user = userEvent.setup();

    render(<AdminClustersPage />);

    await waitFor(() => {
      expect(screen.getByText('ocp-dev-01')).toBeInTheDocument();
    });

    const registerButtons = screen.getAllByRole('button', { name: /register cluster/i });
    await user.click(registerButtons[0]);

    expect(screen.getByLabelText('Register cluster')).toBeInTheDocument();
    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/api server url/i)).toBeInTheDocument();
  });

  it('submits create form and refreshes data', async () => {
    const newCluster: Cluster = {
      id: 3,
      name: 'ocp-qa-01',
      apiServerUrl: 'https://api.ocp-qa-01.example.com:6443',
      createdAt: '2026-04-04T10:00:00Z',
      updatedAt: '2026-04-04T10:00:00Z',
    };

    mockApiFetch
      .mockResolvedValueOnce(mockClusters) // initial load
      .mockResolvedValueOnce(newCluster) // POST create
      .mockResolvedValueOnce([...mockClusters, newCluster]); // refresh after create

    const user = userEvent.setup();

    render(<AdminClustersPage />);

    await waitFor(() => {
      expect(screen.getByText('ocp-dev-01')).toBeInTheDocument();
    });

    const registerButtons = screen.getAllByRole('button', { name: /register cluster/i });
    await user.click(registerButtons[0]);

    const nameInput = screen.getByLabelText(/name/i);
    const urlInput = screen.getByLabelText(/api server url/i);

    await user.type(nameInput, 'ocp-qa-01');
    await user.type(urlInput, 'https://api.ocp-qa-01.example.com:6443');

    const submitButton = screen.getByRole('button', { name: /^register$/i });
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/v1/admin/clusters',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            name: 'ocp-qa-01',
            apiServerUrl: 'https://api.ocp-qa-01.example.com:6443',
          }),
        }),
      );
    });
  });

  it('shows loading spinner while fetching', () => {
    mockApiFetch.mockReturnValue(new Promise(() => {}));

    render(<AdminClustersPage />);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows Cluster Management heading', async () => {
    mockApiFetch.mockResolvedValueOnce(mockClusters);

    render(<AdminClustersPage />);

    await waitFor(() => {
      expect(screen.getByText('Cluster Management')).toBeInTheDocument();
    });
  });
});
