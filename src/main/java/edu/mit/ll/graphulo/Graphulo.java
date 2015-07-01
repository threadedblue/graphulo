package edu.mit.ll.graphulo;

import com.google.common.base.Preconditions;
import edu.mit.ll.graphulo.apply.Abs0Apply;
import edu.mit.ll.graphulo.apply.ApplyIterator;
import edu.mit.ll.graphulo.apply.ColQSpecialByteApply;
import edu.mit.ll.graphulo.apply.JaccardDegreeApply;
import edu.mit.ll.graphulo.apply.OnlyRowApply;
import edu.mit.ll.graphulo.ewise.AndEWiseX;
import edu.mit.ll.graphulo.ewise.EWiseOp;
import edu.mit.ll.graphulo.reducer.EdgeBFSReducer;
import edu.mit.ll.graphulo.reducer.GatherColQReducer;
import edu.mit.ll.graphulo.reducer.SingleBFSReducer;
import edu.mit.ll.graphulo.rowmult.AndMultiply;
import edu.mit.ll.graphulo.rowmult.CartesianRowMultiply;
import edu.mit.ll.graphulo.rowmult.EdgeBFSMultiply;
import edu.mit.ll.graphulo.rowmult.LineRowMultiply;
import edu.mit.ll.graphulo.rowmult.LongMultiply;
import edu.mit.ll.graphulo.rowmult.MultiplyOp;
import edu.mit.ll.graphulo.rowmult.SelectorRowMultiply;
import edu.mit.ll.graphulo.skvi.CountAllIterator;
import edu.mit.ll.graphulo.skvi.MinMaxValueFilter;
import edu.mit.ll.graphulo.skvi.RemoteWriteIterator;
import edu.mit.ll.graphulo.skvi.SingleTransposeIterator;
import edu.mit.ll.graphulo.skvi.SmallLargeRowFilter;
import edu.mit.ll.graphulo.skvi.TableMultIterator;
import edu.mit.ll.graphulo.skvi.TriangularFilter;
import edu.mit.ll.graphulo.skvi.TwoTableIterator;
import edu.mit.ll.graphulo.util.GraphuloUtil;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.BatchScanner;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.BatchWriterConfig;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.MutationsRejectedException;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.TableExistsException;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.admin.TableOperations;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.Combiner;
import org.apache.accumulo.core.iterators.LongCombiner;
import org.apache.accumulo.core.iterators.ValueFormatException;
import org.apache.accumulo.core.iterators.user.ColumnSliceFilter;
import org.apache.accumulo.core.iterators.user.SummingCombiner;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.hadoop.io.Text;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Holds a {@link org.apache.accumulo.core.client.Connector} to an Accumulo instance for calling core client Graphulo operations.
 */
public class Graphulo {
  private static final Logger log = LogManager.getLogger(Graphulo.class);

  public static final IteratorSetting DEFAULT_PLUS_ITERATOR;

  static {
    IteratorSetting sumSetting = new IteratorSetting(6, SummingCombiner.class);
    LongCombiner.setEncodingType(sumSetting, LongCombiner.Type.STRING);
    Combiner.setCombineAllColumns(sumSetting, true);
    DEFAULT_PLUS_ITERATOR = sumSetting;
  }

  protected Connector connector;
  protected PasswordToken password;

  public Graphulo(Connector connector, PasswordToken password) {
    this.connector = connector;
    this.password = password;
    checkCredentials();
  }

  /**
   * Check password works for this user.
   */
  private void checkCredentials() {
    try {
      if (!connector.securityOperations().authenticateUser(connector.whoami(), password))
        throw new IllegalArgumentException("instance " + connector.getInstance().getInstanceName() + ": bad username " + connector.whoami() + " with password " + new String(password.getPassword()));
    } catch (AccumuloException | AccumuloSecurityException e) {
      throw new IllegalArgumentException("instance " + connector.getInstance().getInstanceName() + ": error with username " + connector.whoami() + " with password " + new String(password.getPassword()), e);
    }
  }

  private static final Text EMPTY_TEXT = new Text();


  public long TableMult(String ATtable, String Btable, String Ctable, String CTtable,
                        Class<? extends MultiplyOp> multOp, IteratorSetting plusOp,
                        Collection<Range> rowFilter,
                        String colFilterAT, String colFilterB) {
    return TableMult(ATtable, Btable, Ctable, CTtable, -1, multOp, plusOp, rowFilter, colFilterAT, colFilterB,
        false, false, -1, false);
  }

  public long SpEWiseX(String Atable, String Btable, String Ctable, String CTtable,
                       int BScanIteratorPriority,
                       Class<? extends EWiseOp> multOp, IteratorSetting plusOp,
                       Collection<Range> rowFilter,
                       String colFilterAT, String colFilterB,
                       int numEntriesCheckpoint, boolean trace) {
    return TwoTableEWISE(Atable, Btable, Ctable, CTtable, BScanIteratorPriority,
        multOp, plusOp, rowFilter, colFilterAT, colFilterB,
        false, false, Collections.<IteratorSetting>emptyList(),
        Collections.<IteratorSetting>emptyList(), Collections.<IteratorSetting>emptyList(),
        numEntriesCheckpoint, trace);
  }

  public long SpEWiseSum(String Atable, String Btable, String Ctable, String CTtable,
                         int BScanIteratorPriority,
                         Class<? extends EWiseOp> multOp, IteratorSetting plusOp,
                         Collection<Range> rowFilter,
                         String colFilterAT, String colFilterB,
                         int numEntriesCheckpoint, boolean trace) {
    return TwoTableEWISE(Atable, Btable, Ctable, CTtable, BScanIteratorPriority,
        multOp, plusOp, rowFilter, colFilterAT, colFilterB,
        true, true, Collections.<IteratorSetting>emptyList(),
        Collections.<IteratorSetting>emptyList(), Collections.<IteratorSetting>emptyList(),
        numEntriesCheckpoint, trace);
  }

  /**
   * C += A * B.
   * User-defined "plus" and "multiply". Requires transpose table AT instead of A.
   * If C is not given, then the scan itself returns the results of A * B. (not thoroughly tested)
   *  @param ATtable              Name of Accumulo table holding matrix transpose(A).
   * @param Btable               Name of Accumulo table holding matrix B.
   * @param Ctable               Name of table to store result. Null means don't store the result.
   * @param CTtable              Name of table to store transpose of result. Null means don't store the transpose.
   * @param BScanIteratorPriority Priority to use for Table Multiplication scan-time iterator on table B
   * @param multOp               An operation that "multiplies" two values.
   * @param plusOp               An SKVI to apply to the result table that "sums" values. Not applied if null.
   * @param rowFilter            Row subset of ATtable and Btable, like "a,:,b,g,c,:,". Null means run on all rows.
   * @param colFilterAT          Column qualifier subset of AT. Null means run on all columns.
   * @param colFilterB           Column qualifier subset of B. Null means run on all columns.
   * @param alsoDoAA             Whether to also compute A*A at the same time as A*B. Default false.
   * @param alsoDoBB             Whether to also compute B*B at the same time as A*B. Default false.
   * @param numEntriesCheckpoint # of entries before we emit a checkpoint entry from the scan. -1 means no monitoring.
   * @param trace                Enable server-side performance tracing.
   * @return Number of partial products processed through the RemoteWriteIterator.
   */
  public long TableMult(String ATtable, String Btable, String Ctable, String CTtable,
                        int BScanIteratorPriority,
                        Class<? extends MultiplyOp> multOp, IteratorSetting plusOp,
                        Collection<Range> rowFilter,
                        String colFilterAT, String colFilterB,
                        boolean alsoDoAA, boolean alsoDoBB,
                        int numEntriesCheckpoint, boolean trace) {
    return TwoTableROWCartesian(ATtable, Btable, Ctable, CTtable, BScanIteratorPriority,
        multOp, plusOp, rowFilter, colFilterAT, colFilterB,
        alsoDoAA, alsoDoBB, alsoDoAA, alsoDoBB, Collections.<IteratorSetting>emptyList(),
        Collections.<IteratorSetting>emptyList(), Collections.<IteratorSetting>emptyList(),
        numEntriesCheckpoint, trace);
  }

  /**
   * C += A * B.
   * User-defined "plus" and "multiply". Requires transpose table AT instead of A.
   * If C is not given, then the scan itself returns the results of A * B. (not thoroughly tested)
   *  @param ATtable              Name of Accumulo table holding matrix transpose(A).
   * @param Btable               Name of Accumulo table holding matrix B.
   * @param Ctable               Name of table to store result. Null means don't store the result.
   * @param CTtable              Name of table to store transpose of result. Null means don't store the transpose.
   * @param BScanIteratorPriority Priority to use for Table Multiplication scan-time iterator on table B
   * @param multOp               An operation that "multiplies" two values.
   * @param plusOp               An SKVI to apply to the result table that "sums" values. Not applied if null.
   * @param rowFilter            Row subset of ATtable and Btable, like "a,:,b,g,c,:,". Null means run on all rows.
   * @param colFilterAT          Column qualifier subset of AT. Null means run on all columns.
   * @param colFilterB           Column qualifier subset of B. Null means run on all columns.
   * @param alsoDoAA             Whether to also compute A*A at the same time as A*B. Default false.
   * @param alsoDoBB             Whether to also compute B*B at the same time as A*B. Default false.
   * @param iteratorsBeforeA     Extra iterators used on ATtable before TableMult.
   * @param iteratorsBeforeB     Extra iterators used on  Btable before TableMult.
   * @param iteratorsAfterTwoTable  Extra iterators used after TableMult but before writing entries to Ctable and CTtable.
   * @param numEntriesCheckpoint # of entries before we emit a checkpoint entry from the scan. -1 means no monitoring.
   * @param trace                Enable server-side performance tracing.
   * @return Number of partial products processed through the RemoteWriteIterator.
   */
  public long TableMult(String ATtable, String Btable, String Ctable, String CTtable,
                        int BScanIteratorPriority, Class<? extends MultiplyOp> multOp, IteratorSetting plusOp,
                        Collection<Range> rowFilter, String colFilterAT, String colFilterB,
                        boolean alsoDoAA, boolean alsoDoBB,
                        List<IteratorSetting> iteratorsBeforeA, List<IteratorSetting> iteratorsBeforeB,
                        List<IteratorSetting> iteratorsAfterTwoTable,
                        int numEntriesCheckpoint, boolean trace) {
    return TwoTableROWCartesian(ATtable, Btable, Ctable, CTtable, BScanIteratorPriority,
        multOp, plusOp, rowFilter, colFilterAT, colFilterB,
        alsoDoAA, alsoDoBB, alsoDoAA, alsoDoBB, iteratorsBeforeA, iteratorsBeforeB, iteratorsAfterTwoTable,
        numEntriesCheckpoint, trace);
  }

