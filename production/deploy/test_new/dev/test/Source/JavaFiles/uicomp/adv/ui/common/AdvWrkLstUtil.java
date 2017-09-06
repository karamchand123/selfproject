/*
 * @(#)AdvWrkLstUtil.java Sept 16, 2009
 *
 * Copyright 2009 by CGI Technologies And Solutions, Inc., U.S.A.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of CGI
 * Technologies And Solutions, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in accordance
 * with the terms of the license agreement you entered into with CGI
 * Technologies And Solutions, Inc.
 */
package adv.ui.common;

import org.apache.commons.logging.Log;

import versata.common.DataConst;
import versata.common.SearchRequest;
import versata.common.VSDate;
import versata.common.VSException;
import versata.common.VSORBSession;
import versata.common.VSSession;
import versata.vfc.VSData;
import versata.vfc.VSQuery;
import versata.vfc.VSResultSet;
import versata.vfc.VSRow;
import advantage.AMSStringUtil;

import com.amsinc.gems.adv.common.AMSCommonConstants;
import com.amsinc.gems.adv.common.AMSLogConstants;
import com.amsinc.gems.adv.common.AMSLogger;
import com.amsinc.gems.adv.common.AMSSQLUtil;
import com.amsinc.gems.adv.vfc.html.AMSDataSource;
import com.amsinc.gems.adv.vfc.html.AMSMsgDivElement;
import com.amsinc.gems.adv.vfc.html.AMSPage;

/**
 * AdvMSSGeneralServices provides API service methods that are used by the MSS application.
 */

public class AdvWrkLstUtil implements AdvUIConstants
{
   private static Log moLog = AMSLogger.getLog( AdvWrkLstUtil.class, AMSLogConstants.FUNC_AREA_MSS_PLS_SERVICES ) ;

   AdvWidget moWidget = null;

   private static final String C_LOCK_USID_ATTR = "LOCK_USID";
   private static final String C_ASSIGNEE_FL_ATTR = "ASSIGNEE_FL";
   private static final String C_ASSIGNEE_ATTR = "ASSIGNEE";
   private static final String C_USID_ATTR = "USID";
   private static final String C_ROLEID_ATTR = "ROLEID";
   private static final String C_ROLE_MGR_FL_ATTR = "ROLE_MGR_FL";


   public AdvWrkLstUtil(AdvWidget foWidget)
   {
      moWidget = foWidget;
   }

