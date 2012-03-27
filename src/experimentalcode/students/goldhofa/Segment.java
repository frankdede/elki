package experimentalcode.students.goldhofa;

import java.util.Arrays;

import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;


/**
 * Identifies a CircleSegment by its clusterings and cluster. Can be stored as
 * and retrieved from a String.
 * 
 * A segmentID String consists of the cluster id of each clustering, ordered by
 * clustering and separated by a character. Thus a segment ID describes the
 * common pairs in all clusterings and cluster.
 * 
 * i.e. clusteringID 0 & clusterID 2, clusteringID 1 & clusterID 0 => segmentID:
 * 2-0
 */
public class Segment implements Comparable<Segment> {
  private static final String SEPARATOR = "-";

  /**
   * Object is not clustered
   */
  public static final int UNCLUSTERED = -1;

  public int segmentObjects = 0;
  
  public ModifiableDBIDs objIds = null;

  // Cluster ids
  protected int[] clusterIds;

  public Segment(int clusterings) {
    clusterIds = new int[clusterings];
  }
  
  public int getPairCount() {
    return (segmentObjects * (segmentObjects - 1)) / 2;
  }

  /**
   * Creates a SegmentID by its String representation, extracting clustering and
   * corresponding clusterIDs.
   * 
   * @param segmentID String representation of SegmentID
   */
  public Segment(String segmentID) {
    String[] id = segmentID.split(SEPARATOR);
    clusterIds = new int[id.length];
    for(int i = 0; i < id.length; i++) {
      set(i, Integer.valueOf(id[i]).intValue());
    }
  }

  public void set(int i, int currentCluster) {
    clusterIds[i] = currentCluster;
  }

  public int get(int idx) {
    return clusterIds[idx];
  }

  public int size() {
    return clusterIds.length;
  }

  /**
   * Checks if the segment has a cluster with unpaired objects. Unpaired
   * clusters are represented by "0" (0 = all).
   * 
   * @return
   */
  public boolean isUnpaired() {
    for(int id : clusterIds) {
      if(id == UNCLUSTERED) {
        return true;
      }
    }

    return false;
  }

  /**
   * Check if this segment contains the pairs that are never clustered by any of
   * the clusterings (all 0).
   * 
   * @return
   */
  public boolean isNone() {
    for(int id : clusterIds) {
      if(id != UNCLUSTERED) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns the index of the first clustering having an unpaired cluster, or -1
   * no unpaired cluster exists.
   * 
   * @return clustering id or -1
   */
  public int getUnpairedClusteringIndex() {
    int index = 0;
    for(int id : clusterIds) {
      if(id == UNCLUSTERED) {
        return index;
      }
      index++;
    }

    return -1;
  }

  @Override
  public String toString() {
    String string = "";
    for(int id : clusterIds) {
      string += id + SEPARATOR;
    }
    return string.substring(0, string.length() - SEPARATOR.length());
  }

  @Override
  public boolean equals(Object obj) {
    if(!(Segment.class.isInstance(obj))) {
      return false;
    }

    Segment other = (Segment) obj;
    return Arrays.equals(clusterIds, other.clusterIds);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(clusterIds);
  }

  @Override
  public int compareTo(Segment sid) {
    if(this.size() < sid.size()) {
      return -1;
    }
    if(this.size() > sid.size()) {
      return 1;
    }

    for(int i = 0; i < this.size(); i++) {
      if(this.clusterIds[i] < sid.clusterIds[i]) {
        return -1;
      }
      else if(this.clusterIds[i] > sid.clusterIds[i]) {
        return 1;
      }
    }
    return 0;
  }
}