  public long TwoTableROWCartesian(String ATtable, String Btable, String Ctable, String CTtable,
                                   int BScanIteratorPriority,
                                   //TwoTableIterator.DOTMODE dotmode, //CartesianRowMultiply.ROWMODE rowmode,
                                   Class<? extends MultiplyOp> multOp, IteratorSetting plusOp,
                                   Collection<Range> rowFilter,
                                   String colFilterAT, String colFilterB,
                                   boolean emitNoMatchA, boolean emitNoMatchB,
                                   boolean alsoDoAA, boolean alsoDoBB,
                                   List<IteratorSetting> iteratorsBeforeA, List<IteratorSetting> iteratorsBeforeB,
                                   List<IteratorSetting> iteratorsAfterTwoTable,
                                   int numEntriesCheckpoint, boolean trace) {
    Map<String,String> opt = new HashMap<>();
    opt.put("rowMultiplyOp", CartesianRowMultiply.class.getName());
    opt.put("rowMultiplyOp.opt.multiplyOp", multOp.getName()); // treated same as multiplyOp
    opt.put("rowMultiplyOp.opt.rowmode", CartesianRowMultiply.ROWMODE.ONEROWA.name());
    opt.put("rowMultiplyOp.opt."+CartesianRowMultiply.ALSODOAA, Boolean.toString(alsoDoAA));
    opt.put("rowMultiplyOp.opt."+CartesianRowMultiply.ALSODOBB, Boolean.toString(alsoDoBB));

    return TwoTable(ATtable, Btable, Ctable, CTtable, BScanIteratorPriority,
        TwoTableIterator.DOTMODE.ROW, opt, plusOp,
        rowFilter, colFilterAT, colFilterB,
        emitNoMatchA, emitNoMatchB, iteratorsBeforeA, iteratorsBeforeB, iteratorsAfterTwoTable,
        numEntriesCheckpoint, trace);
  }

  public long TwoTableROWSelector(
      String ATtable, String Btable, String Ctable, String CTtable,
      int BScanIteratorPriority,
      Collection<Range> rowFilter,
      String colFilterAT, String colFilterB,
      boolean ASelectsBRow,
      List<IteratorSetting> iteratorsBeforeA, List<IteratorSetting> iteratorsBeforeB,
      List<IteratorSetting> iteratorsAfterTwoTable,
      int numEntriesCheckpoint, boolean trace
  ) {
    Map<String,String> opt = new HashMap<>();
    opt.put("rowMultiplyOp", SelectorRowMultiply.class.getName());
    opt.put("rowMultiplyOp.opt."+SelectorRowMultiply.ASELECTSBROW, Boolean.toString(ASelectsBRow));

    return TwoTable(ATtable, Btable, Ctable, CTtable, BScanIteratorPriority,
        TwoTableIterator.DOTMODE.ROW, opt, null,
        rowFilter, colFilterAT, colFilterB,
        false, false, iteratorsBeforeA, iteratorsBeforeB, iteratorsAfterTwoTable,
        numEntriesCheckpoint, trace);
  }

  public long TwoTableEWISE(String ATtable, String Btable, String Ctable, String CTtable,
                            int BScanIteratorPriority,
                            //TwoTableIterator.DOTMODE dotmode, //CartesianRowMultiply.ROWMODE rowmode,
                          Class<? extends EWiseOp> multOp, IteratorSetting plusOp,
                          Collection<Range> rowFilter,
                          String colFilterAT, String colFilterB,
                          boolean emitNoMatchA, boolean emitNoMatchB,
                            List<IteratorSetting> iteratorsBeforeA, List<IteratorSetting> iteratorsBeforeB,
                            List<IteratorSetting> iteratorsAfterTwoTable,
                          int numEntriesCheckpoint, boolean trace) {
    Map<String,String> opt = new HashMap<>();
    opt.put("multiplyOp", multOp.getName());

    return TwoTable(ATtable, Btable, Ctable, CTtable, BScanIteratorPriority,
        TwoTableIterator.DOTMODE.EWISE, opt, plusOp,
        rowFilter, colFilterAT, colFilterB,
        emitNoMatchA, emitNoMatchB, iteratorsBeforeA, iteratorsBeforeB, iteratorsAfterTwoTable,
        numEntriesCheckpoint, trace);
  }

  public long TwoTableNONE(String ATtable, String Btable, String Ctable, String CTtable,
                           int BScanIteratorPriority,
                           //TwoTableIterator.DOTMODE dotmode, //CartesianRowMultiply.ROWMODE rowmode,
                            Class<? extends MultiplyOp> multOp, IteratorSetting plusOp,
                            Collection<Range> rowFilter,
                            String colFilterAT, String colFilterB,
                            boolean emitNoMatchA, boolean emitNoMatchB,
                           List<IteratorSetting> iteratorsBeforeA, List<IteratorSetting> iteratorsBeforeB,
                           List<IteratorSetting> iteratorsAfterTwoTable,
                            int numEntriesCheckpoint, boolean trace) {
    Map<String,String> opt = new HashMap<>();

    return TwoTable(ATtable, Btable, Ctable, CTtable, BScanIteratorPriority,
        TwoTableIterator.DOTMODE.NONE, opt, plusOp,
        rowFilter, colFilterAT, colFilterB,
        emitNoMatchA, emitNoMatchB, iteratorsBeforeA, iteratorsBeforeB, iteratorsAfterTwoTable,
        numEntriesCheckpoint, trace);
  }

  private long TwoTable(String ATtable, String Btable, String Ctable, String CTtable,
                        int BScanIteratorPriority,
                       TwoTableIterator.DOTMODE dotmode, Map<String, String> setupOpts,
                       IteratorSetting plusOp, // priority matters
                       Collection<Range> rowFilter,
                       String colFilterAT, String colFilterB,
                       boolean emitNoMatchA, boolean emitNoMatchB,
                        // RemoteSourceIterator has its own priority for scan-time iterators.
                        // Could override by "diterPriority" option
                        List<IteratorSetting> iteratorsBeforeA, List<IteratorSetting> iteratorsBeforeB,
                        List<IteratorSetting> iteratorsAfterTwoTable, // priority doesn't matter for these three
                       int numEntriesCheckpoint, boolean trace) {
    if (ATtable == null || ATtable.isEmpty())
      throw new IllegalArgumentException("Please specify table AT. Given: " + ATtable);
    if (Btable == null || Btable.isEmpty())
      throw new IllegalArgumentException("Please specify table B. Given: " + Btable);
    // Prevent possibility for infinite loop:
    if (ATtable.equals(Ctable))
      throw new UnsupportedOperationException("Could lead to unpredictable results: ATtable=Ctable=" + ATtable);
    if (Btable.equals(Ctable))
      throw new UnsupportedOperationException("Could lead to unpredictable results: Btable=Ctable=" + Btable);
    if (ATtable.equals(CTtable))
      throw new UnsupportedOperationException("Could lead to unpredictable results: ATtable=CTtable=" + ATtable);
    if (Btable.equals(CTtable))
      throw new UnsupportedOperationException("Could lead to unpredictable results: Btable=CTtable=" + Btable);
    if (iteratorsAfterTwoTable == null) iteratorsAfterTwoTable = Collections.emptyList();
    if (iteratorsBeforeA == null) iteratorsBeforeA = Collections.emptyList();
    if (iteratorsBeforeB == null) iteratorsBeforeB = Collections.emptyList();
    if (BScanIteratorPriority <= 0)
      BScanIteratorPriority = 7; // default priority

    if (Ctable != null && Ctable.isEmpty())
      Ctable = null;
    if (CTtable != null && CTtable.isEmpty())
      CTtable = null;
    if (Ctable == null && CTtable == null)
      log.warn("Streaming back result of multiplication to client does not guarantee correctness." +
          "In particular, if Accumulo destroys, re-inits and re-seeks an iterator stack, the stack may not recover.");

    if (dotmode == null)
      throw new IllegalArgumentException("dotmode is required but given null");
//    if (multOp == null)
//      throw new IllegalArgumentException("multOp is required but given null");
    if (rowFilter != null && rowFilter.isEmpty())
      rowFilter = null;

    TableOperations tops = connector.tableOperations();
    if (!tops.exists(ATtable))
      throw new IllegalArgumentException("Table AT does not exist. Given: " + ATtable);
    if (!tops.exists(Btable))
      throw new IllegalArgumentException("Table B does not exist. Given: " + Btable);

    if (Ctable != null && !tops.exists(Ctable))
      try {
        tops.create(Ctable);
      } catch (AccumuloException | AccumuloSecurityException e) {
        log.error("error trying to create Ctable " + Ctable, e);
        throw new RuntimeException(e);
      } catch (TableExistsException e) {
        log.error("crazy", e);
        throw new RuntimeException(e);
      }

    if (CTtable != null && !tops.exists(CTtable))
      try {
        tops.create(CTtable);
      } catch (AccumuloException | AccumuloSecurityException e) {
        log.error("error trying to create CTtable " + Ctable, e);
        throw new RuntimeException(e);
      } catch (TableExistsException e) {
        log.error("crazy", e);
        throw new RuntimeException(e);
      }

    Map<String, String>
        optTT = basicRemoteOpts("AT.", ATtable),
        optRWI = (Ctable != null || CTtable != null) ? basicRemoteOpts("", Ctable, CTtable) : new HashMap<String,String>();
//    optTT.put("trace", String.valueOf(trace)); // logs timing on server // todo Temp removed -- setting of Watch enable
    optTT.put("dotmode", dotmode.name());
    optTT.putAll(setupOpts);
    if (colFilterAT != null)
      optTT.put("AT.colFilter", colFilterAT);
    if (Ctable != null || CTtable != null) {
      optRWI.put("numEntriesCheckpoint", String.valueOf(numEntriesCheckpoint));
    }
    optTT.put("AT.emitNoMatch", Boolean.toString(emitNoMatchA));
    optTT.put("B.emitNoMatch", Boolean.toString(emitNoMatchB));

    if (Ctable != null && plusOp != null)
      GraphuloUtil.applyIteratorSoft(plusOp, tops, Ctable);
    if (CTtable != null && plusOp != null)
      GraphuloUtil.applyIteratorSoft(plusOp, tops, CTtable);

    // scan B with TableMultIterator
    BatchScanner bs;
    try {
      bs = connector.createBatchScanner(Btable, Authorizations.EMPTY, 50); // TODO P2: set number of batch scan threads
    } catch (TableNotFoundException e) {
      log.error("crazy", e);
      throw new RuntimeException(e);
    }

    if (rowFilter != null) {
      if (Ctable != null || CTtable != null) {
        optRWI.put("rowRanges", GraphuloUtil.rangesToD4MString(rowFilter)); // translate row filter to D4M notation
        bs.setRanges(Collections.singleton(new Range()));
      } else
        bs.setRanges(rowFilter);
    } else
      bs.setRanges(Collections.singleton(new Range()));

    DynamicIteratorSetting dis = new DynamicIteratorSetting();
    for (IteratorSetting setting : iteratorsBeforeA)
      dis.append(setting);
    optTT.putAll(dis.buildSettingMap("AT.diter."));

    dis = new DynamicIteratorSetting();
    for (IteratorSetting setting : iteratorsBeforeB)
      dis.append(setting);
    optTT.putAll(dis.buildSettingMap("B.diter."));

    dis = new DynamicIteratorSetting();
    GraphuloUtil.applyGeneralColumnFilter(colFilterB, bs, dis, true);
    dis.append(new IteratorSetting(1, TwoTableIterator.class, optTT));
    for (IteratorSetting setting : iteratorsAfterTwoTable)
      dis.append(setting);
//    dis.append(new IteratorSetting(1, DebugInfoIterator.class)); // DEBUG
    dis.append(new IteratorSetting(1, RemoteWriteIterator.class, optRWI));
    bs.addScanIterator(dis.toIteratorSetting(BScanIteratorPriority));



    // for monitoring: reduce table-level parameter controlling when Accumulo sends back an entry to the client
    String prevPropTableScanMaxMem = null;
    if (numEntriesCheckpoint > 0 && (Ctable != null || CTtable != null))
      try {
        for (Map.Entry<String, String> entry : tops.getProperties(Btable)) {
          if (entry.getKey().equals("table.scan.max.memory"))
            prevPropTableScanMaxMem = entry.getValue();
        }
//        System.out.println("prevPropTableScanMaxMem: "+prevPropTableScanMaxMem);
        if (prevPropTableScanMaxMem == null)
          log.warn("expected table.scan.max.memory to be set on table " + Btable);
        else {
          tops.setProperty(Btable, "table.scan.max.memory", "1B");
        }
      } catch (AccumuloException | AccumuloSecurityException e) {
        log.warn("error trying to get and set property table.scan.max.memory for " + Btable, e);
      } catch (TableNotFoundException e) {
        log.error("crazy", e);
        throw new RuntimeException(e);
      }

    // Do the BatchScan on B
    long numEntries = 0, thisEntries;
    try {
      for (Map.Entry<Key, Value> entry : bs) {
        if (Ctable != null || CTtable != null) {
//          log.debug(entry.getKey() + " -> " + entry.getValue() + " AS " + Key.toPrintableString(entry.getValue().get(), 0, entry.getValue().get().length, 40) + " RAW "+ Arrays.toString(entry.getValue().get()));
          thisEntries = RemoteWriteIterator.decodeValue(entry.getValue(), null);
          log.debug(entry.getKey() + " -> " + thisEntries + " entries processed");
          numEntries += thisEntries;
        } else {
          log.debug(entry.getKey() + " -> " + entry.getValue());
        }
      }
    } finally {
      bs.close();
      if (prevPropTableScanMaxMem != null) {
        try {
          tops.setProperty(Btable, "table.scan.max.memory", prevPropTableScanMaxMem);
        } catch (AccumuloException | AccumuloSecurityException e) {
          log.error("cannot reset table.scan.max.memory property for " + Btable + " to " + prevPropTableScanMaxMem, e);
        }
      }
    }

    return numEntries;

//    // flush
//    if (Ctable != null) {
//      try {
//        long st = System.currentTimeMillis();
//        tops.flush(Ctable, null, null, true);
//        System.out.println("flush " + Ctable + " time: " + (System.currentTimeMillis() - st) + " ms");
//      } catch (TableNotFoundException e) {
//        log.error("crazy", e);
//        throw new RuntimeException(e);
//      } catch (AccumuloSecurityException | AccumuloException e) {
//        log.error("error while flushing " + Ctable);
//        throw new RuntimeException(e);
//      }
//      GraphuloUtil.removeCombiner(tops, Ctable, log);
//    }

  }


