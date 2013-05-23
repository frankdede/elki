package de.lmu.ifi.dbs.elki.algorithm.clustering.hierarchical;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import gnu.trove.list.array.TDoubleArrayList;

import java.util.ArrayList;
import java.util.Comparator;

import de.lmu.ifi.dbs.elki.algorithm.clustering.ClusteringAlgorithm;
import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.model.DendrogramModel;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DBIDDataStore;
import de.lmu.ifi.dbs.elki.database.datastore.DataStore;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.DoubleDistanceDataStore;
import de.lmu.ifi.dbs.elki.database.datastore.WritableIntegerDataStore;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBIDArrayIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDVar;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterEqualConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DistanceParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.EnumParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;
import de.lmu.ifi.dbs.elki.workflow.AlgorithmStep;

/**
 * Extract a flat clustering from a full hierarchy, represented in pointer form.
 * 
 * FIXME: re-check tie handling!
 * 
 * @author Erich Schubert
 */
public class ExtractFlatClusteringFromHierarchy<D extends Distance<D>> implements ClusteringAlgorithm<Clustering<DendrogramModel<D>>> {
  /**
   * Class logger.
   */
  private static final Logging LOG = Logging.getLogger(ExtractFlatClusteringFromHierarchy.class);

  /**
   * Threshold mode.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static enum ThresholdMode {
    /** Cut by minimum number of clusters */
    BY_MINCLUSTERS,
    /** Cut by threshold */
    BY_THRESHOLD,
    /** No thresholding */
    NO_THRESHOLD,
  }

  /**
   * Output mode.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static enum OutputMode {
    /** Strict partitioning. */
    STRICT_PARTITIONS,
    /** Partial hierarchy. */
    PARTIAL_HIERARCHY,
  }

  /**
   * Minimum number of clusters to extract
   */
  private final int minclusters;

  /**
   * Clustering algorithm to run to obtain the hierarchy.
   */
  private HierarchicalClusteringAlgorithm<D> algorithm;

  /**
   * Include empty cluster in the hierarchy produced.
   */
  private OutputMode outputmode = OutputMode.PARTIAL_HIERARCHY;

  /**
   * Threshold for extracting clusters.
   */
  private D threshold = null;

  /**
   * Constructor.
   * 
   * @param algorithm Algorithm to run
   * @param minclusters Minimum number of clusters
   * @param outputmode Output mode: truncated hierarchy or strict partitions.
   */
  public ExtractFlatClusteringFromHierarchy(HierarchicalClusteringAlgorithm<D> algorithm, int minclusters, OutputMode outputmode) {
    super();
    this.algorithm = algorithm;
    this.threshold = null;
    this.minclusters = minclusters;
    this.outputmode = outputmode;
  }

  /**
   * Constructor.
   * 
   * @param algorithm Algorithm to run
   * @param minclusters Minimum number of clusters
   * @param outputmode Output mode: truncated hierarchy or strict partitions.
   */
  public ExtractFlatClusteringFromHierarchy(HierarchicalClusteringAlgorithm<D> algorithm, D threshold, OutputMode outputmode) {
    super();
    this.algorithm = algorithm;
    this.threshold = threshold;
    this.minclusters = -1;
    this.outputmode = outputmode;
  }

  @Override
  public Clustering<DendrogramModel<D>> run(Database database) {
    PointerHierarchyRepresentationResult<D> pointerresult = algorithm.run(database);
    DBIDs ids = pointerresult.getDBIDs();
    DBIDDataStore pi = pointerresult.getParentStore();
    DataStore<D> lambda = pointerresult.getParentDistanceStore();

    Clustering<DendrogramModel<D>> result;
    if (lambda instanceof DoubleDistanceDataStore) {
      result = extractClustersDouble(ids, pi, (DoubleDistanceDataStore) lambda);
    } else {
      result = extractClusters(ids, pi, lambda);
    }
    result.addChildResult(pointerresult);

    return result;
  }

  /**
   * Extract all clusters from the pi-lambda-representation.
   * 
   * @param ids Object ids to process
   * @param pi Pi store
   * @param lambda Lambda store
   * 
   * @return Hierarchical clustering
   */
  private Clustering<DendrogramModel<D>> extractClusters(DBIDs ids, final DBIDDataStore pi, final DataStore<D> lambda) {
    FiniteProgress progress = LOG.isVerbose() ? new FiniteProgress("Extracting clusters", ids.size(), LOG) : null;

    // Sort DBIDs by lambda. We need this for two things:
    // a) to determine the stop distance from "minclusters" parameter
    // b) to process arrows in decreasing / increasing order
    ArrayModifiableDBIDs order = DBIDUtil.newArray(ids);
    order.sort(new CompareByLambda<>(lambda));
    DBIDArrayIter it = order.iter(); // Used multiple times.

    // The initial pass is top-down.
    int split;
    if (minclusters > 0) {
      // Stop distance:
      split = Math.max(ids.size() - minclusters, 0);
      final D stopdist = lambda.get(order.get(split));

      // Tie handling: decrement split.
      while (split > 0) {
        it.seek(split - 1);
        if (stopdist.compareTo(lambda.get(it)) == 0) {
          split--;
        } else {
          break;
        }
      }
    } else if (threshold != null) {
      split = ids.size();
      it.seek(split - 1);
      while (threshold.compareTo(lambda.get(it)) <= 0 && it.valid()) {
        split--;
        it.retract();
      }
    } else { // full hierarchy
      split = 0;
    }

    // Extract the child clusters
    int cnum = 0;
    int expcnum = (ids.size() - split);
    WritableIntegerDataStore cluster_map = DataStoreUtil.makeIntegerStorage(ids, DataStoreFactory.HINT_TEMP, -1);
    ArrayList<ModifiableDBIDs> cluster_dbids = new ArrayList<>(expcnum);
    ArrayList<D> cluster_dist = new ArrayList<>(expcnum);
    ArrayModifiableDBIDs cluster_leads = DBIDUtil.newArray(expcnum);

    DBIDVar succ = DBIDUtil.newVar(); // Variable for successor.
    // Go backwards on the lower part.
    for (it.seek(split - 1); it.valid(); it.retract()) {
      D dist = lambda.get(it); // Distance to successor
      pi.assignVar(it, succ); // succ = pi(it)
      int clusterid = cluster_map.intValue(succ);
      // Successor cluster has already been created:
      if (clusterid >= 0) {
        cluster_dbids.get(clusterid).add(it);
        cluster_map.putInt(it, clusterid);
        // Update distance to maximum encountered:
        if (cluster_dist.get(clusterid).compareTo(dist) < 0) {
          cluster_dist.set(clusterid, dist);
        }
      } else {
        // Need to start a new cluster:
        clusterid = cnum; // next cluster number.
        ModifiableDBIDs cids = DBIDUtil.newArray();
        // Add element and successor as initial members:
        cids.add(succ);
        cluster_map.putInt(succ, clusterid);
        cids.add(it);
        cluster_map.putInt(it, clusterid);
        // Store new cluster.
        cluster_dbids.add(cids);
        cluster_leads.add(succ);
        cluster_dist.add(dist);
        cnum++;
      }

      if (progress != null) {
        progress.incrementProcessed(LOG);
      }
    }
    final Clustering<DendrogramModel<D>> dendrogram;
    switch(outputmode) {
    case PARTIAL_HIERARCHY: {
      // Build a hierarchy out of these clusters.
      dendrogram = new Clustering<>("Hierarchical Clustering", "hierarchical-clustering");
      Cluster<DendrogramModel<D>> root = null;
      ArrayList<Cluster<DendrogramModel<D>>> clusters = new ArrayList<>(expcnum + expcnum - 1);
      // Convert initial clusters to cluster objects
      {
        int i = 0;
        for (DBIDIter it2 = cluster_leads.iter(); it2.valid(); it2.advance(), i++) {
          clusters.add(makeCluster(it2, cluster_dist.get(i), cluster_dbids.get(i)));
        }
        cluster_dist = null; // Invalidate
        cluster_dbids = null; // Invalidate
      }
      // Process the upper part, bottom-up.
      for (it.seek(split); it.valid(); it.advance()) {
        int clusterid = cluster_map.intValue(it);
        // The current cluster:
        final Cluster<DendrogramModel<D>> clus;
        if (clusterid >= 0) {
          clus = clusters.get(clusterid);
        } else {
          clus = makeCluster(it, null, DBIDUtil.deref(it));
          // No need to store in clusters: cannot have another incoming pi
          // pointer!
        }
        // The successor to join:
        pi.assignVar(it, succ); // succ = pi(it)
        if (DBIDUtil.equal(it, succ)) {
          assert (root == null);
          root = clus;
        } else {
          // Parent cluster:
          int parentid = cluster_map.intValue(succ);
          D depth = lambda.get(it);
          // Parent cluster exists - merge as a new cluster:
          if (parentid >= 0) {
            Cluster<DendrogramModel<D>> pclus = makeCluster(succ, depth, DBIDUtil.EMPTYDBIDS);
            dendrogram.addChildCluster(pclus, clusters.get(parentid));
            dendrogram.addChildCluster(pclus, clus);
            clusters.set(parentid, pclus); // Replace existing parent cluster
          } else {
            // Create a new, one-element, parent cluster.
            parentid = cnum;
            cnum++;
            ArrayModifiableDBIDs cids = DBIDUtil.newArray(1);
            cids.add(succ);
            Cluster<DendrogramModel<D>> pclus = makeCluster(succ, depth, cids);
            dendrogram.addChildCluster(pclus, clus);
            assert (clusters.size() == parentid);
            clusters.add(pclus); // Remember parent cluster
            cluster_map.putInt(succ, parentid); // Reference
          }
        }

        // Decrement counter
        if (progress != null) {
          progress.incrementProcessed(LOG);
        }
      }
      // wrap up:
      dendrogram.addToplevelCluster(root);
      break;
    }
    case STRICT_PARTITIONS: {
      // Build a hierarchy out of these clusters.
      dendrogram = new Clustering<>("Flattened Hierarchical Clustering", "flattened-hierarchical-clustering");
      // Convert initial clusters to cluster objects
      {
        int i = 0;
        for (DBIDIter it2 = cluster_leads.iter(); it2.valid(); it2.advance(), i++) {
          dendrogram.addToplevelCluster(makeCluster(it2, cluster_dist.get(i), cluster_dbids.get(i)));
        }
        cluster_dist = null; // Invalidate
        cluster_dbids = null; // Invalidate
      }
      // Process the upper part, bottom-up.
      for (it.seek(split); it.valid(); it.advance()) {
        int clusterid = cluster_map.intValue(it);
        if (clusterid < 0) {
          dendrogram.addToplevelCluster(makeCluster(it, null, DBIDUtil.deref(it)));
        }

        // Decrement counter
        if (progress != null) {
          progress.incrementProcessed(LOG);
        }
      }
      break;
    }
    default:
      throw new AbortException("Unsupported output mode.");
    }

    if (progress != null) {
      progress.ensureCompleted(LOG);
    }

    return dendrogram;
  }

  /**
   * Extract all clusters from the pi-lambda-representation.
   * 
   * @param ids Object ids to process
   * @param pi Pi store
   * @param lambda Lambda store
   * 
   * @return Hierarchical clustering
   */
  private Clustering<DendrogramModel<D>> extractClustersDouble(DBIDs ids, final DBIDDataStore pi, final DoubleDistanceDataStore lambda) {
    FiniteProgress progress = LOG.isVerbose() ? new FiniteProgress("Extracting clusters", ids.size(), LOG) : null;

    // Sort DBIDs by lambda. We need this for two things:
    // a) to determine the stop distance from "minclusters" parameter
    // b) to process arrows in decreasing / increasing order
    ArrayModifiableDBIDs order = DBIDUtil.newArray(ids);
    order.sort(new CompareByDoubleLambda(lambda));
    DBIDArrayIter it = order.iter(); // Used multiple times!

    int split;
    if (minclusters > 0) {
      split = Math.max(ids.size() - minclusters, 0);
      // Stop distance:
      final double stopdist = lambda.doubleValue(order.get(split));

      // Tie handling: decrement split.
      while (split > 0) {
        it.seek(split - 1);
        if (stopdist <= lambda.doubleValue(it)) {
          split--;
        } else {
          break;
        }
      }
    } else if (threshold != null) {
      split = ids.size();
      it.seek(split - 1);
      double stopdist = ((DoubleDistance) threshold).doubleValue();
      while (stopdist <= lambda.doubleValue(it) && it.valid()) {
        split--;
        it.retract();
      }
    } else { // full hierarchy
      split = 0;
    }

    // Extract the child clusters
    int cnum = 0;
    int expcnum = ids.size() - split;
    WritableIntegerDataStore cluster_map = DataStoreUtil.makeIntegerStorage(ids, DataStoreFactory.HINT_TEMP, -1);
    ArrayList<ModifiableDBIDs> cluster_dbids = new ArrayList<>(expcnum);
    TDoubleArrayList cluster_dist = new TDoubleArrayList(expcnum);
    ArrayModifiableDBIDs cluster_leads = DBIDUtil.newArray(expcnum);

    DBIDVar succ = DBIDUtil.newVar(); // Variable for successor.
    // Go backwards on the lower part.
    for (it.seek(split - 1); it.valid(); it.retract()) {
      double dist = lambda.doubleValue(it); // Distance to successor
      pi.assignVar(it, succ); // succ = pi(it)
      int clusterid = cluster_map.intValue(succ);
      // Successor cluster has already been created:
      if (clusterid >= 0) {
        cluster_dbids.get(clusterid).add(it);
        cluster_map.putInt(it, clusterid);
        // Update distance to maximum encountered:
        if (cluster_dist.get(clusterid) < dist) {
          cluster_dist.set(clusterid, dist);
        }
      } else {
        // Need to start a new cluster:
        clusterid = cnum; // next cluster number.
        ModifiableDBIDs cids = DBIDUtil.newArray();
        // Add element and successor as initial members:
        cids.add(succ);
        cluster_map.putInt(succ, clusterid);
        cids.add(it);
        cluster_map.putInt(it, clusterid);
        // Store new cluster.
        cluster_dbids.add(cids);
        cluster_leads.add(succ);
        cluster_dist.add(dist);
        cnum++;
      }

      // Decrement counter
      if (progress != null) {
        progress.incrementProcessed(LOG);
      }
    }
    final Clustering<DendrogramModel<D>> dendrogram;
    switch(outputmode) {
    case PARTIAL_HIERARCHY: {
      // Build a hierarchy out of these clusters.
      dendrogram = new Clustering<>("Hierarchical Clustering", "hierarchical-clustering");
      Cluster<DendrogramModel<D>> root = null;
      ArrayList<Cluster<DendrogramModel<D>>> clusters = new ArrayList<>(expcnum);
      // Convert initial clusters to cluster objects
      {
        int i = 0;
        for (DBIDIter it2 = cluster_leads.iter(); it2.valid(); it2.advance(), i++) {
          @SuppressWarnings("unchecked")
          D depth = (D) new DoubleDistance(cluster_dist.get(i));
          clusters.add(makeCluster(it2, depth, cluster_dbids.get(i)));
        }
        cluster_dist = null; // Invalidate
        cluster_dbids = null; // Invalidate
      }
      // Process the upper part, bottom-up.
      for (it.seek(split); it.valid(); it.advance()) {
        int clusterid = cluster_map.intValue(it);
        // The current cluster:
        final Cluster<DendrogramModel<D>> clus;
        if (clusterid >= 0) {
          clus = clusters.get(clusterid);
        } else {
          clus = makeCluster(it, null, DBIDUtil.deref(it));
        }
        // The successor to join:
        pi.assignVar(it, succ); // succ = pi(it)
        if (DBIDUtil.equal(it, succ)) {
          assert (root == null);
          root = clus;
        } else {
          // Parent cluster:
          int parentid = cluster_map.intValue(succ);
          @SuppressWarnings("unchecked")
          D depth = (D) new DoubleDistance(lambda.doubleValue(it));
          // Parent cluster exists - merge as a new cluster:
          if (parentid >= 0) {
            final Cluster<DendrogramModel<D>> pclus = clusters.get(parentid);
            if (pclus.size() <= 1 && pclus.getModel().getDistance().equals(depth)) {
              dendrogram.addChildCluster(pclus, clus);
            } else {
              Cluster<DendrogramModel<D>> npclus = makeCluster(succ, depth, DBIDUtil.EMPTYDBIDS);
              dendrogram.addChildCluster(npclus, pclus);
              dendrogram.addChildCluster(npclus, clus);
              clusters.set(parentid, npclus); // Replace existing parent cluster
            }
          } else {
            // Create a new, one-element cluster for parent, and a merged
            // cluster on top.
            parentid = cnum;
            cnum++;
            Cluster<DendrogramModel<D>> pclus = makeCluster(succ, depth, DBIDUtil.EMPTYDBIDS);
            dendrogram.addChildCluster(pclus, makeCluster(succ, null, DBIDUtil.deref(succ)));
            dendrogram.addChildCluster(pclus, clus);
            assert (clusters.size() == parentid);
            clusters.add(pclus); // Remember parent cluster
            cluster_map.putInt(succ, parentid); // Reference
          }
        }

        // Decrement counter
        if (progress != null) {
          progress.incrementProcessed(LOG);
        }
      }
      // attach root
      dendrogram.addToplevelCluster(root);
      break;
    }
    case STRICT_PARTITIONS: {
      // Build a hierarchy out of these clusters.
      dendrogram = new Clustering<>("Flattened Hierarchical Clustering", "flattened-hierarchical-clustering");
      // Convert initial clusters to cluster objects
      {
        int i = 0;
        for (DBIDIter it2 = cluster_leads.iter(); it2.valid(); it2.advance(), i++) {
          @SuppressWarnings("unchecked")
          D depth = (D) new DoubleDistance(cluster_dist.get(i));
          dendrogram.addToplevelCluster(makeCluster(it2, depth, cluster_dbids.get(i)));
        }
        cluster_dist = null; // Invalidate
        cluster_dbids = null; // Invalidate
      }
      // Process the upper part, bottom-up.
      for (it.seek(split); it.valid(); it.advance()) {
        int clusterid = cluster_map.intValue(it);
        if (clusterid < 0) {
          dendrogram.addToplevelCluster(makeCluster(it, null, DBIDUtil.deref(it)));
        }

        // Decrement counter
        if (progress != null) {
          progress.incrementProcessed(LOG);
        }
      }
      break;
    }
    default:
      throw new AbortException("Unsupported output mode.");
    }

    if (progress != null) {
      progress.ensureCompleted(LOG);
    }

    return dendrogram;
  }

  /**
   * Make the cluster for the given object
   * 
   * @param lead Leading object
   * @param depth Linkage depth
   * @param members Member objects
   * @return Cluster
   */
  private Cluster<DendrogramModel<D>> makeCluster(DBIDRef lead, D depth, DBIDs members) {
    final String name;
    if (members.size() == 0) {
      name = "merge_" + DBIDUtil.toString(lead) + "_" + depth;
    } else if (depth != null && depth.isInfiniteDistance() || members.size() == 1) {
      assert (members.contains(lead));
      name = "object_" + DBIDUtil.toString(lead);
    } else if (depth != null) {
      name = "cluster_" + DBIDUtil.toString(lead) + "_" + depth;
    } else {
      // Complete data set only?
      name = "cluster_" + DBIDUtil.toString(lead);
    }
    Cluster<DendrogramModel<D>> cluster = new Cluster<>(name, members, new DendrogramModel<>(depth));
    return cluster;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return algorithm.getInputTypeRestriction();
  }

  /**
   * Order a DBID collection by the lambda value.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   * 
   * @param <D> Distance type
   */
  private static final class CompareByLambda<D extends Distance<D>> implements Comparator<DBIDRef> {
    /**
     * Lambda storage
     */
    private final DataStore<D> lambda;

    /**
     * Constructor.
     * 
     * @param lambda Lambda storage
     */
    protected CompareByLambda(DataStore<D> lambda) {
      this.lambda = lambda;
    }

    @Override
    public int compare(DBIDRef id1, DBIDRef id2) {
      D k1 = lambda.get(id1);
      D k2 = lambda.get(id2);
      assert (k1 != null);
      assert (k2 != null);
      return k1.compareTo(k2);
    }
  }

  /**
   * Order a DBID collection by the lambda value.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  private static final class CompareByDoubleLambda implements Comparator<DBIDRef> {
    /**
     * Lambda storage
     */
    private final DoubleDistanceDataStore lambda;

    /**
     * Constructor.
     * 
     * @param lambda Lambda storage
     */
    protected CompareByDoubleLambda(DoubleDistanceDataStore lambda) {
      this.lambda = lambda;
    }

    @Override
    public int compare(DBIDRef id1, DBIDRef id2) {
      double k1 = lambda.doubleValue(id1);
      double k2 = lambda.doubleValue(id2);
      return Double.compare(k1, k2);
    }
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<D extends Distance<D>> extends AbstractParameterizer {
    /**
     * Extraction mode to use.
     */
    public static final OptionID MODE_ID = new OptionID("hierarchical.threshold-mode", "The thresholding mode to use for extracting clusters: by desired number of clusters, or by distance threshold.");

    /**
     * The minimum number of clusters to extract.
     */
    public static final OptionID MINCLUSTERS_ID = new OptionID("hierarchical.minclusters", "The minimum number of clusters to extract (there may be more clusters when tied).");

    /**
     * The threshold level for which to extract the clustering.
     */
    public static final OptionID THRESHOLD_ID = new OptionID("hierarchical.threshold", "The threshold level for which to extract the clusters.");

    /**
     * Flag to include empty clusters that build the top of the hierarchy.
     */
    public static final OptionID OUTPUTMODE_ID = new OptionID("hierarchical.output-mode", "The output mode: a truncated cluster hierarchy, or a strict (flat) partitioning of the data set.");

    /**
     * Number of clusters to extract.
     */
    int minclusters = -1;

    /**
     * Threshold level.
     */
    D threshold = null;

    /**
     * Flag to produce empty clusters to model the hierarchy above.
     */
    OutputMode outputmode = null;

    /**
     * The hierarchical clustering algorithm to run.
     */
    HierarchicalClusteringAlgorithm<D> algorithm;

    /**
     * Threshold mode.
     */
    ThresholdMode thresholdmode = null;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      ObjectParameter<HierarchicalClusteringAlgorithm<D>> algorithmP = new ObjectParameter<>(AlgorithmStep.Parameterizer.ALGORITHM_ID, HierarchicalClusteringAlgorithm.class);
      if (config.grab(algorithmP)) {
        algorithm = algorithmP.instantiateClass(config);
      }

      EnumParameter<ThresholdMode> modeP = new EnumParameter<>(MODE_ID, ThresholdMode.class, ThresholdMode.BY_MINCLUSTERS);
      if (config.grab(modeP)) {
        thresholdmode = modeP.getValue();
      }

      if (thresholdmode == null || ThresholdMode.BY_MINCLUSTERS.equals(thresholdmode)) {
        IntParameter minclustersP = new IntParameter(MINCLUSTERS_ID);
        minclustersP.addConstraint(new GreaterEqualConstraint(1));
        if (config.grab(minclustersP)) {
          minclusters = minclustersP.intValue();
        }
      }

      if (thresholdmode == null || ThresholdMode.BY_THRESHOLD.equals(thresholdmode)) {
        DistanceParameter<D> distP = new DistanceParameter<>(THRESHOLD_ID, algorithm.getDistanceFactory());
        if (config.grab(distP)) {
          threshold = distP.getValue();
        }
      }

      if (thresholdmode == null || !ThresholdMode.NO_THRESHOLD.equals(thresholdmode)) {
        EnumParameter<OutputMode> outputP = new EnumParameter<>(OUTPUTMODE_ID, OutputMode.class);
        if (config.grab(outputP)) {
          outputmode = outputP.getValue();
        }
      } else {
        // This becomes full hierarchy:
        minclusters = -1;
        outputmode = OutputMode.PARTIAL_HIERARCHY;
      }
    }

    @Override
    protected ExtractFlatClusteringFromHierarchy<D> makeInstance() {
      switch(thresholdmode) {
      case NO_THRESHOLD:
      case BY_MINCLUSTERS:
        return new ExtractFlatClusteringFromHierarchy<>(algorithm, minclusters, outputmode);
      case BY_THRESHOLD:
        return new ExtractFlatClusteringFromHierarchy<>(algorithm, threshold, outputmode);
      default:
        throw new AbortException("Unknown extraction mode.");
      }
    }
  }
}