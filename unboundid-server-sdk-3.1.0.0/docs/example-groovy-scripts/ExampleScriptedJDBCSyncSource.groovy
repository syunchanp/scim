/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * docs/licenses/cddl.txt
 * or http://www.opensource.org/licenses/cddl1.php.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * docs/licenses/cddl.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010-2011 UnboundID Corp.
 */

package com.unboundid.directory.sdk.examples.groovy;

import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.io.Serializable;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.util.args.ArgumentParser;
import com.unboundid.util.args.ArgumentException;

import com.unboundid.directory.sdk.sync.types.SyncServerContext;
import com.unboundid.directory.sdk.sync.types.SetStartpointOptions;
import com.unboundid.directory.sdk.sync.types.SetStartpointOptions.StartpointType;
import com.unboundid.directory.sdk.sync.types.DatabaseChangeRecord;
import com.unboundid.directory.sdk.sync.types.DatabaseChangeRecord.ChangeType;
import com.unboundid.directory.sdk.sync.types.TransactionContext;
import com.unboundid.directory.sdk.sync.types.SyncOperation;
import com.unboundid.directory.sdk.sync.scripting.ScriptedJDBCSyncSource;
import com.unboundid.directory.sdk.sync.config.JDBCSyncSourceConfig;
import com.unboundid.directory.sdk.sync.util.ScriptUtils;
import com.unboundid.directory.sdk.common.types.LogSeverity;


/**
 * This class implements the necessary methods to synchronize data from a
 * simple, single-table database schema to its LDAP counterpart.
 * <p>
 * To use this script, place it under
 *  /lib/groovy-scripted-extensions/com/unboundid/directory/sdk/examples/groovy
 * and set the 'script-class' property on the Sync Source to
 *  "com.unboundid.directory.sdk.examples.groovy.ExampleJDBCSyncSource".
 */
public final class ExampleScriptedJDBCSyncSource extends ScriptedJDBCSyncSource
{
  //The server context which can be used for obtaining the server state, logging, etc.
  private SyncServerContext serverContext;

  //The name of the data table.
  private static final String DATA_TABLE = "DataTable";

  //The name of the changelog table.
  private static final String CHANGELOG_TABLE = "ChangeLog";

  //Used to keep track of which changes have been retrieved.
  private long nextChangeNumberToRetrieve;

  //Used to keep track of which changes have finished processing.
  //NOTE: this is the official "startpoint" for this implementation.
  private long lastCompletedChangeNumber;


  /**
   * Updates the provided argument parser to define any configuration arguments
   * which may be used by this extension.  The argument parser may also be
   * updated to define relationships between arguments (e.g. to specify
   * required, exclusive, or dependent argument sets).
   *
   * @param  parser  The argument parser to be updated with the configuration
   *                 arguments which may be used by this extension.
   *
   * @throws  ArgumentException  If a problem is encountered while updating the
   *                             provided argument parser.
   */
  @Override
  public void defineConfigArguments(final ArgumentParser parser)
         throws ArgumentException
  {
    // No arguments will be allowed by default.
  }


  /**
   * This hook is called when a Sync Pipe first starts up, when the
   * <i>resync</i> process first starts up, or when the set-startpoint
   * subcommand is called from the <i>realtime-sync</i> command line tool.
   * Any initialization of this sync source should be performed here. This
   * method should generally store the {@link SyncServerContext} in a class
   * member so that it can be used elsewhere in the implementation.
   * <p>
   * The default implementation is empty.
   *
   * @param ctx
   *          a TransactionContext which provides a valid JDBC connection to the
   *          database.
   * @param  serverContext  A handle to the server context for the server in
   *                        which this extension is running.
   * @param  config         The general configuration for this sync source.
   * @param  parser         The argument parser which has been initialized from
   *                        the configuration for this JDBC sync source.
   */
  @Override
  public void initializeJDBCSyncSource(final TransactionContext ctx,
                                       final SyncServerContext serverContext,
                                       final JDBCSyncSourceConfig config,
                                       final ArgumentParser parser)
  {
    this.serverContext = serverContext;
  }