  /**
   * Use LongCombiner to sum.
   */
  public String AdjBFS(String Atable, String v0, int k, String Rtable, String RtableTranspose,
                       String ADegtable, String degColumn, boolean degInColQ, int minDegree, int maxDegree) {

    return AdjBFS(Atable, v0, k, Rtable, RtableTranspose, -1,
        ADegtable, degColumn, degInColQ, minDegree, maxDegree,
        DEFAULT_PLUS_ITERATOR, false);
  }

  /**
   * Adjacency table Breadth First Search. Sums entries into Rtable from each step of the BFS.
   *
   * @param Atable      Name of Accumulo table.
   * @param v0          Starting nodes, like "a,f,b,c,". Null or empty string "" means start from all nodes.
   *                    v0 may be a range of nodes like "c,:,e,g,k,:,".
   * @param k           Number of steps
   * @param Rtable      Name of table to store result. Null means don't store the result.
   * @param RTtable     Name of table to store transpose of result. Null means don't store the transpose.
   * @param AScanIteratorPriority Priority to use for scan-time iterator on table A
   * @param ADegtable   Name of table holding out-degrees for A. Leave null to filter on the fly with
   *                    the {@link SmallLargeRowFilter}, or do no filtering if minDegree=0 and maxDegree=Integer.MAX_VALUE.
   * @param degColumn   Name of column for out-degrees in ADegtable like "deg". Null means the empty column "".
   *                    If degInColQ==true, this is the prefix before the numeric portion of the column like "deg|", and null means no prefix. Unused if ADegtable is null.
   * @param degInColQ   True means degree is in the Column Qualifier. False means degree is in the Value.
   *                    Unused if ADegtable is null.
   * @param minDegree   Minimum out-degree. Checked before doing any searching, at every step, from ADegtable. Pass 0 for no filtering.
   * @param maxDegree   Maximum out-degree. Checked before doing any searching, at every step, from ADegtable. Pass Integer.MAX_VALUE for no filtering.
   * @param plusOp      An SKVI to apply to the result table that "sums" values. Not applied if null.
   * @param trace       Enable server-side performance tracing.
   * @return          The nodes reachable in exactly k steps from v0.
   */
  @SuppressWarnings("unchecked")
  public String AdjBFS(String Atable, String v0, int k, String Rtable, String RTtable,
                       int AScanIteratorPriority,
                       String ADegtable, String degColumn, boolean degInColQ, int minDegree, int maxDegree,
                       IteratorSetting plusOp,
                       boolean trace) {
    boolean needDegreeFiltering = minDegree > 0 || maxDegree < Integer.MAX_VALUE;
    if (Atable == null || Atable.isEmpty())
      throw new IllegalArgumentException("Please specify Adjacency table. Given: " + Atable);
    if (ADegtable != null && ADegtable.isEmpty())
      ADegtable = null;
    if (Rtable != null && Rtable.isEmpty())
      Rtable = null;
    if (RTtable != null && RTtable.isEmpty())
      RTtable = null;
    if (minDegree < 1)
      minDegree = 1;
    if (maxDegree < minDegree)
      throw new IllegalArgumentException("maxDegree=" + maxDegree + " should be >= minDegree=" + minDegree);
    if (AScanIteratorPriority <= 0)
      AScanIteratorPriority = 4; // default priority

    if (degColumn == null)
      degColumn = "";
    Text degColumnText = new Text(degColumn);

    if (plusOp != null && plusOp.getPriority() >= 20)
      log.warn("Sum iterator setting is >=20. Are you sure you want the priority after the default Versioning iterator priority? " + plusOp);
    if (v0 == null || v0.isEmpty())
      v0 = ":\t";
    Collection<Text> vktexts = new HashSet<>(); //v0 == null ? null : GraphuloUtil.d4mRowToTexts(v0);
    char sep = v0.charAt(v0.length() - 1);

    TableOperations tops = connector.tableOperations();
    if (!tops.exists(Atable))
      throw new IllegalArgumentException("Table A does not exist. Given: " + Atable);
    if (Rtable != null && !tops.exists(Rtable))
      try {
        tops.create(Rtable);
      } catch (AccumuloException | AccumuloSecurityException e) {
        log.error("error trying to create R table " + Rtable, e);
        throw new RuntimeException(e);
      } catch (TableExistsException e) {
        log.error("crazy", e);
        throw new RuntimeException(e);
      }
    if (RTtable != null && !tops.exists(RTtable))
      try {
        tops.create(RTtable);
      } catch (AccumuloException | AccumuloSecurityException e) {
        log.error("error trying to create R table transpose " + RTtable, e);
        throw new RuntimeException(e);
      } catch (TableExistsException e) {
        log.error("crazy", e);
        throw new RuntimeException(e);
      }

    Map<String, String> opt = Rtable != null || RTtable != null ? basicRemoteOpts("", Rtable, RTtable) : new HashMap<String,String>();
//    opt.put("trace", String.valueOf(trace)); // logs timing on server
    opt.put("reducer", GatherColQReducer.class.getName());
    if (Rtable != null || RTtable != null) {
      if (Rtable != null && plusOp != null)
        GraphuloUtil.applyIteratorSoft(plusOp, tops, Rtable);
      if (RTtable != null && plusOp != null)
        GraphuloUtil.applyIteratorSoft(plusOp, tops, RTtable);
    }

    BatchScanner bs;
    try {
      bs = connector.createBatchScanner(Atable, Authorizations.EMPTY, 50); // TODO P2: set number of batch scan threads
    } catch (TableNotFoundException e) {
      log.error("crazy", e);
      throw new RuntimeException(e);
    }

    DynamicIteratorSetting dis = new DynamicIteratorSetting();
    IteratorSetting itsetDegreeFilter = null;
    if (needDegreeFiltering && ADegtable == null) {
      itsetDegreeFilter = new IteratorSetting(3, SmallLargeRowFilter.class);
      SmallLargeRowFilter.setMinColumns(itsetDegreeFilter, minDegree);
      SmallLargeRowFilter.setMaxColumns(itsetDegreeFilter, maxDegree);
    }
    bs.setRanges(Collections.singleton(new Range()));

    try {
      long degTime = 0, scanTime = 0;
      for (int thisk = 1; thisk <= k; thisk++) {
        if (trace)
          if (thisk == 1)
            System.out.println("First step: v0 is " + v0);
          else
            System.out.println("k=" + thisk + " before filter" +
                (vktexts.size() > 5 ? " #=" + String.valueOf(vktexts.size()) : ": " + vktexts.toString()));

        bs.clearScanIterators();
        dis.clear();

        if (needDegreeFiltering && ADegtable != null) { // use degree table
          long t1 = System.currentTimeMillis(), dur;
          vktexts = thisk == 1
              ? filterTextsDegreeTable(ADegtable, degColumnText, degInColQ, minDegree, maxDegree, vktexts, GraphuloUtil.d4mRowToRanges(v0))
              : filterTextsDegreeTable(ADegtable, degColumnText, degInColQ, minDegree, maxDegree, vktexts);
          dur = System.currentTimeMillis() - t1;
          degTime += dur;
          if (trace) {
            System.out.println("Degree Lookup Time: " + dur + " ms");
            System.out.println("k=" + thisk + " after  filter" +
                (vktexts.size() > 5 ? " #=" + String.valueOf(vktexts.size()) : ": " + vktexts.toString()));
          }

          if (vktexts.isEmpty())
            break;
          opt.put("rowRanges", GraphuloUtil.textsToD4mString(vktexts, sep));

        } else {  // no degree table or no filtering
          if (thisk == 1)
            opt.put("rowRanges", v0);
          else
            opt.put("rowRanges", GraphuloUtil.textsToD4mString(vktexts, sep));
          if (needDegreeFiltering) // filtering but no degree table
            dis.append(itsetDegreeFilter);
        }

        dis.append(new IteratorSetting(4, RemoteWriteIterator.class, opt));
        bs.addScanIterator(dis.toIteratorSetting(AScanIteratorPriority));

        GatherColQReducer reducer = new GatherColQReducer();
        reducer.init(Collections.<String, String>emptyMap(), null);
        long t2 = System.currentTimeMillis();
        for (Map.Entry<Key, Value> entry : bs) {
//        System.out.println("A Entry: "+entry.getKey() + " -> " + entry.getValue());
          RemoteWriteIterator.decodeValue(entry.getValue(), reducer);
        }
        long dur = System.currentTimeMillis() - t2;
        scanTime += dur;

        vktexts.clear();
//      vktexts.addAll(reducer.getForClient());
        for (String uk : reducer.getForClient()) {
          vktexts.add(new Text(uk));
        }
        if (trace)
          System.out.println("BatchScan/Iterator Time: " + dur + " ms");
        if (vktexts.isEmpty())
          break;
      }

      if (trace) {
        System.out.println("Total Degree Lookup Time: " + degTime + " ms");
        System.out.println("Total BatchScan/Iterator Time: " + scanTime + " ms");
      }
    } finally {
      bs.close();
    }

    return GraphuloUtil.textsToD4mString(vktexts, sep);
  }

  /**
   * Modifies texts in place, removing the entries that are out of range.
   * Decodes degrees using {@link LongCombiner#STRING_ENCODER}.
   * We disqualify a node if we cannot parse its degree.
   * Does nothing if texts is null or the empty collection.
   * Todo: Add a local cache parameter for known good nodes and known bad nodes, so that we don't have to look them up.
   *
   * @param degColumnText   Name of the degree column qualifier. Blank/null means fetch the empty ("") column.
   * @param degInColQ False means degree in value. True means degree in column qualifier and that degColumnText is a prefix before the numeric portion of the column qualifier degree.
   * @return The same texts object, with nodes that fail the degree filter removed.
   */
  private Collection<Text> filterTextsDegreeTable(String ADegtable, Text degColumnText, boolean degInColQ,
                                                  int minDegree, int maxDegree,
                                                  Collection<Text> texts) {
    if (texts == null || texts.isEmpty())
      return texts;
    return filterTextsDegreeTable(ADegtable, degColumnText, degInColQ,
        minDegree, maxDegree, texts, GraphuloUtil.textsToRanges(texts));
  }

