

package jetbrains.buildServer.clouds.google.utils

/**
 * Provides number sequence.
 */
interface IdProvider {
    /**
     * Gets a next integer from sequence.
     *
     * @return identifier.
     */
    val nextId: Int
}