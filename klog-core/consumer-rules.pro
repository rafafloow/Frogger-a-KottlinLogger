# The manifest references this provider before application code runs.
-keep class dev.rafaflow.klog.core.KLogInitializer {
    public <init>();
}
