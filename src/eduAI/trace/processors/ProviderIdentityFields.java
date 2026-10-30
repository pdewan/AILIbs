package eduAI.trace.processors;

/** Evidence needed to associate factory instances with registry provider keys. */
public final class ProviderIdentityFields {
    public static final String PROVIDER = "provider";
    public static final String FACTORY_IDENTITY = "serverHandleFactoryIdentity";
    public static final String FACTORY = "serverHandleFactory";
    private ProviderIdentityFields() { }
    /** Recognizes the identity representations supported by the trace contract. */
    public static boolean dumpContainsIdentity(String dump, String identity) {
        return identity != null && !identity.isBlank() && dump != null
            && (dump.contains("identity=\"" + identity + "\"")
                || dump.contains("toString=\"" + identity + "\""));
    }
}
