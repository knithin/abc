package abc.weaving.weaver;

/** When weaving a piece of advice, various values may need to be
 *  copied into variables and be passed as parameters to the advice
 *  body. The weaving context is passed to residues and to the advice
 *  declaration itself during weaving. 
 *  Each kind of advice will use a specific type of weaving context, and both
 * {@link abc.weaving.aspectinfo.AbstractAdviceDecl.makeAdviceExecutionStatements} 
 * and the {@link abc.weaving.residues.WeavingVar} instances generated by the
 * {@link abc.weaving.matching.WeavingEnv} for that advice kind will know what type to expect.
 * This class provides a base for the hierarchy of weaving contexts, as well as being appropriate
 * to use for any kind of advice that does not need any information to be passed to it. 
 *  @author Ganesh Sittampalam
 */

public class WeavingContext {

}
