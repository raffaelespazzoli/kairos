import { createContext, useContext } from 'react';
import type { ApplicationSummary } from '../types/application';
import type { PortalError } from '../types/error';

interface ApplicationsContextValue {
  applications: ApplicationSummary[];
  isLoading: boolean;
  error: PortalError | null;
}

const ApplicationsContext = createContext<ApplicationsContextValue>({
  applications: [],
  isLoading: true,
  error: null,
});

export function useApplications() {
  return useContext(ApplicationsContext);
}

export const ApplicationsProvider = ApplicationsContext.Provider;
