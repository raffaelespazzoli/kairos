import { Button } from '@patternfly/react-core';

interface DeepLinkButtonProps {
  href?: string | null;
  toolName: string;
  label?: string;
  ariaLabel?: string;
}

export function DeepLinkButton({ href, toolName, label, ariaLabel }: DeepLinkButtonProps) {
  if (!href) {
    return null;
  }

  return (
    <Button
      variant="link"
      component="a"
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      isInline
      aria-label={ariaLabel}
    >
      {label ?? `Open in ${toolName} ↗`}
    </Button>
  );
}