   /*
    * Inserts a row in DOC_CMNT based on the input parameters
    */
   public boolean addDocCmnt(String fsDocCD, String fsDeptCD, String fsDocId, String fsDocVersnNo,
                             String fsDocCmnt, AMSPage foPage)
   {
      boolean lboolAddDocCmntStatus = false;


      String lsErrMsg = null;

      SearchRequest loSRDocCmnt = null;
      SearchRequest loSRDocHdr = null;
      VSQuery loQryDocCmnt = null;
      VSQuery loQryDocHdr = null;
      VSResultSet loRSDocCmnt = null;
      VSResultSet loRSDocHdr = null;
      VSRow loRowDocCmnt = null;
      VSRow loRowDocHdr = null;

      StringBuffer lsbWhereClause = new StringBuffer();

      if (moLog.isDebugEnabled())
      {
         moLog.debug("addDocCmnt");
      }

      loSRDocCmnt = new SearchRequest();
      loSRDocCmnt.addParameter("DOC_CMNT", "DOC_CD",        fsDocCD);
      loSRDocCmnt.addParameter("DOC_CMNT", "DOC_DEPT_CD",   fsDeptCD);
      loSRDocCmnt.addParameter("DOC_CMNT", "DOC_ID",        fsDocId);
      loSRDocCmnt.addParameter("DOC_CMNT", "DOC_VERS_NO",   fsDocVersnNo);

      loQryDocCmnt = new VSQuery(foPage.getParentApp().getSession(), "DOC_CMNT", loSRDocCmnt, null);
      loRSDocCmnt = loQryDocCmnt.execute();

      if (loRSDocCmnt != null)
      {
         //Get Handle to Document Header, so that document phase can be used for the new Doc Cmnt Row to be inserted
         loSRDocHdr = new SearchRequest();
         loSRDocHdr.addParameter("DOC_HDR", "DOC_CD",       fsDocCD);
         loSRDocHdr.addParameter("DOC_HDR", "DOC_DEPT_CD",  fsDeptCD);
         loSRDocHdr.addParameter("DOC_HDR", "DOC_ID",       fsDocId);
         loSRDocHdr.addParameter("DOC_HDR", "DOC_VERS_NO",  fsDocVersnNo);

         loQryDocHdr = new VSQuery(foPage.getParentApp().getSession(), "DOC_HDR", loSRDocHdr, null);
         loRSDocHdr = loQryDocHdr.execute();

         if (loRSDocHdr != null)
         {
            loRowDocHdr = loRSDocHdr.last();
         }
      }

      //Insert a new Doc Comment Row and populate it using input parameters and the document header information
      if (loRSDocCmnt != null && loRowDocHdr != null)
      {
         loRSDocCmnt.last();
         loRowDocCmnt = loRSDocCmnt.insert();

         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_CD).setString(fsDocCD);
         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_DEPT_CD).setString(fsDeptCD);
         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_ID).setString(fsDocId);
         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_VERS_NO).setString(fsDocVersnNo);
         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_PHASE_CD).setString(loRowDocHdr.getData(AMSCommonConstants.ATTR_DOC_PHASE_CD).getString());

         loRowDocCmnt.getData("CMNT_MSG").setString(fsDocCmnt);
         loRowDocCmnt.getData("CMNT_SUB").setString("Doc Actioned");
         if(AMSStringUtil.strInsensitiveEqual(foPage.getParentApp().getAppName(), C_APPL_NM_MSS))
         {
        	 loRowDocCmnt.getData("CMNT_SUB").setString("MSS Comment");
         }
        

         try
         {
            loRSDocCmnt.updateDataSource() ;
            lboolAddDocCmntStatus = true;
         }
         catch( VSException loExp )
         {
             // Add exception log to logger object
             moLog.error("Unexpected error encountered while processing. ", loExp);


            if (moLog.isErrorEnabled())
            {
               moLog.error("\t Not able to add comments addDocCmnt. " +loExp.getMessage());
            }
            moWidget.raiseException("Not able to add comments. Please contact the System Adminstrator.", moWidget.SEVERITY_ERROR);
            lboolAddDocCmntStatus = false;
         }
         finally
         {
            //If Add Comment step fails due to document action related errors, don't post the error message here.
            //Instead just set the return status to false
            lsErrMsg = extractHighestErrorMsg();
            if (lsErrMsg != null && !lsErrMsg.isEmpty())
            {
               lboolAddDocCmntStatus = false;
            }
            loRSDocCmnt.close() ;
         }
      }

      if (moLog.isDebugEnabled())
      {
         moLog.debug("\t addDocCmnt lboolAddDocCmntStatus = "+lboolAddDocCmntStatus);
         moLog.debug("END addDocCmnt");
      }


      return lboolAddDocCmntStatus;
   }//end addDocCmnt

   /*
    * Inserts a row in DOC_CMNT based on the input parameters overloaded method
    * added for Document Rejection Comments ER
    */
   public boolean addDocCmnt(String fsDocCD, String fsDeptCD, String fsDocId,
         String fsDocVersnNo, String fsDocSub, String fsDocCmnt,
         String fsDocPhaseCode, AMSPage foPage)
   {
      boolean lboolAddDocCmntStatus = false;
      SearchRequest loSRDocCmnt = null;
      VSQuery loQryDocCmnt = null;
      VSResultSet loRSDocCmnt = null;
      VSRow loRowDocCmnt = null;

      loSRDocCmnt = new SearchRequest();
      loSRDocCmnt.addParameter("DOC_CMNT", "DOC_CD", fsDocCD);
      loSRDocCmnt.addParameter("DOC_CMNT", "DOC_DEPT_CD", fsDeptCD);
      loSRDocCmnt.addParameter("DOC_CMNT", "DOC_ID", fsDocId);
      loSRDocCmnt.addParameter("DOC_CMNT", "DOC_VERS_NO", fsDocVersnNo);

      loQryDocCmnt = new VSQuery(foPage.getParentApp().getSession(),
            "DOC_CMNT", loSRDocCmnt, null);
      loRSDocCmnt = loQryDocCmnt.execute();

      // Insert a new Doc Comment Row and populate it using input parameters and
      // the document header information
      if (loRSDocCmnt != null)
      {
         loRSDocCmnt.last();
         loRowDocCmnt = loRSDocCmnt.insert();

         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_CD)
               .setString(fsDocCD);
         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_DEPT_CD).setString(
               fsDeptCD);
         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_ID)
               .setString(fsDocId);
         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_VERS_NO).setString(
               fsDocVersnNo);
         loRowDocCmnt.getData(AMSCommonConstants.ATTR_DOC_PHASE_CD).setString(
               fsDocPhaseCode);
         loRowDocCmnt.getData("CMNT_MSG").setString(fsDocCmnt);
         loRowDocCmnt.getData("CMNT_SUB").setString(fsDocSub);

         try
         {
            loRSDocCmnt.updateDataSource();
            lboolAddDocCmntStatus = true;
         }
         catch (VSException loExp)
         {
            // Add exception log to logger object
            moLog.error("Unexpected error encountered while processing. ",
                  loExp);

            if (moLog.isErrorEnabled())
            {
               moLog.error("\t Not able to add comments addDocCmnt. "
                     + loExp.getMessage());
            }

            lboolAddDocCmntStatus = false;
         }

      }

      if (moLog.isDebugEnabled())
      {
         moLog.debug("\t addDocCmnt lboolAddDocCmntStatus = "
               + lboolAddDocCmntStatus);
         moLog.debug("END addDocCmnt");
      }
      return lboolAddDocCmntStatus;
   }// end addDocCmnt

   /**
    * This method takes task (if assigned to work flow role instead of user), triggers the approval action.
    * On successful approval action, adds approval action comment (if specified) to the document
    * @param fsDocCD
    * @param fsDeptCD
    * @param fsDocId
    * @param fsDocVersnNo
    * @param fsDocActn
    * @param fsDocCmnt
    * @param foPage
    * @param fsDocHdrNm
    * @return
    */
   public boolean triggerApprovalAction(String fsDocCD, String fsDeptCD, String fsDocId, String fsDocVersnNo,
                                        String fsDocActn, String fsDocCmnt, AMSPage foPage, String fsDocHdrNm)
   {
      int liRowCount;

      boolean lboolAprvActnStatus = false;

      String lsErrMsg = null;

      VSRow loRowDocHdr = null;
      VSRow loWrkLstItemRow = null;
      VSResultSet loRSWrkLstItems = null;
      VSORBSession loORBSession = null;

      AMSDataSource loDSHdr = new AMSDataSource();
      StringBuffer lsbWhereClause = new StringBuffer();

      if (moLog.isDebugEnabled())
      {
         moLog.debug("triggerApprovalAction");
      }


      //Get handle to the document header specified
      lsbWhereClause.append(" DOC_CD = ");
      lsbWhereClause.append(AMSSQLUtil.getANSIQuotedStr(fsDocCD,true));
      lsbWhereClause.append(" AND DOC_DEPT_CD = ");
      lsbWhereClause.append(AMSSQLUtil.getANSIQuotedStr(fsDeptCD,true));
      lsbWhereClause.append(" AND DOC_ID = ");
      lsbWhereClause.append(AMSSQLUtil.getANSIQuotedStr(fsDocId,true));
      lsbWhereClause.append(" AND DOC_VERS_NO = "+fsDocVersnNo);

      loDSHdr.setName(fsDocHdrNm);
      loDSHdr.setSession( foPage.getParentApp().getSession() );
      loDSHdr.setQueryInfo(fsDocHdrNm, lsbWhereClause.toString(), "", "", false);
      loDSHdr.setPage(foPage);
      loDSHdr.setDataDependency(false, false);
      loDSHdr.setAllowInsert(true);
      loDSHdr.setAllowDelete(true);
      loDSHdr.setAllowUpdate(true);
      loDSHdr.setNumRowsPerPage(1);
      loDSHdr.setPreFetchRowCount(false);

      loDSHdr.setMaxRowsPerFetch(1);
      loDSHdr.setSaveMode(loDSHdr.SAVE_IMMEDIATE);
      loDSHdr.executeQuery();

      loRowDocHdr = loDSHdr.getCurrentRow();

      if (loRowDocHdr == null)
      {
         if (moLog.isErrorEnabled())
         {
            moLog.debug("\t triggerApprovalAction Not able to find the worklist item");
         }
         moWidget.raiseException("Not able to find the worklist item.", moWidget.SEVERITY_ERROR);

      }
      else
      {
         try
         {
            /*
             * Take the task (if assigned to Workflow role), trigger approval action and add comments (if any)
             */
            if (takeTask(fsDocCD, fsDeptCD, fsDocId, fsDocVersnNo, foPage))
            {
               //If work list is assigned to a role and take task step fails, don't proceed to document approval
               //Get handle to work list item and retrieve the approval level to be used for setting session property
               loRSWrkLstItems = getWrkLstItems(fsDocCD, fsDeptCD, fsDocId, fsDocVersnNo,foPage.getParentApp().getSession());
               if (loRSWrkLstItems!= null && loRSWrkLstItems.last() != null)
               {
                  liRowCount  =  loRSWrkLstItems.getRowCount();

                  if (moLog.isDebugEnabled())
                  {
                     moLog.debug("\t liRowCount = "+liRowCount);
                  }

                  loWrkLstItemRow = loRSWrkLstItems.getRowAt(liRowCount);

                  if (moLog.isDebugEnabled())
                  {
                     moLog.debug("\t triggerAction Approval Level = "+loWrkLstItemRow.getData("APRV_LVL").getString());
                  }
                  loORBSession = foPage.getParentApp().getSession().getORBSession();
                  loORBSession.setProperty( "ams_aprv_lvl", loWrkLstItemRow.getData("APRV_LVL").getString() );

                  if (AMSStringUtil.strEqual(fsDocActn, AdvUIConstants.C_DOC_ACTN_APRV))
                  {
                     loRowDocHdr.getData("DOC_ACTN_CD").setString(String.valueOf(AMSCommonConstants.DOC_ACTN_APPROVE));
                  }
                  else if (AMSStringUtil.strEqual(fsDocActn, AdvUIConstants.C_DOC_ACTN_REJT))
                  {
                     loRowDocHdr.getData("DOC_ACTN_CD").setString(String.valueOf(AMSCommonConstants.DOC_ACTN_REJECT));
                  }


                  if (moLog.isDebugEnabled())
                  {
                     moLog.debug("\t Saving DOC Header row with the doc action = "+fsDocActn);
                  }
                  loDSHdr.updateDataSource();

                  //Now add document comment
                  if(!AMSStringUtil.strIsEmpty(fsDocCmnt))
                  {
                     if (moLog.isDebugEnabled())
                     {
                        moLog.debug("\t Doc Header Save triggered. Now add Document Comments");
                     }

                     if (addDocCmnt(fsDocCD, fsDeptCD, fsDocId, fsDocVersnNo,fsDocCmnt, foPage))
                     {
                        lboolAprvActnStatus = true;
                        loRowDocHdr.getData("DOC_CMNT_FL").setBoolean(true);
                        loDSHdr.updateDataSource();
                     } // end if (!addDocCmnt(fsDocCD, fsDeptCD, fsDocId, fsDocVersnNo,fsDocCmnt, foPage))
                  }
                  else
                  {
                     lboolAprvActnStatus = true;
                  } // end if(!AMSStringUtil.strIsEmpty(fsDocCmnt))

               }// end if (loRSWrkLstItems!= null && loRSWrkLstItems.last() != null)
            }
         }
         catch (Exception loExp)
         {
            // Add exception log to logger object
            moLog.error("Unexpected error encountered while processing. ", loExp);


            lboolAprvActnStatus = false;
            loDSHdr.undo();
            loRSWrkLstItems.close();
            if (moLog.isErrorEnabled())
            {
               moLog.debug("\t triggerApprovalAction Not able to trigger approval action"+loExp.getMessage());
            }
            moWidget.raiseException("Not able to trigger approval action. Please contact the System Adminstrator.", moWidget.SEVERITY_ERROR);
         }
         finally
         {
            lsErrMsg = extractHighestErrorMsg();

            if (lsErrMsg != null && !lsErrMsg.isEmpty())
            {
               moWidget.raiseException(lsErrMsg);
               lboolAprvActnStatus = false;
            }
         }

      } // end if (loRowDocHdr == null)

      if (moLog.isDebugEnabled())
      {
         moLog.debug("\t triggerApprovalAction lboolAprvActnStatus = "+lboolAprvActnStatus);
         moLog.debug("END triggerApprovalAction");
      }

      return lboolAprvActnStatus;
   }// end triggerApprovalAction



   /*
    * This method checks if the user is the manager of a given workflow role
    * @param fsUserId the user id
    * @param fsRoleId the role id
    * @param foVSSession the Session object
    * @return true if the user is a manager of the role
    */
   private boolean isUserRoleMgr( String fsUserID, String fsRoleID, VSSession foVSSession )
   {
      StringBuffer   lsbWhereClause = new StringBuffer(50);
      boolean        lboolRoleMgr = false ;
      VSResultSet    loResultSet = null ;
      VSRow          loResultRow = null ;
      VSQuery        loQuery = null ;

      if ( ( fsUserID == null ) || ( fsRoleID == null ) || ( foVSSession == null ) )
      {
         return false;
      } /* end if ((fsUserId == null) || (foVSSession == null)) */

      lsbWhereClause.append( C_USID_ATTR ) ;
      lsbWhereClause.append(  " = " ) ;
      lsbWhereClause.append( AMSSQLUtil.getANSIQuotedStr(fsUserID,true) ) ;
      lsbWhereClause.append( " AND " ) ;
      lsbWhereClause.append( C_ROLEID_ATTR ) ;
      lsbWhereClause.append( " = " ) ;
      lsbWhereClause.append( AMSSQLUtil.getANSIQuotedStr(fsRoleID,true) ) ;


      loQuery = new VSQuery( foVSSession, "R_WF_USER_ROLE", lsbWhereClause.toString(), "" ) ;
      loResultSet = loQuery.execute() ;

      if ( loResultSet != null )
      {
         loResultRow = loResultSet.first() ;

         if ( loResultRow != null )
         {
            lboolRoleMgr = loResultRow.getData( C_ROLE_MGR_FL_ATTR ).getBoolean() ;
         }
         loResultSet.close();
      } /* end if ( loResultSet != null ) */

      return lboolRoleMgr ;
   } /* end isUserRoleMgr() */

   /**
    * This method checks if the work list is assigned to the user role and
    * then updates the locked user id with the logged in manager's user id.
    *
    * @param fsDocCD
    * @param fsDeptCD
    * @param fsDocId
    * @param fsDocVersnNo
    * @param foPage
    * @return
    */
   public boolean takeTask(String fsDocCD, String fsDeptCD, String fsDocId, String fsDocVersnNo, AMSPage foPage)
   {
      boolean lboolTakeTaskStatus = false;

      VSRow loRowWrkLst = null;

      VSData loLockUsid = null;
      VSData loAssigneeFlag = null;
      String lsLockUsid = null;
      String lsAssigneeRole = null;
      String lsErrMsg = null;

      AMSDataSource loDSWrkLst = new AMSDataSource();
      StringBuffer lsbWhereClause = new StringBuffer();

      if (moLog.isDebugEnabled())
      {
         moLog.debug("takeTask");
      }

      //Get handle to the Work List and see if it is assigned to the work flow role
      lsbWhereClause.append(" DOC_CD = ");
      lsbWhereClause.append(AMSSQLUtil.getANSIQuotedStr(fsDocCD,true));
      lsbWhereClause.append(" AND DOC_DEPT_CD = ");
      lsbWhereClause.append(AMSSQLUtil.getANSIQuotedStr(fsDeptCD,true));
      lsbWhereClause.append(" AND DOC_ID = ");
      lsbWhereClause.append(AMSSQLUtil.getANSIQuotedStr(fsDocId,true));
      lsbWhereClause.append(" AND DOC_VERS_NO = "+fsDocVersnNo);
      lsbWhereClause.append(" AND ASSIGNEE_FL = 1");

      loDSWrkLst.setName("WF_APRV_WRK_LST");
      loDSWrkLst.setSession( foPage.getParentApp().getSession() );
      loDSWrkLst.setQueryInfo("WF_APRV_WRK_LST", lsbWhereClause.toString(), "", "", false);
      loDSWrkLst.setPage(foPage);
      loDSWrkLst.setDataDependency(false, false);
      loDSWrkLst.setAllowInsert(true);
      loDSWrkLst.setAllowDelete(true);
      loDSWrkLst.setAllowUpdate(true);
      loDSWrkLst.setNumRowsPerPage(1);
      loDSWrkLst.setPreFetchRowCount(false);

      loDSWrkLst.setMaxRowsPerFetch(1);
      loDSWrkLst.setSaveMode(loDSWrkLst.SAVE_IMMEDIATE);
      loDSWrkLst.executeQuery();

      loRowWrkLst = loDSWrkLst.getCurrentRow();

      if (loRowWrkLst == null)
      {
         if (moLog.isDebugEnabled())
         {
            moLog.debug("\t Worklist Item not assigned to any role.");
         }
         //Consider no action as success
         lboolTakeTaskStatus = true;
      }
      else
      {
         try
         {
            /*
             * Work list item is assigned to user role rather than user.
             * Make sure the user belongs to the ASSIGNEE role and the work list item is not already locked
             * Take the task by populating the LOCK_USID attribute using the User id.
             *
             */
            loAssigneeFlag = loRowWrkLst.getData(C_ASSIGNEE_FL_ATTR);
            loLockUsid = loRowWrkLst.getData(C_LOCK_USID_ATTR);
            lsLockUsid = loLockUsid.getString() ;

            lsAssigneeRole = loRowWrkLst.getData(C_ASSIGNEE_ATTR).getString();
            if (AMSStringUtil.strIsEmpty(lsLockUsid) || isUserRoleMgr( foPage.getParentApp().getSession().getLogin(), lsAssigneeRole, foPage.getParentApp().getSession() ))
            {
               // If the work list row is not already locked or if locked and the user is the manager of the role
               // set LOCK_USID to current user id and save
               // (If the user is the manager of the role, he will be allowed to lock even if it was locked by another user)
               if (moLog.isDebugEnabled())
               {
                  moLog.debug("\t Taking the task by updating the LOCK_USID ...");
               }

               loLockUsid.setString(foPage.getParentApp().getSession().getLogin());
               loDSWrkLst.updateDataSource();
               lboolTakeTaskStatus = true;
            }
            else
            {
               if (moLog.isDebugEnabled())
               {
                  moLog.debug("\t The task has been already taken by another user");
               }
               //Temp code. Will change based on the decision on error handling
               moWidget.raiseException("The task has been already taken by another user.", moWidget.SEVERITY_ERROR);

               lboolTakeTaskStatus = false;
            } // end if (AMSStringUtil.strIsEmpty(lsLockUsid) || isUserRoleMgr( foVSSession.getLogin(), lsAssignee, loSession ))
         }
         catch (Exception loExp)
         {
            // Add exception log to logger object
            moLog.error("Unexpected error encountered while processing. ", loExp);


            lboolTakeTaskStatus = false;
            loDSWrkLst.undo();
            if (moLog.isErrorEnabled())
            {
               moLog.debug("\t takeTask Not able to take task "+loExp.getMessage());
            }
            moWidget.raiseException("Not able to take task. Please contact the System Adminstrator.", moWidget.SEVERITY_ERROR);
         }
         finally
         {
            //If Take Task step fails due to document action related errors, don't post the error message here.
            //Instead just set the return status to false
            lsErrMsg = extractHighestErrorMsg();

            if (lsErrMsg != null && !lsErrMsg.isEmpty())
            {
               lboolTakeTaskStatus = false;
            }
         }

      }

      if (moLog.isDebugEnabled())
      {
         moLog.debug("\t takeTask lboolTakeTaskStatus = "+lboolTakeTaskStatus);
         moLog.debug("END takeTask");
      }

      return lboolTakeTaskStatus;
   }

   /**
    * This method gets the work list items matching the given document identifier
    * @param fsDocCD
    * @param fsDeptCD
    * @param fsDocId
    * @param fsDocVersnNo
    * @param foVSSession
    * @return
    */
   public VSResultSet getWrkLstItems(String fsDocCD, String fsDeptCD, String fsDocId, String fsDocVersnNo, VSSession foVSSession)
   {
      StringBuffer lsbAddlWhere = null;

      SearchRequest loSRWrkLstItems = null;

      VSQuery loQryWrkLstItems = null;
      VSResultSet loRSWrkLstItems = null;


      if (moLog.isDebugEnabled())
      {
          moLog.debug("getWrkLstItemsDtls");
      }

      loSRWrkLstItems = new SearchRequest();
      loSRWrkLstItems.addParameter("WF_APRV_WRK_LST", "DOC_CD", fsDocCD);
      loSRWrkLstItems.addParameter("WF_APRV_WRK_LST", "DOC_DEPT_CD", fsDeptCD);
      loSRWrkLstItems.addParameter("WF_APRV_WRK_LST", "DOC_ID", fsDocId);
      loSRWrkLstItems.addParameter("WF_APRV_WRK_LST", "DOC_VERS_NO", fsDocVersnNo);
      //loSRWrkLstItems.addParameter("WF_APRV_WRK_LST", "ASSIGNEE", foVSSession.getLogin());

      //Get worklist item if it is assigned directly to him or indirectly via work flow role (Take Task would have locked it)
      lsbAddlWhere = new StringBuffer();
      lsbAddlWhere.append(" (WF_APRV_WRK_LST.ASSIGNEE = "+AMSSQLUtil.getANSIQuotedStr(foVSSession.getLogin(),true)+") OR ");
      lsbAddlWhere.append(" (WF_APRV_WRK_LST.LOCK_USID = "+AMSSQLUtil.getANSIQuotedStr(foVSSession.getLogin(),true)+")");

      loSRWrkLstItems.add(lsbAddlWhere.toString());

      loQryWrkLstItems = new VSQuery(foVSSession, "WF_APRV_WRK_LST", loSRWrkLstItems, null);
      loRSWrkLstItems = loQryWrkLstItems.execute();
      //Result set to be closed by the calling method
      if (moLog.isDebugEnabled())
      {
          moLog.debug("END getWrkLstItemsDtls");
      }
      return loRSWrkLstItems;

   }//end getWrkLstItems


   public String extractHighestErrorMsg()
   {
      int liHighestSeverityLevel = 0;

      StringBuffer lsbAllMsgs    = new StringBuffer() ;

      String lsErrMsg = "";
      String lsFirstMsg = null;
      String lsSessionMsg = null;
      String lsOflowMsg = null;


      String loHighestSeverityMsgInfo[] = null;
      VSORBSession loORBSession = null;



      try
      {
         loORBSession = moWidget.getWidgetMgr().getSession().getORBSession();

         liHighestSeverityLevel = AMSPage.getHighestSeverityLevel(loORBSession);

         if (liHighestSeverityLevel >= AMSPage.SEVERITY_LEVEL_ERROR)
         {
            //Send the message to the page first so that it can be extracted using existing AMSMsgDivElement methods
            lsSessionMsg = loORBSession.getProperty( AMSCommonConstants.PROPERTY_MESSAGES ) ;
            lsOflowMsg   = loORBSession.getProperty( AMSCommonConstants.PROPERTY_OFLOW_MESSAGES ) ;

            lsbAllMsgs.append( lsSessionMsg ) ;
            lsbAllMsgs.append( lsOflowMsg ) ;
            moWidget.moWidgetMgr.moPage.setErrorsFromPage( lsbAllMsgs.toString() ) ;


            //Now extract out the session level error messages
            loHighestSeverityMsgInfo = AMSMsgDivElement.getFirstMessage( moWidget.moWidgetMgr.moPage) ;

            if (loHighestSeverityMsgInfo != null && loHighestSeverityMsgInfo.length > 0)
            {
               lsFirstMsg = loHighestSeverityMsgInfo[AMSMsgDivElement.IDX_MESSAGE];

               if (moLog.isDebugEnabled())
               {
                   moLog.debug("\t extractHighestErrorMsg lsFirstMsg = "+lsFirstMsg);
               }


               //In some cases where special | character is present, extract error message after that
               if(lsFirstMsg.indexOf("|")>0)
               {
                  lsErrMsg = lsFirstMsg.substring(lsFirstMsg.indexOf("|")+1,lsFirstMsg.length());
               }
               else
               {
                  lsErrMsg = lsFirstMsg;
               }

               if (moLog.isDebugEnabled())
               {
                   moLog.debug("\t extractHighestErrorMsg lsErrMsg = "+lsErrMsg);
               }


            }
         }
      }
      catch (Exception loExp)
      {
            // Add exception log to logger object
            moLog.error("Unexpected error encountered while processing. ", loExp);

         if (moLog.isErrorEnabled())
         {
            moLog.debug("\t extractErrorMsg Not able to extract error message"+loExp.getMessage());
         }
         moWidget.raiseException("Not able to extract error message. Please contact the System Adminstrator.", moWidget.SEVERITY_ERROR);
      }
      return lsErrMsg;

   }


   /**
   * Returns Leave Input Units for a given Event Type
   *
   * @param fsEventTypCD
   * @return String Leave Input Units - Days/Weeks/Hrs
   */
  public String getLeaveInputUnits(String fsEventTypCD, String fsLeaveAmount, VSDate foEffectiveDt)
  {
	 if (moLog.isDebugEnabled())
	 {
		moLog.debug("\t \t AdvWrkLstUtil > getLeaveInputUnits > Enter");
		moLog.debug("\t \t \t fsEventTypCD = " + fsEventTypCD);
		moLog.debug("\t \t \t foEffectiveDt = " + foEffectiveDt.toShortString());
	 }

	 char lsLeaveInputUnits = ' ';
	 String lsLeaveInputUnitsDesc = "";

	 String lsLeaveAmount = "";
	 double ldLeaveInputAmount = 0.00;

	 String lsEffectiveDt = AMSSQLUtil.getAMSDate(foEffectiveDt, null, DataConst.DATE, moWidget.getWidgetMgr().getPage().getDatabaseType());

	 VSResultSet loResultSet = null;

	 try
	 {
		SearchRequest loSearchRequest = new SearchRequest();
		loSearchRequest.addParameter("EVNT_TYPE", "EVNT_TYP_CD", fsEventTypCD);

		StringBuilder lsbDateWhere = new StringBuilder(120);
		lsbDateWhere.append(" EFFECTIVE_DT <= ");
		lsbDateWhere.append(lsEffectiveDt);
		lsbDateWhere.append("AND EXPIRATION_DT >= ");
		lsbDateWhere.append(lsEffectiveDt);

		loSearchRequest.add(lsbDateWhere.toString());

		VSQuery loVSQuery = new VSQuery(moWidget.getWidgetMgr().getSession(), "EVNT_TYPE", loSearchRequest, null);

		loResultSet = loVSQuery.execute();

		VSRow loRow = loResultSet.first();

		if (loRow != null)
		{
		   if (loRow.getData("LEV_INPUT_DEF_ID") != null)
		   {
			  lsLeaveInputUnits = loRow.getData("LEV_INPUT_DEF_ID").getString().charAt(0);
		   }
		} /* end if ( loRow != null ) */
	 }
	 catch (Exception foExp)
	 {
		if (moLog.isErrorEnabled())
		{
		   StringBuffer lsbMsg = new StringBuffer(64);
		   lsbMsg.append("Exception while fetching LEV_INPUT_DEF_ID Description for EVNT_TYP_CD = \"");
		   lsbMsg.append(fsEventTypCD);
		   lsbMsg.append("\" for EFFECTIVE_DT = \"");
		   lsbMsg.append(foEffectiveDt.toShortString());
		   lsbMsg.append("\"");
		   moLog.error(lsbMsg.toString(), foExp);
		}

	 }
	 finally
	 {
		if (loResultSet != null)
		{
		   loResultSet.close();
		}
	 }

	 /*
	  * Hours are passed as hh:mm, so converting into hh.mm in order to convert
	  * to double value and compare with 1.00
	  */
	 lsLeaveAmount = fsLeaveAmount.replace(":", ".");

	 /*
	  * Convert Leave amount to double value
	  */
	 try
	 {
		ldLeaveInputAmount = new Double(lsLeaveAmount);
	 }
	 catch (NumberFormatException foExp)
	 {
		if (moLog.isErrorEnabled())
		{
		   StringBuffer lsbMsg = new StringBuffer(64);
		   lsbMsg.append("Exception while converting Leave amount \"");
		   lsbMsg.append(fsLeaveAmount);
		   lsbMsg.append("\" to Double value.");

		   moLog.error(lsbMsg.toString(), foExp);
		}
	 }

	 switch (lsLeaveInputUnits)
	 {
		case 'H':
		   //BGN ADVHR00045162
		   if (ldLeaveInputAmount > 1.00 || ldLeaveInputAmount == 0 )
		   //END ADVHR00045162
		   {
			  lsLeaveInputUnitsDesc = "Hrs";
		   }
		   else
		   {
			  lsLeaveInputUnitsDesc = "Hr";
		   }

		   break;

		case 'D':
		   //BGN ADVHR00045162
		   if (ldLeaveInputAmount > 1.00 || ldLeaveInputAmount == 0)
		   //END ADVHR00045162
		   {
			  lsLeaveInputUnitsDesc = "Days";
		   }
		   else
		   {
			  lsLeaveInputUnitsDesc = "Day";
		   }

		   break;

		case 'W':
		   //BGN ADVHR00045162
		   if (ldLeaveInputAmount > 1.00 || ldLeaveInputAmount == 0 )
		   //END ADVHR00045162
		   {
			  lsLeaveInputUnitsDesc = "Weeks";
		   }
		   else
		   {
			  lsLeaveInputUnitsDesc = "Week";
		   }

		   break;

		default:
		   break;
	 }

	 if (moLog.isDebugEnabled())
	 {
		moLog.debug("\t \t \t lsLeaveInputUnitsDesc = " + lsLeaveInputUnitsDesc);
		moLog.debug("\t \t AdvWrkLstUtil > getLeaveInputUnits > Exit");
	 }

	 return lsLeaveInputUnitsDesc;

   }
}
/* end class AdvWrkLstUtil */