  /**
   * This hook is called when a Sync Pipe shuts down, when the <i>resync</i>
   * process shuts down, or when the set-startpoint subcommand (from the
   * <i>realtime-sync</i> command line tool) is finished. Any clean up of this
   * sync source should be performed here.
   *
   * @param ctx
   *          a TransactionContext which provides a valid JDBC connection to the
   *          database.
   */
  @Override
  public void finalizeJDBCSyncSource(final TransactionContext ctx)
  {
    // No cleanup required.
  }


  /**
   * This method should effectively set the starting point for synchronization
   * to the place specified by the <code>options</code> parameter. This should
   * cause all changes previous to the specified start point to be disregarded
   * and only changes after that point to be returned by
   * {@link #getNextBatchOfChanges(TransactionContext, int, AtomicLong)}.
   * <p>
   * There are several different startpoint types (see
   * {@link SetStartpointOptions}), and this implementation is not required to
   * support them all. If the specified startpoint type is unsupported, this
   * method should throw an {@link IllegalArgumentException}.
   * <p>
   * <b>IMPORTANT</b>: The <code>RESUME_AT_SERIALIZABLE</code> startpoint type
   * must be supported by your implementation, because this is used when a Sync
   * Pipe first starts up.
   * <p>
   * This method can be called from two different contexts:
   * <ul>
   * <li>When the 'set-startpoint' subcommand of the realtime-sync CLI is used
   * (the Sync Pipe is required to be stopped in this context)</li>
   * <li>Immediately after a connection is first established to the source
   * server (e.g. before the first call to
   * {@link #getNextBatchOfChanges(TransactionContext, int, AtomicLong)})</li>
   * </ul>
   * @param ctx
   *          a TransactionContext which provides a valid JDBC connection to the
   *          database.
   * @param options
   *          an object which indicates where exactly to start synchronizing
   *          (e.g. the end of the changelog, specific change number, a certain
   *          time ago, etc)
   * @throws SQLException
   *           if there is any error while setting the start point
   */
  @Override
  public void setStartpoint(final TransactionContext ctx, final SetStartpointOptions options)
            throws SQLException
  {
    switch(options.getStartpointType())
    {
      case StartpointType.BEGINNING_OF_CHANGELOG:
        lastCompletedChangeNumber = 0;
        nextChangeNumberToRetrieve = 0;
        break;
      case StartpointType.END_OF_CHANGELOG:
        PreparedStatement stmt = ctx.prepareStatement(
        "SELECT MAX(change_number) AS value FROM " + CHANGELOG_TABLE);
        ResultSet rset = stmt.executeQuery();
        try
        {
          long value = 0;
          if(rset.next())
          {
            value = rset.getLong("value");
          }
          else
          {
            String msg = "Could not find max change number";
            serverContext.logMessage(LogSeverity.SEVERE_ERROR, msg);
            throw new SQLException(msg);
          }
          lastCompletedChangeNumber = value;
          nextChangeNumberToRetrieve = value + 1;
        }
        finally
        {
          rset.close();
          stmt.close();
        }
        break;
      case StartpointType.RESUME_AT_CHANGE_NUMBER:
        nextChangeNumberToRetrieve = options.getChangeNumber();
        lastCompletedChangeNumber = nextChangeNumberToRetrieve - 1;
        break;
      case StartpointType.RESUME_AT_SERIALIZABLE: //When sync first starts up, this method is
                                                  //called with this StartpointType to initialize
                                                  //the internal state.
        Serializable token = options.getSerializableValue();
        if(token != null)
        {
          lastCompletedChangeNumber = (Long) token;
          nextChangeNumberToRetrieve = lastCompletedChangeNumber + 1;
        }
        break;
      default:
        throw new IllegalArgumentException("This startpoint type is not supported: " +
                        options.getStartpointType().toString());
    }
  }