  /** Used when thisk==1, on first call to BFS. */
  private Collection<Text> filterTextsDegreeTable(String ADegtable, Text degColumnText, boolean degInColQ,
                                                  int minDegree, int maxDegree,
                                                  Collection<Text> texts, Collection<Range> ranges) {
    if (ranges == null || ranges.isEmpty())
      return texts;
    boolean addToTexts = false;
    if (texts == null)
      throw new IllegalArgumentException("texts is null");
    if (texts.isEmpty())
      addToTexts = true;
    if (degColumnText.getLength() == 0)
      degColumnText = null;
    TableOperations tops = connector.tableOperations();
    assert ADegtable != null && !ADegtable.isEmpty() && minDegree > 0 && maxDegree >= minDegree
        && tops.exists(ADegtable);
    BatchScanner bs;
    try {
      bs = connector.createBatchScanner(ADegtable, Authorizations.EMPTY, 4); // TODO P2: set number of batch scan threads
    } catch (TableNotFoundException e) {
      log.error("crazy", e);
      throw new RuntimeException(e);
    }
    bs.setRanges(ranges);
    if (!degInColQ)
      bs.fetchColumn(EMPTY_TEXT, degColumnText == null ? EMPTY_TEXT : degColumnText);
    else if (degColumnText != null) {
      // single range: deg|,:,deg},
      IteratorSetting itset = new IteratorSetting(1, ColumnSliceFilter.class);
      ColumnSliceFilter.setSlice(itset, degColumnText.toString(),
          true, Range.followingPrefix(degColumnText).toString(), false);
      bs.addScanIterator(itset);
    }

    try {
      Text badRow = new Text();
      for (Map.Entry<Key, Value> entry : bs) {
        boolean bad = false;
//      log.debug("Deg Entry: " + entry.getKey() + " -> " + entry.getValue());
        try {
          long deg;
          if (degInColQ) {
            if (degColumnText != null) {
              String degString = GraphuloUtil.stringAfter(degColumnText.getBytes(), entry.getKey().getColumnQualifierData().getBackingArray());
              if (degString == null) // should never occur since we use a column filter
                continue;
              else
                deg = Long.parseLong(degString);
            } else
              deg = LongCombiner.STRING_ENCODER.decode(entry.getKey().getColumnQualifierData().getBackingArray());
          } else
            deg = LongCombiner.STRING_ENCODER.decode(entry.getValue().get());
          if (deg < minDegree || deg > maxDegree)
            bad = true;
        } catch (NumberFormatException | ValueFormatException e) {
          log.warn("Trouble parsing degree entry as long; assuming bad degree: " + entry.getKey() + " -> " + entry.getValue(), e);
          bad = true;
        }

        if (!addToTexts && bad) {
          boolean remove = texts.remove(entry.getKey().getRow(badRow));
          if (!remove)
            log.warn("Unrecognized entry with bad degree; cannot remove: " + entry.getKey() + " -> " + entry.getValue());
        } else if (addToTexts) {
          texts.add(entry.getKey().getRow()); // need new Text object
        }
      }
    } finally {
      bs.close();
    }
    return texts;
  }

  /**
   * Out-degree-filtered Breadth First Search on Incidence table.
   * Conceptually k iterations of: v0 ==startPrefixes==> edge ==endPrefixes==> v1.
   * Works for multi-edge search.
   *
   * @param Etable        Incidence table; rows are edges, column qualifiers are nodes.
   * @param v0            Starting vertices, like "v0,v5,v6,".
   *                      v0 may be a range of nodes like "c,:,e,g,k,:,".
   * @param k             Number of steps.
   * @param Rtable        Name of table to store result. Null means don't store the result.
   * @param RTtable       Name of table to store transpose of result. Null means don't store the transpose.
   * @param startPrefixes   D4M String of Prefixes of edge 'starts' including separator, e.g. 'outA|,outB|,'.
   *                        The "empty prefix" takes the form of ','. Required; if null or empty, assumes the empty prefix.
   * @param endPrefixes     D4M String of Prefixes of edge 'ends' including separator, e.g. 'inA|,inB|,'.
   *                        The "empty prefix" takes the form of ','. Required; if null or empty, assumes the empty prefix.
   *                        Important for multi-edge: None of the end prefixes may be a prefix of another end prefix.
   * @param ETDegtable    Name of table holding out-degrees for ET. Must be provided if degree filtering is used.
   * @param degColumn   Name of column for out-degrees in ETDegtable like "deg". Null means the empty column "".
   *                    If degInColQ==true, this is the prefix before the numeric portion of the column like "deg|", and null means no prefix.
   *                    Unused if ETDegtable is null.
   * @param degInColQ   True means degree is in the Column Qualifier. False means degree is in the Value. Unused if ETDegtable is null.
   * @param minDegree     Minimum out-degree. Checked before doing any searching, at every step, from ADegtable. Pass 0 for no filtering.
   * @param maxDegree     Maximum out-degree. Checked before doing any searching, at every step, from ADegtable. Pass Integer.MAX_VALUE for no filtering.
   * @param plusOp      An SKVI to apply to the result table that "sums" values. Not applied if null.
   * @param EScanIteratorPriority Priority to use for Table Multiplication scan-time iterator on table E
   * @param trace       Enable server-side performance tracing.
   * @return              The nodes reachable in exactly k steps from v0.
   */
  public String  EdgeBFS(String Etable, String v0, int k, String Rtable, String RTtable,
                         String startPrefixes, String endPrefixes,
                         String ETDegtable, String degColumn, boolean degInColQ, int minDegree, int maxDegree,
                         IteratorSetting plusOp, int EScanIteratorPriority, boolean trace) {
    boolean needDegreeFiltering = minDegree > 0 || maxDegree < Integer.MAX_VALUE;
    if (Etable == null || Etable.isEmpty())
      throw new IllegalArgumentException("Please specify Incidence table. Given: " + Etable);
    if (needDegreeFiltering && (ETDegtable == null || ETDegtable.isEmpty()))
      throw new IllegalArgumentException("Need degree table for EdgeBFS. Given: "+ETDegtable);
    if (Rtable != null && Rtable.isEmpty())
      Rtable = null;
    if (RTtable != null && RTtable.isEmpty())
      RTtable = null;
    if (minDegree < 1)
      minDegree = 1;
    if (maxDegree < minDegree)
      throw new IllegalArgumentException("maxDegree=" + maxDegree + " should be >= minDegree=" + minDegree);
    if (EScanIteratorPriority <= 0)
      EScanIteratorPriority = 5; // default priority

    if (degColumn == null)
      degColumn = "";
    Text degColumnText = new Text(degColumn);
    if (plusOp != null && plusOp.getPriority() >= 20)
      log.warn("Sum iterator setting is >=20. Are you sure you want the priority after the default Versioning iterator priority? " + plusOp);
    if (v0 == null || v0.isEmpty())
      v0 = ":\t";
    Collection<Text> vktexts = new HashSet<>();
    char sep = v0.charAt(v0.length() - 1);

    if (startPrefixes == null || startPrefixes.isEmpty())
      startPrefixes = Character.toString(sep);
    if (endPrefixes == null || endPrefixes.isEmpty())
      endPrefixes = Character.toString(sep);
    // error if any endPrefix is a prefix of another
    {
      String[] eps = GraphuloUtil.splitD4mString(endPrefixes);
      for (int i = 0; i < eps.length-1; i++) {
        for (int j = i+1; j < eps.length; j++)
          Preconditions.checkArgument(!eps[i].startsWith(eps[j]) && !eps[j].startsWith(eps[i]),
              "No end prefix should can be a prefix of another. Reason is that these endPrefixes conflict; " +
                  "no way to tell what node comes after the shorter end prefix. Two conflicting end prefixes: %s %s",
              eps[i], eps[j]);
      }
    }


    TableOperations tops = connector.tableOperations();
    if (!tops.exists(Etable))
      throw new IllegalArgumentException("Table E does not exist. Given: " + Etable);
    if (Rtable != null && !tops.exists(Rtable))
      try {
        tops.create(Rtable);
      } catch (AccumuloException | AccumuloSecurityException e) {
        log.error("error trying to create R table " + Rtable, e);
        throw new RuntimeException(e);
      } catch (TableExistsException e) {
        log.error("crazy", e);
        throw new RuntimeException(e);
      }
    if (RTtable != null && !tops.exists(RTtable))
      try {
        tops.create(RTtable);
      } catch (AccumuloException | AccumuloSecurityException e) {
        log.error("error trying to create R table transpose " + RTtable, e);
        throw new RuntimeException(e);
      } catch (TableExistsException e) {
        log.error("crazy", e);
        throw new RuntimeException(e);
      }

    Map<String, String> opt = Rtable != null || RTtable != null ? basicRemoteOpts("C.", Rtable, RTtable) : new HashMap<String,String>();
//    opt.put("trace", String.valueOf(trace)); // logs timing on server
//    opt.put("gatherColQs", "true");  No gathering right now.  Need to implement more general gathering function on RemoteWriteIterator.
    opt.put("dotmode", TwoTableIterator.DOTMODE.ROW.name());
    opt.put("multiplyOp", EdgeBFSMultiply.class.getName());
//    opt.put("AT.zookeeperHost", zookeepers);
//    opt.put("AT.instanceName", instance);
    opt.put("AT.tableName", TwoTableIterator.CLONESOURCE_TABLENAME);
//    opt.put("AT.username", user);
//    opt.put("AT.password", new String(password.getPassword()));


    if (Rtable != null || RTtable != null) {
      opt.put("C.reducer", EdgeBFSReducer.class.getName());
      opt.put("C.reducer.opt."+EdgeBFSReducer.IN_COLUMN_PREFIX, endPrefixes); // MULTI change EdgeBFSReducer for multiple endPrefixes
//      opt.put("C.numEntriesCheckpoint", String.valueOf(numEntriesCheckpoint));

      if (Rtable != null && plusOp != null)
        GraphuloUtil.applyIteratorSoft(plusOp, tops, Rtable);
      if (RTtable != null && plusOp != null)
        GraphuloUtil.applyIteratorSoft(plusOp, tops, RTtable);
    }

    BatchScanner bs;
    try {
      bs = connector.createBatchScanner(Etable, Authorizations.EMPTY, 2); // TODO P2: set number of batch scan threads
    } catch (TableNotFoundException e) {
      log.error("crazy", e);
      throw new RuntimeException(e);
    }

    String colFilterB = prependStartPrefix(endPrefixes, null); // MULTI
    log.debug("fetchColumn "+colFilterB);
//    bs.fetchColumn(EMPTY_TEXT, new Text(GraphuloUtil.prependStartPrefix(endPrefixes, v0, null)));

    try {
      long degTime = 0, scanTime = 0;
      for (int thisk = 1; thisk <= k; thisk++) {
        if (trace)
          if (thisk == 1)
            System.out.println("First step: v0 is " + v0);
          else
            System.out.println("k=" + thisk + " before filter" +
                (vktexts.size() > 5 ? " #=" + String.valueOf(vktexts.size()) : ": " + vktexts.toString()));

        if (needDegreeFiltering) { // use degree table
          long t1 = System.currentTimeMillis(), dur;
          vktexts = thisk == 1
              ? filterTextsDegreeTable(ETDegtable, degColumnText, degInColQ, minDegree, maxDegree, vktexts, GraphuloUtil.d4mRowToRanges(v0))
              : filterTextsDegreeTable(ETDegtable, degColumnText, degInColQ, minDegree, maxDegree, vktexts);
          dur = System.currentTimeMillis() - t1;
          degTime += dur;
          if (trace) {
            System.out.println("Degree Lookup Time: " + dur + " ms");
            System.out.println("k=" + thisk + " after  filter" +
                (vktexts.size() > 5 ? " #=" + String.valueOf(vktexts.size()) : ": " + vktexts.toString()));
          }

          if (vktexts.isEmpty())
            break;
          opt.put("AT.colFilter", prependStartPrefix(startPrefixes, vktexts)); // MULTI

        } else {  // no filtering
          if (thisk == 1) {
            opt.put("AT.colFilter", GraphuloUtil.padD4mString(startPrefixes, ",", v0));
          } else
            opt.put("AT.colFilter", prependStartPrefix(startPrefixes, vktexts));
        }
        log.debug("AT.colFilter: " + opt.get("AT.colFilter"));

        bs.setRanges(Collections.singleton(new Range()));
        bs.clearScanIterators();
        bs.clearColumns();
//        GraphuloUtil.applyGeneralColumnFilter(colFilterB, bs, 4, false);
        opt.put("B.colFilter", colFilterB);
        IteratorSetting itset = new IteratorSetting(EScanIteratorPriority, TableMultIterator.class, opt);
        bs.addScanIterator(itset);

        EdgeBFSReducer reducer = new EdgeBFSReducer();
        reducer.init(Collections.singletonMap(EdgeBFSReducer.IN_COLUMN_PREFIX, endPrefixes), null);
        long t2 = System.currentTimeMillis();
        for (Map.Entry<Key, Value> entry : bs) {
//        System.out.println("A Entry: "+entry.getKey() + " -> " + entry.getValue());
          RemoteWriteIterator.decodeValue(entry.getValue(), reducer);
        }
        long dur = System.currentTimeMillis() - t2;
        scanTime += dur;

        vktexts.clear();
        for (String uk : reducer.getForClient()) {
          vktexts.add(new Text(uk));
        }
        if (trace)
          System.out.println("BatchScan/Iterator Time: " + dur + " ms");
        if (vktexts.isEmpty())
          break;
      }

      if (trace) {
        System.out.println("Total Degree Lookup Time: " + degTime + " ms");
        System.out.println("Total BatchScan/Iterator Time: " + scanTime + " ms");
      }
    } finally {
      bs.close();
    }
    return GraphuloUtil.textsToD4mString(vktexts, sep);

  }


