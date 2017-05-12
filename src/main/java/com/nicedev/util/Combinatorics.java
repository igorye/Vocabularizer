package com.nicedev.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class Combinatorics {
	
	private static final int ARRAY_LIST_MAX_CAPACITY = Integer.MAX_VALUE - 8;
	
	static long factorial(int n) {
		if (n <= 0) return 0;
		if (n == 1) return 1;
		long result = 1;
		for (int i = 2; i <= n; i++) {
			result *= i;
			if (result >>> 63 == 1) throw new Error("Number overflow");
		}
		return result;
	}
	// start * (start+1)* .. * (end - 1)* end
	static long getRangeProduct(int rangeStart, int rangeEnd) {
		if (rangeStart >= rangeEnd) throw new IllegalArgumentException();
		if (rangeStart <= 0 && rangeEnd >=0) return 0;
		return LongStream.rangeClosed(rangeStart, rangeEnd).reduce(1, (left, right) -> left * right);
	}
	
	// n! / ((n-m)! * m!) == (n-m+1)*..*(n-1)*n / m!
	static long getUniqueCombinationsCount(int n, int m) {
		checkArgs(n, m);
		return getRangeProduct(n - m + 1, n) / factorial(m);
	}
	
	// n! / (n-m)! == (n-m+1)* .. *(n-1)*n
	private static long getAccommodationsCount(int n, int m) {
		checkArgs(n, m);
		return getRangeProduct(n - m + 1, n);
	}
	
	private static void checkArgs(int n, int m) {
		if (m <= 0)
			throw new IllegalArgumentException("bySize should be grater than 0");
		if (m >= n)
			throw new IllegalArgumentException("bySize should be less than inputSet size");
	}
	
	public static int[][] getUniqueCombinations(int[] inputSet, int bySize) {
		if (bySize <=0)  throw new IllegalArgumentException("bySize should be grater than 0");
		if (bySize > inputSet.length) throw new IllegalArgumentException("bySize should be less or equal inputSet size");
		int combinationsCount = (int) Combinatorics.getUniqueCombinationsCount(inputSet.length, bySize);
		int[][] combinations = new int[combinationsCount][];
		for (int i = 0; i < combinationsCount; i++)
			combinations[i] = new int[bySize];
		int[] currentSubset = Arrays.copyOf(inputSet, bySize);
		int[] addedCount = new int[]{0};
		getUniqueCombinations(inputSet, combinations, bySize, 0, currentSubset, addedCount);
		return combinations;
	}
	
	private static void getUniqueCombinations(int[] inputSet, int[][] combinations, int bySize, int offset,
	                                          int[] currentSubset, int[] addedCount) {
		if (offset > inputSet.length-bySize) return;
		if (bySize == 0) {
			combinations[addedCount[0]++] = Arrays.copyOf(currentSubset, currentSubset.length);
			return;
		}
		currentSubset[currentSubset.length - bySize] = inputSet[offset];
		getUniqueCombinations(inputSet, combinations, bySize - 1, offset + 1, currentSubset, addedCount);
		getUniqueCombinations(inputSet, combinations, bySize, offset+1, currentSubset, addedCount);
	}
	
	public static void forEachUniqueCombination(int[] inputSet, int bySize, Consumer<int[]> consumer) {
		int[] currentSubset = new int[bySize];
		forEachUniqueCombination(inputSet, bySize, 0, currentSubset, consumer);
	}
	
	private static void forEachUniqueCombination(int[] inputSet, int bySize, int offset,
	                                             int[] currentSubset, Consumer<int[]> consumer) {
		if (offset > inputSet.length-bySize) return;
		if (bySize == 0) {
			consumer.accept(Arrays.copyOf(currentSubset, currentSubset.length));
			return;
		}
		currentSubset[currentSubset.length - bySize] = inputSet[offset];
		forEachUniqueCombination(inputSet, bySize - 1, offset + 1, currentSubset, consumer);
		forEachUniqueCombination(inputSet, bySize, offset+1, currentSubset, consumer);
	}
	
	@SuppressWarnings("unchecked")
	public static<T> T[][] getUniqueCombinations(T[] inputSet, int bySize) {
		int combinationsCount = (int) Combinatorics.getUniqueCombinationsCount(inputSet.length, bySize);
		T[][] combinations = (T[][]) Array.newInstance(inputSet.getClass(), combinationsCount);
		T[] currentSubset = Arrays.copyOf(inputSet, bySize);
		int[] addedCount = new int[]{0};
		getUniqueCombinations(inputSet, combinations, bySize, 0, currentSubset, addedCount);
		return combinations;
	}
	
	private static <T> void getUniqueCombinations(T[] inputSet, T[][] combinations, int size, int offset,
	                                              T[] currentSubset, int[] addedCount) {
		if (offset > inputSet.length-size) return;
		if (size == 0) {
			combinations[addedCount[0]++] = Arrays.copyOf(currentSubset, currentSubset.length);
			return;
		}
		currentSubset[currentSubset.length - size] = inputSet[offset];
		getUniqueCombinations(inputSet, combinations, size - 1, offset + 1, currentSubset, addedCount);
		getUniqueCombinations(inputSet, combinations, size, offset+1, currentSubset, addedCount);
	}
	
	public static <T> void forEachUniqueCombination(T[] inputSet, int bySize, Consumer<T[]> consumer) {
		forEachUniqueCombination(inputSet, bySize, 0, Arrays.copyOf(inputSet, bySize), consumer);
	}
	
	private static <T> void forEachUniqueCombination(T[] inputSet, int bySize, int offset,
	                                                 T[] currentSubset, Consumer<T[]> consumer) {
		if (offset > inputSet.length-bySize) return;
		if (bySize == 0) {
			consumer.accept(Arrays.copyOf(currentSubset, currentSubset.length));
			return;
		}
		currentSubset[currentSubset.length - bySize] = inputSet[offset];
		forEachUniqueCombination(inputSet, bySize - 1, offset + 1, currentSubset, consumer);
		forEachUniqueCombination(inputSet, bySize, offset+1, currentSubset, consumer);
	}
	
	/*public static<T> Collection<Collection<T>> getUniqueCombinations(Collection<T> inputSet, int bySize) {
		int combinationsCount = (int) Combinatorics.getUniqueCombinationsCount(inputSet.size(), bySize);
		Collection<Collection<T>> combinations = new ArrayList<>(combinationsCount);
		List<T> currentSubset = new ArrayList<>(inputSet.stream().limit(bySize).collect(Collectors.toList()));
		getUniqueCombinations(inputSet, combinations, bySize, 0, currentSubset);
		return combinations;
	}
	
	private static <T> void getUniqueCombinations(Collection<T> inputSet, Collection<Collection<T>> subsets,
	                                              int bySize, int offset, List<T> currentSubset) {
		if (offset > (inputSet.size() - bySize)) return;
		if (bySize == 0) {
			subsets.add(new ArrayList<>(currentSubset));
			return;
		}
		int index = currentSubset.size() - bySize;
		T element = inputSet.stream().skip(offset).findFirst().orElseThrow(NullPointerException::new);
		currentSubset.set(index, element);
		getUniqueCombinations(inputSet, subsets, bySize - 1, offset + 1, currentSubset);
		getUniqueCombinations(inputSet, subsets, bySize, offset+1, currentSubset);
	}*/
	
	public static<T> Collection<Collection<T>> getUniqueCombinations(Collection<T> inputSet, int bySize) {
		int combinationsCount = (int) Combinatorics.getUniqueCombinationsCount(inputSet.size(), bySize);
		Collection<Collection<T>> combinations = new ArrayList<>(combinationsCount);
		forEachUniqueCombination(inputSet, bySize, combinations::add);
		return combinations;
	}
	
	public static<T> void forEachUniqueCombination(Collection<T> inputSet, int bySize, Consumer<Collection<T>> consumer) {
		List<T> currentSubset = new ArrayList<>(inputSet.stream().limit(bySize).collect(Collectors.toList()));
		forEachUniqueCombination(inputSet, bySize, 0, currentSubset, consumer);
	}
	
	private static <T> void forEachUniqueCombination(Collection<T> inputSet, int bySize, int offset,
	                                                 List<T> currentSubset, Consumer<Collection<T>> consumer) {
		if (offset > (inputSet.size() - bySize)) return;
		if (bySize == 0) {
			consumer.accept(new ArrayList<>(currentSubset));
			return;
		}
		int index = currentSubset.size() - bySize;
		T element = inputSet.stream().skip(offset).findFirst().orElseThrow(NullPointerException::new);
		currentSubset.set(index, element);
		forEachUniqueCombination(inputSet, bySize - 1, offset + 1, currentSubset, consumer);
		forEachUniqueCombination(inputSet, bySize, offset+1, currentSubset, consumer);
	}
	
	public static <T> void forEachPermutation(T[] inputSet, Consumer<T[]> consumer) {
		T[] copy = Arrays.copyOf(inputSet, inputSet.length);
		forEachPermutation(copy, inputSet.length, consumer);
	}
	
	private static <T> void forEachPermutation(T[] inputSet, int bySize, Consumer<T[]> consumer) {
		if (bySize > 1) {
			int offset = inputSet.length - bySize;
			for (int i = offset; i < inputSet.length; i++) {
				T[] permutation = Arrays.copyOf(inputSet, inputSet.length);
				swap(permutation, offset, i);
				forEachPermutation(permutation, bySize - 1, consumer);
			}
		} else {
			consumer.accept(inputSet);
		}
	}
	
	public static <T> void forEachPermutation(Collection<T> inputSet, Consumer<Collection<T>> consumer) {
		Collection<T> copy = new ArrayList<>(inputSet);
		forEachPermutation(copy, inputSet.size(), consumer);
	}
	
	private static <T> void forEachPermutation(Collection<T> inputSet, int bySize, Consumer<Collection<T>> consumer) {
		if (bySize > 1) {
			int offset = inputSet.size() - bySize;
			for (int i = offset; i < inputSet.size(); i++) {
				List<T> permutation = new ArrayList<>(inputSet);
				swap(permutation, offset, i);
				forEachPermutation(permutation, bySize - 1, consumer);
			}
		} else {
			consumer.accept(new ArrayList<>(inputSet));
		}
	}
	
	@SuppressWarnings("unchecked")
	public static<T> T[][] getPermutations(T[] inputSet) {
		int permutationsCount = (int) factorial(inputSet.length);
		T[][] permutations = (T[][]) Array.newInstance(inputSet.getClass(), permutationsCount);
		int[] addedCount = new int[]{0};
		getPermutations(inputSet, permutations, inputSet.length, addedCount);
		return permutations;
	}
	
	private static <T> void getPermutations(T[] inputSet, T[][] permutations, int bySize, int[] addedCount) {
		if (bySize > 1) {
			int offset = inputSet.length - bySize;
			for (int i = offset; i < inputSet.length; i++) {
				T[] permutation = Arrays.copyOf(inputSet, inputSet.length);
				swap(permutation, offset, i);
				getPermutations(permutation, permutations, bySize - 1, addedCount);
			}
		} else {
			permutations[addedCount[0]++] = Arrays.copyOf(inputSet, inputSet.length);
		}
	}
	
	public static <T> Collection<Collection<T>> getPermutations(Collection<T> inputSet) {
		Collection<Collection<T>> permutations = new ArrayList<>();
		forEachPermutation(inputSet, inputSet.size(), permutations::add);
		return permutations;
	}
	
	private static <T> void swap(T[] array, int from, int to) {
		T temp = array[from];		array[from] = array[to];		array[to] = temp;
	}
	
	private static <T> void swap(List<T> list, int from, int to) {
		T temp = list.get(from);		list.set(from, list.get(to));		list.set(to, temp);
	}
	
	public static <T> void forEachAccommodation(Collection<T> inputSet, int bySize, Consumer<Collection<T>> consumer) {
		Collection<T> copy = new ArrayList<>(inputSet);
		if (bySize < inputSet.size())
			forEachCombinationAndPermutation(copy, bySize, consumer);
		else
			forEachPermutation(copy, bySize, consumer);
	}
	
	private static <T> void forEachCombinationAndPermutation(Collection<T> inputSet, int bySize, Consumer<Collection<T>> consumer) {
		forEachUniqueCombination(inputSet, bySize, ts -> forEachPermutation(ts, ts.size(), consumer));
	}
	
	public static <T> Collection<Collection<T>> getAccommodations(Collection<T> inputSet, int bySize) {
		long accommodationCountL = getAccommodationsCount(inputSet.size(), bySize);
		if (accommodationCountL > ARRAY_LIST_MAX_CAPACITY)
			throw new OutOfMemoryError("Quantity of accommodations is exceeding collection's capacity");
		int accommodationCount = (int) accommodationCountL;
		Collection<Collection<T>> accommodations = new ArrayList<>(accommodationCount);
		forEachAccommodation(inputSet, bySize, accommodations::add);
		return accommodations;
	}
	
}