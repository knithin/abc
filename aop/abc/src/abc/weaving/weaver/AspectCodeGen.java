package abc.weaving.weaver;
import soot.*;
import soot.util.*;
import soot.jimple.*;
import java.util.*;

import abc.soot.util.*;
import abc.weaving.aspectinfo.*;
import abc.weaving.matching.*;

/** Adds fields and methods to classes representing aspects.
 *   @author Jennifer Lhotak 
 *   @author Ondrej Lhotak
 *   @author Laurie Hendren
 *   @date April 30, 2004 
 */

public class AspectCodeGen {

    private static void debug(String message)
      { if (abc.main.Debug.v().aspectCodeGen) 
          System.err.println("ACG*** " + message); 
      }

    public void fillInAspect(Aspect aspct)
      { Per per = aspct.getPer();
        if (per instanceof Singleton) // singleton aspect 
          fillInSingletonAspect(aspct);
        else if ((per instanceof PerThis) || 
                 (per instanceof PerTarget))
          fillInPerObjectAspect(aspct);
        else if ((per instanceof PerCflow) ||
                 (per instanceof PerCflowBelow))
	  fillInPerCflowAspect(aspct);

        else
          throw new CodeGenException("Unknown kind of per aspect");
      }

    /* ===================== SINGLETON ASPECT =================== */

    /** top-level call to fill in singleton aspect with fields and methods */
    public void fillInSingletonAspect( Aspect aspct ) 
      { debug("--- BEGIN filling in Singleton aspect "+ 
            aspct.getName() );
        SootClass cl = aspct.getInstanceClass().getSootClass();

        // add public static final abc$perSingletonInstance field
        debug(" ... adding abc$perSingletonInstance");
        SootField instance = new SootField( "abc$perSingletonInstance", 
        cl.getType(), Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL );
        cl.addField( instance );

        // add private static java.lang.Throwable abc$initFailureCause field
        debug(" ... adding abc$intFailureCause");
        SootField instance2 = new SootField( "abc$initFailureCause", 
        RefType.v("java.lang.Throwable"),
        Modifier.PRIVATE | Modifier.STATIC );
        cl.addField( instance2 );

        // front end has put aspectOf method, fill in body
        debug(" ... adding aspectOf body");
        generateSingletonAspectOfBody(cl);

        // front end has put hasAspect method, fill in body
        debug(" ... adding hasAspect body");
        generateSingletonHasAspectBody(cl);

        // add appropriate init code to clinit()
        debug(" ... handling clinit()");
        generateSingletonClinitBody(cl);

        debug( "--- END filling in Singleton aspect "+cl +"\n" );
      }

    /** front-end has already generated empty aspectOf body,
     *    this method fills in body with implementation
     */
    private void generateSingletonAspectOfBody( SootClass cl ) {
      // get the body of aspectOf
      if (Modifier.isAbstract(cl.getModifiers()))
      	return;
      SootMethod aspectOf = cl.getMethodByName( "aspectOf" );
      Body b = Jimple.v().newBody(aspectOf);
      aspectOf.setActiveBody(b);

      // get the class for org.aspectj.lang.noAspectBoundException
      SootClass nabe = Scene.v().getSootClass(
                              "org.aspectj.lang.NoAspectBoundException");
      Local theAspect = Jimple.v().newLocal("theAspect", cl.getType());
      Local nabException = Jimple.v().newLocal("nabException", 
                                             nabe.getType() );
      Local failureCause = Jimple.v().
        newLocal("failureCause", RefType.v("java.lang.Throwable"));

      // add locals:   <AspectType> theAspect; 
      //               org.aspectj.lang.NoAspectBoundException nabException; 
      //               java.lang.Throwable failurecause; 
      b.getLocals().add(theAspect);
      b.getLocals().add(nabException);
      b.getLocals().add(failureCause);

      // make a field ref to static field abc$perSingletonInstance
      StaticFieldRef ref = Jimple.v().
          newStaticFieldRef(cl.getFieldByName("abc$perSingletonInstance"));

      // get the units of the body so we can insert new Jimple stmts
      Chain units = b.getUnits(); 

      units.addLast( Jimple.v().newAssignStmt( theAspect, ref));
      Stmt newExceptStmt = Jimple.v().newAssignStmt( nabException, 
                         Jimple.v().newNewExpr( nabe.getType() ) );
      units.addLast( Jimple.v().newIfStmt( Jimple.v().
              newEqExpr( theAspect, NullConstant.v() ), newExceptStmt ));
      units.addLast( Jimple.v().newReturnStmt( theAspect ) );
      units.addLast( newExceptStmt );
      List typelist = new LinkedList();
      typelist.add(RefType.v("java.lang.String"));
      typelist.add(RefType.v("java.lang.Throwable"));
      SootMethod initthrowmethod = nabe.getMethod("<init>",typelist);
      debug("init method for the throw in aspectOf is " + initthrowmethod);
      StaticFieldRef causefield = 
        Jimple.v().
          newStaticFieldRef(cl.getFieldByName("abc$initFailureCause"));
      Stmt assigntocause =
         Jimple.v().
           newAssignStmt(failureCause,causefield);
      units.addLast(assigntocause);
      List arglist = new LinkedList();
      // string constant with name of aspect
      arglist.add(StringConstant.v(cl.getName())); 
      // local pointing to cause
      arglist.add(failureCause);  
      // get the cause instance
      Stmt exceptioninit = 
         Jimple.v().
           newInvokeStmt( Jimple.v().newSpecialInvokeExpr
             ( nabException, initthrowmethod, arglist) ) ; 
      units.addLast( exceptioninit );
      units.addLast( Jimple.v().newThrowStmt( nabException ) );

      // have generated:
      //    theAspect = <AspectType>.abc$perSingletonInstance;
      //    if (theAspect == null) goto newExceptStmt;
      //    return(theAspect)
      //    newExceptStmt: nabException = 
      //                  new org.aspectj.lang.noAspectBoundException
      //    failureCause = abc$initFailureCause
      //    org.aspectj.lang.noAspectBoundException.nabException.<init>
      //                  ("AspectName",failureCause)
      //    throw nabException 
    }


