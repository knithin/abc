package abc.weaving.matching;

import soot.*;

import abc.weaving.aspectinfo.AbstractAdviceDecl;
import abc.weaving.residues.Residue;

/** The results of matching at an initialization shadow
 *  @author Ganesh Sittampalam
 *  @date 05-May-04
 */
public class InitializationShadowMatch extends BodyShadowMatch {

    private InitializationShadowMatch(SootMethod container) {
	super(container);
    }

    public static InitializationShadowMatch matchesAt(MethodPosition pos) {
	if(!(pos instanceof WholeMethodPosition)) return null;
	SootMethod container=pos.getContainer();
	if(!container.getName().equals(SootMethod.constructorName)) return null;
	return new InitializationShadowMatch(container);
    }


    public SJPInfo makeSJPInfo() {
	// FIXME: dummy string
	return new SJPInfo
	    ("initialization","ConstructorSignature","makeConstructorSig",
	     SJPInfo.makeConstructorSigData(container),container);
    }

    protected AdviceApplication doAddAdviceApplication
	(MethodAdviceList mal,AbstractAdviceDecl ad,Residue residue) {

	InitializationAdviceApplication aa
	    =new InitializationAdviceApplication(ad,residue);
	mal.addInitializationAdvice(aa);
	return aa;
    }

    public boolean supportsAround() {
	return false;
    }
}
