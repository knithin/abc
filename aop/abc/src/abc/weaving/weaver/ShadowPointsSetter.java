/* abc - The AspectBench Compiler
 * Copyright (C) 2004 Laurie Hendren
 * Copyright (C) 2004 Ondrej Lhotak
 * Copyright (C) 2004 Jennifer Lhotak
 * Copyright (C) 2004 Ganesh Sittampalam
 *
 * This compiler is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This compiler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this compiler, in the file LESSER-GPL;
 * if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package abc.weaving.weaver; 
 
import java.util.*;

import soot.*;
import soot.util.*;
import soot.jimple.*;

import polyglot.util.InternalCompilerError;

import abc.weaving.aspectinfo.*;
import abc.weaving.matching.*;
import abc.weaving.weaver.*;
import abc.soot.util.*;

/** The purpose of this class is to iterate over all AdviceApplication
 *    instances and initialize their ShadowPoints.   
 *    This is done within a class, for each method/constructor body.
 *    If more than one AdviceApplication applies to a particular joinpoint,
 *    then only one ShadowPoints object is allocated, that is shared between
 *    all of them.
 *
 *    Creating the ShadowPoints will also introduce new NOP statements that
 *    mark the beginning and end of the joinpoint shadow.   The weaver uses
 *    these markers to see where to insert code.
 *
 * @author Laurie Hendren
 * @author Ondrej Lhotak
 * @author Jennifer Lhotak
 * @author Ganesh Sittampalam
 */

public class ShadowPointsSetter {


    private Map unitBindings = null;
    public ShadowPointsSetter(Map unitBindings) {
        this.unitBindings = unitBindings;
    }
    public ShadowPointsSetter() {
        this.unitBindings = new HashMap();
    }

    private static void debug(String message)
      { if (abc.main.Debug.v().shadowPointsSetter) 
	   System.err.println("SPS*** " + message);
      }	

    /** Set all ShadowPoints for AdviceApplications in class sc. */

    /* --------------------------- PASS 1 --------------------------*/

    public void setShadowPointsPass1(SootClass sc) {
      debug("--- BEGIN Setting ShadowPoints Pass1 for class " + 
	  sc.getName());

      // for each method in the class 
      for( Iterator methodIt = sc.getMethods().iterator(); 
	   methodIt.hasNext(); ) {

	 // get the next method
         final SootMethod method = (SootMethod) methodIt.next();

	 // nothing to do for abstract or native methods 
         if( method.isAbstract() ) continue;
         if( method.isNative() ) continue;

	 ExecutionShadowMatch esm
	     =GlobalAspectInfo.v().getExecutionShadowMatch(method);

	 if(esm!=null)
	     insertExecutionSP(method,esm);

	 insertStmtSP(method,
		      GlobalAspectInfo.v().getStmtShadowMatchList(method));

	 // The interface initialization shadow points have already been set

	 debug("   --- END Setting ShadowPoints Pass1 for method " + 
	                    method.getName() + "\n");
       } // for each method

      debug(" --- END Setting ShadowPoints Pass1 for class " + sc.
	          getName() + "\n");
    } // setShadowPointsPass1


    private void insertExecutionSP(SootMethod method,ExecutionShadowMatch sm) {

	debug("Need to transform for execution in method: " + method.getName()); 

	// restructure returns, and insert begin and end nops
	sm.setShadowPoints(restructureBody(method));
	debug("ShadowPoints are: " + sm.sp);
	     
    }

    private Stmt rebind(Stmt s) {
        Stmt ret = (Stmt) unitBindings.get(s);
        if( ret != null ) return ret;
        return s;
    }
    private void insertStmtSP(SootMethod method,List/*<StmtShadowMatch>*/ smList) {

	// iterate through all shadow matches looking for Stmt ones
	for( Iterator smIt = smList.iterator(); smIt.hasNext(); ) {
	    final StmtShadowMatch sm = (StmtShadowMatch) smIt.next();
	    
	    debug("Creating a new ShadowPoints .... ");
	    sm.setShadowPoints(insertNopsAroundStmt(sm,method,rebind(sm.getStmt())));

	} // for each statement
    } // insertStmtSP