    /** front-end has already generated empty hasAspect body,
     *    this method fills in body with implementation
     */
    private void generateSingletonHasAspectBody(SootClass cl){
      // get body
      SootMethod hasAspect;
      if (Modifier.isAbstract(cl.getModifiers()))
      	return;
      hasAspect = cl.getMethodByName("hasAspect");
      Body b = Jimple.v().newBody(hasAspect);
      hasAspect.setActiveBody(b);

      // make a LocalGenerator (will give new local names)
      LocalGeneratorEx lg = new LocalGeneratorEx(b);

      // make a local,   <AspectType> theAspect;
      Local theAspect = lg.generateLocal(cl.getType(),"theAspect");
        
      // make a static ref,  <AspectType>.abc$PerSingletonInstance
      StaticFieldRef ref = Jimple.v().
          newStaticFieldRef(cl.getFieldByName("abc$perSingletonInstance"));
        
      // get a Chain of Jimple stmts so new stmts can be inserted
      Chain units = b.getUnits(); 
        
      units.addLast( Jimple.v().newAssignStmt( theAspect, ref));
      ReturnStmt ret0 = Jimple.v().newReturnStmt( IntConstant.v(0) );

      units.addLast( Jimple.v().newIfStmt( Jimple.v().newEqExpr( theAspect, 
                  NullConstant.v() ), ret0 ));
        
      units.addLast( Jimple.v().newReturnStmt( IntConstant.v(1) ) );

      units.addLast( ret0);
      // have generated:
      //    theAspect = <AspectType>.abc$PerSingleonInstance
      //    if (theAspect == null) goto newReturnStmt
      //    return(1)
      //    newReturnStmt: return(0)
    }
    