  /**
   * Gets the current value of the startpoint for change detection. This is the
   * "bookmark" which indicates which changes have already been processed and
   * which have not. In most cases, a change number is used to detect changes
   * and is managed by the Synchronization Server, in which case this
   * implementation needs only to return the latest acknowledged
   * change number. In other cases, the return value may correspond to a
   * different value, such as the SYS_CHANGE_VERSION in Microsoft SQL Server.
   * In any case, this method should return the value that is updated by
   * {@link #acknowledgeCompletedOps(TransactionContext, LinkedList)}.
   * <p>
   * This method is called periodically and the return value is saved in the
   * persistent state for the Sync Pipe that uses this script as its Sync
   * Source.
   * <p>
   * <b>IMPORTANT</b>: The internal value for the startpoint should only be
   * updated after a sync operation is acknowledged back to this script (via
   * {@link #acknowledgeCompletedOps(TransactionContext, LinkedList)}).
   * Otherwise it will be possible for changes to be missed when the
   * Synchronization Server is restarted or a connection error occurs.
   * @return a value to store in the persistent state for the Sync Pipe. This is
   *         usually a change number, but if a changelog table is not used to
   *         detect changes, this value should represent some other token to
   *         pass into
   *         {@link #setStartpoint(TransactionContext, SetStartpointOptions)}
   *         when the sync pipe starts up.
   */
  @Override
  public Serializable getStartpoint()
  {
    return Long.valueOf(lastCompletedChangeNumber);
  }


  /**
   * Return a full source entry (in LDAP form) from the database, corresponding
   * to the {@link DatabaseChangeRecord} that is passed in through the
   * {@link SyncOperation}. This method should perform any queries necessary to
   * gather the latest values for all the attributes to be synchronized.
   * <p>
   * This method <b>must be thread safe</b>, as it will be called repeatedly and
   * concurrently by each of the Sync Pipe worker threads as they process
   * entries.
   * @param ctx
   *          a TransactionContext which provides a valid JDBC connection to the
   *          database.
   * @param operation
   *          the SyncOperation which identifies the database "entry" to
   *          fetch. The DatabaseChangeRecord can be obtained by calling
   *          <code>operation.getDatabaseChangeRecord()</code>.
   *          This is what is returned by
   *        {@link #getNextBatchOfChanges(TransactionContext, int, AtomicLong)}
   *          and also what comes out of
   *        {@link #listAllEntries(TransactionContext, String, BlockingQueue)}
   *          .
   * @return a full LDAP Entry, or null if no such entry exists.
   * @throws SQLException
   *           if there is an error fetching the entry
   */
  @Override
  public Entry fetchEntry(final TransactionContext ctx, final SyncOperation operation)
          throws SQLException
  {
    //Create a map of all the identity key/value pairs (delimited by '%%') that make
    //up the identifiable info. In this single-table example, the identifier is always
    //"uid={uid}". This comes from the 'identifier' column in the changelog
    //table. It is up to the trigger code for each table to construct this.
    DatabaseChangeRecord changeRecord = operation.getDatabaseChangeRecord();
    DN id = changeRecord.getIdentifiableInfo();
    Map<String,String> keyAndValue = ScriptUtils.dnToMap(id);
    if(!keyAndValue.containsKey("uid"))
    {
      throw new IllegalArgumentException("Identifier is missing 'uid'");
    }

    Entry entry;
    String entryType = changeRecord.getEntryType();
    if(entryType.equalsIgnoreCase("person"))
    {
      long uid = Long.valueOf(keyAndValue.get("uid"));
      String sql = "SELECT * FROM " + DATA_TABLE + " WHERE uid = ?";
      PreparedStatement stmt = ctx.prepareStatement(sql);
      try
      {
        stmt.setLong(1, uid);
        //Create an entry using the literal column names as attribute names
        entry = ctx.searchToRawEntry(stmt, "uid");
      }
      finally
      {
        stmt.close();
      }
    }
    else
    {
      throw new IllegalArgumentException("Unknown entry type: " + entryType);
    }
    return entry;
  }


