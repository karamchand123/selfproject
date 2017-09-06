/*
 * @(#)IntegratorImpl.java     1.0 4/24/2000
 *
 * Copyright 1999 by American Management Systems, Inc.,
 * 4050 Legato Road, Fairfax, Virginia, U.S.A.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of American Management Systems, Inc. ("Confidential Information").  You
 * shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 * you entered into with American Management Systems, Inc.
 *
 * PVCS Modification Log:
 *
 * $Log:   N:/ASG/Projects/AMS_ADVANTAGE_Technical_Architecture/archives/dev/advantage/Source/VLS/OtherFiles/Infrastructure/AMSIntegrator.java-arc  $
 *
 *    Rev 1.6   09 Feb 2004 17:11:36   ighosh
 * IR-ADFX2615 - Fixed the SAX parser error because of null attribute.
 *
 *    Rev 1.5   22 Jan 2004 16:05:00   ighosh
 * IR-ADFX2255 - Fix for exception report for Integration.
 *
 */

package advantage;

import java.io.* ;
import java.lang.reflect.* ;
import java.util.* ;
import java.util.zip.* ;

import org.xml.sax.* ;

import javax.ejb.EJBException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.ServiceUnavailableException;
import javax.rmi.PortableRemoteObject;
import javax.xml.parsers.SAXParserFactory ;
import javax.xml.parsers.SAXParser ;
import javax.xml.parsers.ParserConfigurationException ;

import versata.vls.*;
import versata.vls.ejb.VLSContext;
import versata.vls.ejb.VLSContextHome;
import versata.common.*;
import versata.common.Transaction.*;

import org.apache.commons.logging.*;

import com.amsinc.gems.adv.common.AMSParams;
import com.amsinc.gems.adv.common.AMSLogger;
import com.amsinc.gems.adv.common.AMSLogConstants;
import com.amsinc.gems.adv.common.AMSBatchConstants;
import com.amsinc.gems.adv.common.AMSCommonConstants;
import com.amsinc.gems.adv.common.AMSSecurity;
import com.amsinc.gems.adv.common.AMSSecurityException;
import com.amsinc.gems.adv.common.AMSSecurityObject;
import com.amsinc.gems.adv.common.AMSXMLImportExportException;

import advantage.SysManUtil;
import advantage.AMSMetaDataToXML;
import advantage.AMSDataObject;
import advantage.AMSNotificationDataToXML;


/**
* This is the integration implementation class.  It is the middle-man between
* the integration bean and the ADVANTAGE application.  Namely, it interacts
* with SysManUtil and AMSMetaDataToXML to perform the various services and
* translates the various input and output XML formats from and to a single XML
* response and reply for the integration bean.  A new instance of this class
* is created from within Integrator EJB instance.
*
* @author Indrajit Ghosh
*/

