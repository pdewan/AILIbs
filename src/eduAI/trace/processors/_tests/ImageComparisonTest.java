package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import eduAI.trace.ImageByteSummary;
import eduAI.trace.TraceObjectPrinter;

public class ImageComparisonTest {
	public static void main(String[] args) {
		byte[] bytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
		ImageByteSummary expected = ImageByteSummary.from(bytes);
		String genericDump = TraceObjectPrinter.format("image", bytes, 0);
		ImageByteSummary generic = ImageByteSummary.parseFirst(genericDump);
		if (!expected.equals(generic)) {
			throw new AssertionError(
					"Reflective image summary differs: " + genericDump);
		}
		ImageByteSummary wrongPrefix = ImageByteSummary.parseFirst(
				genericDump.replace("prefix=0001", "prefix=ff01"));
		ImageByteSummary wrongSuffix = ImageByteSummary.parseFirst(
				genericDump.replace("suffix=0203", "suffix=02ff"));
		ImageByteSummary wrongCount = ImageByteSummary.parseFirst(
				genericDump.replace("count=10", "count=11"));
		if (expected.equals(wrongPrefix)
				|| expected.equals(wrongSuffix)
				|| expected.equals(wrongCount)) {
			throw new AssertionError(
					"Prefix, suffix, and count must each affect comparison");
		}
		ImageByteSummary nonempty = ImageByteSummary.parseFirstNonempty(
				"imageBytes(count=0,prefix=,suffix=) "
						+ expected.formatted());
		if (!expected.equals(nonempty)) {
			throw new AssertionError(
					"Zero-byte placeholders must not hide the image");
		}
		System.out.println("Image comparison checks passed");
	}
}
