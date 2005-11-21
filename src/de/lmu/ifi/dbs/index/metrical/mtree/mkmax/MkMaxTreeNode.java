package de.lmu.ifi.dbs.index.metrical.mtree.mkmax;

import de.lmu.ifi.dbs.data.MetricalObject;
import de.lmu.ifi.dbs.distance.Distance;
import de.lmu.ifi.dbs.distance.DistanceFunction;
import de.lmu.ifi.dbs.index.metrical.mtree.MTreeNode;
import de.lmu.ifi.dbs.persistent.PageFile;
import de.lmu.ifi.dbs.utilities.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in a MkMax-Tree.
 *
 * @author Elke Achtert (<a href="mailto:achtert@dbs.ifi.lmu.de">achtert@dbs.ifi.lmu.de</a>)
 */
class MkMaxTreeNode<O extends MetricalObject, D extends Distance<D>> extends MTreeNode<O, D> {
  /**
   * Empty constructor for Externalizable interface.
   */
  public MkMaxTreeNode() {
  }

  /**
   * Creates a MkMaxTreeNode object.
   *
   * @param file     the file storing the MkMax-Tree
   * @param capacity the capacity (maximum number of entries plus 1 for overflow) of this node
   * @param isLeaf   indicates wether this node is a leaf node
   */
  public MkMaxTreeNode(PageFile<MTreeNode<O, D>> file, int capacity, boolean isLeaf) {
    super(file, capacity, isLeaf);
  }

  /**
   * Creates a new leaf node with the specified capacity.
   *
   * @param capacity the capacity of the new node
   * @return a new leaf node
   */
  protected MkMaxTreeNode<O, D> createNewLeafNode(int capacity) {
    return new MkMaxTreeNode<O, D>(file, capacity, true);
  }

  /**
   * Creates a new directory node with the specified capacity.
   *
   * @param capacity the capacity of the new node
   * @return a new directory node
   */
  protected MkMaxTreeNode<O, D> createNewDirectoryNode(int capacity) {
    return new MkMaxTreeNode<O, D>(file, capacity, false);
  }

  /**
   * Determines and returns the knn distance of this node as the maximum
   * knn distance of all entries.
   *
   * @param distanceFunction the distance function
   * @return the knn distance of this node
   */
  protected List<D> kNNDistances(DistanceFunction<O, D> distanceFunction) {
    int k = ((MkMaxEntry<D>) entries[0]).getK();

    List<D> result = new ArrayList<D>();
    for (int i = 0; i < k; i++) {
      result.add(distanceFunction.nullDistance());
    }

    for (int i = 0; i < numEntries; i++) {
      for (int j = 0; j < k; j++) {
        MkMaxEntry<D> entry = (MkMaxEntry<D>) entries[i];
        D kDist = result.remove(j);
        result.add(j, Util.max(kDist, entry.getKnnDistance(j + 1)));
      }
    }

    return result;
  }
}
