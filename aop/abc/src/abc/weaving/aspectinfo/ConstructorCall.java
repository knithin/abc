package abc.weaving.aspectinfo;

import soot.*;
import soot.jimple.*;

import polyglot.util.Position;

import abc.weaving.matching.*;
import abc.weaving.residues.*;

/** Handler for <code>call</code> shadow pointcut with a constructor pattern. */
public class ConstructorCall extends ShadowPointcut {
    private ConstructorPattern pattern;

    public ConstructorCall(ConstructorPattern pattern,Position pos) {
	super(pos);
	this.pattern = pattern;
    }

    public ConstructorPattern getPattern() {
	return pattern;
    }

    static private ShadowType shadowType=new NewStmtShadowType();
    
    static {
	ShadowPointcut.registerShadowType(shadowType);
    }

    public ShadowType getShadowType() {
	return shadowType;
    }

    protected Residue matchesAt(MethodPosition position) {
	if(!(position instanceof NewStmtMethodPosition)) return null;

	NewStmtMethodPosition stmtMP=(NewStmtMethodPosition) position;
	Stmt current=stmtMP.getStmt();

	// FIXME: Hack should be removed when patterns are added
	if(getPattern()==null) return AlwaysMatch.v;

	if(!(current instanceof AssignStmt)) return null;
	AssignStmt as = (AssignStmt) current;
	Value rhs = as.getRightOp();
	if(!(rhs instanceof NewExpr)) return null;

	Stmt next=stmtMP.getNextStmt();
	if(!(next instanceof InvokeStmt)) 
	    // FIXME : improve this behaviour
	    throw new Error("INTERNAL ERROR: Didn't find an InvokeStmt after a new");
	SootMethod method=((InvokeStmt) next).getInvokeExpr().getMethod();

	if(!getPattern().matchesConstructor(method)) return null;
	return AlwaysMatch.v;

    }

    public String toString() {
	return "constructorcall("+pattern+")";
    }

}