  /** Transform body of method so it has exactly one return at the end, 
   * preceeded by nop.  This nop is the end point of shadowpoints.
   * Also insert begin nop either at beginning of body (for methods) or
   * just after <init> call (for constructors). 
   * Return the ShadowPoints object containing new begin and end.
   */
  private ShadowPoints restructureBody(SootMethod method) {
    Body b = method.getActiveBody();
    Chain units = b.getUnits().getNonPatchingChain();
     
    // First look for beginning of shadow point.  If it is a method,
    // a new nop inserted at the beginning of method body.  
    // If it is a constructor, a new nop is inserted
    // right after the call to <init> in the body.    Assume
    // we get code from a Java compiler, and there is only one
    // <init>.  If there is more than one <init> throw an exception.
    Stmt startnop = Jimple.v().newNopStmt();
    if (method.getName().equals(SootMethod.constructorName)) { 
	debug("Need to insert after call to <init> and interface initialisations");
	// Find call to <init>,  
	Stmt startstmt = Restructure.findInitStmt(units);

	// Find the last statement with an InterfaceInitNopTag, if any
	Stmt nextstmt = startstmt;
	while(nextstmt!=null) {
	    if(nextstmt.hasTag(IntertypeAdjuster.InterfaceInitNopTag.name) ||
	       (abc.main.Debug.v().ajcCompliance && 
		nextstmt.hasTag(IntertypeAdjuster.ITDInitEndNopTag.name)))
		startstmt=nextstmt;
	    nextstmt=(Stmt) units.getSuccOf(nextstmt);
	}
	units.insertAfter(startnop,startstmt);
    } else if(method.getName().equals(SootMethod.staticInitializerName) 
	      && method.getDeclaringClass().declaresMethod("abc$preClinit",new ArrayList(),VoidType.v())) {
	debug("In a static initializer for a class that already has abc$preClinit");
	Stmt first=(Stmt) units.getFirst();
	while(first!=null) {
	    if(first instanceof InvokeStmt) {
		InvokeStmt is=(InvokeStmt) first;
		if(is.getInvokeExpr().getMethod().getName().equals("abc$preClinit")) break;
	    }
	    first=(Stmt) units.getSuccOf(first);
	}
	if(first==null) throw new InternalCompilerError
	    ("Class "+method.getDeclaringClass()+" had abc$preClinit method but static initializer "+
	     "had no call to it");
	units.insertAfter(startnop,first);
    } else { 
	debug("Need to insert at beginning of method.");  	     
	// now insert a nop for the beginning of real stmts
	Stmt firstrealstmt = Restructure.findFirstRealStmtOrNop(method,units); 
	units.insertBefore(startnop,firstrealstmt);
    }

    // Now deal with end point.  Rewire all returns to end of method body.
    // Insert a nop just before the return at end of body.
    Stmt retnop = Restructure.restructureReturn(method);
    Stmt endnop = Jimple.v().newNopStmt();
    units.insertAfter(endnop,retnop);
    return new ShadowPoints(method,startnop,endnop);
  } // method restructureBody 
    

