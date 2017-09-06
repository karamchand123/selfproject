//{{COMPONENT_IMPORT_STMTS
package advantage;
import java.util.Enumeration;
import java.util.Vector;
import versata.common.*;
import versata.common.vstrace.*;
import versata.vls.cache.*;
import versata.vls.*;
import java.util.*;
import java.text.*;
import java.math.*;
import com.amsinc.gems.adv.common.*;
import com.versata.util.logging.*;
import org.apache.commons.logging.*;

//END_COMPONENT_IMPORT_STMTS}}

/*
**  MERIDIAN_EVENT
*/

//{{COMPONENT_RULES_CLASS_DECL
public class MERIDIAN_EVENTImpl extends  MERIDIAN_EVENTBaseImpl


//END_COMPONENT_RULES_CLASS_DECL}}
{
   /**
    * On this page, only save action is allowed i.e. only updating existing record.
    * However, when user clicks save; internally we don't touch the original record.
    * Instead, we create a copy of that record, change the primary key and save it.
    * This causes beforeUpdate() to run twice, which is incorrect.
    * 
    * mboolUpdating - Flag used to keep track of this Update/ Insert transaction.
    */
   private boolean mboolUpdating;
   
	//{{COMP_CLASS_CTOR
	public MERIDIAN_EVENTImpl (){
		super();
	}
	
	public MERIDIAN_EVENTImpl(Session session, boolean makeDefaults)
	{
		super(session, makeDefaults);
	
	
	
	
	//END_COMP_CLASS_CTOR}}

	}

	//{{EVENT_CODE
	
//{{COMP_EVENT_beforeInsert
public void beforeInsert(DataObject obj, Response response)
{
	//Write Event Code below this line
   
   // Update already is progress; hence don't do anything.   
   mboolUpdating = true;
}
//END_COMP_EVENT_beforeInsert}}

//{{COMP_EVENT_beforeUpdate
public void beforeUpdate(DataObject obj, Response response)
{
	//Write Event Code below this line
   
   // Update already is progress; hence don't do anything.
   if(mboolUpdating)
      return;
      
   /*
    * Do not allow to clone the Event which has already been cloned
    */
      if( getSOURCE_TRANSACT_ID() != null)
      {
         raiseException( "%c:Q0209,v:" + getTRANSACT_ID() + "%" );
         response.reject();
         return;
      }
      else
      {
         if( AMSStringUtil
               .strEqual( getDIRECTION(), CVL_DIRECTIONImpl.OUTBOUND ) )
         {
            // Do not allow Event Status to be changed to Queued for Processing
            // or In Processing for an Outbound Event.
            if( AMSStringUtil.strEqual( getEVENT_STATUS(),
                  CVL_EVENT_STATUSImpl.QUEUED_FOR_PROCESSING )
                  || AMSStringUtil.strEqual( getEVENT_STATUS(),
                        CVL_EVENT_STATUSImpl.IN_PROCESSING ) )
            {
               raiseException( "%c:Q0210%" );
               response.reject();
               return;
            }
         }
         /*Begin Fix for ADVFN00134229
         else
         {
            // Do not allow Event Status to be changed to anything other than
            // Success for ans Inbound Event.
            if( !( AMSStringUtil.strEqual( getEVENT_STATUS(),
                  CVL_EVENT_STATUSImpl.SUCCESS ) ) )
            {
               raiseException( "%c:Q0211%" );
               response.reject();
               return;
            }
         }
         End Fix for ADVFN00134229*/
         /*
          * On this page, only save action is allowed i.e. only updating the
          * EVENT_STATUS field of an existing record. However, when user clicks
          * save; internally we don't touch the original record. Instead, we
          * create a copy of that record, change the primary key and save it.
          * 
          * The primary key of all ABI event pages is a counter stored in table
          * SEQUENCE_GEN. Hence we need to increment this and use the value in
          * inserting the copied record.
          * 
          * In case of Meridian, TRANSACT_ID is the primary key we fetch from
          * SEQUENCE_GEN table, increment it and save the incremented value.
          */
         mboolUpdating = true;
         SearchRequest lsr = new SearchRequest();
         lsr.add( " SEQUENCE_GEN.SEQ_NM = 'MG_TRANSACT_ID_SEQ' " );
         SEQUENCE_GENImpl loSeq = (SEQUENCE_GENImpl)SEQUENCE_GENImpl
               .getObjectByKey( lsr, getSession() );
         long liCnt = loSeq.getSEQ_LAST_ID() + 1;
         loSeq.setSEQ_LAST_ID( liCnt );
         loSeq.save();
         MERIDIAN_EVENTImpl loNew = MERIDIAN_EVENTImpl.getNewObject(
               getSession(), true );
         /*
          * Create a copy of this row
          */
         loNew.copyCorresponding( this, null, true );
         loNew.setTRANSACT_ID( String.valueOf( liCnt ) );
         loNew.setSOURCE_TRANSACT_ID( getTRANSACT_ID() );
         loNew.save();
         /*
          * Revert the changes done of the original record. Since EVENT_STATUS
          * is the only editable field, revert that.
          */
         setEVENT_STATUS( getOldEVENT_STATUS() );
         save();
         /*
          * Issue an Information message saying a particular Event has been
          * cloned.
          */
         raiseException( "%c:Q0208,v:" + getTRANSACT_ID() + ",v:" + liCnt + "%" );
      }
   }

//END_COMP_EVENT_beforeUpdate}}

	//END_EVENT_CODE}}



	public void addListeners() {
		//{{EVENT_ADD_LISTENERS
		
	addRuleEventListener(this);
		//END_EVENT_ADD_LISTENERS}}
	}

	//{{COMPONENT_RULES
		public static MERIDIAN_EVENTImpl getNewObject(Session session, boolean makeDefaults)
		{
			return new MERIDIAN_EVENTImpl(session, makeDefaults);
		}	
	
	//END_COMPONENT_RULES}}
	
}

