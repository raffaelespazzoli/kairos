package com.portal.environment;

import java.util.List;

public record EnvironmentChainResponse(
    List<EnvironmentChainEntryDto> environments,
    String argocdError
) {}