  /**
   * Usage with Matlab D4M:
   * <pre>
   * desiredNumTablets = ...;
   * numEntries = nnz(T);
   * G = DBaddJavaOps('edu.mit.ll.graphulo.MatlabGraphulo','instance','localhost:2181','root','secret');
   * splitPoints = G.findEvenSplits(getName(T), desiredNumTablets-1, numEntries / desiredNumTablets);
   * putSplits(T, splitPoints);
   * % Verify:
   * [splits,entriesPerSplit] = getSplits(T);
   * </pre>
   *
   * @param numSplitPoints      # of desired tablets = numSplitPoints+1
   * @param numEntriesPerTablet desired #entries per tablet = (total #entries in table) / (#desired tablets)
   * @return String with the split points with a newline separator, e.g. "ca\nf\nq\n"
   */
  public String findEvenSplits(String table, int numSplitPoints, int numEntriesPerTablet) {
    if (numSplitPoints < 0)
      throw new IllegalArgumentException("numSplitPoints: " + numSplitPoints);
    if (numSplitPoints == 0)
      return "";

    Scanner scan;
    try {
      scan = connector.createScanner(table, Authorizations.EMPTY);
    } catch (TableNotFoundException e) {
      log.error("Table does not exist: " + table, e);
      throw new RuntimeException(e);
    }
    char sep = '\n';
    StringBuilder sb = new StringBuilder();
    Iterator<Map.Entry<Key, Value>> iterator = scan.iterator();
    for (int sp = 0; sp < numSplitPoints; sp++) {
      for (int entnum = 0; entnum < numEntriesPerTablet - 1; entnum++) {
        if (!iterator.hasNext())
          throw new RuntimeException("not enough entries in table to split into " + (numSplitPoints + 1) + " tablets. Stopped after " + sp + " split points and " + entnum + " entries in the last split point");
        iterator.next();
      }
      if (!iterator.hasNext())
        throw new RuntimeException("not enough entries in table to split into " + (numSplitPoints + 1) + " tablets. Stopped after " + sp + " split points and " + (numEntriesPerTablet - 1) + " entries in the last split point");
      sb.append(iterator.next().getKey().getRow().toString())
          .append(sep);
    }
    scan.close();
    return sb.toString();
  }


  /** Essentially does same thing as {@link GraphuloUtil#padD4mString}.
   * When vktexts is null, similar to {@link GraphuloUtil#singletonsAsPrefix}.
   */
  static String prependStartPrefix(String prefixes, Collection<Text> vktexts) {
    if (prefixes == null || prefixes.isEmpty() ||
        (GraphuloUtil.d4mStringContainsEmptyString(prefixes) && vktexts == null))
      return prependStartPrefix_Single("", GraphuloUtil.DEFAULT_SEP_D4M_STRING, vktexts);
    char sep = prefixes.charAt(prefixes.length()-1);
    String s = "";
    for (String prefix : GraphuloUtil.splitD4mString(prefixes)) {
      s += prependStartPrefix_Single(prefix, sep, vktexts);
    }
    return s;
  }

  /**
   * Todo? Replace with {@link Range#prefix} or {@link Range#followingPrefix(Text)}.
   * May break with unicode.
   * @param prefix e.g. "out|"
   * @param vktexts Set of nodes like "v1,v3,v0,"
   * @return "out|v1,out|v3,out|v0," or "out|,:,out}," if vktexts is null or empty
   */
  static String prependStartPrefix_Single(String prefix, char sep, Collection<Text> vktexts) {
    if (prefix == null)
      prefix = "";
    if (vktexts == null || vktexts.isEmpty()) {
      if (prefix.isEmpty())
        return ":"+sep;
//      byte[] orig = prefix.getBytes();
//      byte[] newb = new byte[orig.length*2+4];
//      System.arraycopy(orig,0,newb,0,orig.length);
//      newb[orig.length] = (byte)sep;
//      newb[orig.length+1] = ':';
//      newb[orig.length+2] = (byte)sep;
//      System.arraycopy(orig,0,newb,orig.length+3,orig.length-1);
//      newb[orig.length*2+2] = (byte) (orig[orig.length-1]+1);
//      newb[orig.length*2+3] = (byte)sep;
//      return new String(newb);
      Text pt = new Text(prefix);
      Text after = Range.followingPrefix(pt);
      return prefix + sep + ':' + sep + after.toString() + sep;
    } else {
      StringBuilder ret = new StringBuilder();
      for (Text vktext : vktexts) {
        ret.append(prefix).append(vktext.toString()).append(sep);
      }
      return ret.toString();
    }
  }