  /** Transform body of method so that a nop is placed both before and
   *  after the given stmt.   Branches to the initial stmt must be rewired
   *  to go to the first nop.   Exeception blocks that end at stmt must
   *  be extended to include the second nop.
   */
  private ShadowPoints insertNopsAroundStmt(
      StmtShadowMatch sm, SootMethod method, Stmt targetstmt) {
    Body b = method.getActiveBody();
    // use a nonPatchingChain because we want to do our own fix of branches
    // and exceptions.
    Chain units = b.getUnits().getNonPatchingChain();  

    // create the beginning and ending nop statements
    Stmt startnop = Jimple.v().newNopStmt();
    Stmt endnop = Jimple.v().newNopStmt();
    //System.err.println("Target statement in "+method+": "+targetstmt);
    Stmt nextstmt = (Stmt) units.getSuccOf(targetstmt);

    // insert new nop stmts into body around targetstmt
   if (sm instanceof HandlerShadowMatch)
     { // verify that no trap exists that starts at the targetstmt nor 
       // ends at the stmt just after the targetstmt
       for( Iterator trIt = b.getTraps().iterator(); trIt.hasNext(); ) 
         { final Trap tr = (Trap) trIt.next();
           if( tr.getBeginUnit() == targetstmt ||
	       tr.getEndUnit() == nextstmt)
	     throw new CodeGenException(
		 "Not expecting a trap to start at, or " +
		 "end just after a handler stmt");
	  }
       // safe situation, insert nop for start and another one after it for end.
       units.insertAfter(startnop,targetstmt);
       units.insertAfter(endnop,startnop);
       debug("Inserting nop after identity stmt " + targetstmt); 
       return new ShadowPoints(method,startnop,endnop);
     }
   else if (sm instanceof ConstructorCallShadowMatch)
     { // expecting a new, followed by an <init> (treat these as one unit)
       debug("Inserting nops around pair of stmts " + targetstmt +
	         " ; " + nextstmt);
       units.insertBefore(startnop,targetstmt);
       targetstmt.redirectJumpsToThisTo(startnop);
       units.insertAfter(endnop,nextstmt);
       // if a trap started at the new,
       // it should now start at the startnop
       Restructure.resetTrapsStart(b,targetstmt,startnop);
       // if a trap ended just after the new, 
       // it should now extend down to the stmt after the endnop 
       Restructure.resetTrapsEnd(b,nextstmt,(Stmt) units.getSuccOf(endnop));
       return new ShadowPoints(method,startnop,endnop);
     }

   else
     { debug("Inserting nops around stmt: " + targetstmt);
       units.insertBefore(startnop,targetstmt);
       targetstmt.redirectJumpsToThisTo(startnop);
       units.insertAfter(endnop,targetstmt);
       // if a trap started at targetstmt 
       // it should now start at the startnop
       Restructure.resetTrapsStart(b,targetstmt,startnop);
       // if a trap ended just after the targetstmt, 
       // it should now extend down to the stmt after the endnop 
       Restructure.resetTrapsEnd(b,nextstmt,(Stmt) units.getSuccOf(endnop));
       return new ShadowPoints(method,startnop,endnop);
     }

  } // method insertNopAroundStmt

    /* --------------------------- PASS 2 --------------------------*/

    public void setShadowPointsPass2(SootClass sc) {
      debug("--- BEGIN Setting ShadowPoints Pass2 for class " +
	  sc.getName());

      // for each method in the class, look to see if it is an <init> and
      //    needs to be inlined
      debug("Iterating through methods, looking for <init> methods ");
      debug("that need inlining"); 

      LinkedList methodlist = new LinkedList();

      for( Iterator methodIt = sc.getMethods().iterator(); 
	   methodIt.hasNext(); ) {

	 // get the next method
         final SootMethod method = (SootMethod) methodIt.next();

	 // nothing to do for abstract or native methods 
         if( method.isAbstract() ) continue;
         if( method.isNative() ) continue;

	 // if it has init or preinit advice list, inline body 
	 if (GlobalAspectInfo.v().getClassInitializationShadowMatch(method)!=null ||
	     GlobalAspectInfo.v().getPreinitializationShadowMatch(method)!=null)

           { debug("Must inline body of " + method.getName());
	     // add to list of methods to process
	     // if returns not restructured, do it now 
	     Restructure.restructureReturn(method);

	     boolean inlining_performed;
	     // keep inlining until no inlining happened
	     do
	       inlining_performed = Restructure.inlineThisCall(method);
	     while (inlining_performed);
	     methodlist.add(method);
	   }
	}

      // now go back and put in the init and preinit ShadowPoints
      for (Iterator methodIt = methodlist.iterator(); methodIt.hasNext();)
	{ // ---- process next <init> method
	  final SootMethod method = (SootMethod) methodIt.next();

          debug("   --- BEGIN Setting ShadowPoints Pass2 for method " + 
	                method.getName());

	  // --- First look at preinitialization pointcuts 
	  PreinitializationShadowMatch psm
	      =GlobalAspectInfo.v().getPreinitializationShadowMatch(method);

	  if(psm!=null) insertPreinitializationSP(method,psm);

	  // --- Then look at initialization pointcuts 
	  ClassInitializationShadowMatch ism
	      =GlobalAspectInfo.v().getClassInitializationShadowMatch(method);

	  if(ism!=null) insertClassInitializationSP(method,ism);

	  debug("   --- END Setting ShadowPoints Pass2 for method " + 
		method.getName() + "\n");
        } // for each method

      debug(" --- END Setting ShadowPoints Pass2 for class " + sc.getName() + "\n");
    } // setShadowPointsPass2


