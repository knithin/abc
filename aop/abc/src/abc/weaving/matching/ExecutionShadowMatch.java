/* abc - The AspectBench Compiler
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

package abc.weaving.matching;

import java.util.*;

import soot.*;
import soot.jimple.*;
import soot.util.Chain;
import soot.tagkit.Host;

import abc.weaving.aspectinfo.*;
import abc.weaving.residues.*;
import abc.soot.util.Restructure;

/** An execution join point shadow.
 *  abc does a front-end transformation that means that static initialization
 *  and advice execution join point shadows are also treated as execution shadows
 *  @author Ganesh Sittampalam
 */
public class ExecutionShadowMatch extends BodyShadowMatch {

    // Because this is a potential target for getEnclosing(),
    // we want to ensure that there is a unique instance per method

    private ExecutionShadowMatch(SootMethod container) {
	super(container);
    }

    private static Hashtable/*<SootMethod,ExecutionShadowMatch>*/ esms=new Hashtable();
    public static void reset() {
	esms=new Hashtable();
    }

    static ExecutionShadowMatch construct(SootMethod container) {
	if(esms.containsKey(container)) return (ExecutionShadowMatch) esms.get(container);
	ExecutionShadowMatch esm=new ExecutionShadowMatch(container);
	esms.put(container,esm);
	return esm;
    }

    public static ExecutionShadowMatch matchesAt(MethodPosition pos) {
	if(!(pos instanceof WholeMethodPosition)) return null;
	if(abc.main.Debug.v().traceMatcher) System.err.println("Execution");

	return construct(pos.getContainer());
    }

    public List/*<SootClass>*/ getExceptions() {
	return container.getExceptions();
    }

    public SJPInfo makeSJPInfo() {
	String jpKind;
	String sigClass;
	String sigMethod;
	String sig;
	if(isStaticInitializer()) {
	    jpKind="staticinitialization";
	    sigClass="InitializerSignature";
	    sigMethod="makeInitializerSig"; 
	    sig=SJPInfo.makeStaticInitializerSigData(container);
	} else if(isConstructor()) {
	    jpKind="constructor-execution";
	    sigClass="ConstructorSignature";
	    sigMethod="makeConstructorSig";
	    sig=SJPInfo.makeConstructorSigData(container);
	} else if(isAdviceBody()) {
	    jpKind="advice-execution";
	    sigClass="AdviceSignature";
	    sigMethod="makeAdviceSig";
	    sig=SJPInfo.makeAdviceSigData(container);
	} else {
	    jpKind="method-execution";
	    sigClass="MethodSignature";
	    sigMethod="makeMethodSig";
	    sig=SJPInfo.makeMethodSigData(container);
	}

	return new SJPInfo
	    (jpKind,sigClass,sigMethod,sig,getHost());
    }

    private boolean isStaticInitializer() {
	return container.getName().equals(SootMethod.staticInitializerName);
    }

    private boolean isConstructor() {
	return container.getName().equals(SootMethod.constructorName);
    }

    private boolean isAdviceBody() {
	return MethodCategory.adviceBody(container);
    }



    protected AdviceApplication doAddAdviceApplication
	(MethodAdviceList mal,AbstractAdviceDecl ad,Residue residue) {
	ExecutionAdviceApplication aa=new ExecutionAdviceApplication(ad,residue);
	mal.addBodyAdvice(aa);
	return aa;
    }

    public ContextValue getReturningContextValue() {

	if(container.getName().equals(SootMethod.staticInitializerName) ||
	   container.getName().equals(SootMethod.constructorName))
	    return super.getReturningContextValue();  // null value

	Stmt nop=Restructure.restructureReturn(container);
	Chain units=container.getActiveBody().getUnits();
	Stmt ret=(Stmt) units.getSuccOf(nop);

	if(ret instanceof ReturnVoidStmt)
	    return super.getReturningContextValue();  // null value
	else if(ret instanceof ReturnStmt)
	    return new JimpleValue(((ReturnStmt) ret).getOp());
	else throw new RuntimeException
		 ("restructureReturn didn't restructure returns correctly");
	   
    }

    public boolean supportsAround() {
	if(isStaticInitializer() && container.getDeclaringClass().isInterface()) return false;
	return true;
    }

    public String joinpointName() {
	if(isStaticInitializer()) 
	    return container.getDeclaringClass().isInterface() 
		? "interface static initialization" 
		: "static initialization";
	if(isConstructor()) return "constructor execution";
	if(isAdviceBody()) return "advice execution";
	return "method execution";
    }

}
