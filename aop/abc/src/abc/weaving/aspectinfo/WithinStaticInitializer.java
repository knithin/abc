package abc.weaving.aspectinfo;

import soot.*;
import polyglot.util.Position;
import abc.weaving.residues.*;

/** Handler for <code>withinstaticinitializer</code> lexical pointcut
 *  @author Ganesh Sittampalam
 *  @date 01-May-04
 */
public class WithinStaticInitializer extends LexicalPointcut {

    public WithinStaticInitializer(Position pos) {
	super(pos);
    }

    protected Residue matchesAt(SootClass cls,SootMethod method) {
	// FIXME: check we're in a static initializer!
	return AlwaysMatch.v;
    }

    public String toString() {
	return "withinstaticinitializer()";
    }
}
