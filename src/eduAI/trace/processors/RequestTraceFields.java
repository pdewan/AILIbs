package eduAI.trace.processors;

/** Named request evidence domains; consumers select only fields they read. */
public final class RequestTraceFields {
    public static final String GENERIC_CONTEXT = "providerIndependentContextWindow";
    public static final String NATIVE_CONTEXT = "providerDependentContextWindow";
    public static final String GENERIC_PARAMETERS = "providerIndependentParameterStore";
    public static final String NATIVE_PARAMETERS = "providerDependentConfiguration";
    public static final String PARAMETER_TRANSLATIONS = "translatedParameterEvidence";
    public static final String MODE = "mode";
    private RequestTraceFields() { }
}
