package com.knot.backend.auth.application.dto.command;

public record CompleteNicknameCommand(
        String nicknameToken,
        String nickname
) {
}
