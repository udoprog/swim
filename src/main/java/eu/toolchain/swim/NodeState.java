package eu.toolchain.swim;

/**
 * <ul>
 * <li>SUSPECT = cannot be determined</li>
 * <li>ALIVE = a node is considered as alive</li>
 * <li>CONFIRM = only happens if a node explicitly tries to remove itself from the cluster.</li>
 * </ul>
 */
public enum NodeState {
    SUSPECT, ALIVE, CONFIRM
}