    /** Create a new method postClinit(). 
     *
     *  If Clinit() already exists, add call to postClinit at end, 
     *  otherwise create a new Clinit and add statements to it.
     */ 
    private void generateSingletonClinitBody( SootClass cl ) {
      // create method:
      //      private static void abc$postClinit() {};
      SootMethod postClinit = new SootMethod( "abc$postClinit", 
      new ArrayList(), VoidType.v(), Modifier.PRIVATE | Modifier.STATIC );

      // add it to the aspect class
      cl.addMethod( postClinit );

      // make a new body and set it as the active one
      Body b = Jimple.v().newBody(postClinit);
      postClinit.setActiveBody(b);

      // create local:   <AspectType> theAspect;
      Local theAspect = Jimple.v().newLocal("theAspect", cl.getType());
      b.getLocals().add(theAspect);

      // get the chain of Jimple statments for the body
      Chain units = b.getUnits();
      units.addLast( Jimple.v().
      newAssignStmt( theAspect, Jimple.v().newNewExpr( cl.getType() ) ) );
      units.addLast( Jimple.v().
        newInvokeStmt( Jimple.v().
          newSpecialInvokeExpr( theAspect, 
            cl.getMethod( "<init>", new ArrayList() ) ) ) );
      StaticFieldRef ref = Jimple.v().
      newStaticFieldRef(cl.getFieldByName("abc$perSingletonInstance"));
      units.addLast( Jimple.v().newAssignStmt( ref, theAspect ) );
      units.addLast( Jimple.v().newReturnVoidStmt() ); 

      // have generated the body:
      //    theAspect = new <AspectType> ();
      //    theAspect.<init>();
      //    <AspectType>.abc$perSingletonInstance = theAspect;
      //    return;

      // now put call to abc$postClinit() into body of Clinit()
      SootMethod clinit;

      // there should already be a clinit body 
      if( !cl.declaresMethod( "void <clinit>()" ) ) 
        throw new CodeGenException("should always be one here");

      debug("getting clinit");
      clinit = cl.getMethod("void <clinit>()");
      // get the body
      Body b2 = clinit.retrieveActiveBody();
      // then the units
      units = b2.getUnits();
      // get a local generator for the body
      LocalGeneratorEx localgen = new LocalGeneratorEx(b2);
      // need a snapshotIterator because we are modifying units as we
      // traverse it
      Iterator it = units.snapshotIterator();
      while( it.hasNext() ) {
         Stmt s = (Stmt) it.next();
         // insert a call to postClinit() just before each return
         if( s instanceof ReturnVoidStmt ) 
           { // make a nop stmt which we will goto
             debug("LJH - inserting before return");
             Stmt nop = Jimple.v().newNopStmt(); 
             // postClinit(); 
             Stmt invokepostClinit =
                   Jimple.v().
                     newInvokeStmt( 
                        Jimple.v().newStaticInvokeExpr( postClinit ) );
             units.insertBefore(invokepostClinit,s);
             // goto return;
             Stmt goto_s = Jimple.v().newGotoStmt(nop);
             units.insertBefore(goto_s,s);
             // catchlocal := @caughtexception
             Local catchLocal =
                localgen.generateLocal(RefType.v("java.lang.Throwable"),
                        "catchLocal");
             CaughtExceptionRef exceptRef = 
                   Jimple.v().newCaughtExceptionRef();
             Stmt exceptionidentity =
                 Jimple.v().newIdentityStmt(catchLocal, exceptRef);
             units.insertBefore(exceptionidentity,s);
             // abc$initFailureCause := catchlocal    
             StaticFieldRef cause = 
                 Jimple.v().
                      newStaticFieldRef(
                         cl.getFieldByName("abc$initFailureCause"));
             Stmt assigntofield =
                  Jimple.v().
                     newAssignStmt(cause,catchLocal);
                        units.insertBefore(assigntofield,s);
             // add nop before return
             units.insertBefore(nop,s);
             // add the try ... catch
             b2.getTraps().
             add(Jimple.v().
                newTrap(Scene.v().getSootClass("java.lang.Throwable"),
            invokepostClinit,goto_s,exceptionidentity));

            // have created:
            //    java.lang.Exception catchLocal
            //    ...
            //    invokepostClinit :  postClinit();
            //    goto_s           :  goto nop;
            //    exceptionidentity:  catchLocal := @caughtexception;
            //    assigntofield    :  abc$initFailureCause := catchLocal;
            //    nop              :  nop
            //    S                :  return;
            //
            //    catch from invokepostClinit upto goto_s handlewith
            //                       exceptionidentity
           }
        }
    }

    /* ===================== PERTHIS/PERTARGET ASPECT ========== */

    /** top-level call to fill in per aspect with fields and methods */
    public void fillInPerObjectAspect( Aspect aspct ) 
      { SootClass cl = aspct.getInstanceClass().getSootClass();
        String aname = aspct.getName();
        String perkind = 
          aspct.getPer() instanceof PerThis ? "This" : "Target";

        debug("--- BEGIN filling in per aspect "+ aname + 
                 " with kind " + perkind);

        String perInterfaceName = aname + "$abc$Per" + perkind;
        // create name of field/get/set with _ instead of .  
        String getSetFieldName = aname.replace('.','_') + "$abc$Per" + perkind;

        String perGetName = getSetFieldName + "Get";
        String perSetName = getSetFieldName + "Set";
        String perFieldName = getSetFieldName + "Field"; 

        debug("perInterfaceName is " + perInterfaceName);
        debug("perGetName is" + perGetName);
        debug("perSetName is" + perSetName);
        debug("perFieldName is" + perFieldName);

        // generate the interface
        SootClass inter =
          genPerObjectInterface(cl, perInterfaceName, perGetName, perSetName);

        // add implementation of interface to all weavable classes
        genPerObjectInterfaceImpl(cl, inter, perGetName, perSetName, perFieldName);

        // front end has put aspectOf method, fill in body
        debug(" ... adding aspectOf body");
        genPerObjectAspectOfBody(cl, inter, perGetName);

        // front end has put hasAspect method, fill in body
        debug(" ... adding hasAspect body");
        genPerObjectHasAspectBody(cl, inter, perGetName);

        // generate the per Object bind method
        debug("... adding per Object bind method");
        genPerObjectBindMethod(cl, inter, perGetName, perSetName, perkind);

        debug( "--- END filling in per aspect "+ aname +
                        " with kind " + perkind +"\n" );
      }

