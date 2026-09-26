package eduAI.trace.processors;

public interface BridgeDemoTraceInputs {
	String IMAGE_FILE_NAME = "bridge_scene.jpg";
	String SYSTEM_PROMPT_1 =
			"You are movie expert and can recognize movie scenes from images. "
					+ "You will be provided with an image and a prompt. "
					+ "Please answer the prompt based on the image and your knowledge "
					+ "of movies. Only the first user prompt will be accompanied by an "
					+ "image. All subsequent prompts will be text only and will not "
					+ "include an image.";
	String SYSTEM_PROMPT_2 =
			"Please think step by step and provide detailed answers.";
	String STARTING_PROMPT =
			" Do you understand the system instruction? Do you recognize the scene "
					+ "in the image enclosed? What are the three questions that the "
					+ "bridge keeper asked Knight Lancelot? What were Lancelot's "
					+ "answers?";
}
