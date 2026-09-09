package com.knot.backend.auth.application.dto.command;

import com.knot.backend.auth.domain.DeviceInfo;

public record ExchangeDeviceCodeCommand(
        String code,
        String codeVerifier,
        DeviceInfo device
) {
}
