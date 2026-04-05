import { Button } from '@patternfly/react-core';

interface DeepLinkButtonProps {
  href: string;
  toolName: string;
}

export function DeepLinkButton({ href, toolName }: DeepLinkButtonProps) {
  return (
    <Button
      variant="link"
      component="a"
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      isInline
    >
      Open in {toolName} ↗
    </Button>
  );
}