  /**
   * Single-table Breadth First Search. Sums entries into Rtable from each step of the BFS.
   * Note that this version does not copy the in-degrees into the result table.
   *
   * @param Stable         Name of Accumulo table.
   * @param edgeColumn     Column that edges are stored in.
   * @param edgeSep        Character used to separate edges in the row key, e.g. '|'.
   * @param v0             Starting nodes, like "a,f,b,c,". Null or empty string "" means start from all nodes.
   *                       v0 may be a range of nodes like "c,:,e,g,k,:,".
   * @param k              Number of steps
   * @param Rtable         Name of table to store result. Null means don't store the result.
   * @param SDegtable      Name of table holding out-degrees for S. This should almost always be the same as Stable.
   *                       Can set to null only if no filtering is necessary, e.g.,
   *                       if minDegree=0 and maxDegree=Integer.MAX_VALUE.
   * @param degColumn      Name of column for out-degrees in SDegtable like "out". Null means the empty column "".
   *                       If degInColQ==true, this is the prefix before the numeric portion of the column
   *                       like "out|", and null means no prefix. Unused if ADegtable is null.
   * @param degInColQ      True means degree is in the Column Qualifier. False means degree is in the Value.
   *                       Unused if SDegtable is null.
   * @param copyOutDegrees True means copy out-degrees from Stable to Rtable. This must be false if SDegtable is different from Stable. (could remove restriction in future)
   * @param computeInDegrees True means compute the in-degrees of nodes reached in Rtable that are not starting nodes for any BFS step.
   *                         Makes little sense to set this to true if copyOutDegrees is false. Invovles an extra scan at the client.
   * @param minDegree      Minimum out-degree. Checked before doing any searching, at every step, from SDegtable. Pass 0 for no filtering.
   * @param maxDegree      Maximum out-degree. Checked before doing any searching, at every step, from SDegtable. Pass Integer.MAX_VALUE for no filtering.
   * @param plusOp         An SKVI to apply to the result table that "sums" values. Not applied if null.
   *                       Be careful: this affects degrees in the result table as well as normal entries.
   * @param outputUnion    Whether to output nodes reachable in EXACTLY or UP TO k BFS steps.
   * @param trace          Enable server-side performance tracing.
   * @return  The nodes reachable in EXACTLY k steps from v0.
   *          If outputUnion is true, then returns the nodes reachable in UP TO k steps from v0.
   */
  @SuppressWarnings("unchecked")
  public String SingleBFS(String Stable, String edgeColumn, char edgeSep,
                          String v0, int k, String Rtable,
                          String SDegtable, String degColumn, boolean degInColQ,
                          boolean copyOutDegrees, boolean computeInDegrees, int minDegree, int maxDegree,
                          IteratorSetting plusOp, boolean outputUnion, boolean trace) {
    boolean needDegreeFiltering = minDegree > 0 || maxDegree < Integer.MAX_VALUE;
    if (Stable == null || Stable.isEmpty())
      throw new IllegalArgumentException("Please specify Single-table. Given: " + Stable);
    if (needDegreeFiltering && (SDegtable == null || SDegtable.isEmpty()))
      throw new IllegalArgumentException("Please specify SDegtable since filtering is required. Given: " + Stable);
    if (Rtable != null && Rtable.isEmpty())
      Rtable = null;
    if (edgeColumn == null)
      edgeColumn = "";
    Text edgeColumnText = new Text(edgeColumn);
    if (copyOutDegrees && !SDegtable.equals(Stable))
      throw new IllegalArgumentException("Stable and SDegtable must be the same when copying out-degrees. Stable: " + Stable + " SDegtable: " + SDegtable);
    if (minDegree < 1)
      minDegree = 1;
    if (maxDegree < minDegree)
      throw new IllegalArgumentException("maxDegree=" + maxDegree + " should be >= minDegree=" + minDegree);
    String edgeSepStr = String.valueOf(edgeSep);

    if (degColumn == null)
      degColumn = "";
    Text degColumnText = new Text(degColumn);

    if (plusOp != null && plusOp.getPriority() >= 20)
      log.warn("Sum iterator setting is >=20. Are you sure you want the priority after the default Versioning iterator priority? " + plusOp);
    if (v0 == null || v0.isEmpty())
      v0 = ":\t";
    Collection<Text> vktexts = new HashSet<>(); //v0 == null ? null : GraphuloUtil.d4mRowToTexts(v0);
    /** If no degree filtering and v0 is a range, then does not include v0 nodes. */
    Collection<Text> mostAllOutNodes = null;
    Collection<Text> allInNodes = null;
    if (computeInDegrees || outputUnion)
      allInNodes = new HashSet<>();
    if (computeInDegrees)
      mostAllOutNodes = new HashSet<>();
    char sep = v0.charAt(v0.length() - 1);

    TableOperations tops = connector.tableOperations();
    if (!tops.exists(Stable))
      throw new IllegalArgumentException("Table A does not exist. Given: " + Stable);
    if (Rtable != null && !tops.exists(Rtable))
      try {
        tops.create(Rtable);
      } catch (AccumuloException | AccumuloSecurityException e) {
        log.error("error trying to create R table " + Rtable, e);
        throw new RuntimeException(e);
      } catch (TableExistsException e) {
        log.error("crazy", e);
        throw new RuntimeException(e);
      }

    Map<String, String>
        opt = Rtable != null ? basicRemoteOpts("", Rtable) : new HashMap<String,String>(),
        optSTI = new HashMap<>();
    optSTI.put(SingleTransposeIterator.EDGESEP, edgeSepStr);
    optSTI.put(SingleTransposeIterator.NEG_ONE_IN_DEG, Boolean.toString(false)); // not a good option
    optSTI.put(SingleTransposeIterator.DEGCOL, degColumn);
//    opt.put("trace", String.valueOf(trace)); // logs timing on server
    opt.put("reducer", SingleBFSReducer.class.getName());
    opt.put("reducer.opt." + SingleBFSReducer.EDGE_SEP, edgeSepStr);
    if (Rtable != null) {
      if (/*Rtable != null &&*/ plusOp != null)
        GraphuloUtil.applyIteratorSoft(plusOp, tops, Rtable);
    }

    BatchScanner bs;
    try {
      bs = connector.createBatchScanner(Stable, Authorizations.EMPTY, 50); // TODO P2: set number of batch scan threads
    } catch (TableNotFoundException e) {
      log.error("crazy", e);
      throw new RuntimeException(e);
    }
    bs.setRanges(Collections.singleton(new Range()));

    bs.fetchColumn(EMPTY_TEXT, edgeColumnText);
    if (copyOutDegrees)
      bs.fetchColumn(EMPTY_TEXT, degColumnText);

    try {
      long degTime = 0, scanTime = 0;
      for (int thisk = 1; thisk <= k; thisk++) {
        if (trace)
          if (thisk == 1)
            System.out.println("First step: v0 is " + v0);
          else
            System.out.println("k=" + thisk + " before filter" +
                (vktexts.size() > 5 ? " #=" + String.valueOf(vktexts.size()) : ": " + vktexts.toString()));

        if (needDegreeFiltering /*&& SDegtable != null*/) { // use degree table
          long t1 = System.currentTimeMillis(), dur;
          vktexts = thisk == 1
              ? filterTextsDegreeTable(SDegtable, degColumnText, degInColQ, minDegree, maxDegree, vktexts, GraphuloUtil.d4mRowToRanges(v0))
              : filterTextsDegreeTable(SDegtable, degColumnText, degInColQ, minDegree, maxDegree, vktexts);
          dur = System.currentTimeMillis() - t1;
          degTime += dur;
          if (trace) {
            System.out.println("Degree Lookup Time: " + dur + " ms");
            System.out.println("k=" + thisk + " after  filter" +
                (vktexts.size() > 5 ? " #=" + String.valueOf(vktexts.size()) : ": " + vktexts.toString()));
          }

          if (vktexts.isEmpty())
            break;
          if (mostAllOutNodes != null)
            mostAllOutNodes.addAll(vktexts);
          opt.put("rowRanges", GraphuloUtil.singletonsAsPrefix(vktexts, sep));
          optSTI.put(SingleTransposeIterator.STARTNODES, GraphuloUtil.textsToD4mString(vktexts, sep));

        } else {  // no filtering
          if (thisk == 1) {
            opt.put("rowRanges", GraphuloUtil.singletonsAsPrefix(v0));
            optSTI.put(SingleTransposeIterator.STARTNODES, v0);
          } else {
            if (mostAllOutNodes != null)
              mostAllOutNodes.addAll(vktexts);
            opt.put("rowRanges", GraphuloUtil.singletonsAsPrefix(vktexts, sep));
            optSTI.put(SingleTransposeIterator.STARTNODES, GraphuloUtil.textsToD4mString(vktexts, sep));
          }
        }

        bs.clearScanIterators();
        DynamicIteratorSetting dis = new DynamicIteratorSetting();
        dis.append(new IteratorSetting(3, SingleTransposeIterator.class, optSTI));
        dis.append(new IteratorSetting(4, RemoteWriteIterator.class, opt));
        bs.addScanIterator(dis.toIteratorSetting(3));


        SingleBFSReducer reducer = new SingleBFSReducer();
        reducer.init(Collections.singletonMap(SingleBFSReducer.EDGE_SEP, edgeSepStr), null);
        long t2 = System.currentTimeMillis();
        for (Map.Entry<Key, Value> entry : bs) {
//        System.out.println("A Entry: "+entry.getKey() + " -> " + entry.getValue());
          RemoteWriteIterator.decodeValue(entry.getValue(), reducer);
        }
        long dur = System.currentTimeMillis() - t2;
        scanTime += dur;

        vktexts.clear();
//      vktexts.addAll(reducer.getForClient());
        for (String uk : reducer.getForClient()) {
          vktexts.add(new Text(uk));
        }
        if (trace)
          System.out.println("BatchScan/Iterator Time: " + dur + " ms");
        if (vktexts.isEmpty())
          break;
        if (allInNodes != null)
          allInNodes.addAll(vktexts);
      }

      if (trace) {
        System.out.println("Total Degree Lookup Time: " + degTime + " ms");
        System.out.println("Total BatchScan/Iterator Time: " + scanTime + " ms");
      }

    } finally {
      bs.close();
    }

    // take union if necessary
    String ret = GraphuloUtil.textsToD4mString(outputUnion ? allInNodes : vktexts, sep);

    // compute in degrees if necessary
    if (computeInDegrees) {
      allInNodes.removeAll(mostAllOutNodes);
//      log.debug("allInNodes: "+allInNodes);
      singleCheckWriteDegrees(allInNodes, Stable, Rtable, degColumn.getBytes(), edgeSepStr, sep);
    }

    return ret;
  }

  private void singleCheckWriteDegrees(Collection<Text> questionNodes, String Stable, String Rtable,
                                       byte[] degColumn, String edgeSepStr, char sep) {
    Scanner scan;
    BatchWriter bw;
    try {
      scan = connector.createScanner(Rtable, Authorizations.EMPTY);
      bw = connector.createBatchWriter(Rtable, new BatchWriterConfig());
    } catch (TableNotFoundException e) {
      log.error("crazy", e);
      throw new RuntimeException(e);
    }

    //GraphuloUtil.singletonsAsPrefix(questionNodes, sep);
    // There is a more efficient way to do this:
    //  scan all the ranges at once with a BatchScanner, somehow preserving the order
    //  such that all the v1, v1|v4, v1|v6, ... appear in order.
    // This is likely not a bottleneck.
    List<Range> rangeList;
    {
      Collection<Range> rs = new TreeSet<>();
      for (Text node : questionNodes) {
        rs.add(Range.prefix(node));
      }
      rangeList = Range.mergeOverlapping(rs);
    }

    try {
      Text row = new Text();
      RANGELOOP: for (Range range : rangeList) {
//        log.debug("range: "+range);
        boolean first = true;
        int cnt = 0, pos = -1;
        scan.setRange(range);
        for (Map.Entry<Key, Value> entry : scan) {
//          log.debug(entry.getKey()+" -> "+entry.getValue());
          if (first) {
            pos = entry.getKey().getRow(row).find(edgeSepStr);
            if (pos == -1)
              continue RANGELOOP;  // this is a degree node; no need to re-write degree
            first = false;
          }
          // assume all other rows in this range are degree rows.
          cnt++;
        }
        // row contains "v1|v2" -- want v1. pos is the byte position of the '|'. cnt is the degree.
        Mutation m = new Mutation(row.getBytes(), 0, pos);
        m.put(GraphuloUtil.EMPTY_BYTES, degColumn, String.valueOf(cnt).getBytes());
        bw.addMutation(m);
      }


    } catch (MutationsRejectedException e) {
      log.error("canceling in-degree ingest because in-degree mutations rejected, sending to Rtable "+Rtable, e);
      throw new RuntimeException(e);
    } finally {
      scan.close();
      try {
        bw.close();
      } catch (MutationsRejectedException e) {
        log.error("in-degree mutations rejected, sending to Rtable "+Rtable, e);
      }
    }

  }


  /**
   * @param Atable   Accumulo adjacency table input
   * @param ATtable  Transpose of input adjacency table
   * @param Rtable   Result table, can be null. Created if nonexisting.
   * @param RTtable  Transpose of result table, can be null. Created if nonexisting.
   * @param BScanIteratorPriority Priority to use for Table Multiplication scan-time iterator on table B
   * @param isDirected True for directed input, false for undirected input
   * @param includeExtraCycles (only applies to directed graphs)
*                           True to include cycles between edges that come into the same node
   * @param plusOp    An SKVI to apply to the result table that "sums" values. Not applied if null.
*                  This has no effect if the result table is empty before the operation.
   * @param rowFilter Experimental. Pass null for no filtering.
   * @param colFilterAT Experimental. Pass null for no filtering.
   * @param colFilterB Experimental. Pass null for no filtering.
   * @param numEntriesCheckpoint # of entries before we emit a checkpoint entry from the scan. -1 means no monitoring.
   * @param trace  Enable server-side performance tracing.
   * @param separator The string to insert between the labels of two nodes.
*                  This string should not appear in any node label. Used to find the original nodes.
   */
  public void LineGraph(String Atable, String ATtable, String Rtable, String RTtable,
                        int BScanIteratorPriority, boolean isDirected, boolean includeExtraCycles, IteratorSetting plusOp,
                        Collection<Range> rowFilter, String colFilterAT, String colFilterB,
                        int numEntriesCheckpoint, boolean trace, String separator) {
    Map<String,String> opt = new HashMap<>();
    opt.put("rowMultiplyOp", LineRowMultiply.class.getName());
    opt.put("rowMultiplyOp.opt."+LineRowMultiply.SEPARATOR, separator);
    opt.put("rowMultiplyOp.opt."+LineRowMultiply.ISDIRECTED, Boolean.toString(isDirected));
    opt.put("rowMultiplyOp.opt."+LineRowMultiply.INCLUDE_EXTRA_CYCLES, Boolean.toString(includeExtraCycles));

    TwoTable(ATtable, Atable, Rtable, RTtable, BScanIteratorPriority,
        TwoTableIterator.DOTMODE.ROW, opt, plusOp,
        rowFilter, colFilterAT, colFilterB,
        false, false, Collections.<IteratorSetting>emptyList(),
        Collections.<IteratorSetting>emptyList(), Collections.<IteratorSetting>emptyList(),
        numEntriesCheckpoint, trace);
  }

  String emptyToNull(String s) { return s != null && s.isEmpty() ? null : s; }

  /**
   * Count number of entries in a table using a BatchScanner with {@link CountAllIterator}.
   */
  public long countEntries(String table) {
    Preconditions.checkArgument(table != null && !table.isEmpty());

    BatchScanner bs;
    try {
      bs = connector.createBatchScanner(table, Authorizations.EMPTY, 2); // todo: 2 threads is arbitrary
    } catch (TableNotFoundException e) {
      log.error("table "+table+" does not exist", e);
      throw new RuntimeException(e);
    }

    bs.setRanges(Collections.singleton(new Range()));
    bs.addScanIterator(new IteratorSetting(30, CountAllIterator.class));

    long cnt = 0l;
    try {
      for (Map.Entry<Key, Value> entry : bs) {
        cnt += new Long(new String(entry.getValue().get()));
      }
    } finally {
      bs.close();
    }
    return cnt;
  }


