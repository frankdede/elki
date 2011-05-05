package de.lmu.ifi.dbs.elki.database.query.knn;

import java.util.List;

import de.lmu.ifi.dbs.elki.data.spatial.SpatialComparable;
import de.lmu.ifi.dbs.elki.database.ids.ArrayDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.query.DistanceResultPair;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.SpatialPrimitiveDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.index.tree.spatial.SpatialIndex;

/**
 * Instance of a KNN query for a particular spatial index.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.uses SpatialIndex
 * @apiviz.uses SpatialPrimitiveDistanceFunction
 */
public class SpatialIndexKNNQuery<O extends SpatialComparable, D extends Distance<D>> extends AbstractDistanceKNNQuery<O, D> {
  /**
   * The index to use
   */
  protected final SpatialIndex<O, ?, ?> index;

  /**
   * Spatial primitive distance function
   */
  protected final SpatialPrimitiveDistanceFunction<? super O, D> distanceFunction;

  /**
   * Constructor.
   * 
   * @param relation Relation to use
   * @param index Index to use
   * @param distanceQuery Distance query to use
   * @param distanceFunction Distance function
   */
  public SpatialIndexKNNQuery(Relation<? extends O> relation, SpatialIndex<O, ?, ?> index, DistanceQuery<O, D> distanceQuery, SpatialPrimitiveDistanceFunction<? super O, D> distanceFunction) {
    super(relation, distanceQuery);
    this.index = index;
    this.distanceFunction = distanceFunction;
  }

  @Override
  public List<DistanceResultPair<D>> getKNNForObject(O obj, int k) {
    return index.kNNQuery(obj, k, distanceFunction);
  }

  @Override
  public List<DistanceResultPair<D>> getKNNForDBID(DBID id, int k) {
    return getKNNForObject(relation.get(id), k);
  }
  
  @Override
  public List<List<DistanceResultPair<D>>> getKNNForBulkDBIDs(ArrayDBIDs ids, int k) {
    // FIXME: supported?
    return index.bulkKNNQueryForIDs(ids, k, distanceFunction);
  }

  @Override
  public D getDistanceFactory() {
    return distanceQuery.getDistanceFactory();
  }
}