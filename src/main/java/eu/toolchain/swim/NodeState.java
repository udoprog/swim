package eu.toolchain.swim;

/**
 * <ul>
 * <li>UNKNOWN = cannot be determined</li>
 * <li>ALIVE = a node is considered as alive</li>
 * <li>DEAD = only happens if a node explicitly tries to remove itself from the cluster.</li>
 * </ul>
 */
public enum NodeState {
    UNKNOWN, ALIVE, LEAVING
}