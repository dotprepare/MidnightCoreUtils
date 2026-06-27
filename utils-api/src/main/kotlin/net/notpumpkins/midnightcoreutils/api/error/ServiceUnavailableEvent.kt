package net.notpumpkins.midnightcoreutils.api.error

import net.notpumpkins.midnightcoreutils.api.event.ModEvent

class ServiceUnavailableEvent(
    val modId: String,
    val serviceClass: Class<*>,
    val reason: String
) : ModEvent()
