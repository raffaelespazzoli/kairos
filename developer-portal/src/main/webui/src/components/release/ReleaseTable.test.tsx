import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ReleaseTable } from './ReleaseTable';
import type { ReleaseSummary } from '../../types/release';

const sampleReleases: ReleaseSummary[] = [
  {
    version: 'v1.2.0',
    createdAt: '2026-04-07T10:00:00Z',
    buildId: null,
    commitSha: 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
    imageReference: 'registry.example.com/team/app:v1.2.0',
  },
  {
    version: 'v1.1.0',
    createdAt: '2026-04-05T14:30:00Z',
    buildId: 'build-001',
    commitSha: 'b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3',
    imageReference: 'registry.example.com/team/app:v1.1.0',
  },
  {
    version: 'v1.0.0',
    createdAt: '2026-04-01T09:00:00Z',
    buildId: null,
    commitSha: 'c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4',
    imageReference: null,
  },
];

describe('ReleaseTable', () => {
  it('renders all release rows', () => {
    render(<ReleaseTable releases={sampleReleases} />);
    expect(screen.getByText('v1.2.0')).toBeInTheDocument();
    expect(screen.getByText('v1.1.0')).toBeInTheDocument();
    expect(screen.getByText('v1.0.0')).toBeInTheDocument();
  });

  it('renders table with compact variant', () => {
    render(<ReleaseTable releases={sampleReleases} />);
    expect(screen.getByRole('grid', { name: 'Releases table' })).toBeInTheDocument();
  });

  it('renders all column headers', () => {
    render(<ReleaseTable releases={sampleReleases} />);
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(screen.getByText('Created')).toBeInTheDocument();
    expect(screen.getByText('Commit')).toBeInTheDocument();
    expect(screen.getByText('Image')).toBeInTheDocument();
  });

  it('truncates commit SHA to 7 characters', () => {
    render(<ReleaseTable releases={sampleReleases} />);
    expect(screen.getByText('a1b2c3d')).toBeInTheDocument();
    expect(screen.getByText('b2c3d4e')).toBeInTheDocument();
    expect(screen.getByText('c3d4e5f')).toBeInTheDocument();
  });

  it('renders commit SHA in monospace font', () => {
    render(<ReleaseTable releases={sampleReleases} />);
    const shaSpan = screen.getByText('a1b2c3d');
    expect(shaSpan.style.fontFamily).toContain('monospace');
  });

  it('renders image reference in monospace font', () => {
    render(<ReleaseTable releases={sampleReleases} />);
    const imageSpan = screen.getByText('registry.example.com/team/app:v1.2.0');
    expect(imageSpan.style.fontFamily).toContain('monospace');
  });

  it('shows dash for null image reference', () => {
    render(<ReleaseTable releases={sampleReleases} />);
    const table = screen.getByRole('grid', { name: 'Releases table' });
    expect(table.textContent).toContain('—');
  });

  it('renders version with strong emphasis', () => {
    render(<ReleaseTable releases={sampleReleases} />);
    const version = screen.getByText('v1.2.0');
    expect(version.tagName.toLowerCase()).toBe('strong');
  });
});