  /**
   * Provides a way for the Synchronization Server to acknowledge back to the
   * script which sync operations it has processed. This method should update
   * the official startpoint which was set by
   * {@link #setStartpoint(TransactionContext, SetStartpointOptions)} and is
   * returned by {@link #getStartpoint()}.
   * <p>
   * <b>IMPORTANT</b>: The internal value for the startpoint should only be
   * updated after a sync operation is acknowledged back to this script (via
   * this method). Otherwise it will be possible for changes to be missed when
   * the Synchronization Server is restarted or a connection error occurs.
   * <p>
   * A {@link TransactionContext} is provided in case the acknowledgment needs
   * to make it all the way back to the database itself (for example if you were
   * using Oracle's Change Data Capture).
   * @param ctx
   *          a TransactionContext which provides a valid JDBC connection to the
   *          database.
   * @param completedOps
   *          a list of {@link SyncOperation}s that have finished processing.
   *          The records are listed in the order they were first detected.
   * @throws SQLException
   *           if there is an error acknowledging the changes back to the
   *           database
   */
  @Override
  public void acknowledgeCompletedOps(final TransactionContext ctx,
                                      final LinkedList<SyncOperation> completedOps)
                                        throws SQLException
  {
    if(!completedOps.isEmpty())
    {
      //Update lastCompletedChangeNumber to that of the last completed operation
      DatabaseChangeRecord last = completedOps.getLast().getDatabaseChangeRecord();
      lastCompletedChangeNumber = last.getChangeNumber();
    }
  }


