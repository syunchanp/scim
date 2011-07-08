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



import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import com.unboundid.directory.sdk.common.config.AlertHandlerConfig;
import com.unboundid.directory.sdk.common.scripting.ScriptedAlertHandler;
import com.unboundid.directory.sdk.common.types.AlertNotification;
import com.unboundid.directory.sdk.common.types.LogSeverity;
import com.unboundid.directory.sdk.common.types.ServerContext;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.util.StaticUtils;
import com.unboundid.util.args.ArgumentException;
import com.unboundid.util.args.ArgumentParser;
import com.unboundid.util.args.FileArgument;



/**
 * This class provides a simple example of a scripted alert handler which will
 * write information about each alert notification to a file in a specified
 * directory.  The file will be named with the alert ID. It has one
 * configuration argument:
 * <UL>
 *   <LI>alert-directory -- The path to the directory in which files will be
 *       written about alerts that are generated.</LI>
 * </UL>
 */
public final class ExampleScriptedAlertHandler
       extends ScriptedAlertHandler
{
  /**
   * The name of the argument that will be used to specify the path to the
   * directory in which to write alert files.
   */
  private static final String ARG_NAME_ALERT_DIR = "alert-directory";



  // The current configuration for this alert handler.
  private volatile AlertHandlerConfig config;

  // The directory in which to write alert files.
  private volatile File alertDirectory;

  // The server context for the server in which this extension is running.
  private ServerContext serverContext;



  /**
   * Creates a new instance of this alert handler.  All alert handler
   * implementations must include a default constructor, but any initialization
   * should generally be done in the {@code initializeAlertHandler} method.
   */
  public ExampleScriptedAlertHandler()
  {
    // No implementation required.
  }



  /**
   * Updates the provided argument parser to define any configuration arguments
   * which may be used by this alert handler.  The argument parser may also be
   * updated to define relationships between arguments (e.g., to specify
   * required, exclusive, or dependent argument sets).
   *
   * @param  parser  The argument parser to be updated with the configuration
   *                 arguments which may be used by this alert handler.
   *
   * @throws  ArgumentException  If a problem is encountered while updating the
   *                             provided argument parser.
   */
  @Override()
  public void defineConfigArguments(final ArgumentParser parser)
         throws ArgumentException
  {
    // Add an argument that allows you to specify the alert directory.
    Character shortIdentifier = null;
    String    longIdentifier  = ARG_NAME_ALERT_DIR;
    boolean   required        = true;
    int       maxOccurrences  = 1;
    String    placeholder     = "{path}";
    String    description     = "The path to the directory in which alert " +
         "files should be written.  Relative paths will be relative to the " +
         "server root.";
    boolean   fileMustExist   = true;
    boolean   parentMustExist = true;
    boolean   mustBeFile      = false;
    boolean   mustBeDirectory = true;

    parser.addArgument(new FileArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description, fileMustExist,
         parentMustExist, mustBeFile, mustBeDirectory));
  }



  /**
   * Initializes this alert handler.
   *
   * @param  serverContext  A handle to the server context for the server in
   *                        which this extension is running.
   * @param  config         The general configuration for this alert handler.
   * @param  parser         The argument parser which has been initialized from
   *                        the configuration for this alert handler.
   *
   * @throws  LDAPException  If a problem occurs while initializing this alert
   *                         handler.
   */
  @Override()
  public void initializeAlertHandler(final ServerContext serverContext,
                                     final AlertHandlerConfig config,
                                     final ArgumentParser parser)
         throws LDAPException
  {
    serverContext.debugInfo("Beginning alert handler initialization");

    this.serverContext = serverContext;
    this.config        = config;

    // Get the path to the directory in which the alert files should be written.
    final FileArgument alertDirArg =
         (FileArgument) parser.getNamedArgument(ARG_NAME_ALERT_DIR);
    alertDirectory = alertDirArg.getValue();
  }



  /**
   * Indicates whether the configuration contained in the provided argument
   * parser represents a valid configuration for this extension.
   *
   * @param  config               The general configuration for this alert
   *                              handler.
   * @param  parser               The argument parser which has been initialized
   *                              with the proposed configuration.
   * @param  unacceptableReasons  A list that can be updated with reasons that
   *                              the proposed configuration is not acceptable.
   *
   * @return  {@code true} if the proposed configuration is acceptable, or
   *          {@code false} if not.
   */
  @Override()
  public boolean isConfigurationAcceptable(final AlertHandlerConfig config,
                      final ArgumentParser parser,
                      final List<String> unacceptableReasons)
  {
    // No special validation is required.
    return true;
  }



  /**
   * Attempts to apply the configuration contained in the provided argument
   * parser.
   *
   * @param  config                The general configuration for this alert
   *                               handler.
   * @param  parser                The argument parser which has been
   *                               initialized with the new configuration.
   * @param  adminActionsRequired  A list that can be updated with information
   *                               about any administrative actions that may be
   *                               required before one or more of the
   *                               configuration changes will be applied.
   * @param  messages              A list that can be updated with information
   *                               about the result of applying the new
   *                               configuration.
   *
   * @return  A result code that provides information about the result of
   *          attempting to apply the configuration change.
   */
  @Override()
  public ResultCode applyConfiguration(final AlertHandlerConfig config,
                                       final ArgumentParser parser,
                                       final List<String> adminActionsRequired,
                                       final List<String> messages)
  {
    // Get the new path to the alert directory.  We don't really care if it's
    // the same as the directory that was already in use.
    final FileArgument alertDirArg =
         (FileArgument) parser.getNamedArgument(ARG_NAME_ALERT_DIR);
    alertDirectory = alertDirArg.getValue();

    // Cache an updated copy of the configuration.
    this.config = config;

    return ResultCode.SUCCESS;
  }



  /**
   * Performs any cleanup which may be necessary when this alert handler is
   * to be taken out of service.
   */
  @Override()
  public void finalizeAlertHandler()
  {
    // No finalization is required.
  }



  /**
   * Performs any processing which may be necessary to handle the provided alert
   * notification.
   *
   * @param  alert  The alert notification generated within the server.
   */
  @Override()
  public void handleAlert(final AlertNotification alert)
  {
    // The filename for the alert will be the alert ID.  It will be unique, so
    // we can be certain there won't be any collisions.
    final File alertFile = new File(alertDirectory, alert.getAlertID());

    final PrintWriter writer;
    try
    {
      writer = new PrintWriter(alertFile);
    }
    catch (final Exception e)
    {
      serverContext.debugCaught(e);

      // If we can't create the file, then we can't write anything about this
      // alert.  We don't want to generate an alert in response to this because
      // that could create an infinite loop.  The only thing we'll do is to log
      // an error message.
      serverContext.logMessage(LogSeverity.SEVERE_ERROR,
           "The example alert handler defined in configuration entry '" +
                config.getConfigObjectDN() + "' could not write file '" +
                alertFile.getAbsolutePath() + "' with information about " +
                "alert " + alert.toString() + ":  " +
                StaticUtils.getExceptionMessage(e));
      return;
    }

    // Write information about the alert to the file.
    writer.println("Alert ID:  " + alert.getAlertID());
    writer.println("Alert Type:  " + alert.getAlertTypeName());
    writer.println("Alert Severity:  " + alert.getAlertSeverity().toString());
    writer.println("Generated by Class:  " +
         alert.getAlertGeneratorClassName());
    writer.println("Alert Message:  " + alert.getAlertMessage());

    writer.close();
  }
}