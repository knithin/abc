package abc.eaj.ast;

import abc.aspectj.ast.MakesAspectMethods;
import abc.aspectj.ast.Pointcut;
import java.util.List;

public interface PCLocalVars extends Pointcut, MakesAspectMethods
{
    // get a list of the new local formals
    List /*<Formal>*/ formals();
}