  /**
   * Return the next batch of change records from the database. Change records
   * are just hints that a change happened; they do not include the actual data
   * of the change. In an effort to never synchronize stale data, the
   * Synchronization Server will go back and fetch the full source entry for
   * each change record.
   * <p>
   * On the first invocation, this should return changes starting from the
   * startpoint that was set by
   * {@link #setStartpoint(TransactionContext, SetStartpointOptions)}. This
   * method is responsible for updating the internal state such that subsequent
   * invocations do not return duplicate changes.
   * <p>
   * The resulting list should be limited by <code>maxChanges</code>. The
   * <code>numStillPending</code> reference should be set to the estimated
   * number of changes that haven't yet been retrieved from the changelog table
   * when this method returns, or zero if all the current changes have been
   * retrieved.
   * <p>
   * <b>IMPORTANT</b>: While this method needs to keep track of which changes
   * have already been returned so that it does not return them again, it should
   * <b>NOT</b> modify the official startpoint. The internal value for the
   * startpoint should only be updated after a sync operation is acknowledged
   * back to this script (via
   * {@link #acknowledgeCompletedOps(TransactionContext, LinkedList)}).
   * Otherwise it will be possible for changes to be missed when the
   * Synchronization Server is restarted or a connection error occurs. The
   * startpoint should not change as a result of this method.
   * <p>
   * This method <b>does not need to be thread-safe</b>. It will be invoked
   * repeatedly by a single thread, based on the polling interval set in the
   * Sync Pipe configuration.
   * @param ctx
   *          a TransactionContext which provides a valid JDBC connection to the
   *          database.
   * @param maxChanges
   *          the maximum number of changes to retrieve
   * @param numStillPending
   *          this should be set to the number of unretrieved changes that
   *          are still pending after this batch has been retrieved. This will
   *          be passed in
   *          as zero, and may be left that way if the actual value cannot be
   *          determined.
   * @return a list of {@link DatabaseChangeRecord} instances, each
   *         corresponding to a row in the changelog table (or the equivalent if
   *         some other change tracking mechanism is being used). If there are
   *         no new changes to return, this method should return an empty list.
   * @throws SQLException
   *           if there is any error while retrieving the next batch of changes
   */
  @Override
  public List<DatabaseChangeRecord> getNextBatchOfChanges(final TransactionContext ctx,
                                                          final int maxChanges,
                                                          final AtomicLong numStillPending)
                                                            throws SQLException
  {
    List<DatabaseChangeRecord> results = new ArrayList<DatabaseChangeRecord>();
    PreparedStatement stmt = ctx.prepareStatement(
            "SELECT * FROM " + CHANGELOG_TABLE + " WHERE change_number >= ? ORDER BY" +
              " change_number ASC");

    //This is a generic way to limit the size of the result set; however, this may
    //or may not be implemented by the JDBC driver to alter the query itself. It may
    //just allow the full set of results to come back and then perform the truncation
    //within the JVM, which can cause out of memory errors. Most databases have a
    //much more efficient mechansim (for example "SELECT TOP(1000)..." in SQLServer
    //and "SELECT ... WHERE ROWNUM < 1000" in Oracle).
    stmt.setMaxRows(maxChanges);

    stmt.setLong(1, nextChangeNumberToRetrieve);
    ResultSet rset = stmt.executeQuery();
    while(rset.next())
    {
      if(results.size() >= maxChanges)
      {
        serverContext.debugError("The result set contained too many rows; expected no more than " + maxChanges);
        break;
      }

      //In this case there is a change_type column in the changelog table which gives us the change type
      ChangeType type = ChangeType.valueOf(rset.getString("change_type"));
      DatabaseChangeRecord.Builder bldr = new DatabaseChangeRecord.Builder(
                                                  type, rset.getString("identifier"));
      long changeNum = rset.getLong("change_number");
      //Update nextChangeNumberToRetrieve so that the next call will get the next batch
      nextChangeNumberToRetrieve = changeNum + 1;
      bldr.changeNumber(changeNum);
      bldr.tableName(rset.getString("table_name"));
      String entryType = rset.getString("entry_type");
      if(!entryType.equalsIgnoreCase("person"))
      {
        //This sync source only handles the "person" entry type
        serverContext.debugInfo("Skipping change with entry type: " + entryType);
        continue;
      }
      bldr.entryType(entryType);

      //Get the list of changed columns for this change. For UPDATE operations, the Sync Server
      //will only modify the destination attributes that depend on the originally changed source
      //columns (i.e. if this is not set, no modifications will take place on the destination entry).
      String cols = rset.getString("changed_columns");
      bldr.changedColumns(cols != null ? cols.split(",") : null);

      //Get the database user who made the change
      bldr.modifier(rset.getString("modifiers_name"));

      //Get the timestamp of the change
      bldr.changeTime(rset.getTimestamp("change_time").getTime());

      results.add(bldr.build());
    }
    rset.close();
    stmt.close();

    //Figure out how many changes are still unretrieved at this point
    stmt = ctx.prepareStatement("SELECT COUNT(*) FROM " + CHANGELOG_TABLE + " WHERE change_number >= ?");
    stmt.setLong(1, nextChangeNumberToRetrieve);
    rset = stmt.executeQuery();
    if(rset.next())
    {
      long stillPending = rset.getLong(1);
      numStillPending.set(stillPending);
    }
    rset.close();
    stmt.close();

    return results;
  }


