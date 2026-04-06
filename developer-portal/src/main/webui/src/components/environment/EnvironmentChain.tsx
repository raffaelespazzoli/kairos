import { Fragment, useRef, useCallback } from 'react';
import { Alert } from '@patternfly/react-core';
import { ArrowRightIcon } from '@patternfly/react-icons';
import { EnvironmentCard } from './EnvironmentCard';
import type { EnvironmentChainEntry } from '../../types/environment';

interface EnvironmentChainProps {
  environments: EnvironmentChainEntry[];
  argocdError?: string | null;
}

export function EnvironmentChain({
  environments,
  argocdError,
}: EnvironmentChainProps) {
  const cardRefs = useRef<(HTMLDivElement | null)[]>([]);

  const setCardRef = useCallback(
    (index: number) => (el: HTMLDivElement | null) => {
      cardRefs.current[index] = el;
    },
    [],
  );

  const handleArrowKeyNavigation = (e: React.KeyboardEvent) => {
    const currentIndex = cardRefs.current.findIndex(
      (ref) => ref === document.activeElement || ref?.contains(document.activeElement),
    );
    if (currentIndex === -1) return;

    if (e.key === 'ArrowRight' && currentIndex < environments.length - 1) {
      e.preventDefault();
      cardRefs.current[currentIndex + 1]?.focus();
    } else if (e.key === 'ArrowLeft' && currentIndex > 0) {
      e.preventDefault();
      cardRefs.current[currentIndex - 1]?.focus();
    }
  };

  return (
    <>
      {argocdError && (
        <Alert variant="warning" title={argocdError} isInline className="pf-v6-u-mb-md" />
      )}
      <div
        role="list"
        aria-label="Environment promotion chain"
        style={{
          display: 'flex',
          alignItems: 'stretch',
          overflowX: 'auto',
          gap: 'var(--pf-t--global--spacer--md)',
          paddingBottom: 'var(--pf-t--global--spacer--sm)',
        }}
        onKeyDown={handleArrowKeyNavigation}
      >
        {environments.map((env, index) => (
          <Fragment key={env.environmentName}>
            <div
              role="listitem"
              style={{ minWidth: 180, flex: '1 0 180px' }}
            >
              <EnvironmentCard
                entry={env}
                nextEnvName={environments[index + 1]?.environmentName}
                ref={setCardRef(index)}
              />
            </div>
            {index < environments.length - 1 && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  flexShrink: 0,
                }}
                aria-hidden="true"
              >
                <ArrowRightIcon />
              </div>
            )}
          </Fragment>
        ))}
      </div>
    </>
  );
}