    private void insertClassInitializationSP
	(SootMethod method,ClassInitializationShadowMatch sm) {
	// should only be for methods called <init>
	debug("Initialization for <init> in method: " + method.getName()); 
	// check that name is <init>, otherwise throw exception
	if (method.getName().equals("<init>")) {

	    debug("dealing with init of <init>");
	    Body b = method.getActiveBody();
	    Chain units = b.getUnits().getNonPatchingChain();
	    Stmt startnop = Jimple.v().newNopStmt();
	    Stmt endnop = Jimple.v().newNopStmt();

	    // Find call to <init>,  
	    Stmt startstmt = Restructure.findInitStmt(units);

	    if(!abc.main.Debug.v().ajcCompliance) {
		// We consider that the class initialisation only starts once interface
		// initialisation has finished; after all, superclass initialisation
		// isn't included in the joinpoint either.

		// Find the last statement with an InterfaceInitNopTag, if any
		Stmt nextstmt = startstmt;
		while(nextstmt!=null) {
		    if(nextstmt.hasTag(IntertypeAdjuster.InterfaceInitNopTag.name))
			startstmt=nextstmt;
		    nextstmt=(Stmt) units.getSuccOf(nextstmt);
		}
	    }

	    units.insertAfter(startnop,startstmt);

	    // insert endnop just after just before final ret
	    units.insertBefore(endnop,units.getLast());

	    sm.setShadowPoints(new ShadowPoints(method,startnop,endnop)); 
	}
	else
	    throw new CodeGenException("Constructor advice on non <init>"); 
    } // insertInitializationSP
    

    private void insertPreinitializationSP
	(SootMethod method,PreinitializationShadowMatch sm) {

	// should only be for methods called <init>
	debug("Preinitialization for <init> in method: " + method.getName()); 
	// check that name is <init>, otherwise throw exception
	if (method.getName().equals("<init>")) {
	    Body b = method.getActiveBody();
	    Chain units = b.getUnits().getNonPatchingChain();
	    Stmt startnop = Jimple.v().newNopStmt();
	    Stmt endnop = Jimple.v().newNopStmt();

	    // insert startnop at beginning of method, just before first
	    // real statement
	    Stmt firstreal = Restructure.findFirstRealStmt(method,units); 
	    units.insertBefore(startnop,firstreal);

	    // insert endnop just before call to <init>,  
	    Stmt initstmt = Restructure.findInitStmt(units);
	    units.insertBefore(endnop,initstmt);
	    
	    sm.setShadowPoints(new ShadowPoints(method,startnop,endnop));
	}
	else
	    throw new CodeGenException("Constructor advice on non <init>"); 
  } // insertPreinitializationSP


} // class ShadowPointsSetter