  /**
   * Gets a list of all the entries in the database for a given entry type. This
   * is used by the 'resync' command line tool. The default implementation
   * throws a {@link UnsupportedOperationException}; subclasses should override
   * if the resync functionality is needed.
   * <p>
   * The <code>entryType</code> is user-defined; it will be
   * passed in on the command line for resync. The <code>outputQueue</code>
   * should contain {@link DatabaseChangeRecord} objects with the
   * <code>ChangeType</code> set to <i>resync</i>.
   * <p>
   * This method should not return until all the entries of the given entryType
   * have been added to the output queue. Separate threads will concurrently
   * drain entries from the queue and process them. (The queue should not
   * actually contain full entries, but rather DatabaseChangeRecord objects
   * which identify the full database entries. These objects are then
   * individually passed in to
   * {@link #fetchEntry(TransactionContext, SyncOperation)}. Therefore,
   * it is important to make sure that the DatabaseChangeRecord instances
   * contain enough identifiable information (e.g. primary keys) for each entry
   * so that the entry can be found again.
   * <p>
   * The lifecycle of resync is similar to that of real-time sync, with a few
   * differences:
   * <ol>
   * <li>Stream out a list of all IDs in the database (for a given entryType)
   * </li>
   * <li>Fetch full source entry for an ID</li>
   * <li>Perform any mappings and compute the equivalent destination entry</li>
   * <li>Fetch full destination entry</li>
   * <li>Diff the computed destination entry and actual destination entry</li>
   * <li>Apply the minimal set of changes at the destination to bring it in sync
   * </li>
   * </ol>
   * If the total set of entries is very large, it is fine to split up the work
   * into multiple database queries within this method. The queue will not grow
   * out of control because it blocks when it becomes full. The queue capacity
   * is fixed at 1000.
   * <p>
   * @param ctx
   *          a TransactionContext which provides a valid JDBC connection to the
   *          database.
   * @param entryType
   *          the type of database entry to be fetched (this is specified
   *          on the CLI for the resync command)
   * @param outputQueue
   *          a queue of DatabaseChangeRecord objects which will be individually
   *          fetched via {@link #fetchEntry(TransactionContext, SyncOperation)}
   * @throws SQLException
   *           if there is an error retrieving the list of entries to resync
   */
  @Override
  public void listAllEntries(final TransactionContext ctx,
                             final String entryType,
                             final BlockingQueue<DatabaseChangeRecord> outputQueue)
                                throws SQLException
  {
    serverContext.debugInfo("Beginning to dump all entries...");
    if(entryType.equalsIgnoreCase("person"))
    {
      //Get a full list of the UIDs
      PreparedStatement stmt = ctx.prepareStatement(
                "SELECT uid FROM " + DATA_TABLE + " ORDER BY uid ASC");
      ResultSet rset = stmt.executeQuery();
      while(rset.next())
      {
        long uid = rset.getLong("uid");
        DatabaseChangeRecord.Builder bldr =
              new DatabaseChangeRecord.Builder(ChangeType.resync, "uid=" + uid);
        bldr.entryType("person"); //set the entry type so that fetch() can use it
        outputQueue.put(bldr.build());
      }
      rset.close();
      stmt.close();
    }
    else
    {
      throw new IllegalArgumentException("Unknown entry type: " + entryType);
    }
  }


