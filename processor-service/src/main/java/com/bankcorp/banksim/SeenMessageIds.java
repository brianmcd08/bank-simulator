package com.bankcorp.banksim;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Every message id the processor has accepted, so a resent message can be recognised. Checked from several request
 * threads at once, so every access holds the lock.
 *
 * <p>In memory only: a restarted processor has forgotten every id and would process a resend again.
 */
public class SeenMessageIds {

    private final Set<String> ids = new HashSet<>();

    /** True the first time an id is seen, false every time after. Checking and recording are one step. */
    public synchronized boolean firstSighting(String messageId) {
        return ids.add(Objects.requireNonNull(messageId, "messageId"));
    }
}