    public void fillInPerCflowAspect(Aspect aspct) {
	SootClass cl = aspct.getInstanceClass().getSootClass();
	
	genPerCflowStackField(cl);
	genPerCflowAspectOfBody(cl);
	genPerCflowHasAspectBody(cl);
	genPerCflowPushMethod(cl);
    }

    /** make interface for per instance */
    private static SootClass genPerObjectInterface( SootClass aclass, 
                      String interfaceName, String getName, String setName)
      { // make the new interface 
        SootClass inter = new SootClass(interfaceName,
                              Modifier.INTERFACE | Modifier.PUBLIC);
        debug("adding " + interfaceName);
        debug("name from SootClass is " + inter.getName());
        inter.setSuperclass(Scene.v().getSootClass("java.lang.Object"));
        
        // add the getter
        SootMethod getter = new SootMethod ( getName, 
                                 new LinkedList(), 
                                 aclass.getType(),
                                 Modifier.ABSTRACT | Modifier.PUBLIC);
        inter.addMethod(getter);

        // add the setter
        ArrayList param = new ArrayList();
        param.add(aclass.getType());
        SootMethod setter = new SootMethod ( setName,
                                 param,
                                 VoidType.v(),
                                 Modifier.ABSTRACT | Modifier.PUBLIC);
        inter.addMethod(setter);
    
        // add to the new interface to the scene as application class
        Scene.v().addClass(inter); 
        inter.setApplicationClass();
        return(inter);
      }

    /** add implements interface and accessor methods to all weavable classes */
    private void genPerObjectInterfaceImpl(SootClass aclass, SootClass inter,
                                        String getName, String setName,
                                        String fieldName)
      { // TODO: right now doing ALL weavable classes as ajc does, should
        // be able to improve upon that with some analysis.  LJH 
        for( Iterator clIt = 
	         GlobalAspectInfo.v().getWeavableClasses().iterator(); 
		 clIt.hasNext(); ) 
          { final AbcClass cl = (AbcClass) clIt.next();
	    final SootClass scl = cl.getSootClass();
            debug("Adding " + inter.getName() + " to " + scl.getName());

            // add the implements
            scl.addInterface(inter); 

            // add the new field
            SootField perfield = new SootField(fieldName,
                                  aclass.getType(),
                                  Modifier.PRIVATE | Modifier.TRANSIENT);
            scl.addField(perfield);


            // add the getter --------------------------------
            SootMethod getter = new SootMethod ( getName, 
                                 new LinkedList(), 
                                 aclass.getType(),
                                 Modifier.PUBLIC);
            Body getbody = Jimple.v().newBody(getter);
            getter.setActiveBody(getbody);
            Chain getunits = getbody.getUnits().getNonPatchingChain();
            LocalGeneratorEx lgetter = new LocalGeneratorEx(getbody);

            // gen needed locals
            Local getthis = lgetter.generateLocal(scl.getType(),"this");
            Local gfieldloc = lgetter.generateLocal(aclass.getType(),
                                                               "fieldloc"); 
            // create the statements
            Stmt idstmt = Jimple.v().newIdentityStmt(getthis,
                    Jimple.v().newThisRef(RefType.v(scl)));
            FieldRef fieldref = Jimple.v().
                                  newInstanceFieldRef(getthis,perfield);
            Stmt fieldassign = Jimple.v().newAssignStmt(gfieldloc,fieldref);
            Stmt returnstmt = Jimple.v().newReturnStmt(gfieldloc);

            // insert statements into chain
            getunits.addLast(idstmt);
            getunits.addLast(fieldassign);
            getunits.addLast(returnstmt);
            
            // add the method
            scl.addMethod(getter);

            // add the setter -------------------------------------
            ArrayList param = new ArrayList();
            param.add(aclass.getType());
            SootMethod setter = new SootMethod ( setName,
                                 param,
                                 VoidType.v(),
                                 Modifier.PUBLIC);

            Body setbody = Jimple.v().newBody(setter);
            setter.setActiveBody(setbody);
            Chain setunits = setbody.getUnits().getNonPatchingChain();
            LocalGeneratorEx lsetter = new LocalGeneratorEx(setbody);

            // gen needed locals
            Local setthis = lsetter.generateLocal(scl.getType(),"this");
            Local sfieldloc = lsetter.generateLocal(
                                              aclass.getType(),"fieldloc"); 

            // create the statements
            Stmt idstmt1 = Jimple.v().newIdentityStmt(setthis,
                               Jimple.v().newThisRef(RefType.v(scl)));
            Stmt idstmt2 = Jimple.v().newIdentityStmt(sfieldloc,
                               Jimple.v().
                                     newParameterRef(aclass.getType(), 0));
            fieldref = Jimple.v().  newInstanceFieldRef(setthis,perfield);
            fieldassign = Jimple.v().newAssignStmt(fieldref,sfieldloc);
            returnstmt = Jimple.v().newReturnVoidStmt();

            // insert the statements into chain
            setunits.addLast(idstmt1);
            setunits.addLast(idstmt2);
            setunits.addLast(fieldassign);
            setunits.addLast(returnstmt);

            // add the method
            scl.addMethod(setter);
          }
      }

