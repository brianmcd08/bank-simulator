package com.bankcorp.banksim;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code banksim.chaos.*} that make failures happen on demand, so a demo does the same thing every
 * run. All are off unless set.
 *
 * @param dropFirstReplyForLoan the first message for this loan id is processed, then its reply is held back for
 *                              {@code replyDelay}. A client whose read timeout is shorter never sees the reply.
 * @param replyDelay            how long to hold the reply back
 */
@ConfigurationProperties("banksim.chaos")
public record ChaosProperties(String dropFirstReplyForLoan, Duration replyDelay) {

    public ChaosProperties {
        if (replyDelay == null) {
            replyDelay = Duration.ofSeconds(5);
        }
    }
}
