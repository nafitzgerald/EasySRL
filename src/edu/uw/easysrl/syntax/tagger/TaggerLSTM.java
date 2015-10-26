package edu.uw.easysrl.syntax.tagger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.uw.deeptagger.DeepTagger;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;

public class TaggerLSTM extends Tagger {
	private final DeepTagger tagger;

	TaggerLSTM(final File modelFolder, final double beta, final int maxTagsPerWord, final CutoffsDictionary cutoffs)
			throws IOException {

		this(DeepTagger.make(modelFolder), beta, maxTagsPerWord, cutoffs);
	}

	private final List<Integer> possibleCategories = new ArrayList<>();

	public TaggerLSTM(final DeepTagger tagger, final double beta, final int maxTagsPerWord,
			final CutoffsDictionary cutoffs) throws IOException {
		super(cutoffs, beta, tagger.getTags().stream().map(Category::valueOf).collect(Collectors.toList()),
				maxTagsPerWord);
		this.tagger = tagger;

		for (int i = 0; i < super.lexicalCategories.size(); i++) {
			possibleCategories.add(i);
		}

	}

	@Override
	public List<List<ScoredCategory>> tag(final List<InputWord> words) {
		final List<String> input = words.stream().map(x -> x.word).collect(Collectors.toList());
		final float[][] scores = tagger.tag(input);
		final List<List<ScoredCategory>> result = new ArrayList<>();
		for (int i = 0; i < input.size(); i++) {
			final List<ScoredCategory> tagsForWord = getTagsForWord(scores[i]);
			result.add(tagsForWord);
		}

		return result;
	}

	private List<ScoredCategory> getTagsForWord(final float[] scores) {
		final int size = maxTagsPerWord;
		float bestScore = Float.NEGATIVE_INFINITY;

		List<ScoredCategory> result = new ArrayList<>(scores.length);
		for (int i = 0; i < scores.length; i++) {
			result.add(new ScoredCategory(super.lexicalCategories.get(i), scores[i]));
			bestScore = Math.max(bestScore, scores[i]);
		}

		Collections.sort(result);
		if (result.size() > size) {
			result = result.subList(0, size);
		}

		final double threshold = beta * Math.exp(bestScore);
		for (int i = 2; i < result.size(); i++) {
			// TODO binary search
			if (Math.exp(result.get(i).getScore()) < threshold) {
				result = result.subList(0, i);
				break;
			}
		}

		return result;
	}

	@Override
	public Map<Category, Double> getCategoryScores(final List<InputWord> sentence, final int wordIndex,
			final double weight, final Collection<Category> categories) {
		throw new RuntimeException("TODO");
	}
}