    /** fill in body of <AspectName> aspectOf (java.lang.Object) */
    private void genPerObjectAspectOfBody(SootClass acl, 
                      SootClass inter, 
                      String getName)
      { // get the aspectOf method
        SootMethod aspectOf = acl.getMethodByName("aspectOf");
        // get the class for  org.aspectj.lang.noAspectBoundException
        SootClass nabe = Scene.v().getSootClass(
                              "org.aspectj.lang.NoAspectBoundException");
        SootMethod nabeinit = nabe.getMethod("<init>",new ArrayList());
        // get the class for java.lang.Object
        SootClass jlo = Scene.v().getSootClass("java.lang.Object");
        // get the method for reading the per object
        SootMethod getter = inter.getMethodByName(getName);

        // make a new body for it, and make it the active one
        Body b = Jimple.v().newBody(aspectOf);
        aspectOf.setActiveBody(b);
        Chain units = b.getUnits();

        // generate the locals needed
        LocalGeneratorEx lg = new LocalGeneratorEx(b);
        Local theObject = lg.generateLocal(jlo.getType(), "theObject");
        Local nabException = lg.generateLocal(nabe.getType(), "nabException");
        Local castedArg = lg.generateLocal(inter.getType(), "castedArg");
        Local canHavePer = lg.generateLocal(BooleanType.v(), "canHavePer");
        Local perInstance = lg.generateLocal(acl.getType(), "perInstance");

        // generate the statements
        Stmt idstmt = Jimple.v().newIdentityStmt(theObject,
                         Jimple.v().newParameterRef(jlo.getType(),0));
        Stmt assign1 = Jimple.v().newAssignStmt(canHavePer,
                          Jimple.v().
                             newInstanceOfExpr(theObject,inter.getType()));  
        Stmt label0 = Jimple.v().newNopStmt(); // placeholder
        Stmt ifinstance = Jimple.v().
            newIfStmt(Jimple.v().
               newEqExpr(canHavePer,IntConstant.v(0)), label0);
        Stmt caststmt = Jimple.v().newAssignStmt(
                castedArg,Jimple.v().newCastExpr(theObject,inter.getType()));
        Stmt interfaceinvoke =
                Jimple.v().newAssignStmt (perInstance,
                    Jimple.v().newInterfaceInvokeExpr(
                      castedArg,getter));
        Stmt ifnull = Jimple.v().
            newIfStmt(Jimple.v().
              newEqExpr(perInstance,NullConstant.v()), label0);
        Stmt returnstmt = Jimple.v().newReturnStmt(perInstance);
        Stmt newexception = Jimple.v().newAssignStmt(
              nabException, Jimple.v().newNewExpr(nabe.getType()));
        Stmt initexception = Jimple.v().newInvokeStmt(
            Jimple.v().newSpecialInvokeExpr(nabException,nabeinit));
        Stmt throwstmt = Jimple.v().newThrowStmt(nabException);

        // insert the statements into the body
        units.addLast(idstmt);
        units.addLast(assign1);
        units.addLast(ifinstance);
        units.addLast(caststmt);
        units.addLast(interfaceinvoke);
        units.addLast(ifnull);
        units.addLast(returnstmt);
        units.addLast(label0);
        units.addLast(newexception);
        units.addLast(initexception);
        units.addLast(throwstmt);
      }

