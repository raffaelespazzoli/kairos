import { Button } from '@patternfly/react-core';

interface DeepLinkButtonProps {
  href?: string | null;
  toolName: string;
  label?: string;
}

export function DeepLinkButton({ href, toolName, label }: DeepLinkButtonProps) {
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
    >
      {label ?? `Open in ${toolName} ↗`}
    </Button>
  );
}