public class AMSIntegrator
         implements DocumentHandler, AMSBatchConstants, AMSCommonConstants
{
   /** the default application name used for initiating VLS session */
   private static final String APPLICATION ="Advantage";

   /** the default package name used for object lookup*/
   private static final String PACKAGE = "advantage";

   /** The code logging object */
   private static Log moLog = AMSLogger.getLog( AMSIntegrator.class,
         AMSLogConstants.FUNC_AREA_METADATA_SERVICES ) ;


   /**
    * EAI Constants used for parsing xml documents
    *
    */
   private static final String RSRC_ID_TAG    = "RSRC_ID" ;
   private static final String HEADER_TAG     = "HEADER" ;
   private static final String REQUESTOR_TAG  = "REQUESTOR" ;
   private static final String RECIPIENT_TAG  = "RECIPIENT" ;
   private static final String PARAMETER_TAG  = "Parameter" ;

   /**
    * The type for document and reference table
    */
   private static final String DOCUMENT       = "DOCUMENT" ;
   private static final String REFERENCE_TBL  = "REFTABLE" ;

   /**
    * Default approval level for integration.
    */
   private static final String INTEGRATION_APRV_LVL = "15";

   /**
    * The current input XML stream
    */
   private InputStream moInputXMLStream = null ;

   /**
    * String buffer that contains the XML that has to be flushed.
    * No temp file is used becaused all processing is sequential
    * and there is no need to store the XML in a file.
    */
   private StringBuffer moXMLToFlush = null;

   /**
    * Flag to indicate a tag while parsing
    */
   private boolean mboolIsActionTag  = false ;

  /**
    * The header properties
    */
   private String msHeaderType = "Reply" ;
   private String msRequestor = "" ;
   private String msRecipient = "" ;
   private Session moSession = null ;
   private int miActionCd = 0 ;

  /**
    * boolean to indicate if XML generated would be
    * namespace aware
    */
   private boolean mboolXMLSchemaAware = false ;

   /**
    * Document import XML tag: AMS_DOCUMENT
    */
   public static final String  AMS_DOCUMENT = "AMS_DOCUMENT" ;


  /**
   * Integrator Implementation class constructor.
   */
   public AMSIntegrator()
   {

   }

  /**
   * Integrator Implementation class constructor.
   *
   * @param fboolXMLSchemaAware true if xml should be schema aware
   */
   public AMSIntegrator( boolean fboolXMLSchemaAware )
   {
      mboolXMLSchemaAware = fboolXMLSchemaAware ;
   }

   /**
    * This method starts the local versata server if it has not been initialzed.
    *
    * @param fsUser The user login
    * @param fsPassword The user password
    * @return If Server was successfully started
    * @throws Exception
    */
   public boolean initializeServer(String fsUser, String fsPassword) throws Exception
   {
      //see if server is already started
      if (ServerEnvironment.getServer() !=null)
      {
         return true;
      }
      
      try
      {
         AMSUtil.startAdminVls("VLSServer");
      }
      catch (Exception loExp)
      {
         // Add exception log to logger object
         moLog.error("Unexpected error encountered while processing. ", loExp);

         throw new Exception("Could not start server:" + loExp.getMessage());
      } // end catch

      return true; //server initialized
   } // initializeServer

   /**
   * This method creates a new session for the given user id and password provided.
   *
   * @param fsUserId The user id for the session to be created.
   * @param fsPassword The password corresponding to the user id of the session to be created.
   * @return String The XML output containing the session identifier or error messages.
   */
   public String createSession(String fsUserID, String fsPassword)
   {
      VSORBSessionImpl     loRetSession      = null;
      VSORBSecuritySession loSecuritySession;

      try
      {
         if ( !initializeServer(fsUserID, fsPassword) )
         {
            moLog.error( "AUTHENTICATION FAILED" ) ;

            writeMsgToXML("AUTHENTICATION FAILED", 3, -1);
            return flushXMLDocument();
         }
         /*
         * create security object
         */
         AMSSecurityObject loSecurityObject = new AMSSecurityObject(fsUserID,fsPassword);
         VSSecurityObjectHolder loSecurityObjHolder = new VSSecurityObjectHolder();
         loSecurityObjHolder.setSecurityObject((Object)loSecurityObject);

         //get server remote handle
         ByteArrayOutputStream loOutStream = new ByteArrayOutputStream();
         ObjectOutput loOutObj = new ObjectOutputStream(loOutStream);
         loOutObj.writeObject(loSecurityObjHolder);
         loOutObj.flush();
         loRetSession = ServerEnvironment.getServer().getSession1Internal(loOutStream.toByteArray(),
            APPLICATION, PACKAGE);

         loSecuritySession = loRetSession.getRemoteReferenceForSecuritySession();
         ServerEnvironment.getServer().addToSessionsList(loRetSession, loSecuritySession);

         /**
         *  will not expire. needs to be closed explicitly
         */
         loRetSession.setExpireTime(Long.MAX_VALUE);

         //reset all ADVANTAGE used property strings
         //resetSessionProperties(loRetSession);

         // mark session as associated with integration processes and offline
         loRetSession.setProperty(AMSCommonConstants.SESSION_INTEGRATION_PROCESS,
            AMSCommonConstants.SESSION_DOC_YES);
         loRetSession.setProperty(AMSCommonConstants.SESSION_OFFLINE_PROCESS,
            AMSCommonConstants.SESSION_DOC_YES);

         moSession = (Session)loRetSession;

         writeMsgToXML("LOGIN SUCCESSFUL", 0, 0);

         loOutStream.close();

         return flushXMLDocument();
      }
      catch(Exception foExp)
      {
         moLog.error( "AUTHENTICATION FAILED: ", foExp ) ;

         writeMsgToXML("AUTHENTICATION FAILED", 3, -1);
         return flushXMLDocument();
      }
   } // end of createSession

  /**
   * This method creates a new session with the option of making it a transactional session.
   * for the given user id and password provided.
   *
   * NOTE: If it is a transactional session, any invocation within this session
   * that will force an implicit commit or rollback (i.e. submitting a document)
   * will also commit or rollback any event notifications retrieved up to that point.
   * Therefore attempting to rollback this session with the method closeSession
   * will not work because the updates might have already been commited.
   *
   * @param fsUserId The user id for the session to be created.
   * @param fsPassword The password corresponding to the user id of the session to be created.
   * @param fboolTransactional Specifies if the session should be a transactional session
   * @return String The XML output containing the session identifier or error messages.
   */
   public String createSession(String fsUserID, String fsPassword,
      boolean fboolTransactional)
   {
      try
      {
         String lsStr = this.createSession(fsUserID, fsPassword);

         if(fboolTransactional && !moSession.isTransactionInProgress())
         {
            moSession.beginTransaction();
         } /* end if. */

         return lsStr;
      }
      catch (Exception foExp)
      {
         moLog.error( "AUTHENTICATION FAILED: ", foExp ) ;

         writeMsgToXML("AUTHENTICATION FAILED", 3, -1);
         return flushXMLDocument();
      }
   } /* end of createSession */

   /**
    * This method sets the argument as Session member variable.
    *
    * @param foSession The Session object to be set for this instance
    * @return boolean - true if Session is not null, false otherwise
    */
   public boolean setSession(Session foSession)
   {
      if (foSession != null)
      {
         moSession = (Session) foSession;
         return true;
      }
      return false;
   }
  /**
   * This method closes the given session.
   *
   * @param fsSessionId The session identifier for the session to be closed.
   * @return String The XML output containing the messages or error messages.
   */
   public String closeSession()
   {
      try
      {
         if (moSession == null)
         {
            writeMsgToXML("AUTHENTICATION FAILED: INVALID SESSION", 3, -1);
            return flushXMLDocument();
         }
         else
         {
            moSession.close();

            writeMsgToXML("LOGOUT SUCCESSFUL", 0, 0);

            return flushXMLDocument();
         }
      }
      catch(Exception foExp)
      {
         moLog.error( "ERROR IN PROCESSING CLOSE SESSION: ", foExp ) ;
         writeMsgToXML("ERROR IN PROCESSING CLOSE SESSION", 3, -1);
         return flushXMLDocument();
      }
   }// end of closeSession()

   /**
   * This method closes the given transactional session.  It tells whether the
   * transaction was successful or not.
   *
   * @param fboolSuccess If the transaction was successful or not.
   * @return String The XML output containing the messages or error messages.
   */
   public String closeSession( boolean fboolCommitTransaction )
   {
      try
      {
         if (moSession == null)
         {
            writeMsgToXML("AUTHENTICATION FAILED: INVALID SESSION", 3, -1);
            return flushXMLDocument();
         }
         else if(moSession.isTransactionInProgress())
         {
            if (fboolCommitTransaction)
            {
               moSession.commit();
            } /* end if (fboolCommitTransaction) */
            else
            {
               moSession.rollback();
            } /* end else */

            return this.closeSession();
         } /* end if (moSession.isTransactionInProgress()) */
         else
         {
            moSession.close();

            writeMsgToXML("Session was closed but no transaction was in progress.", 2, 0);
            return flushXMLDocument();
         } /* end else */
      } /* end try */
      catch (Exception foExp)
      {
         try
         {
            moSession.rollback();
         }
         catch (Exception foExp1)
         {
             // Add exception log to logger object
             moLog.error("Unexpected error encountered while processing. ", foExp1);

         }

         moLog.error( "ERROR IN PROCESSING CLOSE SESSION: ", foExp ) ;

         writeMsgToXML("ERROR IN PROCESSING CLOSE SESSION", 3, -1);
         return flushXMLDocument();
      }
   } /* end of closeSession() */

   /**
   * This method returns a list of all document types defined within the ADVANTAGE application.
   *
   * @param fsInputXML The XML input containing the session identifier
   * @return The XML output containing the document type list or error messages
   * @deprecated Please use getDocTypeList()
   */
   public String documentTypeList(String fsInputXML)
   {
      mboolXMLSchemaAware = false ;
      return getList(fsInputXML, DOCUMENT);

   }// end of documentTypeList()

   /**
   * This method returns a list of all document types defined within the ADVANTAGE application.
   *
   * @param fsInputXML The XML input containing the session identifier
   * @return The XML output containing the document type list or error messages
   */
   public String getDocTypeList(String fsInputXML )
   {
      mboolXMLSchemaAware = true ;
      return getList(fsInputXML, DOCUMENT);

   }// end of documentTypeList()

   /**
   * This method returns a list of all reference tables defined within the ADVANTAGE application.
   *
   * @param fsInputXML The XML input containing the session identifier
   * @return The XML output containing the document type list or error messages
   * @deprecated Please use getRefTableList()
   */
   public String referenceTableList(String fsInputXML)
   {
      mboolXMLSchemaAware = false ;
      return getList(fsInputXML, REFERENCE_TBL);
   }

   /**
   * This method returns a list of all reference tables defined within the ADVANTAGE application.
   *
   * @param fsInputXML The XML input containing the session identifier
   * @return The XML output containing the document type list or error messages
   */
   public String getRefTableList(String fsInputXML)
   {
      mboolXMLSchemaAware = true ;
      return getList(fsInputXML, REFERENCE_TBL);
   }

  /**
   * This method returns a complete description of one or more document types.  Each
   * description includes all tables that form the document, all fields within those
   * tables, and a list of valid actions.
   *
   * @param fsInputXML The XML input containing the session identifier and the document code(s) to describe
   * @return The XML output containing the document type description(s) or error messages
   * @deprecated Please use getDocTypeDescription()
   */
   public String describeDocumentType(String fsInputXML)
   {
      mboolXMLSchemaAware = false ;
      return describe(fsInputXML);

   } // end of describeDocumentType()

  /**
   * This method returns a complete description of one or more document types.  Each
   * description includes all tables that form the document, all fields within those
   * tables, and a list of valid actions.
   *
   * @param fsInputXML The XML input containing the session identifier and the document code(s) to describe
   * @return The XML output containing the document type description(s) or error messages
   */
   public String getDocTypeDescription(String fsInputXML)
   {
      mboolXMLSchemaAware = true ;
      return describe(fsInputXML);

   } // end of describeDocumentType()

  /**
   * This method returns a complete description of one or more reference tables.
   * Each description includes all fields within the table(s), and a list of valid actions.
   *
   * @param fsInputXML The XML input containing the session identifier and the reference table(s) to describe
   * @return the XML output containing the reference table description(s) or error messages
   * @deprecated Please use getRefTableDescription
   */
   public String describeReferenceTable(String fsInputXML)
   {
      mboolXMLSchemaAware = false ;
      return describe(fsInputXML);
   } // end of describeReferenceTable()

  /**
   * This method returns a complete description of one or more reference tables.
   * Each description includes all fields within the table(s), and a list of valid actions.
   *
   * @param fsInputXML The XML input containing the session identifier and the reference table(s) to describe
   * @return the XML output containing the reference table description(s) or error messages
   */
   public String getRefTableDescription(String fsInputXML)
   {
      mboolXMLSchemaAware = true ;
      return describe(fsInputXML);
   } // end of describeReferenceTable()

  /**
   * This method returns a complete description of one or more reference tables or documents.
   * Each description includes all fields within the table(s), and a list of valid actions.
   *
   * @param fsInputXML The XML input containing the session identifier and the reference table(s) to describe
   * @return the XML output containing the reference table description(s) or error messages
   */
   public String describe(String fsInputXML)
   {
      InputStream loInputStream;
      ByteArrayOutputStream loByteOutStream;
      AMSMetaDataToXML loMetaToXML;
      Session loSession = null;

      try
      {
         processXML(fsInputXML);

         if (moSession == null)
         {
            writeMsgToXML("AUTHENTICATION FAILED: INVALID SESSION", 3, -1);
            return flushXMLDocument();
         }

         //set the XML document version and header
         startXMLDocument();
         setXMLHeader(msHeaderType, msRequestor, msRecipient, 0);
         moXMLToFlush.append("<BODY>\n");

         loInputStream = new ByteArrayInputStream(fsInputXML.getBytes());

         // create a new SysManUtil instance and then do the processing
         loMetaToXML = new AMSMetaDataToXML(moSession);
         loMetaToXML.setXMLSchemaAwareness( mboolXMLSchemaAware ) ;
         loMetaToXML.describe(loInputStream);

         loByteOutStream = new ByteArrayOutputStream();
         loMetaToXML.writeXML(loByteOutStream);
         loMetaToXML.cleanUpResources();
         appendToXML(loByteOutStream);

         // set the end body tag and end of XML document
         moXMLToFlush.append("\n</BODY>\n");
         endXMLDocument();
         loByteOutStream.close();

         return flushXMLDocument();

      }
      catch(AMSXMLImportExportException foAMSExpImpExp)
      {
         // Add exception log to logger object
         moLog.error("Unexpected error encountered while processing. ", foAMSExpImpExp);


         writeMsgToXML(foAMSExpImpExp.getMessage(),
                        foAMSExpImpExp.getErrorType(), -1);
         return flushXMLDocument();
      }
      catch(Exception foExp)
      {
         moLog.error( "Exception: ", foExp ) ;

         writeMsgToXML("ERROR IN PROCESSING ACTION", 3, -1);
         return flushXMLDocument();
      }
  } // end of describeReferenceTable()

  /**
   * This method returns a complete list of notifications of one or more reference tables or documents.
   * Each description includes all fields within the table(s), and a list of updates performed.
   *
   * @return the XML output containing the reference table or document notification(s) or error messages
   * @deprecated Please use getEvents()
   */
   public String getNotification()
   {
      mboolXMLSchemaAware = false ;
      return retrieveEvents();
   } // end of getNotification()

  /**
   * This method performs a query or action within the ADVANTAGE application using
   * the parameters passed in the XML.
   *
   * @param fsInputXML The XML input containing the session identifier and the action parameters and input
   * @return the XML output containing the messages and the output
   */
   public String executeAction(String fsInputXML)
   {
      return performAction( fsInputXML, true ) ;
   }

  /**
   * This method performs a query or action within the ADVANTAGE application using
   * the parameters passed in the XML.
   *
   * @param fsInputXML The XML input containing the session identifier and the action parameters and input
   * @return the XML output containing the messages and the output
   * @param Please use executeAction
   */
   public String performAction(String fsInputXML)
   {
      return performAction( fsInputXML, false ) ;
   }

  /**
   * This method performs a query or action within the ADVANTAGE application using
   * the parameters passed in the XML.
   *
   * @param fsInputXML The XML input containing the session identifier and the action parameters and input
   * @return the XML output containing the messages and the output
   */
   public String performAction(String fsInputXML, boolean fboolXMLSchemaAware )
   {
      int liJobRetCode;
      InputStream loInputStream;
      InputStream loDataInputStream;
      SysManUtil loSmu;
      boolean    lboolDocTag = false ;

      try
      {
         mboolXMLSchemaAware = fboolXMLSchemaAware ;
         processXML(fsInputXML);

         if (moSession == null)
         {
            writeMsgToXML("AUTHENTICATION FAILED: INVALID SESSION", 3, -1);
            return flushXMLDocument();
         }
         else
         {
            loInputStream = new ByteArrayInputStream(fsInputXML.getBytes());

            // create a new SysManUtil instance and then do the processing
            loSmu = new SysManUtil( moSession, moSession.getSessionID() + "-"
                  + Long.toString( System.currentTimeMillis() ) );
            loSmu.setProperties( loInputStream ) ;
            loSmu.setExceptionReportLayoutType( SysManUtil.EXP_RPT_XML) ;

            /*
             * Set to true if xmls to be generated should have
             * schema information
             */
            loSmu.setXMLSchemaAwareness( mboolXMLSchemaAware ) ;


            // set the input file stream in SysManUtil
            switch (miActionCd)
            {
               // Document actions
               case DOC_ACTN_DEACTIVATE :
               case DOC_ACTN_ACTIVATE :
               case DOC_ACTN_EDIT :
               case DOC_ACTN_DISCARD :
               case DOC_ACTN_VALIDATE :
               case DOC_ACTN_SUBMIT :
               case DOC_ACTN_IMPORT :
               case DOC_ACTN_DOC_HOLD :
               case DOC_ACTN_DOC_READY :
               case DOC_ACTN_OTHER :
               case DOC_ACTN_ARCHIVE :
               case DOC_ACTN_ARCHIVE_HIST :
               case DOC_ACTN_UNARCHIVE :
               case DOC_ACTN_REJECT_ALL :
               case DOC_ACTN_IMPORT_LATEST :
               case DOC_ACTN_DISCARD_LATEST :

               //table actions
               case TBL_IMPORT :
               case TBL_UPDATE :
               case TBL_OVERLAY :
               case TBL_DELETE :
               {
                  loDataInputStream = new ByteArrayInputStream(fsInputXML.getBytes());

                  loSmu.setInputFileStream(loDataInputStream);
                  break;
               }
            }

            if ( miActionCd == DOC_ACTN_REJECT_ALL || miActionCd == DOC_ACTN_IMPORT_LATEST ||
                 miActionCd == DOC_ACTN_DISCARD_LATEST )
            {
               moSession.setProperty( AMS_APRV_LVL, INTEGRATION_APRV_LVL );
            }

            // call startProcess of SysManUtil
            liJobRetCode = loSmu.startProcess();

            //set the XML document version and header
            startXMLDocument();

            //check for return code
            if(liJobRetCode == RET_CODE_FAILED ||
               liJobRetCode == RET_CODE_NON_FATAL_ERROR)
            {
               setXMLHeader(msHeaderType, msRequestor, msRecipient, -1);
            }
            else
            {
               setXMLHeader(msHeaderType, msRequestor, msRecipient, 0);
            }

            moXMLToFlush.append("<BODY>\n");

            switch (miActionCd)
            {
               case DOC_ACTN_IMPORT_LATEST :
               case DOC_ACTN_DISCARD_LATEST:
                     lboolDocTag = writeAMSDocumentTag( loSmu.getDocumentInformation() ) ;
                  break ;
               default :
                  break ;
             }

            if (liJobRetCode == RET_CODE_SUCCESSFUL)
            {
               moXMLToFlush.append("<AMS_MESSAGE Severity=\"0");
               moXMLToFlush.append("\"><![CDATA[");
               moXMLToFlush.append("PERFORM ACTION WAS SUCCESSFUL");
               moXMLToFlush.append("]]></AMS_MESSAGE>\n");
            }
            else if (liJobRetCode == RET_CODE_FAILED)
            {
               moXMLToFlush.append("<AMS_MESSAGE Severity=\"3");
               moXMLToFlush.append("\"><![CDATA[");
               moXMLToFlush.append("ERROR IN PROCESSING ACTION");
               moXMLToFlush.append("]]></AMS_MESSAGE>\n");
            }

            // Write the log for all actions
            appendSMULogOutput(loSmu);

            switch (miActionCd)
            {
               // Document actions
               case DOC_ACTN_DEACTIVATE :
               case DOC_ACTN_ACTIVATE :
               case DOC_ACTN_EDIT :
               case DOC_ACTN_DISCARD :
               case DOC_ACTN_DISCARD_LATEST:
               case DOC_ACTN_REJECT_ALL :
               {
                  appendSMUErrorOutput(loSmu);
                  break;
               }

               case DOC_ACTN_VALIDATE :
               case DOC_ACTN_SUBMIT :
               {
                  appendSMUExceptionReport(loSmu);
                  break;
               }

               case DOC_ACTN_IMPORT :
               case DOC_ACTN_IMPORT_LATEST :
               case DOC_ACTN_DOC_HOLD :
               case DOC_ACTN_DOC_READY :
               case DOC_ACTN_OTHER :
               {
                  appendSMUErrorOutput(loSmu);
                  break;
               }

               case DOC_ACTN_ARCHIVE :
               case DOC_ACTN_ARCHIVE_HIST :
               case DOC_ACTN_UNARCHIVE :
               case DOC_ACTN_EXPORT :
               {
                  appendSMUExportOutput(loSmu);
                  break;
               }

               // Table actions
               case TBL_IMPORT :
               case TBL_UPDATE :
               case TBL_OVERLAY :
               case TBL_DELETE :
               {
                  appendSMUErrorOutput(loSmu);
                  break;
               }

               case TBL_EXPORT :
               {
                  appendSMUExportOutput(loSmu);
                  appendSMUErrorOutput(loSmu);
                  break;
               }

               case TBL_PURGE :
               {
                  break;
               }
               default :
               {
                  writeMsgToXML("INVALID ACTION ID", 3, -1);
                  return flushXMLDocument();

               }

            }// end of switch statement

            switch (miActionCd)
            {
               case DOC_ACTN_IMPORT_LATEST :
               case DOC_ACTN_DISCARD_LATEST:
                  if ( lboolDocTag )
                  {
                     writeEndAMSDocumentTag() ;
                  } /* end if ( lboolDocTag ) */
                  break ;
               default :
                  break ;
             }

            // set the end body tag and end of XML document
            moXMLToFlush.append("\n</BODY>\n");
            endXMLDocument();

            return flushXMLDocument();
         }
      }
      catch(AMSXMLImportExportException foAMSExpImpExp)
      {
         moLog.error( "Exception: ", foAMSExpImpExp ) ;

         writeMsgToXML(foAMSExpImpExp.getMessage(),
                        foAMSExpImpExp.getErrorType(), -1);
         return flushXMLDocument();
      }
      catch(Exception foExp)
      {
         moLog.error( "Exception: ", foExp ) ;

         writeMsgToXML("ERROR IN PROCESSING ACTION", 3, -1);
         return flushXMLDocument();
      }

   }// end of performAction()

  /**
   * This method returns a complete list of event notifications of one or
   * more reference tables or documents. Each description includes all
   * fields within the table(s), and a list of updates performed.
   *
   * @return the XML output containing the reference table or document notification(s) or error messages
   */
   public String getEvents()
   {
      mboolXMLSchemaAware = true ;
      return retrieveEvents() ;
   } // end of getEvents()

  /**
   * This method returns a list of all document or reference table types defined
   * within the ADVANTAGE application.
   *
   * @param fsInputXML The XML input containing the session identifier
   * @param fsType The type whether document or reference table
   * @return The XML output containing the document type list or error messages
   */
   private String getList(String fsInputXML, String fsType)
   {
      InputStream loInputStream;
      AMSMetaDataToXML loMetaToXML;
      ByteArrayOutputStream loByteOutStream;
      //Session loSession = null;

      try
      {
         processXML(fsInputXML);

         if (moSession == null)
         {
            writeMsgToXML("AUTHENTICATION FAILED: INVALID SESSION", 3, -1);
            return flushXMLDocument();
         }
         else
         {
            //set the XML document version and header
            startXMLDocument();
            setXMLHeader(msHeaderType, msRequestor, msRecipient, 0);
            moXMLToFlush.append("<BODY>\n");

            loInputStream = new ByteArrayInputStream(fsInputXML.getBytes());

            // create a new SysManUtil instance and then do the processing
            loMetaToXML = new AMSMetaDataToXML(moSession);
            loMetaToXML.setXMLSchemaAwareness( mboolXMLSchemaAware ) ;

            if (fsType.equals(DOCUMENT))
            {
               loMetaToXML.createDocumentList();
            }
            else if (fsType.equals(REFERENCE_TBL))
            {
               loMetaToXML.createRefTableList();
            }

            loByteOutStream = new ByteArrayOutputStream();
            loMetaToXML.writeXML(loByteOutStream);
            loMetaToXML.cleanUpResources();
            appendToXML(loByteOutStream);

            // set the end body tag and end of XML document
            moXMLToFlush.append("\n</BODY>\n");
            endXMLDocument();
            loByteOutStream.close();

            return flushXMLDocument();
         }

      }
      catch(AMSXMLImportExportException foAMSExpImpExp)
      {
         moLog.error( "Exception: ", foAMSExpImpExp ) ;

         writeMsgToXML(foAMSExpImpExp.getMessage(),
                        foAMSExpImpExp.getErrorType(), -1);
         return flushXMLDocument();
      }
      catch(Exception foExp)
      {
         moLog.error( "Exception: ", foExp ) ;

         writeMsgToXML("ERROR IN PROCESSING ACTION", 3, -1);
         return flushXMLDocument();
      }

   }// end of getList()

   /**
    * This method creates a SAX parser instance and attempts
    * to parse the specified XML file.
    *
    * @param fsInputXML The XML String to import
    * @exception AMSXMLImportExportException
    */
   private void processXML(String fsInputXML)
                 throws AMSXMLImportExportException
   {
      byte[] lbInputBytes;
      SAXParserFactory loSAXFactory;
      SAXParser loSAXParser;
      Parser loParser;
      InputSource loXMLInput;

      try
      {
         lbInputBytes = fsInputXML.getBytes();
         moInputXMLStream = new ByteArrayInputStream(lbInputBytes);

         loSAXFactory = SAXParserFactory.newInstance() ;
         loSAXFactory.setValidating( false ) ;

         loSAXParser = loSAXFactory.newSAXParser() ;
         loParser = loSAXParser.getParser() ;

         loParser.setDocumentHandler( this ) ;
         loParser.setErrorHandler( new MyErrorHandler() ) ;

         loXMLInput = new InputSource( moInputXMLStream ) ;

         loParser.parse( loXMLInput ) ;
      }
      catch ( SAXParseException foExp )
      {
         moLog.error( "Exception: ", foExp ) ;

         throw new AMSXMLImportExportException(
            "Encountered a exception while parsing: " + foExp.getMessage(),
            AMSXMLImportExportException.PARSE_ERROR) ;
      }
      catch ( SAXException loSAXExcep )
      {
         moLog.error( "Exception: ", loSAXExcep ) ;
         throw new AMSXMLImportExportException(
            "Encountered a SAX exception: " + loSAXExcep.getMessage(),
            AMSXMLImportExportException.SEVERE_ERROR) ;
      }
      catch ( ParserConfigurationException loParseExcep )
      {
         moLog.error( "Exception: ", loParseExcep ) ;
         throw new AMSXMLImportExportException(
            "Encountered a parser configuration exception: " +
                loParseExcep.getMessage(),
            AMSXMLImportExportException.PARSE_ERROR) ;
      }
      catch ( IOException loIOExcep )
      {
         moLog.error( "Exception: ", loIOExcep ) ;
         throw new AMSXMLImportExportException(
            "Encountered an IO exception while parsing: " +
                loIOExcep.getMessage(),
            AMSXMLImportExportException.FILE_ERROR) ;
      }
   }

      /**
    * SAX XML parser event handler.
    */
   public void setDocumentLocator( Locator l )
   {
    /**
      * we'd record this if we needed to resolve relative URIs
      * in content or attributes, or wanted to give diagnostics.
      */
   }

   /**
    * SAX XML parser event handler.
    * @exception SAXException
    */
   public void startDocument()
      throws SAXException
   {

   }

   /**
    * SAX XML parser event handler.
    * @exception SAXException
    */
   public void endDocument()
      throws SAXException
   {

   }

   /**
    * SAX XML parser event handler.
    * @exception SAXException
    */
   public void startElement( String fsTagName, AttributeList foAttrList )
      throws SAXException
   {
      if (fsTagName.equals(HEADER_TAG))
      {
         msRequestor = foAttrList.getValue(REQUESTOR_TAG);
         msRecipient = foAttrList.getValue(RECIPIENT_TAG);
      }
      else if (fsTagName.equals(DFLT_ACTION_ATTR))
      {
         String lsParamValue = foAttrList.getValue(PARAMETER_TAG);

         if (lsParamValue != null && lsParamValue.equals("Y"))
         {
            mboolIsActionTag = true;
         }
         else
         {
            mboolIsActionTag = false;
         }
      }

   }

   /**
    * SAX XML parser event handler.
    * @exception SAXException
    */
   public void endElement( String fsTagName )
      throws SAXException
   {

   }


   /**
    * SAX XML parser event handler.
    * @exception SAXException
    */
   public void characters( char buf[], int offset, int len )
      throws SAXException
   {
      String lsTempData = new String(buf, offset, len).trim();

      if (!lsTempData.equals("") && mboolIsActionTag)
      {
         try
         {
            miActionCd = Integer.parseInt(lsTempData);
            mboolIsActionTag = false;
         }
         catch(NumberFormatException foExp)
         {
             moLog.error( "Exception: ", foExp ) ;
         }
         catch(Exception foExp)
         {
             moLog.error( "Exception: ", foExp ) ;
         }

      }

   }// end of characters()

   /**
    * SAX XML parser event handler.
    * @exception SAXException
    */
   public void ignorableWhitespace( char buf [], int offset, int len )
      throws SAXException
   {
      /**
       * Just ignore the whitespace, we don't need it
       */
      return ;
   }


   /**
    * SAX XML parser event handler.
    * @exception SAXException
    */
   public void processingInstruction( String target, String data )
      throws SAXException
   {
      /**
       * Implement this method if processing instructions are required
       */
      return ;
   }

   /**
    * This inner class handles any validation errors
    * and warnings
    */
   public class MyErrorHandler extends HandlerBase
   {
      /**
       * treat validation errors as fatal
       */
      public void error( SAXParseException e )
         throws SAXParseException
      {
         throw e ;
      }

      /**
       * dump warnings too
       */
      public void warning( SAXParseException err )
         throws SAXParseException
      {
         System.err.println( "** Warning" +
                             ", line " + err.getLineNumber() +
                             ", uri "  + err.getSystemId() ) ;
         System.err.println( "   " + err.getMessage() ) ;
      }
   }// end of MyErrorHandler

   /**
    * This method is used to get the version number for the XML file.
    */
   private String getVersion()
   {
      return "1.0";
   }// end of getVersion()

   /**
    * This method is used to get the signature for the XML file.
    */
   private String getName()
   {
      return "AMS_INTEGRATION_MESSAGE" ;
   }// end of getName()

   /**
    * This method is used to write the XML tag for the start of the
    * XML document
    */
   private void startXMLDocument()
   {
       moXMLToFlush = new StringBuffer(4096);
       moXMLToFlush.append("<?xml version=\"1.0\"");

       /*
        * add character encoding if specified for the
        * application
        */
       if (AMSParams.msXMLExportCharacterEncoding !=null &&
           AMSParams.msXMLExportCharacterEncoding.trim().length() > 0)
       {
          moXMLToFlush.append(" encoding=\"" + AMSParams.msXMLExportCharacterEncoding +
                              "\"");
       }

       moXMLToFlush.append(" ?>");
       moXMLToFlush.append("\n");
       moXMLToFlush.append("<");
       moXMLToFlush.append(getName());

       /*
        * Add namespace information if xmls generated need
        * to be schema aware
        */
       if ( mboolXMLSchemaAware )
       {
          addNameSpace() ;
       } /* end if ( mboolXMLSchemaAware ) */

       moXMLToFlush.append(">\n");

   }// end of startXMLDocument()

   /**
    * This method is used to write the XML tag for the header of the
    * XML document
    */
   private void setXMLHeader(String fsType, String fsRequestor,
                     String fsRecipient, int fiReturnCode)
   {
       moXMLToFlush.append("<HEADER TYPE=\"");
       moXMLToFlush.append(fsType);
       moXMLToFlush.append("\" DATE=\"");
       moXMLToFlush.append((new VSDate()).toString());
       moXMLToFlush.append("\" REQUESTOR=\"");
       moXMLToFlush.append(fsRequestor);
       moXMLToFlush.append("\" RECIPIENT=\"");
       moXMLToFlush.append(fsRecipient);
       moXMLToFlush.append("\" RETURN_CODE=\"");
       moXMLToFlush.append(String.valueOf(fiReturnCode));

       moXMLToFlush.append("\"></HEADER>\n");
   }// end of setXMLHeader()

   /**
    * This method is used to write the XML tag for the end of the
    * XML document
    */
   private void endXMLDocument()
   {
      moXMLToFlush.append("</");
      moXMLToFlush.append(getName());
      moXMLToFlush.append(">");

   }// end of endXMLDocument()

   /**
    * This method is used to write the XML to a String and return
    * XML document as a String
    */
   private String flushXMLDocument()
   {
      String loXMLDoc = null;

      if(moXMLToFlush != null)
      {
         loXMLDoc = moXMLToFlush.toString();
      }

      resetValues();
      return loXMLDoc;

   }// end of flushXMLDocument()

   /**
    * This method is used to reset all values. Called from flushXMLDocument().
    */
   private void resetValues()
   {
      mboolIsActionTag  = false ;

     /**
       * The header properties
       */
      msHeaderType = "Reply" ;
      msRequestor = "" ;
      msRecipient = "" ;
      miActionCd = 0 ;
      moXMLToFlush = null ;

   }// end of flushXMLDocument()

  /**
   * This method is used to write any message generated, and send the message
   * back as XML.  Anything written to moXMLToFlush before this is deleted and
   * only the message is written to moXMLToFlush.
   *
   * @param fsMsg The message.
   * @param fiSeverity The message severity.
   * @param fiReturnCode The message return code.
   */
   private void writeMsgToXML(String fsMsg, int fiSeverity, int fiReturnCode)
   {
         startXMLDocument();
         setXMLHeader(msHeaderType, msRequestor, msRecipient, fiReturnCode);
         moXMLToFlush.append("<BODY>\n");
         moXMLToFlush.append("<AMS_MESSAGE Severity=\"");
         moXMLToFlush.append(String.valueOf(fiSeverity));
         moXMLToFlush.append("\"><![CDATA[");
         moXMLToFlush.append(fsMsg);
         moXMLToFlush.append("]]></AMS_MESSAGE>\n");
         moXMLToFlush.append("</BODY>\n");
         endXMLDocument();

   }// end of writeMsgToXML()

  /**
   * This method appends the log output from the Sys Man Util and adds
   * the XML to moXMLToFlush.
   *
   * @param foSmu The Sys Man Util instance.
   */
   private void appendSMULogOutput(SysManUtil foSmu)
   {
      ByteArrayOutputStream loByteOutStream;

      if(foSmu == null)
      {
         return;
      }
      else
      {
         try
         {
            loByteOutStream = new ByteArrayOutputStream();
            foSmu.getLogOutput(loByteOutStream);
            appendToXML(loByteOutStream);
            loByteOutStream.close();
         }
         catch(IOException foExp)
         {
            moLog.error( "IOException: ", foExp ) ;
         }
         catch(Exception foExp)
         {
            moLog.error( "Exception: ", foExp ) ;
         }
      }
   }// end of appendSMULogOutput()

  /**
   * This method appends the exception report from the Sys Man Util and adds
   * the XML to moXMLToFlush.
   *
   * @param foSmu The Sys Man Util instance.
   */
   private void appendSMUExceptionReport(SysManUtil foSmu)
   {
      ByteArrayOutputStream loByteOutStream;

      if(foSmu == null)
      {
         return;
      }
      else
      {
         try
         {
            loByteOutStream = new ByteArrayOutputStream();
            foSmu.getExceptionReport(loByteOutStream);
            appendToXML(loByteOutStream);
            loByteOutStream.close();
         }
         catch(IOException foExp)
         {
            moLog.error( "Exception: ", foExp ) ;
         }
         catch(Exception foExp)
         {
            moLog.error( "Exception: ", foExp ) ;
         }
      }

   }// end of appendSMUExceptionReport()

  /**
   * This method appends the error output from the Sys Man Util and adds
   * the XML to moXMLToFlush.
   *
   * @param foSmu The Sys Man Util instance.
   */
   private void appendSMUErrorOutput(SysManUtil foSmu)
   {
      ByteArrayOutputStream loByteOutStream;

      if(foSmu == null)
      {
         return;
      }
      else
      {
         try
         {
            loByteOutStream = new ByteArrayOutputStream();
            foSmu.getErrorOutput(loByteOutStream);
            appendToXML(loByteOutStream);
            loByteOutStream.close();
         }
         catch(IOException foExp)
         {
            moLog.error( "Exception: ", foExp ) ;
         }
         catch(Exception foExp)
         {
            moLog.error( "Exception: ", foExp ) ;
         }
      }

   }// end of appendSMUErrorOutput()

  /**
   * This method appends the export output from the Sys Man Util and adds
   * the XML to moXMLToFlush.
   *
   * @param foSmu The Sys Man Util instance.
   */
   private void appendSMUExportOutput(SysManUtil foSmu)
   {
      ByteArrayOutputStream loByteOutStream;

      if(foSmu == null)
      {
         return;
      }
      else
      {
         try
         {
            loByteOutStream = new ByteArrayOutputStream();
            foSmu.getExportOutput(loByteOutStream);
            appendToXML(loByteOutStream);
            loByteOutStream.close();
         }
         catch(IOException foExp)
         {
            moLog.error( "Exception: ", foExp ) ;
         }
         catch(Exception foExp)
         {
            moLog.error( "Exception: ", foExp ) ;
         }
      }
   }//end of appendSMUExportOutput()

  /**
   * This method removes the prolog (if any) from the XML output stream and appends
   * the XML to moXMLToFlush.
   *
   * @param foByteOutStream The output stream from Sys Man Util.
   */
   private void appendToXML(ByteArrayOutputStream foByteOutStream)
   {
      String lsXMLToAppend;
      int liPrologStart;
      int liPrologEnd;

      if (foByteOutStream == null)
      {
         return;
      }
      else
      {
         lsXMLToAppend = foByteOutStream.toString();
         liPrologStart = lsXMLToAppend.indexOf("<?xml");

         if (liPrologStart != -1)
         {
            liPrologEnd = lsXMLToAppend.indexOf("?>");
            lsXMLToAppend = lsXMLToAppend.substring(liPrologEnd + 2);
         }

         moXMLToFlush.append(lsXMLToAppend.trim());
      }
   }// end of appendToXML()

  /**
   * This method abstracts out the logic for retrieving
   * notifications
   *
   * @return the xml document  .
   */
  private String retrieveEvents()
  {
      AMSNotificationDataToXML loNotifyToXML;
      ByteArrayOutputStream loByteOutStream;
      Session loSession = null;

      try
      {
         if (moSession == null)
         {
            writeMsgToXML("AUTHENTICATION FAILED: INVALID SESSION", 3, -1);
            return flushXMLDocument();
         }

         //set the XML document version and header
         startXMLDocument();
         setXMLHeader(msHeaderType, msRequestor, msRecipient, 0);
         moXMLToFlush.append("<BODY>\n");

         // create a new SysManUtil instance and then do the processing
         loNotifyToXML = new AMSNotificationDataToXML(moSession);

         if ( mboolXMLSchemaAware )
         {
            loNotifyToXML.createEventNodes();
         } /* end if ( mboolXMLSchemaAware ) */
         else
         {
            loNotifyToXML.createNotificationNodes();
         } /* end else */

         loByteOutStream = new ByteArrayOutputStream();
         loNotifyToXML.writeXML(loByteOutStream);
         loNotifyToXML.cleanUpResources();
         appendToXML(loByteOutStream);

         // set the end body tag and end of XML document
         moXMLToFlush.append("\n</BODY>\n");
         endXMLDocument();
         loByteOutStream.close();

         return flushXMLDocument();

      }
      catch(AMSXMLImportExportException foAMSExpImpExp)
      {
         // Add exception log to logger object
         moLog.error("Unexpected error encountered while processing. ", foAMSExpImpExp);


         writeMsgToXML(foAMSExpImpExp.getMessage(),
                        foAMSExpImpExp.getErrorType(), -1);
         return flushXMLDocument();
      }
      catch(Exception foExp)
      {
         moLog.error( "Exception: ", foExp ) ;

         writeMsgToXML("ERROR IN PROCESSING ACTION", 3, -1);
         return flushXMLDocument();
      }
  } /* end retrieveEvents() */

   /**
    * Method to create the namespace to be used
    */
   private void addNameSpace()
   {
      moXMLToFlush.append( " xmlns=\"" ) ;
      moXMLToFlush.append( ADV_NAMESPACE_PREFIX ) ;
      moXMLToFlush.append( "IntegrationMessage" ) ;
      moXMLToFlush.append( "\" " ) ;
   } /* end getNameSpace() */

   /**
    * Method to write the AMSDocument Tag
    *
    * @param boolean true if component found
    */
   public boolean writeAMSDocumentTag( Hashtable foDocInfo )
         throws IOException
   {
      String lsDocID ;

      if ( ( foDocInfo == null ) || ( foDocInfo.size() <= 0 ) )
      {
         return false ;
      } /* end if */


      lsDocID = (String) foDocInfo.get(ATTR_DOC_ID) ;

      /**
       * Escape any special characters in DOC_ID
       */
      lsDocID = AMSUtil.escapeSpecialChars( lsDocID );

      /**
       * Start a new document node
       */
      moXMLToFlush.append("<").append(AMS_DOCUMENT).append(" ");
      moXMLToFlush.append(ATTR_DOC_CAT);
      moXMLToFlush.append("=\"");
      moXMLToFlush.append((String) foDocInfo.get(ATTR_DOC_CAT));
      moXMLToFlush.append("\" ");
      moXMLToFlush.append(ATTR_DOC_TYP);
      moXMLToFlush.append("=\"");
      moXMLToFlush.append((String) foDocInfo.get(ATTR_DOC_TYP));
      moXMLToFlush.append("\" ");
      moXMLToFlush.append(ATTR_DOC_CD);
      moXMLToFlush.append("=\"");
      moXMLToFlush.append((String) foDocInfo.get(ATTR_DOC_CD));
      moXMLToFlush.append("\" ");
      moXMLToFlush.append(ATTR_DOC_DEPT_CD);
      moXMLToFlush.append("=\"");
      moXMLToFlush.append((String) foDocInfo.get(ATTR_DOC_DEPT_CD));
      moXMLToFlush.append("\" ");
      moXMLToFlush.append(ATTR_DOC_UNIT_CD);
      moXMLToFlush.append("=\"");
      moXMLToFlush.append((String) foDocInfo.get(ATTR_DOC_UNIT_CD));
      moXMLToFlush.append("\" ");
      moXMLToFlush.append(ATTR_DOC_ID);
      moXMLToFlush.append("=\"");
      moXMLToFlush.append(lsDocID);
      moXMLToFlush.append("\" ");
      moXMLToFlush.append(ATTR_DOC_VERS_NO);
      moXMLToFlush.append("=\"");
      moXMLToFlush.append((String) foDocInfo.get(ATTR_DOC_VERS_NO));
      moXMLToFlush.append( "\">\n" ) ;

      return true ;

   } /* end writeAMSDocumentTag() */

   /**
    * Method to write the AMSDocument end Tag
    *
    * @param boolean true if component found
    */
   public void writeEndAMSDocumentTag()
         throws IOException
   {
      moXMLToFlush.append("\n</");
      moXMLToFlush.append(AMS_DOCUMENT);
      moXMLToFlush.append(">\n");
   } /* end writeEndAMSDocumentTag() */
}