    /** fill in body of boolean hasAspect(java.lang.Object) */
    private void genPerObjectHasAspectBody(SootClass acl, 
                       SootClass inter, String getName)
      { // get the hasAspect method
        SootMethod hasAspect = acl.getMethodByName("hasAspect");
        // get java.lang.Objects and the get and set methods for instance
        SootClass jlo = Scene.v().getSootClass("java.lang.Object");
        SootMethod getter = inter.getMethodByName(getName);

        // make a new body, and make it active
        Body b = Jimple.v().newBody(hasAspect);
        hasAspect.setActiveBody(b);
        Chain units = b.getUnits();

        // gen the locals needed
        LocalGeneratorEx lg = new LocalGeneratorEx(b);
        Local theObject = lg.generateLocal(jlo.getType(),"theObject");
        Local canHavePer = lg.generateLocal(BooleanType.v(), "canHavePer");
        Local castedArg = lg.generateLocal(inter.getType(), "castedArg");
        Local perInstance = lg.generateLocal(acl.getType(), "perInstance");

        // gen the statements needed
        Stmt idstmt = Jimple.v().newIdentityStmt(theObject,
                         Jimple.v().newParameterRef(jlo.getType(),0));
        Stmt assign1 = Jimple.v().newAssignStmt(canHavePer,
                          Jimple.v().
                             newInstanceOfExpr(theObject,inter.getType()));  
        Stmt label0 = Jimple.v().newNopStmt(); // placeholder
        Stmt ifinstance = Jimple.v().
            newIfStmt(Jimple.v().
               newEqExpr(canHavePer,IntConstant.v(0)), label0);
        Stmt caststmt = Jimple.v().newAssignStmt(
                castedArg,Jimple.v().newCastExpr(theObject,inter.getType()));
        Stmt interfaceinvoke =
                Jimple.v().newAssignStmt (perInstance,
                    Jimple.v().newInterfaceInvokeExpr(
                      castedArg,getter));
        Stmt ifnull = Jimple.v().
            newIfStmt(Jimple.v().
              newEqExpr(perInstance,NullConstant.v()), label0);
        Stmt returnstmt_true = Jimple.v().newReturnStmt(IntConstant.v(1));
        Stmt returnstmt_false = Jimple.v().newReturnStmt(IntConstant.v(0));

        // insert the statements into the body
        
        units.addLast(idstmt);
        units.addLast(assign1);
        units.addLast(ifinstance);
        units.addLast(caststmt);
        units.addLast(interfaceinvoke);
        units.addLast(ifnull);
        units.addLast(returnstmt_true);
        units.addLast(label0);
        units.addLast(returnstmt_false);
      }
   
    /** create method public static void abc$perThisBind(java.lang.Object)  or
                      public static void abc$perTargetBind(java.lang.Object) */
    private void genPerObjectBindMethod(SootClass acl, SootClass inter, 
                   String getName, String setName, String perKind)
      { String methodname = "abc$per" + perKind + "Bind"; 
        // get the class for java.lang.Object, and set/get methods
        SootClass jlo = Scene.v().getSootClass("java.lang.Object");
        SootMethod getter = inter.getMethodByName(getName);
        SootMethod setter = inter.getMethodByName(setName);
        SootMethod aclinit = acl.getMethod("<init>",new ArrayList());
        
        // make the method and add it to the class
        ArrayList param = new ArrayList();
        param.add(jlo.getType());
        SootMethod binder = new SootMethod ( methodname,
                                  param, 
                                  VoidType.v(), 
                                  Modifier.PUBLIC | Modifier.STATIC );
        acl.addMethod(binder);

        // get body and chain
        Body b = Jimple.v().newBody(binder);
        binder.setActiveBody(b);
        Chain units = b.getUnits().getNonPatchingChain();

        // generate the locals needed
        LocalGeneratorEx lg = new LocalGeneratorEx(b);
        Local theObject = lg.generateLocal(acl.getType(), "theObject");
        Local castedArg = lg.generateLocal(inter.getType(), "castedArg");
        Local canHavePer = lg.generateLocal(BooleanType.v(), "canHavePer");
        Local perInstance = lg.generateLocal(acl.getType(), "perInstance");
        Local newInstance = lg.generateLocal(acl.getType(), "newInstance");

        // generate the statements
        Stmt idstmt = Jimple.v().newIdentityStmt(theObject,
                         Jimple.v().newParameterRef(jlo.getType(),0));
        Stmt assign1 = Jimple.v().newAssignStmt(canHavePer,
                          Jimple.v().
                             newInstanceOfExpr(theObject,inter.getType()));  
        Stmt label0 = Jimple.v().newNopStmt(); // placeholder
        Stmt ifinstance = Jimple.v().
            newIfStmt(Jimple.v().
               newEqExpr(canHavePer,IntConstant.v(0)), label0);
        Stmt caststmt = Jimple.v().newAssignStmt(
                castedArg,Jimple.v().newCastExpr(theObject,inter.getType()));
        Stmt interfaceinvokeget =
                Jimple.v().newAssignStmt (perInstance,
                    Jimple.v().newInterfaceInvokeExpr(
                      castedArg,getter));
        Stmt ifnotnull = Jimple.v().
            newIfStmt(Jimple.v().
              newNeExpr(perInstance,NullConstant.v()), label0);
        Stmt newstmt = Jimple.v().newAssignStmt(
              newInstance, Jimple.v().newNewExpr(acl.getType()));
        Stmt initstmt = Jimple.v().newInvokeStmt(
            Jimple.v().newSpecialInvokeExpr(newInstance,aclinit));
        Stmt interfaceinvokeset =
            Jimple.v().newInvokeStmt(
              Jimple.v().newInterfaceInvokeExpr(castedArg,setter,newInstance));

        Stmt returnstmt = Jimple.v().newReturnVoidStmt();

        // insert the statements into the body
        units.addLast(idstmt);
        units.addLast(assign1);
        units.addLast(ifinstance);
        units.addLast(caststmt);
        units.addLast(interfaceinvokeget);
        units.addLast(ifnotnull);
        units.addLast(newstmt);
        units.addLast(initstmt);
        units.addLast(interfaceinvokeset);
        units.addLast(label0);
        units.addLast(returnstmt);
      }