  /**
   * From input <b>unweighted, undirected</b> adjacency table Aorig, put the k-Truss
   * of Aorig in Rfinal.
   * @param Aorig Unweighted, undirected adjacency table.
   * @param Rfinal Does not have to previously exist. Writes the kTruss into Rfinal if it already exists.
   *               Use a combiner if you want to sum it in.
   * @param k Trivial if k <= 2.
   * @param forceDelete False means throws exception if the temporary tables used inside the algorithm already exist.
   *                    True means delete them if they exist.
   * @param trace Server-side tracing.
   * @return nnz of the kTruss subgraph, which is 2* the number of edges in the kTruss subgraph.
   *          Returns -1 if k < 2 since there is no point in counting the number of edges.
   */
  public long kTrussAdj(String Aorig, String Rfinal, int k,
                        boolean forceDelete, boolean trace) {
    Aorig = emptyToNull(Aorig);
    Rfinal = emptyToNull(Rfinal);
    Preconditions.checkArgument(Aorig != null, "Input table must be given: Aorig=%s", Aorig);
    Preconditions.checkArgument(Rfinal != null, "Output table must be given or operation is useless: Rfinal=%s", Rfinal);
    TableOperations tops = connector.tableOperations();
    Preconditions.checkArgument(tops.exists(Aorig), "Input table must exist: Aorig=%s", Aorig);
    boolean RfinalExists = tops.exists(Rfinal);

    try {
      if (k <= 2) {               // trivial case: every graph is a 2-truss
        if (RfinalExists)
          AdjBFS(Aorig, null, 1, Rfinal, null, -1, null, null, false, 0, Integer.MAX_VALUE, null, trace);
        else
          tops.clone(Aorig, Rfinal, true, null, null);    // flushes Aorig before cloning
        return -1;
      }

      // non-trivial case: k is 3 or more.
      String Atmp, A2tmp, AtmpAlt;
      long nnzBefore, nnzAfter;
      String tmpBaseName = Aorig+"_kTrussAdj_";
      Atmp = tmpBaseName+"tmpA";
      A2tmp = tmpBaseName+"tmpA2";
      AtmpAlt = tmpBaseName+"tmpAalt";
      // verify temporary tables do not exist
      if (!forceDelete) {
        Preconditions.checkState(!tops.exists(Atmp), "Temporary table already exists: %s. Set forceDelete=true to delete.", Atmp);
        Preconditions.checkState(!tops.exists(A2tmp), "Temporary table already exists: %s. Set forceDelete=true to delete.", A2tmp);
        Preconditions.checkState(!tops.exists(AtmpAlt), "Temporary table already exists: %s. Set forceDelete=true to delete.", AtmpAlt);
      } else {
        if (tops.exists(Atmp)) tops.delete( Atmp);
        if (tops.exists(A2tmp)) tops.delete(A2tmp);
        if (tops.exists(AtmpAlt)) tops.delete(AtmpAlt);
      }
      tops.clone(Aorig, Atmp, true, null, null);

      // Inital nnz
      // Careful: nnz figure will be inaccurate if there are multiple versions of an entry in Aorig.
      // The truly accurate count is to count them first!
//      D4mDbTableOperations d4mtops = new D4mDbTableOperations(connector.getInstance().getInstanceName(),
//          connector.getInstance().getZooKeepers(), connector.whoami(), new String(password.getPassword()));
//      nnzAfter = d4mtops.getNumberOfEntries(Collections.singletonList(Aorig))
      // Above method dangerous. Instead:
      nnzAfter = countEntries(Aorig);

      // Filter out entries with < k-2
      IteratorSetting kTrussFilter = new IteratorSetting(10, MinMaxValueFilter.class);
      kTrussFilter.addOption(MinMaxValueFilter.MINVALUE, Long.toString(k-2l));

      do {
        nnzBefore = nnzAfter;

        TableMult(Atmp, Atmp, A2tmp, null, -1, AndMultiply.class, DEFAULT_PLUS_ITERATOR,
            null, null, null, false, false,
            Collections.<IteratorSetting>emptyList(), Collections.<IteratorSetting>emptyList(),
            Collections.singletonList(kTrussFilter), -1, trace
        );
        // A2tmp has a SummingCombiner

        nnzAfter = SpEWiseX(A2tmp, Atmp, AtmpAlt, null, -1, AndEWiseX.class, null,
            null, null, null, -1, trace);

        tops.delete(Atmp);
        tops.delete(A2tmp);
        { String t = Atmp; Atmp = AtmpAlt; AtmpAlt = t; }

        log.debug("nnzBefore "+nnzBefore+" nnzAfter "+nnzAfter);
      } while (nnzBefore != nnzAfter);
      // Atmp, ATtmp have the result table. Could be empty.

      if (RfinalExists)  // sum whole graph into existing graph
        AdjBFS(Atmp, null, 1, Rfinal, null, -1, null, null, false, 0, Integer.MAX_VALUE, null, trace);
      else                                           // result is new;
        tops.clone(Atmp, Rfinal, true, null, null);  // flushes Atmp before cloning

      return nnzAfter;

    } catch (AccumuloException | AccumuloSecurityException | TableExistsException | TableNotFoundException e) {
      log.error("Exception in kTrussAdj", e);
      throw new RuntimeException(e);
    }
  }


  /**
   * From input <b>unweighted, undirected</b> incidence table Eorig, put the k-Truss
   * of Eorig in Rfinal.  Needs transpose ETorig, and can output transpose of k-Truss subgraph too.
   * @param Eorig Unweighted, undirected incidence table.
   * @param ETorig Transpose of input incidence table.
   *               Please use <tt>AdjBFS(Eorig, null, 1, null, ETorig, null, null, false, 0, Integer.MAX_VALUE, null, trace)</tt>
   *               if you need to create it.
   * @param Rfinal Does not have to previously exist. Writes the kTruss into Rfinal if it already exists.
   *               Use a combiner if you want to sum it in.
   * @param RTfinal Does not have to previously exist. Writes in the transpose of the kTruss subgraph.
   * @param k Trivial if k <= 2.
   * @param forceDelete False means throws exception if the temporary tables used inside the algorithm already exist.
   *                    True means delete them if they exist.
   * @param trace Server-side tracing.
   * @return  nnz of the kTruss subgraph, which is 2* the number of edges in the kTruss subgraph.
   *          Returns -1 if k < 2 since there is no point in counting the number of edges.
   */
  public long kTrussEdge(String Eorig, String ETorig, String Rfinal, String RTfinal, int k,
                         // iterator priority?
                         boolean forceDelete, boolean trace) {
    // small optimization possible: pass in Aorig = ET*E if present. Saves first iteration matrix multiply. Not really worth it.
    Eorig = emptyToNull(Eorig);
    ETorig = emptyToNull(ETorig);
    Rfinal = emptyToNull(Rfinal);
    Preconditions.checkArgument(Eorig != null, "Input table must be given: Eorig=%s", Eorig);
    Preconditions.checkArgument(ETorig != null, "Input table must be given: ETorig=%s", ETorig);
    Preconditions.checkArgument(Rfinal != null || RTfinal != null, "One Output table must be given or operation is useless: Rfinal=%s; RTfinal=%s", Rfinal, RTfinal);
    TableOperations tops = connector.tableOperations();
    Preconditions.checkArgument(tops.exists(Eorig), "Input table must exist: Eorig=%s", Eorig);
    Preconditions.checkArgument(tops.exists(ETorig), "Input table must exist: ETorig=%s", ETorig);
    boolean RfinalExists = Rfinal != null && tops.exists(Rfinal),
        RTfinalExists = RTfinal != null && tops.exists(RTfinal);

    try {
      if (k <= 2) {               // trivial case: every graph is a 2-truss
        if (RfinalExists && RTfinalExists) { // sum whole graph into existing graph
          // AdjBFS works just as well as EdgeBFS because we're not doing any filtering.
          AdjBFS(Eorig, null, 1, Rfinal, RTfinal, -1, null, null, false, 0, Integer.MAX_VALUE, null, trace);
        } else if (RfinalExists) {
          AdjBFS(Eorig, null, 1, Rfinal, null, -1, null, null, false, 0, Integer.MAX_VALUE, null, trace);
          if (RTfinal != null)
            tops.clone(ETorig, RTfinal, true, null, null);
        } else if (RTfinalExists) {
          AdjBFS(Eorig, null, 1, null, RTfinal, -1, null, null, false, 0, Integer.MAX_VALUE, null, trace);
          if (Rfinal != null)
            tops.clone(Eorig, Rfinal, true, null, null);
        } else {                                          // both graphs are new;
          if (Rfinal != null)
            tops.clone(Eorig, Rfinal, true, null, null);  // flushes Eorig before cloning
          if (RTfinal != null)
            tops.clone(ETorig, RTfinal, true, null, null);
        }
        return -1;
      }

      // non-trivial case: k is 3 or more.
      String Etmp, ETtmp, Rtmp, Atmp, EtmpAlt, ETtmpAlt;
      long nnzBefore, nnzAfter;
      String tmpBaseName = Eorig+"_kTrussEdge_";
      Etmp = tmpBaseName+"tmpE";
      ETtmp = tmpBaseName+"tmpET";
      Atmp = tmpBaseName+"tmpA";
      Rtmp = tmpBaseName+"tmpR";
      EtmpAlt = tmpBaseName+"tmpEalt";
      ETtmpAlt = tmpBaseName+"tmpETalt";
      // verify temporary tables do not exist
      if (!forceDelete) {
        Preconditions.checkState(!tops.exists(Etmp), "Temporary table already exists: %s. Set forceDelete=true to delete.", Etmp);
        Preconditions.checkState(!tops.exists(ETtmp), "Temporary table already exists: %s. Set forceDelete=true to delete.", ETtmp);
        Preconditions.checkState(!tops.exists(Atmp), "Temporary table already exists: %s. Set forceDelete=true to delete.", Atmp);
        Preconditions.checkState(!tops.exists(Rtmp), "Temporary table already exists: %s. Set forceDelete=true to delete.", Rtmp);
        Preconditions.checkState(!tops.exists(EtmpAlt), "Temporary table already exists: %s. Set forceDelete=true to delete.", EtmpAlt);
        Preconditions.checkState(!tops.exists(ETtmpAlt), "Temporary table already exists: %s. Set forceDelete=true to delete.", ETtmpAlt);
      } else {
        if (tops.exists(Etmp)) tops.delete( Etmp);
        if (tops.exists(ETtmp)) tops.delete( ETtmp);
        if (tops.exists(Atmp)) tops.delete(Atmp);
        if (tops.exists(Rtmp)) tops.delete(Rtmp);
        if (tops.exists(EtmpAlt)) tops.delete(EtmpAlt);
        if (tops.exists(ETtmpAlt)) tops.delete(ETtmpAlt);
      }
      tops.clone(Eorig, Etmp, true, null, null);
      tops.clone(ETorig, ETtmp, true, null, null);

      // Inital nnz
      // Careful: nnz figure will be inaccurate if there are multiple versions of an entry in Aorig.
      // The truly accurate count is to count them first!
//      D4mDbTableOperations d4mtops = new D4mDbTableOperations(connector.getInstance().getInstanceName(),
//          connector.getInstance().getZooKeepers(), connector.whoami(), new String(password.getPassword()));
//      nnzAfter = d4mtops.getNumberOfEntries(Collections.singletonList(Eorig))
      // Above method dangerous. Instead:
      nnzAfter = countEntries(Eorig);

      // Filter out entries with < k-2
      IteratorSetting kTrussFilter = new IteratorSetting(10, MinMaxValueFilter.class);
      kTrussFilter.addOption(MinMaxValueFilter.MINVALUE, Long.toString(k-2l));

      // No Diagonal Filter
      IteratorSetting noDiagFilter = new IteratorSetting(10, TriangularFilter.class);
      noDiagFilter.addOption(TriangularFilter.TRIANGULAR_TYPE, TriangularFilter.TriangularType.NoDiagonal.name());

      // R -> sum -> kTrussFilter -> TT_RowSelector
      List<IteratorSetting> iteratorsBeforeA = new ArrayList<>();
      {
        IteratorSetting minMaxSetting = new IteratorSetting(1, MinMaxValueFilter.class);
        minMaxSetting.addOption(MinMaxValueFilter.MAXVALUE, "2");
        minMaxSetting.addOption(MinMaxValueFilter.MINVALUE, "2");
        iteratorsBeforeA.add(minMaxSetting);
      }
      iteratorsBeforeA.add(new IteratorSetting(1, ApplyIterator.class,
          Collections.singletonMap(ApplyIterator.APPLYOP, Abs0Apply.class.getName())));
      iteratorsBeforeA.add(new IteratorSetting(1, ApplyIterator.class,
          Collections.singletonMap(ApplyIterator.APPLYOP, OnlyRowApply.class.getName())));
      iteratorsBeforeA.add(DEFAULT_PLUS_ITERATOR);
      iteratorsBeforeA.add(kTrussFilter);

      do {
        nnzBefore = nnzAfter;

        TableMult(Etmp, Etmp, Atmp, null, -1, AndMultiply.class, DEFAULT_PLUS_ITERATOR, null, null, null, false, false, null, null, Collections.singletonList(noDiagFilter), -1, trace
        );
        // Atmp has a SummingCombiner

        TableMult(ETtmp, Atmp, Rtmp, null, -1, AndMultiply.class, DEFAULT_PLUS_ITERATOR, null, null, null, false, false, null, null, null, -1, trace
        );
        // Rtmp has a SummingCombiner
        tops.delete(ETtmp);
        tops.delete(Atmp);

        // R -> sum -> kTrussFilter -> TT_RowSelector <- E
        //                              \-> Writing to EtmpAlt, ETtmpAlt
        nnzAfter = TwoTableROWSelector(Rtmp, Etmp, EtmpAlt, ETtmpAlt, -1, null, null, null, true,
            iteratorsBeforeA, null, null, -1, trace);
        tops.delete(Etmp);

        { String t = Etmp; Etmp = EtmpAlt; EtmpAlt = t; }
        { String t = ETtmp; ETtmp = ETtmpAlt; ETtmpAlt = t; }

        log.debug("nnzBefore "+nnzBefore+" nnzAfter "+nnzAfter);
      } while (nnzBefore != nnzAfter);
      // Etmp, ETtmp have the result table. Could be empty.

      if (RfinalExists && RTfinalExists) { // sum whole graph into existing graph
        // AdjBFS works just as well as EdgeBFS because we're not doing any filtering.
        AdjBFS(Etmp, null, 1, Rfinal, RTfinal, -1, null, null, false, 0, Integer.MAX_VALUE, null, trace);
      } else if (RfinalExists) {
        AdjBFS(Etmp, null, 1, Rfinal, null, -1, null, null, false, 0, Integer.MAX_VALUE, null, trace);
        if (RTfinal != null)
          tops.clone(ETtmp, RTfinal, true, null, null);
      } else if (RTfinalExists) {
        AdjBFS(Etmp, null, 1, null, RTfinal, -1, null, null, false, 0, Integer.MAX_VALUE, null, trace);
        if (Rfinal != null)
          tops.clone(Etmp, Rfinal, true, null, null);
      } else {                                          // both graphs are new;
        if (Rfinal != null)
          tops.clone(Etmp, Rfinal, true, null, null);  // flushes Etmp before cloning
        if (RTfinal != null)
          tops.clone(ETtmp, RTfinal, true, null, null);
      }

      return nnzAfter;

    } catch (AccumuloException | AccumuloSecurityException | TableExistsException | TableNotFoundException e) {
      log.error("Exception in kTrussAdj", e);
      throw new RuntimeException(e);
    }
  }



