package edu.uw.easysrl.syntax.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.util.FastTreeMap;

abstract class ChartCell {
	/**
	 * Possibly adds a @AgendaItem to this chart cell. Returns true if the parse was added, and false if the cell was
	 * unchanged.
	 */
	public final boolean add(final AgendaItem entry) {
		return add(entry.getEquivalenceClassKey(), entry);
	}

	public abstract boolean add(final Object key, final AgendaItem entry);

	public abstract Iterable<AgendaItem> getEntries();

	abstract int size();

	public static class ChartCellNbestFactory extends ChartCellFactory {
		private final int nbest;
		private final double nbestBeam;

		public ChartCellNbestFactory(final int nbest, final double nbestBeam, final int maxSentenceLength,
				final Collection<Category> categories) {
			super();
			this.nbest = nbest;
			this.nbestBeam = nbestBeam;
			final Random randomGenerator = new Random();
			categoryToArgumentToHeadToModifierToHash = HashBasedTable.create();
			for (final Category c : categories) {
				for (int i = 1; i <= c.getNumberOfArguments(); i++) {
					final int[][] array = new int[maxSentenceLength][maxSentenceLength];
					categoryToArgumentToHeadToModifierToHash.put(c, i, array);
					for (int head = 0; head < maxSentenceLength; head++) {
						for (int child = 0; child < maxSentenceLength; child++) {
							array[head][child] = randomGenerator.nextInt();
						}
					}
				}
			}
		}

		private final Map<SyntaxTreeNode, Integer> nodeToHash = new HashMap<>();
		private final Table<Category, Integer, int[][]> categoryToArgumentToHeadToModifierToHash;

		private int getHash(final SyntaxTreeNode parse) {
			Integer result = nodeToHash.get(parse);
			if (result == null) {
				result = 0;
				final List<UnlabelledDependency> resolvedUnlabelledDependencies = parse
						.getResolvedUnlabelledDependencies();
				if (resolvedUnlabelledDependencies != null) {
					for (final UnlabelledDependency dep : resolvedUnlabelledDependencies) {
						for (final int arg : dep.getArguments()) {
							if (dep.getHead() != arg) {
								result = result
										^ categoryToArgumentToHeadToModifierToHash.get(dep.getCategory(),
												dep.getArgNumber())[dep.getHead()][arg];
							}
						}

					}
				}

				for (final SyntaxTreeNode child : parse.getChildren()) {
					result = result ^ getHash(child);
				}
			}

			return result;
		}

		/**
		 * Chart Cell used for N-best parsing. It allows multiple entries with the same category, if they are not
		 * equivalent.
		 */
		protected class CellNBest extends ChartCell {
			private final ListMultimap<Object, AgendaItem> keyToEntries = ArrayListMultimap.create();

			@Override
			public Collection<AgendaItem> getEntries() {
				return keyToEntries.values();
			}

			@Override
			public boolean add(final Object key, final AgendaItem newEntry) {
				final List<AgendaItem> existing = keyToEntries.get(key);
				if (existing.size() > nbest
						|| (existing.size() > 0 && newEntry.getCost() < nbestBeam * existing.get(0).getCost())) {
					return false;
				} else {
					keyToEntries.put(key, newEntry);
					return true;
				}

			}

			@Override
			int size() {
				return keyToEntries.size();
			}
		}

		/**
		 * Chart Cell used for N-best parsing. It allows multiple entries with the same category, if they are not
		 * equivalent.
		 */
		class CellNBestWithHashing extends ChartCell {
			private final ListMultimap<Object, AgendaItem> keyToEntries = ArrayListMultimap.create();
			private final Multimap<Object, Integer> keyToHash = HashMultimap.create();

			@Override
			public Collection<AgendaItem> getEntries() {
				return keyToEntries.values();
			}

			@Override
			public boolean add(final Object key, final AgendaItem newEntry) {

				final List<AgendaItem> existing = keyToEntries.get(key);
				if (existing.size() > nbest
						|| (existing.size() > 0 && newEntry.getCost() < nbestBeam * existing.get(0).getCost())) {
					return false;
				} else {
					final Integer hash = getHash(newEntry.getParse());
					if (keyToHash.containsEntry(key, hash)) {
						// Already have an equivalent node.
						return false;
					}

					keyToEntries.put(key, newEntry);
					keyToHash.put(key, hash);

					// Cache out hash for this parse.
					nodeToHash.put(newEntry.getParse(), hash);
					return true;
				}
			}

			@Override
			int size() {
				return keyToEntries.size();
			}
		}

		@Override
		public ChartCell make() {
			return // new CellNBest();
			new CellNBestWithHashing();
		}

		@Override
		public void newSentence() {
			nodeToHash.clear();
		}
	}

	/**
	 * Chart Cell used for 1-best parsing.
	 */
	protected static class Cell1Best extends ChartCell {
		final Map<Object, AgendaItem> keyToProbability = new HashMap<>();

		@Override
		public Collection<AgendaItem> getEntries() {
			return keyToProbability.values();
		}

		@Override
		public boolean add(final Object key, final AgendaItem entry) {
			return keyToProbability.putIfAbsent(key, entry) == null;
		}

		@Override
		int size() {
			return keyToProbability.size();
		}

		public static ChartCellFactory factory() {
			return new ChartCellFactory() {

				@Override
				public ChartCell make() {
					return new Cell1Best();
				}
			};
		}
	}

	protected static class Cell1BestTreeBased extends ChartCell {
		final FastTreeMap<Object, AgendaItem> keyToProbability = new FastTreeMap<>();

		@Override
		public Iterable<AgendaItem> getEntries() {
			return keyToProbability.values();
		}

		@Override
		public boolean add(final Object key, final AgendaItem entry) {
			return keyToProbability.putIfAbsent(key, entry);
		}

		@Override
		int size() {
			return keyToProbability.size();
		}

		public static ChartCellFactory factory() {
			return new ChartCellFactory() {

				@Override
				public ChartCell make() {
					return new Cell1BestTreeBased();
				}
			};
		}
	}

	protected static class Cell1BestCKY extends Cell1Best {
		@Override
		public Collection<AgendaItem> getEntries() {
			return keyToProbability.values();
		}

		@Override
		public boolean add(final Object key, final AgendaItem entry) {
			final AgendaItem currentEntry = keyToProbability.get(key);
			if (currentEntry == null || entry.getInsideScore() > currentEntry.getInsideScore()) {
				keyToProbability.put(key, entry);
				return true;
			} else {
				return false;
			}
		}

	}

	static class CellNoDynamicProgram extends ChartCell {
		private final List<AgendaItem> entries;

		CellNoDynamicProgram() {
			this(new ArrayList<>());
		}

		CellNoDynamicProgram(final List<AgendaItem> entries) {
			this.entries = entries;

		}

		@Override
		public Collection<AgendaItem> getEntries() {
			return entries;
		}

		@Override
		public boolean add(final Object key, final AgendaItem newEntry) {
			return entries.add(newEntry);
		}

		@Override
		int size() {
			return entries.size();
		}

	}

	static abstract class ChartCellFactory {
		public abstract ChartCell make();

		/**
		 * Reset factory for a new sentence.
		 */
		public void newSentence() {
		}
	}
}