    public void genPerCflowStackField(SootClass aspct) {
	SootField perCflowStack;

	SootClass stackClass=Scene.v()
	    .loadClassAndSupport("org.aspectj.runtime.internal.CFlowStack");
	RefType stackType=stackClass.getType();

	perCflowStack=new SootField("abc$perCflowStack",stackType,
				    Modifier.PUBLIC | Modifier.STATIC);

	SootMethod preClinit=new AspectCodeGen().getPreClinit(aspct);
	LocalGeneratorEx lg=new LocalGeneratorEx
	    (preClinit.getActiveBody());
	    
	Local loc=lg.generateLocal(stackType,"perCflowStack");
	Chain units=preClinit.getActiveBody().getUnits();

	Stmt returnStmt=(Stmt) units.getFirst();
	while(!(returnStmt instanceof ReturnVoidStmt)) 
	    returnStmt=(Stmt) units.getSuccOf(returnStmt);
	    
	units.insertBefore(Jimple.v().newAssignStmt
			   (loc,Jimple.v().newNewExpr(stackType)),
			   returnStmt);
	units.insertBefore(Jimple.v().newInvokeStmt
			   (Jimple.v().newSpecialInvokeExpr
			    (loc,stackClass.getMethod
			     (SootMethod.constructorName,
			      new ArrayList()))),
			   returnStmt);
	units.insertBefore(Jimple.v().newAssignStmt
			   (Jimple.v().newStaticFieldRef(perCflowStack),loc),
			   returnStmt);
	debug("adding abc$perCflowStack field");
	aspct.addField(perCflowStack);
    }

    private void genPerCflowHasAspectBody(SootClass cl){
	SootMethod hasAspect;
	if (Modifier.isAbstract(cl.getModifiers()))
	    return;
	hasAspect = cl.getMethod("hasAspect",new ArrayList());
	Body b = Jimple.v().newBody(hasAspect);
	hasAspect.setActiveBody(b);
	
	LocalGeneratorEx lg = new LocalGeneratorEx(b);
	
	Chain units = b.getUnits(); 
	
	SootClass stackClass=Scene.v()
	    .loadClassAndSupport("org.aspectj.runtime.internal.CFlowStack");
	RefType stackType=stackClass.getType();

	SootField perCflowStack=cl.getFieldByName("abc$perCflowStack");
	Local stackLoc=lg.generateLocal(stackType,"perCflowStack");
      
	units.addLast(Jimple.v().newAssignStmt
		      (stackLoc,Jimple.v().newStaticFieldRef(perCflowStack)));


	Local hasAspectLoc=lg.generateLocal(BooleanType.v(),"hasAspect");
	units.addLast(Jimple.v().newAssignStmt
		      (hasAspectLoc,
		       Jimple.v().newVirtualInvokeExpr
		       (stackLoc,
			stackClass.getMethod("isValid",new ArrayList()))));

	units.addLast(Jimple.v().newReturnStmt(hasAspectLoc));
    }

