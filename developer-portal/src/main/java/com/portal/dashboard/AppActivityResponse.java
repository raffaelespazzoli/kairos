package com.portal.dashboard;

import java.util.List;

public record AppActivityResponse(
        List<TeamActivityEventDto> events,
        String error) {
}
