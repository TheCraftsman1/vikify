package com.vikify.app.utils

/**
 * Shuffle Utilities for Context Queue
 * 
 * Provides clean shuffle index generation that:
 * 1. Keeps the current song at the start (index 0 in the mapping)
 * 2. Shuffles all other indices randomly
 * 3. Never reorders the original list - only creates a mapping
 */
object ShuffleUtils {
    
    /**
     * Generate shuffle indices that preserve the current song at position 0.
     * 
     * Example: For a list of 5 songs with startIndex = 2:
     * - Original order: [0, 1, 2, 3, 4]
     * - Possible result: [2, 0, 4, 1, 3]
     * 
     * This means:
     * - Play position 0 → original[2] (current song)
     * - Play position 1 → original[0]
     * - Play position 2 → original[4]
     * - etc.
     * 
     * @param size Total number of songs in the list
     * @param startIndex Index of the currently playing song (to preserve at start)
     * @return List of indices representing the shuffled play order
     */
    fun generateShuffleIndices(size: Int, startIndex: Int): List<Int> {
        if (size <= 1) return (0 until size).toList()
        
        // Create a list [0, 1, 2, ... size-1]
        val indices = (0 until size).toMutableList()
        
        // Remove the current song so it doesn't appear twice
        indices.remove(startIndex)
        
        // Shuffle the remaining indices
        indices.shuffle()
        
        // Add the current song at the very start (so position 0 maps to current song)
        indices.add(0, startIndex)
        
        return indices
    }
    
    /**
     * Get the original index from shuffle position.
     * 
     * @param shuffleIndices The shuffle mapping
     * @param playPosition The current position in shuffle order (0-based)
     * @return The index in the original list
     */
    fun getOriginalIndex(shuffleIndices: List<Int>, playPosition: Int): Int {
        return if (playPosition in shuffleIndices.indices) {
            shuffleIndices[playPosition]
        } else {
            playPosition
        }
    }
    
    /**
     * Find the shuffle position for a given original index.
     * 
     * @param shuffleIndices The shuffle mapping
     * @param originalIndex The index in the original list
     * @return The position in the shuffle order, or -1 if not found
     */
    fun getShufflePosition(shuffleIndices: List<Int>, originalIndex: Int): Int {
        return shuffleIndices.indexOf(originalIndex)
    }
}
