package abc.weaving.weaver;

import soot.*;
import soot.util.*;
import soot.jimple.*;
import soot.javaToJimple.LocalGenerator;
import java.util.*;
import abc.weaving.aspectinfo.*;
import abc.weaving.matching.*;
import abc.weaving.weaver.*;

/** The purpose of this class is to iterate over all AdviceApplication
 *    instances for a Class and to print the key information, including
 *    information about static join points.  Useful only for debugging.
 *
 * @author Laurie Hendren
 * @date May 19, 2004
 */

public class PrintAdviceInfo {

    private static void debug(String message)
      { if (abc.main.Debug.v().printAdviceInfo) 
	   System.err.println("PAI*** " + message);
      }	

    /** generate code for all the static join points in class sc */
    public static void printAdviceInfo(SootClass sc) {
      debug("--- BEGIN Printing advice info for class " + 
	  sc.getName());

      // for each method in the class 
      for( Iterator methodIt = sc.getMethods().iterator(); 
	   methodIt.hasNext(); ) {

	 // get the next method
         final SootMethod method = (SootMethod) methodIt.next();

	 // nothing to do for abstract or native methods 
         if( method.isAbstract() ) continue;
         if( method.isNative() ) continue;

	 // get all the advice list for this method
         MethodAdviceList adviceList = 
	     GlobalAspectInfo.v().getAdviceList(method);

	 // if no advice list for this method, nothing to do
	 if ((adviceList == null) || adviceList.isEmpty())
           { debug("No advice list for method " + method.getName());
	     continue;
	   }
         
         debug("   --- BEGIN printing advice info for method " + 
	                method.getName());

	 // --- Deal with each of the four lists 
	 if (adviceList.hasBodyAdvice())
	    printAdviceForMethod("Body",adviceList.bodyAdvice);

         if (adviceList.hasInitializationAdvice())
	    printAdviceForMethod("Intialization",
		             adviceList.initializationAdvice);

	 if (adviceList.hasPreinitializationAdvice())
	    printAdviceForMethod("Preinitialization",
		            adviceList.preinitializationAdvice);

	 if (adviceList.hasStmtAdvice())
	    printAdviceForMethod("Statement",adviceList.stmtAdvice);

	 debug("   --- END printing advice info for method " + 
	                    method.getName() + "\n");
       } // for each method

      debug(" --- END printing advice info for class " + 
	                sc.getName() + "\n");
    } // printAdviceInfo 


  private static void printAdviceForMethod( String kind, 
	                List /*<AdviceApplication>*/ adviceApplList) {

     debug("Advice for " + kind);
     for (Iterator alistIt = adviceApplList.iterator(); alistIt.hasNext();)
        { final AdviceApplication adviceappl = 
	                  (AdviceApplication) alistIt.next(); 
          debug(" ---- Advice " + adviceappl.advice);
	  debug(" ----------  SJP " + adviceappl.sjpInfo);
	  debug(" ----------  ESJP " + adviceappl.sjpEnclosing);
	} // each advice for the SJP
    } // printAdviceForMethod 

} // class PrintAdviceInfo 
