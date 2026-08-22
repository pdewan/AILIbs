package al_libs.s_eliza;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BasicElizaClient implements ElizaClient {
	private final Map<String, List<Rule>> script = new LinkedHashMap<>();
	private final List<String> defaultResponses =
			List.of(
					"Please go on.",
					"I see. Tell me more.",
					"Does that trouble you?",
					"Why do you say that?");
	private final Map<String, String> pronouns = new LinkedHashMap<>();
	private int defaultResponseIndex;

	public BasicElizaClient() {
		initializePronouns();
		initializeScript();
	}

	@Override
	public synchronized ElizaResponse generate(
			String aModelName,
			String aPrompt) {
		String prompt = aPrompt == null ? "" : aPrompt;
		return new ElizaResponse(
				modelName(aModelName),
				generateResponse(prompt),
				prompt);
	}

	@Override
	public synchronized ElizaResponse generate(
			String aModelName,
			List<ElizaMessage> aMessages) {
		return generate(
				aModelName,
				lastUserPrompt(aMessages));
	}

	@Override
	public synchronized Iterator<String> generateStream(
			String aModelName,
			String aPrompt) {
		return twoChunkIterator(generate(aModelName, aPrompt).text());
	}

	@Override
	public synchronized Iterator<String> generateStream(
			String aModelName,
			List<ElizaMessage> aMessages) {
		return twoChunkIterator(generate(aModelName, aMessages).text());
	}

	private String generateResponse(String anInput) {
		String cleanInput = anInput
				.replaceAll("[.,!?]", "")
				.toLowerCase(Locale.ROOT);
		String[] words = cleanInput.trim().isEmpty()
				? new String[0]
				: cleanInput.trim().split("\\s+");
		for (String keyword : script.keySet()) {
			if (containsWord(words, keyword)) {
				for (Rule rule : script.get(keyword)) {
					Matcher matcher = rule.pattern.matcher(cleanInput);
					if (matcher.matches()) {
						String capturedText =
								matcher.groupCount() >= 1
										? matcher.group(1)
										: "";
						return rule.responseTemplate.replace(
								"$1",
								swapPronouns(capturedText));
					}
				}
			}
		}
		String fallback = defaultResponses.get(defaultResponseIndex);
		defaultResponseIndex =
				(defaultResponseIndex + 1) % defaultResponses.size();
		return fallback;
	}

	private Iterator<String> twoChunkIterator(String aText) {
		String text = aText == null ? "" : aText;
		int split = splitIndex(text);
		return Arrays
				.asList(
						text.substring(0, split),
						text.substring(split))
				.iterator();
	}

	private int splitIndex(String aText) {
		if (aText.length() <= 1) {
			return 0;
		}
		int midpoint = aText.length() / 2;
		int leftSpace = aText.lastIndexOf(' ', midpoint);
		int rightSpace = aText.indexOf(' ', midpoint);
		if (leftSpace <= 0) {
			return rightSpace > 0 ? rightSpace + 1 : midpoint;
		}
		if (rightSpace < 0) {
			return leftSpace + 1;
		}
		return midpoint - leftSpace <= rightSpace - midpoint
				? leftSpace + 1
				: rightSpace + 1;
	}

	private String lastUserPrompt(List<ElizaMessage> aMessages) {
		if (aMessages == null || aMessages.isEmpty()) {
			return "";
		}
		for (int i = aMessages.size() - 1; i >= 0; i--) {
			ElizaMessage message = aMessages.get(i);
			if (message != null && message.role() == ElizaRole.USER) {
				return message.text();
			}
		}
		ElizaMessage lastMessage = aMessages.get(aMessages.size() - 1);
		return lastMessage == null ? "" : lastMessage.text();
	}

	private boolean containsWord(
			String[] words,
			String aKeyword) {
		for (String word : words) {
			if (word.equals(aKeyword)) {
				return true;
			}
		}
		return false;
	}

	private String swapPronouns(String aText) {
		if (aText == null || aText.trim().isEmpty()) {
			return "";
		}
		List<String> result = new ArrayList<>();
		for (String word : aText.split("\\s+")) {
			result.add(pronouns.getOrDefault(word, word));
		}
		return String.join(" ", result);
	}

	private void initializePronouns() {
		pronouns.put("i", "you");
		pronouns.put("me", "you");
		pronouns.put("my", "your");
		pronouns.put("am", "are");
		pronouns.put("was", "were");
		pronouns.put("myself", "yourself");
		pronouns.put("you", "I");
		pronouns.put("your", "my");
	}

	private void initializeScript() {
		List<Rule> amRules = new ArrayList<>();
		amRules.add(new Rule(
				".*i am feeling (.*)",
				"Why do you feel $1?"));
		amRules.add(new Rule(
				".*i am (.*)",
				"Did you come to me because you are $1?"));
		script.put("am", amRules);

		List<Rule> computerRules = new ArrayList<>();
		computerRules.add(new Rule(
				".*computer.*",
				"Do computers worry you?"));
		script.put("computer", computerRules);

		List<Rule> nameRules = new ArrayList<>();
		nameRules.add(new Rule(
				".*your name.*",
				"My name is ELIZA. What is yours?"));
		script.put("name", nameRules);

		List<Rule> hateRules = new ArrayList<>();
		hateRules.add(new Rule(
				".*i hate (.*)",
				"Why do you hate $1?"));
		script.put("hate", hateRules);
	}

	private String modelName(String aModelName) {
		return aModelName == null || aModelName.trim().isEmpty()
				? ElizaClient.DEFAULT_MODEL_NAME
				: aModelName;
	}

	private static class Rule {
		private final Pattern pattern;
		private final String responseTemplate;

		Rule(
				String aRegex,
				String aResponseTemplate) {
			pattern = Pattern.compile(aRegex, Pattern.CASE_INSENSITIVE);
			responseTemplate = aResponseTemplate;
		}
	}
}
