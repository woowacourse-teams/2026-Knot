package com.knot.backend.chat.application;

import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param turnTimeout
 *            답변 없는 USER 메시지를 "진행 중인 턴"으로 보는 시간. 이 시간이 지나면 다음 검색 요청을 받는다(로드맵
 *            Q23)
 */
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(Duration turnTimeout) {

    public void validate() {
        if (turnTimeout == null || turnTimeout.isZero() || turnTimeout.isNegative()) {
            throw new ChatException(ChatErrorCode.CHAT_CONFIGURATION_INVALID);
        }
    }
}
