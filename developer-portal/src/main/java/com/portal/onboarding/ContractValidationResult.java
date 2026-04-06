package com.portal.onboarding;

import java.util.List;

public record ContractValidationResult(
    boolean allPassed,
    List<ContractCheck> checks,
    String runtimeType,
    List<String> detectedEnvironments
) {}