    private void genPerCflowAspectOfBody(SootClass cl){
	SootMethod hasAspect;
	if (Modifier.isAbstract(cl.getModifiers()))
	    return;
	hasAspect = cl.getMethod("aspectOf",new ArrayList());
	Body b = Jimple.v().newBody(hasAspect);
	hasAspect.setActiveBody(b);
	
	LocalGeneratorEx lg = new LocalGeneratorEx(b);
	
	Chain units = b.getUnits(); 

	SootClass stackClass=Scene.v()
	    .loadClassAndSupport("org.aspectj.runtime.internal.CFlowStack");
	RefType stackType=stackClass.getType();
	
	SootField perCflowStack=cl.getFieldByName("abc$perCflowStack");
	Local stackLoc=lg.generateLocal(stackType,"perCflowStack");
      
	units.addLast(Jimple.v().
		      newAssignStmt(stackLoc,
				    Jimple.v().newStaticFieldRef(perCflowStack)));

	Type object=Scene.v().getSootClass("java.lang.Object").getType();

	Local theAspectO=lg.generateLocal(object,"theAspectO");
	units.addLast(Jimple.v().newAssignStmt
		      (theAspectO,
		       Jimple.v().newVirtualInvokeExpr
		       (stackLoc,
			stackClass.getMethod("peekInstance",new ArrayList()))));

	Local theAspect=lg.generateLocal(cl.getType(),"theAspect");
	units.addLast(Jimple.v().newAssignStmt
		      (theAspect,Jimple.v().newCastExpr(theAspectO,cl.getType())));

	units.addLast(Jimple.v().newReturnStmt(theAspect));
    }


    /** create method public static void abc$perCflowPush() */
    private void genPerCflowPushMethod(SootClass aspct) {
	SootClass stackClass=Scene.v()
	    .loadClassAndSupport("org.aspectj.runtime.internal.CFlowStack");
	RefType stackType=stackClass.getType();

	Type object=Scene.v().getSootClass("java.lang.Object").getType();

	SootMethod perCflowPush=new SootMethod
	    ("abc$perCflowPush",new ArrayList(),VoidType.v(),
	     Modifier.PUBLIC | Modifier.STATIC);

	Body b=Jimple.v().newBody(perCflowPush);
	perCflowPush.setActiveBody(b);

	LocalGeneratorEx lg=new LocalGeneratorEx(b);

	Chain units=b.getUnits();

	SootField perCflowStack=aspct.getFieldByName("abc$perCflowStack");
	Local stackLoc=lg.generateLocal(stackType,"perCflowStack");

	units.addLast(Jimple.v().newAssignStmt
		      (stackLoc,Jimple.v().newStaticFieldRef(perCflowStack)));

	Local aspectLoc=lg.generateLocal(aspct.getType(),"aspect");
	units.addLast(Jimple.v().newAssignStmt
		      (aspectLoc,Jimple.v().newNewExpr(aspct.getType())));
	units.addLast(Jimple.v().newInvokeStmt
		      (Jimple.v().newSpecialInvokeExpr
		       (aspectLoc,aspct.getMethod
			(SootMethod.constructorName,new ArrayList()))));

	ArrayList pushArgs=new ArrayList(1);
	pushArgs.add(object);

	units.addLast(Jimple.v().newInvokeStmt
		      (Jimple.v().newVirtualInvokeExpr
		       (stackLoc,
			stackClass.getMethod("pushInstance",pushArgs),
			aspectLoc)));

	units.addLast(Jimple.v().newReturnVoidStmt());
		      

	aspct.addMethod(perCflowPush);
	
    }


    public SootMethod getPreClinit(SootClass cl) {
	if(cl.declaresMethod("abc$preClinit",new ArrayList(),VoidType.v()))
	    return cl.getMethod("abc$preClinit",new ArrayList(),VoidType.v());

	SootMethod preClinit=new SootMethod
	    ("abc$preClinit",new ArrayList(),
	     VoidType.v(),Modifier.PRIVATE | Modifier.STATIC);

	SootMethod clinit=cl.getMethodByName(SootMethod.staticInitializerName);

	Body b = Jimple.v().newBody(preClinit);
	preClinit.setActiveBody(b);

	b.getUnits().addLast(Jimple.v().newReturnVoidStmt());

	cl.addMethod(preClinit);

	// There shouldn't be any identity statements; 
	// perhaps we should do getFirstRealStmt or whatever just in case,
	// though
	clinit.getActiveBody().getUnits().addFirst
	    (Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(preClinit)));

	return preClinit;

    }

    
    /* =============== PERCFLOW/PERCFLOWBELOW ASPECT ============= */
    // aspectOf
    
    // hasAspect

    // add static for cflow stack, add initializer in preClinit()

}
