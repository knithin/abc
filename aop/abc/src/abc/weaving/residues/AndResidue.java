package abc.weaving.residues;

import soot.Local;
import soot.SootMethod;
import soot.jimple.*;
import soot.util.Chain;
import abc.soot.util.LocalGeneratorEx;
import abc.weaving.weaver.WeavingContext;

/** Conjunction of two residues
 *  @author Ganesh Sittampalam
 *  @date 28-Apr-04
 */ 
public class AndResidue extends Residue {
    private Residue left;
    private Residue right;

    /** Get the left operand */
    public Residue getLeftOp() {
	return left;
    }

    /** Get the right operand */
    public Residue getRightOp() {
	return right;
    }

    public String toString() {
	return "("+left+") && ("+right+")";
    }

    public Stmt codeGen(SootMethod method,LocalGeneratorEx localgen,
			Chain units,Stmt begin,Stmt fail,boolean sense,
			WeavingContext wc) {
	if(sense) {
	    // want to fall through if both left and right succeed, otherwise jump to fail
	    Stmt middle=left.codeGen(method,localgen,units,begin,fail,true,wc);
	    return right.codeGen(method,localgen,units,middle,fail,true,wc);
	} else {
	    // want to jump to fail if both left and right succeed, otherwise fall through
	    Stmt nopStmt=Jimple.v().newNopStmt();
	    // if left succeeds, drop through, otherwise goto nop stmt
	    Stmt middle=left.codeGen(method,localgen,units,begin,nopStmt,true,wc);
	    // if right succeeds then jump to fail, otherwise fall through
	    Stmt end=right.codeGen(method,localgen,units,middle,fail,false,wc);
	    // make fall through statement be the nop to catch the left residue failing
	    units.insertAfter(nopStmt,end);
	    return nopStmt;
	}
	    
    }

    /** Private constructor to force use of smart constructor */
    private AndResidue(Residue left,Residue right) {
	this.left=left;
	this.right=right;
    }

    /** Smart constructor; some short-circuiting may need to be removed
     *  to mimic ajc behaviour
     */
    public static Residue construct(Residue left,Residue right) {
	if(NeverMatch.neverMatches(left) || right instanceof AlwaysMatch) 
	    return left;
	if(left instanceof AlwaysMatch || NeverMatch.neverMatches(right))
	    return right;
	return new AndResidue(left,right);
    }

	public void getAdviceFormalBindings(Bindings bindings) {
		getLeftOp().getAdviceFormalBindings(bindings);
		getRightOp().getAdviceFormalBindings(bindings);
	}
	public Residue restructureToCreateBindingsMask(soot.Local bindingsMaskLocal, Bindings bindings) {
		left=left.restructureToCreateBindingsMask(bindingsMaskLocal, bindings);
		right=right.restructureToCreateBindingsMask(bindingsMaskLocal, bindings);
		return this;
	}
}
