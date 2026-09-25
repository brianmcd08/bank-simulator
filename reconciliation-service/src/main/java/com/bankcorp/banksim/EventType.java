package com.bankcorp.banksim;

/** The kind of event a bank is reporting. Each type is routed to its own queue by the topic. */
public enum EventType {
    POSITION_UPDATE,
    PAYMENT
}
