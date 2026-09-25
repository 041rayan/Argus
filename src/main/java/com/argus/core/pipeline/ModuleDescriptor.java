package com.argus.core.pipeline;

/** Static self-description of a module. */
public record ModuleDescriptor(String name, ScanPhase phase, RiskLevel risk) {
}
