package event;

/**
 * Kind of item the player used, carried by {@link ItemUsedEvent}.
 */
public enum UsedItemType {
    /** A Cluster Grenade was launched. */
    CLUSTER_GRENADE,
    /** An Energy Shield item was collected, which raises the shield at once. */
    ENERGY_SHIELD
}
