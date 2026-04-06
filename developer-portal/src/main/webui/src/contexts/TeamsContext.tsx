import { createContext, useContext } from 'react';
import type { TeamSummary } from '../types/team';

interface TeamsContextValue {
  teams: TeamSummary[];
  activeTeamId: number | null;
  activeTeam: TeamSummary | null;
}

const TeamsContext = createContext<TeamsContextValue>({
  teams: [],
  activeTeamId: null,
  activeTeam: null,
});

export function useTeams() {
  return useContext(TeamsContext);
}

export const TeamsProvider = TeamsContext.Provider;