  /**
   * From input <b>unweighted, undirected</b> adjacency table Aorig,
   * put the Jaccard coefficients in the upper triangle of Rfinal.
   * @param Aorig Unweighted, undirected adjacency table.
   * @param Rfinal Should not previously exist. Writes the Jaccard table into Rfinal,
   *               using a couple combiner-like iterators.
   * @param trace Server-side tracing.
   * @return nnz of the result Jaccard table
   */
  public long Jaccard(String Aorig, String ADeg, String Rfinal,
                      boolean trace) {
    Aorig = emptyToNull(Aorig);
    ADeg = emptyToNull(ADeg);
    Rfinal = emptyToNull(Rfinal);
    Preconditions.checkArgument(Aorig != null, "Input table must be given: Aorig=%s", Aorig);
    Preconditions.checkArgument(ADeg != null, "Input degree table must be given: ADeg=%s", ADeg);
    Preconditions.checkArgument(Rfinal != null, "Output table must be given or operation is useless: Rfinal=%s", Rfinal);
    TableOperations tops = connector.tableOperations();
    Preconditions.checkArgument(tops.exists(Aorig), "Input table must exist: Aorig=%s", Aorig);
    Preconditions.checkArgument(tops.exists(ADeg), "Input degree table must exist: ADeg=%s", ADeg);
    Preconditions.checkArgument(!tops.exists(Rfinal), "Output Jaccard table must not exist: Rfinal=%s", Rfinal);

    // "Plus" iterator to set on Rfinal
    IteratorSetting RPlusIteratorSetting;
    {
      DynamicIteratorSetting dis = new DynamicIteratorSetting();
      dis.append(DEFAULT_PLUS_ITERATOR);

      Map<String,String> opt = basicRemoteOpts(ApplyIterator.APPLYOP + ApplyIterator.OPT_SUFFIX, ADeg);
      opt.put(ApplyIterator.APPLYOP, JaccardDegreeApply.class.getName());
      IteratorSetting JDegApply = new IteratorSetting(1, ApplyIterator.class, opt);
      dis.append(JDegApply);
      RPlusIteratorSetting = dis.toIteratorSetting(DEFAULT_PLUS_ITERATOR.getPriority());
    }

    List<IteratorSetting> afterTTIterators = new LinkedList<>();
    {
//        DynamicIteratorSetting dis = new DynamicIteratorSetting();
      afterTTIterators.add(new IteratorSetting(1, TriangularFilter.class,
          Collections.singletonMap(TriangularFilter.TRIANGULAR_TYPE,
              TriangularFilter.TriangularType.Upper.name())));
      afterTTIterators.add(new IteratorSetting(1, ApplyIterator.class,
          Collections.singletonMap(ApplyIterator.APPLYOP, ColQSpecialByteApply.class.getName())));
//        afterTTIterators = dis.toIteratorSetting(1);
    }

    long Jnnz = TableMult(Aorig, Aorig, Rfinal, null, -1, LongMultiply.class, RPlusIteratorSetting, null, null, null, true, true, Collections.singletonList(new IteratorSetting(1, TriangularFilter.class,
            Collections.singletonMap(TriangularFilter.TRIANGULAR_TYPE,
                TriangularFilter.TriangularType.Lower.name()))), Collections.singletonList(new IteratorSetting(1, TriangularFilter.class,
                    Collections.singletonMap(TriangularFilter.TRIANGULAR_TYPE,
                        TriangularFilter.TriangularType.Upper.name()))), afterTTIterators, -1, trace
    );

    log.debug("Jaccard nnz "+Jnnz);
    return Jnnz;
  }


  /**
   * Create a degree table from an existing table.
   * @param table Name of original table.
   * @param Degtable Name of degree table. Created if it does not exist.
   *                 Use a combiner if you want to sum in the new degree entries into an existing table.
   * @param countColumns True means degrees are the <b>number of entries in each row</b>.
   *                     False means degrees are the <b>sum or weights of entries in each row</b>.
   * @param trace Server-side tracing.
   * @return The number of rows in the original table.
   */
  public long generateDegreeTable(String table, String Degtable, boolean countColumns, boolean trace) {
    table = emptyToNull(table);
    Degtable = emptyToNull(Degtable);
    Preconditions.checkArgument(table != null, "Input table must be given: table=%s", table);
    Preconditions.checkArgument(Degtable != null, "Output table must be given: Degtable=%s", Degtable);
    TableOperations tops = connector.tableOperations();
    Preconditions.checkArgument(tops.exists(table), "Input table must exist: table=%s", table);
    BatchScanner bs;
    try {
      if (!tops.exists(Degtable))
        tops.create(Degtable);
      bs = connector.createBatchScanner(table, Authorizations.EMPTY, 2); // todo: 2 threads arbitrary
    } catch (TableNotFoundException | TableExistsException e) {
      log.error("crazy", e);
      throw new RuntimeException(e);
    } catch (AccumuloException | AccumuloSecurityException e) {
      log.error("problem creating degree table "+Degtable, e);
      throw new RuntimeException(e);
    }
    bs.setRanges(Collections.singleton(new Range()));

    {
      DynamicIteratorSetting dis = new DynamicIteratorSetting();
      if (countColumns)
          dis.append(new IteratorSetting(1, ApplyIterator.class,
              Collections.singletonMap(ApplyIterator.APPLYOP, Abs0Apply.class.getName())));
      dis
        .append(new IteratorSetting(1, ApplyIterator.class,
            Collections.singletonMap(ApplyIterator.APPLYOP, OnlyRowApply.class.getName())))
        .append(DEFAULT_PLUS_ITERATOR)
        .append(new IteratorSetting(1, RemoteWriteIterator.class, basicRemoteOpts("", Degtable)));
      bs.addScanIterator(dis.toIteratorSetting(DEFAULT_PLUS_ITERATOR.getPriority()));
    }

    long totalRows = 0;
    try {
      for (Map.Entry<Key, Value> entry : bs) {
        totalRows += RemoteWriteIterator.decodeValue(entry.getValue(), null);
      }
    } finally {
      bs.close();
    }

    return totalRows;
  }

  private Map<String,String> basicRemoteOpts(String prefix, String remoteTable) {
    return basicRemoteOpts(prefix, remoteTable, null);
  }

  /**
   * Create the basic iterator settings for the {@link RemoteWriteIterator}.
   * @param prefix A prefix to apply to keys in the option map, e.g., the "B" in "B.tableName".
   * @param remoteTable Name of table to write to. Null does not put in the table name.
   * @param remoteTableTranspose Name of table to write transpose to. Null does not put in the transpose table name.
   * @return The basic set of options for {@link RemoteWriteIterator}.
   */
  private Map<String,String> basicRemoteOpts(String prefix, String remoteTable, String remoteTableTranspose) {
    if (prefix == null) prefix = "";
    Map<String,String> opt = new HashMap<>();
    String instance = connector.getInstance().getInstanceName();
    String zookeepers = connector.getInstance().getZooKeepers();
    String user = connector.whoami();
    opt.put(prefix+"zookeeperHost", zookeepers);
    opt.put(prefix+"instanceName", instance);
    if (remoteTable != null)
      opt.put(prefix+"tableName", remoteTable);
    if (remoteTableTranspose != null)
      opt.put(prefix+"tableNameTranspose", remoteTableTranspose);
    opt.put(prefix+"username", user);
    opt.put(prefix+"password", new String(password.getPassword()));
    return opt;
  }


}
