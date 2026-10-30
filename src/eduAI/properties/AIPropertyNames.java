package eduAI.properties;

/** Shared property-name contract for clients, CLI input, and trace checks. */
public interface AIPropertyNames {
    String CURRENT_MODELS = "current_models";
    String SYSTEM_PROMPT = "system_prompt";
    String TEMPERATURE = "temperature";
    String TIMEOUT = "timeout";
    String STREAM = "stream";
    String STREAMING_ALIAS = "streaming";
    String NUM_CTX = "num_ctx";
    String TOP_K = "top_k";
}