  /**
   * Gets a list of all the entries in the database from a given file input.
   * This is used by the 'resync' command line tool. The default implementation
   * throws a {@link UnsupportedOperationException}; subclasses should override
   * if the resync functionality is needed for specific database records, which
   * can be specified in the input file.
   * <p>
   * The format for the <code>inputLines</code> (e.g. the content of the file)
   * is user-defined; it may be key/value pairs, primary keys, or full SQL
   * statements, for example. The use of this method is triggered via the
   * <i>--sourceInputFile</i> argument on the resync CLI. The
   * <code>outputQueue</code> should contain {@link DatabaseChangeRecord}
   * objects with the <code>ChangeType</code> set to <i>resync</i>.
   * <p>
   * This method should not return until all the entries specified by the input
   * file have been added to the output queue. Separate threads will
   * concurrently drain entries from the queue and process them. (The queue
   * should not actually contain full entries, but rather DatabaseChangeRecord
   * objects which identify the full database entries. These objects are then
   * individually passed in to
   * {@link #fetchEntry(TransactionContext, SyncOperation)}. Therefore,
   * it is important to make sure that the DatabaseChangeRecord instances
   * contain enough identifiable information (e.g. primary keys) for each entry
   * so that the entry can be found again.
   * <p>
   * The lifecycle of resync is similar to that of real-time sync, with a few
   * differences:
   * <ol>
   * <li>Stream out a list of all IDs in the database (using the given input
   *  file)</li>
   * <li>Fetch full source entry for an ID</li>
   * <li>Perform any mappings and compute the equivalent destination entry</li>
   * <li>Fetch full destination entry</li>
   * <li>Diff the computed destination entry and actual destination entry</li>
   * <li>Apply the minimal set of changes at the destination to bring it in sync
   * </li>
   * </ol>
   * If the total set of entries is very large, it is fine to split up the work
   * into multiple database queries within this method. The queue will not grow
   * out of control because it blocks when it becomes full. The queue capacity
   * is fixed at 1000.
   * <p>
   * @param ctx
   *          a TransactionContext which provides a valid JDBC connection to the
   *          database.
   * @param inputLines
   *          an Iterator containing the lines from the specified input file to
   *          resync (this is specified on the CLI for the resync command).
   *          These lines can be any format, for example a set of primary keys,
   *          a set of WHERE clauses, a set of full SQL queries, etc.
   * @param outputQueue
   *          a queue of DatabaseChangeRecord objects which will be individually
   *          fetched via {@link #fetchEntry(TransactionContext, SyncOperation)}
   * @throws SQLException
   *           if there is an error retrieving the list of entries to resync
   */
  @Override
  public void listAllEntries(
                          final TransactionContext ctx,
                          final Iterator<String> inputLines,
                          final BlockingQueue<DatabaseChangeRecord> outputQueue)
                             throws SQLException
  {
    while(inputLines.hasNext())
    {
      String uid = inputLines.next().trim();
      if(uid.isEmpty())
      {
        continue;
      }

      DatabaseChangeRecord.Builder bldr = new DatabaseChangeRecord.Builder(
                                               ChangeType.resync, "uid=" + uid);
      //Set the entry type so that fetchEntry() can use it
      bldr.entryType("person");
      outputQueue.put(bldr.build());
    }
  }


  /**
   * Performs a cleanup of the changelog table (if desired). There is a
   * background thread that periodically invokes this method. It should remove
   * any rows in the changelog table that are more than
   * <code>maxAgeMillis</code> milliseconds old.
   * <p>
   * <b>NOTE:</b> If the system clock on the database server is not in sync with
   * the system clock on the Synchronization Server, this method should query
   * the database for its current time in order to determine the cut-off point
   * for deleting changelog records.
   * <p>
   * If a separate mechanism will be used to manage the changelog table, this
   * method may be implemented as a no-op and always return zero. This is how
   * the default implementation behaves.
   * @param ctx
   *          a TransactionContext which provides a valid JDBC connection to the
   *          database.
   * @param maxAgeMillis
   *          the period of time (in milliseconds) after which a changelog table
   *          record should be deleted
   * @return the number of rows that were deleted from the changelog table
   * @throws SQLException
   *           if there is an error purging records from the changelog table
   */
  @Override
  public int cleanupChangelog(final TransactionContext ctx, final long maxAgeMillis) throws SQLException
  {
    //get current time on database
    PreparedStatement stmt = ctx.prepareStatement("SELECT CURRENT_TIMESTAMP");
    ResultSet rset = stmt.executeQuery();
    long currentTimeMillis;
    try
    {
      if(rset.next())
      {
        currentTimeMillis = rset.getTimestamp(1).getTime();
      }
      else
      {
        throw new SQLException("Cannot determine current timestamp on database.");
      }
    }
    finally
    {
      rset.close();
      stmt.close();
    }

    stmt = ctx.prepareStatement(
            "DELETE FROM " + CHANGELOG_TABLE + " WHERE change_time < ?");
    stmt.clearWarnings();
    stmt.setTimestamp(1, new Timestamp(currentTimeMillis - maxAgeMillis));
    int rowCount = stmt.executeUpdate();
    stmt.close();
    return rowCount;
  }
}