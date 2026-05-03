package io.github.eyuppastirmaci.dioptra.logging

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import io.github.eyuppastirmaci.dioptra.config.CredentialMasker

class MaskingPatternLayout : PatternLayout() {

    override fun doLayout(event: ILoggingEvent): String {
        return CredentialMasker.maskSensitiveValues(super.doLayout(event))
    }
}
